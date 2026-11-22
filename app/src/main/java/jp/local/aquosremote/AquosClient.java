package jp.local.aquosremote;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** One short-lived TCP connection per command. No HTTP, no third-party SDK. */
public final class AquosClient {
    private final String host;
    private final int port;

    public AquosClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public String send(String command) throws IOException {
        final byte[] frame = AquosProtocol.frame(command);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 3000);
            socket.setSoTimeout(3000);
            try (OutputStream output = socket.getOutputStream();
                 BufferedReader input = new BufferedReader(
                         new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))) {
                output.write(frame);
                output.flush();
                String reply = input.readLine(); // AQUOS responses are CR/CRLF-terminated
                if (reply == null) throw new IOException("テレビから応答がありません");
                return reply.trim();
            }
        }
    }
}
