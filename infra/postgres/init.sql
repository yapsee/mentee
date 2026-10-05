-- Runs once, on the first start of an empty postgres volume.
-- One database per service: services never share tables.
CREATE DATABASE product_db;
CREATE DATABASE order_db;
CREATE DATABASE payment_db;
