package com.caiograton.meubolso;

import android.content.Context;
import android.content.SharedPreferences;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Ponte entre o app e o widget de lançamento rápido:
 * sync() grava idioma e último valor para o widget; quickPending() diz se o app
 * foi aberto pelo widget (e limpa o aviso).
 */
@CapacitorPlugin(name = "QuickWidget")
public class QuickWidgetPlugin extends Plugin {
    public static final String ACTION_QUICK = "com.caiograton.meubolso.QUICK";
    public static volatile boolean pending = false;

    @PluginMethod
    public void quickPending(PluginCall call) {
        JSObject o = new JSObject();
        o.put("pending", pending);
        pending = false;
        call.resolve(o);
    }

    @PluginMethod
    public void sync(PluginCall call) {
        Context c = getContext();
        SharedPreferences.Editor e = c.getSharedPreferences(QuickWidgetProvider.PREFS, Context.MODE_PRIVATE).edit();
        e.putString("lang", call.getString("lang", "pt-BR"));
        e.putString("value", call.getString("value", ""));
        e.putString("date", call.getString("date", ""));
        e.apply();
        QuickWidgetProvider.refreshAll(c);
        call.resolve();
    }
}
