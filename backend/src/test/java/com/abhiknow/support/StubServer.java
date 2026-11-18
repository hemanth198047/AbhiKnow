package com.abhiknow.support;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/** Local HTTP server standing in for a provider. Records requests so tests can assert on URL translation. */
public final class StubServer implements AutoCloseable {
    public record Response(int status, String body, String contentType) {
        public static Response json(String body) { return new Response(200, body, "application/json"); }
        public static Response xml(String body) { return new Response(200, body, "application/atom+xml"); }
        public static Response status(int s) { return new Response(s, "{}", "application/json"); }
    }

    public record Seen(String pathAndQuery, String userAgent) {}

    private final HttpServer server;
    public final List<Seen> requests = new CopyOnWriteArrayList<>();

    public StubServer(Function<String, Response> handler) {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/", ex -> {
            String pq = ex.getRequestURI().getRawPath() + (ex.getRequestURI().getRawQuery() == null ? "" : "?" + ex.getRequestURI().getRawQuery());
            requests.add(new Seen(pq, ex.getRequestHeaders().getFirst("User-Agent")));
            Response r = handler.apply(pq);
            byte[] b = r.body().getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", r.contentType());
            ex.sendResponseHeaders(r.status(), b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.start();
    }

    public String url() { return "http://127.0.0.1:" + server.getAddress().getPort(); }

    @Override public void close() { server.stop(0); }
}
