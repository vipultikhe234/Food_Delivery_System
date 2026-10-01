package com.fooddelivery.platform.web.error;

/**
 * One entry of the {@code errors} array: {@code field} is a property path such as {@code
 * items[0].quantity}, {@code code} an UPPER_SNAKE rule name such as {@code MAX}.
 */
public record FieldViolation(String field, String code, String message) {}
