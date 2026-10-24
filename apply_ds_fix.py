import sys
F = "app/src/main/java/com/arkael/app/AppDownscale.java"
s = open(F, encoding="utf-8").read()
old_start = s.index('            String f = String.format(java.util.Locale.US, "%.2f", pct / 100f);')
old_end = s.index('            return true;', old_start)
new = '''            String f = String.format(java.util.Locale.US, "%.2f", pct / 100f);
            RootShell.run("cmd game set --mode 2 --downscale " + f + " " + pkg + " 2>&1");
            RootShell.run("device_config put game_overlay " + pkg + " mode=2:downscaleFactor=" + f + " 2>&1");
            RootShell.run("cmd game mode 2 " + pkg + " 2>&1");
            String chk = RootShell.run("cmd game list-configs " + pkg + " 2>&1");
            try { java.io.FileWriter w = new java.io.FileWriter("/sdcard/Download/arkael_downscale_log.txt", true);
                w.write(pkg + " " + f + "\\n" + chk + "\\n\\n"); w.close(); } catch (Throwable ignore) {}
'''
if "cmd game set --mode 2" in s:
    print("SUDAH DITERAPKAN"); sys.exit(0)
s = s[:old_start] + new + s[old_end:]
open(F, "w", encoding="utf-8").write(s)
print("OK: AppDownscale.push diperbaiki")
