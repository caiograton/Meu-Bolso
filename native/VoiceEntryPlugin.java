package com.caiograton.meubolso;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.json.JSONArray;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.SpeechService;

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
        stopLocal();
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
                try {
                    if (svc != null) svc.stop();
                    else if (rec != null) rec.stopListening();
                } catch (Exception ignored) { }
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
                vTok++;
                stopRec();
                stopLocal();
                if (c != null) c.reject("cancel", "cancel");
                call.resolve();
            }
        });
    }

    // ---------------------------------------------------------------------
    // Voz do próprio app (Vosk), para aparelhos sem reconhecimento de voz
    // offline do Android. O modelo do idioma é baixado só quando a pessoa
    // aceita, e todo o reconhecimento acontece no aparelho.
    // ---------------------------------------------------------------------

    private static final String BASE_URL = "https://alphacephei.com/vosk/models/";
    // idioma, pasta do modelo, tamanho aproximado do download (MB)
    private static final String[][] PACKS = {
        { "pt", "vosk-model-small-pt-0.3", "31" },
        { "en", "vosk-model-small-en-us-0.15", "40" },
        { "es", "vosk-model-small-es-0.42", "39" }
    };

    private SpeechService svc;
    private Model vModel;
    private String vModelLang = "";
    private volatile int vTok = 0;
    private volatile boolean packCancel = false;
    private volatile boolean packBusy = false;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable freeModel = new Runnable() {
        @Override
        public void run() {
            synchronized (VoiceEntryPlugin.this) {
                if (svc == null && vModel != null) {
                    try { vModel.close(); } catch (Throwable ignored) { }
                    vModel = null;
                    vModelLang = "";
                }
            }
        }
    };

    private String[] findPack(String lang) {
        for (String[] p : PACKS) if (p[0].equals(lang)) return p;
        return null;
    }

    private File packDir(String lang) {
        return new File(new File(getContext().getFilesDir(), "vosk"), lang);
    }

    private static long dirSize(File f) {
        if (f.isFile()) return f.length();
        long t = 0;
        File[] l = f.listFiles();
        if (l != null) for (File c : l) t += dirSize(c);
        return t;
    }

    private static void deleteRec(File f) {
        if (f == null || !f.exists()) return;
        File[] l = f.listFiles();
        if (l != null) for (File c : l) deleteRec(c);
        f.delete();
    }

    /** Quais idiomas já estão instalados e quanto ocupam. */
    @PluginMethod
    public void packs(PluginCall call) {
        JSArray arr = new JSArray();
        for (String[] pk : PACKS) {
            File d = packDir(pk[0]);
            boolean ok = new File(d, ".ok").exists();
            JSObject o = new JSObject();
            o.put("lang", pk[0]);
            o.put("installed", ok);
            o.put("mb", ok ? Math.round(dirSize(d) / 1048576.0) : 0);
            arr.put(o);
        }
        JSObject r = new JSObject();
        r.put("packs", arr);
        call.resolve(r);
    }

    /** Baixa e instala o modelo de um idioma (só depois de a pessoa aceitar). */
    @PluginMethod
    public void packGet(final PluginCall call) {
        final String lang = call.getString("lang", "");
        final String[] pk = findPack(lang);
        if (pk == null) {
            call.reject("bad", "bad");
            return;
        }
        if (packBusy) {
            call.reject("busy", "busy");
            return;
        }
        packBusy = true;
        packCancel = false;
        new Thread(new Runnable() {
            @Override
            public void run() {
                doPack(call, pk);
            }
        }).start();
    }

    private void progress(String lang, String phase, long done, long total) {
        JSObject o = new JSObject();
        o.put("lang", lang);
        o.put("phase", phase);
        o.put("done", done);
        o.put("total", total);
        notifyListeners("packProgress", o);
    }

    private void doPack(PluginCall call, String[] pk) {
        File root = new File(getContext().getFilesDir(), "vosk");
        root.mkdirs();
        File tmp = new File(root, pk[0] + ".zip.part");
        File stage = new File(root, pk[0] + ".stage");
        File out = packDir(pk[0]);
        String err = null;
        try {
            deleteRec(out);
            deleteRec(stage);
            tmp.delete();
            long expected = Long.parseLong(pk[2]) * 1048576L;
            if (root.getUsableSpace() < expected * 4) {
                err = "space";
                return;
            }
            HttpURLConnection c = (HttpURLConnection) new URL(BASE_URL + pk[1] + ".zip").openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setInstanceFollowRedirects(true);
            if (c.getResponseCode() != 200) {
                err = "net";
                return;
            }
            long total = -1;
            try { total = Long.parseLong(String.valueOf(c.getHeaderField("Content-Length"))); } catch (NumberFormatException ignored) { }
            if (total <= 0 || total > 150L * 1048576L) {
                err = "bad";
                return;
            }
            long got = 0, lastNote = 0;
            InputStream in = c.getInputStream();
            FileOutputStream fo = new FileOutputStream(tmp);
            try {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (packCancel) throw new IOException("cancel");
                    fo.write(buf, 0, n);
                    got += n;
                    long now = System.currentTimeMillis();
                    if (now - lastNote > 250) {
                        lastNote = now;
                        progress(pk[0], "down", got, total);
                    }
                }
            } finally {
                try { fo.close(); } catch (IOException ignored) { }
                try { in.close(); } catch (IOException ignored) { }
                c.disconnect();
            }
            if (got != total) {
                err = "net";
                return;
            }
            progress(pk[0], "unzip", got, total);
            if (!unzip(tmp, stage)) {
                err = packCancel ? "cancel" : "bad";
                return;
            }
            File model = findModelRoot(stage);
            if (model == null || !model.renameTo(out)) {
                err = "bad";
                return;
            }
            // confere se o modelo carrega antes de dar por instalado
            try {
                Model m = new Model(out.getAbsolutePath());
                m.close();
            } catch (Throwable t) {
                deleteRec(out);
                err = "bad";
                return;
            }
            new File(out, ".ok").createNewFile();
        } catch (IOException e) {
            String m = String.valueOf(e.getMessage());
            err = "cancel".equals(m) ? "cancel" : (m.contains("ENOSPC") ? "space" : "net");
        } catch (Throwable t) {
            err = "bad";
        } finally {
            deleteRec(stage);
            tmp.delete();
            if (err != null) deleteRec(out);
            packBusy = false;
            if (err == null) {
                JSObject o = new JSObject();
                o.put("ok", true);
                call.resolve(o);
            } else {
                call.reject(err, err);
            }
        }
    }

    private boolean unzip(File zip, File dest) throws IOException {
        dest.mkdirs();
        String base = dest.getCanonicalPath() + File.separator;
        long total = 0;
        int count = 0;
        ZipInputStream zin = new ZipInputStream(new FileInputStream(zip));
        try {
            ZipEntry e;
            byte[] buf = new byte[65536];
            while ((e = zin.getNextEntry()) != null) {
                if (packCancel) return false;
                if (++count > 5000) return false;
                File f = new File(dest, e.getName());
                if (!f.getCanonicalPath().startsWith(base)) return false;
                if (e.isDirectory()) {
                    f.mkdirs();
                    continue;
                }
                f.getParentFile().mkdirs();
                FileOutputStream fo = new FileOutputStream(f);
                try {
                    int n;
                    while ((n = zin.read(buf)) > 0) {
                        total += n;
                        if (total > 600L * 1048576L) return false;
                        fo.write(buf, 0, n);
                    }
                } finally {
                    fo.close();
                }
            }
        } finally {
            zin.close();
        }
        return true;
    }

    /** A pasta do modelo é a que tem "am" e "conf" dentro (no zip ela vem dentro de outra pasta). */
    private File findModelRoot(File dir) {
        if (new File(dir, "am").isDirectory() && new File(dir, "conf").isDirectory()) return dir;
        File[] l = dir.listFiles();
        if (l != null) {
            for (File c : l) {
                if (c.isDirectory()) {
                    File r = findModelRoot(c);
                    if (r != null) return r;
                }
            }
        }
        return null;
    }

    @PluginMethod
    public void packCancel(PluginCall call) {
        packCancel = true;
        call.resolve();
    }

    @PluginMethod
    public void packRemove(PluginCall call) {
        String lang = call.getString("lang", "");
        if (findPack(lang) == null) {
            call.reject("bad", "bad");
            return;
        }
        synchronized (this) {
            if (lang.equals(vModelLang) && vModel != null) {
                try { vModel.close(); } catch (Throwable ignored) { }
                vModel = null;
                vModelLang = "";
            }
        }
        deleteRec(packDir(lang));
        call.resolve();
    }

    /** Escuta um valor falado com a voz do app. Só números e moedas entram no vocabulário. */
    @PluginMethod
    public void listenLocal(final PluginCall call) {
        if (getPermissionState("mic") != PermissionState.GRANTED) {
            call.reject("perm", "perm");
            return;
        }
        final String lang = call.getString("lang", "pt");
        final File dir = packDir(lang);
        if (findPack(lang) == null || !new File(dir, ".ok").exists()) {
            call.reject("pack", "pack");
            return;
        }
        final int tok = ++vTok;
        main.removeCallbacks(freeModel);
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                stopRec();
                stopLocal();
                cur = call;
            }
        });
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Model m;
                    synchronized (VoiceEntryPlugin.this) {
                        if (vModel == null || !lang.equals(vModelLang)) {
                            if (vModel != null) {
                                try { vModel.close(); } catch (Throwable ignored) { }
                                vModel = null;
                            }
                            vModel = new Model(dir.getAbsolutePath());
                            vModelLang = lang;
                        }
                        m = vModel;
                    }
                    if (tok != vTok) return;
                    Recognizer r = new Recognizer(m, 16000.0f, grammar(lang));
                    final SpeechService s = new SpeechService(r, 16000.0f);
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (tok != vTok || cur != call) {
                                try { s.cancel(); s.shutdown(); } catch (Throwable ignored) { }
                                return;
                            }
                            svc = s;
                            s.startListening(localListener(call), 12000);
                            notifyListeners("ready", new JSObject());
                        }
                    });
                } catch (final Throwable t) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (tok == vTok) finish(call, null, "fail");
                        }
                    });
                }
            }
        }).start();
    }

    private static String clean(String json, String key) {
        try {
            String t = new JSONObject(json).optString(key, "");
            return t.replace("[unk]", " ").replaceAll("\\s+", " ").trim();
        } catch (Exception e) {
            return "";
        }
    }

    private JSObject okObj(String t) {
        JSObject o = new JSObject();
        o.put("text", t);
        return o;
    }

    private org.vosk.android.RecognitionListener localListener(final PluginCall call) {
        return new org.vosk.android.RecognitionListener() {
            private String last = "";

            @Override
            public void onPartialResult(String h) {
                String t = clean(h, "partial");
                if (t.isEmpty()) return;
                last = t;
                notifyListeners("partial", okObj(t));
            }

            @Override
            public void onResult(String h) {
                String t = clean(h, "text");
                if (!t.isEmpty()) finish(call, okObj(t), null);
            }

            @Override
            public void onFinalResult(String h) {
                String t = clean(h, "text");
                if (t.isEmpty()) t = last;
                if (t.isEmpty()) finish(call, null, "nomatch");
                else finish(call, okObj(t), null);
            }

            @Override
            public void onError(Exception e) {
                finish(call, null, "fail");
            }

            @Override
            public void onTimeout() {
                if (last.isEmpty()) finish(call, null, "timeout");
                else finish(call, okObj(last), null);
            }
        };
    }

    private static String grammar(String lang) {
        String w;
        if ("en".equals(lang)) {
            w = "zero one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen twenty thirty forty fifty sixty seventy eighty ninety hundred thousand and a dollar dollars euro euros pound pounds cent cents buck bucks quid";
        } else if ("es".equals(lang)) {
            w = "cero uno una un dos tres cuatro cinco seis siete ocho nueve diez once doce trece catorce quince dieciséis diecisiete dieciocho diecinueve veinte veintiuno veintidós veintitrés veinticuatro veinticinco veintiséis veintisiete veintiocho veintinueve treinta cuarenta cincuenta sesenta setenta ochenta noventa cien ciento doscientos doscientas trescientos trescientas cuatrocientos cuatrocientas quinientos quinientas seiscientos seiscientas setecientos setecientas ochocientos ochocientas novecientos novecientas mil y con euro euros dólar dólares peso pesos libra libras céntimo céntimos centavo centavos";
        } else {
            w = "zero um uma dois duas três tres quatro cinco seis sete oito nove dez onze doze treze catorze quatorze quinze dezesseis dezasseis dezessete dezassete dezoito dezenove dezanove vinte trinta quarenta cinquenta sessenta setenta oitenta noventa cem cento duzentos duzentas trezentos trezentas quatrocentos quatrocentas quinhentos quinhentas seiscentos seiscentas setecentos setecentas oitocentos oitocentas novecentos novecentas mil e euro euros real reais dólar dólares libra libras centavo centavos prata pratas conto contos";
        }
        JSONArray a = new JSONArray();
        for (String x : w.split(" ")) a.put(x);
        a.put("[unk]");
        return a.toString();
    }

    private void stopLocal() {
        SpeechService s = svc;
        svc = null;
        if (s != null) {
            try { s.cancel(); } catch (Throwable ignored) { }
            try { s.shutdown(); } catch (Throwable ignored) { }
        }
        main.removeCallbacks(freeModel);
        main.postDelayed(freeModel, 30000);
    }

    @Override
    protected void handleOnDestroy() {
        stopRec();
        stopLocal();
        packCancel = true;
        freeModel.run();
        super.handleOnDestroy();
    }
}
