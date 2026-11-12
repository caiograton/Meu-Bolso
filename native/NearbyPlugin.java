package com.caiograton.meubolso;

import android.Manifest;
import android.os.Build;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;
import java.nio.charset.StandardCharsets;

/**
 * Troca de lançamentos por perto: os dois celulares conversam direto
 * (Bluetooth e Wi-Fi, sem internet e sem servidor). O QR mostrado na tela
 * traz o nome do ponto de encontro e a chave; tudo o que passa por aqui já
 * vem cifrado do app.
 */
@CapacitorPlugin(
    name = "Nearby",
    permissions = {
        @Permission(alias = "n33", strings = {
            "android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_ADVERTISE",
            "android.permission.BLUETOOTH_CONNECT", "android.permission.NEARBY_WIFI_DEVICES" }),
        @Permission(alias = "n31", strings = {
            "android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_ADVERTISE",
            "android.permission.BLUETOOTH_CONNECT", Manifest.permission.ACCESS_FINE_LOCATION }),
        @Permission(alias = "nloc", strings = { Manifest.permission.ACCESS_FINE_LOCATION })
    }
)
public class NearbyPlugin extends Plugin {
    private static final String SERVICE = "com.caiograton.meubolso.troca";
    private static final int MAX_BYTES = 32768;
    private ConnectionsClient cc;

    @Override
    public void load() {
        cc = Nearby.getConnectionsClient(getContext());
    }

    private String alias() {
        int v = Build.VERSION.SDK_INT;
        return v >= 33 ? "n33" : (v >= 31 ? "n31" : "nloc");
    }

    private String stateOf() {
        PermissionState s = getPermissionState(alias());
        return s == null ? "prompt" : s.toString();
    }

    private static String err(Exception e) {
        if (e instanceof ApiException) return "code:" + ((ApiException) e).getStatusCode();
        return String.valueOf(e.getMessage());
    }

    @PluginMethod
    public void check(PluginCall call) {
        JSObject o = new JSObject();
        o.put("state", stateOf());
        call.resolve(o);
    }

    @PluginMethod
    public void request(PluginCall call) {
        if (getPermissionState(alias()) == PermissionState.GRANTED) {
            check(call);
            return;
        }
        requestPermissionForAlias(alias(), call, "permCb");
    }

    @PermissionCallback
    private void permCb(PluginCall call) {
        check(call);
    }

    private final PayloadCallback payloadCb = new PayloadCallback() {
        @Override
        public void onPayloadReceived(String id, Payload payload) {
            if (payload.getType() != Payload.Type.BYTES || payload.asBytes() == null) return;
            JSObject o = new JSObject();
            o.put("id", id);
            o.put("text", new String(payload.asBytes(), StandardCharsets.UTF_8));
            notifyListeners("message", o);
        }

        @Override
        public void onPayloadTransferUpdate(String id, PayloadTransferUpdate update) { }
    };

    private final ConnectionLifecycleCallback lifecycle = new ConnectionLifecycleCallback() {
        @Override
        public void onConnectionInitiated(String id, ConnectionInfo info) {
            JSObject o = new JSObject();
            o.put("id", id);
            o.put("name", info.getEndpointName());
            o.put("incoming", info.isIncomingConnection());
            notifyListeners("initiated", o);
        }

        @Override
        public void onConnectionResult(String id, ConnectionResolution result) {
            JSObject o = new JSObject();
            o.put("id", id);
            o.put("ok", result.getStatus().isSuccess());
            o.put("code", result.getStatus().getStatusCode());
            notifyListeners("result", o);
        }

        @Override
        public void onDisconnected(String id) {
            JSObject o = new JSObject();
            o.put("id", id);
            notifyListeners("disconnected", o);
        }
    };

    private final EndpointDiscoveryCallback discovery = new EndpointDiscoveryCallback() {
        @Override
        public void onEndpointFound(String id, DiscoveredEndpointInfo info) {
            JSObject o = new JSObject();
            o.put("id", id);
            o.put("name", info.getEndpointName());
            notifyListeners("found", o);
        }

        @Override
        public void onEndpointLost(String id) {
            JSObject o = new JSObject();
            o.put("id", id);
            notifyListeners("lost", o);
        }
    };

    @PluginMethod
    public void advertise(PluginCall call) {
        String name = call.getString("name", "");
        AdvertisingOptions opt = new AdvertisingOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build();
        cc.startAdvertising(name, SERVICE, lifecycle, opt)
            .addOnSuccessListener(v -> call.resolve())
            .addOnFailureListener(e -> call.reject(err(e)));
    }

    @PluginMethod
    public void discover(PluginCall call) {
        DiscoveryOptions opt = new DiscoveryOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build();
        cc.startDiscovery(SERVICE, discovery, opt)
            .addOnSuccessListener(v -> call.resolve())
            .addOnFailureListener(e -> call.reject(err(e)));
    }

    @PluginMethod
    public void connect(PluginCall call) {
        String id = call.getString("id", "");
        String name = call.getString("name", "");
        cc.requestConnection(name, id, lifecycle)
            .addOnSuccessListener(v -> call.resolve())
            .addOnFailureListener(e -> call.reject(err(e)));
    }

    @PluginMethod
    public void accept(PluginCall call) {
        String id = call.getString("id", "");
        cc.acceptConnection(id, payloadCb)
            .addOnSuccessListener(v -> call.resolve())
            .addOnFailureListener(e -> call.reject(err(e)));
    }

    @PluginMethod
    public void reject(PluginCall call) {
        String id = call.getString("id", "");
        cc.rejectConnection(id)
            .addOnSuccessListener(v -> call.resolve())
            .addOnFailureListener(e -> call.reject(err(e)));
    }

    @PluginMethod
    public void send(PluginCall call) {
        String id = call.getString("id", "");
        String text = call.getString("text", "");
        byte[] b = text.getBytes(StandardCharsets.UTF_8);
        if (b.length > MAX_BYTES) { call.reject("big"); return; }
        cc.sendPayload(id, Payload.fromBytes(b))
            .addOnSuccessListener(v -> call.resolve())
            .addOnFailureListener(e -> call.reject(err(e)));
    }

    private void stopAll() {
        try { cc.stopAdvertising(); } catch (Exception ignored) { }
        try { cc.stopDiscovery(); } catch (Exception ignored) { }
        try { cc.stopAllEndpoints(); } catch (Exception ignored) { }
    }

    @PluginMethod
    public void stop(PluginCall call) {
        stopAll();
        call.resolve();
    }

    @Override
    protected void handleOnDestroy() {
        stopAll();
        super.handleOnDestroy();
    }
}
