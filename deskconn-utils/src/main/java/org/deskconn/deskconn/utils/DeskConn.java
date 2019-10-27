package org.deskconn.deskconn.utils;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

import androidx.room.Room;

import org.deskconn.deskconn.utils.database.AppDatabase;
import org.deskconn.deskconn.utils.database.Service;
import org.deskconn.deskconn.utils.database.ServiceDao;
import org.libsodium.jni.keys.SigningKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.crossbar.autobahn.wamp.Client;
import io.crossbar.autobahn.wamp.Session;
import io.crossbar.autobahn.wamp.auth.CryptosignAuth;
import io.crossbar.autobahn.wamp.types.CallResult;

public class DeskConn {

    private static final String TAG = DeskConn.class.getName();
    private static final String SERVICE_NAME = "_deskconn._tcp.local.";

    private final List<ServiceListener> mServiceListeners = new ArrayList<>();

    private ServiceFinder mFinder;
    private Session mSession;
    private ExecutorService mExecutor;
    private Helpers mHelpers;
    private ServiceDao mServiceDao;
    private ServiceFinder.ServiceListener mDiscoveryListener = new ServiceFinder.ServiceListener() {
        @Override
        public void onFound(String name) {
            mServiceListeners.forEach(listener -> listener.onFound(name));
        }

        @Override
        public void onResolved(Service service) {
            mServiceListeners.forEach(listener -> listener.onResolved(service));
        }

        @Override
        public void onLost(String hostIP) {
            mServiceListeners.forEach(listener -> listener.onLost(hostIP));
        }
    };

    public DeskConn(Context context) {
        mExecutor = Executors.newSingleThreadExecutor();
        mFinder = new ServiceFinder(context, SERVICE_NAME, mExecutor);
        mHelpers = new Helpers(context);
        AppDatabase db = Room.databaseBuilder(context, AppDatabase.class, "services").build();
        mServiceDao = db.servicesDao();
        ensureKeyPair();
    }

    public interface ServiceListener {
        void onFound(String name);
        void onResolved(Service service);
        void onLost(String hostIP);
        void onConnect(Session session);
        void onDisconnect();
    }

    public void addServiceListener(ServiceListener listener) {
        mServiceListeners.add(listener);
    }

    public void removeServiceListener(ServiceListener listener) {
        mServiceListeners.remove(listener);
    }

    public void startDiscovery() {
        mFinder.addServiceListener(mDiscoveryListener);
        mFinder.start();
    }

    public void stopDiscovery() {
        mFinder.removeServiceListener(mDiscoveryListener);
        mFinder.stop();
    }

    private void ensureKeyPair() {
        if (!mHelpers.areKeysSet()) {
            SigningKey key = new SigningKey();
            mHelpers.saveKeys(key.getVerifyKey().toString(), key.toString());
        }
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

    private void setPaired(Service service) {
        mExecutor.submit(() -> mServiceDao.insertAll(service));
    }

    public void disconnect() {
        if (isConnected()) {
            mSession.leave();
        }
    }

    private void actuallyConnect(Service service, CryptosignAuth auth) {
        mSession = new Session();
        mSession.addOnJoinListener((session, details) -> {
            setPaired(service);
            mServiceListeners.forEach(listener -> listener.onConnect(session));
        });
        mSession.addOnLeaveListener((session, details) -> {
            mServiceListeners.forEach(ServiceListener::onDisconnect);
            System.out.println("Left...");
            System.out.println(details.message);
        });
        String crossbarURL = String.format(Locale.US, "ws://%s:%d/ws", service.getHostIP(),
                service.getPort());
        Client client = new Client(mSession, crossbarURL, service.getRealm(), auth);
        client.connect().whenComplete((exitInfo, throwable) -> {
        });
    }

    public void connect(Service service) {
        connect(service, null);
    }

    public void connect(Service service, String otp) {
        Helpers.KeyPair keyPair = mHelpers.getKeyPair();
        Map<String, Object> authextra = new HashMap<>();
        authextra.put("pubkey", keyPair.getPublicKey());
        if (otp != null) {
            authextra.put("otp", otp);
        }
        CryptosignAuth auth = new CryptosignAuth(keyPair.getPublicKey(),
                "deskconn", keyPair.getPrivateKey(), authextra);
        actuallyConnect(service, auth);
    }

    public boolean isConnected() {
        return mSession != null && mSession.isConnected();
    }
}
