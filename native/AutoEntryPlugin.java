package com.caiograton.meubolso;

import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import androidx.core.app.NotificationManagerCompat;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import org.json.JSONArray;

/** Ponte do Lançamento automático: acesso às notificações, apps escolhidos e fila de avisos. */
@CapacitorPlugin(name = "AutoEntry")
public class AutoEntryPlugin extends Plugin {

    @Override
    public void load() { NotifReaderService.plugin = this; }

    @Override
    protected void handleOnDestroy() { if (NotifReaderService.plugin == this) NotifReaderService.plugin = null; }

    void posted() { notifyListeners("posted", new JSObject()); }

    private boolean access() {
        return NotificationManagerCompat.getEnabledListenerPackages(getContext()).contains(getContext().getPackageName());
    }

    private SharedPreferences prefs() { return getContext().getSharedPreferences(NotifReaderService.PREFS, android.content.Context.MODE_PRIVATE); }

    @PluginMethod
    public void status(PluginCall call) {
        JSObject r = new JSObject();
        r.put("access", access());
        r.put("on", prefs().getBoolean("on", false));
        r.put("apps", prefs().getString("apps", ""));
        call.resolve(r);
    }

    /** Liga ou desliga e define de quais apps ler. Desligar também apaga a fila. */
    @PluginMethod
    public void config(PluginCall call) {
        boolean on = Boolean.TRUE.equals(call.getBoolean("on", false));
        JSArray a = call.getArray("apps", new JSArray());
        StringBuilder sb = new StringBuilder();
        try { for (int i = 0; i < a.length(); i++) { String s = a.getString(i); if (s != null && s.matches("[A-Za-z0-9_.]{3,120}")) { if (sb.length() > 0) sb.append(','); sb.append(s); } } } catch (Exception ignored) { }
        SharedPreferences.Editor e = prefs().edit().putBoolean("on", on).putString("apps", sb.toString());
        if (!on) e.putString("q", "[]");
        e.apply();
        if (on && access() && Build.VERSION.SDK_INT >= 24) {
            try { NotificationListenerService.requestRebind(new ComponentName(getContext(), NotifReaderService.class)); } catch (Exception ignored) { }
        }
        call.resolve();
    }

    /** Abre a tela do Android para dar (ou tirar) o acesso às notificações. */
    @PluginMethod
    public void openAccess(PluginCall call) {
        try {
            Intent i;
            if (Build.VERSION.SDK_INT >= 30) {
                i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS);
                i.putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, new ComponentName(getContext(), NotifReaderService.class).flattenToString());
            } else {
                i = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { getContext().startActivity(i); }
            catch (Exception e1) {
                Intent j = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
                j.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(j);
            }
            call.resolve();
        } catch (Exception e) { call.reject("open"); }
    }

    /** Entrega os avisos guardados e esvazia a fila. */
    @PluginMethod
    public void pull(PluginCall call) {
        try {
            JSONArray q = NotifReaderService.take(getContext());
            JSObject r = new JSObject();
            r.put("items", new JSArray(q.toString()));
            call.resolve(r);
        } catch (Exception e) { call.reject("pull"); }
    }

    /** Diz quais destes apps estão instalados (os pacotes ficam declarados no manifesto, em queries). */
    @PluginMethod
    public void installed(PluginCall call) {
        JSArray a = call.getArray("pkgs", new JSArray()), out = new JSArray();
        PackageManager pm = getContext().getPackageManager();
        try {
            for (int i = 0; i < a.length(); i++) {
                String p = a.getString(i);
                try { pm.getPackageInfo(p, 0); out.put(p); } catch (Exception ignored) { }
            }
        } catch (Exception ignored) { }
        JSObject r = new JSObject();
        r.put("pkgs", out);
        call.resolve(r);
    }
}
