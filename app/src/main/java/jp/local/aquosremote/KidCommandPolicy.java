package jp.local.aquosremote;

import java.util.Locale;

/** Defense in depth: even if called accidentally, Kids may not send arbitrary TCP commands. */
public final class KidCommandPolicy {
    private KidCommandPolicy() { }

    public static boolean allowed(String raw) {
        String command = AquosProtocol.normalized(raw).trim().toUpperCase(Locale.ROOT);
        if (command.startsWith("VOLM")) {
            String requested = command.substring(4).trim();
            if (!requested.matches("[0-9]{1,3}")) return false;
            int level = Integer.parseInt(requested);
            return level >= 0 && level <= 25;
        }
        if (command.equals("POWR0") || command.equals("POWR1")
                || command.equals("MUTE1") || command.equals("MUTE2")
                || command.equals("CHUP") || command.equals("CHDW")
                || command.equals("ITGD") || command.equals("ITVD")) return true;
        if (command.matches("IAVD[1-5]")) return true;
        return command.matches("RCKY(38|40|41|42|43|44|45|46)");
    }
}
