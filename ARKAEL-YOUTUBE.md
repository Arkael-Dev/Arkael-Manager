# Arkael YouTube — panduan fitur

Branch: `feature/arkael-yt-revanced` (turunan `feature/ai-agent-native`, tidak mengubahnya).

## Sudah ada sebelumnya
- Pemutaran tanpa iklan: URL stream diambil langsung lewat NewPipeExtractor ke CDN Google,
  jadi tidak ada break iklan, tidak butuh login.
- Cari, putar, putar berikutnya/sebelumnya, mini-player, PiP, kontrol gestur (kecerahan/volume).
- Audio di latar belakang + notifikasi media.
- Unduh video/audio, riwayat, lanjut dari posisi terakhir.
- SponsorBlock, Return YouTube Dislike, DeArrow.

## Baru di branch ini
| Fitur | Di mana |
|---|---|
| Chip **🌐 Jaringan** di pemutar | ganti User-Agent (bawaan / YouTube Android / Chrome / kustom), atur batas cache, kosongkan cache |
| **Cache video** Media3 (0 / 128 MB / 256 MB / 512 MB / 1 GB / 2 GB) | bagian yang sudah ditonton tidak diunduh ulang, seek ke belakang instan |
| Header HTTP browser (UA, Referer, Accept-Language) untuk semua stream | memperbaiki video yang menolak diputar (403) |
| **Pilihan kategori SponsorBlock** (sponsor, selfpromo, interaction, intro, outro, preview, music_offtopic, filler) | Extras → tombol *Kategori…*; default tetap 3 kategori aman |
| Label & menu memakai nama **Arkael YouTube** | `AndroidManifest.xml`, beranda |
| `YtNet.java`, `YtFeed.java` | modul jaringan/cache dan feed channel lokal |

## Perbaikan UI (tanpa login Google)
- **Garis merah "lanjut tonton"** di bawah thumbnail hasil pencarian, riwayat, dan beranda:
  langsung kelihatan video mana yang belum selesai.
- **Tekan lama** sebuah video → Putar sekarang / Simpan ke playlist / Salin link / Mulai dari awal.
- **Tombol ✕** di tab Riwayat untuk membuang satu video.
- **Refresh lebih cepat**: balik dari pemutar tidak memuat ulang beranda (hanya kalau riwayat
  berubah atau sudah 5 menit); tab Riwayat/Disukai instan dari data lokal.
- Tap chip kategori = cari kategori, **tekan lama = cari kata intinya** saja.
- Judul app: **Arkael YouTube**; teks bantuan diperbarui.

## Belum
- Beranda "langganan" dari channel yang diikuti (kerangka `YtFeed` sudah ada, UI belum).
- Login Google untuk langganan akun asli.

## Soal login Google — baca ini
1. Untuk **nonton** tidak perlu login. Yang didapat dari login cuma: langganan pribadi,
   riwayat akun, playlist pribadi, video khusus anggota/berbayar.
2. Google memblokir OAuth di WebView (`disallowed_useragent`) dan sudah menegur/mencabut akses
   aplikasi pihak ketiga (Vanced/ReVanced). Memaksa login di app seperti ini berisiko
   **akun ditangguhkan**.
3. Jalur yang relatif aman: login lewat **Chrome Custom Tab** (bukan WebView) dengan OAuth client
   milik kamu sendiri, lalu tarik playlist/langganan lewat InnerTube. Ini pekerjaan terpisah,
   butuh project di Google Cloud Console dan kode auth, dan tetap melanggar ToS YouTube.

Keputusan: fitur login tidak dinyalakan di branch ini sampai kamu setuju menanggung risikonya.

## Build
Workflow `.github/workflows/branch-build.yml` otomatis membuat APK bertanda tangan untuk setiap
branch selain `main`. Push branch ini lalu buka **Actions → Branch build (test) → Artifacts**.
