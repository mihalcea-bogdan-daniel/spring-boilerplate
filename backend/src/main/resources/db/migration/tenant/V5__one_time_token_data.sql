-- Small payload carried by a one-time token (e.g. who started an impersonation).
alter table one_time_tokens add column data varchar(320);
