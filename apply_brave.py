import sys
F = "app/src/main/java/com/arkael/app/WebBrowserActivity.java"
try:
    s = open(F, encoding="utf-8").read()
except OSError:
    print("GAGAL: WebBrowserActivity.java tidak ada (patch Browser v23 belum terpasang)"); sys.exit(1)
if "search.brave.com" in s:
    print("SUDAH DITERAPKAN"); sys.exit(0)
a1 = 'private static final String HOME = "https://duckduckgo.com/";'
a2 = 'else url = HOME + "?q=" + Uri.encode(s);'
for a in (a1, a2):
    if s.count(a) != 1:
        print("GAGAL: anchor tidak unik/hilang: " + a + " (tidak ada yang diubah)"); sys.exit(1)
s = s.replace(a1, 'private static final String HOME = "https://search.brave.com/";\n    private static final String SEARCH = "https://search.brave.com/search?q=";')
s = s.replace(a2, 'else url = SEARCH + Uri.encode(s);')
open(F, "w", encoding="utf-8").write(s)
print("OK: pencarian browser diganti ke Brave Search")
