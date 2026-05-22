-- Fix for missing unique constraint on store_inventory_map
-- This ensures that the ON CONFLICT clause in LocationProcessorWorker works correctly

DO $$ 
BEGIN
    IF NOT EXISTS (
        SELECT 1 
        FROM pg_constraint 
        WHERE conname = 'store_inventory_map_store_id_item_id_key'
    ) THEN
        ALTER TABLE store_inventory_map 
        ADD CONSTRAINT store_inventory_map_store_id_item_id_key UNIQUE (store_id, item_id);
    END IF;
END $$;
