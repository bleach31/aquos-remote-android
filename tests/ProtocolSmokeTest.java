import jp.local.aquosremote.AquosClient;
import jp.local.aquosremote.AquosProtocol;
import jp.local.aquosremote.KidCommandPolicy;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

public final class ProtocolSmokeTest {
    private static void check(boolean value, String description) {
        if (!value) throw new AssertionError(description);
    }

    public static void main(String[] args) throws Exception {
        check(Arrays.equals(AquosProtocol.frame("VOLM20"), "VOLM20  \r".getBytes(StandardCharsets.US_ASCII)), "VOLM framing");
        check(Arrays.equals(AquosProtocol.frame("CHUP"), "CHUP    \r".getBytes(StandardCharsets.US_ASCII)), "CHUP framing");
        check(Arrays.equals(AquosProtocol.frame("volm????"), "VOLM????\r".getBytes(StandardCharsets.US_ASCII)), "query framing");
        check(AquosProtocol.parsedVolume("20") == 20, "volume parse");
        check(AquosProtocol.parsedVolume("ERR") == -1, "ERR parsing");
        try {
            AquosProtocol.frame("POWR12345");
            throw new AssertionError("Should reject overlength frame");
        } catch (IllegalArgumentException expected) { }

        check(KidCommandPolicy.allowed("VOLM25"), "kid allows 25");
        check(!KidCommandPolicy.allowed("VOLM26"), "kid rejects 26");
        check(!KidCommandPolicy.allowed("VOLM99"), "kid rejects 99");
        check(!KidCommandPolicy.allowed("VOLM????"), "kid rejects raw query");
        check(!KidCommandPolicy.allowed("RCKY33"), "kid rejects volume-up remote key");
        check(KidCommandPolicy.allowed("RCKY40"), "kid allows OK remote key");
        check(!KidCommandPolicy.allowed("POWR    "), "kid rejects unknown power parameter");
        try (ServerSocket server = new ServerSocket(0)) {
            AtomicReference<String> received = new AtomicReference<>();
            Thread mockTv = new Thread(() -> {
                try (Socket socket = server.accept()) {
                    StringBuilder s = new StringBuilder();
                    int ch;
                    while ((ch = socket.getInputStream().read()) != -1 && ch != '\r') {
                        s.append((char) ch);
                    }
                    received.set(s.toString());
                    socket.getOutputStream().write("OK\r".getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().flush();
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            mockTv.start();
            String reply = new AquosClient("127.0.0.1", server.getLocalPort()).send("VOLM20");
            mockTv.join(2000);
            check("OK".equals(reply), "response handling");
            check("VOLM20  ".equals(received.get()), "TCP bytes");
        }
        System.out.println("PASS: framing, response parsing, and TCP mock device");
    }
}
