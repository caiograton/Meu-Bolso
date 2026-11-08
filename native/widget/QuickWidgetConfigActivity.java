package com.caiograton.meubolso;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.core.view.WindowCompat;

/**
 * Tela que abre ao adicionar o widget (e ao "Configurar" o widget depois):
 * escolha do fundo claro, escuro ou seguindo o tema do app.
 */
public class QuickWidgetConfigActivity extends Activity {
    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setResult(RESULT_CANCELED);
        Intent in = getIntent();
        if (in != null && in.getExtras() != null) {
            widgetId = in.getExtras().getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }
        final SharedPreferences sp = getSharedPreferences(QuickWidgetProvider.PREFS, MODE_PRIVATE);
        String lang = sp.getString("lang", "pt-BR");
        String l = lang != null && lang.length() >= 2 ? lang.substring(0, 2).toLowerCase() : "pt";
        String[] t = texts(l);
        String cur = QuickWidgetProvider.mode(sp, widgetId);

        boolean dark = QuickWidgetProvider.appDark(this, sp);
        int bg = dark ? 0xFF1E1416 : 0xFFF5F3EF, ink = dark ? 0xFFF5F3EF : 0xFF1E1416, mut = dark ? 0xFFB3A39F : 0xFF8A7F78;
        int card = dark ? 0xFF2A1E21 : 0xFFFFFFFF, line = dark ? 0xFF3B2B2F : 0xFFE6DED2, sel = dark ? 0xFFC9A55A : 0xFF7B1F2A;
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(!dark);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightNavigationBars(!dark);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(bg);
        sv.setFitsSystemWindows(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(22);
        root.setPadding(p, dp(28), p, dp(28));
        sv.addView(root);

        TextView h = new TextView(this);
        h.setText(t[0]);
        h.setTextColor(ink);
        h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        h.setTypeface(Typeface.create(Typeface.SERIF, Typeface.NORMAL));
        root.addView(h);
        TextView s = new TextView(this);
        s.setText(t[1]);
        s.setTextColor(mut);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        s.setPadding(0, dp(6), 0, dp(18));
        root.addView(s);

        String[] modes = {"light", "dark", "app"};
        int[] imgs = {R.drawable.mb_wprev_light, R.drawable.mb_wprev_dark, R.drawable.mb_wprev_app};
        for (int i = 0; i < 3; i++) {
            final String m = modes[i];
            LinearLayout c = new LinearLayout(this);
            c.setOrientation(LinearLayout.VERTICAL);
            c.setPadding(dp(14), dp(14), dp(14), dp(12));
            GradientDrawable gd = new GradientDrawable();
            gd.setCornerRadius(dp(18));
            gd.setColor(card);
            gd.setStroke(dp(m.equals(cur) ? 2 : 1), m.equals(cur) ? sel : line);
            c.setBackground(gd);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(12);
            c.setLayoutParams(lp);
            ImageView iv = new ImageView(this);
            iv.setImageResource(imgs[i]);
            iv.setAdjustViewBounds(true);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            c.addView(iv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            TextView lb = new TextView(this);
            lb.setText((m.equals(cur) ? "✓  " : "") + t[2 + i]);
            lb.setTextColor(ink);
            lb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            lb.setTypeface(Typeface.DEFAULT_BOLD);
            lb.setGravity(Gravity.CENTER_HORIZONTAL);
            lb.setPadding(0, dp(10), 0, 0);
            c.addView(lb);
            c.setClickable(true);
            c.setFocusable(true);
            c.setContentDescription(t[2 + i]);
            c.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { choose(sp, m); }
            });
            root.addView(c);
        }
        TextView f = new TextView(this);
        f.setText(t[5]);
        f.setTextColor(mut);
        f.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        f.setPadding(0, dp(8), 0, 0);
        root.addView(f);
        setContentView(sv);
    }

    private void choose(SharedPreferences sp, String m) {
        sp.edit().putString("bg_" + widgetId, m).apply();
        AppWidgetManager.getInstance(this).updateAppWidget(widgetId, QuickWidgetProvider.build(this, widgetId));
        Intent r = new Intent();
        r.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        setResult(RESULT_OK, r);
        finish();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    static String[] texts(String l) {
        switch (l) {
            case "en": return new String[]{"Widget background", "Choose how the widget looks on your home screen.", "Light", "Dark", "Follow the app theme", "You can change it later: touch and hold the widget, or go to Settings › Quick entry."};
            case "es": return new String[]{"Fondo del widget", "Elige cómo se ve el widget en tu pantalla de inicio.", "Claro", "Oscuro", "Seguir el tema de la app", "Puedes cambiarlo después: mantén pulsado el widget o ve a Ajustes › Movimiento rápido."};
            case "ja": return new String[]{"ウィジェットの背景", "ホーム画面でのウィジェットの見た目を選んでください。", "ライト", "ダーク", "アプリのテーマに合わせる", "あとで変更できます：ウィジェットを長押しするか、設定 › クイック入力から。"};
            case "de": return new String[]{"Widget-Hintergrund", "Wähle, wie das Widget auf deinem Startbildschirm aussieht.", "Hell", "Dunkel", "Dem App-Design folgen", "Du kannst es später ändern: Widget gedrückt halten oder Einstellungen › Schnellbuchung."};
            case "fr": return new String[]{"Fond du widget", "Choisissez l’apparence du widget sur votre écran d’accueil.", "Clair", "Sombre", "Suivre le thème de l’appli", "Vous pourrez changer plus tard : appui long sur le widget ou Réglages › Saisie rapide."};
            case "pl": return new String[]{"Tło widżetu", "Wybierz wygląd widżetu na ekranie głównym.", "Jasne", "Ciemne", "Zgodnie z motywem aplikacji", "Możesz to zmienić później: przytrzymaj widżet lub przejdź do Ustawienia › Szybki wpis."};
            case "it": return new String[]{"Sfondo del widget", "Scegli come appare il widget nella schermata Home.", "Chiaro", "Scuro", "Segui il tema dell’app", "Puoi cambiarlo dopo: tieni premuto il widget o vai in Impostazioni › Inserimento rapido."};
            default: return new String[]{"Fundo do widget", "Escolha como o widget aparece na sua tela inicial.", "Claro", "Escuro", "Seguir o tema do app", "Dá para mudar depois: toque e segure o widget ou vá em Configurações › Lançamento rápido."};
        }
    }
}
