package com.example.standalone.web;

import com.google.gson.Gson;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Web 面板的 JSON 读写与统一响应（安全响应头集中在此设置）
 */
final class WebJson {
    static final Gson GSON = new Gson();

    /** 请求体上限，超过即拒绝，避免超大请求 */
    private static final int MAX_BODY = 64 * 1024;

    private WebJson() {
    }

    /**
     * 安全响应头：Web 服务可绑定 0.0.0.0，这些是必需的兜底防护
     */
    static void applySecurityHeaders(HttpExchange exchange) {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Security-Policy",
                "default-src 'self'; script-src 'self'; img-src 'self' http: https: data:; "
                        + "connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Frame-Options", "DENY");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("Cache-Control", "no-store");
    }

    static void sendJson(HttpExchange exchange, int code, Object body) throws IOException {
        byte[] data = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        applySecurityHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(code, data.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(data);
        }
    }

    static void sendError(HttpExchange exchange, int code, String message) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        sendJson(exchange, code, body);
    }

    /**
     * 读取 JSON 请求体；超过上限或解析失败返回 null
     */
    static <T> T readBody(HttpExchange exchange, Class<T> type) throws IOException {
        byte[] data;
        try (InputStream in = exchange.getRequestBody()) {
            data = in.readNBytes(MAX_BODY + 1);
        }
        if (data.length > MAX_BODY) {
            return null;
        }
        try {
            return GSON.fromJson(new String(data, StandardCharsets.UTF_8), type);
        } catch (Exception e) {
            return null;
        }
    }
}