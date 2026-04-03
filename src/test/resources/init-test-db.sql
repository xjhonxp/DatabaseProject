-- Create test schema
CREATE SCHEMA IF NOT EXISTS test_schema;

-- Create tables
CREATE TABLE test_schema.users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE
);

CREATE TABLE test_schema.products (
    product_id INT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    price DECIMAL(10,2) CHECK (price >= 0),
    stock_quantity INT DEFAULT 0,
    description TEXT
);

-- Create view
CREATE VIEW test_schema.active_users AS
SELECT id, username, email, created_at
FROM test_schema.users
WHERE is_active = TRUE;

-- Create index
CREATE INDEX idx_users_email ON test_schema.users(email);
CREATE UNIQUE INDEX idx_products_name ON test_schema.products(name);

-- Create sequence
CREATE SEQUENCE test_schema.order_id_seq START WITH 1000 INCREMENT BY 5;

-- Create function
CREATE OR REPLACE FUNCTION test_schema.calculate_discounted_price(base_price DECIMAL, discount_percent INT)
RETURNS DECIMAL AS $$
BEGIN
    RETURN base_price * (1 - discount_percent::DECIMAL / 100);
END;
$$ LANGUAGE plpgsql;

-- Create stored procedure
CREATE OR REPLACE PROCEDURE test_schema.deactivate_user(user_id INT)
LANGUAGE plpgsql AS $$
BEGIN
    UPDATE test_schema.users SET is_active = FALSE WHERE id = user_id;
END;
$$;

-- Add comments
COMMENT ON TABLE test_schema.users IS 'System users table';
COMMENT ON COLUMN test_schema.users.email IS 'User email address';