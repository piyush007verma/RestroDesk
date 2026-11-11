package com.restaurant.model;

import java.math.BigDecimal;

/** Successful payments made with one payment method. */
public record MethodTotal(PaymentMethod method, int payments, BigDecimal amount) {
}
