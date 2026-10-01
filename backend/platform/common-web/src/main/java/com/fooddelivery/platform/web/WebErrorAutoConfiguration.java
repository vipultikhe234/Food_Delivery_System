package com.fooddelivery.platform.web;

import com.fooddelivery.platform.web.error.ConcurrencyExceptionHandler;
import com.fooddelivery.platform.web.error.GlobalExceptionHandler;
import com.fooddelivery.platform.web.error.ProblemDetails;
import com.fooddelivery.platform.web.error.ProblemErrorController;
import com.fooddelivery.platform.web.error.SecurityExceptionHandler;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.SearchStrategy;
import org.springframework.boot.webmvc.autoconfigure.error.ErrorMvcAutoConfiguration;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers the platform error format for servlet services (REQ-PLAT-004). */
@AutoConfiguration(before = ErrorMvcAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class WebErrorAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  ProblemDetails problemDetails() {
    return new ProblemDetails(Clock.systemUTC());
  }

  @Bean
  GlobalExceptionHandler globalExceptionHandler(ProblemDetails problems) {
    return new GlobalExceptionHandler(problems);
  }

  @Bean
  @ConditionalOnMissingBean(value = ErrorController.class, search = SearchStrategy.CURRENT)
  ProblemErrorController problemErrorController(ProblemDetails problems) {
    return new ProblemErrorController(problems);
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = "org.springframework.dao.OptimisticLockingFailureException")
  static class ConcurrencyErrorConfiguration {

    @Bean
    ConcurrencyExceptionHandler concurrencyExceptionHandler(ProblemDetails problems) {
      return new ConcurrencyExceptionHandler(problems);
    }
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = "org.springframework.security.access.AccessDeniedException")
  static class SecurityErrorConfiguration {

    @Bean
    SecurityExceptionHandler securityExceptionHandler(ProblemDetails problems) {
      return new SecurityExceptionHandler(problems);
    }
  }
}
