package com.example.standalone;

import com.coloryr.allmusic.server.core.AllMusic;

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
