package com.caiograton.meubolso;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.core.app.NotificationManagerCompat;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

/**
 * Estado e pedido das permissões (câmera, microfone, notificações), para que o
 * app mostre antes a sua própria explicação, com o tema do Sigillum.
 * Estados: granted, prompt, prompt-with-rationale, denied (o Android não
 * pergunta mais; só nas configurações do aparelho).
 * Também abre links externos (Play Store) e as configurações do app.
 */
@CapacitorPlugin(
    name = "Perms",
    permissions = {
        @Permission(alias = "cam", strings = { Manifest.permission.CAMERA }),
        @Permission(alias = "mic", strings = { Manifest.permission.RECORD_AUDIO }),
        @Permission(alias = "ntf", strings = { "android.permission.POST_NOTIFICATIONS" })
    }
)
public class PermsPlugin extends Plugin {

    private String state(String a) {
        if ("ntf".equals(a) && Build.VERSION.SDK_INT < 33) {
            return NotificationManagerCompat.from(getContext()).areNotificationsEnabled() ? "granted" : "denied";
        }
        PermissionState s = getPermissionState(a);
        if (s == null) return "prompt";
        if (s == PermissionState.GRANTED && "ntf".equals(a)
            && !NotificationManagerCompat.from(getContext()).areNotificationsEnabled()) return "denied";
        return s.toString();
    }

    private String alias(PluginCall call) {
        String a = call.getString("p", "");
        return ("cam".equals(a) || "mic".equals(a) || "ntf".equals(a)) ? a : null;
    }

    @PluginMethod
    public void check(PluginCall call) {
        String a = alias(call);
        if (a == null) { call.reject("permissão desconhecida"); return; }
        JSObject o = new JSObject();
        o.put("state", state(a));
        call.resolve(o);
    }

    @PluginMethod
    public void request(PluginCall call) {
        String a = alias(call);
        if (a == null) { call.reject("permissão desconhecida"); return; }
        String s = state(a);
        if ("granted".equals(s) || ("ntf".equals(a) && Build.VERSION.SDK_INT < 33)) {
            JSObject o = new JSObject();
            o.put("state", s);
            call.resolve(o);
            return;
        }
        requestPermissionForAlias(a, call, "reqDone");
    }

    @PermissionCallback
    private void reqDone(PluginCall call) {
        JSObject o = new JSObject();
        o.put("state", state(alias(call)));
        call.resolve(o);
    }

    /** Abre a página do app nas configurações do Android (ou a de notificações). */
    @PluginMethod
    public void openSettings(PluginCall call) {
        try {
            Intent i;
            if ("ntf".equals(call.getString("p", "")) && Build.VERSION.SDK_INT >= 26) {
                i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                i.putExtra(Settings.EXTRA_APP_PACKAGE, getContext().getPackageName());
            } else {
                i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", getContext().getPackageName(), null));
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            call.resolve();
        } catch (Exception e) {
            call.reject("não abriu", e);
        }
    }

    /** Abre a página do Sigillum na Play Store (app da loja ou navegador). */
    @PluginMethod
    public void openStore(PluginCall call) {
        String pkg = getContext().getPackageName();
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg));
            i.setPackage("com.android.vending");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            call.resolve();
        } catch (Exception e) {
            try {
                Intent w = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + pkg));
                w.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(w);
                call.resolve();
            } catch (Exception e2) {
                call.reject("não abriu", e2);
            }
        }
    }
}
