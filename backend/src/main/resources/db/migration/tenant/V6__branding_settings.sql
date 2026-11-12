insert into tenant_settings (key, value) values
    ('brand.primaryColor', 'emerald'),
    ('brand.logoUrl', null)
on conflict (key) do nothing;
