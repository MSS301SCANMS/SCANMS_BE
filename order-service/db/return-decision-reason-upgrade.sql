-- Allow a shop's return decision to include a useful explanation.
ALTER TABLE return_requests
    ALTER COLUMN decision_reason TYPE varchar(3000);
