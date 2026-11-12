package com.polygis.tenancy;

import java.util.regex.Pattern;

/** Single place that turns slugs into schema names and guards against SQL injection in identifiers. */
public final class TenantSchemas {

    public static final Pattern SLUG = Pattern.compile("^[a-z][a-z0-9-]{1,40}[a-z0-9]$");
    private static final Pattern SCHEMA = Pattern.compile("^[a-z_][a-z0-9_]{0,62}$");

    private TenantSchemas() {
    }

    public static String schemaFor(String slug) {
        if (!SLUG.matcher(slug).matches()) {
            throw new IllegalArgumentException("Invalid tenant slug: " + slug);
        }
        return "t_" + slug.replace('-', '_');
    }

    public static String requireValidSchema(String schema) {
        if (schema == null || !SCHEMA.matcher(schema).matches()) {
            throw new IllegalArgumentException("Invalid schema identifier: " + schema);
        }
        return schema;
    }
}
