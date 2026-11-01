package com.caiograton.meubolso;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

/**
 * Widget "Lançamento rápido": barra bordô com o bolso e a moeda, o valor do
 * último lançamento e o botão que abre a tela rápida do app.
 * Os textos seguem o idioma escolhido dentro do app.
 */
public class QuickWidgetProvider extends AppWidgetProvider {
    static final String PREFS = "mb_widget";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) {
            manager.updateAppWidget(id, build(context));
        }
    }

    static void refreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, QuickWidgetProvider.class));
        for (int id : ids) {
            manager.updateAppWidget(id, build(context));
        }
    }

    static RemoteViews build(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String lang = prefs.getString("lang", "pt-BR");
        String value = prefs.getString("value", "");
        String l = lang != null && lang.length() >= 2 ? lang.substring(0, 2).toLowerCase() : "pt";

        String sub;
        String label;
        if ("en".equals(l)) {
            sub = "last entry";
            label = "Quick\nentry";
        } else if ("es".equals(l)) {
            sub = "último movimiento";
            label = "Movimiento\nrápido";
        } else if ("ja".equals(l)) {
            sub = "最後の入力";
            label = "クイック\n入力";
        } else {
            sub = "último lançamento";
            label = "Lançamento\nrápido";
        }
        if (value == null || value.length() == 0) {
            value = "—";
        }

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.mb_widget_quick);
        views.setTextViewText(R.id.mb_title, "Meu Bolso");
        views.setTextViewText(R.id.mb_value, value);
        views.setTextViewText(R.id.mb_sub, sub);
        views.setTextViewText(R.id.mb_btn_label, label);

        Intent intent = new Intent(context, MainActivity.class);
        intent.setAction(QuickWidgetPlugin.ACTION_QUICK);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.mb_root, pi);
        views.setOnClickPendingIntent(R.id.mb_btn, pi);
        return views;
    }
}
