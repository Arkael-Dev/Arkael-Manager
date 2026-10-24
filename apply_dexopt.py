# Jalankan dari ~/Arkael-Manager :  python3 apply_dexopt.py
# Dex2oat Optimizer v23: kartu dipasang dari MainActivity (tidak bergantung isi ExtrasEntry.java).
import glob, os, re, sys

D = 'app/src/main/java/com/arkael/app/'
MF = 'app/src/main/AndroidManifest.xml'
report = []
for need in ('DexOptActivity.java', 'DexOptEntry.java'):
    if not os.path.exists(D + need):
        print('GAGAL: %s belum ada. Unzip arkael_dexopt_v23.zip di ~/Arkael-Manager dulu.' % need); sys.exit(1)
for need in ('Tr.java', 'Notifier.java'):
    if not os.path.exists(D + need):
        print('GAGAL: %s tidak ada. Tidak ada yang diubah.' % need); sys.exit(1)

# ---- Manifest
if not os.path.exists(MF):
    report.append('GAGAL  Manifest tidak ditemukan (jalankan dari root repo)')
else:
    m = open(MF, encoding='utf-8').read()
    if '.DexOptActivity' in m:
        report.append('SUDAH  Manifest')
    elif '</application>' not in m:
        report.append('GAGAL  Manifest: </application> tidak ketemu')
    else:
        j = m.rindex('</application>'); ls = m.rfind('\n', 0, j) + 1
        blk = '        <activity android:name=".DexOptActivity" android:exported="false" android:label="Dex2oat Optimizer"/>\n'
        open(MF, 'w', encoding='utf-8').write(m[:ls] + blk + m[ls:])
        report.append('OK     Manifest: DexOptActivity didaftarkan')

# ---- kalau patch v22 sudah memasang kartu di ExtrasEntry, jangan dobel
ex = D + 'ExtrasEntry.java'
if os.path.exists(ex) and 'DexOptActivity' in open(ex, encoding='utf-8').read():
    report.append('SUDAH  Kartu sudah dipasang lewat ExtrasEntry (v22), tidak perlu hook MainActivity')
else:
    mas = glob.glob('app/src/main/java/**/MainActivity.java', recursive=True)
    if not mas:
        report.append('GAGAL  MainActivity.java tidak ketemu')
    else:
        ma = mas[0]
        s = open(ma, encoding='utf-8').read()
        if 'DexOptEntry.attach' in s:
            report.append('SUDAH  Hook MainActivity')
        else:
            pat = re.compile(r'^([ \t]*)ExtrasEntry\.attach\(this\);[^\n]*$', re.M)
            if not pat.search(s):
                report.append('GAGAL  "ExtrasEntry.attach(this);" tidak ada di MainActivity. Tambahkan manual di onCreate setelah setContentView:')
                report.append('       try { DexOptEntry.attach(this); } catch (Throwable ignored) {}')
            else:
                s = pat.sub(lambda mm: mm.group(0) + '\n' + mm.group(1) + 'try { DexOptEntry.attach(this); } catch (Throwable ignored) {}', s, count=1)
                open(ma, 'w', encoding='utf-8').write(s)
                report.append('OK     MainActivity: kartu Dex2oat dipasang setelah ExtrasEntry.attach')

print('\n'.join(report))
print('\nLanjut: build ulang & install, lalu git add -A && git commit -m "feat: Dex2oat Optimizer" && git push')
