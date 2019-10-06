package org.deskconn.deskconn.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.LinkAddress;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.room.Room;

import org.deskconn.deskconn.utils.database.AppDatabase;
import org.deskconn.deskconn.utils.database.Service;
import org.libsodium.jni.keys.SigningKey;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

import io.crossbar.autobahn.wamp.Client;
import io.crossbar.autobahn.wamp.Session;
import io.crossbar.autobahn.wamp.auth.CryptosignAuth;
import io.crossbar.autobahn.wamp.types.CallResult;

public class DeskConn {

    private static final String TAG = DeskConn.class.getName();
    private static final String SERVICE_NAME = "_deskconn._tcp.local.";

    private final List<Consumer<Session>> mOnConnectListeners;
    private final List<Runnable> mOnDisconnectListeners;
    private final List<Consumer<Service>> mOnServiceFoundListeners;
    private final List<Consumer<String>> mOnServiceLostListeners;

    private Context mContext;
    private Session mWAMP;
    private JmDNS mJmDNS;
    private ExecutorService mExecutor;
    private String mWiFiIP;
    private Helpers mHelpers;
    private AppDatabase mDB;

    public DeskConn(Context context) {
        mContext = context;
        mOnConnectListeners = new ArrayList<>();
        mOnDisconnectListeners = new ArrayList<>();
        mOnServiceFoundListeners = new ArrayList<>();
        mOnServiceLostListeners = new ArrayList<>();
        mExecutor = Executors.newSingleThreadExecutor();
        mHelpers = new Helpers(mContext);
        mDB = Room.databaseBuilder(context, AppDatabase.class, "services").build();
        ensureKeyPair();
    }

    private void ensureKeyPair() {
        if (!mHelpers.areKeysSet()) {
            SigningKey key = new SigningKey();
            String pubKey = key.getVerifyKey().toString();
            String privKey = key.toString();
            mHelpers.saveKeys(pubKey, privKey);
        }
    }

    private void trackWiFiState() {
        ConnectivityManager cm = mContext.getSystemService(ConnectivityManager.class);
        NetworkRequest.Builder builder = new NetworkRequest.Builder();
        builder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
        cm.requestNetwork(builder.build(), new NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                super.onAvailable(network);
                mWiFiIP = getNetworkIP(network);
                actuallyStartDiscovery().thenAccept(started -> {

                });
            }

            @Override
            public void onLost(@NonNull Network network) {
                super.onLost(network);
                mWiFiIP = null;
                stopDiscovery();
            }
        });
    }

    public boolean isWiFiConnected() {
        return mWiFiIP != null;
    }

    public void addOnConnectListener(Consumer<Session> callback) {
        mOnConnectListeners.add(callback);
        if (isConnected()) {
            callback.accept(mWAMP);
        }
    }

    public void removeOnConnectListener(Consumer<Session> method) {
        mOnConnectListeners.remove(method);
    }

    public void addOnDisconnectListener(Runnable method) {
        mOnDisconnectListeners.add(method);
    }

    public void removeOnDisconnectListener(Runnable method) {
        mOnDisconnectListeners.remove(method);
    }

    public void addOnServiceFoundListener(Consumer<Service> callback) {
        mOnServiceFoundListeners.add(callback);
    }

    public void removeOnServiceFoundListner(Consumer<Service> callback) {
        mOnServiceFoundListeners.remove(callback);
    }

    public void addOnServiceLostListener(Consumer<String> callback) {
        mOnServiceLostListeners.add(callback);
    }

    public void removeOnServiceLostListener(Consumer<String> callback) {
        mOnServiceLostListeners.remove(callback);
    }

    public void startDiscovery() {
        trackWiFiState();
    }

    private CompletableFuture<Boolean> actuallyStartDiscovery() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        mExecutor.submit(() -> {
            try {
                mJmDNS = JmDNS.create(InetAddress.getByName(mWiFiIP));
                mJmDNS.addServiceListener(SERVICE_NAME, new ServiceListener() {
                    @Override
                    public void serviceAdded(ServiceEvent event) {}

                    @Override
                    public void serviceRemoved(ServiceEvent event) {
                        try {
                            String host = event.getDNS().getInetAddress().getHostAddress();
                            mOnServiceLostListeners.forEach(consumer -> consumer.accept(host));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void serviceResolved(ServiceEvent event) {
                        ServiceInfo info = event.getInfo();
                        try {
                            String host = event.getDNS().getInetAddress().getHostAddress();
                            Service service = new Service(event.getName(), host,
                                    info.getPropertyString("realm"), info.getPort(),
                                    info.getPropertyString("uid"));
                            mOnServiceFoundListeners.forEach(consumer -> consumer.accept(service));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
                future.complete(true);
            } catch (IOException e) {
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    public void stopDiscovery() {
        if (mJmDNS != null) {
            mJmDNS.unregisterAllServices();
            mJmDNS = null;
        }
    }

    private String getNetworkIP(Network network) {
        ConnectivityManager cm = mContext.getSystemService(ConnectivityManager.class);
        List<LinkAddress> addresses = cm.getLinkProperties(network).getLinkAddresses();
        for (LinkAddress linkAddress: addresses) {
            if (linkAddress.getAddress() instanceof Inet4Address) {
                return linkAddress.getAddress().getHostName();
            }
        }
        return null;
    }

    public boolean isWiFiEnabled() {
        WifiManager wifimanager = mContext.getSystemService(WifiManager.class);
        if (wifimanager == null) {
            return false;
        }
        return wifimanager.isWifiEnabled();
    }

    public boolean isPaired(String uid) {
        return mDB.servicesDao().findBySystemUID(uid) != null;
    }

    public synchronized void disconnect() {
        if (isConnected()) {
            mWAMP.leave();
        }
    }

    public boolean isConnected() {
        return mWAMP != null && mWAMP.isConnected();
    }

    public CompletableFuture<Boolean> pair(String otp) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        Helpers.KeyPair keyPair = mHelpers.getKeyPair();

        CompletableFuture<CallResult> call = mWAMP.call("org.deskconn.pairing.pair", otp,
                keyPair.getPublicKey());
        call.thenAccept(callResult -> {
            mHelpers.setPaired(true);
            future.complete(true);
        });
        call.exceptionally(throwable -> {
            future.complete(false);
            Log.e(TAG, "pair: ", throwable);
            return null;
        });
        return future;
    }

    public void connect(Service service) {
        mWAMP = new Session();
        mWAMP.addOnJoinListener((session, details) -> {
            mOnConnectListeners.forEach(sessionConsumer -> sessionConsumer.accept(session));
        });
        mWAMP.addOnLeaveListener((session, details) -> {
            System.out.println("Left...");
            System.out.println(details.reason);
        });
        String crossbarURL = String.format(Locale.US, "ws://%s:%d/ws", service.getHostIP(),
                service.getPort());
        Helpers helpers = new Helpers(mContext);
        Client client;
        if (helpers.isPaired()) {
            Helpers.KeyPair keyPair = mHelpers.getKeyPair();
            CryptosignAuth auth = new CryptosignAuth(keyPair.getPublicKey(),
                    keyPair.getPrivateKey(), keyPair.getPublicKey());
            client = new Client(mWAMP, crossbarURL, service.getRealm(), auth);
        } else {
            client = new Client(mWAMP, crossbarURL, service.getRealm());
        }
        client.connect().whenComplete((exitInfo, throwable) -> {
            mOnDisconnectListeners.forEach(Runnable::run);
        });
    }
}
