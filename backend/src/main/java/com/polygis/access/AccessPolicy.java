package com.polygis.access;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import com.polygis.audit.AuditService;
import com.polygis.common.ApiException;
import com.polygis.group.UserGroupMemberRepository;
import com.polygis.org.OrgMembership;
import com.polygis.org.OrgMembershipRepository;
import com.polygis.org.OrgRank;
import com.polygis.org.OrgRankRepository;
import com.polygis.org.OrgService;
import com.polygis.org.OrgUnitRepository;

/**
 * The single place that decides who may do what with any owned resource. A caller may perform an action on a
 * resource when one of its relations for that action names:
 * <ul>
 *   <li>the caller,</li>
 *   <li>a unit the caller belongs to (or, for {@code SUBTREE} types, a unit above one they belong to),</li>
 *   <li>a group the caller belongs to,</li>
 *   <li>someone the caller supervises, or a unit inside a subtree the caller leads, when the type lists the
 *       action in {@code supervisorActions},</li>
 * </ul>
 * or the caller holds the type's admin permission for the action.
 */
@Service
public class AccessPolicy {

    /** Which relations a list shows. */
    public enum Scope {
        /** Everything the caller can see. */
        VISIBLE,
        /** Related to the caller, their units or their groups; not through supervision. */
        MINE,
        /** Related to people or units the caller supervises. */
        TEAM,
        /** Every resource of the type; needs the admin permission. */
        ALL
    }

    private static final String MEMO = AccessPolicy.class.getName() + ".context";

    private final Map<String, ResourceTypeDefinition> types;
    private final List<RelationSource> sources;
    private final TupleRelationSource tuples;
    private final OrgService org;
    private final OrgMembershipRepository memberships;
    private final OrgRankRepository ranks;
    private final OrgUnitRepository units;
    private final UserGroupMemberRepository groupMembers;
    private final AuditService audit;

    public AccessPolicy(List<ResourceTypeDefinition> types, List<RelationSource> sources, TupleRelationSource tuples,
                        OrgService org, OrgMembershipRepository memberships, OrgRankRepository ranks,
                        OrgUnitRepository units, UserGroupMemberRepository groupMembers, AuditService audit) {
        this.types = types.stream().collect(Collectors.toMap(ResourceTypeDefinition::type, Function.identity()));
        this.sources = sources;
        this.tuples = tuples;
        this.org = org;
        this.memberships = memberships;
        this.ranks = ranks;
        this.units = units;
        this.groupMembers = groupMembers;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ definitions and sources

    public ResourceTypeDefinition type(String type) {
        ResourceTypeDefinition def = types.get(type);
        if (def == null) throw ApiException.notFound("Resource type");
        return def;
    }

    /** The source that owns the relations of a type: a specific one if registered, else the tuple store. */
    public RelationSource sourceFor(String type) {
        return sources.stream().filter(s -> s != tuples && s.supports(type)).findFirst().orElse(tuples);
    }

    // ------------------------------------------------------------------ deciding

    @Transactional(readOnly = true)
    public AccessFilter filter(String type, String action) {
        return filter(type, action, Scope.VISIBLE);
    }

    /** What the current caller may see for an action, for list queries. */
    @Transactional(readOnly = true)
    public AccessFilter filter(String type, String action, Scope scope) {
        ResourceTypeDefinition def = type(type);
        boolean admin = hasAdminPermission(def, action);
        if (scope == Scope.ALL) {
            if (!admin) throw ApiException.forbidden("scope_all", "You can't see every " + type);
            return AccessFilter.all();
        }
        if (admin && scope == Scope.VISIBLE) return AccessFilter.all();
        Set<String> relations = def.relationsFor(action);
        UUID me = currentUserId();
        if (me == null || relations.isEmpty()) return AccessFilter.nothing();
        Context ctx = context(me);

        Set<UUID> users = new HashSet<>();
        Set<UUID> unitIds = new HashSet<>();
        Set<UUID> groupIds = new HashSet<>();
        if (scope != Scope.TEAM) {
            users.add(me);
            unitIds.addAll(def.unitInheritance() == ResourceTypeDefinition.UnitInheritance.SUBTREE
                    ? ctx.unitsWithAncestors() : ctx.units());
            groupIds.addAll(ctx.groups());
        }
        if (scope != Scope.MINE && def.supervisorMay(action)) {
            users.addAll(ctx.subordinates());
            unitIds.addAll(ctx.ledSubtree());
        }
        return new AccessFilter(false, relations, users, unitIds, groupIds);
    }

    @Transactional(readOnly = true)
    public boolean can(String action, ResourceRef ref) {
        AccessFilter f = filter(ref.type(), action);
        if (f.everything()) return true;
        if (f.isEmpty()) return false;
        return sourceFor(ref.type()).relationsOf(ref).stream().anyMatch(f::matches);
    }

    /** 404 when the caller can't even read the resource (its existence stays private), 403 when only the action is missing. */
    @Transactional(readOnly = true)
    public void require(String action, ResourceRef ref) {
        if (can(action, ref)) return;
        if (!"read".equals(action) && can("read", ref)) {
            throw ApiException.forbidden("access_denied", "You can't " + action + " this " + ref.type());
        }
        throw ApiException.notFound(capitalise(ref.type()));
    }

    /** Every action of the type with whether the caller may perform it, for the UI. */
    @Transactional(readOnly = true)
    public Map<String, Boolean> allowedActions(ResourceRef ref) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        type(ref.type()).actions().forEach(a -> result.put(a, can(a, ref)));
        return result;
    }

    /** Ids the caller can see for list endpoints; empty Optional when the source can't list (query it yourself). */
    @Transactional(readOnly = true)
    public Optional<List<String>> visibleIds(String type, String action, Scope scope) {
        AccessFilter f = filter(type, action, scope);
        if (f.everything()) return Optional.empty();
        return sourceFor(type).visibleIds(type, f);
    }

    // ------------------------------------------------------------------ changing relations

    @Transactional(readOnly = true)
    public List<RelationTuple> relations(ResourceRef ref) {
        return sourceFor(ref.type()).relationsOf(ref);
    }

    /**
     * Records the owner of a freshly created resource. The caller may only name themself, a unit they belong to
     * or lead, or a group they belong to - unless they hold the type's admin permission.
     */
    @Transactional
    public void createOwned(ResourceRef ref, SubjectRef owner) {
        ResourceTypeDefinition def = type(ref.type());
        UUID me = requireUserId();
        if (!mayActFor(me, owner) && def.adminPermissions().keySet().stream().noneMatch(OrgLookups::hasAuthority)) {
            throw ApiException.forbidden("owner_not_allowed", "You can't create things on behalf of that owner");
        }
        writable(ref.type()).add(new RelationTuple(ref, def.ownerRelation(), owner), me);
        audit.record("access.granted", ref.type(), ref.id(), def.ownerRelation() + " " + owner);
    }

    @Transactional
    public void grant(ResourceRef ref, String relation, SubjectRef subject) {
        ResourceTypeDefinition def = type(ref.type());
        require("share", ref);
        if (!def.relations().contains(relation)) {
            throw ApiException.badRequest("unknown_relation", "Unknown relation " + relation);
        }
        writable(ref.type()).add(new RelationTuple(ref, relation, subject), requireUserId());
        audit.record("access.granted", ref.type(), ref.id(), relation + " " + subject);
    }

    @Transactional
    public void revoke(ResourceRef ref, String relation, SubjectRef subject) {
        ResourceTypeDefinition def = type(ref.type());
        require("share", ref);
        if (def.ownerRelation().equals(relation) && relations(ref).stream()
                .filter(t -> t.relation().equals(relation)).count() <= 1) {
            throw ApiException.conflict("last_owner", "Transfer ownership instead of removing the only owner");
        }
        writable(ref.type()).remove(new RelationTuple(ref, relation, subject));
        audit.record("access.revoked", ref.type(), ref.id(), relation + " " + subject);
    }

    /** Replaces every owner with the new one. */
    @Transactional
    public void transferOwnership(ResourceRef ref, SubjectRef newOwner) {
        ResourceTypeDefinition def = type(ref.type());
        require("share", ref);
        RelationSource source = writable(ref.type());
        relations(ref).stream().filter(t -> t.relation().equals(def.ownerRelation())).forEach(source::remove);
        source.add(new RelationTuple(ref, def.ownerRelation(), newOwner), requireUserId());
        audit.record("access.transferred", ref.type(), ref.id(), newOwner.toString());
    }

    /** Call when a resource is deleted. */
    @Transactional
    public void forget(ResourceRef ref) {
        if (sourceFor(ref.type()) == tuples) tuples.removeAll(ref);
    }

    private RelationSource writable(String type) {
        RelationSource source = sourceFor(type);
        if (!source.writable()) {
            throw ApiException.badRequest("relations_external", "Access to " + type + " is managed by its own system");
        }
        return source;
    }

    private boolean mayActFor(UUID me, SubjectRef subject) {
        Context ctx = context(me);
        return switch (subject.kind()) {
            case USER -> subject.id().equals(me);
            case UNIT -> ctx.units().contains(subject.id()) || ctx.ledSubtree().contains(subject.id());
            case GROUP -> ctx.groups().contains(subject.id());
        };
    }

    // ------------------------------------------------------------------ the caller

    /** Who the caller is in the organisation, computed once per request. */
    record Context(Set<UUID> units, Set<UUID> unitsWithAncestors, Set<UUID> ledSubtree, Set<UUID> groups,
                   Set<UUID> subordinates) {
    }

    @SuppressWarnings("unchecked")
    Context context(UUID userId) {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        Map<UUID, Context> memo = attrs == null ? null
                : (Map<UUID, Context>) attrs.getAttribute(MEMO, RequestAttributes.SCOPE_REQUEST);
        if (memo != null && memo.containsKey(userId)) return memo.get(userId);

        Map<UUID, OrgRank> rankById = new HashMap<>();
        ranks.findAll().forEach(r -> rankById.put(r.getId(), r));
        Set<UUID> mine = new HashSet<>();
        Set<UUID> up = new HashSet<>();
        Set<UUID> led = new HashSet<>();
        for (OrgMembership m : memberships.findByUserId(userId)) {
            mine.add(m.getUnitId());
            up.addAll(units.ancestorIds(m.getUnitId()));
            OrgRank rank = rankById.get(m.getRankId());
            if (rank != null && rank.isSupervises()) led.addAll(units.subtreeIds(m.getUnitId()));
        }
        Context ctx = new Context(Set.copyOf(mine), Set.copyOf(up), Set.copyOf(led),
                Set.copyOf(groupMembers.groupIdsOf(userId)), org.subordinateIds(userId));
        if (attrs != null) {
            if (memo == null) {
                memo = new HashMap<>();
                attrs.setAttribute(MEMO, memo, RequestAttributes.SCOPE_REQUEST);
            }
            memo.put(userId, ctx);
        }
        return ctx;
    }

    private static boolean hasAdminPermission(ResourceTypeDefinition def, String action) {
        return def.adminPermissions().entrySet().stream()
                .anyMatch(e -> e.getValue().contains(action) && OrgLookups.hasAuthority(e.getKey()));
    }

    public static UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth instanceof JwtAuthenticationToken jwt ? UUID.fromString(jwt.getToken().getSubject()) : null;
    }

    private static UUID requireUserId() {
        UUID id = currentUserId();
        if (id == null) throw ApiException.forbidden("person_required", "Only people can do this");
        return id;
    }

    private static String capitalise(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Small helpers kept here so the policy has no dependency on web or controller classes. */
    static final class OrgLookups {
        private OrgLookups() {
        }

        static boolean hasAuthority(String authority) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null) return false;
            for (GrantedAuthority a : auth.getAuthorities()) if (authority.equals(a.getAuthority())) return true;
            return false;
        }
    }
}
