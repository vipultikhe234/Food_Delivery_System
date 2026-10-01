package com.fooddelivery.platform.testsupport;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Marks a test class that needs a Docker engine (Testcontainers).
 *
 * <p>Without Docker the tests are disabled on a developer machine, but they still run, and fail,
 * when the {@code CI} environment variable is {@code true}. A missing Docker engine in CI can
 * therefore never turn into silently skipped tests.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ExtendWith(DockerAvailableCondition.class)
public @interface RequiresDocker {}
