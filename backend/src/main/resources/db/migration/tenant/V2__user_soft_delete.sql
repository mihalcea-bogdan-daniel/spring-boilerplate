-- Soft delete: keep the row for audit/history, free the e-mail address for re-use.
alter table users add column deleted_at timestamptz;

drop index ux_users_email;
create unique index ux_users_email on users (lower(email)) where deleted_at is null;
