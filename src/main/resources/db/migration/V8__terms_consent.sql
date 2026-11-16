-- GDPR: when each user accepted the privacy policy and terms, and which version they accepted.
-- Accounts created before this existed keep null (they are asked nothing; the policy applies from now on).
alter table tbl_profiles add column if not exists terms_accepted_at timestamp(6);
alter table tbl_profiles add column if not exists terms_version varchar(20);
