package com.example.standalone;

import com.coloryr.allmusic.server.core.AllMusic;
import com.coloryr.allmusic.server.core.side.IAllMusicLogger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 日志实现：输出到控制台、写入文件并转发到 GUI。
 * <p>
 * 关键点：
 * - 保留历史日志，GUI 控制台创建较晚（核心启动之后）时能补全启动日志；
 * - 接管 System.out / System.err，使 SLF4J 等直接打印的第三方输出也能进入控制台。
 */
public class LogStandalone implements IAllMusicLogger {
    public static final LogStandalone INSTANCE = new LogStandalone();

    /** 原始标准输出/错误，用于避免接管后递归 */
    private static final PrintStream ORIGINAL_OUT = System.out;
    private static final PrintStream ORIGINAL_ERR = System.err;

    /** 历史日志上限（GUI 创建前产生的日志需要回放） */
    private static final int HISTORY_LIMIT = 1000;

    private final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();
    private final Deque<String> history = new ArrayDeque<>();
    private final SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss");
    private PrintWriter fileWriter;

    private LogStandalone() {
        try {
            // 日志目录与其它数据一样固定在服务端所在文件夹，避免双击启动时落到工作目录
            File dir = new File(Main.getBaseDir(), AllMusic.SERVER_DIR);
            dir.mkdirs();
            fileWriter = new PrintWriter(new OutputStreamWriter(
                    new FileOutputStream(new File(dir, "latest.log"), true),
                    StandardCharsets.UTF_8), true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 接管标准输出与标准错误，使第三方库（如 SLF4J）的输出也能显示在 GUI 控制台。
     * 需在启动早期调用。
     */
    public static void installStreamCapture() {
        System.setOut(new PrintStream(new TeeStream(ORIGINAL_OUT), true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new TeeStream(ORIGINAL_ERR), true, StandardCharsets.UTF_8));
    }

    /**
     * 注册日志监听（GUI 使用），并立即回放已产生的历史日志
     */
    public void addListener(Consumer<String> listener) {
        listeners.add(listener);
        for (String line : historySnapshot()) {
            try {
                listener.accept(line);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 注销日志监听
     */
    public void removeListener(Consumer<String> listener) {
        listeners.remove(listener);
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
     * 追加一行带时间戳的日志
     */
    public void append(String text) {
        String line = "[" + format.format(new Date()) + "] " + text;
        ORIGINAL_OUT.println(line);
        publish(line);
    }

    /**
     * 追加一行已由标准输出/错误打印过的日志（不再重复打印，仅写文件并转发 GUI）
     */
    public void appendRaw(String line) {
        publish(line);
    }

    private void publish(String line) {
        if (fileWriter != null) {
            fileWriter.println(line);
        }
        synchronized (history) {
            history.addLast(line);
            while (history.size() > HISTORY_LIMIT) {
                history.removeFirst();
            }
        }
        for (Consumer<String> listener : listeners) {
            try {
                listener.accept(line);
            } catch (Exception ignored) {
            }
        }
    }

    private List<String> historySnapshot() {
        synchronized (history) {
            return new ArrayList<>(history);
        }
    }

    /**
     * 透传输出流：内容原样写回原始流，同时按行转发到日志（GUI 控制台）
     */
    private static final class TeeStream extends OutputStream {
        private final OutputStream original;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        TeeStream(OutputStream original) {
            this.original = original;
        }

        @Override
        public void write(int b) throws IOException {
            original.write(b);
            feed(new byte[]{(byte) b}, 0, 1);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            original.write(b, off, len);
            feed(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            original.flush();
        }

        /** 按行切分并转发 */
        private void feed(byte[] b, int off, int len) {
            synchronized (buffer) {
                for (int i = 0; i < len; i++) {
                    byte c = b[off + i];
                    if (c == '\n') {
                        emit();
                    } else if (c != '\r') {
                        buffer.write(c);
                    }
                }
                if (buffer.size() > 16384) {
                    buffer.reset();
                }
            }
        }

        private void emit() {
            String line = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
            buffer.reset();
            if (!line.isEmpty()) {
                INSTANCE.appendRaw(line);
            }
        }
    }
}