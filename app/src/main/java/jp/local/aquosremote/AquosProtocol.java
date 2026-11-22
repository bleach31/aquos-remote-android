package jp.local.aquosremote;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Sharp AQUOS IP/RS-232 style frame: four-character command + four-character parameter + CR. */
public final class AquosProtocol {
    private AquosProtocol() { }

    public static String normalized(String input) {
        if (input == null) throw new IllegalArgumentException("コマンドが空です");
        String value = input.trim().toUpperCase(Locale.ROOT).replace('_', ' ');
        if (value.length() < 4 || value.length() > 8) {
            throw new IllegalArgumentException("コマンドは4～8文字にしてください");
        }
        if (!value.substring(0, 4).matches("[A-Z0-9]{4}")) {
            throw new IllegalArgumentException("先頭4文字は英数字で指定してください");
        }
        if (!value.substring(4).matches("[A-Z0-9? ]{0,4}")) {
            throw new IllegalArgumentException("パラメータは英数字・?・空白のみ使用できます");
        }
        return String.format(Locale.ROOT, "%-8s", value);
    }

    public static byte[] frame(String input) {
        return (normalized(input) + "\r").getBytes(StandardCharsets.US_ASCII);
    }

    public static int parsedVolume(String response) {
        if (response == null || !response.trim().matches("[0-9]{1,3}")) return -1;
        int number = Integer.parseInt(response.trim());
        return number >= 0 && number <= 100 ? number : -1;
    }
}
