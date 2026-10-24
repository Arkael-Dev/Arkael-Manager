import os, sys
D = "app/src/main/java/com/arkael/app/"
def rd(n): return open(D + n, encoding="utf-8").read()
def need(s, a, n):
    if s.count(a) != 1:
        print("GAGAL: anchor tidak unik/hilang di %s: %s" % (n, a[:60])); sys.exit(1)

# --- 1. GameListActivity: tombol Downscale di editor profil + terapkan saat PLAY
gl = rd("GameListActivity.java")
if "AppDownscale" in gl:
    print("SUDAH DITERAPKAN"); sys.exit(0)
a1 = 'col.addView(lbl(dc, "ON GAME START"));'
a2 = 'Booster.applyProfile(GameListActivity.this, p, true);'
need(gl, a1, "GameListActivity.java"); need(gl, a2, "GameListActivity.java")
ui = '''col.addView(lbl(dc, "DOWNSCALE (game resolution, applied on PLAY)"));
        final android.widget.Button btnDs = new android.widget.Button(dc);
        btnDs.setAllCaps(false);
        final Runnable refreshDs = new Runnable() {
            @Override public void run() {
                btnDs.setText("Downscale: " + AppDownscale.label(GameListActivity.this, AppDownscale.get(GameListActivity.this, it.pkg)));
            }
        };
        refreshDs.run();
        btnDs.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { AppDownscale.showPicker(GameListActivity.this, it.pkg, it.name, refreshDs); }
        });
        col.addView(btnDs);

        '''
gl = gl.replace(a1, ui + a1)
gl = gl.replace(a2, a2 + '\n                if (AppDownscale.get(GameListActivity.this, it.pkg) != 100) AppDownscale.applyFor(GameListActivity.this, it.pkg);')

# --- 2. GameService: auto game mode ikut menerapkan downscale
gs = rd("GameService.java")
a3 = 'Booster.applyProfile(this, p, false);'
need(gs, a3, "GameService.java")
gs = gs.replace(a3, a3 + '\n        if (AppDownscale.get(this, pkg) != 100) AppDownscale.applyFor(this, pkg);')

# --- 3. (opsional) perbaikan format device_config v18, kalau belum
ad = None
if os.path.exists(D + "AppDownscale.java"):
    ad = rd("AppDownscale.java")
    old = 'mode=2:downscaleFactor=" + f + " 2>&1");'
    if old in ad:
        ad = ad.replace(old, 'mode=2,downscaleFactor=" + f + ":mode=3,downscaleFactor=" + f + " 2>&1");')

# semua anchor lolos, baru tulis
open(D + "GameListActivity.java", "w", encoding="utf-8").write(gl)
open(D + "GameService.java", "w", encoding="utf-8").write(gs)
if ad is not None: open(D + "AppDownscale.java", "w", encoding="utf-8").write(ad)
print("OK: Downscale masuk ke Games & profiles (editor profil, PLAY, auto game mode)")
