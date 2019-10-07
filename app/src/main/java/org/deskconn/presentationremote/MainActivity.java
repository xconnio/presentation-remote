package org.deskconn.presentationremote;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import org.deskconn.deskconn.utils.DeskConn;
import org.deskconn.deskconn.utils.database.Service;
import org.deskconn.presentationremote.adapter.AvailableServiceAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import io.crossbar.autobahn.wamp.Session;


public class MainActivity extends AppCompatActivity implements AdapterView.OnItemClickListener {

    private static final int REQUEST_ACCESS_NETWORK_STATE = 10;
    private static final String TAG = MainActivity.class.getSimpleName();
    private DeskConn mDeskConn;
    private Map<String, Service> mServices;
    private ListView availableConnectionsList;
    private ArrayList<String> serviceArrayList;
    private AvailableServiceAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        serviceArrayList = new ArrayList<>();
        availableConnectionsList = findViewById(R.id.connections_list);
        mDeskConn = ((AppGlobals) getApplication()).getDeskConn();
        mServices = new HashMap<>();
        adapter = new AvailableServiceAdapter(serviceArrayList, MainActivity.this, mServices);
        availableConnectionsList.setAdapter(adapter);
        availableConnectionsList.setOnItemClickListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ensurePermissions();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mDeskConn.stopDiscovery();
        cleanup();
    }

    private void ensurePermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_NETWORK_STATE)
                == PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "ensurePermissions: I have all the permissions");
            findAndConnect();
        } else {
            if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_NETWORK_STATE)) {
                Log.i(TAG, "ensurePermissions: I should probably ask the user");
            } else {
                Log.i(TAG, "ensurePermissions: Don't need any permissions");
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_NETWORK_STATE},
                        REQUEST_ACCESS_NETWORK_STATE);
            }
        }
    }

    private void findAndConnect() {
        mDeskConn.addOnServiceFoundListener(this::onServiceFound);
        mDeskConn.addOnServiceLostListener(this::onServiceLost);
        mDeskConn.addOnConnectListener(new Consumer<Session>() {
            @Override
            public void accept(Session session) {

            }
        });
        mDeskConn.startDiscovery();
    }

    private void cleanup() {
        mDeskConn.removeOnServiceFoundListener(this::onServiceFound);
        mDeskConn.removeOnServiceLostListener(this::onServiceLost);
    }

    private void onServiceFound(Service service) {
        Log.i(TAG, "onServiceFound: Found service " + service.getHostName());
        Log.i(TAG, "onServiceFound: " + service.getSystemUID());
        Log.i(TAG, "onServiceFound: Found service " + serviceArrayList);
        if (!serviceArrayList.contains(service.getHostIP()) && service.getHostIP() != null) {
            mServices.put(service.getHostIP(), service);
            serviceArrayList.add(service.getHostIP());
            new android.os.Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (adapter != null) {
                        adapter.notifyDataSetChanged();
                    }
                }
            }, 1000);
            adapter.notifyDataSetChanged();
        }
        Log.i(TAG, "onServiceFound: " + mServices);
        Log.i(TAG, "onServiceFound: Found service " + serviceArrayList);
    }

    private void onServiceLost(String serviceIP) {
        Log.i(TAG, "onServiceLost: Lost " + serviceIP);
        if (mServices.containsKey(serviceIP)) {
            mServices.remove(serviceIP);
            Log.i(TAG, "onServiceLost: Removed " + serviceIP);
        }
        if (serviceArrayList.contains(serviceIP)) {
            serviceArrayList.remove(serviceIP);
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        }
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        String ip = serviceArrayList.get(i);
        Service service = mServices.get(ip);
        mDeskConn.connect(service);
        /**
         * Proceed with connection here.
         */
    }
}
