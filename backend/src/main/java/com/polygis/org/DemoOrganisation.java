package com.polygis.org;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.polygis.org.OrgDtos.MemberRequest;
import com.polygis.org.OrgDtos.UnitRequest;
import com.polygis.user.UserAdminService;
import com.polygis.user.UserRepository;
import com.polygis.user.UserStatus;
import com.polygis.user.dto.UserDtos.RoleRequest;
import com.polygis.user.dto.UserDtos.UpdateUserRequest;

/**
 * The example organisation from the docs, for demo data. Runs inside the tenant (TenantContext set by the
 * caller) and only when the tenant has no units yet. People are looked up by e-mail prefix and must exist.
 * <pre>
 * root            alice director 900
 * ├── Engineering bob head 500
 * │   ├── Platform carol chief 100 · dave deputy 80 · erin member 10
 * │   └── Mobile   frank chief 100 · gina member 10
 * └── Sales       hana head 500 · ivan coordinator 50
 * </pre>
 * Leaders also get a SUPERVISOR role with {@code org:impersonate}, so "sign in as" can be tried.
 */
@Component
public class DemoOrganisation {

    public static final String SUPERVISOR_ROLE = "SUPERVISOR";

    private final OrgService org;
    private final OrgUnitRepository units;
    private final OrgRankRepository ranks;
    private final UserRepository users;
    private final UserAdminService userAdmin;
    private final TransactionTemplate tx;

    public DemoOrganisation(OrgService org, OrgUnitRepository units, OrgRankRepository ranks, UserRepository users,
                            UserAdminService userAdmin, TransactionTemplate tx) {
        this.org = org;
        this.units = units;
        this.ranks = ranks;
        this.users = users;
        this.userAdmin = userAdmin;
        this.tx = tx;
    }

    public void seed(String emailDomain) {
        tx.executeWithoutResult(s -> {
            UUID root = units.findRoot().orElseThrow().getId();
            if (units.existsByParentId(root)) return;
            Map<String, UUID> rank = new HashMap<>();
            ranks.findAll().forEach(r -> rank.put(r.getKey(), r.getId()));
            UUID eng = org.createUnit(new UnitRequest("Engineering", root, 1)).id();
            UUID platform = org.createUnit(new UnitRequest("Platform team", eng, 1)).id();
            UUID mobile = org.createUnit(new UnitRequest("Mobile team", eng, 2)).id();
            UUID sales = org.createUnit(new UnitRequest("Sales", root, 2)).id();

            place("alice", emailDomain, root, rank.get("director"));
            place("bob", emailDomain, eng, rank.get("head"));
            place("carol", emailDomain, platform, rank.get("chief"));
            place("dave", emailDomain, platform, rank.get("deputy"));
            place("erin", emailDomain, platform, rank.get("member"));
            place("frank", emailDomain, mobile, rank.get("chief"));
            place("gina", emailDomain, mobile, rank.get("member"));
            place("hana", emailDomain, sales, rank.get("head"));
            place("ivan", emailDomain, sales, rank.get("coordinator"));

            if (userAdmin.roles().stream().noneMatch(r -> SUPERVISOR_ROLE.equals(r.name()))) {
                userAdmin.createRole(new RoleRequest(SUPERVISOR_ROLE, "Leads people: can sign in as them",
                        Set.of("org:read", "org:impersonate", "project:read", "project:write")));
            }
            for (String leader : new String[] {"alice", "bob", "carol", "frank", "hana"}) {
                users.findByEmailIgnoreCase(leader + "@" + emailDomain).ifPresent(u -> userAdmin.update(u.getId(),
                        new UpdateUserRequest(u.getFirstName(), u.getLastName(), UserStatus.ACTIVE,
                                Set.of("USER", SUPERVISOR_ROLE))));
            }
        });
    }

    private void place(String name, String domain, UUID unit, UUID rankId) {
        users.findByEmailIgnoreCase(name + "@" + domain).ifPresent(u ->
                org.addMember(null, true, unit, new MemberRequest(u.getId(), rankId)));
    }
}
