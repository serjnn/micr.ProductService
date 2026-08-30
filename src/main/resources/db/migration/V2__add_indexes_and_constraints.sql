-- Migration V2: Add performance indexes and unique constraint to prevent duplicate subscriptions

-- Index for product category filtering
CREATE INDEX IF NOT EXISTS idx_product_category ON product (category);

-- Indexes for subscriber lookups
CREATE INDEX IF NOT EXISTS idx_subscribers_product_id ON subscribers (product_id);
CREATE INDEX IF NOT EXISTS idx_subscribers_client_id ON subscribers (client_id);

-- Enforce unique subscription per product and client
ALTER TABLE subscribers ADD CONSTRAINT uq_subscribers_product_client UNIQUE (product_id, client_id);
