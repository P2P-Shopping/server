-- Issue #154: Closed-Loop TSP — Fix Start / Finish
-- Adds entry_point (user enters) and exit_point (checkout counters) to every store.
-- Both columns are nullable so existing stores without coordinates still work.

ALTER TABLE store_geofences
    ADD COLUMN IF NOT EXISTS entry_point GEOMETRY(Point, 4326),
    ADD COLUMN IF NOT EXISTS exit_point  GEOMETRY(Point, 4326);

-- Spatial index on exit_point — used by RoutingService.fetchExitPoint()
CREATE INDEX IF NOT EXISTS idx_store_geofences_exit_point
    ON store_geofences USING GIST (exit_point);
