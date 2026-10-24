import re, sys
F = "app/src/main/java/com/arkael/app/GameLauncherActivity.java"
L = open(F, encoding="utf-8").read().split("\n")
if not any("AppDownscale" in x or "pvScale" in x for x in L):
    print("SUDAH DICABUT"); sys.exit(0)

out, i = [], 0
while i < len(L):
    s = L[i].strip()
    if s == "pvScale = new TextView(this);":                      # blok tombol pratinjau
        j = i
        while j < len(L) and "pvContent.addView(pvScale, psl);" not in L[j]: j += 1
        blk = "\n".join(L[i:j + 1])
        if j >= len(L) or j - i > 20 or "AppDownscale.showPicker" not in blk:
            print("GAGAL: blok pvScale tidak sesuai, tidak ada yang diubah"); sys.exit(1)
        i = j + 1; continue
    if s == "private TextView pvScale;": i += 1; continue
    if s.startswith("if (pvScale != null) pvScale.setText(") and s.endswith(";"): i += 1; continue
    if s == "try { AppDownscale.applyFor(this, g.pkg); } catch (Throwable ignored) {}": i += 1; continue
    out.append(L[i]); i += 1

res = "\n".join(out)
if "AppDownscale" in res or "pvScale" in res:
    print("GAGAL: masih ada sisa AppDownscale/pvScale, tidak ada yang diubah"); sys.exit(1)
open(F, "w", encoding="utf-8").write(res)
print("OK: downscale dicabut dari GameLauncherActivity (%d baris dihapus)" % (len(L) - len(out)))
