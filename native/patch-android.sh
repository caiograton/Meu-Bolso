#!/usr/bin/env bash
# Aplica as personalizações nativas ao projeto Android gerado pelo Capacitor.
set -euo pipefail

PKG_DIR="android/app/src/main/java/com/caiograton/meubolso"
test -d "$PKG_DIR" || { echo "Pasta $PKG_DIR não encontrada"; exit 1; }

cp native/DeviceLockPlugin.java "$PKG_DIR/DeviceLockPlugin.java"

cat > "$PKG_DIR/MainActivity.java" <<'JAVA'
package com.caiograton.meubolso;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(DeviceLockPlugin.class);
        super.onCreate(savedInstanceState);
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
