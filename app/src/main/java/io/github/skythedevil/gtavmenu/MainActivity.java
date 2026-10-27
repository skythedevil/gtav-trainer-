package io.github.skythedevil.gtavmenu;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.skythedevil.gtavmenu.core.InstallException;
import io.github.skythedevil.gtavmenu.core.Installer;

/** One simple screen: PS5 IP, "Check connection", "Install in-game menu", and a log. */
public class MainActivity extends Activity {
    private static final int BG = Color.parseColor("#101715");
    private static final int PANEL = Color.parseColor("#1b2622");
    private static final int ACCENT = Color.parseColor("#3ddc97");

    private SharedPreferences prefs;
    private EditText ipField, portField;
    private Button checkButton, installButton;
    private TextView logView;
    private ScrollView logScroll;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean busy;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("gtav-menu", MODE_PRIVATE);
        if (prefs.getString("identity", null) == null)
            prefs.edit().putString("identity", UUID.randomUUID().toString()).apply();
        buildScreen();
        log("Ready. Start your PS5 jailbreak and PS5Debug-NG 1.3.2, open GTA V, load into Story Mode, then tap “Check connection”.");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        worker.shutdown();
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private TextView label(String text, int sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(text); t.setTextSize(sp); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text); b.setAllCaps(false); b.setTextColor(Color.BLACK);
        b.setBackgroundColor(ACCENT);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        lp.topMargin = dp(10);
        b.setLayoutParams(lp);
        return b;
    }

    private EditText field(String hint, String value, int inputType) {
        EditText e = new EditText(this);
        e.setHint(hint); e.setText(value); e.setInputType(inputType);
        e.setTextColor(Color.WHITE); e.setHintTextColor(Color.GRAY);
        e.setSingleLine(true);
        return e;
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(18), dp(36), dp(18), dp(18));

        root.addView(label("GTA V – PS5 Menu Installer", 22, true, Color.WHITE));
        root.addView(label("Story Mode only • PS5 firmware 13.60 • PS5Debug-NG 1.3.2", 12, false, Color.LTGRAY));

        ipField = field("PS5 IP address (e.g. 192.168.1.25)", prefs.getString("host", ""),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        portField = field("Port", String.valueOf(prefs.getInt("port", 744)), InputType.TYPE_CLASS_NUMBER);
        root.addView(ipField);
        root.addView(portField);

        checkButton = button("Check connection (changes nothing)");
        installButton = button("Install in-game menu");
        checkButton.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { start(false); } });
        installButton.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { confirmInstall(); } });
        root.addView(checkButton);
        root.addView(installButton);

        logView = label("", 13, false, Color.WHITE);
        logView.setPadding(dp(12), dp(12), dp(12), dp(12));
        logView.setBackgroundColor(PANEL);
        logView.setGravity(Gravity.TOP);
        logScroll = new ScrollView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        lp.topMargin = dp(14);
        logScroll.setLayoutParams(lp);
        logScroll.addView(logView);
        root.addView(logScroll);

        root.addView(label("In GTA: L1 + D-pad Right opens the menu. Reinstall after restarting GTA.", 12, false, Color.LTGRAY));
        setContentView(root);
    }

    // ---- actions --------------------------------------------------------------------

    private void confirmInstall() {
        new AlertDialog.Builder(this)
                .setTitle("Install in-game menu?")
                .setMessage("GTA V must be running, in Story Mode, with your character loaded and the game unpaused.\n\n"
                        + "Some menu options (money, skills, trophies) can affect your autosaves.")
                .setPositiveButton("Install", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) { start(true); }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void start(final boolean install) {
        if (busy) return;
        final String host = ipField.getText().toString().trim();
        final int port;
        try { port = Integer.parseInt(portField.getText().toString().trim()); }
        catch (NumberFormatException e) { log("Port must be a number (normally 744)."); return; }
        try { Installer.validateEndpoint(host, port); }
        catch (InstallException e) { log(e.getMessage()); return; }
        prefs.edit().putString("host", host).putInt("port", port).apply();

        setBusy(true);
        final Installer.Config cfg = new Installer.Config(host, port, prefs.getString("identity", "unknown"));
        final Installer.Progress progress = new Installer.Progress() {
            public void say(String m) { postLog(m); }
        };
        worker.execute(new Runnable() {
            public void run() {
                try {
                    Installer.Assets assets = new AppAssets(getAssets());
                    Installer.Result r = install
                            ? Installer.install(cfg, assets, new FileRecovery(new File(getFilesDir(), "native-bridge-recovery.json")), progress)
                            : Installer.inspect(cfg, assets, progress);
                    postLog("✔ " + r.message + (install && !r.framesAdvancing
                            ? "\n(The menu did not report running yet. If L1 + D-pad Right does nothing, unpause GTA and wait a few seconds.)" : ""));
                } catch (InstallException e) {
                    postLog("✖ " + e.getMessage());
                } catch (RuntimeException e) {
                    postLog("✖ Unexpected problem: " + e);
                } finally {
                    runOnUiThread(new Runnable() { public void run() { setBusy(false); } });
                }
            }
        });
    }

    private void setBusy(boolean b) {
        busy = b;
        checkButton.setEnabled(!b); installButton.setEnabled(!b);
        if (b) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void postLog(final String m) {
        runOnUiThread(new Runnable() { public void run() { log(m); } });
    }

    private void log(String m) {
        CharSequence old = logView.getText();
        logView.setText(old.length() == 0 ? m : old + "\n\n" + m);
        logScroll.post(new Runnable() { public void run() { logScroll.fullScroll(View.FOCUS_DOWN); } });
    }

    // ---- helpers the installer needs ------------------------------------------------

    /** Reads the menu files bundled inside the app (assets/menu/...). */
    private static final class AppAssets implements Installer.Assets {
        private final AssetManager am;
        AppAssets(AssetManager am) { this.am = am; }
        public byte[] read(String path) throws IOException {
            InputStream in = am.open("menu/" + path);
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192]; int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return out.toByteArray();
            } finally { in.close(); }
        }
    }

    /** Remembers where the menu was installed, so a second install can safely re-attach. */
    private static final class FileRecovery implements Installer.RecoveryStore {
        private final File file;
        FileRecovery(File file) { this.file = file; }
        public String load() {
            if (!file.exists()) return null;
            try {
                FileInputStream in = new FileInputStream(file);
                try {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096]; int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    return new String(out.toByteArray(), StandardCharsets.UTF_8);
                } finally { in.close(); }
            } catch (IOException e) { return null; }
        }
        public void save(String json) {
            try {
                FileOutputStream out = new FileOutputStream(file);
                try { out.write(json.getBytes(StandardCharsets.UTF_8)); } finally { out.close(); }
            } catch (IOException e) { throw new InstallException("Could not save recovery info: " + e.getMessage(), e); }
        }
    }
}
