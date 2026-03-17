CREATE TABLE product (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price NUMERIC(19, 4) NOT NULL,
    category VARCHAR(255)
);

CREATE TABLE subscribers (
    id SERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    client_id BIGINT NOT NULL,
    CONSTRAINT fk_product FOREIGN KEY (product_id) REFERENCES product (id) ON DELETE CASCADE
);
