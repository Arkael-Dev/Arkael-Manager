# Jalankan dari ~/Arkael-Manager :  python3 apply_perf.py
# Mengurangi lag di Game Launcher: animasi tak terbatas dijeda saat layar tidak tampil dan dimatikan di HP RAM kecil,
# polling statistik lebih jarang di HP kentang.
import os, re, sys
D = 'app/src/main/java/com/arkael/app/'
GL = D + 'GameLauncherActivity.java'
for need in (D + 'Fx.java', D + 'AppDownscale.java'):
    if not os.path.exists(need):
        print('GAGAL: %s belum ada. Unzip arkael_perf_v16.zip dulu.' % need); sys.exit(1)
if not os.path.exists(GL):
    print('GAGAL: GameLauncherActivity.java tidak ketemu'); sys.exit(1)
s = open(GL, encoding='utf8').read()
if 'Fx.track' in s:
    print('SUDAH: GameLauncherActivity sudah dipatch'); sys.exit(0)
ok, bad = [], []

s, n = re.subn(r'^([ \t]*)va\.start\(\);[ \t]*$', lambda m: m.group(0) + '\n' + m.group(1) + 'Fx.track(getContext(), va);', s, flags=re.M)
(ok if n else bad).append('animasi: %d tempat' % n)

s, n = re.subn(r'(protected void onPause\(\)\s*\{\s*super\.onPause\(\);)', r'\1\n        Fx.pauseAll();', s, count=1)
(ok if n else bad).append('onPause')
s, n = re.subn(r'(protected void onResume\(\)\s*\{\s*super\.onResume\(\);)', r'\1\n        Fx.resumeAll();', s, count=1)
(ok if n else bad).append('onResume')
s, n = re.subn(r'main\.postDelayed\(this, 2000\);', 'main.postDelayed(this, Fx.pollMs(GameLauncherActivity.this));', s)
(ok if n else bad).append('polling')
open(GL, 'w', encoding='utf8').write(s)
print('OK   :', ', '.join(ok) or '-')
print('GAGAL:', ', '.join(bad) or '-')
