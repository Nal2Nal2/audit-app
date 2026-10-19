package com.nal2nal2.audit;

import android.content.Context;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "WidgetBridge")
public class WidgetBridgePlugin extends Plugin {
    @PluginMethod
    public void update(PluginCall call) {
        String data = call.getString("data");
        if (data == null) {
            call.reject("data가 없어요");
            return;
        }
        Context ctx = getContext();
        ctx.getSharedPreferences(AuditWidget.PREFS, Context.MODE_PRIVATE)
            .edit().putString(AuditWidget.KEY, data).apply();
        AuditWidget.refreshAll(ctx);
        call.resolve();
    }
}
