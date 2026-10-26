CREATE TABLE IF NOT EXISTS referral_clicks (
 id varchar(255) PRIMARY KEY, referral_link_id varchar(255), product_id varchar(255), short_video_id varchar(255),
 clicked_at timestamp(6), expires_at timestamp(6)
);
CREATE INDEX IF NOT EXISTS ix_referral_clicks_expiry ON referral_clicks(expires_at);
