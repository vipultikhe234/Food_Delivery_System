package com.fooddelivery.platform.security;

import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.DeferringLoadBalancerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestTemplate;

/**
 * Token validation and method security for servlet services (docs/09-security.md §3.1). The service
 * supplies the filter chain through {@link ResourceServerSecurity}.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(JwtProperties.class)
public class ResourceServerAutoConfiguration {

  static final Duration JWKS_TIMEOUT = Duration.ofSeconds(2);

  @Bean
  @ConditionalOnMissingBean
  UserJwtConverter userJwtConverter() {
    return new UserJwtConverter();
  }

  /** Keys are fetched on the first token, so the service starts before identity-service. */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty("fdp.security.jwt.jwks-uri")
  JwtDecoder jwtDecoder(JwtProperties properties, ObjectProvider<JwksHttpInterceptor> discovery) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(JWKS_TIMEOUT);
    requestFactory.setReadTimeout(JWKS_TIMEOUT);
    RestTemplate rest = new RestTemplate(requestFactory);
    discovery.ifAvailable(interceptor -> rest.getInterceptors().add(interceptor.delegate()));
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withJwkSetUri(properties.jwksUri().toString())
            .jwsAlgorithm(SignatureAlgorithm.RS256)
            .restOperations(rest)
            .build();
    decoder.setJwtValidator(JwtValidation.validator(properties));
    return decoder;
  }

  /** Keeps Spring Cloud types out of the bean signatures above. */
  public record JwksHttpInterceptor(ClientHttpRequestInterceptor delegate) {}

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(DeferringLoadBalancerInterceptor.class)
  static class DiscoveryJwksConfiguration {

    /** Resolved per request: without a load balancer bean the URI is used as it is. */
    @Bean
    JwksHttpInterceptor jwksHttpInterceptor(
        ObjectProvider<DeferringLoadBalancerInterceptor> loadBalancer) {
      return new JwksHttpInterceptor(
          (request, body, execution) -> {
            DeferringLoadBalancerInterceptor interceptor = loadBalancer.getIfAvailable();
            return interceptor == null
                ? execution.execute(request, body)
                : interceptor.intercept(request, body, execution);
          });
    }
  }

  /** {@code @PermitAll} marks public endpoints, so every endpoint carries an explicit rule. */
  @Configuration(proxyBeanMethods = false)
  @EnableMethodSecurity(jsr250Enabled = true)
  static class MethodSecurityConfiguration {}
}
