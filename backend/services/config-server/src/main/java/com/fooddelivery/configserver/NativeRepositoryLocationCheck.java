package com.fooddelivery.configserver;

import java.io.IOException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * Stops the config server at startup when the native repository location is missing, instead of
 * serving empty configuration to every service (REQ-PLAT-003 AC4).
 */
@Component
@Profile("native")
class NativeRepositoryLocationCheck implements InitializingBean {

  static final String PROPERTY = "spring.cloud.config.server.native.search-locations";

  private final Environment environment;
  private final ResourceLoader resourceLoader;

  NativeRepositoryLocationCheck(Environment environment, ResourceLoader resourceLoader) {
    this.environment = environment;
    this.resourceLoader = resourceLoader;
  }

  @Override
  public void afterPropertiesSet() throws IOException {
    String locations = environment.getProperty(PROPERTY);
    if (locations == null || locations.isBlank()) {
      throw new IllegalStateException(PROPERTY + " must be set (CONFIG_REPO_LOCATION)");
    }
    for (String location : locations.split(",")) {
      Resource resource = resourceLoader.getResource(location.trim());
      if (!resource.exists() || !resource.getFile().isDirectory()) {
        throw new IllegalStateException("Config repository directory not found: " + location);
      }
    }
  }
}
