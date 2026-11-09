package com.caiograton.meubolso;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * Salva um arquivo de verdade na pasta Downloads do aparelho (sem abrir o menu de compartilhar).
 * Android 10 ou mais novo: MediaStore (não precisa de permissão). Mais antigo: pasta pública + permissão de gravação.
 */
@CapacitorPlugin(
    name = "FileSave",
    permissions = { @Permission(alias = "storage", strings = { Manifest.permission.WRITE_EXTERNAL_STORAGE }) }
)
public class FileSavePlugin extends Plugin {

    @PluginMethod
    public void save(PluginCall call) {
        if (call.getString("name") == null || call.getString("data") == null) {
            call.reject("args");
            return;
        }
        if (Build.VERSION.SDK_INT < 29 && getPermissionState("storage") != PermissionState.GRANTED) {
            requestPermissionForAlias("storage", call, "storageResult");
            return;
        }
        doSave(call);
    }

    @PermissionCallback
    private void storageResult(PluginCall call) {
        if (getPermissionState("storage") == PermissionState.GRANTED) doSave(call);
        else call.reject("perm");
    }

    private void doSave(PluginCall call) {
        String name = call.getString("name").replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").replaceAll("^\\.+", "_");
        if (name.length() > 120) name = name.substring(name.length() - 120);
        if (name.isEmpty()) name = "arquivo";
        String mime = call.getString("mime", "application/octet-stream");
        if (!mime.matches("application/pdf|image/jpeg|image/png|text/csv|application/json|text/plain")) mime = "application/octet-stream";
        String b64 = call.getString("data");
        if (b64.length() > 60_000_000) { call.reject("args"); return; }
        try {
            byte[] bytes = Base64.decode(call.getString("data"), Base64.DEFAULT);
            JSObject o = new JSObject();
            if (Build.VERSION.SDK_INT >= 29) {
                ContentResolver cr = getContext().getContentResolver();
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                v.put(MediaStore.MediaColumns.IS_PENDING, 1);
                Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri == null) { call.reject("fail"); return; }
                try (OutputStream out = cr.openOutputStream(uri)) {
                    if (out == null) { call.reject("fail"); return; }
                    out.write(bytes);
                }
                ContentValues done = new ContentValues();
                done.put(MediaStore.MediaColumns.IS_PENDING, 0);
                cr.update(uri, done, null, null);
                o.put("uri", uri.toString());
                o.put("open", true);
            } else {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!dir.exists()) dir.mkdirs();
                File f = new File(dir, name);
                try (FileOutputStream out = new FileOutputStream(f)) {
                    out.write(bytes);
                }
                o.put("uri", f.getAbsolutePath());
            }
            o.put("name", name);
            call.resolve(o);
        } catch (Exception e) {
            call.reject("fail");
        }
    }

    /** Abre no leitor de PDF um arquivo que este app acabou de salvar em Downloads (Android 10 ou mais novo). */
    @PluginMethod
    public void open(PluginCall call) {
        String u = call.getString("uri");
        if (u == null || Build.VERSION.SDK_INT < 29 || !u.startsWith(MediaStore.Downloads.EXTERNAL_CONTENT_URI.toString())) {
            call.reject("args");
            return;
        }
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(Uri.parse(u), "application/pdf");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            call.resolve();
        } catch (ActivityNotFoundException e) {
            call.reject("noapp");
        } catch (Exception e) {
            call.reject("fail");
        }
    }
}
