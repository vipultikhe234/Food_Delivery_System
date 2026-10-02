package com.fooddelivery.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fooddelivery.platform.testsupport.EndpointSecurityRules;
import org.junit.jupiter.api.Test;

/** Every endpoint declares who may call it (REQ-AUTH-003 AC1, docs/09 §3.1). */
class EndpointSecurityTest {

  @Test
  void everyEndpointDeclaresItsPermissionOrIsExplicitlyPublic() {
    assertThat(EndpointSecurityRules.unsecuredEndpoints("com.fooddelivery.identity")).isEmpty();
  }
}
