# Jalankan dari ~/Arkael-Manager :  python3 ui_apply.py
import re, shutil
R='app/src/main/res'; J='app/src/main/java/com/arkael/app/MainActivity.java'
X=R+'/layout/activity_main.xml'
shutil.copy(X,X+'.bak'); shutil.copy(J,J+'.bak')
x=open(X,encoding='utf8').read(); j=open(J,encoding='utf8').read()

LIN={'btn_cpu_gov':'ic_cpu','btn_set_zram':'ic_ram','btn_thermal':'ic_thermal','btn_clean_ram':'ic_trash',
 'btn_gpu_control':'ic_gpu','btn_io_scheduler':'ic_storage','btn_zram_algo':'ic_ram','btn_saturation':'ic_palette',
 'btn_renderer':'ic_gpu','btn_vsync':'ic_sync','btn_keybox_generator':'ic_key'}
TV={'btn_change_banner':'ic_image','btn_reset_banner':'ic_sync','btn_theme_glass':'ic_palette',
 'btn_theme_color':'ic_palette','tv_dev_link':'ic_send','tv_channel_link':'ic_send'}
pat=re.compile(r'@android:drawable/[a-z_0-9]+')
n=0
for table,whole in ((LIN,True),(TV,False)):
    for i,icon in table.items():
        k=x.find('android:id="@+id/%s"'%i)
        if k<0: continue
        s=x.rfind('<',0,k)
        if whole:
            nx=x.find('android:id="@+id/',k+10); e=x.rfind('<',0,nx) if nx>=0 else len(x)
        else:
            e=x.find('>',k)+1
        seg,c=pat.subn('@drawable/'+icon,x[s:e]); n+=c
        x=x[:s]+seg+x[e:]
# sembunyikan label teks "Gov/Zram/Heat/..." yang dipakai sebagai pengganti ikon
def hide(m):
    t=re.sub(r'\s*android:visibility="[^"]*"','',m.group(0))
    return t.replace('<TextView','<TextView android:visibility="gone"',1)
x,h=re.subn(r'<TextView\b[^>]*?android:text="(?:Gov|Zram|Heat|Del|GPU|IO|Algo|Col|GL|Sync|Key)"[^>]*?>',hide,x)
open(X,'w',encoding='utf8').write(x)

# ikon nav bawah + tint ikut warna aktif
for a,b in (('ic_menu_info_details','ic_monitor'),('ic_menu_manage','ic_tools'),('ic_menu_preferences','ic_settings')):
    j=j.replace('(android.R.drawable.%s, 0, 0, 0)'%a,'(R.drawable.%s, 0, 0, 0)'%b)
j,t=re.subn(r'\(\(TextView\)(nav\w+)\.getChildAt\(0\)\)\.setTextColor\(([^;]*)\);',
 r'{ TextView navIc=(TextView)\1.getChildAt(0); navIc.setTextColor(\2); navIc.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(\2)); }',j)
open(J,'w',encoding='utf8').write(j)
print('ikon layout diganti:',n,'| label disembunyikan:',h,'| nav tint:',t)
print('Backup: activity_main.xml.bak & MainActivity.java.bak')
