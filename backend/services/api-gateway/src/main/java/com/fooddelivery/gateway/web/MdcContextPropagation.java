package com.fooddelivery.gateway.web;

import com.fooddelivery.platform.observability.correlation.CorrelationId;
import io.micrometer.context.ContextRegistry;
import org.slf4j.MDC;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * Restores the correlation id from the Reactor context into the MDC on whichever thread runs the
 * next operator, so gateway log lines carry it (requires spring.reactor.context-propagation=auto).
 */
@Component
class MdcContextPropagation implements InitializingBean {

  @Override
  public void afterPropertiesSet() {
    ContextRegistry.getInstance()
        .registerThreadLocalAccessor(
            CorrelationId.MDC_KEY,
            () -> MDC.get(CorrelationId.MDC_KEY),
            value -> MDC.put(CorrelationId.MDC_KEY, value),
            () -> MDC.remove(CorrelationId.MDC_KEY));
  }
}
