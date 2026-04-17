package com.palgate.opener;

import android.content.*;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            AppPrefs prefs = new AppPrefs(ctx);
            if (prefs.isServiceEnabled()) {
                Intent service = new Intent(ctx, GateMonitorService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ctx.startForegroundService(service);
                } else {
                    ctx.startService(service);
                }
            }
        }
    }
}
