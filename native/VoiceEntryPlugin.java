package com.caiograton.meubolso;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognitionSupport;
import android.speech.RecognitionSupportCallback;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import java.util.ArrayList;
import java.util.List;

/**
 * Lançamento rápido por voz.
 *
 * Só usa o reconhecimento de voz DO PRÓPRIO APARELHO (Android 13 ou mais novo):
 * o áudio nunca vai para a internet. Se o aparelho não tiver o pacote do idioma,
 * o app avisa e explica como baixar; nada é enviado para a nuvem.
 */
@CapacitorPlugin(
    name = "VoiceEntry",
    permissions = { @Permission(alias = "mic", strings = { Manifest.permission.RECORD_AUDIO }) }
)
public class VoiceEntryPlugin extends Plugin {

    private SpeechRecognizer rec;
    private PluginCall cur;
    private long lastLevel = 0;

    /** Diz se dá para reconhecer voz sem internet e se o idioma já está baixado. */
    @PluginMethod
    public void status(final PluginCall call) {
        if (Build.VERSION.SDK_INT < 33) {
            JSObject o = new JSObject();
            o.put("state", "old");
            call.resolve(o);
            return;
        }
        final String lang = call.getString("lang", "pt-BR");
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                checkSupport(call, lang);
            }
        });
    }

    @RequiresApi(33)
    private void checkSupport(final PluginCall call, String lang) {
        final JSObject o = new JSObject();
        try {
            Context ctx = getContext();
            if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx)) {
                o.put("state", "none");
                call.resolve(o);
                return;
            }
            final SpeechRecognizer r = SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx);
            Intent it = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            it.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            it.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
            r.checkRecognitionSupport(it, ContextCompat.getMainExecutor(ctx), new RecognitionSupportCallback() {
                @Override
                public void onSupportResult(RecognitionSupport s) {
                    JSArray installed = new JSArray();
                    for (String l : s.getInstalledOnDeviceLanguages()) installed.put(l);
                    JSArray supported = new JSArray();
                    for (String l : s.getSupportedOnDeviceLanguages()) supported.put(l);
                    o.put("state", "ok");
                    o.put("installed", installed);
                    o.put("supported", supported);
                    try { r.destroy(); } catch (Exception ignored) { }
                    call.resolve(o);
                }

                @Override
                public void onError(int error) {
                    o.put("state", "unknown");
                    try { r.destroy(); } catch (Exception ignored) { }
                    call.resolve(o);
                }
            });
        } catch (Exception e) {
            o.put("state", "unknown");
            call.resolve(o);
        }
    }

    /** Pede a permissão do microfone (uma vez; o Android lembra a resposta). */
    @PluginMethod
    public void askMic(PluginCall call) {
        if (getPermissionState("mic") == PermissionState.GRANTED) {
            JSObject o = new JSObject();
            o.put("granted", true);
            call.resolve(o);
            return;
        }
        requestPermissionForAlias("mic", call, "askResult");
    }

    @PermissionCallback
    private void askResult(PluginCall call) {
        JSObject o = new JSObject();
        o.put("granted", getPermissionState("mic") == PermissionState.GRANTED);
        call.resolve(o);
    }

    /** Escuta uma frase (um lançamento) e devolve o texto. */
    @PluginMethod
    public void listen(PluginCall call) {
        if (Build.VERSION.SDK_INT < 33) {
            call.reject("old", "old");
            return;
        }
        if (getPermissionState("mic") != PermissionState.GRANTED) {
            call.reject("perm", "perm");
            return;
        }
        startListening(call);
    }

    @RequiresApi(33)
    private void startListening(final PluginCall call) {
        final String lang = call.getString("lang", "pt-BR");
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                stopRec();
                Context ctx = getContext();
                try {
                    if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx)) {
                        call.reject("none", "none");
                        return;
                    }
                    rec = SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx);
                } catch (Exception e) {
                    rec = null;
                    call.reject("fail", "fail");
                    return;
                }
                cur = call;
                rec.setRecognitionListener(new RecognitionListener() {
                    @Override
                    public void onReadyForSpeech(Bundle params) {
                        notifyListeners("ready", new JSObject());
                    }

                    @Override
                    public void onBeginningOfSpeech() { }

                    @Override
                    public void onRmsChanged(float rmsdB) {
                        long now = System.currentTimeMillis();
                        if (now - lastLevel < 90) return;
                        lastLevel = now;
                        JSObject o = new JSObject();
                        o.put("v", rmsdB);
                        notifyListeners("level", o);
                    }

                    @Override
                    public void onBufferReceived(byte[] buffer) { }

                    @Override
                    public void onEndOfSpeech() { }

                    @Override
                    public void onError(int error) {
                        finish(call, null, codeFor(error));
                    }

                    @Override
                    public void onResults(Bundle results) {
                        ArrayList<String> l = results == null ? null
                            : results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (l == null || l.isEmpty() || l.get(0) == null || l.get(0).trim().isEmpty()) {
                            finish(call, null, "nomatch");
                            return;
                        }
                        JSObject o = new JSObject();
                        o.put("text", l.get(0));
                        JSArray alts = new JSArray();
                        for (String s : l) alts.put(s);
                        o.put("alts", alts);
                        finish(call, o, null);
                    }

                    @Override
                    public void onPartialResults(Bundle partial) {
                        ArrayList<String> l = partial == null ? null
                            : partial.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (l == null || l.isEmpty() || l.get(0) == null) return;
                        JSObject o = new JSObject();
                        o.put("text", l.get(0));
                        notifyListeners("partial", o);
                    }

                    @Override
                    public void onEvent(int eventType, Bundle params) { }
                });
                Intent it = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                it.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                it.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
                it.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
                it.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
                it.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
                it.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.getPackageName());
                try {
                    rec.startListening(it);
                } catch (Exception e) {
                    finish(call, null, "fail");
                }
            }
        });
    }

    private static String codeFor(int error) {
        switch (error) {
            case 7: return "nomatch";   // ERROR_NO_MATCH
            case 6: return "timeout";   // ERROR_SPEECH_TIMEOUT
            case 9: return "perm";      // ERROR_INSUFFICIENT_PERMISSIONS
            case 12:                    // ERROR_LANGUAGE_NOT_SUPPORTED
            case 13: return "lang";     // ERROR_LANGUAGE_UNAVAILABLE
            case 3: return "audio";     // ERROR_AUDIO
            case 8: return "busy";      // ERROR_RECOGNIZER_BUSY
            default: return "fail";
        }
    }

    private void finish(PluginCall call, JSObject ok, String err) {
        if (cur != call) return;
        cur = null;
        stopRec();
        if (err != null) call.reject(err, err);
        else call.resolve(ok);
    }

    private void stopRec() {
        SpeechRecognizer r = rec;
        rec = null;
        if (r != null) {
            try { r.cancel(); } catch (Exception ignored) { }
            try { r.destroy(); } catch (Exception ignored) { }
        }
    }

    /** O usuário tocou em "Terminar": encerra a escuta e entrega o que foi ouvido. */
    @PluginMethod
    public void stop(final PluginCall call) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try { if (rec != null) rec.stopListening(); } catch (Exception ignored) { }
                call.resolve();
            }
        });
    }

    /** Cancela a escuta atual (voltar, fechar, app indo para segundo plano). */
    @PluginMethod
    public void cancel(final PluginCall call) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                PluginCall c = cur;
                cur = null;
                stopRec();
                if (c != null) c.reject("cancel", "cancel");
                call.resolve();
            }
        });
    }

    @Override
    protected void handleOnDestroy() {
        stopRec();
        super.handleOnDestroy();
    }
}
