import os, sys

LAY = "app/src/main/res/layout/activity_main.xml"
MAIN = "app/src/main/java/com/arkael/app/MainActivity.java"
SVC = "app/src/main/java/com/arkael/app/TranslateService.java"
ACT = "app/src/main/java/com/arkael/app/TranslateActivity.java"
MAN = "app/src/main/AndroidManifest.xml"

for f in (LAY, MAIN, MAN):
    if not os.path.exists(f):
        print("GAGAL: tidak ada " + f + " (jalankan dari ~/Arkael-Manager)"); sys.exit(1)
for f in (SVC, ACT):
    if not os.path.exists(f):
        print("GAGAL: tidak ada " + f + " (jalankan apply_translate.py dulu)"); sys.exit(1)

CARD = '''<LinearLayout android:id="@+id/btn_translate" android:layout_width="match_parent" android:layout_height="wrap_content" android:minHeight="66dp" android:orientation="horizontal" android:gravity="center_vertical" android:tag="card" android:background="@drawable/bg_card_rounded" android:foreground="?android:attr/selectableItemBackground" android:padding="12dp" android:layout_marginBottom="10dp" android:clickable="true" android:focusable="true">
  <ImageView android:layout_width="42dp" android:layout_height="42dp" android:padding="10dp" android:background="@drawable/bg_ic_cyan" android:src="@drawable/ic_sync" android:tint="#00E5FF" android:tag="ic:#00E5FF"/>
  <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:orientation="vertical" android:layout_marginStart="14dp">
    <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="AUTO TRANSLATE" android:textColor="#FFFFFF" android:textSize="14sp" android:textStyle="bold"/>
    <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="Terjemahkan teks game ke Indonesia, tombol melayang (root)" android:textColor="#8B95A3" android:textSize="11sp" android:layout_marginTop="2dp"/>
  </LinearLayout>
  <ImageView android:layout_width="20dp" android:layout_height="20dp" android:src="@drawable/ic_chevron" android:tint="#556070"/>
</LinearLayout>'''

METHOD = '''private void toggleTranslate() {
        if (!android.provider.Settings.canDrawOverlays(this)) {
            android.widget.Toast.makeText(this, "Aktifkan 'Tampilkan di atas aplikasi lain' untuk Arkael", android.widget.Toast.LENGTH_LONG).show();
            startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:" + getPackageName())));
            return;
        }
        if (TranslateService.running) {
            stopService(new Intent(this, TranslateService.class));
            android.widget.Toast.makeText(this, "Auto Translate dimatikan", android.widget.Toast.LENGTH_SHORT).show();
        } else {
            startService(new Intent(this, TranslateService.class));
            android.widget.Toast.makeText(this, "Auto Translate aktif. Buka game, tap tombol biru \\u6587A", android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private void launchKeyboxGenerator() {'''

L1 = 'findViewById(R.id.btn_game_launcher).setOnClickListener(v -> startActivity(new Intent(this, GameLauncherActivity.class)));'
K1 = '<LinearLayout android:id="@+id/btn_keybox_generator"'
K2 = 'private void launchKeyboxGenerator() {'
AM = 'android:label="Translate" android:exported="true"'

# (path, marker, old, new)
STEPS = [
    (LAY, "btn_translate", K1, CARD + K1),
    (MAIN, "btn_translate", L1, L1 + "\n        findViewById(R.id.btn_translate).setOnClickListener(v -> toggleTranslate());"),
    (MAIN, "private void toggleTranslate", K2, METHOD),
    (SVC, "volatile boolean running", "    private boolean busy = false, showing = false;",
        "    private boolean busy = false, showing = false;\n    static volatile boolean running = false;"),
    (SVC, "running = true;", "        super.onCreate();", "        super.onCreate();\n        running = true;"),
    (SVC, "running = false;\n        super.onDestroy", "        super.onDestroy();", "        running = false;\n        super.onDestroy();"),
    (ACT, "finishAndRemoveTask", "        finish();", "        finishAndRemoveTask();"),
    (MAN, 'android:taskAffinity=""', AM, AM + ' android:taskAffinity="" android:noHistory="true"'),
]

texts = {}
for p in (LAY, MAIN, SVC, ACT, MAN):
    texts[p] = open(p, encoding="utf-8").read()

errors, changed = [], set()
for path, marker, old, new in STEPS:
    t = texts[path]
    if marker in t:
        continue
    if t.count(old) != 1:
        errors.append(path + ": anchor tidak unik/hilang -> " + old[:60])
        continue
    texts[path] = t.replace(old, new)
    changed.add(path)

if errors:
    print("GAGAL (tidak ada yang diubah):")
    for e in errors:
        print(" - " + e)
    sys.exit(1)
if not changed:
    print("SUDAH DITERAPKAN"); sys.exit(0)

for p in changed:
    open(p, "w", encoding="utf-8").write(texts[p])
print("OK: kartu AUTO TRANSLATE ada di Tools + ikon Translate tidak lagi membuka Arkael Manager")
print("Diubah: " + ", ".join(sorted(os.path.basename(p) for p in changed)))
