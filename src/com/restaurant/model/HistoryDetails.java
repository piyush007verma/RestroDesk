package com.restaurant.model;

import java.util.List;

/** Everything shown in the details card of Order History. {@code bill} is null when the order has no bill. */
public record HistoryDetails(HistoryRow row, List<OrderLine> lines, Bill bill) {
}
