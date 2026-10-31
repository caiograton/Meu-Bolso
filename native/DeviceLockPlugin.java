package com.caiograton.meubolso;

import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.util.concurrent.Executor;

/**
 * Bloqueio do app usando o bloqueio de tela do próprio aparelho:
 * digital, rosto, PIN, padrão ou senha, conforme o que a pessoa já usa.
 */
@CapacitorPlugin(name = "DeviceLock")
public class DeviceLockPlugin extends Plugin {

    private static final int AUTH =
        BiometricManager.Authenticators.BIOMETRIC_WEAK | BiometricManager.Authenticators.DEVICE_CREDENTIAL;

    @PluginMethod
    public void isAvailable(PluginCall call) {
        int r = BiometricManager.from(getContext()).canAuthenticate(AUTH);
        JSObject o = new JSObject();
        o.put("available", r == BiometricManager.BIOMETRIC_SUCCESS);
        o.put("code", r);
        call.resolve(o);
    }

    @PluginMethod
    public void authenticate(final PluginCall call) {
        final FragmentActivity activity = getActivity();
        if (activity == null) {
            call.reject("Atividade indisponível");
            return;
        }
        final String title = call.getString("title", "Meu Bolso");
        final String subtitle = call.getString("subtitle", "Confirme sua identidade");
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    Executor executor = ContextCompat.getMainExecutor(activity);
                    BiometricPrompt prompt = new BiometricPrompt(activity, executor, new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                            JSObject o = new JSObject();
                            o.put("success", true);
                            call.resolve(o);
                        }

                        @Override
                        public void onAuthenticationError(int errorCode, CharSequence errString) {
                            call.reject(String.valueOf(errString), String.valueOf(errorCode));
                        }
                    });
                    BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                        .setTitle(title)
                        .setSubtitle(subtitle)
                        .setAllowedAuthenticators(AUTH)
                        .build();
                    prompt.authenticate(info);
                } catch (Exception e) {
                    call.reject(String.valueOf(e.getMessage()));
                }
            }
        });
    }
}
