#!/usr/bin/env bash
# Aplica as personalizações nativas ao projeto Android gerado pelo Capacitor.
set -euo pipefail

PKG_DIR="android/app/src/main/java/com/caiograton/meubolso"
test -d "$PKG_DIR" || { echo "Pasta $PKG_DIR não encontrada"; exit 1; }

cp native/DeviceLockPlugin.java "$PKG_DIR/DeviceLockPlugin.java"
cp native/SafeAreaPlugin.java "$PKG_DIR/SafeAreaPlugin.java"
cp native/InsetMath.java "$PKG_DIR/InsetMath.java"

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
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    private static final int BORDO = 0xFF7B1F2A;
    private View top;
    private View bottom;
    private View content;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(DeviceLockPlugin.class);
        registerPlugin(SafeAreaPlugin.class);
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        getWindow().setStatusBarColor(BORDO);
        getWindow().setNavigationBarColor(Color.BLACK);
        WindowInsetsControllerCompat wc = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        wc.setAppearanceLightStatusBars(false);
        wc.setAppearanceLightNavigationBars(false);

        // As faixas das barras do Android são pintadas aqui (bordô em cima,
        // preto embaixo) e o app fica sempre entre elas e acima do teclado.
        final FrameLayout decor = (FrameLayout) getWindow().getDecorView();
        top = new View(this);
        top.setBackgroundColor(BORDO);
        bottom = new View(this);
        bottom.setBackgroundColor(Color.BLACK);
        decor.addView(top, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, Gravity.TOP));
        decor.addView(bottom, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, Gravity.BOTTOM));

        content = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, w) -> {
            v.post(this::applyInsets);
            return WindowInsetsCompat.CONSUMED;
        });
        // Depois de cada layout: se o sistema já encolheu a janela por causa do
        // teclado, não somamos o teclado de novo (era o que deixava a tela cinza).
        content.getViewTreeObserver().addOnGlobalLayoutListener(this::applyInsets);
        ViewCompat.requestApplyInsets(content);
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
        boolean imeVisible = w.isVisible(WindowInsetsCompat.Type.ime());
        int imeBottom = imeVisible ? w.getInsets(WindowInsetsCompat.Type.ime()).bottom : 0;
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

echo "Personalizações nativas aplicadas."
