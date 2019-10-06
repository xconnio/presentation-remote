package org.deskconn.deskconn.utils.database;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;


@Entity
public class Service {
    @PrimaryKey
    public int uid;

    @ColumnInfo(name = "host_name")
    public String hostName;

    @ColumnInfo(name = "host_ip")
    public String hostIP;

    @ColumnInfo(name = "port")
    public int port;

    @ColumnInfo(name = "realm")
    public String realm;

    @ColumnInfo(name = "system_uid")
    public String systemUID;

    public Service(String hostName, String hostIP, String realm, int port, String systemUID) {
        this.hostName = hostName;
        this.hostIP = hostIP;
        this.realm = realm;
        this.port = port;
        this.systemUID = systemUID;
    }

    public String getHostName() {
        return hostName;
    }

    public String getHostIP() {
        return hostIP;
    }

    public String getRealm() {
        return realm;
    }

    public int getPort() {
        return port;
    }

    public String getSystemUID() {
        return systemUID;
    }
}
