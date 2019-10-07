package org.deskconn.presentationremote;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;

import org.deskconn.deskconn.utils.DeskConn;
import org.deskconn.deskconn.utils.database.Service;
import org.deskconn.presentationremote.adapter.AvailableServiceAdapter;

import java.util.ArrayList;
import java.util.List;


public class MainActivity extends AppCompatActivity implements AdapterView.OnItemClickListener {

    private static final String TAG = MainActivity.class.getSimpleName();

    private DeskConn mDeskConn;
    private List<Service> mAvailableServices;
    private List<Service> mPairedServices;
    private AvailableServiceAdapter mAdapter;

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

    private void findAndConnect() {
        mAdapter.notifyDataSetChanged();
        mDeskConn.addOnServiceFoundListener(onServiceFound);
        mDeskConn.addOnServiceLostListener(onServiceLost);
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
                // FIXME: Show a dialog to put the pairing code ==> runOnUiThread()
                // FIXME: Once we have the code call mDeskConn.pair()
            }
        });
    }
}
