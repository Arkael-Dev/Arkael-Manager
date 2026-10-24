# Jalankan dari ~/Arkael-Manager :  python3 apply_bahasa.py
# Memperbaiki sistem bahasa app supaya benar-benar multi bahasa:
#  1) teks Indonesia yang tertulis langsung di kode Java dibungkus Tr.s("...") -> ikut bahasa pilihan di Pengaturan
#  2) manifest: simpan bahasa pilihan (Android < 13) + daftar bahasa untuk "Bahasa app" di Pengaturan Android
#  3) build.gradle: cabut pembatas bahasa (resConfigs) kalau ada
#  4) laporan sisa teks Indonesia -> bahasa_sisa.txt (kirim ke saya kalau masih ada)
import os, re, sys

D = 'app/src/main/java/com/arkael/app/'
RES = 'app/src/main/res/'
MAN = 'app/src/main/AndroidManifest.xml'
LANGS = ['id', 'en', 'ms', 'ar', 'de', 'es', 'fr', 'hi', 'it', 'ja', 'ko', 'pt', 'ru', 'th', 'tr', 'vi', 'zh']
SKIP_PREFIX = ('Eq', 'Tr', 'Locale')  # Eq* = protokol mesin EQ (sudah diterjemahkan lewat EqText), Locale* = nama bahasa

for need in (D + 'Tr.java', D + 'TrData.java', 'tr_table.tsv'):
    if not os.path.exists(need):
        print('GAGAL: %s belum ada. Unzip arkael_bahasa_v15.zip dulu.' % need); sys.exit(1)

KEYS = set()
for ln in open('tr_table.tsv', encoding='utf8'):
    if ln.strip():
        KEYS.add(ln.rstrip('\n').split('|')[0].strip())


def wrap_source(src):
    """Bungkus literal yang ada di kamus dengan Tr.s(...). Aman untuk komentar, char, text block, case, anotasi."""
    out, i, n = [], 0, len(src)
    count, statics, frags = 0, [], []
    while i < n:
        if src.startswith('//', i):
            j = src.find('\n', i); j = n if j < 0 else j
            out.append(src[i:j]); i = j; continue
        if src.startswith('/*', i):
            j = src.find('*/', i + 2); j = n if j < 0 else j + 2
            out.append(src[i:j]); i = j; continue
        if src.startswith('"""', i):
            j = src.find('"""', i + 3); j = n if j < 0 else j + 3
            out.append(src[i:j]); i = j; continue
        c = src[i]
        if c == "'":
            j = i + 1
            while j < n and src[j] != "'":
                j += 2 if src[j] == '\\' else 1
            out.append(src[i:j + 1]); i = j + 1; continue
        if c == '"':
            j = i + 1
            while j < n and src[j] != '"':
                j += 2 if src[j] == '\\' else 1
            raw = src[i + 1:j]
            lit = src[i:j + 1]
            core = raw.strip()
            pre = src[max(0, i - 80):i]
            ok = (core in KEYS
                  and not re.search(r'\bcase\s*$', pre)
                  and not re.search(r'Tr\.s\(\s*$', pre)
                  and not re.search(r'@\w+\s*\(\s*$', pre))
            if ok:
                out.append('Tr.s(' + lit + ')'); count += 1
                ls = src.rfind('\n', 0, i) + 1
                le = src.find('\n', i); le = n if le < 0 else le
                line = src[ls:le].strip()
                if re.search(r'\bstatic\b', line):
                    statics.append(line[:110])
                if raw != core or core.endswith(('(', ':')):
                    frags.append(line[:110])
            else:
                out.append(lit)
            i = j + 1; continue
        out.append(c); i += 1
    return ''.join(out), count, statics, frags


# ---------- 1) Java ----------
tot, per, all_static, all_frag = 0, [], [], []
for f in sorted(os.listdir(D)):
    if not f.endswith('.java') or f.startswith(SKIP_PREFIX):
        continue
    s = open(D + f, encoding='utf8').read()
    new, cnt, st, fr = wrap_source(s)
    if cnt:
        open(D + f, 'w', encoding='utf8').write(new)
        per.append('  %3d  %s' % (cnt, f)); tot += cnt
        all_static += ['%s: %s' % (f, x) for x in st]
        all_frag += ['%s: %s' % (f, x) for x in fr]
print('== JAVA: %d teks dibungkus Tr.s() ==' % tot)
print('\n'.join(per) or '  (tidak ada yang baru - mungkin sudah pernah dijalankan)')

# ---------- 2) Manifest ----------
def gradle_text():
    for p in ('app/build.gradle', 'app/build.gradle.kts'):
        if os.path.exists(p):
            return p, open(p, encoding='utf8').read()
    return None, ''

print('\n== MANIFEST ==')
if os.path.exists(MAN):
    m = open(MAN, encoding='utf8').read()
    orig = m
    if 'AppLocalesMetadataHolderService' not in m and '</application>' in m:
        svc = ('    <service\n        android:name="androidx.appcompat.app.AppLocalesMetadataHolderService"\n'
               '        android:enabled="false"\n        android:exported="false">\n'
               '        <meta-data\n            android:name="autoStoreLocales"\n            android:value="true" />\n'
               '    </service>\n')
        m = m.replace('</application>', svc + '</application>', 1)
        print('  + AppLocalesMetadataHolderService (bahasa pilihan tersimpan di Android < 13)')
    else:
        print('  = AppLocalesMetadataHolderService sudah ada')
    gp, gt = gradle_text()
    mm = (re.search(r'compileSdk(?:Version)?\s*(?:=|\s)\s*(\d+)', gt)
          or re.search(r'compileSdk\s*\{\s*version\s*=\s*release\((\d+)', gt))
    csdk = int(mm.group(1)) if mm else 0
    cfg = RES + 'xml/locales_config.xml'
    if 'localeConfig' not in m:
        if csdk >= 33:
            if not os.path.exists(cfg):
                os.makedirs(RES + 'xml', exist_ok=True)
                body = ''.join('    <locale android:name="%s"/>\n' % l for l in LANGS)
                open(cfg, 'w', encoding='utf8').write(
                    '<?xml version="1.0" encoding="utf-8"?>\n<locale-config xmlns:android="http://schemas.android.com/apk/res/android">\n' + body + '</locale-config>\n')
                print('  + res/xml/locales_config.xml (%d bahasa)' % len(LANGS))
            m2 = re.sub(r'<application\b', '<application\n        android:localeConfig="@xml/locales_config"', m, count=1)
            if m2 != m:
                m = m2; print('  + android:localeConfig di <application> (menu "Bahasa app" di Pengaturan Android)')
        else:
            print('  ! compileSdk %s < 33, localeConfig dilewati' % (csdk or '?'))
    else:
        print('  = localeConfig sudah ada')
        if os.path.exists(cfg):
            t = open(cfg, encoding='utf8').read()
            have = set(re.findall(r'android:name="([\w-]+)"', t))
            miss = [l for l in LANGS if l not in have and not (l == 'id' and 'in' in have)]
            if miss and '</locale-config>' in t:
                t = t.replace('</locale-config>', ''.join('    <locale android:name="%s"/>\n' % l for l in miss) + '</locale-config>')
                open(cfg, 'w', encoding='utf8').write(t)
                print('  + locales_config: tambah ' + ', '.join(miss))
    if m != orig:
        open(MAN, 'w', encoding='utf8').write(m)
else:
    print('  ! AndroidManifest.xml tidak ketemu')

# ---------- 3) build.gradle ----------
print('\n== BUILD.GRADLE ==')
gp, gt = gradle_text()
if gp:
    lines, changed = gt.split('\n'), []
    for k, ln in enumerate(lines):
        if re.match(r'\s*(resConfigs?|resourceConfigurations|localeFilters)\b', ln) and ln.count('(') == ln.count(')') and ln.count('[') == ln.count(']'):
            lines[k] = ln[:len(ln) - len(ln.lstrip())] + '// (bahasa) ' + ln.lstrip()
            changed.append(ln.strip())
    if changed:
        open(gp, 'w', encoding='utf8').write('\n'.join(lines))
        for c in changed: print('  - dimatikan (membatasi bahasa di APK): ' + c)
    else:
        print('  = tidak ada pembatas bahasa')
else:
    print('  ! build.gradle tidak ketemu')

# ---------- 4) laporan ----------
IND = set('yang dan untuk tidak belum atau dengan dari sedang buka simpan hapus batal tutup gagal berhasil pengaturan setelan '
          'pilih semua tambah ubah kembali lanjut mulai matikan nyalakan aktif izin izinkan mainkan tersimpan memuat coba '
          'tampilkan sembunyikan cari kosong riwayat bahasa suara musik layar lagi sudah bisa harus tekan ketuk geser '
          'beranda langganan disukai unduh berikutnya sebelumnya ulangi acak kecepatan kualitas hasil terbaru populer '
          'selesai menunggu coba tunggu sebentar pengguna otomatis manual nama baru lihat tonton putar jeda'.split())
lit = re.compile(r'"((?:\\.|[^"\\])*)"')
rep = []
for f in sorted(os.listdir(D)):
    if not f.endswith('.java') or f.startswith(SKIP_PREFIX):
        continue
    for n, ln in enumerate(open(D + f, encoding='utf8', errors='ignore'), 1):
        s = ln.lstrip()
        if s.startswith(('//', '*', '/*', 'import ', 'package ')):
            continue
        for m in lit.finditer(ln):
            t = m.group(1)
            if 'Tr.s(' in ln[max(0, m.start() - 6):m.start()]:
                continue
            words = re.findall(r'[a-zA-Z]+', t.lower())
            if len(t.strip()) >= 3 and any(w in IND for w in words):
                rep.append('%s:%d: %s' % (f, n, t[:120]))
xml_hits = []
for dp, _, fs in os.walk(RES):
    base = os.path.basename(dp)
    if not (base.startswith(('layout', 'menu', 'xml'))):
        continue
    for f in fs:
        if not f.endswith('.xml'): continue
        for n, ln in enumerate(open(os.path.join(dp, f), encoding='utf8', errors='ignore'), 1):
            for m in re.finditer(r'android:(?:text|hint|title|contentDescription|label|summary)="([^@?][^"]*)"', ln):
                words = re.findall(r'[a-zA-Z]+', m.group(1).lower())
                if any(w in IND for w in words):
                    xml_hits.append('%s/%s:%d: %s' % (base, f, n, m.group(1)[:100]))
rp = ['== SISA TEKS INDONESIA DI JAVA (belum diterjemahkan) =='] + (rep or ['(kosong)'])
rp += ['', '== TEKS DI XML (layout/menu/xml) =='] + (xml_hits or ['(kosong)'])
rp += ['', '== STATIS (teks baru ikut bahasa baru setelah app dibuka ulang) =='] + (all_static or ['(kosong)'])
rp += ['', '== POTONGAN KALIMAT (cek susunan di bahasa lain) =='] + (all_frag or ['(kosong)'])
open('bahasa_sisa.txt', 'w', encoding='utf8').write('\n'.join(rp) + '\n')
print('\n== LAPORAN ==')
print('  sisa teks Indonesia di Java : %d' % len(rep))
print('  teks Indonesia di XML       : %d' % len(xml_hits))
print('  teks statis / potongan      : %d / %d' % (len(all_static), len(all_frag)))
print('Detail: bahasa_sisa.txt  (kalau sisa > 0, kirim isinya ke saya)')
