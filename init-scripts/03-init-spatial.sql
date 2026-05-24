-- Activam extensia PostGIS (adauga suport pentru date geografice)
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pgcrypto;


-- ============================================================
-- STORE_GEOFENCES (conturul fizic al magazinului)
-- ============================================================
CREATE TABLE store_geofences (
    store_id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_store_id   VARCHAR(255),
    name                VARCHAR(255) NOT NULL,
    boundary_polygon    GEOMETRY(Polygon, 4326) NOT NULL,
    floor_level         INT DEFAULT 0,
    created_at          TIMESTAMP DEFAULT NOW()
);

-- Index spatial pe conturul magazinului
-- (folosit pentru "ping-ul asta e in interiorul magazinului?")
CREATE INDEX idx_store_geofences_boundary
    ON store_geofences USING GIST (boundary_polygon);

CREATE UNIQUE INDEX idx_store_geofences_external_store_id
    ON store_geofences (external_store_id)
    WHERE external_store_id IS NOT NULL;

ALTER TABLE store_prices
    DROP CONSTRAINT IF EXISTS fk_store_prices_store;
ALTER TABLE store_prices
    ADD CONSTRAINT fk_store_prices_store
    FOREIGN KEY (store_id) REFERENCES store_geofences(store_id) ON DELETE CASCADE;

ALTER TABLE shopping_lists
    DROP COLUMN IF EXISTS final_store;
ALTER TABLE shopping_lists
    ADD COLUMN IF NOT EXISTS final_store_id UUID;
ALTER TABLE shopping_lists
    DROP CONSTRAINT IF EXISTS fk_shopping_lists_final_store;
ALTER TABLE shopping_lists
    ADD CONSTRAINT fk_shopping_lists_final_store
    FOREIGN KEY (final_store_id) REFERENCES store_geofences(store_id) ON DELETE SET NULL;

CREATE TABLE IF NOT EXISTS store_candidate_submissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    submitted_name VARCHAR(255) NOT NULL,
    normalized_name VARCHAR(255) NOT NULL,
    submitted_address VARCHAR(255),
    submission_notes VARCHAR(500),
    submitted_by_user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source_list_id UUID NOT NULL REFERENCES shopping_lists(id) ON DELETE CASCADE,
    matched_store_id UUID REFERENCES store_geofences(store_id) ON DELETE SET NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_store_candidate_submissions_normalized_name
    ON store_candidate_submissions (normalized_name);

CREATE TABLE IF NOT EXISTS shopping_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shopping_list_id UUID NOT NULL REFERENCES shopping_lists(id) ON DELETE CASCADE,
    started_by_user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    store_id UUID REFERENCES store_geofences(store_id) ON DELETE SET NULL,
    store_candidate_submission_id UUID REFERENCES store_candidate_submissions(id) ON DELETE SET NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    started_at TIMESTAMP NOT NULL DEFAULT NOW(),
    finished_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_shopping_sessions_list_status
    ON shopping_sessions (shopping_list_id, status);

-- ============================================================
-- RAW_USER_PINGS (datele brute trimise de utilizatori)
-- ============================================================
CREATE TABLE raw_user_pings (
    ping_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_telemetry_id VARCHAR(64),
    external_store_id VARCHAR(255),
    external_item_id VARCHAR(255),
    store_id        UUID NOT NULL REFERENCES store_geofences(store_id),
    item_id         UUID NOT NULL REFERENCES items(id),
    location_point  GEOMETRY(Point, 4326) NOT NULL,  -- coordonatele GPS ale ping-ului
    accuracy_m      FLOAT,                            -- cat de precis e GPS-ul (metri)
    floor_level     INT DEFAULT 0,
    loc_provider    VARCHAR(50),                      -- ex: "GPS", "WiFi", "BLE"
    marked_at       TIMESTAMP DEFAULT NOW()
);

-- Index spatial pe locatia ping-ului
-- (folosit pentru "ce ping-uri sunt in raza de X metri?")
CREATE INDEX idx_raw_user_pings_location
    ON raw_user_pings USING GIST (location_point);

-- Index normal pe store_id (query-uri frecvente: "toate ping-urile din magazinul X")
CREATE INDEX idx_raw_user_pings_store_id
    ON raw_user_pings (store_id);

CREATE UNIQUE INDEX idx_raw_user_pings_source_telemetry_id
    ON raw_user_pings (source_telemetry_id);

CREATE INDEX idx_raw_user_pings_external_ids
    ON raw_user_pings (external_store_id, external_item_id);

CREATE TABLE telemetry_import_state (
    job_name            VARCHAR(100) PRIMARY KEY,
    last_telemetry_id   VARCHAR(64),
    updated_at          TIMESTAMP DEFAULT NOW()
);

-- ============================================================
-- STORE_INVENTORY_MAP (harta agregata - unde e fiecare produs)
-- ============================================================
CREATE TABLE store_inventory_map (
    map_id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    store_id                UUID NOT NULL REFERENCES store_geofences(store_id),
    item_id                 UUID NOT NULL REFERENCES items(id),
    estimated_loc_point     GEOMETRY(Point, 4326) NOT NULL,  -- locatia calculata din ping-uri
    confidence_score        FLOAT CHECK (confidence_score BETWEEN 0 AND 1),
    ping_count              INT DEFAULT 0,                   -- cate ping-uri au contribuit
    last_updated            TIMESTAMP DEFAULT NOW(),

    -- Un item poate aparea o singura data per magazin
    UNIQUE (store_id, item_id)
);

-- Index spatial pe locatia estimata
-- (folosit pentru "ce produse sunt langa mine?")
CREATE INDEX idx_store_inventory_map_location
    ON store_inventory_map USING GIST (estimated_loc_point);

-- Index pe store_id + confidence_score
-- (query-uri de tipul: "produsele din magazinul X cu confidence > 0.7")
CREATE INDEX idx_store_inventory_map_store_confidence
    ON store_inventory_map (store_id, confidence_score);
