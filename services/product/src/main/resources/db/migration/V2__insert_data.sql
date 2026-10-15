-- Seed data so the catalogue is not empty on first start.

INSERT INTO category (name, description) VALUES
    ('Keyboards',  'Mechanical and membrane keyboards'),
    ('Monitors',   'Computer screens and displays'),
    ('Mice',       'Wired and wireless mice'),
    ('Headphones', 'Headsets and earphones');

INSERT INTO product (name, description, available_quantity, price, category_id) VALUES
    ('Keychron K2',          'Wireless mechanical keyboard, 75% layout, brown switches', 40, 89.99,
        (SELECT id FROM category WHERE name = 'Keyboards')),
    ('Logitech MX Keys',     'Low-profile wireless keyboard with backlight',             25, 109.99,
        (SELECT id FROM category WHERE name = 'Keyboards')),
    ('Razer BlackWidow V4',  'Full-size mechanical gaming keyboard, RGB',                15, 169.99,
        (SELECT id FROM category WHERE name = 'Keyboards')),
    ('Dell U2723QE',         '27" 4K IPS monitor, USB-C hub',                            10, 579.00,
        (SELECT id FROM category WHERE name = 'Monitors')),
    ('LG 27GP850',           '27" QHD 165 Hz gaming monitor',                            12, 349.00,
        (SELECT id FROM category WHERE name = 'Monitors')),
    ('Samsung Odyssey G9',   '49" curved super ultra-wide monitor',                       3, 1299.00,
        (SELECT id FROM category WHERE name = 'Monitors')),
    ('Logitech MX Master 3S','Ergonomic wireless mouse, quiet clicks',                   50, 99.99,
        (SELECT id FROM category WHERE name = 'Mice')),
    ('Razer DeathAdder V3',  'Ultra-light wired gaming mouse',                           30, 69.99,
        (SELECT id FROM category WHERE name = 'Mice')),
    ('Apple Magic Mouse',    'Multi-touch wireless mouse',                               20, 79.00,
        (SELECT id FROM category WHERE name = 'Mice')),
    ('Sony WH-1000XM5',      'Noise-cancelling wireless headphones',                     18, 349.99,
        (SELECT id FROM category WHERE name = 'Headphones')),
    ('Bose QuietComfort 45', 'Noise-cancelling over-ear headphones',                      8, 279.00,
        (SELECT id FROM category WHERE name = 'Headphones')),
    ('HyperX Cloud II',      'Wired gaming headset, 7.1 surround',                       35, 79.99,
        (SELECT id FROM category WHERE name = 'Headphones'));
