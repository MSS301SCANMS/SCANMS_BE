CREATE TABLE IF NOT EXISTS short_videos (
 id varchar(255) PRIMARY KEY, creator_user_id varchar(255), collaborator_id varchar(255), creator_name varchar(255),
 store_id varchar(255), title varchar(255), caption varchar(2000), video_url varchar(1500), poster_url varchar(1500),
 public_id varchar(500), duration_seconds float8 NOT NULL, status varchar(255), product_links jsonb,
 created_at timestamp(6), version bigint
);
CREATE INDEX IF NOT EXISTS ix_short_videos_feed ON short_videos(status,created_at DESC,id DESC);
CREATE INDEX IF NOT EXISTS ix_short_videos_creator ON short_videos(creator_user_id,created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uk_short_videos_asset ON short_videos(public_id);
