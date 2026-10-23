# Jalankan dari ~/Arkael-Manager :  python3 apply_equalizer.py
# (setelah unzip arkael_equalizer_v12.zip ke ~/Arkael-Manager)
# Equalizer sistem (root): manifest (activity + service + izin), kartu di layar utama, klik listener,
# dan aturan proguard supaya kelas daemon root tidak dihapus/diganti nama saat build release.
import os
B = 'app/src/main/java/com/arkael/app/'
MAN, LAY, MAIN = 'app/src/main/AndroidManifest.xml', 'app/src/main/res/layout/activity_main.xml', B + 'MainActivity.java'
PRO = 'app/proguard-rules.pro'
ok, bad = [], []

for need in (B + 'EqConfig.java', B + 'EqEngine.java', B + 'EqRootDaemon.java', B + 'EqService.java',
             B + 'EqualizerActivity.java', 'app/src/main/res/drawable/ic_equalizer.xml'):
    if not os.path.exists(need):
        print('GAGAL:', need, 'belum ada. Unzip arkael_equalizer_v12.zip ke ~/Arkael-Manager dulu.'); raise SystemExit(1)

def load(f):
    if not os.path.exists(f):
        bad.append(f + ' (tidak ditemukan)'); return None
    return open(f, encoding='utf8').read()

def save(f, s):
    open(f, 'w', encoding='utf8').write(s)

# ---- manifest
s = load(MAN)
if s is not None:
    if '.EqualizerActivity' in s and '.EqService' in s:
        ok.append('manifest (sudah terpasang)')
    else:
        block = ('<activity android:name=".EqualizerActivity" android:exported="false" android:label="Equalizer"\n'
                 '            android:configChanges="orientation|screenSize|keyboardHidden"/>\n\n'
                 '        <service android:name=".EqService" android:exported="false" android:foregroundServiceType="specialUse">\n'
                 '            <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="audio_effects"/>\n'
                 '        </service>\n\n        ')
        a = '<service android:name=".GameService"'
        if s.count(a) == 1:
            s = s.replace(a, block + a, 1)
        elif s.count('</application>') == 1:
            s = s.replace('</application>', block + '</application>', 1)
        else:
            bad.append('manifest: anchor GameService / </application> tidak ketemu'); s = None
        if s is not None:
            need = ['android.permission.MODIFY_AUDIO_SETTINGS', 'android.permission.FOREGROUND_SERVICE',
                    'android.permission.FOREGROUND_SERVICE_SPECIAL_USE', 'android.permission.POST_NOTIFICATIONS']
            miss = [p for p in need if p not in s]
            if miss and s.count('<application') == 1:
                s = s.replace('<application', ''.join('<uses-permission android:name="%s"/>\n    ' % p for p in miss) + '<application', 1)
            elif miss:
                bad.append('manifest: izin %s tambahkan manual' % ', '.join(miss))
            save(MAN, s); ok.append('manifest (+%d izin)' % len(miss))

# ---- kartu di layar utama (tepat sebelum kartu Game Launcher)
CARD = ('<LinearLayout android:id="@+id/btn_equalizer" android:layout_width="match_parent" android:layout_height="wrap_content" android:minHeight="66dp" android:orientation="horizontal" android:gravity="center_vertical" android:tag="card" android:background="@drawable/bg_card_rounded" android:foreground="?android:attr/selectableItemBackground" android:padding="12dp" android:layout_marginBottom="10dp" android:clickable="true" android:focusable="true">\n'
        '  <ImageView android:layout_width="42dp" android:layout_height="42dp" android:padding="10dp" android:background="@drawable/bg_ic_cyan" android:src="@drawable/ic_equalizer" android:tint="#00E5FF" android:tag="ic:#00E5FF"/>\n'
        '  <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:orientation="vertical" android:layout_marginStart="14dp">\n'
        '    <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="EQUALIZER" android:textColor="#FFFFFF" android:textSize="14sp" android:textStyle="bold"/>\n'
        '    <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="EQ 10-band, bass, surround 3D untuk semua suara (root)" android:textColor="#8B95A3" android:textSize="11sp" android:layout_marginTop="2dp"/>\n'
        '  </LinearLayout>\n'
        '  <ImageView android:layout_width="20dp" android:layout_height="20dp" android:src="@drawable/ic_chevron" android:tint="#556070"/>\n'
        '</LinearLayout>')
s = load(LAY)
if s is not None:
    if 'btn_equalizer' in s:
        ok.append('layout (sudah terpasang)')
    else:
        done = False
        for a in ('<LinearLayout android:id="@+id/btn_game_launcher"', '<LinearLayout android:id="@+id/btn_keybox_generator"'):
            if s.count(a) == 1:
                save(LAY, s.replace(a, CARD + a, 1)); ok.append('layout'); done = True; break
        if not done:
            bad.append('layout: anchor btn_game_launcher / btn_keybox_generator tidak ketemu')

# ---- klik listener
s = load(MAIN)
if s is not None:
    if 'EqualizerActivity' in s:
        ok.append('MainActivity (sudah terpasang)')
    else:
        done = False
        for a in ('findViewById(R.id.btn_game_launcher).setOnClickListener', 'findViewById(R.id.btn_keybox_generator).setOnClickListener'):
            if s.count(a) == 1:
                new = ('findViewById(R.id.btn_equalizer).setOnClickListener(v -> startActivity(new Intent(this, EqualizerActivity.class)));\n'
                       '        ' + a)
                save(MAIN, s.replace(a, new, 1)); ok.append('MainActivity'); done = True; break
        if not done:
            bad.append('MainActivity: anchor btn_game_launcher / btn_keybox_generator tidak ketemu')

# ---- proguard: daemon root dipanggil lewat nama kelas dari app_process
KEEP = ('\n# Arkael Equalizer: daemon root dijalankan lewat app_process dengan nama kelas ini\n'
        '-keep class com.arkael.app.EqRootDaemon { public static void main(java.lang.String[]); }\n')
if os.path.exists(PRO):
    s = load(PRO)
    if 'EqRootDaemon' in s:
        ok.append('proguard (sudah terpasang)')
    else:
        save(PRO, s.rstrip('\n') + '\n' + KEEP); ok.append('proguard')
else:
    ok.append('proguard (file tidak ada, dilewati)')

print('OK  :', ', '.join(ok) or '-')
print('GAGAL:', ', '.join(bad) or '-')
if bad: print('Bagian yang GAGAL tidak diubah. Kirim output ini ke saya.')
