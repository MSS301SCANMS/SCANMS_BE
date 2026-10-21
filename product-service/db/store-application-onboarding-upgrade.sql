-- For deployments using ddl-auto=validate/none; local update adds this column automatically.
ALTER TABLE store_applications ADD COLUMN IF NOT EXISTS onboarding_data jsonb;
