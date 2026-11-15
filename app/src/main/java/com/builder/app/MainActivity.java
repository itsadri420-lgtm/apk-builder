package com.builder.app;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MainActivity extends Activity {
    EditText tokenIn, repoIn, branchIn, codeIn;
    TextView logView;
    SharedPreferences sp;
    String token, repo, branch, code;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("cfg", MODE_PRIVATE);
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (16 * getResources().getDisplayMetrics().density);
        root.setPadding(p, p * 2, p, p);

        tokenIn = field("GitHub Token", false);
        tokenIn.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        repoIn = field("Repo (benutzer/repo)", false);
        branchIn = field("Branch", false);
        codeIn = field("Code hier einfügen (### FILE: pfad)", true);
        tokenIn.setText(sp.getString("token", ""));
        repoIn.setText(sp.getString("repo", ""));
        branchIn.setText(sp.getString("branch", "main"));

        Button buildBtn = new Button(this);
        buildBtn.setText("Hochladen & APK bauen");
        Button dlBtn = new Button(this);
        dlBtn.setText("Letzte APK laden");
        logView = new TextView(this);
        logView.setPadding(0, p, 0, 0);

        root.addView(tokenIn);
        root.addView(repoIn);
        root.addView(branchIn);
        root.addView(codeIn);
        root.addView(buildBtn);
        root.addView(dlBtn);
        root.addView(logView);
        sv.addView(root);
        setContentView(sv);

        buildBtn.setOnClickListener(v -> {
            readInputs();
            new Thread(this::doBuild).start();
        });
        dlBtn.setOnClickListener(v -> {
            readInputs();
            new Thread(() -> {
                try {
                    download(null);
                } catch (Exception e) {
                    log("Fehler: " + e.getMessage());
                }
            }).start();
        });
    }

    EditText field(String hint, boolean multi) {
        EditText e = new EditText(this);
        e.setHint(hint);
        if (multi) {
            e.setMinLines(8);
            e.setGravity(Gravity.TOP);
            e.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        } else {
            e.setSingleLine(true);
        }
        return e;
    }

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
        runOnUiThread(() -> logView.append(s + "\n"));
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
        HttpURLConnection c = (HttpURLConnection) new URL("https://api.github.com" + path).openConnection();
        c.setRequestMethod(method);
        c.setRequestProperty("Authorization", "Bearer " + token);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.getOutputStream().write(body.getBytes("UTF-8"));
        }
        int code = c.getResponseCode();
        String r = read(code < 400 ? c.getInputStream() : c.getErrorStream());
        if (code >= 400) throw new Exception("GitHub " + code + ": " + r);
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
                        download(run.getLong("id"));
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

    void download(Long runId) throws Exception {
        if (runId == null) {
            JSONArray runs = new JSONObject(api("GET", "/repos/" + repo
                    + "/actions/runs?status=success&per_page=1&branch=" + branch, null))
                    .getJSONArray("workflow_runs");
            if (runs.length() == 0) {
                log("Kein erfolgreicher Build gefunden.");
                return;
            }
            runId = runs.getJSONObject(0).getLong("id");
        }
        JSONArray arts = new JSONObject(api("GET", "/repos/" + repo + "/actions/runs/" + runId + "/artifacts", null))
                .getJSONArray("artifacts");
        if (arts.length() == 0) {
            log("Kein Artefakt gefunden.");
            return;
        }
        long aid = arts.getJSONObject(0).getLong("id");
        log("Lade APK…");
        HttpURLConnection c = (HttpURLConnection) new URL("https://api.github.com/repos/" + repo
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
                final Uri u = saveApk(z, "app-" + System.currentTimeMillis() + ".apk");
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
            log("Bitte Installation erlauben, danach 'Letzte APK laden' tippen.");
            return;
        }
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(u, "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }
}
