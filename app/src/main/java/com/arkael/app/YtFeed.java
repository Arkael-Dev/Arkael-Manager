package com.arkael.app;

import android.content.Context;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.search.SearchExtractor;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Feed "Beranda" gaya ReVanced: video dari channel yang diikuti, digabung jadi satu daftar
 * lalu diurutkan dari yang paling baru. Tanpa akun Google — daftar channel disimpan di HP.
 *
 * Catatan: ini bukan langganan akun YouTube kamu (itu butuh login). Ini langganan lokal:
 * kamu tempel URL/@handle channel, aplikasi menarik video terbarunya dan menyimpannya di sini.
 */
final class YtFeed {
    private YtFeed() {}

    /** URL contoh yang bisa ditempel user, dirapikan jadi URL channel yang bisa dibaca extractor. */
    static String channelUrl(String input) {
        String s = input == null ? "" : input.trim();
        if (s.isEmpty()) return null;
        if (s.startsWith("@")) s = "https://www.youtube.com/" + s;
        if (!s.startsWith("http")) {
            if (s.startsWith("UC") && s.length() >= 24) s = "https://www.youtube.com/channel/" + s;
            else s = "https://www.youtube.com/@" + s;
        }
        return s;
    }

    /** Ambil video terbaru satu channel, maksimal ~15. Blocking: panggil di thread background. */
    static List<StreamInfoItem> channelLatest(Context c, String url) throws Exception {
        NewPipeBootstrap.ensureInit();
        List<StreamInfoItem> out = new ArrayList<>();
        ChannelInfo ci = ChannelInfo.getInfo(url);
        ListExtractor.InfoItemsPage<StreamInfoItem> page = ci.getRelatedItems();
        if (page != null) out.addAll(page.getItems());
        return out;
    }

    /** Pencarian biasa; dipakai juga untuk "trending" sederhana kalau beranda kosong. */
    static List<InfoItem> search(String query) throws Exception {
        NewPipeBootstrap.ensureInit();
        SearchExtractor ex = ServiceList.YouTube.getSearchExtractor(query);
        SearchInfo info = SearchInfo.getInfo(ex);
        List<InfoItem> out = new ArrayList<>();
        if (info.getRelatedItems() != null) out.addAll(info.getRelatedItems());
        return out;
    }
}
