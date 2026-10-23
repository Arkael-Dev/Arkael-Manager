# Run from ~/Arkael-Manager :  python3 apply_gamelauncher.py
# (after unzipping arkael_gamelauncher.zip into ~/Arkael-Manager)
# Game Launcher: manifest entry + QUERY_ALL_PACKAGES, card in the main screen's tools list, click listener.
import os
B = 'app/src/main/java/com/arkael/app/'
MAN, LAY, MAIN = 'app/src/main/AndroidManifest.xml', 'app/src/main/res/layout/activity_main.xml', B + 'MainActivity.java'
ok, bad = [], []

def load(f):
    if not os.path.exists(f):
        bad.append(f + ' (tidak ditemukan)'); return None
    return open(f, encoding='utf8').read()

for need in (B + 'GameLauncherActivity.java', 'app/src/main/res/raw/gl_open.wav', 'app/src/main/res/raw/gl_launch.wav', 'app/src/main/res/drawable/ic_gamepad.xml'):
    if not os.path.exists(need):
        print('GAGAL:', need, 'belum ada. Unzip arkael_gamelauncher.zip ke ~/Arkael-Manager dulu.'); raise SystemExit

# ---- manifest
s = load(MAN)
if s is not None:
    if 'GameLauncherActivity' in s:
        ok.append('manifest (sudah terpasang)')
    else:
        a = '<service android:name=".GameService"'
        if a not in s: bad.append('manifest: anchor GameService tidak ketemu')
        else:
            s = s.replace(a, '<activity android:name=".GameLauncherActivity" android:exported="false" android:label="Game Launcher"\n'
                             '            android:configChanges="orientation|screenSize|keyboardHidden"/>\n\n        ' + a, 1)
            if 'QUERY_ALL_PACKAGES' not in s:
                if '<application' in s:
                    s = s.replace('<application', '<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES"/>\n    <application', 1)
                else: bad.append('manifest: <application> tidak ketemu')
            open(MAN, 'w', encoding='utf8').write(s); ok.append('manifest')

# ---- layout card (placed right before the keybox card, i.e. after YouTube)
CARD = ('<LinearLayout android:id="@+id/btn_game_launcher" android:layout_width="match_parent" android:layout_height="wrap_content" android:minHeight="66dp" android:orientation="horizontal" android:gravity="center_vertical" android:tag="card" android:background="@drawable/bg_card_rounded" android:foreground="?android:attr/selectableItemBackground" android:padding="12dp" android:layout_marginBottom="10dp" android:clickable="true" android:focusable="true">\n'
        '  <ImageView android:layout_width="42dp" android:layout_height="42dp" android:padding="10dp" android:background="@drawable/bg_ic_cyan" android:src="@drawable/ic_gamepad" android:tint="#00E5FF" android:tag="ic:#00E5FF"/>\n'
        '  <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:orientation="vertical" android:layout_marginStart="14dp">\n'
        '    <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="GAME LAUNCHER" android:textColor="#FFFFFF" android:textSize="14sp" android:textStyle="bold"/>\n'
        '    <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="Semua game di HP ini, sekali ketuk langsung main" android:textColor="#8B95A3" android:textSize="11sp" android:layout_marginTop="2dp"/>\n'
        '  </LinearLayout>\n'
        '  <ImageView android:layout_width="20dp" android:layout_height="20dp" android:src="@drawable/ic_chevron" android:tint="#556070"/>\n'
        '</LinearLayout>')
s = load(LAY)
if s is not None:
    if 'btn_game_launcher' in s: ok.append('layout (sudah terpasang)')
    else:
        a = '<LinearLayout android:id="@+id/btn_keybox_generator"'
        if a not in s: bad.append('layout: anchor btn_keybox_generator tidak ketemu')
        else:
            s = s.replace(a, CARD + a, 1)
            open(LAY, 'w', encoding='utf8').write(s); ok.append('layout')

# ---- click listener
s = load(MAIN)
if s is not None:
    if 'GameLauncherActivity' in s: ok.append('MainActivity (sudah terpasang)')
    else:
        a = '        findViewById(R.id.btn_keybox_generator).setOnClickListener'
        if a not in s: bad.append('MainActivity: anchor btn_keybox_generator tidak ketemu')
        else:
            s = s.replace(a, '        findViewById(R.id.btn_game_launcher).setOnClickListener(v -> startActivity(new Intent(this, GameLauncherActivity.class)));\n' + a, 1)
            open(MAIN, 'w', encoding='utf8').write(s); ok.append('MainActivity')

print('OK  :', ', '.join(ok) or '-')
print('GAGAL:', ', '.join(bad) or '-')
if bad: print('Bagian yang GAGAL tidak diubah. Kirim output ini ke saya.')
