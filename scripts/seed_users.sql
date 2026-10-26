
INSERT INTO users (user_id, created_at, updated_at, email, full_name, identity_subject, status, phone_number, username)
VALUES ('0773dbd2-ef49-4aee-9089-231946d28279', NOW(), NOW(), 'kolctv01@scanms.com', 'Le Van KOLCTV', 'db857a09-9468-42df-b3c5-275c18564ee5', 'ACTIVE', '0911222333', 'kolctv01')
ON CONFLICT (email) DO UPDATE SET identity_subject = 'db857a09-9468-42df-b3c5-275c18564ee5', status = 'ACTIVE';


INSERT INTO user_roles (user_id, role_id)
SELECT user_id, '3286c240-67cf-4068-878e-3285643a48c3' FROM users WHERE email = 'kolctv01@scanms.com'
ON CONFLICT DO NOTHING;


INSERT INTO user_roles (user_id, role_id)
SELECT user_id, '32368142-1147-41d9-b5f2-639c9ae52025' FROM users WHERE email = 'kolctv01@scanms.com'
ON CONFLICT DO NOTHING;


INSERT INTO users (user_id, created_at, updated_at, email, full_name, identity_subject, status, phone_number, username)
VALUES ('6e405eec-561b-4e57-bed2-098d38dc3abf', NOW(), NOW(), 'shopowner01@scanms.com', 'Nguyen Van ShopOwner', '6463cf68-42aa-4526-bbf1-cffb04911a13', 'ACTIVE', '0922333444', 'shopowner01')
ON CONFLICT (email) DO UPDATE SET identity_subject = '6463cf68-42aa-4526-bbf1-cffb04911a13', status = 'ACTIVE';


INSERT INTO user_roles (user_id, role_id)
SELECT user_id, '3286c240-67cf-4068-878e-3285643a48c3' FROM users WHERE email = 'shopowner01@scanms.com'
ON CONFLICT DO NOTHING;


INSERT INTO user_roles (user_id, role_id)
SELECT user_id, '4ea22f2e-c1fa-4c93-bcd4-8897d1a89927' FROM users WHERE email = 'shopowner01@scanms.com'
ON CONFLICT DO NOTHING;


INSERT INTO users (user_id, created_at, updated_at, email, full_name, identity_subject, status, phone_number, username)
VALUES ('a10cecf8-a4d6-42f3-937a-cead57887258', NOW(), NOW(), 'manager01@scanms.com', 'System Manager', '977fad6b-686c-488b-9660-938552607661', 'ACTIVE', '0933444555', 'manager01')
ON CONFLICT (email) DO UPDATE SET identity_subject = '977fad6b-686c-488b-9660-938552607661', status = 'ACTIVE';


INSERT INTO user_roles (user_id, role_id)
SELECT user_id, '3286c240-67cf-4068-878e-3285643a48c3' FROM users WHERE email = 'manager01@scanms.com'
ON CONFLICT DO NOTHING;


INSERT INTO user_roles (user_id, role_id)
SELECT user_id, 'c06aaaea-1dc1-4fdb-bdd0-7d34ac4840ad' FROM users WHERE email = 'manager01@scanms.com'
ON CONFLICT DO NOTHING;


INSERT INTO users (user_id, created_at, updated_at, email, full_name, identity_subject, status, phone_number, username)
VALUES ('8fb750dc-7df9-4f89-9109-86cf2afb1eb6', NOW(), NOW(), 'admin@scanms.com', 'System Administrator', 'b40f1882-9e28-4625-ab6c-64b1a6b1c676', 'ACTIVE', '0944555666', 'admin')
ON CONFLICT (email) DO UPDATE SET identity_subject = 'b40f1882-9e28-4625-ab6c-64b1a6b1c676', status = 'ACTIVE';


INSERT INTO user_roles (user_id, role_id)
SELECT user_id, '3286c240-67cf-4068-878e-3285643a48c3' FROM users WHERE email = 'admin@scanms.com'
ON CONFLICT DO NOTHING;


INSERT INTO user_roles (user_id, role_id)
SELECT user_id, '4b3d9dd3-a16e-4956-b72e-c99e828c57e4' FROM users WHERE email = 'admin@scanms.com'
ON CONFLICT DO NOTHING;
