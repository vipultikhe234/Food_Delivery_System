package com.fooddelivery.platform.observability;

import com.fooddelivery.platform.observability.correlation.CorrelationIdFilter;
import com.fooddelivery.platform.observability.correlation.CorrelationIdPropagatingInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** Registers correlation-id handling for servlet services and their outgoing REST calls. */
@AutoConfiguration
public class ObservabilityAutoConfiguration {

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
  @ConditionalOnClass(name = "jakarta.servlet.Filter")
  static class ServletCorrelationConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "correlationIdFilterRegistration")
    FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegistration() {
      FilterRegistrationBean<CorrelationIdFilter> registration =
          new FilterRegistrationBean<>(new CorrelationIdFilter());
      registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
      registration.addUrlPatterns("/*");
      return registration;
    }
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(RestClientCustomizer.class)
  static class RestClientCorrelationConfiguration {

    @Bean
    RestClientCustomizer correlationIdRestClientCustomizer() {
      return builder -> builder.requestInterceptor(new CorrelationIdPropagatingInterceptor());
    }
  }
}
