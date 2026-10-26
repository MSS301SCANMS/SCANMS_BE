-- Optional local bootstrap; application startup performs the same guarded initialization.
-- Never reactivate or overwrite a platform policy chosen by an operator.
BEGIN;
SELECT pg_advisory_xact_lock(624303);
INSERT INTO commission_policies
 (policy_id,policy_name,scope_type,commission_rate,return_window_days,valid_from,status,priority,version,created_at,updated_at)
SELECT '5e5ad502-5e70-4e37-83ca-f6d0f03cf510','Hoa hồng mặc định 10%','PLATFORM',10,14,CURRENT_TIMESTAMP,'ACTIVE',-100,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM commission_policies WHERE scope_type='PLATFORM')
ON CONFLICT DO NOTHING;
COMMIT;
