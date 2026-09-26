package com.example.standalone.web;

import com.example.standalone.ConfigCatalog;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 路由：{@code /api/*} 走接口（公开只读 + 管理员写操作），其余路径返回 jar 内的静态页面。
 */
final class WebRouter implements HttpHandler {
    private final WebServer server;

    WebRouter(WebServer server) {
        this.server = server;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            if (path == null) {
                WebJson.sendError(exchange, 404, "未找到");
                return;
            }
            if (path.startsWith("/api/")) {
                if (!"GET".equals(method) && !"POST".equals(method)) {
                    WebJson.sendError(exchange, 405, "不支持的请求方法");
                    return;
                }
                handleApi(exchange, path, method);
                return;
            }
            if (!"GET".equals(method)) {
                WebJson.sendError(exchange, 405, "不支持的请求方法");
                return;
            }
            serveStatic(exchange, path);
        } catch (Exception e) {
            try {
                WebJson.sendError(exchange, 500, "服务端内部错误");
            } catch (Exception ignored) {
            }
        } finally {
            exchange.close();
        }
    }

    // ---------- 接口 ----------

    private void handleApi(HttpExchange exchange, String path, String method) throws IOException {
        switch (path) {
            case "/api/public/status" -> {
                if (requireGet(exchange, method)) {
                    WebJson.sendJson(exchange, 200, WebApi.status());
                }
            }
            case "/api/public/queue" -> {
                if (requireGet(exchange, method)) {
                    WebJson.sendJson(exchange, 200, WebApi.queue());
                }
            }
            case "/api/public/players" -> {
                if (requireGet(exchange, method)) {
                    WebJson.sendJson(exchange, 200, WebApi.players());
                }
            }
            case "/api/public/stats" -> {
                if (requireGet(exchange, method)) {
                    WebJson.sendJson(exchange, 200, WebApi.stats(limit(exchange, 50)));
                }
            }
            case "/api/public/perf" -> {
                if (requireGet(exchange, method)) {
                    WebJson.sendJson(exchange, 200, WebApi.perf());
                }
            }
            case "/api/public/lyric" -> {
                if (requireGet(exchange, method)) {
                    WebJson.sendJson(exchange, 200, WebApi.lyric());
                }
            }
            case "/api/login" -> {
                if (requirePost(exchange, method)) {
                    login(exchange);
                }
            }
            default -> handleAdmin(exchange, path, method);
        }
    }

    private void handleAdmin(HttpExchange exchange, String path, String method) throws IOException {
        WebAuth auth = server.auth();
        if (auth == null) {
            WebJson.sendError(exchange, 403, "Web 面板未启用");
            return;
        }
        String token = bearer(exchange);
        if (!auth.hasPassword()) {
            WebJson.sendError(exchange, 403, "尚未设置管理员密码，请在服务端设置页设置后再登录");
            return;
        }
        if (!auth.valid(token)) {
            WebJson.sendError(exchange, 401, "未登录或登录已过期");
            return;
        }

        switch (path) {
            case "/api/me" -> {
                if (requireGet(exchange, method)) {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("admin", true);
                    WebJson.sendJson(exchange, 200, body);
                }
            }
            case "/api/logout" -> {
                if (requirePost(exchange, method)) {
                    auth.logout(token);
                    ok(exchange, "");
                }
            }
            case "/api/admin/bans" -> {
                if (requireGet(exchange, method)) {
                    WebJson.sendJson(exchange, 200, WebApi.bans());
                }
            }
            case "/api/admin/logs" -> {
                if (requireGet(exchange, method)) {
                    WebJson.sendJson(exchange, 200,
                            WebApi.logs(number(exchange, "since", 0), (int) number(exchange, "limit", 200)));
                }
            }
            case "/api/admin/config" -> {
                if ("GET".equals(method)) {
                    WebJson.sendJson(exchange, 200, WebApi.config());
                } else if ("POST".equals(method)) {
                    configWrite(exchange);
                } else {
                    WebJson.sendError(exchange, 405, "请使用 GET 或 POST");
                }
            }
            case "/api/admin/password" -> {
                if (requirePost(exchange, method)) {
                    setPassword(exchange);
                }
            }
            case "/api/admin/next" -> {
                if (requirePost(exchange, method)) {
                    action(exchange, "next");
                }
            }
            case "/api/admin/queue/delete" -> {
                if (requirePost(exchange, method)) {
                    IndexBody body = WebJson.readBody(exchange, IndexBody.class);
                    if (body == null || body.index <= 0) {
                        WebJson.sendError(exchange, 400, "参数错误：index 必须为正整数");
                        return;
                    }
                    action(exchange, "delete " + body.index);
                }
            }
            case "/api/admin/music/ban", "/api/admin/music/unban" -> {
                if (requirePost(exchange, method)) {
                    IdBody body = WebJson.readBody(exchange, IdBody.class);
                    if (body == null || !safeArgument(body.id, 64)) {
                        WebJson.sendError(exchange, 400, "参数错误：id 非法");
                        return;
                    }
                    action(exchange, path.endsWith("unban") ? "unban " + body.id : "ban " + body.id);
                }
            }
            case "/api/admin/player/ban", "/api/admin/player/unban" -> {
                if (requirePost(exchange, method)) {
                    NameBody body = WebJson.readBody(exchange, NameBody.class);
                    if (body == null || !safeArgument(body.name, 16)) {
                        WebJson.sendError(exchange, 400, "参数错误：name 非法");
                        return;
                    }
                    action(exchange, path.endsWith("unban")
                            ? "unbanplayer " + body.name : "banplayer " + body.name);
                }
            }
            case "/api/admin/command" -> {
                if (requirePost(exchange, method)) {
                    CommandBody body = WebJson.readBody(exchange, CommandBody.class);
                    if (body == null || body.command == null || body.command.isBlank()) {
                        WebJson.sendError(exchange, 400, "参数错误：命令为空");
                        return;
                    }
                    if (!WebCommands.allowed(body.command)) {
                        WebJson.sendError(exchange, 403, "该命令不被 Web 面板允许");
                        return;
                    }
                    action(exchange, body.command);
                }
            }
            default -> WebJson.sendError(exchange, 404, "未找到");
        }
    }

    private void login(HttpExchange exchange) throws IOException {
        WebAuth auth = server.auth();
        if (auth == null) {
            WebJson.sendError(exchange, 403, "Web 面板未启用");
            return;
        }
        LoginBody body = WebJson.readBody(exchange, LoginBody.class);
        if (body == null) {
            WebJson.sendError(exchange, 413, "请求体过大或格式错误");
            return;
        }
        String[] out = new String[1];
        int code = auth.login(body.password, clientIp(exchange), out);
        switch (code) {
            case WebAuth.OK -> {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("token", out[0]);
                WebJson.sendJson(exchange, 200, result);
            }
            case WebAuth.NO_PASSWORD ->
                    WebJson.sendError(exchange, 403, "尚未设置管理员密码，请在服务端设置页设置");
            case WebAuth.LOCKED -> WebJson.sendError(exchange, 429, "登录失败次数过多，请 5 分钟后再试");
            default -> WebJson.sendError(exchange, 401, "密码错误");
        }
    }

    /** 写入单个配置项并落盘（配置项清单与控制台 server config 指令共用 ConfigCatalog） */
    private void configWrite(HttpExchange exchange) throws IOException {
        ConfigBody body = WebJson.readBody(exchange, ConfigBody.class);
        if (body == null || body.path == null || body.path.isBlank()) {
            WebJson.sendError(exchange, 400, "参数错误：path 不能为空");
            return;
        }
        ConfigCatalog.Item item = ConfigCatalog.find(body.path);
        if (item == null) {
            WebJson.sendError(exchange, 404, "没有这个配置项：" + body.path);
            return;
        }
        String error = ConfigCatalog.write(item.path, body.value);
        if (error != null) {
            WebJson.sendError(exchange, 400, error);
            return;
        }
        ConfigCatalog.saveAll();
        ok(exchange, item.cn + "（" + item.path + "）已保存为 " + body.value
                + (item.path.startsWith("standalone.") ? "，该配置需重启服务端后生效" : ""));
    }

    /**
     * 设置或清除 Web 管理员密码
     * <p>
     * 两者都会作废所有登录会话（包括发起本次请求的这个），因此返回文案里明确提示需要重新登录。
     */
    private void setPassword(HttpExchange exchange) throws IOException {
        WebAuth auth = server.auth();
        if (auth == null) {
            WebJson.sendError(exchange, 403, "Web 面板未启用");
            return;
        }
        PasswordBody body = WebJson.readBody(exchange, PasswordBody.class);
        if (body == null) {
            WebJson.sendError(exchange, 413, "请求体过大或格式错误");
            return;
        }
        String password = body.password == null ? "" : body.password.trim();
        if (password.isEmpty()) {
            auth.clearPassword();
            ok(exchange, "已清除管理员密码，Web 管理功能已关闭");
            return;
        }
        if (password.length() < 4) {
            WebJson.sendError(exchange, 400, "密码至少 4 位");
            return;
        }
        auth.setPassword(password.toCharArray());
        ok(exchange, "管理员密码已更新，所有登录会话已失效，请重新登录");
    }

    /** 执行管理指令并返回服务端输出 */
    private void action(HttpExchange exchange, String command) throws IOException {
        if (!WebCommands.allowed(command)) {
            WebJson.sendError(exchange, 403, "该命令不被 Web 面板允许");
            return;
        }
        String output = WebCommands.exec(command);
        ok(exchange, output);
    }

    private void ok(HttpExchange exchange, String output) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("output", output == null ? "" : output);
        WebJson.sendJson(exchange, 200, body);
    }

    // ---------- 静态资源 ----------

    private void serveStatic(HttpExchange exchange, String path) throws IOException {
        String name = path.isEmpty() || path.equals("/") ? "index.html" : path.substring(1);
        if (name.contains("..") || name.contains("\\") || name.startsWith("/")) {
            WebJson.sendError(exchange, 404, "未找到");
            return;
        }
        byte[] data = server.resource("/web/" + name);
        if (data == null) {
            WebJson.sendError(exchange, 404, "未找到");
            return;
        }
        String etag = "\"" + Integer.toHexString(Arrays.hashCode(data)) + "-" + data.length + "\"";
        WebJson.applySecurityHeaders(exchange);
        exchange.getResponseHeaders().set("ETag", etag);
        if (etag.equals(exchange.getRequestHeaders().getFirst("If-None-Match"))) {
            exchange.sendResponseHeaders(304, -1);
            return;
        }
        exchange.getResponseHeaders().set("Content-Type", contentType(name));
        exchange.sendResponseHeaders(200, data.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(data);
        }
    }

    private static String contentType(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (lower.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        if (lower.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        return "application/octet-stream";
    }

    // ---------- 请求辅助 ----------

    private static boolean requireGet(HttpExchange exchange, String method) throws IOException {
        if (!"GET".equals(method)) {
            WebJson.sendError(exchange, 405, "请使用 GET");
            return false;
        }
        return true;
    }

    private static boolean requirePost(HttpExchange exchange, String method) throws IOException {
        if (!"POST".equals(method)) {
            WebJson.sendError(exchange, 405, "请使用 POST");
            return false;
        }
        return true;
    }

    private static String bearer(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null) {
            return null;
        }
        // 大小写不敏感地匹配 Bearer
        if (header.length() < 8 || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        return header.substring(7).trim();
    }

    private static String clientIp(HttpExchange exchange) {
        try {
            return exchange.getRemoteAddress().getAddress().getHostAddress();
        } catch (Exception e) {
            return "";
        }
    }

    private static int limit(HttpExchange exchange, int fallback) {
        int value = (int) number(exchange, "limit", fallback);
        return Math.max(1, Math.min(200, value));
    }

    private static long number(HttpExchange exchange, String key, long fallback) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null || query.isEmpty()) {
            return fallback;
        }
        for (String part : query.split("&")) {
            int eq = part.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            if (!part.substring(0, eq).equals(key)) {
                continue;
            }
            try {
                return Long.parseLong(java.net.URLDecoder.decode(part.substring(eq + 1),
                        java.nio.charset.StandardCharsets.UTF_8));
            } catch (Exception ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    /** 参数白名单：仅允许字母数字与 _ - . ，避免命令拼接被注入额外参数 */
    private static boolean safeArgument(String value, int maxLength) {
        if (value == null || value.isEmpty() || value.length() > maxLength) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '-' && c != '.') {
                return false;
            }
        }
        return true;
    }

    // ---------- 请求体 ----------

    private static final class LoginBody {
        String password;
    }

    private static final class IndexBody {
        int index;
    }

    private static final class IdBody {
        String id;
    }

    private static final class NameBody {
        String name;
    }

    private static final class CommandBody {
        String command;
    }

    private static final class ConfigBody {
        String path;
        String value;
    }

    private static final class PasswordBody {
        String password;
    }
}