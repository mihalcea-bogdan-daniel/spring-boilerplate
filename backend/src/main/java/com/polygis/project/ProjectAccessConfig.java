package com.polygis.project;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.polygis.access.ResourceTypeDefinition;

/** How access works for projects. Every owned module registers one of these. */
@Configuration
public class ProjectAccessConfig {

    public static final String TYPE = "project";

    @Bean
    ResourceTypeDefinition projectResourceType() {
        return ResourceTypeDefinition.of(TYPE)
                .action("read", "owner", "editor", "viewer")
                .action("write", "owner", "editor")
                .action("delete", "owner")
                .action("share", "owner")
                .supervisorActions("read")
                .adminPermission("project:read-all", "read");
    }
}
