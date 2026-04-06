package com.p2ps.sync.repository;

import com.p2ps.sync.model.RoomItemState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoomItemStateRepository extends JpaRepository<RoomItemState, String> {

    Optional<RoomItemState> findByListIdAndItemId(String listId, String itemId);

    void deleteByListIdAndItemId(String listId, String itemId);
}