#!/usr/bin/env bash
# Aplica as personalizações nativas ao projeto Android gerado pelo Capacitor.
set -euo pipefail

PKG_DIR="android/app/src/main/java/com/caiograton/meubolso"
test -d "$PKG_DIR" || { echo "Pasta $PKG_DIR não encontrada"; exit 1; }

cp native/DeviceLockPlugin.java "$PKG_DIR/DeviceLockPlugin.java"
cp native/SafeAreaPlugin.java "$PKG_DIR/SafeAreaPlugin.java"

cat > "$PKG_DIR/MainActivity.java" <<'JAVA'
package com.caiograton.meubolso;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    private static final int BORDO = 0xFF7B1F2A;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(DeviceLockPlugin.class);
        registerPlugin(SafeAreaPlugin.class);
        super.onCreate(savedInstanceState);

        // Modo "borda a borda" igual em todas as versões do Android: o teclado
        // passa a ser tratado só por nós (abaixo), sem redimensionar duas vezes.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        getWindow().setStatusBarColor(BORDO);
        getWindow().setNavigationBarColor(Color.BLACK);
        WindowInsetsControllerCompat wc = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        wc.setAppearanceLightStatusBars(false);
        wc.setAppearanceLightNavigationBars(false);

        // O app nunca fica por baixo das barras do Android: o conteúdo é
        // empurrado para dentro e as faixas das barras são pintadas aqui
        // (bordô em cima, preto embaixo).
        final FrameLayout decor = (FrameLayout) getWindow().getDecorView();
        final View top = new View(this);
        top.setBackgroundColor(BORDO);
        final View bottom = new View(this);
        bottom.setBackgroundColor(Color.BLACK);
        decor.addView(top, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, Gravity.TOP));
        decor.addView(bottom, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, Gravity.BOTTOM));

        final View content = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, w) -> {
            Insets sys = w.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            int imeBottom = 0;
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                imeBottom = w.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            }
            v.setPadding(sys.left, sys.top, sys.right, Math.max(sys.bottom, imeBottom));
            top.getLayoutParams().height = sys.top;
            top.requestLayout();
            bottom.getLayoutParams().height = sys.bottom;
            bottom.requestLayout();
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(content);
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
