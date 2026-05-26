-- Add address column to store_geofences
ALTER TABLE store_geofences
    ADD COLUMN IF NOT EXISTS address VARCHAR(500);
