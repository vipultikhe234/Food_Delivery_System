package com.fooddelivery.identity.domain;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A normalised login identifier. E-mail addresses are trimmed and lower-cased; phone numbers must
 * be E.164 ({@code +919876543210}), which fits {@code users.phone varchar(16)}.
 */
public record Identifier(Type type, String value) {

  public enum Type {
    EMAIL,
    PHONE
  }

  private static final Pattern E164 = Pattern.compile("\\+[1-9][0-9]{7,14}");
  private static final int MAX_EMAIL_LENGTH = 254;

  public static Optional<Identifier> email(String raw) {
    if (raw == null) {
      return Optional.empty();
    }
    String value = raw.strip().toLowerCase(Locale.ROOT);
    int at = value.indexOf('@');
    if (at <= 0
        || at != value.lastIndexOf('@')
        || at == value.length() - 1
        || value.length() > MAX_EMAIL_LENGTH
        || value.chars().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c))) {
      return Optional.empty();
    }
    return Optional.of(new Identifier(Type.EMAIL, value));
  }

  public static Optional<Identifier> phone(String raw) {
    if (raw == null) {
      return Optional.empty();
    }
    String value = raw.strip();
    return E164.matcher(value).matches()
        ? Optional.of(new Identifier(Type.PHONE, value))
        : Optional.empty();
  }

  /** Login accepts either kind in one field: anything with an {@code @} is an e-mail address. */
  public static Optional<Identifier> parse(String raw) {
    if (raw == null) {
      return Optional.empty();
    }
    return raw.indexOf('@') >= 0 ? email(raw) : phone(raw);
  }
}
