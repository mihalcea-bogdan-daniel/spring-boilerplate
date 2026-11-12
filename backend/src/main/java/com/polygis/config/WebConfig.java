package com.polygis.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.polygis.tenancy.TenantContext;

@Configuration
public class WebConfig {

    /** Propagates the TenantContext into @Async work (mail sending, background jobs). */
    @Bean
    TaskDecorator tenantTaskDecorator() {
        return runnable -> {
            var tenant = TenantContext.current().orElse(null);
            return () -> {
                if (tenant == null) {
                    runnable.run();
                } else {
                    TenantContext.runAs(tenant, runnable);
                }
            };
        };
    }

    @Bean(name = "taskExecutor")
    Executor taskExecutor(TaskDecorator tenantTaskDecorator) {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(4);
        ex.setMaxPoolSize(16);
        ex.setQueueCapacity(500);
        ex.setThreadNamePrefix("async-");
        ex.setTaskDecorator(tenantTaskDecorator);
        ex.initialize();
        return ex;
    }
}
