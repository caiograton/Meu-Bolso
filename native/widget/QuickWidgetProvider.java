package com.caiograton.meubolso;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;
import java.util.Calendar;
import java.util.Locale;

/**
 * Widget "Lançamento rápido": barra bordô com o bolso e a moeda, o valor do
 * último lançamento RÁPIDO (só se for de hoje ou de ontem) e o botão que abre a
 * tela rápida do app. Tocar no bolsinho esconde/mostra o valor (estado próprio do
 * widget, sem ligação com o olho do app). Os textos seguem o idioma do app.
 * O widget se atualiza sozinho na virada do dia (DATE_CHANGED) e uma vez por dia.
 */
public class QuickWidgetProvider extends AppWidgetProvider {
    static final String PREFS = "mb_widget";
    static final String ACTION_TOGGLE = "com.caiograton.meubolso.WIDGET_TOGGLE";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) {
            manager.updateAppWidget(id, build(context, id));
        }
        scheduleMidnight(context);
    }

    @Override
    public void onDeleted(Context context, int[] ids) {
        SharedPreferences.Editor e = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        for (int id : ids) e.remove("bg_" + id);
        e.apply();
    }

    /** Fundo escolhido para este widget: "light", "dark" ou "app" (segue o tema do app). */
    static String mode(SharedPreferences sp, int id) {
        String m = sp.getString("bg_" + id, null);
        if (m == null) m = sp.getString("bg_default", "dark");
        return m;
    }

    /** Tema atual do app (gravado pelo app); sem registro, o tema do celular. */
    static boolean appDark(Context c, SharedPreferences sp) {
        String t = sp.getString("theme", "");
        if ("dark".equals(t)) return true;
        if ("light".equals(t)) return false;
        int night = c.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return night == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    @Override
    public void onEnabled(Context context) {
        scheduleMidnight(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String a = intent == null ? null : intent.getAction();
        if (ACTION_TOGGLE.equals(a)) {
            SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            sp.edit().putBoolean("hidden", !sp.getBoolean("hidden", false)).apply();
            refreshAll(context);
            return;
        }
        if (Intent.ACTION_DATE_CHANGED.equals(a) || Intent.ACTION_TIME_CHANGED.equals(a)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(a) || ACTION_MIDNIGHT.equals(a)) {
            refreshAll(context);
            scheduleMidnight(context);
            return;
        }
        super.onReceive(context, intent);
    }

    static final String ACTION_MIDNIGHT = "com.caiograton.meubolso.WIDGET_MIDNIGHT";

    /** Garante a atualização depois da meia-noite mesmo se o aviso de troca de data falhar. */
    static void scheduleMidnight(Context context) {
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Calendar c = Calendar.getInstance();
            c.add(Calendar.DAY_OF_YEAR, 1);
            c.set(Calendar.HOUR_OF_DAY, 0);
            c.set(Calendar.MINUTE, 5);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            Intent i = new Intent(context, QuickWidgetProvider.class);
            i.setAction(ACTION_MIDNIGHT);
            PendingIntent pi = PendingIntent.getBroadcast(context, 7, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.set(AlarmManager.RTC, c.getTimeInMillis(), pi);
        } catch (Exception ignored) { }
    }

    static void refreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, QuickWidgetProvider.class));
        for (int id : ids) {
            manager.updateAppWidget(id, build(context, id));
        }
    }

    static String iso(Calendar c) {
        return String.format(Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    /** true se a data (aaaa-mm-dd) é hoje ou ontem, pelo relógio do aparelho. */
    static boolean recent(String date) {
        if (date == null || date.length() == 0) return false;
        Calendar t = Calendar.getInstance();
        if (iso(t).equals(date)) return true;
        t.add(Calendar.DAY_OF_YEAR, -1);
        return iso(t).equals(date);
    }

    static RemoteViews build(Context context, int widgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String lang = prefs.getString("lang", "pt-BR");
        String value = prefs.getString("value", "");
        String date = prefs.getString("date", "");
        boolean hidden = prefs.getBoolean("hidden", false);
        String l = lang != null && lang.length() >= 2 ? lang.substring(0, 2).toLowerCase() : "pt";

        String sub;
        String none;
        String label;
        if ("en".equals(l)) {
            sub = "last quick entry";
            none = "No recent quick entry";
            label = "Quick\nentry";
        } else if ("es".equals(l)) {
            sub = "último movimiento rápido";
            none = "Ningún movimiento rápido reciente";
            label = "Movimiento\nrápido";
        } else if ("ja".equals(l)) {
            sub = "最後のクイック入力";
            none = "最近のクイック入力はありません";
            label = "クイック\n入力";
        } else if ("de".equals(l)) {
            sub = "letzte Schnellbuchung";
            none = "Keine aktuelle Schnellbuchung";
            label = "Schnell-\nbuchung";
        } else if ("fr".equals(l)) {
            sub = "dernière saisie rapide";
            none = "Aucune saisie rapide récente";
            label = "Saisie\nrapide";
        } else if ("pl".equals(l)) {
            sub = "ostatni szybki wpis";
            none = "Brak ostatnich szybkich wpisów";
            label = "Szybki\nwpis";
        } else if ("it".equals(l)) {
            sub = "ultimo inserimento rapido";
            none = "Nessun inserimento rapido recente";
            label = "Inserimento\nrapido";
        } else {
            sub = "último lançamento rápido";
            none = "Nenhum lançamento rápido recente";
            label = "Lançamento\nrápido";
        }
        boolean has = value != null && value.length() > 0 && recent(date);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.mb_widget_quick);
        views.setTextViewText(R.id.mb_title, "Sigillum");
        String bgm = mode(prefs, widgetId);
        boolean light = "light".equals(bgm) || ("app".equals(bgm) && !appDark(context, prefs));
        views.setInt(R.id.mb_root, "setBackgroundResource", light ? R.drawable.mb_widget_bg_light : R.drawable.mb_widget_bg);
        views.setTextColor(R.id.mb_title, light ? 0xFF7B1F2A : 0xFFF5F3EF);
        views.setTextColor(R.id.mb_value, light ? 0xFF1E1416 : 0xFFF5F3EF);
        views.setTextColor(R.id.mb_sub, light ? 0xFF8A7F78 : 0xFFCDBFB8);
        views.setTextViewText(R.id.mb_btn_label, label);
        if (has) {
            views.setViewVisibility(R.id.mb_value, android.view.View.VISIBLE);
            views.setTextViewText(R.id.mb_value, hidden ? "\u2022\u2022\u2022\u2022\u2022" : value);
            views.setTextViewText(R.id.mb_sub, sub);
            views.setImageViewResource(R.id.mb_glyph, hidden ? R.drawable.mb_widget_glyph_hide : R.drawable.mb_widget_glyph);
        } else {
            views.setViewVisibility(R.id.mb_value, android.view.View.GONE);
            views.setTextViewText(R.id.mb_sub, none);
            views.setImageViewResource(R.id.mb_glyph, R.drawable.mb_widget_glyph);
        }

        Intent intent = new Intent(context, MainActivity.class);
        intent.setAction(QuickWidgetPlugin.ACTION_QUICK);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.mb_root, pi);
        views.setOnClickPendingIntent(R.id.mb_btn, pi);

        if (has) {
            Intent t = new Intent(context, QuickWidgetProvider.class);
            t.setAction(ACTION_TOGGLE);
            PendingIntent tp = PendingIntent.getBroadcast(context, 1, t,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.mb_glyph, tp);
        } else {
            views.setOnClickPendingIntent(R.id.mb_glyph, pi);
        }
        return views;
    }
}
