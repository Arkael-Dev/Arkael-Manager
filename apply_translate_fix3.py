import os, re, sys

MAN = "app/src/main/AndroidManifest.xml"
ACT = "app/src/main/java/com/arkael/app/TranslateActivity.java"

if not os.path.exists(MAN):
    print("GAGAL: jalankan dari ~/Arkael-Manager"); sys.exit(1)

m = open(MAN, encoding="utf-8").read()
pat = re.compile(r'[ \t]*<activity\s+android:name="\.TranslateActivity".*?</activity>[ \t]*\n?', re.S)
found = pat.findall(m)
if len(found) > 1:
    print("GAGAL: blok TranslateActivity lebih dari satu (tidak ada yang diubah)"); sys.exit(1)

done = []
if found:
    m = pat.sub("", m, count=1)
    open(MAN, "w", encoding="utf-8").write(m)
    done.append("ikon Translate dihapus dari manifest")
if os.path.exists(ACT):
    os.remove(ACT)
    done.append("TranslateActivity.java dihapus")

if not done:
    print("SUDAH DITERAPKAN"); sys.exit(0)
print("OK: " + "; ".join(done))
