# Jalankan dari ~/Arkael-Manager :  python3 apply_yt_extras.py
# Pasang di pemutar YouTube: lanjut dari menit terakhir, SponsorBlock, Return YouTube Dislike, DeArrow (judul).
import os
F = 'app/src/main/java/com/arkael/app/YoutubePlayerActivity.java'
ok, bad = [], []

if not os.path.exists(F):
    print('GAGAL: %s tidak ditemukan (jalankan dari root repo)' % F); raise SystemExit(1)
s = open(F, encoding='utf8').read()

if 'YtExtras.' in s and 'showExtrasDialog' in s:
    print('Sudah terpasang, tidak ada yang diubah.'); raise SystemExit(0)

def rep(name, old, new):
    global s
    if s.count(old) != 1:
        bad.append('%s (anchor ditemukan %dx, harusnya 1x)' % (name, s.count(old))); return
    s = s.replace(old, new, 1); ok.append(name)

# 1. field
rep('field', "    private YtStore.Entry curEntry;\n", '''    private YtStore.Entry curEntry;

    // ---- extras: posisi terakhir, SponsorBlock, Return YouTube Dislike, DeArrow
    private final android.os.Handler exHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private java.util.List<YtExtras.Seg> exSegs = new java.util.ArrayList<YtExtras.Seg>();
    private final java.util.Set<Integer> exSkipped = new java.util.HashSet<Integer>();
    private int exTickN;
    private final Runnable exTick = new Runnable() {
        @Override public void run() {
            try { exTickBody(); } catch (Exception ignored) { }
            exHandler.postDelayed(this, 500);
        }
    };
''')

# 2. resume saat mulai putar
rep('resume', "        controller.setMediaItem(item);\n", '''        long exResume = YtExtras.getPosition(this, videoUrl);
        controller.setMediaItem(item, exResume);
        if (exResume > 0) Toast.makeText(this, "Lanjut dari " + fmtDuration(exResume / 1000), Toast.LENGTH_SHORT).show();
''')

# 3. mulai ticker setelah controller siap
rep('ticker', "        controller.addListener(playerListener);\n", "        controller.addListener(playerListener);\n        exStart();\n")

# 4. chip Extras
rep('chip', '        chips.addView(chip("⬇  Download", v -> showDownloadDialog()));\n',
    '        chips.addView(chip("⬇  Download", v -> showDownloadDialog()));\n'
    '        chips.addView(chip("🧩  Extras", v -> showExtrasDialog()));\n')

# 5. muat extras setelah info video diambil
rep('populate', "        loadComments(videoUrl);\n    }\n", "        loadComments(videoUrl);\n        loadExtras(videoUrl);\n    }\n")

# 6. ganti video di tempat: simpan posisi video lama dulu
rep('onNewIntent', "        readIntent(intent);\n", "        exSavePos();\n        exReset();\n        readIntent(intent);\n")

# 7. simpan posisi saat app disembunyikan / ditutup
rep('onStop', "        if (!inPip && !audioMode && player != null) player.pause();\n",
    "        exSavePos();\n        if (!inPip && !audioMode && player != null) player.pause();\n")
rep('onDestroy', "        subHandler.removeCallbacksAndMessages(null);\n",
    "        subHandler.removeCallbacksAndMessages(null);\n        exSavePos();\n        exHandler.removeCallbacksAndMessages(null);\n")

# 8. method
methods = r'''    // ---------------------------------------------------------------- extras

    private boolean exOn(String key) {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(key, true);
    }

    private void exStart() {
        exHandler.removeCallbacks(exTick);
        exHandler.postDelayed(exTick, 500);
    }

    private void exReset() {
        exSegs = new java.util.ArrayList<YtExtras.Seg>();
        exSkipped.clear();
    }

    /** True kalau yang sedang diputar memang video di layar ini (bukan video lama saat berganti). */
    private boolean exCurrent() {
        if (player == null || videoUrl == null) return false;
        MediaItem cur = player.getCurrentMediaItem();
        return cur != null && videoUrl.equals(cur.mediaId);
    }

    private void exTickBody() {
        if (!exCurrent()) return;
        int st = player.getPlaybackState();
        if (st == Player.STATE_ENDED) { YtExtras.clearPosition(this, videoUrl); return; }
        long pos = player.getCurrentPosition();

        if (player.isPlaying() && !exSegs.isEmpty() && exOn("ex_sb")) {
            for (int i = 0; i < exSegs.size(); i++) {
                YtExtras.Seg g = exSegs.get(i);
                if (exSkipped.contains(i)) continue;                 // sekali saja per segmen: kalau diputar ulang, tidak dipaksa
                if (pos >= g.startMs && pos < g.endMs - 400) {
                    exSkipped.add(i);
                    long to = g.endMs;
                    long dur = player.getDuration();
                    if (dur > 0 && to > dur) to = dur;
                    player.seekTo(to);
                    Toast.makeText(this, "⏭ Dilewati: " + YtExtras.categoryLabel(g.category), Toast.LENGTH_SHORT).show();
                    break;
                }
            }
        }
        if (++exTickN % 10 == 0 && player.isPlaying()) exSavePos();   // tiap ~5 detik
    }

    private void exSavePos() {
        try {
            if (!exCurrent() || player.getPlaybackState() == Player.STATE_ENDED) return;
            YtExtras.savePosition(this, videoUrl, player.getCurrentPosition(), player.getDuration());
        } catch (Exception ignored) { }
    }

    private void loadExtras(final String url) {
        final String id = YtExtras.videoId(url);
        if (id == null) return;
        exReset();

        if (exOn("ex_sb")) new Thread(() -> {
            final java.util.List<YtExtras.Seg> segs = YtExtras.sponsorSegments(id);
            runOnUiThread(() -> { if (!isFinishing() && url.equals(videoUrl)) exSegs = segs; });
        }).start();

        if (exOn("ex_ryd")) new Thread(() -> {
            final long[] v = YtExtras.dislikes(id);
            runOnUiThread(() -> {
                if (isFinishing() || !url.equals(videoUrl) || v == null || v[1] < 0) return;
                long l = (curInfo != null && curInfo.getLikeCount() >= 0) ? curInfo.getLikeCount() : v[0];
                likes.setText("👍 " + (l >= 0 ? compact(l) : "?") + "   👎 " + compact(v[1]));
                likes.setVisibility(View.VISIBLE);
            });
        }).start();

        if (exOn("ex_dearrow")) new Thread(() -> {
            final String t = YtExtras.deArrowTitle(id);
            runOnUiThread(() -> {
                if (isFinishing() || !url.equals(videoUrl) || t == null || t.isEmpty()) return;
                title.setText(t);
            });
        }).start();
    }

    private void showExtrasDialog() {
        final String[] keys = {"ex_sb", "ex_ryd", "ex_dearrow"};
        String[] names = {"SponsorBlock (lewati sponsor)", "Return YouTube Dislike (jumlah dislike)", "DeArrow (judul tidak clickbait)"};
        final boolean[] on = new boolean[keys.length];
        for (int i = 0; i < keys.length; i++) on[i] = exOn(keys[i]);
        new AlertDialog.Builder(this)
                .setTitle("Extras")
                .setMultiChoiceItems(names, on, (d, which, checked) ->
                        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(keys[which], checked).apply())
                .setPositiveButton("OK", (d, w) -> {
                    if (curInfo != null) {                                    // kembalikan tampilan asli kalau dimatikan
                        if (!exOn("ex_dearrow") && curInfo.getName() != null) title.setText(curInfo.getName());
                        if (!exOn("ex_ryd") && curInfo.getLikeCount() >= 0) likes.setText("👍 " + compact(curInfo.getLikeCount()));
                    }
                    if (videoUrl != null) loadExtras(videoUrl);
                })
                .setNegativeButton("Batal", null)
                .show();
    }

'''
rep('methods', "    private void enterPip() {\n", methods + "    private void enterPip() {\n")

open(F, 'w', encoding='utf8').write(s) if not bad else None
print('BERHASIL:'); [print('  +', x) for x in ok]
if bad:
    print('GAGAL (file TIDAK diubah sama sekali):'); [print('  !', x) for x in bad]; raise SystemExit(1)
print('Selesai. YtExtras.java sudah ada dari zip.')
