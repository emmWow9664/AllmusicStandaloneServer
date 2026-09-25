package com.example.standalone;

import com.coloryr.allmusic.server.core.AllMusic;
import jdk.net.ExtendedSocketOptions;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * TCP 服务端：接受客户端模组的连接
 */
public class MusicServer {
    public static final MusicServer INSTANCE = new MusicServer();

    private ServerSocket serverSocket;
    private volatile boolean running;
    private static volatile int currentPort = 0;

    public static String getPortText() {
        return currentPort == 0 ? "未启动" : String.valueOf(currentPort);
    }

    private final ExecutorService acceptExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "allmusic-accept");
        t.setDaemon(true);
        return t;
    });

    private MusicServer() {
    }

    /**
     * 启动监听
     */
    public void start(String host, int port) throws IOException {
        stop();
        ServerSocket socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(host, port));
        serverSocket = socket;
        running = true;
        currentPort = port;
        acceptExecutor.execute(this::acceptLoop);
        AllMusic.log.data("<light_purple>[AllMusic]<yellow>已监听端口：" + port);
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                socket.setSoTimeout(0);
                enableKeepAlive(socket);
                new ClientSession(socket).start();
            } catch (Exception e) {
                if (running) {
                    AllMusic.log.data("<light_purple>[AllMusic]<red>接受连接出错");
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 开启 TCP 保活并缩短探测间隔（空闲 20 秒开始探测，每 5 秒一次，连续 3 次无响应判定断开）。
     * <p>
     * 客户端异常掉线（断网、断电、NAT/路由器回收连接）时 TCP 层收不到 FIN/RST，
     * 会话读线程会一直阻塞在 read 上、永远不注销，服务端就会一直显示该玩家在线。
     * 保活探测失败后由内核报错使读线程退出，从而正常注销会话。
     */
    private void enableKeepAlive(Socket socket) {
        try {
            socket.setKeepAlive(true);
            socket.setOption(ExtendedSocketOptions.TCP_KEEPIDLE, 20);
            socket.setOption(ExtendedSocketOptions.TCP_KEEPINTERVAL, 5);
            socket.setOption(ExtendedSocketOptions.TCP_KEEPCOUNT, 3);
        } catch (Exception e) {
            // 部分平台/内核不支持自定义保活参数，退回系统默认保活（间隔较长但同样能探测）
            AllMusic.log.data("<light_purple>[AllMusic]<yellow>当前平台不支持自定义 TCP 保活参数，使用系统默认值");
        }
    }

    /**
     * 停止监听
     */
    public void stop() {
        running = false;
        currentPort = 0;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (Exception ignored) {
            }
            serverSocket = null;
        }
    }

    public boolean isRunning() {
        return running;
    }
}
