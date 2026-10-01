package com.fooddelivery.platform.persistence.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class MoneyTest {

  @Test
  void normalisesToTwoDecimalsWithHalfUpRounding() {
    assertThat(Money.inr("249").amount()).isEqualByComparingTo("249.00");
    assertThat(Money.inr("249").amount().scale()).isEqualTo(2);
    assertThat(Money.inr("10.005").amount()).isEqualByComparingTo("10.01");
    assertThat(Money.inr("10.004").amount()).isEqualByComparingTo("10.00");
  }

  @Test
  void arithmeticIsExactAndRequiresTheSameCurrency() {
    Money price = Money.inr("0.10");

    assertThat(price.times(3)).isEqualTo(Money.inr("0.30"));
    assertThat(price.plus(Money.inr("0.20"))).isEqualTo(Money.inr("0.30"));
    assertThat(Money.inr("1.00").minus(Money.inr("1.50")).isNegative()).isTrue();
    assertThatThrownBy(() -> price.plus(Money.of("1.00", "USD")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsUnknownCurrenciesAndAmountsBeyondTheColumn() {
    assertThatThrownBy(() -> Money.of("1.00", "XXY")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Money.inr("10000000000.00"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(Money.inr("9999999999.99").amount()).isEqualByComparingTo("9999999999.99");
  }

  @Test
  void serialisesTheAmountAsADecimalString() {
    JsonMapper json = JsonMapper.builder().build();

    String written = json.writeValueAsString(Money.inr("249"));
    Money read = json.readValue("{\"amount\":\"249.5\",\"currency\":\"INR\"}", Money.class);

    assertThat(written).isEqualTo("{\"amount\":\"249.00\",\"currency\":\"INR\"}");
    assertThat(read.amount()).isEqualTo(new BigDecimal("249.50"));
  }
}
