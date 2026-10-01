package com.fooddelivery.platform.observability.logging;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masks secrets and personal data in log text (docs/09-security.md §4, REQ-OBS-001 AC2).
 *
 * <p>Masking is a safety net. Code must still never log request bodies, credentials or contact data
 * on purpose.
 */
public final class PiiMasker {

  static final String MASK = "****";

  private static final Pattern AUTHORIZATION_HEADER =
      Pattern.compile(
          "(?i)(\\bauthorization\"?\\s*[:=]\\s*\"?)(?:(?:bearer|basic|digest)\\s+)?[^\"\\s,&;}]+");

  private static final Pattern BEARER = Pattern.compile("(?i)(bearer\\s+)[A-Za-z0-9\\-._~+/]+=*");

  private static final Pattern JWT =
      Pattern.compile("eyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]*");

  private static final Pattern SENSITIVE_KEY_VALUE =
      Pattern.compile(
          "(?i)(\"?\\b(?:[\\w-]*(?:password|passwd|secret|token|api[_-]?key)|pwd|otp|pin|cvv|cvc"
              + "|cookie|set-cookie|address|line1|line2|landmark)\"?\\s*[:=]\\s*\"?)"
              + "([^\"\\s,&;}]+)");

  private static final Pattern EMAIL =
      Pattern.compile("([A-Za-z0-9._%+-])[A-Za-z0-9._%+-]*@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})");

  private static final Pattern CARD_CANDIDATE =
      Pattern.compile("(?<![\\w])(?:\\d[ -]?){12,18}\\d(?![\\w])");

  private static final Pattern INDIAN_MOBILE =
      Pattern.compile("(?<![\\w+])(?:\\+?91[\\s-]?)?([6-9]\\d{4}[\\s-]?\\d{5})(?![\\w])");

  private PiiMasker() {}

  /** Returns the text with tokens, secrets, e-mail addresses, card numbers and phones masked. */
  public static String mask(String text) {
    if (text == null || text.isEmpty()) {
      return text;
    }
    String result = AUTHORIZATION_HEADER.matcher(text).replaceAll("$1" + MASK);
    result = BEARER.matcher(result).replaceAll("$1" + MASK);
    result = JWT.matcher(result).replaceAll(MASK);
    result = SENSITIVE_KEY_VALUE.matcher(result).replaceAll("$1" + MASK);
    result = EMAIL.matcher(result).replaceAll("$1***@$2");
    result = maskCards(result);
    result = maskPhones(result);
    return result;
  }

  private static String maskCards(String text) {
    Matcher matcher = CARD_CANDIDATE.matcher(text);
    StringBuilder out = new StringBuilder();
    while (matcher.find()) {
      String digits = matcher.group().replaceAll("[ -]", "");
      String replacement =
          luhnValid(digits) ? "*".repeat(digits.length() - 4) + last4(digits) : matcher.group();
      matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(out);
    return out.toString();
  }

  private static String maskPhones(String text) {
    Matcher matcher = INDIAN_MOBILE.matcher(text);
    StringBuilder out = new StringBuilder();
    while (matcher.find()) {
      String digits = matcher.group(1).replaceAll("[\\s-]", "");
      matcher.appendReplacement(out, Matcher.quoteReplacement("******" + last4(digits)));
    }
    matcher.appendTail(out);
    return out.toString();
  }

  private static String last4(String digits) {
    return digits.substring(digits.length() - 4);
  }

  static boolean luhnValid(String digits) {
    if (digits.length() < 13 || digits.length() > 19) {
      return false;
    }
    int sum = 0;
    boolean doubleIt = false;
    for (int i = digits.length() - 1; i >= 0; i--) {
      int d = digits.charAt(i) - '0';
      if (doubleIt) {
        d *= 2;
        if (d > 9) {
          d -= 9;
        }
      }
      sum += d;
      doubleIt = !doubleIt;
    }
    return sum % 10 == 0;
  }
}
