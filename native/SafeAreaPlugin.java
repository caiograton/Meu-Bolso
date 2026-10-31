package com.caiograton.meubolso;

import android.view.View;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Informa ao app o espaço ocupado pela barra de status (em cima) e pela
 * barra de navegação do Android (embaixo), em pixels CSS, para que o
 * conteúdo não fique escondido atrás delas.
 * Se a WebView já estiver deslocada pelo sistema, desconta esse deslocamento,
 * evitando espaço duplicado.
 */
@CapacitorPlugin(name = "SafeArea")
public class SafeAreaPlugin extends Plugin {

    @PluginMethod
    public void getInsets(final PluginCall call) {
        if (getActivity() == null) {
            call.reject("Atividade indisponível");
            return;
        }
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                JSObject o = new JSObject();
                View v = getBridge().getWebView();
                float d = getContext().getResources().getDisplayMetrics().density;
                int top = 0, bottom = 0;
                WindowInsetsCompat w = v == null ? null : ViewCompat.getRootWindowInsets(v);
                if (w != null) {
                    Insets i = w.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                    int[] loc = new int[2];
                    v.getLocationOnScreen(loc);
                    int rootH = v.getRootView().getHeight();
                    int below = rootH - (loc[1] + v.getHeight());
                    top = Math.max(0, i.top - loc[1]);
                    bottom = Math.max(0, i.bottom - Math.max(0, below));
                }
                o.put("top", top / d);
                o.put("bottom", bottom / d);
                call.resolve(o);
            }
        });
    }
}
