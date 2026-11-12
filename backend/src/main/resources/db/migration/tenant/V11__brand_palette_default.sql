-- The product accent ("brand", #dc7e3d) replaces emerald as the default primary colour.
-- Tenants still on the old seeded default move to it; any other choice is kept.
update tenant_settings set value = 'brand' where key = 'brand.primaryColor' and value = 'emerald';
