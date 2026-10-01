package com.fooddelivery.platform.observability.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PiiMaskerTest {

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "login ok for 9876543210|login ok for ******3210",
        "phone +91 98765 43210 verified|phone ******3210 verified",
        "phone +91-9876543210|phone ******3210",
        "mail rahul.sharma@example.com sent|mail r***@example.com sent",
        "card 4111 1111 1111 1111 declined|card ************1111 declined",
        "card 4111-1111-1111-1111|card ************1111",
        "Authorization: Bearer abc.def-ghi_jkl|Authorization: ****",
        "header bearer AbC123+/=|header bearer ****",
        "password=Secret123 user=u1|password=**** user=u1",
        "{\"otp\":\"482913\",\"ok\":true}|{\"otp\":\"****\",\"ok\":true}",
        "refresh_token=r-1.2.3&x=1|refresh_token=****&x=1",
        "csrf_token=abc123 next|csrf_token=**** next",
        "clientSecret: s3cr3t|clientSecret: ****",
        "address: 12 MG Road|address: **** MG Road",
      })
  void masksSensitiveValues(String input, String expected) {
    assertThat(PiiMasker.mask(input)).isEqualTo(expected);
  }

  @Test
  void masksRawJwt() {
    String jwt =
        "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ1c2VyLTEiLCJleHAiOjE3MDAwMDAwMDB9.c2lnbmF0dXJlLXZhbHVl";
    assertThat(PiiMasker.mask("token " + jwt + " rejected")).isEqualTo("token **** rejected");
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "order 0192f6a4-6b1e-7c3d-9a8b-1234567890ab created",
        "took 1727766600000 ms",
        "amount 4111111111111112 is not a card",
        "trace 4bf92f3577b34da6a3ce929d0e0e4736",
        "otp request 1234 queued",
        "spin=3 shipping=fast",
      })
  void leavesNonSensitiveValuesUntouched(String input) {
    assertThat(PiiMasker.mask(input)).isEqualTo(input);
  }

  @Test
  void handlesNullAndEmpty() {
    assertThat(PiiMasker.mask(null)).isNull();
    assertThat(PiiMasker.mask("")).isEmpty();
  }

  @Test
  void luhnCheck() {
    assertThat(PiiMasker.luhnValid("4111111111111111")).isTrue();
    assertThat(PiiMasker.luhnValid("4111111111111112")).isFalse();
    assertThat(PiiMasker.luhnValid("411111")).isFalse();
  }
}
