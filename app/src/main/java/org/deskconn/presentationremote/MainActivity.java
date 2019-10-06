package org.deskconn.presentationremote;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.util.Consumer;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

import org.deskconn.deskconn.utils.DeskConn;
import org.deskconn.deskconn.utils.database.Service;

import java.util.HashMap;
import java.util.Map;


public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ACCESS_NETWORK_STATE = 10;
    private static final String TAG = MainActivity.class.getName();

    private DeskConn mDeskConn;
    private Map<String, Service> mServices;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mDeskConn = ((AppGlobals) getApplication()).getDeskConn();
        mServices = new HashMap<>();
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
        mDeskConn.startDiscovery();
    }

    private void cleanup() {
        mDeskConn.removeOnServiceFoundListener(this::onServiceFound);
        mDeskConn.removeOnServiceLostListener(this::onServiceLost);
    }

    private void onServiceFound(Service service) {
        Log.i(TAG, "onServiceFound: Found service " + service.getHostName());
        Log.i(TAG, "onServiceFound: " + service.getSystemUID());
        mServices.put(service.getHostIP(), service);
    }

    private void onServiceLost(String serviceIP) {
        Log.i(TAG, "onServiceLost: Lost " + serviceIP);
        if (mServices.containsKey(serviceIP)) {
            mServices.remove(serviceIP);
            Log.i(TAG, "onServiceLost: Removed " + serviceIP);
        }
    }
}
