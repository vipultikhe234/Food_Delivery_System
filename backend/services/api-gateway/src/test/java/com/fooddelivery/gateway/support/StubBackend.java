package com.fooddelivery.gateway.support;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Plays identity-service (JWKS) and any routed service: other paths answer 200 with the request
 * line and headers it received, one {@code name: value} per line (names lower-cased).
 */
public final class StubBackend implements AutoCloseable {

  private final HttpServer server;

  public StubBackend(RSAKey signingKey) throws IOException {
    this(signingKey, "stub-1");
  }

  public StubBackend(RSAKey signingKey, String instanceName) throws IOException {
    String jwks = new JWKSet(signingKey.toPublicJWK()).toString();
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/.well-known/jwks.json", ex -> respond(ex, "application/json", jwks));
    server.createContext(
        "/",
        ex -> {
          ex.getResponseHeaders().set("X-Stub-Instance", instanceName);
          respond(ex, "text/plain", echo(ex));
        });
    server.start();
  }

  public String baseUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  @Override
  public void close() {
    server.stop(0);
  }

  private static String echo(HttpExchange exchange) {
    StringBuilder body = new StringBuilder();
    body.append("request: ")
        .append(exchange.getRequestMethod())
        .append(' ')
        .append(exchange.getRequestURI().getRawPath())
        .append('\n');
    Map<String, List<String>> sorted = new TreeMap<>();
    exchange
        .getRequestHeaders()
        .forEach((name, values) -> sorted.put(name.toLowerCase(Locale.ROOT), values));
    sorted.forEach(
        (name, values) ->
            body.append(name).append(": ").append(String.join(",", values)).append('\n'));
    return body.toString();
  }

  private static void respond(HttpExchange exchange, String contentType, String body)
      throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", contentType);
    exchange.sendResponseHeaders(200, bytes.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(bytes);
    }
  }
}
