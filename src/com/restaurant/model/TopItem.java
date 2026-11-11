package com.restaurant.model;

import java.math.BigDecimal;

/** One dish in the best-selling report. {@code sales} = quantity x the price at order time (before discount and GST). */
public record TopItem(String name, int quantity, BigDecimal sales) {
}
