package com.p2ps.sync.service;

import com.p2ps.dto.ListUpdatePayload;

public interface ListSyncStore {

    /**
     * Applies a list mutation for the given list and payload.
     * Implementations may normalize or mutate the supplied payload in place and
     * return the same instance, or return a different payload object with the
     * applied result. Invalid input handling is implementation-defined, but
     * callers should expect either a returned payload or an exception for
     * malformed state.
     */
    ListUpdatePayload apply(String listId, ListUpdatePayload payload);
}
