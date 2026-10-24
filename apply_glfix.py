# Jalankan dari ~/Arkael-Manager :  python3 apply_glfix.py
# Perbaikan lag Game Launcher (v21):
#  A. BgView: garis diagonal+gradient digambar SEKALI ke bitmap, animasi maksimal 30 FPS
#  B. Semua animasi tak berujung dipantau Fx (dijeda saat layar tidak tampil, mati di HP RAM kecil), polling lebih jarang di HP kentang
#  C. Ikon putar overlay hanya berputar saat overlay tampil (sebelumnya berputar terus walau tersembunyi)
#  D. Cut.draw: tidak membuat objek & path baru tiap frame
#  E. Daftar game: pilih game hanya mengganti 2 baris, tidak membangun ulang seluruh daftar
import os, re, sys, shutil

D = 'app/src/main/java/com/arkael/app/'
GL = D + 'GameLauncherActivity.java'
if not os.path.exists(GL):
    print('GAGAL: %s tidak ketemu. Jalankan dari ~/Arkael-Manager' % GL); sys.exit(1)
if not os.path.exists(D + 'Fx.java'):
    print('GAGAL: Fx.java belum ada. Unzip arkael_glfix_v21.zip di ~/Arkael-Manager dulu.'); sys.exit(1)

src = open(GL, encoding='utf-8').read()
s = src
report = []

def rep(s, old, new, name):
    n = s.count(old)
    if n != 1:
        report.append('LEWAT  %s (pola ditemukan %d kali, harus 1)' % (name, n))
        return s, False
    return s.replace(old, new), True

# ------------------------------------------------------------------ A. BgView
def step_A(s):
    if 'buildCache(' in s:
        report.append('SUDAH  A. BgView cache'); return s
    t = s
    ok = True
    t, k = rep(t,
        'private final Paint bg = new Paint(), stripe = new Paint(Paint.ANTI_ALIAS_FLAG), glow = new Paint(), sweep = new Paint();',
        'private final Paint bg = new Paint(), stripe = new Paint(Paint.ANTI_ALIAS_FLAG), glow = new Paint(), sweep = new Paint();\n'
        '        private android.graphics.Bitmap cache;\n'
        '        private long lastInv;', 'A1 fields'); ok &= k
    t, k = rep(t,
        'glow.setShader(new RadialGradient(w * 0.88f, h * 1.05f, h * 1.15f, Color.argb(255, 255, 30, 60), Color.TRANSPARENT, Shader.TileMode.CLAMP));',
        'glow.setShader(new RadialGradient(w * 0.88f, h * 1.05f, h * 1.15f, Color.argb(255, 255, 30, 60), Color.TRANSPARENT, Shader.TileMode.CLAMP));\n'
        '            buildCache(w, h);', 'A2 onSizeChanged'); ok &= k
    t, k = rep(t,
        '        @Override protected void onDraw(Canvas cv) {\n'
        '            int w = getWidth(), h = getHeight();\n'
        '            cv.drawRect(0, 0, w, h, bg);\n'
        '            for (float x = -h; x < w; x += 34 * d) cv.drawLine(x, h, x + h * 0.58f, 0, stripe);\n',
        '        private void buildCache(int w, int h) {\n'
        '            cache = null;\n'
        '            if (w <= 0 || h <= 0) return;\n'
        '            try {\n'
        '                android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);\n'
        '                Canvas bc = new Canvas(b);\n'
        '                bc.drawRect(0, 0, w, h, bg);\n'
        '                for (float x = -h; x < w; x += 34 * d) bc.drawLine(x, h, x + h * 0.58f, 0, stripe);\n'
        '                cache = b;\n'
        '            } catch (Throwable t) { cache = null; }\n'
        '        }\n'
        '        @Override protected void onDraw(Canvas cv) {\n'
        '            int w = getWidth(), h = getHeight();\n'
        '            if (cache != null) cv.drawBitmap(cache, 0, 0, null);\n'
        '            else {\n'
        '                cv.drawRect(0, 0, w, h, bg);\n'
        '                for (float x = -h; x < w; x += 34 * d) cv.drawLine(x, h, x + h * 0.58f, 0, stripe);\n'
        '            }\n', 'A3 onDraw'); ok &= k
    t, k = rep(t,
        'va.setDuration(7000);\n'
        '            va.setRepeatCount(ValueAnimator.INFINITE);\n'
        '            va.setInterpolator(new LinearInterpolator());\n'
        '            va.addUpdateListener(a -> { phase = (Float) a.getAnimatedValue(); invalidate(); });',
        'va.setDuration(7000);\n'
        '            va.setRepeatCount(ValueAnimator.INFINITE);\n'
        '            va.setInterpolator(new LinearInterpolator());\n'
        '            va.addUpdateListener(a -> {\n'
        '                phase = (Float) a.getAnimatedValue();\n'
        '                long now = android.os.SystemClock.uptimeMillis();\n'
        '                if (now - lastInv >= 33) { lastInv = now; invalidate(); }   // maks ~30 FPS untuk latar\n'
        '            });', 'A4 throttle'); ok &= k
    if ok:
        report.append('OK     A. BgView: bitmap cache + 30 FPS')
        return t
    report.append('GAGAL  A. dibatalkan seluruhnya (BgView beda dari yang diharapkan)')
    return s

# ------------------------------------------------------------------ B. Fx
def step_B(s):
    if 'Fx.track' in s and 'Fx.pauseAll' in s:
        report.append('SUDAH  B. Fx'); return s
    t = s
    t, n = re.subn(r'^([ \t]*)va\.start\(\);[ \t]*$',
                   lambda m: m.group(0) + '\n' + m.group(1) + 'Fx.track(getContext(), va);', t, flags=re.M) \
        if 'Fx.track' not in t else (t, 0)
    t, n2 = re.subn(r'(protected void onPause\(\)\s*\{\s*super\.onPause\(\);)', r'\1\n        Fx.pauseAll();', t, count=1)
    t, n3 = re.subn(r'(protected void onResume\(\)\s*\{\s*super\.onResume\(\);)', r'\1\n        Fx.resumeAll();', t, count=1)
    t, n4 = re.subn(r'main\.postDelayed\(this, 2000\);', 'main.postDelayed(this, Fx.pollMs(GameLauncherActivity.this));', t)
    report.append('OK     B. Fx: animasi=%d onPause=%d onResume=%d polling=%d' % (n, n2, n3, n4))
    return t

# ------------------------------------------------------------------ C. HexIcon hanya berputar saat tampil
def step_C(s):
    if 'startSpin()' in s:
        report.append('SUDAH  C. HexIcon'); return s
    pat = re.compile(r'if \(spin\) \{\s*va = ValueAnimator\.ofFloat\(0f, 360f\);.*?va\.start\(\);(?:\s*Fx\.track\([^;]*;)?\s*\}', re.S)
    if len(pat.findall(s)) != 1:
        report.append('LEWAT  C. HexIcon (blok animasi tidak ketemu / tidak unik)'); return s
    t = pat.sub('startSpin();', s, count=1)
    t, k = rep(t,
        'void set(Drawable d, int color) { this.d = d; this.color = color; invalidate(); }',
        'void set(Drawable d, int color) { this.d = d; this.color = color; invalidate(); }\n'
        '        private void startSpin() {\n'
        '            if (!spin || va != null || !isShown()) return;   // tersembunyi (overlay GONE) = tidak berputar\n'
        '            va = ValueAnimator.ofFloat(0f, 360f);\n'
        '            va.setDuration(9000);\n'
        '            va.setRepeatCount(ValueAnimator.INFINITE);\n'
        '            va.setInterpolator(new LinearInterpolator());\n'
        '            va.addUpdateListener(a -> { rot = (Float) a.getAnimatedValue(); invalidate(); });\n'
        '            va.start();\n'
        '            Fx.track(getContext(), va);\n'
        '        }\n'
        '        @Override protected void onVisibilityChanged(View changed, int vis) {\n'
        '            super.onVisibilityChanged(changed, vis);\n'
        '            if (!spin || !isAttachedToWindow()) return;\n'
        '            if (isShown()) startSpin();\n'
        '            else if (va != null) { va.cancel(); va = null; }\n'
        '        }', 'C2')
    if not k:
        return s
    report.append('OK     C. HexIcon: berputar hanya saat terlihat')
    return t

# ------------------------------------------------------------------ D. Cut.draw
def step_D(s):
    if 'built = true' in s:
        report.append('SUDAH  D. Cut'); return s
    t, k = rep(s,
        '        @Override public void draw(Canvas cv) {\n'
        '            RectF r = new RectF(getBounds());\n'
        '            r.inset(sw / 2f, sw / 2f);\n'
        '            buildPath(path, r, cut, c);\n',
        '        private final RectF rf = new RectF();\n'
        '        private int bl_, bt_, br_, bb_;\n'
        '        private boolean built;\n'
        '        @Override public void draw(Canvas cv) {\n'
        '            android.graphics.Rect b = getBounds();\n'
        '            if (!built || b.left != bl_ || b.top != bt_ || b.right != br_ || b.bottom != bb_) {\n'
        '                bl_ = b.left; bt_ = b.top; br_ = b.right; bb_ = b.bottom; built = true;\n'
        '                rf.set(b);\n'
        '                rf.inset(sw / 2f, sw / 2f);\n'
        '                buildPath(path, rf, cut, c);\n'
        '            }\n', 'D Cut.draw')
    if k: report.append('OK     D. Cut.draw: path di-cache')
    return t

# ------------------------------------------------------------------ E. renderList inkremental
def step_E(s):
    if 'buildRow(' in s:
        report.append('SUDAH  E. daftar game'); return s
    head = 'private void renderList() {\n        listCol.removeAllViews();\n        for (int i = 0; i < games.size(); i++) {\n'
    tail = ('            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);\n'
            '            rl.bottomMargin = dp(4);\n'
            '            listCol.addView(row, rl);\n'
            '        }\n'
            '        introDone = true;\n'
            '    }')
    a = s.find(head)
    if a < 0 or s.count(head) != 1:
        report.append('LEWAT  E. renderList beda dari yang diharapkan'); return s
    b = s.find(tail, a)
    if b < 0 or b - a > 6000:
        report.append('LEWAT  E. akhir renderList tidak cocok'); return s
    body = s[a + len(head):b]
    if 'LinearLayout row = new LinearLayout(this);' not in body or 'select(idx)' not in body:
        report.append('LEWAT  E. isi renderList tidak sesuai'); return s
    new = ('private void renderList() {\n'
           '        listCol.removeAllViews();\n'
           '        for (int i = 0; i < games.size(); i++) listCol.addView(buildRow(i), rowLp());\n'
           '        introDone = true;\n'
           '    }\n\n'
           '    private LinearLayout.LayoutParams rowLp() {\n'
           '        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);\n'
           '        rl.bottomMargin = dp(4);\n'
           '        return rl;\n'
           '    }\n\n'
           '    /** Ganti hanya satu baris (dipakai saat memilih game) supaya tidak membangun ulang seluruh daftar. */\n'
           '    private void refreshRow(int i) {\n'
           '        if (i < 0 || i >= games.size() || i >= listCol.getChildCount()) return;\n'
           '        listCol.removeViewAt(i);\n'
           '        listCol.addView(buildRow(i), i, rowLp());\n'
           '    }\n\n'
           '    private View buildRow(int i) {\n'
           + body +
           '        return row;\n'
           '    }')
    t = s[:a] + new + s[b + len(tail):]
    t, k = rep(t,
        '        sel = i;\n        playFx(sSelect, 0.8f);\n        renderList();\n',
        '        final int prev = sel;\n'
        '        sel = i;\n'
        '        playFx(sSelect, 0.8f);\n'
        '        if (listCol.getChildCount() == games.size()) { refreshRow(prev); refreshRow(i); }\n'
        '        else renderList();\n', 'E2 select')
    if not k:
        return s
    report.append('OK     E. daftar game: pilih = ganti 2 baris saja')
    return t

for fn in (step_A, step_B, step_C, step_D, step_E):
    s = fn(s)

print('\n'.join(report))
if s == src:
    print('\nTidak ada perubahan.'); sys.exit(0)
if s.count('{') != s.count('}'):
    print('\nGAGAL: jumlah kurung { } tidak seimbang setelah patch, file TIDAK diubah.'); sys.exit(1)
bak = GL + '.bak_glfix'
if not os.path.exists(bak):
    shutil.copy(GL, bak)
open(GL, 'w', encoding='utf-8').write(s)
print('\nSelesai. Cadangan: %s' % bak)
print('Lanjut: git add -A && git commit -m "perf(game-launcher): kurangi lag" && git push')
