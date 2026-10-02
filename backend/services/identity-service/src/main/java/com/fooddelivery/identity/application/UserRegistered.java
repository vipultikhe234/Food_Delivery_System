package com.fooddelivery.identity.application;

import java.util.List;
import java.util.UUID;

/**
 * Payload of {@code UserRegistered} v1 on {@code identity.events.v1}
 * (event-contracts/identity.events.v1/UserRegistered.v1.schema.json). user-service creates the
 * profile from it. Contact details stay out of the event (docs/08 §1, minimal PII).
 */
public record UserRegistered(UUID userId, String fullName, List<String> roles) {

  public static final String TOPIC = "identity.events.v1";
  public static final String TYPE = "UserRegistered";
  public static final int VERSION = 1;
}
