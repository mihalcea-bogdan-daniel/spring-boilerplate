package com.polygis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.util.UriComponentsBuilder;

import com.polygis.support.IntegrationTestBase;
import com.polygis.support.OrgFixture;

class TeamImpersonationIT extends IntegrationTestBase {

    /** carol (chief of Platform) gets org:impersonate through a SUPERVISOR role. */
    private OrgFixture fixture(String slug) throws Exception {
        OrgFixture org = new OrgFixture(this, slug);
        mvc.perform(jsonPost("/api/admin/roles", Map.of("name", "SUPERVISOR",
                        "permissions", List.of("org:read", "org:impersonate")))
                .with(host(org.host)).header("Authorization", bearer(org.adminToken))).andExpect(status().isCreated());
        setRoles(org, "carol", List.of("USER", "SUPERVISOR"));
        return org;
    }

    @Test
    void supervisorSignsInAsSubordinateWithAnAuditedRestrictedSession() throws Exception {
        OrgFixture org = fixture("team-imp");
        String carol = org.token("carol");

        var res = mvc.perform(post("/api/me/team/" + org.userIds.get("erin") + "/impersonate").with(host(org.host))
                .header("Authorization", bearer(carol))).andExpect(status().isOk()).andReturn();
        var url = UriComponentsBuilder.fromUriString(body(res).get("url").asText()).build();
        assertThat(url.getHost()).isEqualTo(org.host);
        String code = url.getQueryParams().getFirst("impersonate");

        String erin = accessToken(mvc.perform(jsonPost("/api/auth/impersonation/exchange", Map.of("code", code))
                        .with(host(org.host)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(org.email("erin")))
                .andExpect(jsonPath("$.user.impersonatedBy").value(org.email("carol"))).andReturn());

        // credentials, sessions and further impersonation are off limits
        mvc.perform(put("/api/me/password").with(host(org.host)).header("Authorization", bearer(erin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("newPassword", "Hijacked-12345"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("impersonated"));
        mvc.perform(delete("/api/me/sessions").with(host(org.host)).header("Authorization", bearer(erin)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("impersonated"));

        // erin sees who signed in as her
        mvc.perform(get("/api/me/access-log").with(host(org.host)).header("Authorization", bearer(org.token("erin"))))
                .andExpect(jsonPath("$.content[0].action").value("org.impersonation.started"))
                .andExpect(jsonPath("$.content[0].details").value(org.email("carol")));
    }

    @Test
    void guardsRefuseEverythingElse() throws Exception {
        OrgFixture org = fixture("team-guard");
        String carol = org.token("carol");

        // not supervised: frank is in another team
        impersonate(org, carol, "frank").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("not_supervised"));
        // no org:impersonate: dan supervises erin but holds only USER
        impersonate(org, org.token("dan"), "erin").andExpect(status().isForbidden());
        // escalation: erin holding a permission carol lacks
        setRoles(org, "erin", List.of("USER", "TENANT_ADMIN"));
        impersonate(org, carol, "erin").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("privilege_escalation"));
        setRoles(org, "erin", List.of("USER"));
        // mutual: dan also leads a unit where carol is a member
        org.unit("Guild", "root");
        org.place("dan", "Guild", "chief");
        org.place("carol", "Guild", "member");
        impersonate(org, carol, "dan").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("mutual_supervision"));
        // self
        impersonate(org, carol, "carol").andExpect(status().isBadRequest());
    }

    @Test
    void supervisorSeesTheTeamAndItsActivity() throws Exception {
        OrgFixture org = fixture("team-view");
        mvc.perform(put("/api/me").with(host(org.host)).header("Authorization", bearer(org.token("erin")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("firstName", "Erin", "lastName", "E"))))
                .andExpect(status().isOk());
        String carol = org.token("carol");
        mvc.perform(get("/api/me/team").with(host(org.host)).header("Authorization", bearer(carol)))
                .andExpect(jsonPath("$[1].email").value(org.email("erin")))
                .andExpect(jsonPath("$[1].placements[0].unitName").value("Platform"))
                .andExpect(jsonPath("$[1].placements[0].level").value(10));
        mvc.perform(get("/api/me/team/activity").with(host(org.host)).header("Authorization", bearer(carol)))
                .andExpect(status().isOk())
                .andExpect(r -> assertThat(body(r).get("content").findValuesAsText("actorEmail"))
                        .allMatch(e -> Set.of(org.email("dan"), org.email("erin")).contains(e)));
        // frank's activity is not carol's business
        mvc.perform(get("/api/me/team").with(host(org.host)).header("Authorization", bearer(org.token("erin"))))
                .andExpect(jsonPath("$.length()").value(0));
    }

    private org.springframework.test.web.servlet.ResultActions impersonate(OrgFixture org, String token, String who)
            throws Exception {
        return mvc.perform(post("/api/me/team/" + org.userIds.get(who) + "/impersonate").with(host(org.host))
                .header("Authorization", bearer(token)));
    }

    private void setRoles(OrgFixture org, String name, List<String> roles) throws Exception {
        mvc.perform(put("/api/admin/users/" + org.userIds.get(name)).with(host(org.host))
                        .header("Authorization", bearer(org.adminToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("firstName", name, "status", "ACTIVE", "roles", roles))))
                .andExpect(status().isOk());
    }
}
