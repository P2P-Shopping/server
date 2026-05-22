package com.p2ps.store.repository;

import com.p2ps.store.model.StoreGeofence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface StoreGeofenceRepository extends JpaRepository<StoreGeofence, UUID> {
}
