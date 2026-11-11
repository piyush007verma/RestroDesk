package com.restaurant.service;

import com.restaurant.dao.ReportsDao;
import com.restaurant.exception.DatabaseException;
import com.restaurant.exception.InvalidInputException;
import com.restaurant.model.ReportData;

import java.time.LocalDate;

/** Chooses the dates of a report and collects every number the Reports screen needs. Read-only. */
public class ReportsService {

    /** The choices of the period drop-down. */
    public enum Period {
        TODAY("Today"), LAST_7_DAYS("Last 7 days"), THIS_MONTH("This month"), ALL_TIME("All time"), CUSTOM("Custom range");

        private final String label;

        Period(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** First and last day (both included) of a report. */
    public record DateRange(LocalDate from, LocalDate to) {
    }

    public static final int TOP_ITEMS = 8;
    private static final LocalDate EARLIEST = LocalDate.of(2000, 1, 1);

    private final ReportsDao dao;

    public ReportsService(ReportsDao dao) {
        this.dao = dao;
    }

    /** {@code today} is passed in so the rule can be tested; the screen passes LocalDate.now(). */
    public DateRange resolve(Period period, LocalDate customFrom, LocalDate customTo, LocalDate today)
            throws InvalidInputException {
        return switch (period) {
            case TODAY -> new DateRange(today, today);
            case LAST_7_DAYS -> new DateRange(today.minusDays(6), today); // today and the 6 days before = 7 days
            case THIS_MONTH -> new DateRange(today.withDayOfMonth(1), today);
            case ALL_TIME -> new DateRange(EARLIEST, today);
            case CUSTOM -> {
                if (customFrom == null || customTo == null) {
                    throw new InvalidInputException("Please choose both a start date and an end date.");
                }
                if (customFrom.isAfter(customTo)) {
                    throw new InvalidInputException("The start date is after the end date. Please choose the start date first, then a later end date.");
                }
                if (customFrom.isBefore(EARLIEST)) {
                    throw new InvalidInputException("The start date is too old. Please choose a date after 1 Jan 2000.");
                }
                yield new DateRange(customFrom, customTo);
            }
        };
    }

    public ReportData load(DateRange range) throws DatabaseException {
        return new ReportData(
                dao.summary(range.from(), range.to()),
                dao.dailySales(range.from(), range.to()),
                dao.monthlySales(range.from(), range.to()),
                dao.topItems(range.from(), range.to(), TOP_ITEMS),
                dao.paymentMethods(range.from(), range.to()));
    }

    /** When revenue was last reset (null = never). Reports and the Dashboard show this date. */
    public com.restaurant.model.RevenueReset latestReset() throws DatabaseException {
        return dao.latestReset();
    }

    /** How many paid bills and how much money a reset would stop counting right now. Changes nothing. */
    public com.restaurant.model.RevenueReset previewReset() throws DatabaseException {
        return dao.previewReset();
    }

    /** Reset revenue: from now on only newer bills are counted. Old bills stay in the database untouched. */
    public com.restaurant.model.RevenueReset resetRevenue() throws DatabaseException {
        return dao.resetRevenue();
    }
}
