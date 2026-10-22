package com.arkael.app;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Small built-in ad blocker for the in-app anime browser: host blocklist + cosmetic JS. */
final class AdBlock {
    private AdBlock() {}

    private static final Set<String> HOSTS = new HashSet<>(Arrays.asList(
            "doubleclick.net", "googlesyndication.com", "googleadservices.com", "googletagservices.com",
            "googletagmanager.com", "google-analytics.com", "adservice.google.com", "pagead2.googlesyndication.com",
            "adnxs.com", "rubiconproject.com", "pubmatic.com", "criteo.com", "criteo.net", "amazon-adsystem.com",
            "taboola.com", "outbrain.com", "mgid.com", "revcontent.com", "adskeeper.com", "adsterra.com",
            "highperformanceformat.com", "profitablegatecpm.com", "effectivegatecpm.com", "profitabledisplaynetwork.com",
            "popads.net", "popcash.net", "propellerads.com", "propellerclick.com", "onclickads.net", "onclkds.com",
            "exoclick.com", "exosrv.com", "juicyads.com", "trafficjunky.net", "clickadu.com", "hilltopads.net",
            "a-ads.com", "ad-maven.com", "admaven.com", "adcash.com", "monetag.com", "richads.com", "realsrv.com",
            "tsyndicate.com", "trafficstars.com", "plugrush.com", "bidvertiser.com", "yllix.com", "adtng.com",
            "histats.com", "dtscout.com", "acscdn.com", "adsco.re", "pushails.com", "notifpush.com",
            "an.yandex.ru", "mc.yandex.ru", "ads.yahoo.com", "adform.net", "smartadserver.com", "casalemedia.com",
            "scorecardresearch.com", "quantserve.com", "hotjar.com", "clarity.ms"));

    private static final String[] URL_PARTS = {
            "/popunder", "popads.", "pop_under", "/adsbygoogle", "doubleclick.", "googlesyndication."};

    static boolean isBlocked(String url, String host) {
        if (host != null) {
            String h = host.toLowerCase(Locale.ROOT);
            while (true) {
                if (HOSTS.contains(h)) return true;
                int i = h.indexOf('.');
                if (i < 0 || h.indexOf('.', i + 1) < 0) break;   // keep at least "a.b"
                h = h.substring(i + 1);
            }
            if (HOSTS.contains(h)) return true;
        }
        if (url != null) {
            String u = url.toLowerCase(Locale.ROOT);
            for (String p : URL_PARTS) if (u.contains(p)) return true;
        }
        return false;
    }

    /** Runs on every page: kills window.open pop-ups, hides ad containers, removes invisible click-hijack layers. */
    static final String JS =
            "(function(){try{"
            + "if(window.__axab)return;window.__axab=1;"
            + "window.open=function(){return null;};"
            + "var css='ins.adsbygoogle,[id^=\"div-gpt-ad\"],[id^=\"google_ads\"],iframe[src*=\"doubleclick\"],"
            + "iframe[src*=\"googlesyndication\"],a[rel~=\"sponsored\"],.adsbox,.ad-banner,.ads-banner,.banner-ads,"
            + "#floating-ads,.floating-ads,.sticky-ads{display:none!important}';"
            + "var st=document.createElement('style');st.textContent=css;(document.head||document.documentElement).appendChild(st);"
            + "function sweep(){try{var W=innerWidth,H=innerHeight,els=document.body?document.body.querySelectorAll('div,a,span,iframe'):[];"
            + "for(var i=0;i<els.length;i++){var e=els[i],s=getComputedStyle(e);"
            + "if(s.position!=='fixed'&&s.position!=='absolute')continue;"
            + "var r=e.getBoundingClientRect();"
            + "if(r.width<W*0.8||r.height<H*0.8)continue;"
            + "if(e.querySelector('video,canvas,input,button,img,svg,iframe[src*=\"embed\"]'))continue;"
            + "if(e.tagName==='IFRAME'&&/embed|player|video|stream/i.test(e.src||''))continue;"
            + "if((e.innerText||'').trim().length>0)continue;"
            + "var bg=s.backgroundColor;var clear=(bg==='rgba(0, 0, 0, 0)'||bg==='transparent'||parseFloat(s.opacity)<0.1);"
            + "if(clear&&parseInt(s.zIndex||'0',10)>=100){e.remove();}}}catch(x){}}"
            + "sweep();setInterval(sweep,1500);"
            + "}catch(x){}})();";
}
