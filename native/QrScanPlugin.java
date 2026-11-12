package com.caiograton.meubolso;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;

/**
 * Leitura do QR de troca com o leitor do Google Play Services: a câmera abre
 * numa tela do próprio sistema e o Sigillum recebe só o texto do QR (o app
 * não precisa da permissão da câmera para isso).
 */
@CapacitorPlugin(name = "QrScan")
public class QrScanPlugin extends Plugin {

    @PluginMethod
    public void scan(PluginCall call) {
        GmsBarcodeScannerOptions opt = new GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build();
        GmsBarcodeScanner sc = GmsBarcodeScanning.getClient(getActivity(), opt);
        sc.startScan()
            .addOnSuccessListener(b -> {
                JSObject r = new JSObject();
                r.put("text", b.getRawValue() == null ? "" : b.getRawValue());
                call.resolve(r);
            })
            .addOnCanceledListener(() -> {
                JSObject r = new JSObject();
                r.put("cancelled", true);
                call.resolve(r);
            })
            .addOnFailureListener(e -> call.reject(String.valueOf(e.getMessage())));
    }
}
