import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class TestSearch {
    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream("test-result.txt"), true, StandardCharsets.UTF_8));
        Socket socket = new Socket("127.0.0.1", 5223);
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        DataInputStream in = new DataInputStream(socket.getInputStream());

        send(out, 0, "{\"type\":\"handshake\",\"name\":\"ApiTest\"}".getBytes(StandardCharsets.UTF_8));
        System.out.println("[sent] handshake");
        Thread.sleep(1500);
        send(out, 0, "{\"type\":\"command\",\"text\":\"/music search 周杰伦\"}".getBytes(StandardCharsets.UTF_8));
        System.out.println("[sent] /music search 周杰伦");

        socket.setSoTimeout(20000);
        long end = System.currentTimeMillis() + 20000;
        while (System.currentTimeMillis() < end) {
            try {
                int kind = in.readUnsignedByte();
                int len = in.readInt();
                byte[] data = new byte[len];
                in.readFully(data);
                String s = new String(data, StandardCharsets.UTF_8);
                System.out.println("[recv] kind=" + kind + " len=" + len + " -> " + s);
                if (s.contains("搜索结果") || s.contains("没有找到") || s.contains("超时") || s.contains("失败")) {
                    break;
                }
            } catch (java.net.SocketTimeoutException e) {
                System.out.println("[recv] timeout, 继续等待...");
            } catch (Exception e) {
                System.out.println("[recv] end: " + e);
                break;
            }
        }
        socket.close();
    }

    static void send(DataOutputStream out, int kind, byte[] data) throws IOException {
        out.writeByte(kind);
        out.writeInt(data.length);
        out.write(data);
        out.flush();
    }
}
