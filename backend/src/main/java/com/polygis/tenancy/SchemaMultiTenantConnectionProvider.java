package com.polygis.tenancy;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;

/**
 * Schema-per-tenant: one shared pool, each borrowed connection is pointed at the tenant schema via
 * {@code Connection#setSchema} (PostgreSQL JDBC translates this to {@code SET search_path}) and reset to
 * {@code public} on release so a pooled connection never leaks a tenant.
 */
public class SchemaMultiTenantConnectionProvider implements MultiTenantConnectionProvider<String> {

    private final transient DataSource dataSource;

    public SchemaMultiTenantConnectionProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public Connection getConnection(String schema) throws SQLException {
        Connection connection = getAnyConnection();
        try {
            connection.setSchema(TenantSchemas.requireValidSchema(schema));
            return connection;
        } catch (SQLException | RuntimeException e) {
            connection.close();
            throw e;
        }
    }

    @Override
    public void releaseConnection(String schema, Connection connection) throws SQLException {
        try {
            connection.setSchema(TenantContext.DEFAULT_SCHEMA);
        } finally {
            connection.close();
        }
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return unwrapType.isInstance(this);
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        if (isUnwrappableAs(unwrapType)) return unwrapType.cast(this);
        throw new IllegalArgumentException("Cannot unwrap to " + unwrapType);
    }
}
