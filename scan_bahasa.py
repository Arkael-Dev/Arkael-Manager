# Jalankan dari ~/Arkael-Manager :  python3 scan_bahasa.py
# Mencari (1) cara app menerapkan bahasa pilihan, (2) teks Indonesia yang masih tertulis langsung di kode Java
# (inilah yang membuat bahasa Indonesia tetap muncul saat memilih English). Hasil: bahasa_scan.txt. Kirim isi file itu ke saya.
import os, re
ROOT = 'app/src/main'
IND = set('yang dan untuk tidak belum atau dengan dari sedang buka simpan hapus batal tutup gagal berhasil pengaturan setelan '
          'pilih semua tambah ubah kembali lanjut mulai matikan nyalakan aktif izin izinkan mainkan tersimpan memuat coba '
          'tampilkan sembunyikan cari kosong riwayat bahasa suara musik layar lagi sudah bisa harus tekan ketuk geser'.split())
loc_pat = re.compile(r'setApplicationLocales|Locale\.setDefault|createConfigurationContext|attachBaseContext|updateConfiguration|LocaleList|setLocale|applyOverrideConfiguration')
lit = re.compile(r'"((?:\\.|[^"\\])*)"')
out, per_file, locale_hits = [], {}, []
for dp, _, fs in os.walk(ROOT):
    for f in fs:
        if not f.endswith('.java'): continue
        path = os.path.join(dp, f)
        for n, ln in enumerate(open(path, encoding='utf8', errors='ignore'), 1):
            if loc_pat.search(ln): locale_hits.append('%s:%d: %s' % (path, n, ln.strip()[:150]))
            if ln.lstrip().startswith(('//', '*', '/*')): continue
            for m in lit.finditer(ln):
                t = m.group(1)
                words = re.findall(r'[a-zA-Z]+', t.lower())
                if len(t) >= 4 and sum(w in IND for w in words) >= 1 and ' ' in t or (len(words) == 1 and words[0] in IND and len(t) >= 4):
                    per_file.setdefault(path, []).append((n, t))
out.append('== CARA APP MENERAPKAN BAHASA ==')
out += locale_hits or ['(tidak ketemu pemanggilan locale sama sekali)']
out.append('\n== TEKS INDONESIA DI KODE JAVA (jumlah per file) ==')
for p, v in sorted(per_file.items(), key=lambda x: -len(x[1])):
    out.append('%4d  %s' % (len(v), p))
out.append('\n== DETAIL ==')
for p, v in sorted(per_file.items(), key=lambda x: -len(x[1])):
    out.append('# ' + p)
    for n, t in v: out.append('  %d: %s' % (n, t[:110]))
open('bahasa_scan.txt', 'w', encoding='utf8').write('\n'.join(out) + '\n')
print('\n'.join(out[:60]))
print('\nSelengkapnya di bahasa_scan.txt (%d file, %d teks)' % (len(per_file), sum(len(v) for v in per_file.values())))
