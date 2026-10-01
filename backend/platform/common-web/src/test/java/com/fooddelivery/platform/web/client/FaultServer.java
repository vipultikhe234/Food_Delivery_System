package com.fooddelivery.platform.web.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Scriptable HTTP server for fault injection: each request takes the next scripted reply. */
final class FaultServer implements AutoCloseable {

  record Reply(int status, Duration delay) {
    static Reply ok() {
      return new Reply(200, Duration.ZERO);
    }

    static Reply status(int status) {
      return new Reply(status, Duration.ZERO);
    }

    static Reply slow(Duration delay) {
      return new Reply(200, delay);
    }
  }

  private final HttpServer server;
  private final Queue<Reply> script = new ConcurrentLinkedQueue<>();
  private final AtomicInteger requests = new AtomicInteger();
  private volatile Reply fallback = Reply.ok();

  FaultServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    server.createContext("/", this::handle);
    server.start();
  }

  FaultServer then(Reply... replies) {
    script.addAll(java.util.List.of(replies));
    return this;
  }

  FaultServer always(Reply reply) {
    fallback = reply;
    return this;
  }

  int requests() {
    return requests.get();
  }

  String baseUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  private void handle(HttpExchange exchange) throws IOException {
    requests.incrementAndGet();
    exchange.getRequestBody().readAllBytes();
    Reply reply = script.poll();
    if (reply == null) {
      reply = fallback;
    }
    try {
      Thread.sleep(reply.delay());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    byte[] body = ("reply " + reply.status()).getBytes(StandardCharsets.UTF_8);
    try {
      exchange.sendResponseHeaders(reply.status(), body.length);
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(body);
      }
    } catch (IOException e) {
      // the client gave up (read timeout); nothing to send
    } finally {
      exchange.close();
    }
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
