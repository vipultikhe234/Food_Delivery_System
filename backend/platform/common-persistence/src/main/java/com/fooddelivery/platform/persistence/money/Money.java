package com.fooddelivery.platform.persistence.money;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * An amount with its currency, stored as {@code numeric(12,2)} plus {@code char(3)} and sent as
 * {@code {"amount":"249.00","currency":"INR"}} (docs/06 §1.1, docs/07 §1, REQ-PLAT-008 AC5).
 * Floating point is never used for money.
 */
@Embeddable
public record Money(
    @Column(name = "amount", precision = 12, scale = 2, nullable = false)
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal amount,
    @Column(name = "currency", length = 3, nullable = false) @JdbcTypeCode(SqlTypes.CHAR)
        String currency) {

  public static final int SCALE = 2;
  public static final String INR = "INR";
  private static final BigDecimal MAX = new BigDecimal("9999999999.99");

  public Money {
    Objects.requireNonNull(amount, "amount");
    Objects.requireNonNull(currency, "currency");
    Currency.getInstance(currency);
    amount = amount.setScale(SCALE, RoundingMode.HALF_UP);
    if (amount.abs().compareTo(MAX) > 0) {
      throw new IllegalArgumentException("amount exceeds numeric(12,2): " + amount);
    }
  }

  public static Money of(String amount, String currency) {
    return new Money(new BigDecimal(amount), currency);
  }

  public static Money inr(String amount) {
    return of(amount, INR);
  }

  public static Money zero(String currency) {
    return new Money(BigDecimal.ZERO, currency);
  }

  public Money plus(Money other) {
    return new Money(amount.add(sameCurrency(other).amount), currency);
  }

  public Money minus(Money other) {
    return new Money(amount.subtract(sameCurrency(other).amount), currency);
  }

  public Money times(int quantity) {
    return new Money(amount.multiply(BigDecimal.valueOf(quantity)), currency);
  }

  @JsonIgnore
  public boolean isNegative() {
    return amount.signum() < 0;
  }

  private Money sameCurrency(Money other) {
    if (!currency.equals(other.currency)) {
      throw new IllegalArgumentException(
          "currency mismatch: " + currency + " vs " + other.currency);
    }
    return other;
  }
}
