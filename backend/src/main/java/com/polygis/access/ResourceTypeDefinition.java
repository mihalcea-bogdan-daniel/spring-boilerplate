package com.polygis.access;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * How access works for one resource type. Register one as a Spring bean per type:
 * <pre>{@code
 * @Bean ResourceTypeDefinition projectType() {
 *     return ResourceTypeDefinition.of("project")
 *         .action("read",   "owner", "editor", "viewer")
 *         .action("write",  "owner", "editor")
 *         .action("delete", "owner")
 *         .action("share",  "owner")
 *         .supervisorActions("read")
 *         .adminPermission("project:read-all", "read");
 * }
 * }</pre>
 * Relations are an open set per type: a workflow task would use {@code assignee} and {@code candidate}.
 */
public final class ResourceTypeDefinition {

    /** Whether a resource owned by a unit is also visible to members of the units below it. */
    public enum UnitInheritance { SUBTREE, UNIT_ONLY }

    private final String type;
    private final Map<String, Set<String>> relationsByAction = new LinkedHashMap<>();
    private final Set<String> supervisorActions = new LinkedHashSet<>();
    private final Map<String, Set<String>> adminPermissions = new LinkedHashMap<>();
    private UnitInheritance unitInheritance = UnitInheritance.SUBTREE;
    private String ownerRelation = "owner";

    private ResourceTypeDefinition(String type) {
        this.type = type;
    }

    public static ResourceTypeDefinition of(String type) {
        return new ResourceTypeDefinition(type);
    }

    /** Holders of any of these relations may perform the action. */
    public ResourceTypeDefinition action(String action, String... relations) {
        relationsByAction.put(action, Set.of(relations));
        return this;
    }

    /** Actions a supervisor inherits on resources related to the people (and units) they supervise. */
    public ResourceTypeDefinition supervisorActions(String... actions) {
        supervisorActions.addAll(Set.of(actions));
        return this;
    }

    /** RBAC bypass: holders of the permission may perform these actions on every resource of the type. */
    public ResourceTypeDefinition adminPermission(String permission, String... actions) {
        adminPermissions.put(permission, Set.of(actions));
        return this;
    }

    public ResourceTypeDefinition unitInheritance(UnitInheritance inheritance) {
        this.unitInheritance = inheritance;
        return this;
    }

    /** The relation that means "owner" (transfers move it). Default {@code owner}. */
    public ResourceTypeDefinition ownerRelation(String relation) {
        this.ownerRelation = relation;
        return this;
    }

    public String type() {
        return type;
    }

    public Set<String> actions() {
        return relationsByAction.keySet();
    }

    public Set<String> relationsFor(String action) {
        return relationsByAction.getOrDefault(action, Set.of());
    }

    /** Every relation any action mentions: the only ones that may be granted. */
    public Set<String> relations() {
        Set<String> all = new LinkedHashSet<>();
        relationsByAction.values().forEach(all::addAll);
        return all;
    }

    public boolean supervisorMay(String action) {
        return supervisorActions.contains(action);
    }

    public Map<String, Set<String>> adminPermissions() {
        return adminPermissions;
    }

    public UnitInheritance unitInheritance() {
        return unitInheritance;
    }

    public String ownerRelation() {
        return ownerRelation;
    }
}
