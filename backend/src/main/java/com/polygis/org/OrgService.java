package com.polygis.org;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import com.polygis.audit.AuditService;
import com.polygis.common.ApiException;
import com.polygis.org.OrgDtos.*;
import com.polygis.user.User;
import com.polygis.user.UserRepository;
import com.polygis.user.UserStatus;

/**
 * The organisation tree and the supervision rule.
 * <p><b>A supervises B</b> when A holds a rank marked {@code supervises} in unit U, B is a member of U or of any
 * unit below U, and A's level is greater than B's level there. Equal levels are peers; disabled people neither
 * supervise nor count. A "global" role is simply a supervising rank in the root unit.
 */
@Service
public class OrgService {

    private static final String MEMO = OrgService.class.getName() + ".subordinates";

    private final OrgUnitRepository units;
    private final OrgRankRepository ranks;
    private final OrgMembershipRepository memberships;
    private final UserRepository users;
    private final AuditService audit;

    public OrgService(OrgUnitRepository units, OrgRankRepository ranks, OrgMembershipRepository memberships,
                      UserRepository users, AuditService audit) {
        this.units = units;
        this.ranks = ranks;
        this.memberships = memberships;
        this.users = users;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ supervision

    /** Ids of everyone the user supervises. Memoised for the current request; org writes clear it. */
    @Transactional(readOnly = true)
    public Set<UUID> subordinateIds(UUID userId) {
        Map<UUID, Set<UUID>> memo = memo();
        if (memo != null && memo.containsKey(userId)) return memo.get(userId);
        Set<UUID> ids = Set.copyOf(memberships.subordinateIds(userId));
        if (memo != null) memo.put(userId, ids);
        return ids;
    }

    public boolean supervises(UUID supervisor, UUID subject) {
        return supervisor != null && subject != null && subordinateIds(supervisor).contains(subject);
    }

    /** The user's level over the unit (their best supervising rank in it or above it), or null. */
    @Transactional(readOnly = true)
    public Integer leadLevel(UUID userId, UUID unitId) {
        return memberships.leadLevel(userId, unitId);
    }

    @Transactional(readOnly = true)
    public boolean leadsAny(UUID userId) {
        return memberships.leadsAny(userId);
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, Set<UUID>> memo() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs == null) return null;
        Object memo = attrs.getAttribute(MEMO, RequestAttributes.SCOPE_REQUEST);
        if (memo == null) {
            memo = new HashMap<UUID, Set<UUID>>();
            attrs.setAttribute(MEMO, memo, RequestAttributes.SCOPE_REQUEST);
        }
        return (Map<UUID, Set<UUID>>) memo;
    }

    private static void forget() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs != null) attrs.removeAttribute(MEMO, RequestAttributes.SCOPE_REQUEST);
    }

    // ------------------------------------------------------------------ reading

    /** The whole tree with members, annotated with the caller's lead level per unit. */
    @Transactional(readOnly = true)
    public ChartNode chart(UUID callerId) {
        List<OrgUnit> all = units.findAllByOrderBySortOrderAscNameAsc();
        Map<UUID, OrgRank> rankById = ranks.findAll().stream().collect(Collectors.toMap(OrgRank::getId, r -> r));
        List<OrgMembership> allMembers = memberships.findAll();
        Map<UUID, User> people = users.findAllById(allMembers.stream().map(OrgMembership::getUserId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(User::getId, Function.identity()));

        Map<UUID, List<MemberDto>> membersByUnit = new HashMap<>();
        Map<UUID, Integer> ownLead = new HashMap<>(); // caller's supervising level held directly in a unit
        for (OrgMembership m : allMembers) {
            User u = people.get(m.getUserId());
            OrgRank r = rankById.get(m.getRankId());
            if (u == null || r == null) continue; // soft-deleted person
            membersByUnit.computeIfAbsent(m.getUnitId(), k -> new ArrayList<>())
                    .add(new MemberDto(u.getId(), u.getEmail(), u.displayName(), r.getId(), r.getLevel()));
            if (u.getId().equals(callerId) && r.isSupervises() && u.getStatus() == UserStatus.ACTIVE) {
                ownLead.merge(m.getUnitId(), r.getLevel(), Math::max);
            }
        }
        membersByUnit.values().forEach(list -> list.sort(Comparator.comparingInt(MemberDto::level).reversed()
                .thenComparing(MemberDto::name, String.CASE_INSENSITIVE_ORDER)));

        Map<UUID, List<OrgUnit>> childrenOf = new HashMap<>();
        OrgUnit root = null;
        for (OrgUnit u : all) {
            if (u.isRoot()) root = u;
            else childrenOf.computeIfAbsent(u.getParentId(), k -> new ArrayList<>()).add(u);
        }
        if (root == null) throw ApiException.notFound("Organisation");
        return node(root, childrenOf, membersByUnit, ownLead, null);
    }

    private static ChartNode node(OrgUnit unit, Map<UUID, List<OrgUnit>> childrenOf,
                                  Map<UUID, List<MemberDto>> membersByUnit, Map<UUID, Integer> ownLead,
                                  Integer inheritedLead) {
        Integer own = ownLead.get(unit.getId());
        Integer lead = inheritedLead;
        if (own != null && (lead == null || own > lead)) lead = own;
        final Integer leadHere = lead;
        List<ChartNode> children = childrenOf.getOrDefault(unit.getId(), List.of()).stream()
                .map(c -> node(c, childrenOf, membersByUnit, ownLead, leadHere)).toList();
        return new ChartNode(unit.getId(), unit.getParentId(), unit.getName(), unit.getSortOrder(),
                membersByUnit.getOrDefault(unit.getId(), List.of()), leadHere, children);
    }

    @Transactional(readOnly = true)
    public List<RankDto> ranks() {
        return ranks.findAllByOrderByLevelDesc().stream().map(RankDto::from).toList();
    }

    /** Active people for the member picker. */
    @Transactional(readOnly = true)
    public List<PersonDto> people(String q) {
        return users.search(q == null ? "" : q.trim(),
                        org.springframework.data.domain.PageRequest.of(0, 20,
                                org.springframework.data.domain.Sort.by("email")))
                .stream().filter(u -> u.getStatus() != UserStatus.DISABLED)
                .map(u -> new PersonDto(u.getId(), u.getEmail(), u.displayName())).toList();
    }

    /** Everyone the user supervises, with the units and ranks they hold. */
    @Transactional(readOnly = true)
    public List<SubordinateDto> team(UUID userId) {
        Set<UUID> ids = subordinateIds(userId);
        if (ids.isEmpty()) return List.of();
        Map<UUID, OrgUnit> unitById = units.findAll().stream().collect(Collectors.toMap(OrgUnit::getId, u -> u));
        Map<UUID, OrgRank> rankById = ranks.findAll().stream().collect(Collectors.toMap(OrgRank::getId, r -> r));
        return users.findAllById(ids).stream()
                .sorted(Comparator.comparing(User::displayName, String.CASE_INSENSITIVE_ORDER))
                .map(u -> new SubordinateDto(u.getId(), u.getEmail(), u.displayName(), u.getStatus().name(),
                        u.getLastLoginAt(), memberships.findByUserId(u.getId()).stream()
                        .filter(m -> unitById.containsKey(m.getUnitId()) && rankById.containsKey(m.getRankId()))
                        .map(m -> new SubordinateDto.Placement(m.getUnitId(), unitById.get(m.getUnitId()).getName(),
                                m.getRankId(), rankById.get(m.getRankId()).getLevel()))
                        .toList()))
                .toList();
    }

    // ------------------------------------------------------------------ memberships (leaders and org:manage)

    /**
     * Adds a person to a unit. Without {@code org:manage} the actor must lead the unit with a level strictly above
     * the rank being given, and may not change their own placement.
     */
    @Transactional
    public void addMember(UUID actorId, boolean canManage, UUID unitId, MemberRequest req) {
        OrgUnit unit = unit(unitId);
        OrgRank rank = rank(req.rankId());
        User person = users.findById(req.userId()).orElseThrow(() -> ApiException.notFound("User"));
        if (memberships.existsById(new OrgMembership.Key(person.getId(), unitId))) {
            throw ApiException.conflict("already_member", "Already a member of this unit");
        }
        requireCanAssign(actorId, canManage, unitId, person.getId(), rank, null);
        OrgMembership m = new OrgMembership();
        m.setUserId(person.getId());
        m.setUnitId(unitId);
        m.setRankId(rank.getId());
        memberships.save(m);
        forget();
        audit.record("org.member.added", "user", person.getId(), unit.getName() + " as " + rank.getKey());
    }

    @Transactional
    public void changeRank(UUID actorId, boolean canManage, UUID unitId, UUID userId, UUID rankId) {
        OrgUnit unit = unit(unitId);
        OrgMembership m = membership(userId, unitId);
        OrgRank rank = rank(rankId);
        requireCanAssign(actorId, canManage, unitId, userId, rank, rank(m.getRankId()));
        m.setRankId(rank.getId());
        forget();
        audit.record("org.member.rank_changed", "user", userId, unit.getName() + " as " + rank.getKey());
    }

    @Transactional
    public void removeMember(UUID actorId, boolean canManage, UUID unitId, UUID userId) {
        OrgUnit unit = unit(unitId);
        OrgMembership m = membership(userId, unitId);
        OrgRank current = rank(m.getRankId());
        requireCanAssign(actorId, canManage, unitId, userId, current, current);
        memberships.delete(m);
        forget();
        audit.record("org.member.removed", "user", userId, unit.getName());
    }

    private void requireCanAssign(UUID actorId, boolean canManage, UUID unitId, UUID personId, OrgRank newRank,
                                  OrgRank currentRank) {
        if (canManage) return;
        if (personId.equals(actorId)) {
            throw ApiException.forbidden("own_placement", "You cannot change your own placement");
        }
        Integer lead = leadLevel(actorId, unitId);
        if (lead == null) throw ApiException.forbidden("not_leader", "You do not lead this unit");
        if (newRank.getLevel() >= lead || (currentRank != null && currentRank.getLevel() >= lead)) {
            throw ApiException.forbidden("rank_too_high", "You can only assign ranks below your own");
        }
    }

    // ------------------------------------------------------------------ units (org:manage)

    @Transactional
    public UnitDto createUnit(UnitRequest req) {
        if (req.parentId() == null) throw ApiException.badRequest("parent_required", "A unit needs a parent");
        unit(req.parentId());
        OrgUnit u = new OrgUnit();
        u.setParentId(req.parentId());
        u.setName(req.name().trim());
        u.setSortOrder(req.sortOrder() == null ? 0 : req.sortOrder());
        units.save(u);
        audit.record("org.unit.created", "org_unit", u.getId(), u.getName());
        return UnitDto.from(u);
    }

    @Transactional
    public UnitDto updateUnit(UUID id, UnitRequest req) {
        OrgUnit u = unit(id);
        if (u.isRoot() && req.parentId() != null) {
            throw ApiException.badRequest("root_fixed", "The organisation itself cannot be moved");
        }
        if (!u.isRoot()) {
            if (req.parentId() == null) throw ApiException.badRequest("parent_required", "A unit needs a parent");
            if (!req.parentId().equals(u.getParentId())) {
                unit(req.parentId());
                if (new HashSet<>(units.subtreeIds(id)).contains(req.parentId())) {
                    throw ApiException.badRequest("cycle", "A unit cannot move under itself");
                }
                u.setParentId(req.parentId());
                forget();
                audit.record("org.unit.moved", "org_unit", id, u.getName());
            }
        }
        u.setName(req.name().trim());
        if (req.sortOrder() != null) u.setSortOrder(req.sortOrder());
        return UnitDto.from(u);
    }

    @Transactional
    public void deleteUnit(UUID id) {
        OrgUnit u = unit(id);
        if (u.isRoot()) throw ApiException.badRequest("root_fixed", "The organisation itself cannot be deleted");
        if (units.existsByParentId(id)) throw ApiException.conflict("unit_has_children", "Delete or move its sub-units first");
        if (memberships.existsByUnitId(id)) throw ApiException.conflict("unit_has_members", "Remove its members first");
        units.delete(u);
        audit.record("org.unit.deleted", "org_unit", id, u.getName());
    }

    // ------------------------------------------------------------------ ranks (org:manage)

    @Transactional
    public RankDto createRank(RankRequest req) {
        if (ranks.existsByKey(req.key())) throw ApiException.conflict("rank_exists", "A rank with this key exists");
        OrgRank r = new OrgRank();
        r.setKey(req.key());
        apply(r, req);
        ranks.save(r);
        audit.record("org.rank.created", "org_rank", r.getId(), r.getKey() + " " + r.getLevel());
        return RankDto.from(r);
    }

    @Transactional
    public RankDto updateRank(UUID id, RankRequest req) {
        OrgRank r = rank(id);
        if (!r.getKey().equals(req.key()) && ranks.existsByKey(req.key())) {
            throw ApiException.conflict("rank_exists", "A rank with this key exists");
        }
        r.setKey(req.key());
        apply(r, req);
        forget();
        audit.record("org.rank.updated", "org_rank", id, r.getKey() + " " + r.getLevel());
        return RankDto.from(r);
    }

    @Transactional
    public void deleteRank(UUID id) {
        OrgRank r = rank(id);
        if (memberships.existsByRankId(id)) throw ApiException.conflict("rank_in_use", "Someone still holds this rank");
        ranks.delete(r);
        audit.record("org.rank.deleted", "org_rank", id, r.getKey());
    }

    private static void apply(OrgRank r, RankRequest req) {
        r.setLevel(req.level());
        r.setLabels(new HashMap<>(req.labels()));
        r.setAliases(req.aliases() == null ? new String[0]
                : req.aliases().stream().map(String::trim).filter(s -> !s.isEmpty()).distinct().toArray(String[]::new));
        r.setSupervises(req.supervises());
    }

    // ------------------------------------------------------------------ lookups

    private OrgUnit unit(UUID id) {
        return units.findById(id).orElseThrow(() -> ApiException.notFound("Unit"));
    }

    private OrgRank rank(UUID id) {
        return ranks.findById(id).orElseThrow(() -> ApiException.notFound("Rank"));
    }

    private OrgMembership membership(UUID userId, UUID unitId) {
        return memberships.findById(new OrgMembership.Key(userId, unitId))
                .orElseThrow(() -> ApiException.notFound("Membership"));
    }
}
