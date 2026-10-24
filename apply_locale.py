# Jalankan dari ~/Arkael-Manager :  python3 apply_locale.py
# Menambah attachBaseContext (bahasa pilihan) ke semua Activity biasa yang belum punya.
import os, re
D = 'app/src/main/java/com/arkael/app/'
if not os.path.exists(D + 'LocaleWrap.java'):
    print('GAGAL: LocaleWrap.java belum ada. Unzip arkael_locale_v14.zip dulu.'); raise SystemExit(1)
pat = re.compile(r'((?:public\s+)?(?:abstract\s+)?class\s+\w+\s+extends\s+(?:android\.app\.)?Activity\s*\{)')
M = ('\n    @Override\n    protected void attachBaseContext(android.content.Context base) {\n'
     '        super.attachBaseContext(LocaleWrap.wrap(base));\n    }\n')
done, skip = [], []
for f in sorted(os.listdir(D)):
    if not f.endswith('.java'): continue
    s = open(D + f, encoding='utf8').read()
    if not pat.search(s): continue
    if 'attachBaseContext' in s: skip.append(f); continue
    open(D + f, 'w', encoding='utf8').write(pat.sub(lambda m: m.group(1) + M, s, count=1)); done.append(f)
print('DITAMBAH :', ', '.join(done) or '-')
print('DILEWATI :', ', '.join(skip) or '-', '(sudah punya attachBaseContext)')
