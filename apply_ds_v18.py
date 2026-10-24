import sys
F = "app/src/main/java/com/arkael/app/AppDownscale.java"
s = open(F, encoding="utf-8").read()
old = 'RootShell.run("device_config put game_overlay " + pkg + " mode=2:downscaleFactor=" + f + " 2>&1");'
new = 'RootShell.run("device_config put game_overlay " + pkg + " mode=2,downscaleFactor=" + f + ":mode=3,downscaleFactor=" + f + " 2>&1");'
if new in s:
    print("SUDAH DITERAPKAN"); sys.exit(0)
if old not in s:
    print("GAGAL: baris v17 tidak ditemukan, tidak ada yang diubah"); sys.exit(1)
open(F, "w", encoding="utf-8").write(s.replace(old, new))
print("OK: format device_config downscale diperbaiki")
