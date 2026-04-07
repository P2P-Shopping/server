package com.p2ps.sync.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "room_item_states", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"list_id", "item_id"})
})
public class RoomItemState {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "list_id", nullable = false, length = 128)
    private String listId;

    @Column(name = "item_id", nullable = false, length = 128)
    private String itemId;

    @Column(name = "content", length = 512)
    private String content;

    @Column(name = "checked", nullable = false)
    private boolean checked;

    @Column(name = "client_timestamp")
    private Long clientTimestamp;

    @Column(name = "last_updated", nullable = false)
    private Instant lastUpdated;

    protected RoomItemState() {
    }

    public RoomItemState(String listId, String itemId) {
        this.listId = listId;
        this.itemId = itemId;
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (lastUpdated == null) {
            lastUpdated = Instant.now();
        }
    }

    @PreUpdate
    void onUpdate() {
        lastUpdated = Instant.now();
    }
}
