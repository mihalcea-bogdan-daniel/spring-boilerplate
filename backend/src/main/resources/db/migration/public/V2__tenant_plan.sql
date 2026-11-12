-- Commercial plan of a tenant; limits per plan are configured in application.yml (app.plans.*).
alter table tenants add column plan varchar(40) not null default 'free';
