-- Portable clothing products. Never reset existing prices, stock, ownership or approval.
INSERT INTO stores (store_id,owner_user_id,name,description,approval_status,rating_average,rating_count,version,created_at,updated_at)
SELECT '02c1461c-aead-4366-9116-f72915bac9ce', '${soraOwnerId}', 'Sora Skin', 'Trang phục dễ mặc, dễ phối cho mỗi ngày.', 'APPROVED', 0, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM stores WHERE lower(trim(name)) = 'sora skin')
ON CONFLICT DO NOTHING;

-- Additional outfit for purchase, referral and full-return testing.
INSERT INTO product_categories (category_id,name,slug,description,active,display_order,created_at,updated_at)
VALUES ('f2eca4eb-80b0-49d9-894f-5a066a088a01','Áo thun','sora-ao-thun','Áo thun và trang phục thường ngày',TRUE,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;

-- One-time replacement of the original dress sample; keep purchased stock and prices.
UPDATE products SET name='Áo thun cotton trắng cổ tròn Sora',
 description='Áo thun cotton trắng tay ngắn, cổ tròn, phom regular dễ phối đồ. Chọn kích cỡ S, M, L hoặc XL trước khi đặt hàng. Sản phẩm mẫu để kiểm thử mua hàng và hoàn trả.',
 category='Áo thun',material='Cotton',category_id='f2eca4eb-80b0-49d9-894f-5a066a088a01',
 image_refs='{"main":"/assets/products/sora-white-tshirt.png"}'::jsonb,
 attributes=attributes || '{"demoSeed":"sora-clothing-v3"}'::jsonb,updated_at=CURRENT_TIMESTAMP
WHERE product_id='c32f57a8-66c9-4f93-8a3c-97406e58b201'
 AND name='Đầm linen trắng cổ V Sora' AND attributes->>'demoSeed'='sora-clothing-v2';
UPDATE product_variants SET sku=replace(sku,'SORA-LINEN-TRANG-','SORA-THUN-TRANG-'),updated_at=CURRENT_TIMESTAMP
WHERE product_id='c32f57a8-66c9-4f93-8a3c-97406e58b201' AND sku LIKE 'SORA-LINEN-TRANG-%';
UPDATE product_images SET image_url='/assets/products/sora-white-tshirt.png',object_key='seed/sora-white-tshirt.png',alt_text='Áo thun cotton trắng cổ tròn Sora',updated_at=CURRENT_TIMESTAMP
WHERE image_id='54879fb6-9aee-4ca5-aa02-d1564ead3201' AND image_url='/assets/products/real/linen-dress.jpg';

INSERT INTO products (product_id,store_id,category_id,name,description,category,material,attributes,image_refs,sizes_stock,size_guide,price_vnd,status,version,created_at,updated_at)
SELECT 'c32f57a8-66c9-4f93-8a3c-97406e58b201',s.store_id,
       (SELECT category_id FROM product_categories WHERE slug='sora-ao-thun'),
       'Áo thun cotton trắng cổ tròn Sora',
       'Áo thun cotton trắng tay ngắn, cổ tròn, phom regular dễ phối đồ. Chọn kích cỡ S, M, L hoặc XL trước khi đặt hàng. Sản phẩm mẫu dùng trải nghiệm mua hàng, tiếp thị liên kết và trả hàng trên SCANMS.',
       'Áo thun','Cotton',
       '{"color":"Trắng","demoSeed":"sora-clothing-v3","brand":"Sora Skin"}'::jsonb,
       '{"main":"/assets/products/sora-white-tshirt.png"}'::jsonb,
       '{"S":20,"M":20,"L":20,"XL":20}'::jsonb,
       '{"unit":"cm","note":"Chọn size theo số đo; nhắn shop để được tư vấn.","sizes":["S","M","L","XL"]}'::jsonb,
       199000,'ACTIVE',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
FROM stores s WHERE lower(trim(s.name))='sora skin'
ON CONFLICT DO NOTHING;

INSERT INTO product_variants (variant_id,product_id,sku,size,color,price_vnd,stock_quantity,status,version,created_at,updated_at)
SELECT v.id,'c32f57a8-66c9-4f93-8a3c-97406e58b201',v.sku,v.size,'Trắng',199000,20,'ACTIVE',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
FROM (VALUES
 ('bc3d2e64-13f8-4551-9b11-83e12ef1a201','SORA-THUN-TRANG-S','S'),
 ('bc3d2e64-13f8-4551-9b11-83e12ef1a202','SORA-THUN-TRANG-M','M'),
 ('bc3d2e64-13f8-4551-9b11-83e12ef1a203','SORA-THUN-TRANG-L','L'),
 ('bc3d2e64-13f8-4551-9b11-83e12ef1a204','SORA-THUN-TRANG-XL','XL')
) AS v(id,sku,size)
WHERE EXISTS (SELECT 1 FROM products WHERE product_id='c32f57a8-66c9-4f93-8a3c-97406e58b201')
ON CONFLICT DO NOTHING;

INSERT INTO product_images (image_id,product_id,object_key,image_url,primary_image,sort_order,alt_text,created_at,updated_at)
SELECT '54879fb6-9aee-4ca5-aa02-d1564ead3201','c32f57a8-66c9-4f93-8a3c-97406e58b201',
       'seed/sora-white-tshirt.png','/assets/products/sora-white-tshirt.png',TRUE,0,'Áo thun cotton trắng cổ tròn Sora',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
WHERE EXISTS (SELECT 1 FROM products WHERE product_id='c32f57a8-66c9-4f93-8a3c-97406e58b201')
ON CONFLICT DO NOTHING;

INSERT INTO product_categories (category_id,name,slug,description,active,display_order,created_at,updated_at)
VALUES ('8d4da686-5959-49e4-9a3c-08b314c8e8eb','Áo sơ mi','sora-ao-so-mi','Áo sơ mi và trang phục thường ngày',TRUE,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;

INSERT INTO products (product_id,store_id,category_id,name,description,category,material,attributes,image_refs,sizes_stock,size_guide,price_vnd,status,version,created_at,updated_at)
SELECT 'a77f64b0-f46d-4c9b-87db-c07f797a2b21', s.store_id,
       (SELECT category_id FROM product_categories WHERE slug = 'sora-ao-so-mi'),
       'Áo sơ mi caro xanh dài tay Sora',
       'Áo sơ mi caro xanh dài tay, phom regular dễ phối cùng quần jeans hoặc kaki. Có thể mặc riêng hoặc khoác ngoài áo thun. Chọn kích cỡ S, M, L hoặc XL trước khi đặt hàng. Sản phẩm mẫu để trải nghiệm mua sắm trên SCANMS.',
       'Áo sơ mi','Vải dệt',
       '{"color":"Caro xanh","demoSeed":"sora-clothing-v1","brand":"Sora Skin"}'::jsonb,
       '{"main":"/assets/products/sora-caro-shirt.webp"}'::jsonb,
       '{"S":25,"M":25,"L":25,"XL":25}'::jsonb,
       '{"unit":"cm","note":"Tham khảo số đo trước khi chọn size; nhắn shop để được tư vấn.","sizes":["S","M","L","XL"]}'::jsonb,
       299000,'ACTIVE',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
FROM stores s WHERE lower(trim(s.name)) = 'sora skin'
ON CONFLICT DO NOTHING;

INSERT INTO product_variants (variant_id,product_id,sku,size,color,price_vnd,stock_quantity,status,version,created_at,updated_at)
SELECT v.id,'a77f64b0-f46d-4c9b-87db-c07f797a2b21',v.sku,v.size,'Caro xanh',299000,25,'ACTIVE',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
FROM (VALUES
 ('de84349a-8413-47d4-ae1a-0c3e014c1a01','SORA-CARO-XANH-S','S'),
 ('de84349a-8413-47d4-ae1a-0c3e014c1a02','SORA-CARO-XANH-M','M'),
 ('de84349a-8413-47d4-ae1a-0c3e014c1a03','SORA-CARO-XANH-L','L'),
 ('de84349a-8413-47d4-ae1a-0c3e014c1a04','SORA-CARO-XANH-XL','XL')
) AS v(id,sku,size)
WHERE EXISTS (SELECT 1 FROM products WHERE product_id = 'a77f64b0-f46d-4c9b-87db-c07f797a2b21')
ON CONFLICT DO NOTHING;

INSERT INTO product_images (image_id,product_id,object_key,image_url,primary_image,sort_order,alt_text,created_at,updated_at)
SELECT '45516ef7-f72a-4bac-b28e-0cba09b2de75','a77f64b0-f46d-4c9b-87db-c07f797a2b21',
       'seed/sora-caro-shirt.webp','/assets/products/sora-caro-shirt.webp',TRUE,0,'Áo sơ mi caro xanh dài tay Sora',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
WHERE EXISTS (SELECT 1 FROM products WHERE product_id = 'a77f64b0-f46d-4c9b-87db-c07f797a2b21')
ON CONFLICT DO NOTHING;
