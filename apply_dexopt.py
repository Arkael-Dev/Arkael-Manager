# Jalankan dari ~/Arkael-Manager :  python3 apply_dexopt.py
# Menambah "Dex2oat Optimizer" (kartu di tab Tools + layar sendiri + registrasi manifest).
import os, re, sys

D = 'app/src/main/java/com/arkael/app/'
MF = 'app/src/main/AndroidManifest.xml'
EX = D + 'ExtrasEntry.java'
report = []

if not os.path.exists(D + 'DexOptActivity.java'):
    print('GAGAL: DexOptActivity.java belum ada. Unzip arkael_dexopt_v22.zip di ~/Arkael-Manager dulu.'); sys.exit(1)
for need in ('Tr.java', 'Notifier.java'):
    if not os.path.exists(D + need):
        print('GAGAL: %s tidak ada (butuh paket bahasa v15 / extras). Tidak ada yang diubah.' % need); sys.exit(1)

# ---- Manifest
if not os.path.exists(MF):
    report.append('GAGAL  Manifest tidak ditemukan')
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

# ---- Kartu di tab Tools
if not os.path.exists(EX):
    report.append('GAGAL  ExtrasEntry.java tidak ada: pasang kartu manual -> startActivity(new Intent(this, DexOptActivity.class))')
else:
    s = open(EX, encoding='utf-8').read()
    if 'DexOptActivity' in s:
        report.append('SUDAH  Kartu Tools')
    else:
        pat = re.compile(r'^([ \t]*)card\(a, col, R\.drawable\.ic_cpu, R\.drawable\.bg_ic_cyan', re.M)
        found = pat.findall(s)
        if len(found) != 1:
            report.append('GAGAL  Kartu: titik sisip tidak ketemu (%d). Tambahkan manual di ExtrasEntry.attach:' % len(found))
            report.append('       card(a, col, R.drawable.ic_cpu, R.drawable.bg_ic_red, "#FFB300", DexOptActivity.cardTitle(), DexOptActivity.cardSub(), DexOptActivity.class);')
        else:
            ind = found[0]
            new = (ind + 'card(a, col, R.drawable.ic_cpu, R.drawable.bg_ic_red, "#FFB300",\n'
                   + ind + '        DexOptActivity.cardTitle(), DexOptActivity.cardSub(), DexOptActivity.class);\n')
            s = pat.sub(lambda mm: new + mm.group(0), s, count=1)
            open(EX, 'w', encoding='utf-8').write(s)
            report.append('OK     Kartu "Dex2oat Optimizer" ditambah di tab Tools')

print('\n'.join(report))
print('\nLanjut: git add -A && git commit -m "feat: Dex2oat Optimizer" && git push')
