package com.builder.app;

import android.app.Activity;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MainActivity extends Activity {
    static final int CYAN = 0xFF00E5FF, VIOLET = 0xFF7C4DFF, PINK = 0xFFFF4DA6;
    static final int TXT = 0xFFE8F0FF, DIM = 0xFF8A97B8, CARD = 0xCC121A38;

    EditText tokenIn, repoIn, branchIn, codeIn;
    TextView logView, listStatus;
    SharedPreferences sp;
    String token, repo, branch, code;
    FrameLayout root;
    ScrollView mainPage, listPage;
    LinearLayout listBox;
    int screen = 0;
    String currentRepo = "";
    float dp;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("cfg", MODE_PRIVATE);
        dp = getResources().getDisplayMetrics().density;
        getWindow().setStatusBarColor(0xFF050816);
        getWindow().setNavigationBarColor(0xFF050816);
        root = new FrameLayout(this);
        root.addView(new BgView(this));
        buildMain();
        buildList();
        root.addView(mainPage);
        root.addView(listPage);
        listPage.setVisibility(View.GONE);
        setContentView(root);
    }

    // ---------- UI-Helfer ----------
    int d(int v) {
        return (int) (v * dp);
    }

    GradientDrawable box(int fill, int stroke, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(d(radius));
        if (stroke != 0) g.setStroke(d(1), stroke);
        return g;
    }

    TextView text(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    Button btn(String s, boolean primary) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setStateListAnimator(null);
        if (primary) {
            b.setTextColor(0xFFFFFFFF);
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{0xFF00B8D4, VIOLET});
            g.setCornerRadius(d(14));
            b.setBackground(g);
        } else {
            b.setTextColor(CYAN);
            b.setBackground(box(0x2200E5FF, CYAN, 14));
        }
        return b;
    }

    LinearLayout.LayoutParams lp(int top) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, -2);
        l.topMargin = d(top);
        return l;
    }

    LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(box(CARD, 0x5500E5FF, 16));
        c.setPadding(d(16), d(14), d(16), d(14));
        return c;
    }

    EditText field(String hint, boolean multi) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(DIM);
        e.setTextColor(TXT);
        e.setBackground(box(0x22FFFFFF, 0x3300E5FF, 12));
        e.setPadding(d(14), d(12), d(14), d(12));
        e.setTextSize(15);
        if (multi) {
            e.setMinLines(8);
            e.setGravity(Gravity.TOP);
            e.setTextSize(12);
            e.setTypeface(Typeface.MONOSPACE);
            e.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        } else {
            e.setSingleLine(true);
        }
        return e;
    }

    // ---------- Hauptseite ----------
    void buildMain() {
        mainPage = new ScrollView(this);
        mainPage.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(d(20), d(52), d(20), d(28));

        TextView title = text("APK BUILDER", 30, CYAN, true);
        title.setLetterSpacing(0.12f);
        col.addView(title);
        col.addView(text("Code einfügen · bauen · installieren", 13, DIM, false));

        LinearLayout set = card();
        tokenIn = field("GitHub Token", false);
        tokenIn.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        repoIn = field("Repo (benutzer/repo)", false);
        branchIn = field("Branch", false);
        tokenIn.setText(sp.getString("token", ""));
        repoIn.setText(sp.getString("repo", ""));
        branchIn.setText(sp.getString("branch", "main"));
        set.addView(tokenIn);
        set.addView(repoIn, lp(8));
        set.addView(branchIn, lp(8));
        col.addView(set, lp(18));

        LinearLayout cc = card();
        codeIn = field("Code hier einfügen (### FILE: pfad)", true);
        cc.addView(codeIn);
        Button paste = btn("Aus Zwischenablage einfügen", false);
        cc.addView(paste, lp(8));
        col.addView(cc, lp(14));

        Button build = btn("⚡  Hochladen & APK bauen", true);
        col.addView(build, lp(16));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button last = btn("Letzte APK", false);
        Button apps = btn("Meine APKs", false);
        LinearLayout.LayoutParams h1 = new LinearLayout.LayoutParams(0, -2, 1f);
        h1.rightMargin = d(5);
        LinearLayout.LayoutParams h2 = new LinearLayout.LayoutParams(0, -2, 1f);
        h2.leftMargin = d(5);
        row.addView(last, h1);
        row.addView(apps, h2);
        col.addView(row, lp(10));

        logView = text("", 12, 0xFF7CFFB2, false);
        logView.setTypeface(Typeface.MONOSPACE);
        LinearLayout lc = card();
        lc.addView(logView);
        col.addView(lc, lp(14));
        mainPage.addView(col);

        paste.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                codeIn.setText(cm.getPrimaryClip().getItemAt(0).coerceToText(this));
            }
        });
        build.setOnClickListener(v -> {
            readInputs();
            new Thread(this::doBuild).start();
        });
        last.setOnClickListener(v -> {
            readInputs();
            new Thread(() -> {
                try {
                    download(repo, null);
                } catch (Exception e) {
                    log("Fehler: " + e.getMessage());
                }
            }).start();
        });
        apps.setOnClickListener(v -> {
            readInputs();
            remember(repo);
            showApps();
        });
    }

    void buildList() {
        listPage = new ScrollView(this);
        listPage.setVerticalScrollBarEnabled(false);
        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        listBox.setPadding(d(20), d(52), d(20), d(28));
        listPage.addView(listBox);
        listStatus = text("", 12, 0xFF7CFFB2, false);
        listStatus.setTypeface(Typeface.MONOSPACE);
    }

    void show(int s) {
        screen = s;
        mainPage.setVisibility(s == 0 ? View.VISIBLE : View.GONE);
        listPage.setVisibility(s == 0 ? View.GONE : View.VISIBLE);
        listPage.scrollTo(0, 0);
    }

    @Override
    public void onBackPressed() {
        if (screen == 2) showApps();
        else if (screen == 1) show(0);
        else super.onBackPressed();
    }

    void resetList(String title, String sub, Runnable back) {
        listBox.removeAllViews();
        Button b = btn("←  Zurück", false);
        b.setOnClickListener(v -> back.run());
        listBox.addView(b, new LinearLayout.LayoutParams(-2, -2));
        TextView t = text(title, 24, CYAN, true);
        t.setPadding(0, d(16), 0, 0);
        listBox.addView(t);
        listBox.addView(text(sub, 13, DIM, false));
        listBox.addView(listStatus, lp(10));
    }

    // ---------- Meine APKs ----------
    List<String> loadRepos() {
        List<String> l = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp.getString("repos", "[]"));
            for (int i = 0; i < a.length(); i++) l.add(a.getString(i));
        } catch (Exception ignored) {
        }
        return l;
    }

    void remember(String r) {
        if (r == null || r.isEmpty() || !r.contains("/")) return;
        List<String> l = loadRepos();
        l.remove(r);
        l.add(0, r);
        sp.edit().putString("repos", new JSONArray(l).toString()).apply();
    }

    void forget(String r) {
        List<String> l = loadRepos();
        l.remove(r);
        sp.edit().putString("repos", new JSONArray(l).toString()).apply();
    }

    void showApps() {
        show(1);
        resetList("Meine APKs", "Tippen: Versionen & Infos · Lange drücken: entfernen", () -> show(0));
        List<String> repos = loadRepos();
        if (repos.isEmpty()) {
            listBox.addView(text("Noch keine Apps. Baue eine App oder trage ein Repo ein und tippe auf \"Meine APKs\".", 14, DIM, false), lp(16));
        }
        for (String r : repos) {
            LinearLayout c = card();
            String nm = r.substring(r.indexOf('/') + 1);
            c.addView(text("◆  " + nm, 18, TXT, true));
            c.addView(text(r, 12, DIM, false));
            c.setOnClickListener(v -> showVersions(r));
            c.setOnLongClickListener(v -> {
                forget(r);
                showApps();
                return true;
            });
            listBox.addView(c, lp(12));
        }
    }

    String find(String src, String re) {
        Matcher m = Pattern.compile(re).matcher(src);
        return m.find() ? m.group(1) : "–";
    }

    String date(String s) {
        if (s == null || s.length() < 16) return "–";
        return s.substring(0, 10) + "  " + s.substring(11, 16);
    }

    void info(LinearLayout c, String k, String v) {
        TextView a = text(k, 11, DIM, false);
        a.setPadding(0, d(8), 0, 0);
        c.addView(a);
        c.addView(text(v, 14, TXT, false));
    }

    void showVersions(String r) {
        token = tokenIn.getText().toString().trim();
        currentRepo = r;
        show(2);
        resetList(r.substring(r.indexOf('/') + 1), r, this::showApps);
        TextView ld = text("Lade Infos…", 14, DIM, false);
        listBox.addView(ld, lp(14));
        new Thread(() -> {
            try {
                JSONObject info = new JSONObject(api("GET", "/repos/" + r, null));
                String gradle = "";
                try {
                    gradle = api("GET", "/repos/" + r + "/contents/app/build.gradle", null,
                            "application/vnd.github.raw+json");
                } catch (Exception ignored) {
                }
                Map<Long, JSONObject> runs = new HashMap<>();
                JSONArray ra = new JSONObject(api("GET",
                        "/repos/" + r + "/actions/runs?status=success&per_page=50", null))
                        .getJSONArray("workflow_runs");
                for (int i = 0; i < ra.length(); i++) {
                    runs.put(ra.getJSONObject(i).getLong("id"), ra.getJSONObject(i));
                }
                JSONArray arts = new JSONObject(api("GET",
                        "/repos/" + r + "/actions/artifacts?per_page=50", null))
                        .getJSONArray("artifacts");
                final String g = gradle;
                runOnUiThread(() -> renderVersions(r, ld, info, g, runs, arts));
            } catch (Exception e) {
                runOnUiThread(() -> ld.setText("Fehler: " + e.getMessage()));
            }
        }).start();
    }

    void renderVersions(String r, TextView ld, JSONObject info, String g,
                        Map<Long, JSONObject> runs, JSONArray arts) {
        if (screen != 2 || !r.equals(currentRepo)) return;
        listBox.removeView(ld);
        String nm = r.substring(r.indexOf('/') + 1);

        LinearLayout ic = card();
        ic.addView(text("App-Infos", 16, PINK, true));
        info(ic, "App-ID", find(g, "applicationId\\s+[\"']([^\"']+)[\"']"));
        info(ic, "Version", find(g, "versionName\\s+[\"']([^\"']+)[\"']")
                + "  (Code " + find(g, "versionCode\\s+(\\d+)") + ")");
        info(ic, "Repository", r);
        info(ic, "Sichtbarkeit", info.optBoolean("private") ? "privat" : "öffentlich");
        info(ic, "Letzte Änderung", date(info.optString("pushed_at")));
        info(ic, "Gespeicherte Builds", String.valueOf(arts.length()));
        if (!info.isNull("description") && !info.optString("description").isEmpty()) {
            info(ic, "Beschreibung", info.optString("description"));
        }
        listBox.addView(ic, lp(14));

        listBox.addView(text("Versionen", 18, CYAN, true), lp(20));
        if (arts.length() == 0) {
            listBox.addView(text("Keine gespeicherten Builds. GitHub löscht Build-Dateien nach 90 Tagen.", 14, DIM, false), lp(8));
        }
        for (int i = 0; i < arts.length(); i++) {
            JSONObject a = arts.optJSONObject(i);
            if (a == null) continue;
            boolean expired = a.optBoolean("expired");
            JSONObject wr = a.optJSONObject("workflow_run");
            JSONObject run = wr == null ? null : runs.get(wr.optLong("id"));
            int no = run == null ? 0 : run.optInt("run_number");
            String msg = "";
            if (run != null) {
                JSONObject hc = run.optJSONObject("head_commit");
                if (hc != null) msg = hc.optString("message", "").split("\n")[0];
            }
            long aid = a.optLong("id");
            LinearLayout c = card();
            c.addView(text((i == 0 ? "★ Neueste · " : "") + "Build " + (no > 0 ? "#" + no : String.valueOf(aid)), 16, TXT, true));
            if (!msg.isEmpty()) c.addView(text(msg, 13, DIM, false));
            c.addView(text(date(a.optString("created_at")) + "  ·  "
                    + String.format(Locale.US, "%.1f MB", a.optLong("size_in_bytes") / 1048576.0), 12, DIM, false));
            c.addView(text("Verfügbar bis " + date(a.optString("expires_at")), 12, DIM, false));
            if (expired) {
                c.addView(text("abgelaufen", 13, PINK, true));
            } else {
                String fn = nm + "-build-" + (no > 0 ? no : aid) + ".apk";
                Button b = btn("Herunterladen & installieren", true);
                b.setOnClickListener(v -> new Thread(() -> {
                    try {
                        downloadArtifact(r, aid, fn);
                    } catch (Exception e) {
                        log("Fehler: " + e.getMessage());
                    }
                }).start());
                c.addView(b, lp(10));
            }
            listBox.addView(c, lp(10));
        }
    }

    // ---------- Logik ----------
    void readInputs() {
        token = tokenIn.getText().toString().trim();
        repo = repoIn.getText().toString().trim();
        branch = branchIn.getText().toString().trim();
        if (branch.isEmpty()) branch = "main";
        code = codeIn.getText().toString();
        sp.edit().putString("token", token).putString("repo", repo)
                .putString("branch", branch).apply();
    }

    void log(String s) {
        runOnUiThread(() -> {
            logView.append(s + "\n");
            listStatus.setText(s);
        });
    }

    Map<String, String> parse(String t) {
        Map<String, String> m = new LinkedHashMap<>();
        String path = null;
        StringBuilder sb = null;
        for (String line : t.replace("\r", "").split("\n", -1)) {
            String tl = line.trim();
            if (tl.startsWith("### FILE:")) {
                if (path != null) m.put(path, sb.toString());
                path = tl.substring(9).trim().replace("`", "");
                while (path.startsWith("/")) path = path.substring(1);
                sb = new StringBuilder();
            } else if (path != null) {
                if (tl.startsWith("```")) continue;
                sb.append(line).append("\n");
            }
        }
        if (path != null) m.put(path, sb.toString());
        return m;
    }

    String read(InputStream is) throws Exception {
        if (is == null) return "";
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) o.write(buf, 0, n);
        return o.toString("UTF-8");
    }

    String api(String method, String path, String body) throws Exception {
        return api(method, path, body, "application/vnd.github+json");
    }

    String api(String method, String path, String body, String accept) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL("https://api.github.com" + path).openConnection();
        c.setRequestMethod(method);
        c.setRequestProperty("Authorization", "Bearer " + token);
        c.setRequestProperty("Accept", accept);
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.getOutputStream().write(body.getBytes("UTF-8"));
        }
        int rc = c.getResponseCode();
        String r = read(rc < 400 ? c.getInputStream() : c.getErrorStream());
        if (rc >= 400) throw new Exception("GitHub " + rc + ": " + r);
        return r;
    }

    void doBuild() {
        try {
            Map<String, String> files = parse(code);
            if (files.isEmpty()) {
                log("Keine Dateien gefunden. Format: ### FILE: pfad");
                return;
            }
            log(files.size() + " Dateien werden hochgeladen…");
            String parent = new JSONObject(api("GET", "/repos/" + repo + "/git/ref/heads/" + branch, null))
                    .getJSONObject("object").getString("sha");
            String baseTree = new JSONObject(api("GET", "/repos/" + repo + "/git/commits/" + parent, null))
                    .getJSONObject("tree").getString("sha");
            JSONArray tree = new JSONArray();
            for (Map.Entry<String, String> e : files.entrySet()) {
                tree.put(new JSONObject().put("path", e.getKey()).put("mode", "100644")
                        .put("type", "blob").put("content", e.getValue()));
            }
            String newTree = new JSONObject(api("POST", "/repos/" + repo + "/git/trees",
                    new JSONObject().put("base_tree", baseTree).put("tree", tree).toString()))
                    .getString("sha");
            String commit = new JSONObject(api("POST", "/repos/" + repo + "/git/commits",
                    new JSONObject().put("message", "Update via Builder").put("tree", newTree)
                            .put("parents", new JSONArray().put(parent)).toString()))
                    .getString("sha");
            api("PATCH", "/repos/" + repo + "/git/refs/heads/" + branch,
                    new JSONObject().put("sha", commit).toString());
            remember(repo);
            log("Hochgeladen. Build startet…");

            for (int i = 0; i < 150; i++) {
                Thread.sleep(8000);
                JSONArray runs = new JSONObject(api("GET",
                        "/repos/" + repo + "/actions/runs?head_sha=" + commit, null))
                        .getJSONArray("workflow_runs");
                if (runs.length() == 0) {
                    log("Warte auf Build-Start…");
                    continue;
                }
                JSONObject run = runs.getJSONObject(0);
                String st = run.getString("status");
                if (st.equals("completed")) {
                    String con = run.optString("conclusion");
                    if (con.equals("success")) {
                        download(repo, run.getLong("id"));
                    } else {
                        log("Build fehlgeschlagen (" + con + "). Details: " + run.getString("html_url"));
                    }
                    return;
                }
                log("Build läuft (" + st + ")…");
            }
            log("Zeitüberschreitung.");
        } catch (Exception e) {
            log("Fehler: " + e.getMessage());
        }
    }

    void download(String rp, Long runId) throws Exception {
        if (runId == null) {
            JSONArray runs = new JSONObject(api("GET", "/repos/" + rp
                    + "/actions/runs?status=success&per_page=1&branch=" + branch, null))
                    .getJSONArray("workflow_runs");
            if (runs.length() == 0) {
                log("Kein erfolgreicher Build gefunden.");
                return;
            }
            runId = runs.getJSONObject(0).getLong("id");
        }
        JSONArray arts = new JSONObject(api("GET", "/repos/" + rp + "/actions/runs/" + runId + "/artifacts", null))
                .getJSONArray("artifacts");
        if (arts.length() == 0) {
            log("Kein Artefakt gefunden.");
            return;
        }
        downloadArtifact(rp, arts.getJSONObject(0).getLong("id"),
                "app-" + System.currentTimeMillis() + ".apk");
    }

    void downloadArtifact(String rp, long aid, String fname) throws Exception {
        log("Lade APK…");
        HttpURLConnection c = (HttpURLConnection) new URL("https://api.github.com/repos/" + rp
                + "/actions/artifacts/" + aid + "/zip").openConnection();
        c.setInstanceFollowRedirects(false);
        c.setRequestProperty("Authorization", "Bearer " + token);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        int rc = c.getResponseCode();
        InputStream in;
        if (rc >= 300 && rc < 400) {
            HttpURLConnection c2 = (HttpURLConnection) new URL(c.getHeaderField("Location")).openConnection();
            in = c2.getInputStream();
        } else if (rc < 400) {
            in = c.getInputStream();
        } else {
            throw new Exception("Download " + rc + ": " + read(c.getErrorStream()));
        }
        ZipInputStream z = new ZipInputStream(in);
        ZipEntry e;
        while ((e = z.getNextEntry()) != null) {
            if (e.getName().endsWith(".apk")) {
                final Uri u = saveApk(z, fname);
                remember(rp);
                log("APK liegt im Download-Ordner.");
                runOnUiThread(() -> install(u));
                return;
            }
        }
        log("Keine APK im Artefakt.");
    }

    Uri saveApk(InputStream in, String name) throws Exception {
        ContentValues v = new ContentValues();
        v.put(MediaStore.Downloads.DISPLAY_NAME, name);
        v.put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive");
        v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
        Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
        OutputStream o = getContentResolver().openOutputStream(u);
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
        o.close();
        return u;
    }

    void install(Uri u) {
        if (!getPackageManager().canRequestPackageInstalls()) {
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName())));
            log("Bitte Installation erlauben, danach erneut herunterladen.");
            return;
        }
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(u, "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    // ---------- Animierter Hintergrund ----------
    static class BgView extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final float[][] orbs = new float[14][5];
        final int[] cols = {0xFF00E5FF, 0xFF7C4DFF, 0xFFFF4DA6};
        Shader bg;
        float t = 0;

        BgView(Context c) {
            super(c);
            Random r = new Random(7);
            for (float[] o : orbs) {
                o[0] = r.nextFloat();
                o[1] = r.nextFloat();
                o[2] = 0.05f + r.nextFloat() * 0.12f;
                o[3] = 0.3f + r.nextFloat() * 0.7f;
                o[4] = r.nextFloat() * 6.28f;
            }
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            bg = new LinearGradient(0, 0, w, h,
                    new int[]{0xFF050816, 0xFF0E1033, 0xFF1A0B38}, null, Shader.TileMode.CLAMP);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            t += 0.02f;
            p.setStyle(Paint.Style.FILL);
            p.setShader(bg);
            c.drawRect(0, 0, w, h, p);
            p.setShader(null);
            for (int i = 0; i < orbs.length; i++) {
                float[] o = orbs[i];
                float x = (o[0] + 0.06f * (float) Math.sin(t * o[3] + o[4])) * w;
                float y = h * (1f - ((o[1] + t * o[3] * 0.02f) % 1f));
                float r = o[2] * Math.min(w, h);
                int col = cols[i % 3] & 0x00FFFFFF;
                for (int k = 3; k >= 1; k--) {
                    p.setColor(col | ((10 + (4 - k) * 8) << 24));
                    c.drawCircle(x, y, r * k / 3f, p);
                }
                p.setColor(col | (150 << 24));
                c.drawCircle(x, y, r * 0.08f, p);
            }
            postInvalidateOnAnimation();
        }
    }
}
