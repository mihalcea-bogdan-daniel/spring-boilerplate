-- Access tokens issued before this instant are rejected (password change, disable, role change, sign-out everywhere).
alter table users add column sessions_invalid_before timestamptz;
