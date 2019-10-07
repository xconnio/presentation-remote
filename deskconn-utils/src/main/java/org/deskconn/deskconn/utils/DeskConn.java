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
import org.deskconn.deskconn.utils.database.ServiceDao;
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

    private final List<OnConnectListener> mOnConnectListeners;
    private final List<OnDisconnectListener> mOnDisconnectListeners;
    private final List<OnServiceFoundListener> mOnServiceFoundListeners;
    private final List<OnServiceLostListener> mOnServiceLostListeners;

    private Context mContext;
    private Session mWAMP;
    private JmDNS mJmDNS;
    private ExecutorService mExecutor;
    private String mWiFiIP;
    private Helpers mHelpers;
    private ServiceDao mServiceDao;

    public DeskConn(Context context) {
        mContext = context;
        mOnConnectListeners = new ArrayList<>();
        mOnDisconnectListeners = new ArrayList<>();
        mOnServiceFoundListeners = new ArrayList<>();
        mOnServiceLostListeners = new ArrayList<>();
        mExecutor = Executors.newSingleThreadExecutor();
        mHelpers = new Helpers(mContext);
        AppDatabase db = Room.databaseBuilder(context, AppDatabase.class, "services").build();
        mServiceDao = db.servicesDao();
        ensureKeyPair();
    }

    public interface OnConnectListener {
        void onConnect(Session session);
    }

    public interface OnDisconnectListener {
        void onDisconnect();
    }

    public interface OnServiceFoundListener {
        void onFound(Service service);
    }

    public interface OnServiceLostListener {
        void onLost(String hostIP);
    }

    public void addOnConnectListener(OnConnectListener listener) {
        mOnConnectListeners.add(listener);
        if (isConnected()) {
            listener.onConnect(mWAMP);
        }
    }

    public void removeOnConnectListener(OnConnectListener listener) {
        mOnConnectListeners.remove(listener);
    }

    public void addOnDisconnectListener(OnDisconnectListener listener) {
        mOnDisconnectListeners.add(listener);
    }

    public void removeOnDisconnectListener(OnDisconnectListener listener) {
        mOnDisconnectListeners.remove(listener);
    }

    public void addOnServiceFoundListener(OnServiceFoundListener listener) {
        mOnServiceFoundListeners.add(listener);
    }

    public void removeOnServiceFoundListener(OnServiceFoundListener listener) {
        mOnServiceFoundListeners.remove(listener);
    }

    public void addOnServiceLostListener(OnServiceLostListener listener) {
        mOnServiceLostListeners.add(listener);
    }

    public void removeOnServiceLostListener(OnServiceLostListener listener) {
        mOnServiceLostListeners.remove(listener);
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

    public boolean isWiFiEnabled() {
        WifiManager wifimanager = mContext.getSystemService(WifiManager.class);
        if (wifimanager == null) {
            return false;
        }
        return wifimanager.isWifiEnabled();
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
                            mOnServiceLostListeners.forEach(listener -> listener.onLost(host));
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
                            mOnServiceFoundListeners.forEach(listener -> listener.onFound(service));
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

    public CompletableFuture<Boolean> pair(Service service, String otp) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        Helpers.KeyPair keyPair = mHelpers.getKeyPair();

        CompletableFuture<CallResult> callFuture = mWAMP.call("org.deskconn.pairing.pair", otp,
                keyPair.getPublicKey());
        callFuture.thenAccept(callResult -> {
            mServiceDao.insertAll(service);
            future.complete(true);
        });
        callFuture.exceptionally(throwable -> {
            future.complete(false);
            Log.e(TAG, "pair: ", throwable);
            return null;
        });
        return future;
    }

    public CompletableFuture<Boolean> isPaired(Service service) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        mExecutor.submit(() -> {
            Service pairedService = mServiceDao.findBySystemUID(service.getSystemUID());
            if (pairedService == null) {
                future.complete(false);
            } else {
                future.complete(true);
            }
        });
        return future;
    }

    public CompletableFuture<List<Service>> getPairedServices() {
        CompletableFuture<List<Service>> future = new CompletableFuture<>();
        mExecutor.submit(() -> {
            future.complete(mServiceDao.getAll());
        });
        return future;
    }

    public synchronized void disconnect() {
        if (isConnected()) {
            mWAMP.leave();
        }
    }

    public void connect(Service service) {
        mWAMP = new Session();
        mWAMP.addOnJoinListener((session, details) -> {
            mOnConnectListeners.forEach(listener -> listener.onConnect(session));
        });
        mWAMP.addOnLeaveListener((session, details) -> {
            System.out.println("Left...");
            System.out.println(details.reason);
        });
        String crossbarURL = String.format(Locale.US, "ws://%s:%d/ws", service.getHostIP(),
                service.getPort());

        isPaired(service).thenAccept(paired -> {
            Client client;
            if (paired) {
                Helpers.KeyPair keyPair = mHelpers.getKeyPair();
                CryptosignAuth auth = new CryptosignAuth(keyPair.getPublicKey(),
                        keyPair.getPrivateKey(), keyPair.getPublicKey());
                client = new Client(mWAMP, crossbarURL, service.getRealm(), auth);
            } else {
                client = new Client(mWAMP, crossbarURL, service.getRealm());
            }

            client.connect().whenComplete((exitInfo, throwable) -> {
                mOnDisconnectListeners.forEach(OnDisconnectListener::onDisconnect);
            });
        });
    }

    public boolean isConnected() {
        return mWAMP != null && mWAMP.isConnected();
    }
}
