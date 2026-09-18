import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class TestClient {
    public static void main(String[] args) throws Exception {
        Socket socket = new Socket("127.0.0.1", 5223);
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        DataInputStream in = new DataInputStream(socket.getInputStream());

        // 1. handshake
        send(out, 0, "{\"type\":\"handshake\",\"name\":\"TestPlayer\"}".getBytes(StandardCharsets.UTF_8));
        System.out.println("[sent] handshake");

        // 2. wait for server to respond with HUD_DATA (should come within a few seconds)
        socket.setSoTimeout(8000);
        try {
            int kind = in.readUnsignedByte();
            int len = in.readInt();
            byte[] data = new byte[len];
            in.readFully(data);
            System.out.println("[recv] kind=" + kind + " len=" + len + " payload=" + new String(data, StandardCharsets.UTF_8).substring(0, Math.min(120, len)));
        } catch (Exception e) {
            System.out.println("[recv] timeout/no data: " + e);
        }

        // 3. send a command
        send(out, 0, "{\"type\":\"command\",\"text\":\"/music help\"}".getBytes(StandardCharsets.UTF_8));
        System.out.println("[sent] /music help");

        // read a few packets
        for (int i = 0; i < 3; i++) {
            try {
                int kind = in.readUnsignedByte();
                int len = in.readInt();
                byte[] data = new byte[len];
                in.readFully(data);
                String s = new String(data, StandardCharsets.UTF_8);
                System.out.println("[recv] kind=" + kind + " len=" + len + " payload=" + s.substring(0, Math.min(160, len)));
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
