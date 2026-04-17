package com.palgate.opener;

import android.content.*;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context ctx, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        AppPrefs prefs = new AppPrefs(ctx);
        if (!prefs.isServiceOn() || !prefs.isLinked()) return;
        Intent svc = new Intent(ctx, GateMonitorService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(svc);
        else ctx.startService(svc);
    }
}
