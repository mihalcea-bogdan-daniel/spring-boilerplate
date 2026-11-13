package com.polygis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.polygis.support.IntegrationTestBase;
import com.polygis.support.OrgFixture;

class OrgIT extends IntegrationTestBase {

    @Test
    void supervisionFollowsLevelsDownTheTree() throws Exception {
        OrgFixture org = new OrgFixture(this, "org-rule");

        assertThat(org.teamOf("alice")).containsExactly("bob", "carol", "dan", "erin", "frank", "gina", "hana", "ivan");
        assertThat(org.teamOf("bob")).containsExactly("carol", "dan", "erin", "frank", "gina");
        assertThat(org.teamOf("carol")).containsExactly("dan", "erin");
        assertThat(org.teamOf("dan")).containsExactly("erin");
        assertThat(org.teamOf("erin")).isEmpty();                 // members don't supervise
        assertThat(org.teamOf("hana")).containsExactly("ivan");

        // a second placement is enough: gina also works in Platform, so carol supervises her
        org.place("gina", "Platform", "member");
        assertThat(org.teamOf("carol")).containsExactly("dan", "erin", "gina");

        // equal levels are peers: another chief in Platform is not carol's subordinate
        org.place("kim", "Platform", "chief");
        assertThat(org.teamOf("carol")).containsExactly("dan", "erin", "gina");
        assertThat(org.teamOf("bob")).contains("kim");
    }

    @Test
    void disabledPeopleNeitherSuperviseNorCount() throws Exception {
        OrgFixture org = new OrgFixture(this, "org-disabled");
        disable(org, "erin");
        assertThat(org.teamOf("carol")).containsExactly("dan");

        String carolToken = org.token("carol"); // still signed in when she gets disabled
        disable(org, "carol");
        // whatever her old token still allows, it no longer carries any supervision
        mvc.perform(get("/api/me/team").with(host(org.host)).header("Authorization", bearer(carolToken)))
                .andExpect(r -> assertThat(r.getResponse().getStatus() == 401
                        || "[]".equals(r.getResponse().getContentAsString())).isTrue());
        assertThat(org.teamOf("bob")).containsExactly("dan", "frank", "gina");
    }

    @Test
    void chartShowsTheTreeAndWhereTheCallerLeads() throws Exception {
        OrgFixture org = new OrgFixture(this, "org-chart");
        mvc.perform(get("/api/org/chart").with(host(org.host)).header("Authorization", bearer(org.token("carol"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leadLevel").doesNotExist())
                .andExpect(jsonPath("$.members[0].email").value(org.email("alice")))
                .andExpect(jsonPath("$.children[0].name").value("Engineering"))
                .andExpect(jsonPath("$.children[0].children[1].name").value("Platform"))
                .andExpect(jsonPath("$.children[0].children[1].leadLevel").value(100))
                .andExpect(jsonPath("$.children[0].children[1].members[0].email").value(org.email("carol")))
                .andExpect(jsonPath("$.children[0].children[0].leadLevel").doesNotExist());
    }

    @Test
    void leadersManageTheirUnitsOnlyBelowTheirOwnRank() throws Exception {
        OrgFixture org = new OrgFixture(this, "org-lead");
        String carol = org.token("carol");
        String platform = org.unitIds.get("Platform");
        String newcomer = org.user("lena", java.util.Set.of("USER"));

        // carol (chief 100) may add a member and promote them to deputy (80)
        mvc.perform(jsonPost("/api/org/units/" + platform + "/members",
                        Map.of("userId", newcomer, "rankId", org.rankIds.get("member")))
                        .with(host(org.host)).header("Authorization", bearer(carol)))
                .andExpect(status().isNoContent());
        mvc.perform(put("/api/org/units/" + platform + "/members/" + newcomer).with(host(org.host))
                        .header("Authorization", bearer(carol)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("rankId", org.rankIds.get("deputy")))))
                .andExpect(status().isNoContent());
        // ...but not to her own level, not in another team, and not herself
        mvc.perform(put("/api/org/units/" + platform + "/members/" + newcomer).with(host(org.host))
                        .header("Authorization", bearer(carol)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("rankId", org.rankIds.get("chief")))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("rank_too_high"));
        mvc.perform(delete("/api/org/units/" + org.unitIds.get("Mobile") + "/members/" + org.userIds.get("gina"))
                        .with(host(org.host)).header("Authorization", bearer(carol)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/org/units/" + platform + "/members/" + org.userIds.get("carol"))
                        .with(host(org.host)).header("Authorization", bearer(carol)))
                .andExpect(status().isForbidden());
        // bob leads Engineering and therefore every team below it
        mvc.perform(delete("/api/org/units/" + platform + "/members/" + newcomer)
                        .with(host(org.host)).header("Authorization", bearer(org.token("bob"))))
                .andExpect(status().isNoContent());
        // restructuring needs org:manage
        mvc.perform(jsonPost("/api/admin/org/units", Map.of("name", "X", "parentId", platform))
                        .with(host(org.host)).header("Authorization", bearer(carol)))
                .andExpect(status().isForbidden());
    }

    @Test
    void treeStaysATreeAndTheRootStays() throws Exception {
        OrgFixture org = new OrgFixture(this, "org-tree");
        String eng = org.unitIds.get("Engineering");
        String root = org.unitIds.get("root");

        mvc.perform(put("/api/admin/org/units/" + eng).with(host(org.host)).header("Authorization", bearer(org.adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Engineering", "parentId", org.unitIds.get("Platform")))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("cycle"));
        mvc.perform(delete("/api/admin/org/units/" + root).with(host(org.host)).header("Authorization", bearer(org.adminToken)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("root_fixed"));
        mvc.perform(delete("/api/admin/org/units/" + eng).with(host(org.host)).header("Authorization", bearer(org.adminToken)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("unit_has_children"));
        mvc.perform(delete("/api/admin/org/ranks/" + org.rankIds.get("chief")).with(host(org.host))
                        .header("Authorization", bearer(org.adminToken)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("rank_in_use"));

        // moving Mobile under Sales hands it to hana
        mvc.perform(put("/api/admin/org/units/" + org.unitIds.get("Mobile")).with(host(org.host))
                        .header("Authorization", bearer(org.adminToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Mobile", "parentId", org.unitIds.get("Sales")))))
                .andExpect(status().isOk());
        assertThat(org.teamOf("hana")).containsExactly("frank", "gina", "ivan");
        assertThat(org.teamOf("bob")).isEqualTo(List.of("carol", "dan", "erin"));

        mvc.perform(get("/api/admin/audit").param("q", "org.unit.moved").with(host(org.host))
                        .header("Authorization", bearer(org.adminToken)))
                .andExpect(jsonPath("$.content[0].action").value("org.unit.moved"));
    }

    @Test
    void ranksAreATenantCatalogue() throws Exception {
        OrgFixture org = new OrgFixture(this, "org-ranks");
        var created = mvc.perform(jsonPost("/api/admin/org/ranks", Map.of("key", "senior", "level", 300,
                        "labels", Map.of("en", "Senior manager", "ro", "Manager senior"),
                        "aliases", List.of("Senior"), "supervises", true))
                        .with(host(org.host)).header("Authorization", bearer(org.adminToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.labels.ro").value("Manager senior")).andReturn();
        mvc.perform(get("/api/org/ranks").with(host(org.host)).header("Authorization", bearer(org.token("erin"))))
                .andExpect(jsonPath("$[2].key").value("senior"));
        mvc.perform(delete("/api/admin/org/ranks/" + body(created).get("id").asText()).with(host(org.host))
                        .header("Authorization", bearer(org.adminToken)))
                .andExpect(status().isNoContent());
    }

    private void disable(OrgFixture org, String name) throws Exception {
        mvc.perform(put("/api/admin/users/" + org.userIds.get(name)).with(host(org.host))
                        .header("Authorization", bearer(org.adminToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("status", "DISABLED", "roles", List.of("USER")))))
                .andExpect(status().isOk());
    }
}
