package com.restaurant.model;

import java.util.List;

/** Everything the Reports screen shows for one period. */
public record ReportData(ReportSummary summary, List<SalesRow> daily, List<SalesRow> monthly,
                         List<TopItem> topItems, List<MethodTotal> methods) {
}
