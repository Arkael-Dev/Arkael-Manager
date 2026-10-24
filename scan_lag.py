# Jalankan dari ~/Arkael-Manager :  python3 scan_lag.py
# Mencari pola kode yang biasa bikin lag (gambar ulang terus-menerus, polling cepat, root shell di UI, thread per detik).
# Hasil: lag_scan.txt. Kirim isi file itu ke saya.
import os, re
ROOT = 'app/src/main'
PAT = [
 ('ANIMASI TAK TERBATAS', re.compile(r'INFINITE')),
 ('POLLING <=1500ms', re.compile(r'postDelayed\([^;]*,\s*(\d{1,4})\s*\)')),
 ('THREAD BARU (cek apakah di dalam loop/poll)', re.compile(r'new Thread\(')),
 ('ROOT SHELL', re.compile(r'RootShell\.run\(')),
 ('SLEEP', re.compile(r'Thread\.sleep\(')),
 ('BERAT DI UI? (icon/bitmap/daftar app)', re.compile(r'loadIcon\(|decodeFile\(|decodeStream\(|queryIntentActivities\(|getInstalledApplications\(|getInstalledPackages\(')),
 ('SharedPreferences commit()', re.compile(r'\.commit\(\)')),
 ('LAYER SOFTWARE', re.compile(r'LAYER_TYPE_SOFTWARE')),
 ('BLUR/SHADOW', re.compile(r'setShadowLayer|BlurMaskFilter|RenderEffect|setRenderEffect')),
 ('WAKELOCK/SERVICE', re.compile(r'WakeLock|startForeground\(')),
 ('RECEIVER (cek unregister)', re.compile(r'registerReceiver\(|unregisterReceiver\(')),
]
out, counts, det = [], {}, {}
for dp, _, fs in os.walk(ROOT):
    for f in sorted(fs):
        if not f.endswith('.java'): continue
        p = os.path.join(dp, f)
        for n, ln in enumerate(open(p, encoding='utf8', errors='ignore'), 1):
            s = ln.strip()
            if s.startswith(('//', '*', '/*')): continue
            for name, rx in PAT:
                m = rx.search(ln)
                if not m: continue
                if name.startswith('POLLING') and int(m.group(1)) > 1500: continue
                counts.setdefault(name, {}).setdefault(f, 0)
                counts[name][f] += 1
                det.setdefault(name, []).append('%s:%d: %s' % (f, n, s[:120]))
out.append('== RINGKASAN (jumlah per file) ==')
for name, _ in PAT:
    if name in counts:
        out.append('# ' + name)
        for f, c in sorted(counts[name].items(), key=lambda x: -x[1])[:8]:
            out.append('  %3d  %s' % (c, f))
out.append('\n== DETAIL ==')
for name, _ in PAT:
    if name in det:
        out.append('# ' + name)
        out += ['  ' + x for x in det[name][:40]]
open('lag_scan.txt', 'w', encoding='utf8').write('\n'.join(out) + '\n')
print('\n'.join(out[:45]))
print('\nSelengkapnya di lag_scan.txt')
