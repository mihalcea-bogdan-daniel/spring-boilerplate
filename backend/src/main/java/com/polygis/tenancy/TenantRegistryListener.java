package com.polygis.tenancy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * Keeps every node's {@link TenantRegistry} in sync through PostgreSQL LISTEN/NOTIFY: a tenant provisioned,
 * suspended or re-pointed on one node is visible on all others within milliseconds instead of waiting for the
 * 60-second poll (which remains as a safety net). Uses one dedicated connection outside the pool.
 */
@Slf4j
@Component
public class TenantRegistryListener {

    private final TenantRegistry registry;
    private final DataSourceProperties dataSource;
    private final boolean enabled;
    private volatile boolean running;
    private Thread thread;

    public TenantRegistryListener(TenantRegistry registry, DataSourceProperties dataSource,
                                  @Value("${app.tenancy.registry-notify:true}") boolean enabled) {
        this.registry = registry;
        this.dataSource = dataSource;
        this.enabled = enabled;
    }

    @PostConstruct
    void start() {
        if (!enabled) return;
        running = true;
        thread = new Thread(this::listen, "tenant-registry-listener");
        thread.setDaemon(true);
        thread.start();
    }

    @PreDestroy
    void stop() {
        running = false;
        if (thread != null) thread.interrupt();
    }

    private void listen() {
        while (running) {
            try (Connection connection = DriverManager.getConnection(dataSource.determineUrl(),
                    dataSource.determineUsername(), dataSource.determinePassword())) {
                try (Statement st = connection.createStatement()) {
                    st.execute("LISTEN " + TenantRegistry.CHANNEL);
                }
                PGConnection pg = connection.unwrap(PGConnection.class);
                registry.reload(); // catch up on anything missed while disconnected
                while (running) {
                    PGNotification[] notifications = pg.getNotifications(10_000);
                    if (notifications != null && notifications.length > 0) registry.reload();
                }
            } catch (SQLException | RuntimeException e) {
                if (!running) return;
                log.warn("Tenant registry listener lost its connection, retrying in 5s: {}", e.getMessage());
                try {
                    Thread.sleep(5_000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
