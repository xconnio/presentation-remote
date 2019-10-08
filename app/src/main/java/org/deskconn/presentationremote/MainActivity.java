package org.deskconn.presentationremote;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.material.snackbar.Snackbar;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.deskconn.deskconn.utils.DeskConn;
import org.deskconn.deskconn.utils.database.Service;
import org.deskconn.presentationremote.adapter.AvailableServiceAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class MainActivity extends AppCompatActivity implements AdapterView.OnItemClickListener {

    private static final String TAG = MainActivity.class.getSimpleName();

    private DeskConn mDeskConn;
    private List<Service> mAvailableServices;
    private List<Service> mPairedServices;
    private AvailableServiceAdapter mAdapter;
    private IntentIntegrator integrator;
    private static final int MY_CAMERA_REQUEST_CODE = 100;
    private Service selectedService = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mDeskConn = ((AppGlobals) getApplication()).getDeskConn();
        mAvailableServices = new ArrayList<>();
        mPairedServices = new ArrayList<>();
        mDeskConn.getPairedServices().thenAccept(pairedServices -> {
            mPairedServices = pairedServices;
        });
        mAdapter = new AvailableServiceAdapter(this, mAvailableServices, mPairedServices);
        ListView servicesListView = findViewById(R.id.connections_list);
        servicesListView.setAdapter(mAdapter);
        servicesListView.setOnItemClickListener(this);
        integrator = new IntentIntegrator(this);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES);
        integrator.setPrompt("Scan a qrcode");
        integrator.setOrientationLocked(true);
        integrator.setCameraId(0);  // Use a specific camera of the device
        integrator.setBeepEnabled(false);
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
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_CAMERA_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Snackbar.make(findViewById(android.R.id.content), "Permission granted",
                        Snackbar.LENGTH_SHORT).show();
                new android.os.Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        integrator.initiateScan(); // `this` is the current Activity
                    }
                }, 2000);
            } else {
                Snackbar.make(findViewById(android.R.id.content), "Permission denied, Permission required!",
                        Snackbar.LENGTH_SHORT).show();
            }
        }
    }

    private void findAndConnect() {
        mAdapter.notifyDataSetChanged();
        mDeskConn.addOnServiceFoundListener(onServiceFound);
        mDeskConn.addOnServiceLostListener(onServiceLost);
        mDeskConn.addOnConnectListener(session -> {
            Log.i(TAG, "findAndConnect: ");
            if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA},
                        MY_CAMERA_REQUEST_CODE);
            } else {
                integrator.initiateScan(); // `this` is the current Activity
            }
        });
        mDeskConn.startDiscovery();
    }

    private void cleanup() {
        mDeskConn.removeOnServiceFoundListener(onServiceFound);
        mDeskConn.removeOnServiceLostListener(onServiceLost);
        mDeskConn.stopDiscovery();
        mDeskConn.disconnect();
        mAvailableServices.clear();
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

    private DeskConn.OnServiceFoundListener onServiceFound = service -> {
        Log.i(TAG, "onServiceFound: Service found " + service.getHostName());
        mAvailableServices.add(service);
        runOnUiThread(() -> mAdapter.notifyDataSetChanged());
    };

    private DeskConn.OnServiceLostListener onServiceLost = serviceIP -> {
        Log.i(TAG, "onServiceLost: Lost " + serviceIP);
        if (removeServiceByIP(serviceIP)) {
            mAdapter.notifyDataSetChanged();
            Log.i(TAG, "onServiceLost: Removed " + serviceIP);
        }
    };

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        Service service = mAvailableServices.get(i);
        mDeskConn.isPaired(service).thenAccept(paired -> {
            if (paired) {
                mDeskConn.connect(service);
            } else {
                mDeskConn.connect(service);
                // FIXME: Show a dialog to put the pairing code ==> runOnUiThread()
                // FIXME: Once we have the code call mDeskConn.pair()
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
                if (selectedService != null) {
                    mDeskConn.pair(selectedService, result.getContents());
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }
}
