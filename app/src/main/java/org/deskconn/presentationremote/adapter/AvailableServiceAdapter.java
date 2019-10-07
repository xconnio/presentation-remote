package org.deskconn.presentationremote.adapter;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

import org.deskconn.deskconn.utils.database.Service;
import org.deskconn.presentationremote.R;

import java.util.List;

public class AvailableServiceAdapter extends BaseAdapter {

    private ViewHolder mViewHolder;
    private Activity mActivity;
    private List<Service> mAvailableServices;
    private List<Service> mPairedServices;

    public AvailableServiceAdapter(Activity activity, List<Service> availableServices,
                                   List<Service> pairedServices) {
        mActivity = activity;
        mAvailableServices = availableServices;
        mPairedServices = pairedServices;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = mActivity.getLayoutInflater().inflate(R.layout.service_raw, parent,
                    false);
            mViewHolder = new ViewHolder();
            mViewHolder.name = convertView.findViewById(R.id.service_name);
            convertView.setTag(mViewHolder);
        } else {
            mViewHolder = (ViewHolder) convertView.getTag();
        }
        Service service = (Service) getItem(position);
        mViewHolder.name.setText(service.getHostName());
        return convertView;
    }

    @Override
    public int getCount() {
        return mAvailableServices.size();
    }

    @Override
    public Object getItem(int i) {
        return mAvailableServices.get(i);
    }

    @Override
    public long getItemId(int i) {
        return 0;
    }

    private class ViewHolder {
        AppCompatTextView name;
    }
}
