package com.fooddelivery.platform.events.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

class OutboxPublisherTest {

  @Test
  @SuppressWarnings("unchecked")
  void publishingOutsideATransactionIsAProgrammingError() {
    NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    OutboxPublisher publisher =
        new OutboxPublisher(
            jdbc,
            JsonMapper.builder().build(),
            "order-service",
            Clock.systemUTC(),
            mock(ObjectProvider.class),
            mock(ObjectProvider.class));

    assertThatIllegalStateException()
        .isThrownBy(() -> publisher.publish(event().build()))
        .withMessageContaining("business transaction");
    verifyNoInteractions(jdbc);
  }

  @Test
  void thePartitionKeyDefaultsToTheAggregateId() {
    UUID orderId = UUID.randomUUID();

    NewEvent byAggregate =
        NewEvent.builder("order.events.v1", "OrderCreated", 1)
            .aggregate("Order", orderId, 0L)
            .payload(Map.of())
            .build();
    NewEvent byOrder = event().partitionKey(orderId.toString()).build();

    assertThat(byAggregate.partitionKey()).isEqualTo(orderId.toString());
    assertThat(byOrder.partitionKey()).isEqualTo(orderId.toString());
    assertThat(byOrder.actor().type()).isEqualTo("SYSTEM");
  }

  @Test
  void eventVersionsStartAtOne() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                NewEvent.builder("order.events.v1", "OrderCreated", 0)
                    .aggregate("Order", UUID.randomUUID(), 0L)
                    .payload(Map.of())
                    .build());
  }

  private static NewEvent.Builder event() {
    return NewEvent.builder("payment.events.v1", "PaymentCompleted", 1)
        .aggregate("Payment", UUID.randomUUID(), 3L)
        .payload(Map.of("status", "SUCCESS"));
  }
}
