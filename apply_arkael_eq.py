# Jalankan dari ~/Arkael-Manager :  python3 apply_arkael_eq.py
# (setelah unzip arkael_equalizer_v13.zip ke ~/Arkael-Manager)
# 1) Arkael Equalizer: ganti nama (manifest, kartu layar utama, notifikasi), izin RECORD_AUDIO untuk visualizer
# 2) Downscale per game di Game Launcher (baris "Downscale" di panel preview + diterapkan saat Mainkan)
import os, re
B = 'app/src/main/java/com/arkael/app/'
MAN, LAY = 'app/src/main/AndroidManifest.xml', 'app/src/main/res/layout/activity_main.xml'
SVC, GL = B + 'EqService.java', B + 'GameLauncherActivity.java'
ok, bad = [], []

for need in (B + 'EqVisual.java', B + 'EqVizSource.java', B + 'EqPresetStore.java', B + 'EqText.java', B + 'AppDownscale.java',
             B + 'EqualizerActivity.java', 'app/src/main/res/values/strings_eq.xml'):
    if not os.path.exists(need):
        print('GAGAL:', need, 'belum ada. Unzip arkael_equalizer_v13.zip ke ~/Arkael-Manager dulu.'); raise SystemExit(1)

def rd(f): return open(f, encoding='utf8').read()
def wr(f, s): open(f, 'w', encoding='utf8').write(s)

# ---- manifest: label + izin mic
if os.path.exists(MAN):
    s = rd(MAN); n = s
    n = n.replace('android:name=".EqualizerActivity" android:exported="false" android:label="Equalizer"',
                  'android:name=".EqualizerActivity" android:exported="false" android:label="@string/ax_eq_title"')
    if 'android.permission.RECORD_AUDIO' not in n and n.count('<application') == 1:
        n = n.replace('<application', '<uses-permission android:name="android.permission.RECORD_AUDIO"/>\n    <application', 1)
    if n != s: wr(MAN, n); ok.append('manifest')
    else: ok.append('manifest (sudah)')
else:
    bad.append('manifest tidak ditemukan')

# ---- kartu layar utama
if os.path.exists(LAY):
    s = rd(LAY)
    i = s.find('android:id="@+id/btn_equalizer"')
    if i < 0:
        bad.append('layout: kartu btn_equalizer tidak ada (jalankan apply_equalizer.py v12 dulu)')
    else:
        head, tail = s[:i], s[i:]
        tail2 = tail.replace('android:text="EQUALIZER"', 'android:text="@string/ax_eq_title"', 1)
        tail2 = re.sub(r'android:text="EQ 10-band, bass, surround 3D[^"]*"', 'android:text="@string/ax_eq_card_desc"', tail2, count=1)
        if tail2 != tail: wr(LAY, head + tail2); ok.append('layout')
        else: ok.append('layout (sudah)')
else:
    bad.append('layout tidak ditemukan')

# ---- notifikasi EqService
if os.path.exists(SVC):
    s = rd(SVC); n = s
    n = n.replace('new NotificationChannel(CH, "Equalizer", ', 'new NotificationChannel(CH, getString(R.string.ax_eq_title), ')
    n = n.replace('.setContentTitle("Equalizer aktif")', '.setContentTitle(getString(R.string.ax_eq_note_title))')
    n = n.replace('.setContentText(status)', '.setContentText(EqText.loc(this, status))')
    n = n.replace('new Notification.Action.Builder(0, "Matikan", stop)', 'new Notification.Action.Builder(0, getString(R.string.ax_eq_note_stop), stop)')
    if n != s: wr(SVC, n); ok.append('EqService')
    else: ok.append('EqService (sudah)')
else:
    bad.append('EqService tidak ditemukan')

# ---- downscale di Game Launcher
if not os.path.exists(GL):
    bad.append('GameLauncherActivity tidak ditemukan')
else:
    s = rd(GL)
    if 'AppDownscale' in s:
        ok.append('GameLauncher (sudah)')
    else:
        a_field = 'private TextView pvName, pvSub, emptyView, stBat, stCpu, stGpu, ovText;'
        a_view = '        pvContent.addView(pvSub, new LinearLayout.LayoutParams(-1, -2));\n'
        a_upd = '        pvSub.setText(profileSummary(g.pkg));\n'
        a_run = '            try { Booster.applyProfile(this, GameProfiles.get(this, g.pkg), false); } catch (Throwable ignored) {}\n'
        if all(s.count(a) == 1 for a in (a_field, a_view, a_upd, a_run)):
            s = s.replace(a_field, a_field + '\n    private TextView pvScale;', 1)
            view = (a_view +
                    '        pvScale = new TextView(this);\n'
                    '        pvScale.setTextColor(Color.WHITE);\n'
                    '        pvScale.setTextSize(11);\n'
                    '        pvScale.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));\n'
                    '        pvScale.setGravity(Gravity.CENTER);\n'
                    '        pvScale.setPadding(dp(14), dp(5), dp(14), dp(5));\n'
                    '        pvScale.setBackground(new Cut(Color.parseColor("#40181820"), Color.parseColor("#99FF1E3C"), dp(1.2f), dp(8), true, true, true, true));\n'
                    '        pvScale.setOnClickListener(v -> { if (!games.isEmpty()) { playFx(sTab, 0.9f); Game gg = games.get(sel);\n'
                    '            AppDownscale.showPicker(this, gg.pkg, gg.name, () -> updatePreview(false)); } });\n'
                    '        LinearLayout.LayoutParams psl = new LinearLayout.LayoutParams(-2, -2);\n'
                    '        psl.topMargin = dp(8);\n'
                    '        pvContent.addView(pvScale, psl);\n')
            s = s.replace(a_view, view, 1)
            s = s.replace(a_upd, a_upd + '        if (pvScale != null) pvScale.setText(getString(R.string.ax_ds_row, AppDownscale.label(this, AppDownscale.get(this, g.pkg))));\n', 1)
            s = s.replace(a_run, a_run + '            try { AppDownscale.applyFor(this, g.pkg); } catch (Throwable ignored) {}\n', 1)
            wr(GL, s); ok.append('GameLauncher (downscale)')
        else:
            bad.append('GameLauncher: anchor tidak cocok (file bukan v8?)')

print('OK  :', ', '.join(ok) or '-')
print('GAGAL:', ', '.join(bad) or '-')
if bad: print('Bagian yang GAGAL tidak diubah. Kirim output ini ke saya.')
