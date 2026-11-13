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
            if (sbn == null) return;
            String pkg = sbn.getPackageName();
            if (pkg == null || pkg.equals(getPackageName()) || !apps(this).contains(pkg)) return;
            Notification n = sbn.getNotification();
            if (n == null || (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0 || sbn.isOngoing()) return;
            Bundle ex = n.extras;
            if (ex == null) return;
            String title = txt(ex, Notification.EXTRA_TITLE);
            String text = txt(ex, Notification.EXTRA_BIG_TEXT);
            if (text.isEmpty()) text = txt(ex, Notification.EXTRA_TEXT);
            if (title.isEmpty() && text.isEmpty()) return;
            if (title.length() > 200) title = title.substring(0, 200);
            if (text.length() > 600) text = text.substring(0, 600);
            JSONObject o = new JSONObject();
            o.put("pkg", pkg);
            o.put("title", title);
            o.put("text", text);
            o.put("when", sbn.getPostTime());
            o.put("key", sbn.getKey() + "|" + sbn.getPostTime());
            push(this, o);
            AutoEntryPlugin pl = plugin;
            if (pl != null) pl.posted();
        } catch (Exception ignored) { }
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
