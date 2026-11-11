package com.restaurant.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Sales of one day (or, for the monthly report, of one month: {@code day} is the first day of that month). */
public record SalesRow(LocalDate day, int orders, BigDecimal sales, BigDecimal discounts, BigDecimal gst) {
}
