package com.arkael.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;

/**
 * Invisible activity that asks Android for the screen/audio capture grant. With root the dialog is
 * normally skipped (PROJECT_MEDIA appop is allowed first); otherwise the user taps "Start now" once.
 */
public class AudioConsentActivity extends Activity {
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LocaleWrap.wrap(base));
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            MediaProjectionManager m = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            startActivityForResult(m.createScreenCaptureIntent(), 77);
        } catch (Exception e) {
            AudioCapService.consentResult(this, 0, null);
            finish();
        }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        AudioCapService.consentResult(this, res, data);
        finish();
    }
}
