package com.caiograton.meubolso;

import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.View;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Vibração leve do sistema (a mesma dos apps nativos). Usa o retorno tátil do
 * próprio Android, então respeita a opção "Retorno tátil" do celular e não
 * precisa de permissão.
 */
@CapacitorPlugin(name = "Haptic")
public class HapticPlugin extends Plugin {
    @PluginMethod
    public void tap(PluginCall call) {
        String k = call.getString("kind", "tick");
        final int c;
        if ("confirm".equals(k)) {
            c = Build.VERSION.SDK_INT >= 30 ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.VIRTUAL_KEY;
        } else if ("on".equals(k)) {
            c = Build.VERSION.SDK_INT >= 34 ? HapticFeedbackConstants.TOGGLE_ON : HapticFeedbackConstants.CLOCK_TICK;
        } else if ("off".equals(k)) {
            c = Build.VERSION.SDK_INT >= 34 ? HapticFeedbackConstants.TOGGLE_OFF : HapticFeedbackConstants.CLOCK_TICK;
        } else {
            c = HapticFeedbackConstants.CLOCK_TICK;
        }
        try {
            final View v = getBridge().getWebView();
            v.post(() -> v.performHapticFeedback(c));
        } catch (Exception ignored) { }
        call.resolve();
    }
}
