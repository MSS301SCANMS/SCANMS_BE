BEGIN;
CREATE TABLE IF NOT EXISTS checkout_reservations (
 order_id varchar(255) PRIMARY KEY,request_hash text,lines jsonb,status varchar(255),created_at timestamp with time zone
);
COMMIT;
