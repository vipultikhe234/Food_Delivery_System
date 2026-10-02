package com.fooddelivery.platform.testsupport;

import jakarta.annotation.security.DenyAll;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Every controller method must state who may call it (docs/09-security.md §3.1): a permission
 * check, or {@code @PermitAll} for the few public endpoints. Use from one test per service:
 *
 * <pre>{@code
 * assertThat(EndpointSecurityRules.unsecuredEndpoints("com.fooddelivery.order")).isEmpty();
 * }</pre>
 */
public final class EndpointSecurityRules {

  private static final List<Class<? extends Annotation>> ACCESS_RULES =
      List.of(
          PreAuthorize.class,
          PostAuthorize.class,
          PermitAll.class,
          DenyAll.class,
          RolesAllowed.class);

  private EndpointSecurityRules() {}

  /** Handler methods under the package without an access rule, as {@code Class#method}. */
  public static List<String> unsecuredEndpoints(String basePackage) {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));
    List<String> unsecured = new ArrayList<>();
    for (BeanDefinition candidate : scanner.findCandidateComponents(basePackage)) {
      Class<?> controller =
          ClassUtils.resolveClassName(
              candidate.getBeanClassName(), EndpointSecurityRules.class.getClassLoader());
      boolean classRule = hasAccessRule(controller);
      for (Method method : controller.getDeclaredMethods()) {
        if (AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)
            && !classRule
            && !hasAccessRule(method)) {
          unsecured.add(controller.getSimpleName() + "#" + method.getName());
        }
      }
    }
    return unsecured;
  }

  private static boolean hasAccessRule(AnnotatedElement element) {
    return ACCESS_RULES.stream()
        .anyMatch(rule -> AnnotatedElementUtils.hasAnnotation(element, rule));
  }
}
