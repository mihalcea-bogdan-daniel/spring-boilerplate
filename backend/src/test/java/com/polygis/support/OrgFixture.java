package com.polygis.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Builds the example organisation from the docs in a fresh tenant:
 * <pre>
 * root            alice director 900
 * ├── Engineering bob head 500
 * │   ├── Platform carol chief 100 · dan deputy 80 · erin member 10
 * │   └── Mobile   frank chief 100 · gina member 10
 * └── Sales       hana head 500 · ivan coordinator 50
 * </pre>
 * Every person has the password {@link #PASSWORD} and the USER role unless said otherwise.
 */
public class OrgFixture {

    public static final String PASSWORD = "Org-Passw0rd-1";

    private final IntegrationTestBase t;
    public final String slug;
    public final String host;
    public final String adminToken;
    public final Map<String, String> userIds = new HashMap<>();
    public final Map<String, String> unitIds = new LinkedHashMap<>();
    public final Map<String, String> rankIds = new HashMap<>();

    public OrgFixture(IntegrationTestBase test, String slug) throws Exception {
        this.t = test;
        this.slug = slug;
        this.host = IntegrationTestBase.hostOf(slug);
        String adminEmail = t.provisionTenant(slug);
        this.adminToken = t.accessToken(t.otpLogin(host, adminEmail));

        for (JsonNode r : t.body(t.mvc.perform(get("/api/org/ranks").with(IntegrationTestBase.host(host))
                .header("Authorization", IntegrationTestBase.bearer(adminToken))).andExpect(status().isOk()).andReturn())) {
            rankIds.put(r.get("key").asText(), r.get("id").asText());
        }
        JsonNode chart = t.body(t.mvc.perform(get("/api/org/chart").with(IntegrationTestBase.host(host))
                .header("Authorization", IntegrationTestBase.bearer(adminToken))).andExpect(status().isOk()).andReturn());
        unitIds.put("root", chart.get("id").asText());
        unit("Engineering", "root");
        unit("Platform", "Engineering");
        unit("Mobile", "Engineering");
        unit("Sales", "root");

        place("alice", "root", "director");
        place("bob", "Engineering", "head");
        place("carol", "Platform", "chief");
        place("dan", "Platform", "deputy");
        place("erin", "Platform", "member");
        place("frank", "Mobile", "chief");
        place("gina", "Mobile", "member");
        place("hana", "Sales", "head");
        place("ivan", "Sales", "coordinator");
    }

    public String email(String name) {
        return name + "@" + slug + ".test";
    }

    public String unit(String name, String parent) throws Exception {
        String id = t.body(t.mvc.perform(t.jsonPost("/api/admin/org/units",
                        Map.of("name", name, "parentId", unitIds.get(parent))).with(IntegrationTestBase.host(host))
                .header("Authorization", IntegrationTestBase.bearer(adminToken)))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
        unitIds.put(name, id);
        return id;
    }

    /** Creates the person if needed and gives them the rank in the unit. */
    public void place(String name, String unit, String rank) throws Exception {
        if (!userIds.containsKey(name)) user(name, Set.of("USER"));
        t.mvc.perform(t.jsonPost("/api/org/units/" + unitIds.get(unit) + "/members",
                        Map.of("userId", userIds.get(name), "rankId", rankIds.get(rank)))
                .with(IntegrationTestBase.host(host)).header("Authorization", IntegrationTestBase.bearer(adminToken)))
                .andExpect(status().isNoContent());
    }

    public String user(String name, Set<String> roles) throws Exception {
        String id = t.body(t.mvc.perform(t.jsonPost("/api/admin/users", Map.of("email", email(name),
                        "firstName", name, "password", PASSWORD, "roles", roles))
                .with(IntegrationTestBase.host(host)).header("Authorization", IntegrationTestBase.bearer(adminToken)))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
        userIds.put(name, id);
        return id;
    }

    public String token(String name) throws Exception {
        return t.accessToken(t.login(host, email(name), PASSWORD));
    }

    /** First names of the people {@code name} supervises, via GET /api/me/team. */
    public List<String> teamOf(String name) throws Exception {
        JsonNode team = t.body(t.mvc.perform(get("/api/me/team").with(IntegrationTestBase.host(host))
                .header("Authorization", IntegrationTestBase.bearer(token(name)))).andExpect(status().isOk()).andReturn());
        return java.util.stream.StreamSupport.stream(team.spliterator(), false)
                .map(n -> n.get("email").asText().split("@")[0]).sorted().toList();
    }
}
