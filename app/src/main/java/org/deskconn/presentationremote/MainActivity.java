package org.deskconn.presentationremote;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.deskconn.deskconn.utils.DeskConn;
import org.deskconn.deskconn.utils.database.Service;
import org.deskconn.presentationremote.adapter.AvailableServiceAdapter;

import java.util.ArrayList;
import java.util.List;

import io.crossbar.autobahn.wamp.Session;


public class MainActivity extends AppCompatActivity implements AdapterView.OnItemClickListener,
        DeskConn.ServiceListener {

    private static final String TAG = MainActivity.class.getSimpleName();

    private DeskConn mDeskConn;
    private List<Service> mAvailableServices = new ArrayList<>();
    private List<Service> mPairedServices = new ArrayList<>();
    private AvailableServiceAdapter mAdapter;
    private static final int CAMERA_REQUEST_CODE = 100;
    private Service mSelectedService = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mDeskConn = ((AppGlobals) getApplication()).getDeskConn();
        mAdapter = new AvailableServiceAdapter(this, mAvailableServices, mPairedServices);
        ListView servicesListView = findViewById(R.id.connections_list);
        servicesListView.setAdapter(mAdapter);
        servicesListView.setOnItemClickListener(this);
        mDeskConn.getPairedServices().thenAccept(pairedServices -> mPairedServices = pairedServices);
    }

    @Override
    protected void onResume() {
        super.onResume();
        findAndConnect();
    }

    @Override
    protected void onPause() {
        super.onPause();
        cleanup();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Snackbar.make(findViewById(android.R.id.content), "Permission granted",
                        Snackbar.LENGTH_SHORT).show();
                scanQRCode(MainActivity.this);
            } else {
                Snackbar.make(findViewById(android.R.id.content),
                        "Permission denied, Permission required!", Snackbar.LENGTH_SHORT).show();
            }
        }
    }

    private void findAndConnect() {
        mAdapter.notifyDataSetChanged();
        mDeskConn.addServiceListener(this);
        mDeskConn.startDiscovery();
    }

    private void cleanup() {
        mDeskConn.removeServiceListener(this);
        mDeskConn.stopDiscovery();
        mDeskConn.disconnect();
        mAvailableServices.clear();
        mAdapter.notifyDataSetChanged();
    }

    private boolean removeServiceByIP(String serviceIP) {
        for (int i = 0; i < mAvailableServices.size(); i++) {
            Service service = mAvailableServices.get(i);
            if (serviceIP.equals(service.getHostIP())) {
                mAvailableServices.remove(i);
                return true;
            }
        }
        return false;
    }

    private void scanQRCode(Activity activity) {
        IntentIntegrator integrator = new IntentIntegrator(activity);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES);
        integrator.setPrompt("Scan deskconn pairing code");
        integrator.setOrientationLocked(true);
        integrator.setCameraId(0);
        integrator.setBeepEnabled(false);
        integrator.initiateScan();
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        Service service = mAvailableServices.get(i);
        mDeskConn.isPaired(service).thenAccept(paired -> {
            if (paired) {
                mDeskConn.connect(service);
            } else {
                mSelectedService = service;
                scanQRCode(MainActivity.this);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            if (result.getContents() == null) {
                Toast.makeText(this, "Cancelled", Toast.LENGTH_LONG).show();
            } else {
                if (mSelectedService != null) {
                    mDeskConn.connect(mSelectedService, result.getContents());
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onFound(String service) {

    }

    @Override
    public void onResolved(Service service) {
        mAvailableServices.add(service);
        runOnUiThread(() -> mAdapter.notifyDataSetChanged());
    }

    @Override
    public void onLost(String hostIP) {
        if (removeServiceByIP(hostIP)) {
            mAdapter.notifyDataSetChanged();
            Log.i(TAG, "onServiceLost: Removed " + hostIP);
        }
    }

    @Override
    public void onConnect(Session session) {
        System.out.println("Connected...");
    }

    @Override
    public void onDisconnect() {

    }
}
