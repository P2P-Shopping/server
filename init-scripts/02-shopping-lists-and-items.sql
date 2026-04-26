
CREATE TABLE IF NOT EXISTS p2p_product_catalog (
    id UUID PRIMARY KEY,
    generic_name VARCHAR(255) NOT NULL,
    specific_name VARCHAR(255) NOT NULL,
    brand VARCHAR(100),
    category VARCHAR(50),
    estimated_price DECIMAL(10, 2),
    purchase_count INTEGER NOT NULL DEFAULT 0
);

ALTER TABLE p2p_product_catalog ADD COLUMN IF NOT EXISTS category VARCHAR(50);
ALTER TABLE p2p_product_catalog ADD COLUMN IF NOT EXISTS estimated_price DECIMAL(10, 2);

CREATE TABLE IF NOT EXISTS shopping_lists (
    id UUID PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    user_id INTEGER NOT NULL,
    category VARCHAR(50) NOT NULL DEFAULT 'NORMAL',
    subcategory VARCHAR(100),
    final_store VARCHAR(255),
    CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
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
    version BIGINT DEFAULT 0,
    list_id UUID NOT NULL,
    catalog_id UUID,
    CONSTRAINT fk_list FOREIGN KEY (list_id) REFERENCES shopping_lists(id) ON DELETE CASCADE,
    CONSTRAINT fk_catalog FOREIGN KEY (catalog_id) REFERENCES p2p_product_catalog(id) ON DELETE SET NULL
    );

ALTER TABLE items ADD COLUMN IF NOT EXISTS catalog_id UUID;
ALTER TABLE items DROP CONSTRAINT IF EXISTS fk_catalog;
ALTER TABLE items ADD CONSTRAINT fk_catalog FOREIGN KEY (catalog_id) REFERENCES p2p_product_catalog(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_shopping_lists_user ON shopping_lists(user_id);
CREATE INDEX IF NOT EXISTS idx_items_list ON items(list_id);
CREATE INDEX IF NOT EXISTS idx_items_category ON items(category);
CREATE INDEX IF NOT EXISTS idx_items_catalog ON items(catalog_id);
CREATE INDEX IF NOT EXISTS idx_catalog_purchase_count ON p2p_product_catalog(purchase_count DESC);
