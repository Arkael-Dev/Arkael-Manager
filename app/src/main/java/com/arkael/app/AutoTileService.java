package com.arkael.app;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Tile Quick Settings: nyalakan/matikan Auto game mode dari panel notifikasi. */
public class AutoTileService extends TileService {
    @Override public void onStartListening() { refresh(); }

    @Override public void onClick() {
        if (GameProfiles.auto(this)) {
            GameProfiles.setAuto(this, false);
            GameService.stop(this);
        } else {
            if (!Settings.canDrawOverlays(this)) { openApp(); return; }
            GameProfiles.setAuto(this, true);
            try {
                GameService.start(this);
            } catch (Throwable t) {
                GameProfiles.setAuto(this, false);
                openApp();
                return;
            }
        }
        refresh();
    }

    private void openApp() {
        Intent i = new Intent(this, GameListActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        } else {
            startActivityAndCollapse(i);
        }
    }

    private void refresh() {
        Tile t = getQsTile();
        if (t == null) return;
        t.setState(GameProfiles.auto(this) ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setLabel("Auto game mode");
        t.updateTile();
    }
}
