package com.arkael.app;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Repo git lokal untuk mode agent: dibuat dari assets/aiagent pada pemakaian pertama,
 * lalu di-commit dan di-push ke GitHub pakai git bawaan sistem (/system/bin).
 * Tidak butuh root: semua file ada di folder app.
 */
final class AiGit {

    static final String DEFAULT_BRANCH = "feature/ai-agent-native";
    private static final String TEMPLATE = "aiagent";
    private static final int MAX_OUT = 4000;

    private AiGit() {}

    static File root(Context ctx) {
        File d = new File(ctx.getFilesDir(), "aiagent");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    static File repo(Context ctx) {
        return new File(root(ctx), "repo");
    }

    static File home(Context ctx) {
        File d = new File(root(ctx), "home");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    static boolean gitAvailable() {
        return new File("/system/bin/git").exists()
                || new File("/system/xbin/git").exists()
                || new File("/vendor/bin/git").exists();
    }

    /** Jalankan perintah di dalam repo. Mengembalikan gabungan stdout+stderr. */
    static String exec(Context ctx, String cmd) {
        if (!gitAvailable()) {
            return "git tidak ada di ROM ini (/system/bin/git tidak ditemukan). "
                    + "Repo tetap bisa disiapkan, tapi commit/push butuh git. "
                    + "Kalau perangkat root, pasang git di Termux lalu jalankan dari Termux.";
        }
        File repo = repo(ctx);
        if (!repo.exists()) repo.mkdirs();
        ProcessBuilder pb = new ProcessBuilder("sh", "-c", cmd);
        pb.directory(repo);
        pb.redirectErrorStream(true);
        java.util.Map<String, String> env = pb.environment();
        env.put("HOME", home(ctx).getAbsolutePath());
        env.put("TMPDIR", ctx.getCacheDir().getAbsolutePath());
        env.put("PATH", "/system/bin:/system/xbin:/vendor/bin");
        env.put("GIT_TERMINAL_PROMPT", "0");
        env.put("GIT_ASKPASS", "echo");
        env.put("LC_ALL", "C");

        Process p = null;
        try {
            p = pb.start();
            OutputStream os = p.getOutputStream();
            os.close();                       // tidak ada input interaktif
            StringBuilder sb = new StringBuilder();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
                if (sb.length() > 200000) break;   // jangan sampai memori habis
            }
            int code = p.waitFor();
            String out = redact(sb.toString());
            if (code != 0 && !out.trim().isEmpty()) out += "\n(exit " + code + ")";
            if (out.length() > MAX_OUT) out = out.substring(0, MAX_OUT) + "\n\u2026(output dipotong)";
            return out.trim();
        } catch (Exception e) {
            return "gagal menjalankan perintah git: " + e.getMessage();
        } finally {
            if (p != null) try { p.destroy(); } catch (Exception ignore) {}
        }
    }

    /** Jangan pernah bocorkan token ke chat atau ke log. */
    static String redact(String s) {
        if (s == null) return "";
        return s.replaceAll("(https://)[^/@\\s]+(:[^/@\\s]+)?@", "$1***@")
                .replaceAll("(?i)(token|password|secret)=[^\\s&'\"]+", "$1=***");
    }

    /** Siapkan repo dari template bila belum ada. Idempoten. */
    static String ensure(Context ctx) {
        File repo = repo(ctx);
        if (new File(repo, ".git").exists()) return "repo sudah siap: " + repo.getAbsolutePath();
        if (!repo.exists() && !repo.mkdirs()) return "tidak bisa membuat folder repo";

        List<String> copied = copyAssets(ctx, TEMPLATE, repo);
        String user = AiStore.gitUser(ctx);
        String mail = AiStore.gitEmail(ctx);
        String remote = remote(ctx);
        String branch = AiStore.gitBranch(ctx);

        StringBuilder cmd = new StringBuilder();
        cmd.append("git init -q -b ").append(shell(branch)).append(" 2>&1 || git init -q 2>&1\n");
        cmd.append("git config user.name ").append(shell(user)).append('\n');
        cmd.append("git config user.email ").append(shell(mail)).append('\n');
        cmd.append("git config core.autocrlf false\n");
        cmd.append("git add -A\n");
        cmd.append("git -c user.name=").append(shell(user))
           .append(" -c user.email=").append(shell(mail))
           .append(" commit -q -m 'init: template agent dari aplikasi' 2>&1\n");
        if (!remote.isEmpty()) {
            cmd.append("git remote remove origin 2>/dev/null\n");
            cmd.append("git remote add origin ").append(shell(remote)).append('\n');
        }
        cmd.append("git status --short --branch\n");

        String out = exec(ctx, cmd.toString());
        StringBuilder sb = new StringBuilder();
        sb.append("repo dibuat: ").append(repo.getAbsolutePath()).append('\n');
        sb.append("template disalin: ").append(copied.size()).append(" file\n");
        for (int i = 0; i < copied.size() && i < 20; i++) sb.append("  ").append(copied.get(i)).append('\n');
        sb.append("branch: ").append(branch).append('\n');
        sb.append("remote: ").append(remote.isEmpty() ? "(belum diatur \u2014 isi di menu \u2699 \u2192 Git)" : remoteRedacted(remote)).append('\n');
        sb.append('\n').append(out);
        return sb.toString();
    }

    static String remote(Context ctx) {
        String owner = AiStore.gitOwner(ctx);
        String name = AiStore.gitRepo(ctx);
        if (owner.isEmpty() || name.isEmpty()) return "";
        String tok = AiStore.gitToken(ctx);
        String user = AiStore.gitUser(ctx);
        String plain = "https://github.com/" + owner + "/" + name + ".git";
        if (tok.isEmpty()) return plain;
        return "https://" + (user.isEmpty() ? "x-access-token" : user) + ":" + tok + "@github.com/"
                + owner + "/" + name + ".git";
    }

    private static String remoteRedacted(String remote) {
        return redact(remote);
    }

    /** Salin folder aset rekursif ke tujuan; mengembalikan path relatif yang disalin. */
    static List<String> copyAssets(Context ctx, String assetDir, File dest) {
        List<String> out = new ArrayList<>();
        try {
            copyNode(ctx.getAssets(), assetDir, dest, "", out);
        } catch (Exception e) {
            out.add("(gagal menyalin template: " + e.getMessage() + ")");
        }
        return out;
    }

    private static void copyNode(AssetManager am, String assetPath, File dest, String rel, List<String> out)
            throws Exception {
        String[] children = am.list(assetPath);
        if (children == null || children.length == 0) {
            // file: pakai path relatif lengkap (folder + nama), jangan hanya basename
            String fileName = assetPath.substring(assetPath.lastIndexOf('/') + 1);
            String relPath = rel.isEmpty() ? fileName : rel + "/" + fileName;
            File f = new File(dest, relPath);
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            InputStream in = am.open(assetPath);
            try {
                OutputStream os = new FileOutputStream(f);
                try {
                    byte[] buf = new byte[16 * 1024];
                    int r;
                    while ((r = in.read(buf)) > 0) os.write(buf, 0, r);
                } finally {
                    try { os.close(); } catch (Exception ignore) {}
                }
            } finally {
                try { in.close(); } catch (Exception ignore) {}
            }
            out.add(rel.isEmpty() ? name : rel + "/" + name);
            return;
        }
        for (String c : children) {
            String ap = assetPath + "/" + c;
            String r = rel.isEmpty() ? c : rel + "/" + c;
            String[] grand = am.list(ap);
            if (grand != null && grand.length > 0) {
                File dir = new File(dest, r);
                if (!dir.exists()) dir.mkdirs();
                copyNode(am, ap, dest, r, out);
            } else {
                copyNode(am, ap, dest, rel, out);
            }
        }
    }

    /** Kutip satu argumen untuk sh. */
    static String shell(String s) {
        if (s == null) s = "";
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
