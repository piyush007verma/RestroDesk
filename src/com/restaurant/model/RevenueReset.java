package com.restaurant.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One reset of the revenue figures. Before a reset it is a PREVIEW ({@code resetAt} is null): how many paid bills and how much money
 * would stop being counted. After a reset it describes the saved reset.
 */
public record RevenueReset(LocalDateTime resetAt, int billsCleared, BigDecimal amountCleared) {
}
