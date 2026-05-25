package com.p2ps.store.repository;

import com.p2ps.store.model.StoreGeofence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface StoreGeofenceRepository extends JpaRepository<StoreGeofence, UUID> {

    @Modifying
    @Query(value = "INSERT INTO store_geofences (store_id, name, address, boundary_polygon) " +
            "VALUES (:id, :name, :address, ST_Buffer(ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326), 0.0005))",
            nativeQuery = true)
    void insertPlaceholderStore(@Param("id") UUID id,
                                @Param("name") String name,
                                @Param("address") String address,
                                @Param("latitude") Double latitude,
                                @Param("longitude") Double longitude);
}
