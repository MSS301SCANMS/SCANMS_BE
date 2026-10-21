-- Optional for deployments using JPA_DDL_AUTO=validate/none. Local development
-- uses Hibernate update. Run on promotion_db before deploying the new service.
-- A duplicate existing token must be reviewed before adding the unique index;
-- this migration never deletes or rewrites referral links.
BEGIN;
ALTER TABLE referral_links ADD COLUMN IF NOT EXISTS label varchar(150);
ALTER TABLE referral_links ADD COLUMN IF NOT EXISTS channel varchar(255);
CREATE UNIQUE INDEX IF NOT EXISTS uq_referral_links_token ON referral_links(token);
COMMIT;
