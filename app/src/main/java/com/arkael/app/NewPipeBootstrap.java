package com.arkael.app;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;

/**
 * Plain java.net.HttpURLConnection-based Downloader for NewPipeExtractor. No extra HTTP library
 * needed; NewPipe only needs something that can send a request and hand back status/body/headers.
 */
final class NewPipeBootstrap {
    private static volatile boolean init;

    static synchronized void ensureInit() {
        if (init) return;
        NewPipe.init(new Downloader() {
            @Override public Response execute(Request req) throws IOException, ReCaptchaException {
                HttpURLConnection c = null;
                try {
                    URL url = new URL(req.url());
                    c = (HttpURLConnection) url.openConnection();
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(20000);
                    c.setInstanceFollowRedirects(true);
                    c.setRequestMethod(req.httpMethod());
                    if (req.headers() != null) {
                        for (Map.Entry<String, List<String>> e : req.headers().entrySet()) {
                            for (String v : e.getValue()) c.addRequestProperty(e.getKey(), v);
                        }
                    }
                    if (c.getRequestProperty("User-Agent") == null) {
                        c.setRequestProperty("User-Agent",
                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Safari/537.36");
                    }
                    byte[] body = req.dataToSend();
                    if (body != null) {
                        c.setDoOutput(true);
                        OutputStream os = c.getOutputStream();
                        os.write(body);
                        os.flush();
                        os.close();
                    }
                    int code = c.getResponseCode();
                    InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
                    String bodyStr = is == null ? "" : readAll(is);
                    if (code == 429) throw new ReCaptchaException("reCAPTCHA / rate limit", req.url());
                    return new Response(code, c.getResponseMessage(), c.getHeaderFields(), bodyStr, c.getURL().toString());
                } finally {
                    if (c != null) c.disconnect();
                }
            }
        });
        init = true;
    }

    private static String readAll(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
        return bos.toString("UTF-8");
    }

    private NewPipeBootstrap() {}
}
