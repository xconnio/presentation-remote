package org.deskconn.presentationremote;

import android.app.Application;

import org.deskconn.deskconn.utils.DeskConn;

public class AppGlobals extends Application {

    private DeskConn mDeskConn;

    @Override
    public void onCreate() {
        super.onCreate();
        mDeskConn = new DeskConn(getApplicationContext());
    }

    public DeskConn getDeskConn() {
        return mDeskConn;
    }
}
