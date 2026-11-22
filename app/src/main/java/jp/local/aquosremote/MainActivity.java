package jp.local.aquosremote;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Simple, local-only AQUOS IP remote. No Google TV pairing, HTTP, cloud or telemetry. */
public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(9, 18, 27);
    private static final int CARD = Color.rgb(23, 34, 45);
    private static final int BUTTON = Color.rgb(41, 56, 69);
    private static final int OUTLINE = Color.rgb(63, 79, 91);
    private static final int ACCENT = Color.rgb(55, 212, 180);
    private static final int WHITE = Color.rgb(243, 249, 250);
    private static final int SECONDARY = Color.rgb(172, 192, 200);
    private static final int WARNING = Color.rgb(250, 185, 101);
    private static final int DANGER = Color.rgb(225, 116, 117);
    private static final boolean KIDS_MODE = BuildConfig.KIDS_MODE;
    private static final int MAX_VOLUME = KIDS_MODE ? 25 : 60;

    private final ExecutorService networkQueue = Executors.newSingleThreadExecutor();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private SharedPreferences preferences;
    private TextView endpointLabel;
    private TextView statusLabel;
    private TextView debugResultLabel;
    private TextView volumeLabel;
    private SeekBar volumeSlider;
    private volatile int knownVolume = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences("aquos_settings", MODE_PRIVATE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        createUi();
        refreshVolume();
    }

    @Override
    protected void onDestroy() {
        networkQueue.shutdownNow();
        super.onDestroy();
    }

    private void createUi() {
        ScrollView scrolling = new ScrollView(this);
        scrolling.setFillViewport(true);
        scrolling.setBackgroundColor(BG);
        scrolling.setClipToPadding(false);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(24), dp(18), dp(28));
        scrolling.addView(page, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scrolling);

        TextView eyebrow = makeText(KIDS_MODE ? "SHARP AQUOS · KIDS" : "SHARP AQUOS · LAN REMOTE", 12, ACCENT, true);
        page.addView(eyebrow);
        TextView title = makeText(KIDS_MODE ? "テレビリモコン Kids" : "テレビリモコン", 29, WHITE, true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.topMargin = dp(7);
        page.addView(title, titleLp);
        TextView subtitle = makeText(KIDS_MODE ? "音量は最大25 · このアプリでの操作を制限" : "LC-60XD35 · 家庭内LANから直接操作", 13, SECONDARY, false);
        LinearLayout.LayoutParams subtitleLp = new LinearLayout.LayoutParams(-1, -2);
        subtitleLp.topMargin = dp(7);
        subtitleLp.bottomMargin = dp(21);
        page.addView(subtitle, subtitleLp);

        LinearLayout connection = card(page);
        addHeading(connection, "接続", "テレビへのTCP接続を確認");
        endpointLabel = makeText(endpointText(), 16, WHITE, true);
        connection.addView(endpointLabel);
        statusLabel = makeText("未確認", 13, SECONDARY, false);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.topMargin = dp(7);
        statusLp.bottomMargin = dp(12);
        connection.addView(statusLabel, statusLp);
        LinearLayout connectButtons = buttonRow();
        addButton(connectButtons, "接続・音量確認", true, this::refreshVolume);
        addButton(connectButtons, "IP / ポート", false, this::showSettings);
        connection.addView(connectButtons);

        LinearLayout power = card(page);
        addHeading(power, "電源", "機種によって電源オンは利用できません");
        LinearLayout powerButtons = buttonRow();
        addButton(powerButtons, "⏻  ON", true, () -> sendControl("電源オン", "POWR1"));
        addButton(powerButtons, "⏻  OFF", false, () -> confirmPowerOff());
        power.addView(powerButtons);

        LinearLayout volume = card(page);
        addHeading(volume, "音量", KIDS_MODE ? "Kids版：送信可能な音量は0～25" : "スライダー操作は指を離した時に送信");
        volumeLabel = makeText("現在の音量：未取得", 23, WHITE, true);
        volume.addView(volumeLabel);
        volumeSlider = new SeekBar(this);
        volumeSlider.setMax(MAX_VOLUME);
        volumeSlider.setProgress(20);
        volumeSlider.setProgressTintList(ColorStateList.valueOf(ACCENT));
        volumeSlider.setThumbTintList(ColorStateList.valueOf(ACCENT));
        LinearLayout.LayoutParams volumeSliderLp = new LinearLayout.LayoutParams(-1, dp(50));
        volumeSliderLp.topMargin = dp(8);
        volumeSliderLp.bottomMargin = dp(5);
        volume.addView(volumeSlider, volumeSliderLp);
        volumeSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) volumeLabel.setText("指定する音量：" + progress);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                setVolume(seekBar.getProgress());
            }
        });
        LinearLayout adjustRow = buttonRow();
        addButton(adjustRow, "−5", false, () -> changeVolume(-5));
        addButton(adjustRow, "−1", false, () -> changeVolume(-1));
        addButton(adjustRow, "+1", false, () -> changeVolume(+1));
        addButton(adjustRow, "+5", false, () -> changeVolume(+5));
        volume.addView(adjustRow);
        LinearLayout muteRow = buttonRow();
        addButton(muteRow, "消音", false, () -> sendControl("消音", "MUTE1"));
        addButton(muteRow, "消音解除", false, () -> sendControl("消音解除", "MUTE2"));
        volume.addView(muteRow);

        LinearLayout channel = card(page);
        addHeading(channel, "チャンネル", "テレビ放送の選局");
        LinearLayout channelRow = buttonRow();
        addButton(channelRow, "CH −", false, () -> sendControl("チャンネル−", "CHDW"));
        addButton(channelRow, "CH ＋", false, () -> sendControl("チャンネル＋", "CHUP"));
        channel.addView(channelRow);

        LinearLayout input = card(page);
        addHeading(input, "入力切替", "入力番号の対応はテレビの機種・設定によります");
        LinearLayout inputRow = buttonRow();
        addButton(inputRow, "テレビ", false, () -> sendControl("テレビ入力", "ITVD"));
        addButton(inputRow, "次の入力", true, () -> sendControl("入力切替", "ITGD"));
        input.addView(inputRow);
        LinearLayout inputRow2 = buttonRow();
        for (int i = 1; i <= 3; i++) {
            final int inputNumber = i;
            addButton(inputRow2, "入力 " + i, false,
                    () -> sendControl("入力 " + inputNumber, "IAVD" + inputNumber));
        }
        input.addView(inputRow2);
        LinearLayout inputRow3 = buttonRow();
        addButton(inputRow3, "入力 4", false, () -> sendControl("入力 4", "IAVD4"));
        addButton(inputRow3, "入力 5", false, () -> sendControl("入力 5", "IAVD5"));
        input.addView(inputRow3);

        if (!KIDS_MODE) {
        LinearLayout debug = card(page);
        addHeading(debug, "コマンド試験", "機種依存のコマンドも直接送信できる");
        TextView help = makeText("例：VOLM20 / VOLM???? / POWR0。8文字に空白パディングし、末尾にCRを付けます。", 13, SECONDARY, false);
        debug.addView(help);
        EditText customCommand = new EditText(this);
        customCommand.setSingleLine(true);
        customCommand.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        customCommand.setText("VOLM????");
        customCommand.setTextColor(WHITE);
        customCommand.setHintTextColor(SECONDARY);
        customCommand.setSelectAllOnFocus(true);
        LinearLayout.LayoutParams customLp = new LinearLayout.LayoutParams(-1, dp(56));
        customLp.topMargin = dp(9);
        debug.addView(customCommand, customLp);
        LinearLayout customRow = buttonRow();
        addButton(customRow, "送信", true,
                () -> sendControl("手動送信", customCommand.getText().toString()));
        debug.addView(customRow);
        debugResultLabel = makeText("結果：未送信", 15, SECONDARY, true);
        debugResultLabel.setPadding(dp(12), dp(12), dp(12), dp(12));
        debugResultLabel.setBackground(rounded(BUTTON, OUTLINE, 10));
        LinearLayout.LayoutParams debugResultLp = new LinearLayout.LayoutParams(-1, -2);
        debugResultLp.topMargin = dp(12);
        debug.addView(debugResultLabel, debugResultLp);
        }

        LinearLayout nav = card(page);
        addHeading(nav, "メニュー / カーソル", "RCKY方式（LC-60XD35では未検証）");
        LinearLayout navUp = buttonRow();
        addButton(navUp, "↑", false, () -> sendControl("上", "RCKY41"));
        nav.addView(navUp);
        LinearLayout navMiddle = buttonRow();
        addButton(navMiddle, "←", false, () -> sendControl("左", "RCKY43"));
        addButton(navMiddle, "決定", true, () -> sendControl("決定", "RCKY40"));
        addButton(navMiddle, "→", false, () -> sendControl("右", "RCKY44"));
        nav.addView(navMiddle);
        LinearLayout navDown = buttonRow();
        addButton(navDown, "↓", false, () -> sendControl("下", "RCKY42"));
        nav.addView(navDown);
        LinearLayout navActions = buttonRow();
        addButton(navActions, "メニュー", false, () -> sendControl("メニュー", "RCKY38"));
        addButton(navActions, "戻る", false, () -> sendControl("戻る", "RCKY45"));
        addButton(navActions, "終了", false, () -> sendControl("終了", "RCKY46"));
        nav.addView(navActions);
        if (!KIDS_MODE) {
            LinearLayout homeRow = buttonRow();
            addButton(homeRow, "ホーム（試験）", false, () -> sendControl("ホーム（試験）", "RCKY39"));
            nav.addView(homeRow);
        }

        TextView footer = makeText("TCP 10002 · AQUOS独自IP制御  |  テレビと同じLANに接続してください。", 12, SECONDARY, false);
        footer.setGravity(Gravity.CENTER_HORIZONTAL);
        footer.setPadding(dp(4), dp(5), dp(4), dp(5));
        page.addView(footer);
    }

    private LinearLayout card(LinearLayout page) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(17), dp(16), dp(17), dp(16));
        card.setBackground(rounded(CARD, OUTLINE, 18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(13);
        page.addView(card, lp);
        return card;
    }

    private void addHeading(LinearLayout card, String heading, String info) {
        TextView h = makeText(heading, 19, WHITE, true);
        card.addView(h);
        TextView explanation = makeText(info, 12, SECONDARY, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(3);
        lp.bottomMargin = dp(14);
        card.addView(explanation, lp);
    }

    private LinearLayout buttonRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.topMargin = dp(5);
        row.setLayoutParams(rowLp);
        return row;
    }

    private void addButton(LinearLayout row, String text, boolean prominent, Runnable action) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(prominent ? BG : WHITE);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(4), 0, dp(4), 0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setBackground(new RippleDrawable(
                ColorStateList.valueOf(prominent ? Color.WHITE : ACCENT),
                rounded(prominent ? ACCENT : BUTTON, prominent ? ACCENT : OUTLINE, 12),
                null));
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(53), 1f);
        if (row.getChildCount() > 0) lp.leftMargin = dp(7);
        row.addView(button, lp);
    }

    private TextView makeText(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private GradientDrawable rounded(int fill, int border, int radiusDp) {
        GradientDrawable result = new GradientDrawable();
        result.setColor(fill);
        result.setCornerRadius(dp(radiusDp));
        result.setStroke(dp(1), border);
        return result;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String host() {
        return preferences.getString("host", "192.168.68.74");
    }

    private int port() {
        return preferences.getInt("port", 10002);
    }

    private String endpointText() {
        return host() + ":" + port();
    }

    private void status(String message, boolean success) {
        statusLabel.setText(message);
        statusLabel.setTextColor(success ? ACCENT : WARNING);
        // Show manual command feedback next to its Send button, without scrolling.
        if (debugResultLabel != null && message.startsWith("手動送信")) {
            debugResultLabel.setText(message.replaceFirst("^手動送信", "結果"));
            debugResultLabel.setTextColor(success ? ACCENT : WARNING);
        }
    }

    private void ui(Runnable action) {
        uiHandler.post(() -> {
            if (!isFinishing() && !isDestroyed()) action.run();
        });
    }

    private void sendControl(String label, String command) {
        final String targetHost = host();
        final int targetPort = port();
        try {
            AquosProtocol.normalized(command);
            if (KIDS_MODE && !KidCommandPolicy.allowed(command)) {
                throw new IllegalArgumentException("Kids版ではこの操作を許可していません");
            }
        } catch (IllegalArgumentException invalid) {
            if ("手動送信".equals(label)) status(label + "：入力エラー（" + invalid.getMessage() + "）", false);
            else toast(invalid.getMessage());
            return;
        }
        status(label + " を送信中…", false);
        networkQueue.execute(() -> {
            try {
                AquosClient client = new AquosClient(targetHost, targetPort);
                if (KIDS_MODE && !command.equals("MUTE1") && !command.equals("POWR0")
                        && !command.equals("POWR1") && !command.startsWith("VOLM")) {
                    // Avoid unmuting or switching a TV currently set above the child's cap.
                    enforceKidsLimit(client);
                }
                String answer = client.send(command);
                if (KIDS_MODE && command.equals("POWR1") && "OK".equalsIgnoreCase(answer)) {
                    // The TV may need a moment to wake; best-effort clamp after power on.
                    Thread.sleep(500);
                    try { enforceKidsLimit(client); } catch (IOException ignored) { }
                }
                ui(() -> {
                    if ("ERR".equalsIgnoreCase(answer) || answer.isEmpty()) {
                        status(label + "：テレビがコマンドを拒否しました（" + answer + "）", false);
                    } else {
                        status(label + "：" + answer, true);
                        if (command.toUpperCase(Locale.ROOT).startsWith("VOLM")) {
                            if ("OK".equalsIgnoreCase(answer)) {
                                int volume = AquosProtocol.parsedVolume(command.substring(4).trim());
                                if (volume >= 0) displayVolume(volume);
                            } else {
                                int volume = AquosProtocol.parsedVolume(answer);
                                if (volume >= 0) displayVolume(volume);
                            }
                        }
                    }
                });
            } catch (Exception ex) {
                ui(() -> status(label + "：" + friendlyError(ex), false));
            }
        });
    }

    private void refreshVolume() {
        status("接続・音量を確認中…", false);
        final String targetHost = host();
        final int targetPort = port();
        networkQueue.execute(() -> {
            try {
                AquosClient client = new AquosClient(targetHost, targetPort);
                String response = client.send("VOLM????");
                int actual = AquosProtocol.parsedVolume(response);
                if (KIDS_MODE && actual > MAX_VOLUME) {
                    Thread.sleep(120);
                    String capped = client.send("VOLM" + MAX_VOLUME);
                    if (!"OK".equalsIgnoreCase(capped)) {
                        throw new IOException("音量25への制限が失敗しました（" + capped + "）");
                    }
                    actual = MAX_VOLUME;
                }
                final int resultVolume = actual;
                ui(() -> {
                    if (resultVolume >= 0) {
                        displayVolume(resultVolume);
                        status("接続 OK · 現在の音量を取得しました", true);
                    } else {
                        status("TCP応答あり（" + response + "）· 音量照会は非対応の可能性", false);
                    }
                });
            } catch (Exception ex) {
                ui(() -> status("接続できません：" + friendlyError(ex), false));
            }
        });
    }

    private void displayVolume(int volume) {
        knownVolume = volume;
        volumeLabel.setText("現在の音量：" + volume);
        volumeSlider.setProgress(Math.min(volume, MAX_VOLUME));
    }

    private void setVolume(int value) {
        int requested = Math.max(0, Math.min(MAX_VOLUME, value));
        sendControl("音量 " + requested, "VOLM" + requested);
    }

    /** Read current volume first to avoid clobbering changes made by the physical remote. */
    private void changeVolume(int delta) {
        final String targetHost = host();
        final int targetPort = port();
        status("現在の音量を確認中…", false);
        networkQueue.execute(() -> {
            try {
                AquosClient client = new AquosClient(targetHost, targetPort);
                int current = AquosProtocol.parsedVolume(client.send("VOLM????"));
                if (current < 0 && !KIDS_MODE) current = knownVolume;
                if (current < 0) {
                    ui(() -> status("音量を取得できません。スライダーで値を指定してください。", false));
                    return;
                }
                int requested = Math.max(0, Math.min(MAX_VOLUME, current + delta));
                // Some AQUOS models expect a brief pause between commands.
                Thread.sleep(120);
                String response = client.send("VOLM" + requested);
                ui(() -> {
                    if ("OK".equalsIgnoreCase(response)) {
                        displayVolume(requested);
                        status("音量 " + requested + "：OK", true);
                    } else {
                        status("音量変更は拒否されました（" + response + "）", false);
                    }
                });
            } catch (Exception ex) {
                ui(() -> status("音量変更できません：" + friendlyError(ex), false));
            }
        });
    }

    /** Fail closed in Kids mode if the TV cannot confirm its actual volume. */
    private void enforceKidsLimit(AquosClient client) throws IOException, InterruptedException {
        int actual = AquosProtocol.parsedVolume(client.send("VOLM????"));
        if (actual < 0) throw new IOException("TVの音量を確認できず、安全のため操作を中止しました");
        if (actual > MAX_VOLUME) {
            Thread.sleep(120);
            String result = client.send("VOLM" + MAX_VOLUME);
            if (!"OK".equalsIgnoreCase(result)) {
                throw new IOException("音量25への制限が失敗しました（" + result + "）");
            }
            ui(() -> displayVolume(MAX_VOLUME));
        }
    }

    private void confirmPowerOff() {
        new AlertDialog.Builder(this)
                .setTitle("テレビの電源を切りますか？")
                .setMessage("AQUOSにPOWR0コマンドを送信します。")
                .setNegativeButton("キャンセル", null)
                .setPositiveButton("電源オフ", (dialog, which) -> sendControl("電源オフ", "POWR0"))
                .show();
    }

    private void showSettings() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(5), dp(22), 0);
        TextView ipCaption = makeText("テレビのIPv4アドレス", 13, SECONDARY, false);
        content.addView(ipCaption);
        EditText ip = new EditText(this);
        ip.setSingleLine(true);
        ip.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        ip.setText(host());
        ip.setSelectAllOnFocus(true);
        content.addView(ip);
        TextView portCaption = makeText("TCPポート", 13, SECONDARY, false);
        LinearLayout.LayoutParams captionLp = new LinearLayout.LayoutParams(-1, -2);
        captionLp.topMargin = dp(17);
        content.addView(portCaption, captionLp);
        EditText portField = new EditText(this);
        portField.setSingleLine(true);
        portField.setInputType(InputType.TYPE_CLASS_NUMBER);
        portField.setText(String.valueOf(port()));
        content.addView(portField);

        new AlertDialog.Builder(this)
                .setTitle("接続設定")
                .setView(content)
                .setNegativeButton("キャンセル", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    String newHost = ip.getText().toString().trim();
                    String newPort = portField.getText().toString().trim();
                    if (!validIpv4(newHost)) {
                        toast("IPv4アドレスを確認してください");
                        return;
                    }
                    int parsedPort;
                    try {
                        parsedPort = Integer.parseInt(newPort);
                        if (parsedPort <= 0 || parsedPort > 65535) throw new NumberFormatException();
                    } catch (NumberFormatException ex) {
                        toast("ポート番号は1～65535で指定してください");
                        return;
                    }
                    preferences.edit().putString("host", newHost).putInt("port", parsedPort).apply();
                    knownVolume = -1;
                    volumeLabel.setText("現在の音量：未取得");
                    endpointLabel.setText(endpointText());
                    refreshVolume();
                })
                .show();
    }

    private static boolean validIpv4(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3 || !part.matches("[0-9]+")) return false;
            try {
                int n = Integer.parseInt(part);
                if (n < 0 || n > 255) return false;
            } catch (NumberFormatException ex) {
                return false;
            }
        }
        return true;
    }

    private static String friendlyError(Exception ex) {
        if (ex instanceof java.net.SocketTimeoutException) return "タイムアウト（Wi-Fi・電源・ポートを確認）";
        if (ex instanceof java.net.ConnectException) return "接続拒否（IP・ポートを確認）";
        if (ex instanceof IOException) return "通信エラー（" + ex.getClass().getSimpleName() + "）";
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
