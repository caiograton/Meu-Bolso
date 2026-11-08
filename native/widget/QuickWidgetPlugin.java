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
        String th = call.getString("theme", null);
        if (th != null) e.putString("theme", th);
        e.apply();
        QuickWidgetProvider.refreshAll(c);
        call.resolve();
    }

    /** Fundo escolhido em Configurações › Lançamento rápido: vale para todos os widgets. */
    @PluginMethod
    public void setBg(PluginCall call) {
        Context c = getContext();
        String m = call.getString("mode", "dark");
        if (!"light".equals(m) && !"app".equals(m)) m = "dark";
        SharedPreferences sp = c.getSharedPreferences(QuickWidgetProvider.PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor e = sp.edit();
        e.putString("bg_default", m);
        for (String k : sp.getAll().keySet()) if (k.startsWith("bg_") && !"bg_default".equals(k)) e.remove(k);
        e.apply();
        QuickWidgetProvider.refreshAll(c);
        call.resolve();
    }

    @PluginMethod
    public void getBg(PluginCall call) {
        JSObject o = new JSObject();
        o.put("mode", getContext().getSharedPreferences(QuickWidgetProvider.PREFS, Context.MODE_PRIVATE).getString("bg_default", "dark"));
        call.resolve(o);
    }
}
