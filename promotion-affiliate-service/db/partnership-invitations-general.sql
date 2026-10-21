-- Store/KOL introductions are valid before a product is selected.
ALTER TABLE IF EXISTS partnership_invitations ALTER COLUMN product_id DROP NOT NULL;
