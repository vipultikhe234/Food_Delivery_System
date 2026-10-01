package com.fooddelivery.platform.persistence.it;

import com.fooddelivery.platform.persistence.idempotency.IdempotencyService;
import com.fooddelivery.platform.persistence.money.Money;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.OncePerRequestFilter;

@SpringBootApplication
class PersistenceTestApplication {

  record CreateWidget(String name, String price) {}

  /** Stands in for the JWT filter: the caller's user id arrives in a test header. */
  @Component
  static class TestUserFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
      String user = request.getHeader("X-Test-User");
      if (user == null) {
        chain.doFilter(request, response);
        return;
      }
      Principal principal = () -> user;
      chain.doFilter(
          new HttpServletRequestWrapper(request) {
            @Override
            public Principal getUserPrincipal() {
              return principal;
            }
          },
          response);
    }
  }

  @Component
  static class Behaviour {
    final AtomicInteger executions = new AtomicInteger();
    final AtomicBoolean failNext = new AtomicBoolean();
    volatile CountDownLatch entered = new CountDownLatch(0);
    volatile CountDownLatch release = new CountDownLatch(0);
  }

  @RestController
  static class WidgetController {
    private final IdempotencyService idempotency;
    private final WidgetRepository widgets;
    private final TransactionTemplate transactions;
    private final Behaviour behaviour;

    WidgetController(
        IdempotencyService idempotency,
        WidgetRepository widgets,
        PlatformTransactionManager transactionManager,
        Behaviour behaviour) {
      this.idempotency = idempotency;
      this.widgets = widgets;
      this.transactions = new TransactionTemplate(transactionManager);
      this.behaviour = behaviour;
    }

    @PostMapping("/widgets")
    ResponseEntity<?> create(@RequestBody CreateWidget body, HttpServletRequest request) {
      return idempotency.execute(
          request,
          body,
          () -> {
            behaviour.executions.incrementAndGet();
            Widget widget = widgets.save(new Widget(body.name(), Money.inr(body.price())));
            behaviour.entered.countDown();
            await(behaviour.release);
            if (behaviour.failNext.getAndSet(false)) {
              throw new IllegalStateException("simulated failure after the insert");
            }
            return ResponseEntity.created(URI.create("/widgets/" + widget.getId()))
                .header("ETag", "\"" + widget.getVersion() + "\"")
                .body(Map.of("id", widget.getId(), "name", widget.getName()));
          });
    }

    /** Two requests that read the same version and both write: the second must get a 409. */
    @PutMapping("/widgets/{id}/conflicting-renames")
    ResponseEntity<Void> conflictingRenames(@PathVariable UUID id) {
      Widget first = transactions.execute(status -> widgets.findById(id).orElseThrow());
      Widget second = transactions.execute(status -> widgets.findById(id).orElseThrow());
      first.rename("first");
      widgets.save(first);
      second.rename("second");
      widgets.save(second);
      return ResponseEntity.noContent().build();
    }

    private static void await(CountDownLatch latch) {
      try {
        if (!latch.await(10, TimeUnit.SECONDS)) {
          throw new IllegalStateException("test latch not released");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }
    }
  }
}
