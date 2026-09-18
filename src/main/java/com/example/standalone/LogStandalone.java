package com.example.standalone;

import com.coloryr.allmusic.server.core.side.IAllMusicLogger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 日志实现：输出到控制台、写入文件并转发到 GUI
 */
public class LogStandalone implements IAllMusicLogger {
    public static final LogStandalone INSTANCE = new LogStandalone();

    private final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();
    private final SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss");
    private PrintWriter fileWriter;

    private LogStandalone() {
        try {
            File dir = new File("allmusic_server");
            dir.mkdirs();
            fileWriter = new PrintWriter(new OutputStreamWriter(
                    new FileOutputStream(new File(dir, "latest.log"), true),
                    StandardCharsets.UTF_8), true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 注册日志监听（GUI 使用）
     */
    public void addListener(Consumer<String> listener) {
        listeners.add(listener);
    }

    @Override
    public void data(String data) {
        append(data);
    }

    @Override
    public void data(Component data) {
        String text = MiniMessage.miniMessage().serialize(data);
        append(text);
    }

    /**
     * 追加一行日志
     */
    public void append(String text) {
        String line = "[" + format.format(new Date()) + "] " + text;
        System.out.println(line);
        if (fileWriter != null) {
            fileWriter.println(line);
        }
        for (Consumer<String> listener : listeners) {
            try {
                listener.accept(line);
            } catch (Exception ignored) {
            }
        }
    }
}
