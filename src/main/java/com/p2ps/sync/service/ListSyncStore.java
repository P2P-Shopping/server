package com.p2ps.sync.service;

import com.p2ps.dto.ListUpdatePayload;

public interface ListSyncStore {

    ListUpdatePayload apply(String listId, ListUpdatePayload payload);
}