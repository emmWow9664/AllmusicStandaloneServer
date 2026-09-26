package com.example.standalone.web;

import com.coloryr.allmusic.server.core.AllMusic;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 内嵌 Web 展示与管理面板。
 * <p>
 * 使用 JDK 自带的 {@link HttpServer}，不引入任何第三方 Web 框架；
 * 页面资源随 ShadowJar 打包，运行时从 classpath 读取。
 */
public final class WebServer {
    public static final WebServer INSTANCE = new WebServer();

    /** 需要预加载的静态资源（bg.png 是面板壁纸） */
    private static final String[] STATIC_FILES = {"index.html", "app.js", "style.css", "bg.png"};

    private final Map<String, byte[]> resources = new ConcurrentHashMap<>();
    private HttpServer server;
    private ExecutorService executor;
    private volatile WebAuth auth;
    private volatile boolean running;
    private static volatile int currentPort = 0;

    private WebServer() {
    }

    public static String getPortText() {
        return currentPort == 0 ? "未启动" : String.valueOf(currentPort);
    }

    public boolean isRunning() {
        return running;
    }

    public WebAuth auth() {
        return auth;
    }

    /**
     * 获取（必要时创建）管理员凭据管理器。
     * <p>
     * Web 服务未启用时，设置界面仍可通过它设置/清除管理员密码。
     */
    public WebAuth auth(File dataDir) {
        WebAuth current = auth;
        if (current == null) {
            synchronized (this) {
                if (auth == null) {
                    auth = new WebAuth(dataDir);
                }
                current = auth;
            }
        }
        return current;
    }

    /** 界面展示用的访问地址 */
    public String getUrlText() {
        int port = currentPort;
        return port == 0 ? "未启动" : "http://127.0.0.1:" + port + "/";
    }

    /** 读取预加载的静态资源，未命中返回 null */
    public byte[] resource(String path) {
        return resources.get(path);
    }

    /**
     * 启动监听；端口占用等失败由调用方处理（不应影响服务端其它功能）
     */
    public synchronized void start(String host, int port, File dataDir) throws IOException {
        stop();
        auth = new WebAuth(dataDir);
        loadResources();
        HttpServer http = HttpServer.create(new InetSocketAddress(host, port), 0);
        http.createContext("/", new WebRouter(this));
        executor = Executors.newVirtualThreadPerTaskExecutor();
        http.setExecutor(executor);
        http.start();
        server = http;
        running = true;
        currentPort = port;
        WebApi.startLogCapture();
        log("<light_purple>[AllMusic]<yellow>Web 面板已启动：" + getUrlText() + bindHint(host));
    }

    /**
     * 停止监听并释放端口
     */
    public synchronized void stop() {
        running = false;
        WebApi.stopLogCapture();
        if (server != null) {
            try {
                server.stop(0);
            } catch (Exception ignored) {
            }
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        currentPort = 0;
    }

    private void loadResources() {
        resources.clear();
        for (String name : STATIC_FILES) {
            String path = "/web/" + name;
            try (InputStream in = WebServer.class.getResourceAsStream(path)) {
                if (in == null) {
                    log("<light_purple>[AllMusic]<red>Web 资源缺失：" + path);
                    continue;
                }
                resources.put(path, in.readAllBytes());
            } catch (Exception e) {
                log("<light_purple>[AllMusic]<red>Web 资源读取失败：" + path);
            }
        }
    }

    private static String bindHint(String host) {
        if (host == null || host.isEmpty() || "0.0.0.0".equals(host) || "::".equals(host)) {
            return "（已绑定所有网卡，局域网内可用本机 IP 访问）";
        }
        return "";
    }

    private static void log(String message) {
        try {
            AllMusic.log.data(message);
        } catch (Exception ignored) {
        }
    }
}