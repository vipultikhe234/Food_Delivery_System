package com.fooddelivery.platform.observability.logging;

import java.util.Set;
import org.springframework.boot.json.JsonWriter.MemberPath;
import org.springframework.boot.json.JsonWriter.Members;
import org.springframework.boot.json.JsonWriter.ValueProcessor;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;

/**
 * Applies {@link PiiMasker} to every string value of a structured log line except the technical
 * fields. Identifier fields are excluded so that a trace or correlation id is never altered and
 * stays searchable (REQ-OBS-001 AC3).
 */
public class MaskingJsonMembersCustomizer
    implements StructuredLoggingJsonMembersCustomizer<Object> {

  static final Set<String> UNMASKED_FIELDS =
      Set.of(
          "@timestamp",
          "timestamp",
          "level",
          "level_value",
          "logger_name",
          "logger",
          "thread_name",
          "thread",
          "service",
          "env",
          "version",
          "traceId",
          "spanId",
          "correlationId",
          "userId");

  @Override
  public void customize(Members<Object> members) {
    members.applyingValueProcessor(
        ValueProcessor.of(String.class, PiiMasker::mask)
            .whenHasPath(MaskingJsonMembersCustomizer::isMaskable));
  }

  private static boolean isMaskable(MemberPath path) {
    return path.name() == null || !UNMASKED_FIELDS.contains(path.name());
  }
}
