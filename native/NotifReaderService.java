package com.caiograton.meubolso;

import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Lançamento automático (Plus). Só guarda avisos dos apps que a pessoa escolheu no
 * Sigillum, e só título, texto, app e hora. Tudo fica neste aparelho, numa fila que o
 * app lê e apaga ao abrir. Nada sai do celular.
 */
public class NotifReaderService extends NotificationListenerService {
    static final String PREFS = "mb_au";
    static final int MAX = 200;
    static volatile AutoEntryPlugin plugin;
    static volatile NotifReaderService inst;

    @Override
    public void onListenerConnected() { inst = this; scanActive(); }

    @Override
    public void onListenerDisconnected() { if (inst == this) inst = null; }

    /** Lê os avisos que ainda estão na barra (de antes de ligar, ou de um banco marcado agora). */
    void scanActive() {
        try {
            StatusBarNotification[] a = getActiveNotifications();
            if (a == null) return;
            int n = 0;
            for (StatusBarNotification sbn : a) if (keep(sbn)) n++;
            AutoEntryPlugin pl = plugin;
            if (n > 0 && pl != null) pl.posted();
        } catch (Exception ignored) { }
    }

    static Set<String> apps(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!p.getBoolean("on", false)) return new HashSet<>();
        String s = p.getString("apps", "");
        return new HashSet<>(Arrays.asList(s.isEmpty() ? new String[0] : s.split(",")));
    }

    private static String txt(Bundle ex, String k) {
        CharSequence cs = ex.getCharSequence(k);
        return cs == null ? "" : cs.toString();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            if (keep(sbn)) { AutoEntryPlugin pl = plugin; if (pl != null) pl.posted(); }
        } catch (Exception ignored) { }
    }

    /** Guarda o aviso na fila se for de um app escolhido. Devolve true quando guardou. */
    private boolean keep(StatusBarNotification sbn) throws Exception {
        if (sbn == null) return false;
        String pkg = sbn.getPackageName();
        if (pkg == null || pkg.equals(getPackageName()) || !apps(this).contains(pkg)) return false;
        Notification n = sbn.getNotification();
        if (n == null || (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0 || sbn.isOngoing()) return false;
        Bundle ex = n.extras;
        if (ex == null) return false;
        String title = txt(ex, Notification.EXTRA_TITLE);
        String text = txt(ex, Notification.EXTRA_BIG_TEXT);
        if (text.isEmpty()) text = txt(ex, Notification.EXTRA_TEXT);
        if (title.isEmpty() && text.isEmpty()) return false;
        if (title.length() > 200) title = title.substring(0, 200);
        if (text.length() > 600) text = text.substring(0, 600);
        JSONObject o = new JSONObject();
        o.put("pkg", pkg);
        o.put("title", title);
        o.put("text", text);
        o.put("when", sbn.getPostTime());
        o.put("key", sbn.getKey() + "|" + sbn.getPostTime());
        push(this, o);
        return true;
    }

    static synchronized void push(Context c, JSONObject o) throws Exception {
        SharedPreferences p = c.getSharedPreferences(PREFS, MODE_PRIVATE);
        JSONArray q = new JSONArray(p.getString("q", "[]"));
        q.put(o);
        JSONArray out = new JSONArray();
        for (int i = Math.max(0, q.length() - MAX); i < q.length(); i++) out.put(q.get(i));
        p.edit().putString("q", out.toString()).apply();
    }

    static synchronized JSONArray take(Context c) throws Exception {
        SharedPreferences p = c.getSharedPreferences(PREFS, MODE_PRIVATE);
        JSONArray q = new JSONArray(p.getString("q", "[]"));
        p.edit().putString("q", "[]").apply();
        return q;
    }
}
