
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS unaccent;

CREATE OR REPLACE FUNCTION public.f_unaccent(input_text TEXT)
RETURNS TEXT
LANGUAGE sql
IMMUTABLE
PARALLEL SAFE
STRICT
AS $$
    SELECT public.unaccent('public.unaccent', input_text)
$$;

CREATE TABLE IF NOT EXISTS p2p_product_catalog (
    id UUID PRIMARY KEY,
    generic_name VARCHAR(255) NOT NULL,
    specific_name VARCHAR(255) NOT NULL,
    brand VARCHAR(100),
    category VARCHAR(50),
    estimated_price DECIMAL(10, 2),
    purchase_count INTEGER NOT NULL DEFAULT 0
    );

CREATE TABLE IF NOT EXISTS user_product_history (
                                                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    catalog_id UUID REFERENCES p2p_product_catalog(id) ON DELETE SET NULL,
    custom_name VARCHAR(255) NOT NULL,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    last_added_timestamp BIGINT
    );

ALTER TABLE user_product_history ADD COLUMN IF NOT EXISTS brand VARCHAR(100);
ALTER TABLE user_product_history ADD COLUMN IF NOT EXISTS category VARCHAR(50);
ALTER TABLE user_product_history ADD COLUMN IF NOT EXISTS price DECIMAL(10, 2);
ALTER TABLE user_product_history ADD COLUMN IF NOT EXISTS store_name VARCHAR(255);
ALTER TABLE user_product_history DROP CONSTRAINT IF EXISTS chk_user_product_history_price_non_negative;
ALTER TABLE user_product_history ADD CONSTRAINT chk_user_product_history_price_non_negative CHECK (price IS NULL OR price >= 0);

CREATE INDEX IF NOT EXISTS idx_user_history_user_id ON user_product_history(user_id);
-- Adaugam index de trigrame pe numele custom pentru a pastra cautarea rapida
CREATE INDEX IF NOT EXISTS trgm_idx_user_history_custom_name
    ON user_product_history USING gin (lower(custom_name) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS trgm_idx_user_history_custom_name_unaccent
    ON user_product_history USING gin (f_unaccent(lower(custom_name)) gin_trgm_ops);

ALTER TABLE p2p_product_catalog ADD COLUMN IF NOT EXISTS category VARCHAR(50);
ALTER TABLE p2p_product_catalog ADD COLUMN IF NOT EXISTS estimated_price DECIMAL(10, 2);
CREATE UNIQUE INDEX IF NOT EXISTS unq_product_catalog_name_brand
    ON p2p_product_catalog(specific_name, COALESCE(brand, ''));

CREATE TABLE IF NOT EXISTS store_prices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    catalog_id UUID NOT NULL REFERENCES p2p_product_catalog(id) ON DELETE CASCADE,
    store_name VARCHAR(255) NOT NULL,
    price DECIMAL(10, 2) NOT NULL CHECK (price >= 0),
    last_updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS unq_store_prices_catalog_store
    ON store_prices(catalog_id, lower(store_name));
CREATE INDEX IF NOT EXISTS idx_store_prices_last_updated_at
    ON store_prices(last_updated_at);

CREATE TABLE IF NOT EXISTS shopping_lists (
    id UUID PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    user_id INTEGER NOT NULL,
    category VARCHAR(50) NOT NULL DEFAULT 'NORMAL',
    subcategory VARCHAR(100),
    final_store VARCHAR(255),
    CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
    );

CREATE TABLE IF NOT EXISTS shopping_list_collaborators (
    shopping_list_id UUID NOT NULL,
    user_id INTEGER NOT NULL,
    PRIMARY KEY (shopping_list_id, user_id),
    CONSTRAINT fk_collaborators_list FOREIGN KEY (shopping_list_id) REFERENCES shopping_lists(id) ON DELETE CASCADE,
    CONSTRAINT fk_collaborators_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);


ALTER TABLE shopping_lists ADD COLUMN IF NOT EXISTS category VARCHAR(50) NOT NULL DEFAULT 'NORMAL';
ALTER TABLE shopping_lists ADD COLUMN IF NOT EXISTS subcategory VARCHAR(100);
ALTER TABLE shopping_lists ADD COLUMN IF NOT EXISTS final_store VARCHAR(255);

CREATE TABLE IF NOT EXISTS items (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    is_checked BOOLEAN NOT NULL DEFAULT FALSE,
    brand VARCHAR(100),
    quantity VARCHAR(50),
    price DECIMAL(10, 2) DEFAULT 0 CHECK (price >= 0),
    category VARCHAR(50),
    is_recurrent BOOLEAN DEFAULT FALSE,
    last_updated_timestamp BIGINT,
    created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000),
    version BIGINT DEFAULT 0,
    list_id UUID NOT NULL,
    catalog_id UUID,
    external_item_id VARCHAR(255),
    CONSTRAINT fk_list FOREIGN KEY (list_id) REFERENCES shopping_lists(id) ON DELETE CASCADE,
    CONSTRAINT fk_catalog FOREIGN KEY (catalog_id) REFERENCES p2p_product_catalog(id) ON DELETE SET NULL
    );

ALTER TABLE items ADD COLUMN IF NOT EXISTS catalog_id UUID;
ALTER TABLE items ADD COLUMN IF NOT EXISTS external_item_id VARCHAR(255);
ALTER TABLE items DROP CONSTRAINT IF EXISTS fk_catalog;
ALTER TABLE items ADD CONSTRAINT fk_catalog FOREIGN KEY (catalog_id) REFERENCES p2p_product_catalog(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_shopping_lists_user ON shopping_lists(user_id);
CREATE INDEX IF NOT EXISTS idx_items_list ON items(list_id);
CREATE INDEX IF NOT EXISTS idx_items_category ON items(category);
CREATE INDEX IF NOT EXISTS idx_items_catalog ON items(catalog_id);
CREATE INDEX IF NOT EXISTS idx_items_external_item_id
    ON items (external_item_id)
    WHERE external_item_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_catalog_purchase_count ON p2p_product_catalog(purchase_count DESC);
CREATE INDEX IF NOT EXISTS idx_collaborators_user ON shopping_list_collaborators(user_id);
CREATE INDEX IF NOT EXISTS idx_items_name_trgm ON items USING gin (LOWER(name) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_catalog_generic_name_trgm ON p2p_product_catalog USING gin (LOWER(generic_name) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_catalog_specific_name_trgm ON p2p_product_catalog USING gin (LOWER(specific_name) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_catalog_brand_trgm ON p2p_product_catalog USING gin (LOWER(COALESCE(brand, '')) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS trgm_idx_catalog_generic_name_unaccent
    ON p2p_product_catalog USING gin (f_unaccent(lower(generic_name)) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS trgm_idx_catalog_specific_name_unaccent
    ON p2p_product_catalog USING gin (f_unaccent(lower(specific_name)) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS trgm_idx_catalog_brand_unaccent
    ON p2p_product_catalog USING gin (f_unaccent(lower(COALESCE(brand, ''))) gin_trgm_ops);

