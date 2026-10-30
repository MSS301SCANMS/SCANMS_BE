-- Backfill Product.attributes fashion tags for outfit AI.
-- Run against product_db (port 5437):
--   psql -h 127.0.0.1 -p 5437 -U postgres -d product_db -f backfill-fashion-tags.sql

CREATE OR REPLACE FUNCTION scanms_norm(t text)
RETURNS text LANGUAGE sql IMMUTABLE AS $$
  SELECT lower(translate(coalesce(t, ''),
    'áàảãạăắằẳẵặâấầẩẫậéèẻẽẹêếềểễệíìỉĩịóòỏõọôốồổỗộơớờởỡợúùủũụưứừửữựýỳỷỹỵđÁÀẢÃẠĂẮẰẲẴẶÂẤẦẨẪẬÉÈẺẼẸÊẾỀỂỄỆÍÌỈĨỊÓÒỎÕỌÔỐỒỔỖỘƠỚỜỞỠỢÚÙỦŨỤƯỨỪỬỮỰÝỲỶỸỴĐ',
    'aaaaaaaaaaaaaaaaaeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyydAAAAAAAAAAAAAAAAAEEEEEEEEEEEIIIIIOOOOOOOOOOOOOOOOOUUUUUUUUUUUYYYYYD'));
$$;

WITH inferred AS (
  SELECT
    product_id,
    CASE
      WHEN scanms_norm(name || ' ' || coalesce(category, '')) ~ '(giay|sneaker|sandal|boot|dep |loafer|footwear|cleat)' THEN 'SHOE'
      WHEN scanms_norm(name || ' ' || coalesce(category, '')) ~ '(ao khoac|hoodie|jacket|cardigan)'
           AND scanms_norm(name) ~ '(co mu|mu )' THEN 'OUTERWEAR'
      WHEN scanms_norm(name || ' ' || coalesce(category, '')) ~ '(^| )(non|mu luoi|mu bucket|beanie|snapback|beret|fedora|bucket)( |$)'
           OR scanms_norm(category) ~ 'non|mu' THEN 'HAT'
      WHEN scanms_norm(name || ' ' || coalesce(category, '')) ~ '(vay|dam)' THEN 'DRESS'
      WHEN scanms_norm(name || ' ' || coalesce(category, '')) ~ '(quan|short|jean|jogger|legging)' THEN 'BOTTOM'
      WHEN scanms_norm(name || ' ' || coalesce(category, '')) ~ '(ao khoac|hoodie|blazer|coat|jacket|cardigan)' THEN 'OUTERWEAR'
      WHEN scanms_norm(name || ' ' || coalesce(category, '')) ~ '(ao |so mi|tank|crop)' THEN 'TOP'
      WHEN scanms_norm(name || ' ' || coalesce(category, '')) ~ '(bo |set |outfit|corset)' THEN 'SET'
      ELSE 'OTHER'
    END AS apparel_type,
    CASE
      WHEN scanms_norm(name || ' ' || coalesce(category, '')) ~ '(^| )nu( |$)|women|woman|ladies'
           AND scanms_norm(name || ' ' || coalesce(category, '')) !~ '(^| )nam( |$)|men|man' THEN 'FEMALE'
      WHEN scanms_norm(name || ' ' || coalesce(category, '')) ~ '(^| )nam( |$)|men|man|male'
           AND scanms_norm(name || ' ' || coalesce(category, '')) !~ '(^| )nu( |$)|women|woman' THEN 'MALE'
      ELSE 'UNISEX'
    END AS gender_target,
    CASE
      WHEN scanms_norm(name || ' ' || coalesce(category, '') || ' ' || coalesce(description, '')) ~ 'streetwear|street' THEN 'STREETWEAR'
      WHEN scanms_norm(name || ' ' || coalesce(category, '') || ' ' || coalesce(description, '')) ~ 'sport|athletic|running|baseball|tennis' THEN 'SPORT'
      WHEN scanms_norm(name || ' ' || coalesce(category, '') || ' ' || coalesce(description, '')) ~ 'formal|cong so|blazer' THEN 'FORMAL'
      WHEN scanms_norm(name || ' ' || coalesce(category, '') || ' ' || coalesce(description, '')) ~ 'chic|elegant' THEN 'CHIC'
      WHEN scanms_norm(name || ' ' || coalesce(category, '') || ' ' || coalesce(description, '')) ~ 'minimal' THEN 'MINIMAL'
      ELSE 'CASUAL'
    END AS fashion_style,
    CASE
      WHEN attributes ? 'color' AND jsonb_typeof(attributes->'color') = 'string'
        THEN jsonb_build_array(lower(attributes->>'color'))
      ELSE '[]'::jsonb
    END AS color_tags
  FROM products
)
UPDATE products p
SET attributes = coalesce(p.attributes, '{}'::jsonb)
  || jsonb_build_object(
       'apparelType', i.apparel_type,
       'genderTarget', i.gender_target,
       'fashionStyle', i.fashion_style,
       'colorTags', CASE
         WHEN coalesce(p.attributes->'colorTags', 'null'::jsonb) = 'null'::jsonb
              OR jsonb_typeof(p.attributes->'colorTags') <> 'array'
              OR jsonb_array_length(p.attributes->'colorTags') = 0
           THEN i.color_tags
         ELSE p.attributes->'colorTags'
       END
     )
FROM inferred i
WHERE p.product_id = i.product_id
  AND (
    p.attributes IS NULL
    OR NOT (p.attributes ? 'apparelType')
    OR coalesce(p.attributes->>'apparelType', '') = ''
  );

-- Force shoe/hat tags for demo seeds when name clearly matches
UPDATE products
SET attributes = coalesce(attributes, '{}'::jsonb)
  || jsonb_build_object('apparelType', 'SHOE', 'genderTarget', coalesce(attributes->>'genderTarget', 'UNISEX'))
WHERE scanms_norm(name) ~ 'giay|sneaker|sandal|boot|loafer'
  AND coalesce(attributes->>'apparelType', '') NOT IN ('SHOE');

UPDATE products
SET attributes = coalesce(attributes, '{}'::jsonb)
  || jsonb_build_object('apparelType', 'HAT', 'genderTarget', coalesce(attributes->>'genderTarget', 'UNISEX'))
WHERE scanms_norm(name) ~ '(^| )non |mu luoi|mu bucket|beanie|snapback|bucket'
  AND scanms_norm(name) !~ 'ao khoac|hoodie|jacket'
  AND coalesce(attributes->>'apparelType', '') NOT IN ('HAT');

SELECT attributes->>'apparelType' AS apparel_type, count(*) AS cnt
FROM products
GROUP BY 1
ORDER BY cnt DESC;
