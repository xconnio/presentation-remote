package org.deskconn.presentationremote.adapter;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

import org.deskconn.deskconn.utils.database.Service;
import org.deskconn.presentationremote.R;

import java.util.ArrayList;
import java.util.Map;

public class AvailableServiceAdapter extends BaseAdapter {

    private ViewHolder viewHolder;
    private Activity mActivity;
    private ArrayList<String> mServiceList;
    private Map<String, Service> serviceHashMap;

    public AvailableServiceAdapter(ArrayList<String> arrayList, Activity activity,
                                   Map<String, Service> servicesData) {
        mActivity = activity;
        mServiceList = arrayList;
        serviceHashMap = servicesData;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = mActivity.getLayoutInflater().inflate(R.layout.service_raw, parent, false);
            viewHolder = new ViewHolder();
            viewHolder.name = convertView.findViewById(R.id.service_name);
            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }
        String ip = mServiceList.get(position);
        viewHolder.name.setText(serviceHashMap.get(ip).getHostName());
        return convertView;
    }

    @Override
    public int getCount() {
        return mServiceList.size();
    }

    @Override
    public Object getItem(int i) {
        return mServiceList.get(i);
    }

    @Override
    public long getItemId(int i) {
        return 0;
    }

    private class ViewHolder {
        AppCompatTextView name;
    }
}
