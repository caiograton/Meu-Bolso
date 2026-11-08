#!/usr/bin/env bash
# Aplica as personalizações nativas ao projeto Android gerado pelo Capacitor.
set -euo pipefail

PKG_DIR="android/app/src/main/java/com/caiograton/meubolso"
test -d "$PKG_DIR" || { echo "Pasta $PKG_DIR não encontrada"; exit 1; }

# Dados financeiros não vão para o backup automático do Android (nuvem/adb).
MANIFEST="android/app/src/main/AndroidManifest.xml"
if grep -q 'android:allowBackup=' "$MANIFEST"; then
  sed -i 's/android:allowBackup="true"/android:allowBackup="false"/' "$MANIFEST"
else
  sed -i 's/<application/<application android:allowBackup="false"/' "$MANIFEST"
fi
grep -q 'android:allowBackup="false"' "$MANIFEST" || { echo "allowBackup não desativado"; exit 1; }

cp native/DeviceLockPlugin.java "$PKG_DIR/DeviceLockPlugin.java"
cp native/VoiceEntryPlugin.java "$PKG_DIR/VoiceEntryPlugin.java"
cp native/FileSavePlugin.java "$PKG_DIR/FileSavePlugin.java"
cp native/SafeAreaPlugin.java "$PKG_DIR/SafeAreaPlugin.java"
cp native/InsetMath.java "$PKG_DIR/InsetMath.java"
cp native/ImeTracker.java "$PKG_DIR/ImeTracker.java"
cp native/widget/QuickWidgetPlugin.java "$PKG_DIR/QuickWidgetPlugin.java"
cp native/widget/QuickWidgetProvider.java "$PKG_DIR/QuickWidgetProvider.java"
cp native/widget/QuickWidgetConfigActivity.java "$PKG_DIR/QuickWidgetConfigActivity.java"

cat > "$PKG_DIR/MainActivity.java" <<'JAVA'
package com.caiograton.meubolso;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsAnimationCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.getcapacitor.BridgeActivity;
import java.util.List;

public class MainActivity extends BridgeActivity {
    private static final int BORDO = 0xFF7B1F2A;
    private View top;
    private View bottom;
    private View content;
    private final ImeTracker imeTracker = new ImeTracker();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(DeviceLockPlugin.class);
        registerPlugin(SafeAreaPlugin.class);
        registerPlugin(QuickWidgetPlugin.class);
        registerPlugin(VoiceEntryPlugin.class);
        registerPlugin(FileSavePlugin.class);
        super.onCreate(savedInstanceState);
        if (getSharedPreferences("mb_sec", MODE_PRIVATE).getBoolean("secure", false)) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        }
        if (QuickWidgetPlugin.ACTION_QUICK.equals(getIntent().getAction())) {
            QuickWidgetPlugin.pending = true;
        }

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        // Abertura na cor do tema (pergaminho no claro, tinta no escuro): o tema
        // escolhido no app fica guardado aqui no aparelho; sem escolha, segue o
        // tema do celular. Nada de bordô antes da animação do selo.
        final int open = openColor();
        final boolean light = isLight(open);
        getWindow().setStatusBarColor(open);
        getWindow().setNavigationBarColor(open);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(open));
        try { getBridge().getWebView().setBackgroundColor(open); } catch (Exception ignored) { }
        WindowInsetsControllerCompat wc = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        wc.setAppearanceLightStatusBars(light);
        wc.setAppearanceLightNavigationBars(light);

        // As faixas das barras do Android são pintadas aqui (bordô em cima,
        // preto embaixo) e o app fica sempre entre elas e acima do teclado.
        final FrameLayout decor = (FrameLayout) getWindow().getDecorView();
        top = new View(this);
        top.setBackgroundColor(open);
        bottom = new View(this);
        bottom.setBackgroundColor(open);
        decor.addView(top, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, Gravity.TOP));
        decor.addView(bottom, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, Gravity.BOTTOM));

        content = findViewById(android.R.id.content);
        content.setBackgroundColor(open);
        // Plano B: se o app não avisar o fim da abertura, troca sozinho.
        content.postDelayed(this::endSplash, 6000);
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, w) -> {
            v.post(this::applyInsets);
            return WindowInsetsCompat.CONSUMED;
        });
        // Depois de cada layout: se o sistema já encolheu a janela por causa do
        // teclado, não somamos o teclado de novo (era o que deixava a tela cinza).
        content.getViewTreeObserver().addOnGlobalLayoutListener(this::applyInsets);
        // O app acompanha o teclado quadro a quadro (abre e fecha deslizando).
        ViewCompat.setWindowInsetsAnimationCallback(content,
                new WindowInsetsAnimationCompat.Callback(WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
            @Override
            public void onPrepare(WindowInsetsAnimationCompat animation) {
                imeTracker.onPrepare();
            }

            @Override
            public WindowInsetsCompat onProgress(WindowInsetsCompat insets, List<WindowInsetsAnimationCompat> running) {
                imeTracker.onProgress(insets.getInsets(WindowInsetsCompat.Type.ime()).bottom);
                applyInsets();
                return insets;
            }

            @Override
            public void onEnd(WindowInsetsAnimationCompat animation) {
                imeTracker.onEnd();
                applyInsets();
            }
        });
        ViewCompat.requestApplyInsets(content);
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && QuickWidgetPlugin.ACTION_QUICK.equals(intent.getAction())) {
            QuickWidgetPlugin.pending = true;
            try {
                getBridge().getWebView().post(() -> getBridge().getWebView().evaluateJavascript(
                        "window.dispatchEvent(new Event('mbquick'))", null));
            } catch (Exception ignored) { }
        }
    }

    private boolean splashEnded = false;

    /** Cor da abertura: a última cor de fundo do app (gravada pelo SafeArea) ou o tema do celular. */
    private int openColor() {
        String c = getSharedPreferences("mb_ui", MODE_PRIVATE).getString("bg", null);
        if (c != null) {
            try { return Color.parseColor(c); } catch (Exception ignored) { }
        }
        int night = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return night == android.content.res.Configuration.UI_MODE_NIGHT_YES ? 0xFF1E1416 : 0xFFF5F3EF;
    }

    private static boolean isLight(int c) {
        return (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000 > 140;
    }

    /** Fim da animação do selo: barra de cima bordô (junto do cabeçalho) e de baixo preta. */
    public void endSplash() {
        runOnUiThread(() -> {
            if (splashEnded) return;
            splashEnded = true;
            getWindow().setStatusBarColor(BORDO);
            getWindow().setNavigationBarColor(Color.BLACK);
            getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.BLACK));
            if (top != null) top.setBackgroundColor(BORDO);
            if (bottom != null) bottom.setBackgroundColor(Color.BLACK);
            if (content != null) content.setBackgroundColor(Color.BLACK);
            WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            c.setAppearanceLightStatusBars(false);
            c.setAppearanceLightNavigationBars(false);
        });
    }

    private int screenHeight() {
        if (Build.VERSION.SDK_INT >= 30) {
            return getWindowManager().getMaximumWindowMetrics().getBounds().height();
        }
        DisplayMetrics m = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(m);
        return m.heightPixels;
    }

    private static void setHeight(View v, int h) {
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp.height != h) {
            lp.height = h;
            v.setLayoutParams(lp);
        }
    }

    private void applyInsets() {
        if (content == null) return;
        WindowInsetsCompat w = ViewCompat.getRootWindowInsets(content);
        if (w == null) return;
        Insets sys = w.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
        int systemIme = w.isVisible(WindowInsetsCompat.Type.ime()) ? w.getInsets(WindowInsetsCompat.Type.ime()).bottom : 0;
        int imeBottom = imeTracker.effective(systemIme);
        boolean imeVisible = imeBottom > 0;
        int[] loc = new int[2];
        content.getLocationOnScreen(loc);
        int bp = InsetMath.bottomPad(screenHeight(), loc[1] + content.getHeight(), sys.bottom, imeBottom);
        int tp = InsetMath.topPad(loc[1], sys.top);
        if (content.getPaddingLeft() != sys.left || content.getPaddingTop() != tp
                || content.getPaddingRight() != sys.right || content.getPaddingBottom() != bp) {
            content.setPadding(sys.left, tp, sys.right, bp);
        }
        setHeight(top, sys.top);
        setHeight(bottom, InsetMath.bottomBarHeight(imeVisible, sys.bottom));
    }
}
JAVA

GRADLE="android/app/build.gradle"
test -f "$GRADLE" || { echo "$GRADLE não encontrado"; exit 1; }
sed -i '0,/^dependencies {/s//dependencies {\n    implementation "androidx.biometric:biometric:1.1.0"/' "$GRADLE"
grep -q "androidx.biometric:biometric" "$GRADLE" || { echo "Não foi possível adicionar a dependência de biometria"; exit 1; }

MANIFEST="android/app/src/main/AndroidManifest.xml"
if ! grep -q "USE_BIOMETRIC" "$MANIFEST"; then
  sed -i '0,/<application/s//<uses-permission android:name="android.permission.USE_BIOMETRIC" \/>\n    <application/' "$MANIFEST"
fi
grep -q "USE_BIOMETRIC" "$MANIFEST" || { echo "Não foi possível adicionar a permissão"; exit 1; }
if ! grep -q "android.permission.CAMERA" "$MANIFEST"; then
  sed -i '0,/<application/s//<uses-permission android:name="android.permission.CAMERA" \/>\n    <uses-feature android:name="android.hardware.camera" android:required="false" \/>\n    <application/' "$MANIFEST"
fi
grep -q "android.permission.CAMERA" "$MANIFEST" || { echo "Não foi possível adicionar a permissão da câmera"; exit 1; }
# Microfone, só para o lançamento por voz (reconhecimento no aparelho, sem internet).
if ! grep -q "android.permission.RECORD_AUDIO" "$MANIFEST"; then
  sed -i '0,/<application/s//<uses-permission android:name="android.permission.RECORD_AUDIO" \/>\n    <uses-feature android:name="android.hardware.microphone" android:required="false" \/>\n    <queries>\n        <intent>\n            <action android:name="android.speech.RecognitionService" \/>\n        <\/intent>\n    <\/queries>\n    <application/' "$MANIFEST"
fi
grep -q "android.permission.RECORD_AUDIO" "$MANIFEST" || { echo "Não foi possível adicionar a permissão do microfone"; exit 1; }
if ! grep -q "WRITE_EXTERNAL_STORAGE" "$MANIFEST"; then
  sed -i '0,/<application/s//<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="28" \/>\n    <application/' "$MANIFEST"
fi
grep -q "WRITE_EXTERNAL_STORAGE" "$MANIFEST" || { echo "Não foi possível adicionar a permissão de gravação"; exit 1; }
grep -q "android.speech.RecognitionService" "$MANIFEST" || { echo "Não foi possível declarar o serviço de voz"; exit 1; }

# Abertura sem piscada: a tela inicial do Android (inclusive a do Android 12+)
# já nasce na cor do pergaminho (ou tinta, no modo escuro), sem ícone, e emenda
# direto na animação do selo.
RES="android/app/src/main/res"
mkdir -p "$RES/values" "$RES/values-v31" "$RES/drawable"
cat > "$RES/values/meubolso_cores.xml" <<'XML'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="mb_bordo">#7B1F2A</color>
    <color name="mb_abertura">#F5F3EF</color>
</resources>
XML
mkdir -p "$RES/values-night"
cat > "$RES/values-night/sigillum_cores.xml" <<'XML'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="mb_abertura">#1E1416</color>
</resources>
XML
cat > "$RES/drawable/splash_vazio.xml" <<'XML'
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="@android:color/transparent" />
    <size android:width="108dp" android:height="108dp" />
</shape>
XML
cat > "$RES/values/styles.xml" <<'XML'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="AppTheme" parent="Theme.AppCompat.Light.DarkActionBar">
        <item name="colorPrimary">@color/mb_bordo</item>
        <item name="colorPrimaryDark">@color/mb_bordo</item>
        <item name="colorAccent">@color/mb_bordo</item>
    </style>
    <style name="AppTheme.NoActionBar" parent="Theme.AppCompat.DayNight.NoActionBar">
        <item name="windowActionBar">false</item>
        <item name="windowNoTitle">true</item>
        <item name="android:background">@null</item>
        <item name="android:windowBackground">@color/mb_abertura</item>
        <item name="android:statusBarColor">@color/mb_bordo</item>
    </style>
    <style name="AppTheme.NoActionBarLaunch" parent="Theme.SplashScreen">
        <item name="android:background">@color/mb_abertura</item>
        <item name="android:windowBackground">@color/mb_abertura</item>
        <item name="android:statusBarColor">@color/mb_bordo</item>
        <item name="windowSplashScreenBackground">@color/mb_abertura</item>
        <item name="windowSplashScreenAnimatedIcon">@drawable/splash_vazio</item>
        <item name="postSplashScreenTheme">@style/AppTheme.NoActionBar</item>
    </style>
</resources>
XML
cat > "$RES/values-v31/styles.xml" <<'XML'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="AppTheme.NoActionBarLaunch" parent="Theme.SplashScreen">
        <item name="android:background">@color/mb_abertura</item>
        <item name="android:windowBackground">@color/mb_abertura</item>
        <item name="android:statusBarColor">@color/mb_bordo</item>
        <item name="windowSplashScreenBackground">@color/mb_abertura</item>
        <item name="windowSplashScreenAnimatedIcon">@drawable/splash_vazio</item>
        <item name="postSplashScreenTheme">@style/AppTheme.NoActionBar</item>
        <item name="android:windowSplashScreenBackground">@color/mb_abertura</item>
        <item name="android:windowSplashScreenAnimatedIcon">@drawable/splash_vazio</item>
    </style>
</resources>
XML

# Widget "Lançamento rápido" (barra bordô na tela inicial)
mkdir -p "$RES/layout" "$RES/xml" "$RES/drawable-nodpi"
cp native/widget/mb_widget_quick.xml "$RES/layout/mb_widget_quick.xml"
cp native/widget/mb_widget_bg.xml "$RES/drawable/mb_widget_bg.xml"
cp native/widget/mb_widget_bg_light.xml "$RES/drawable/mb_widget_bg_light.xml"
cp native/widget/mb_wprev_light.png native/widget/mb_wprev_dark.png native/widget/mb_wprev_app.png "$RES/drawable-nodpi/"
cp native/widget/mb_widget_btn.xml "$RES/drawable/mb_widget_btn.xml"
cp native/widget/mb_widget_info.xml "$RES/xml/mb_widget_info.xml"
cp native/widget/mb_widget_glyph.png "$RES/drawable-nodpi/mb_widget_glyph.png"
cp native/widget/mb_widget_glyph_hide.png "$RES/drawable-nodpi/mb_widget_glyph_hide.png"
if ! grep -q "QuickWidgetProvider" "$MANIFEST"; then
  sed -i '0,/<\/application>/s//    <receiver android:name="com.caiograton.meubolso.QuickWidgetProvider" android:exported="true" android:label="Sigillum">\n            <intent-filter>\n                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" \/>\n                <action android:name="android.intent.action.DATE_CHANGED" \/>\n                <action android:name="android.intent.action.TIME_SET" \/>\n                <action android:name="android.intent.action.TIMEZONE_CHANGED" \/>\n            <\/intent-filter>\n            <meta-data android:name="android.appwidget.provider" android:resource="@xml\/mb_widget_info" \/>\n        <\/receiver>\n    <\/application>/' "$MANIFEST"
fi
grep -q "QuickWidgetProvider" "$MANIFEST" || { echo "Não foi possível registrar o widget"; exit 1; }
# Tela de escolha do fundo do widget (abre ao adicionar e em "Configurar")
if ! grep -q "QuickWidgetConfigActivity" "$MANIFEST"; then
  sed -i '0,/<\/application>/s//    <activity android:name="com.caiograton.meubolso.QuickWidgetConfigActivity" android:exported="true" android:label="Sigillum" android:theme="@style\/AppTheme.NoActionBar">\n            <intent-filter>\n                <action android:name="android.appwidget.action.APPWIDGET_CONFIGURE" \/>\n            <\/intent-filter>\n        <\/activity>\n    <\/application>/' "$MANIFEST"
fi
grep -q "QuickWidgetConfigActivity" "$MANIFEST" || { echo "Não foi possível registrar a tela do widget"; exit 1; }

echo "Personalizações nativas aplicadas."
