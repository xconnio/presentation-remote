package org.deskconn.deskconn.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;

import androidx.annotation.NonNull;

import org.deskconn.deskconn.utils.database.Service;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;

public class ServiceFinder {

    private final Context mContext;
    private final String mServiceName;
    private final List<ServiceListener> mServiceListeners = new ArrayList<>();

    private JmDNS mJmDNS;
    private String mWiFiIP;
    private ExecutorService mExecutor;

    public ServiceFinder(Context context, String serviceName) {
        this(context, serviceName, Executors.newSingleThreadExecutor());
    }

    public ServiceFinder(Context context, String serviceName, ExecutorService executor) {
        mContext = context;
        mServiceName = serviceName;
        mExecutor = executor;
    }

    public interface ServiceListener {
        void onFound(String name);
        void onResolved(Service service);
        void onLost(String hostIP);
    }

    public void addServiceListener(ServiceListener listener) {
        mServiceListeners.add(listener);
    }

    public void removeServiceListener(ServiceListener listener) {
        mServiceListeners.remove(listener);
    }

    public void start() {
        trackWiFiState();
    }

    private CompletableFuture<Boolean> actuallyStartDiscovery() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        mExecutor.submit(() -> {
            try {
                mJmDNS = JmDNS.create(InetAddress.getByName(mWiFiIP), "android");
                mJmDNS.addServiceListener(mServiceName, new javax.jmdns.ServiceListener() {
                    @Override
                    public void serviceAdded(ServiceEvent event) {
                        mServiceListeners.forEach(listener -> listener.onFound(
                                event.getInfo().getApplication()));
                    }

                    @Override
                    public void serviceRemoved(ServiceEvent event) {
                        try {
                            String host = event.getDNS().getInetAddress().getHostAddress();
                            mServiceListeners.forEach(listener -> listener.onLost(host));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void serviceResolved(ServiceEvent event) {
                        ServiceInfo info = event.getInfo();
                        String host = info.getInet4Addresses()[0].getHostAddress();
                        Service service = new Service(event.getName(), host,
                                info.getPropertyString("realm"), info.getPort(),
                                info.getPropertyString("uid"));
                        mServiceListeners.forEach(listener -> listener.onResolved(service));
                    }
                });
                future.complete(true);
            } catch (IOException e) {
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    public void stop() {
        if (mJmDNS != null) {
            mJmDNS.unregisterAllServices();
            mJmDNS = null;
        }
    }

    private void trackWiFiState() {
        ConnectivityManager cm = mContext.getSystemService(ConnectivityManager.class);
        NetworkRequest.Builder builder = new NetworkRequest.Builder();
        builder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
        cm.requestNetwork(builder.build(), new ConnectivityManager.NetworkCallback() {
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
                stop();
            }
        });
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
}
