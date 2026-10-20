package com.arkael.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;

/** One persistent "su" shell, so reading live stats every second does not spawn a process each time. */
final class RootShell {
    private static final String END = "__ARK_END__";
    private static Process proc;
    private static OutputStream out;
    private static BufferedReader in;

    private RootShell() {}

    private static boolean ensure() {
        if (proc != null) {
            try {
                proc.exitValue();   // throws if still running
                close();
            } catch (IllegalThreadStateException alive) {
                return true;
            }
        }
        try {
            proc = new ProcessBuilder("su").redirectErrorStream(true).start();
            out = proc.getOutputStream();
            in = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            return true;
        } catch (Exception e) {
            close();
            return false;
        }
    }

    /** Runs a shell script as root and returns everything it printed. Call from a background thread. */
    static synchronized String run(String script) {
        if (!ensure()) return "";
        try {
            out.write((script + "\necho " + END + "\n").getBytes("UTF-8"));
            out.flush();
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                if (line.equals(END)) return sb.toString();
                sb.append(line).append('\n');
            }
            close();            // EOF: su died or was denied
            return sb.toString();
        } catch (IOException e) {
            close();
            return "";
        }
    }

    static boolean hasRoot() {
        return run("id").contains("uid=0");
    }

    static synchronized void close() {
        try { if (out != null) out.close(); } catch (Exception ignored) {}
        try { if (in != null) in.close(); } catch (Exception ignored) {}
        try { if (proc != null) proc.destroy(); } catch (Exception ignored) {}
        proc = null; out = null; in = null;
    }
}
