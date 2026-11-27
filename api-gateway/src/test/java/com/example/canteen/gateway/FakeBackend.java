package com.example.canteen.gateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Stands in for all the services behind the gateway. By default it answers 200 with the
 * request path (and query) it received, plus the X-Forwarded-Prefix header it got.
 * {@link #serveJson} adds a fixed JSON answer for one path, for example a JWKS.
 */
final class FakeBackend {

    private final HttpServer server;

    FakeBackend() {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            String seen = exchange.getRequestURI().getRawPath() + (query == null ? "" : "?" + query);
            String prefix = exchange.getRequestHeaders().getFirst("X-Forwarded-Prefix");
            exchange.getResponseHeaders().set("X-Seen-Forwarded-Prefix", prefix == null ? "none" : prefix);
            respond(exchange, "text/plain", seen);
        });
        server.start();
    }

    String url() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    void serveJson(String path, String json) {
        server.createContext(path, exchange -> respond(exchange, "application/json", json));
    }

    void stop() {
        server.stop(0);
    }

    private static void respond(HttpExchange exchange, String contentType, String text) throws IOException {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
