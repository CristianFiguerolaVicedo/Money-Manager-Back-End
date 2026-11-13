-- Audit: profile photos were uploaded straight from the browser to Cloudinary with an unsigned preset.
-- Now the backend checks them and keeps them here. photo_key is random and changes on every upload, so the
-- public URL /profile-photos/{photo_key} cannot be guessed and can be cached forever.
create table if not exists tbl_profile_photos (
    profile_id bigint primary key references tbl_profiles (id) on delete cascade,
    photo_key varchar(64) not null unique,
    content_type varchar(20) not null,
    data bytea not null,
    updated_at timestamp(6)
);
