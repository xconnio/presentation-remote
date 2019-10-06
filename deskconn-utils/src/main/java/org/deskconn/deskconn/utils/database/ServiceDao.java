package org.deskconn.deskconn.utils.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;


@Dao
public interface ServiceDao {
    @Query("SELECT * FROM Service")
    List<Service> getAll();

    @Query("SELECT * FROM Service WHERE system_uid LIKE :systemUID LIMIT 1")
    Service findBySystemUID(String systemUID);

    @Insert
    void insertAll(Service... services);

    @Query("DELETE FROM Service WHERE system_uid LIKE :systemUID")
    void delete(String systemUID);
}
