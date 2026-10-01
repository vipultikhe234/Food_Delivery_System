package com.fooddelivery.platform.persistence.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fooddelivery.platform.web.error.ApiException;
import com.fooddelivery.platform.web.error.CommonErrorCode;
import com.fooddelivery.platform.web.error.FieldViolation;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

class IdempotencyServiceValidationTest {

  private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
  private final IdempotencyService service =
      new IdempotencyService(
          jdbc,
          mock(PlatformTransactionManager.class),
          JsonMapper.builder().build(),
          new IdempotencyProperties(true, Duration.ofHours(24), Duration.ofSeconds(60), null));

  @Test
  void anonymousRequestsAreRejected() {
    MockHttpServletRequest request = request();
    request.setUserPrincipal(null);

    ApiException e = reject(request);

    assertThat(e.errorCode()).isEqualTo(CommonErrorCode.UNAUTHENTICATED);
    verifyNoInteractions(jdbc);
  }

  @Test
  void aMissingKeyIsAValidationError() {
    MockHttpServletRequest request = request();
    request.removeHeader(IdempotencyService.HEADER);

    ApiException e = reject(request);

    assertThat(e.errorCode()).isEqualTo(CommonErrorCode.VALIDATION_FAILED);
    assertThat(e.errors())
        .containsExactly(
            new FieldViolation(IdempotencyService.HEADER, "REQUIRED", "header is required"));
    verifyNoInteractions(jdbc);
  }

  @Test
  void keysLongerThan128CharactersOrWithOddCharactersAreRejected() {
    for (String key : new String[] {"k".repeat(129), "has space", "semi;colon"}) {
      MockHttpServletRequest request = request();
      request.removeHeader(IdempotencyService.HEADER);
      request.addHeader(IdempotencyService.HEADER, key);

      ApiException e = reject(request);

      assertThat(e.errors()).extracting(FieldViolation::code).containsExactly("INVALID_VALUE");
    }
    verifyNoInteractions(jdbc);
  }

  private ApiException reject(MockHttpServletRequest request) {
    return catchThrowableOfType(
        ApiException.class,
        () -> service.execute(request, "{}", () -> ResponseEntity.ok().build()));
  }

  private static MockHttpServletRequest request() {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders");
    request.setUserPrincipal(UUID.randomUUID()::toString);
    request.addHeader(IdempotencyService.HEADER, UUID.randomUUID().toString());
    return request;
  }
}
