package com.fooddelivery.platform.testsupport;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

/** Execution condition behind {@link RequiresDocker}. */
public class DockerAvailableCondition implements ExecutionCondition {

  @Override
  public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
    if (isCi(System.getenv("CI"))) {
      return ConditionEvaluationResult.enabled("CI: Docker-based tests always run");
    }
    if (DockerClientFactory.instance().isDockerAvailable()) {
      return ConditionEvaluationResult.enabled("Docker is available");
    }
    return ConditionEvaluationResult.disabled(
        "NOT EXECUTED: Docker is not available on this machine (runs in CI)");
  }

  static boolean isCi(String value) {
    return "true".equalsIgnoreCase(value);
  }
}
