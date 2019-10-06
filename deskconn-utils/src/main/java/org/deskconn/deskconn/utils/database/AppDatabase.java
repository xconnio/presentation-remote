package org.deskconn.deskconn.utils.database;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import org.deskconn.deskconn.utils.database.Service;
import org.deskconn.deskconn.utils.database.ServiceDao;


@Database(entities = {Service.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
    public abstract ServiceDao servicesDao();
}
