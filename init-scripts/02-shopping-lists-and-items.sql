
CREATE TABLE IF NOT EXISTS shopping_lists (
    id UUID PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    user_id INTEGER NOT NULL,
    CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
    );

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
    CONSTRAINT fk_list FOREIGN KEY (list_id) REFERENCES shopping_lists(id) ON DELETE CASCADE
    );
CREATE INDEX IF NOT EXISTS idx_shopping_lists_user ON shopping_lists(user_id);
CREATE INDEX IF NOT EXISTS idx_items_list ON items(list_id);
CREATE INDEX IF NOT EXISTS idx_items_category ON items(category);
