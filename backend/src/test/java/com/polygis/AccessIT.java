package com.polygis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.polygis.access.AccessFilter;
import com.polygis.access.RelationSource;
import com.polygis.access.RelationTuple;
import com.polygis.access.ResourceRef;
import com.polygis.access.ResourceTypeDefinition;
import com.polygis.access.SubjectRef;
import com.polygis.support.IntegrationTestBase;
import com.polygis.support.OrgFixture;

@Import(AccessIT.ExternalTasks.class)
class AccessIT extends IntegrationTestBase {

    /** Stand-in for a workflow engine that keeps its own assignments (assignee, candidate groups). */
    @TestConfiguration
    static class ExternalTasks {
        static final Map<String, List<RelationTuple>> TASKS = new HashMap<>();

        @Bean
        ResourceTypeDefinition externalTaskType() {
            return ResourceTypeDefinition.of("ext.task")
                    .action("read", "assignee", "candidate")
                    .action("complete", "assignee", "candidate")
                    .supervisorActions("read");
        }

        @Bean
        RelationSource externalTasks() {
            return new RelationSource() {
                public boolean supports(String type) {
                    return "ext.task".equals(type);
                }

                public List<RelationTuple> relationsOf(ResourceRef r) {
                    return TASKS.getOrDefault(r.id(), List.of());
                }

                public Optional<List<String>> visibleIds(String type, AccessFilter f) {
                    return Optional.of(TASKS.entrySet().stream()
                            .filter(e -> e.getValue().stream().anyMatch(f::matches)).map(Map.Entry::getKey).toList());
                }
            };
        }
    }

    @Test
    void ownerSupervisorsAndStrangers() throws Exception {
        OrgFixture org = new OrgFixture(this, "acc-own");
        String id = create(org, "erin", Map.of("name", "Erin's plan"));

        read(org, "erin", id).andExpect(status().isOk()).andExpect(jsonPath("$.owners[0].name").value("erin"));
        read(org, "carol", id).andExpect(status().isOk());   // supervises erin
        read(org, "alice", id).andExpect(status().isOk());   // director at the root
        read(org, "frank", id).andExpect(status().isNotFound()); // other team: doesn't even learn it exists
        read(org, "dan", id).andExpect(status().isOk());

        // supervision is read-only
        update(org, "carol", id).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("access_denied"));
        update(org, "erin", id).andExpect(status().isOk());
        mvc.perform(get("/api/access/project/" + id).with(host(org.host)).header("Authorization", bearer(org.token("carol"))))
                .andExpect(jsonPath("$.can.read").value(true)).andExpect(jsonPath("$.can.write").value(false))
                .andExpect(jsonPath("$.can.share").value(false));

        // scopes
        assertThat(names(org, "carol", "MINE")).isEmpty();
        assertThat(names(org, "carol", "TEAM")).containsExactly("Renamed");
        list(org, "carol", "ALL").andExpect(status().isForbidden());
        assertThat(namesWith(org.adminToken, org, "ALL")).containsExactly("Renamed");
    }

    @Test
    void unitsAndGroupsOwnTogether() throws Exception {
        OrgFixture org = new OrgFixture(this, "acc-unit");
        String platform = create(org, "carol", Map.of("name", "Platform roadmap", "ownerKind", "UNIT",
                "ownerId", org.unitIds.get("Platform")));
        String eng = create(org, "bob", Map.of("name", "Engineering budget", "ownerKind", "UNIT",
                "ownerId", org.unitIds.get("Engineering")));

        read(org, "erin", platform).andExpect(status().isOk());   // member of the owning unit
        read(org, "bob", platform).andExpect(status().isOk());    // leads the subtree
        read(org, "frank", platform).andExpect(status().isNotFound());
        read(org, "erin", eng).andExpect(status().isOk());        // owned higher up: SUBTREE inheritance
        read(org, "hana", eng).andExpect(status().isNotFound());

        // nobody owns on behalf of a unit they're not in
        mvc.perform(jsonPost("/api/projects", Map.of("name", "x", "ownerKind", "UNIT", "ownerId", org.unitIds.get("Sales")))
                        .with(host(org.host)).header("Authorization", bearer(org.token("erin"))))
                .andExpect(status().isForbidden());

        // a household: a group owns it, its members use it
        String erin = org.token("erin");
        String household = body(mvc.perform(jsonPost("/api/groups", Map.of("name", "Smith household", "kind", "household"))
                .with(host(org.host)).header("Authorization", bearer(erin))).andExpect(status().isCreated()).andReturn())
                .get("id").asText();
        mvc.perform(put("/api/groups/" + household + "/members").with(host(org.host)).header("Authorization", bearer(erin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("userId", org.userIds.get("frank")))))
                .andExpect(status().isOk());
        String car = create(org, "erin", Map.of("name", "Family car", "ownerKind", "GROUP", "ownerId", household));
        read(org, "frank", car).andExpect(status().isOk());
        read(org, "gina", car).andExpect(status().isNotFound());
        // frank is a plain member: he can't change the group
        mvc.perform(delete("/api/groups/" + household).with(host(org.host)).header("Authorization", bearer(org.token("frank"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void sharingRevokingAndTransfer() throws Exception {
        OrgFixture org = new OrgFixture(this, "acc-share");
        String id = create(org, "erin", Map.of("name", "Shared"));
        String gina = org.userIds.get("gina");

        relation(org, "erin", id, "viewer", gina, false).andExpect(status().isNoContent());
        read(org, "gina", id).andExpect(status().isOk());
        update(org, "gina", id).andExpect(status().isForbidden());
        relation(org, "erin", id, "editor", gina, false).andExpect(status().isNoContent());
        update(org, "gina", id).andExpect(status().isOk());
        // only owners share
        relation(org, "gina", id, "viewer", org.userIds.get("ivan"), false).andExpect(status().isForbidden());
        relation(org, "erin", id, "boss", gina, false).andExpect(status().isBadRequest());
        // the last owner can't be removed, only replaced
        relation(org, "erin", id, "owner", org.userIds.get("erin"), true).andExpect(status().isConflict());
        mvc.perform(jsonPost("/api/access/project/" + id + "/transfer",
                        Map.of("subjectKind", "USER", "subjectId", org.userIds.get("ivan")))
                        .with(host(org.host)).header("Authorization", bearer(org.token("erin"))))
                .andExpect(status().isNoContent());
        read(org, "ivan", id).andExpect(jsonPath("$.owners[0].name").value("ivan"));
        read(org, "erin", id).andExpect(status().isNotFound()); // erin gave it away
        mvc.perform(get("/api/admin/audit").param("q", "access.transferred").with(host(org.host))
                        .header("Authorization", bearer(org.adminToken)))
                .andExpect(jsonPath("$.content[0].targetId").value(id));
    }

    @Test
    void externalSourcesPlugInUnchanged() throws Exception {
        OrgFixture org = new OrgFixture(this, "acc-ext");
        ResourceRef task = new ResourceRef("ext.task", "42");
        ExternalTasks.TASKS.put("42", List.of(
                new RelationTuple(task, "assignee", SubjectRef.user(java.util.UUID.fromString(org.userIds.get("erin")))),
                new RelationTuple(task, "candidate", SubjectRef.unit(java.util.UUID.fromString(org.unitIds.get("Mobile"))))));

        access(org, "erin").andExpect(jsonPath("$.can.complete").value(true));
        access(org, "gina").andExpect(jsonPath("$.can.complete").value(true));   // candidate through her unit
        access(org, "carol").andExpect(jsonPath("$.can.read").value(true))       // supervises erin
                .andExpect(jsonPath("$.can.complete").value(false));
        access(org, "hana").andExpect(status().isNotFound());
        // relations of external types are managed by their own system
        mvc.perform(jsonPost("/api/access/ext.task/42/relations", Map.of("relation", "assignee", "subjectKind", "USER",
                        "subjectId", org.userIds.get("hana"))).with(host(org.host)).header("Authorization", bearer(org.token("erin"))))
                .andExpect(status().isForbidden()); // erin can't share: no relation grants "share" on tasks
    }

    // ------------------------------------------------------------------ helpers

    private String create(OrgFixture org, String who, Map<String, Object> body) throws Exception {
        return body(mvc.perform(jsonPost("/api/projects", body).with(host(org.host))
                .header("Authorization", bearer(org.token(who)))).andExpect(status().isCreated()).andReturn())
                .get("id").asText();
    }

    private ResultActions read(OrgFixture org, String who, String id) throws Exception {
        return mvc.perform(get("/api/projects/" + id).with(host(org.host)).header("Authorization", bearer(org.token(who))));
    }

    private ResultActions update(OrgFixture org, String who, String id) throws Exception {
        return mvc.perform(put("/api/projects/" + id).with(host(org.host)).header("Authorization", bearer(org.token(who)))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("name", "Renamed"))));
    }

    private ResultActions list(OrgFixture org, String who, String scope) throws Exception {
        return mvc.perform(get("/api/projects").param("scope", scope).with(host(org.host))
                .header("Authorization", bearer(org.token(who))));
    }

    private List<String> names(OrgFixture org, String who, String scope) throws Exception {
        return namesWith(org.token(who), org, scope);
    }

    private List<String> namesWith(String token, OrgFixture org, String scope) throws Exception {
        var content = body(mvc.perform(get("/api/projects").param("scope", scope).with(host(org.host))
                .header("Authorization", bearer(token))).andExpect(status().isOk()).andReturn()).get("content");
        return StreamSupport.stream(content.spliterator(), false).map(n -> n.get("name").asText()).toList();
    }

    private ResultActions relation(OrgFixture org, String who, String id, String relation, String userId,
                                   boolean remove) throws Exception {
        var body = Map.of("relation", relation, "subjectKind", "USER", "subjectId", userId);
        var req = remove
                ? delete("/api/access/project/" + id + "/relations").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body))
                : jsonPost("/api/access/project/" + id + "/relations", body);
        return mvc.perform(req.with(host(org.host)).header("Authorization", bearer(org.token(who))));
    }

    private ResultActions access(OrgFixture org, String who) throws Exception {
        return mvc.perform(get("/api/access/ext.task/42").with(host(org.host)).header("Authorization", bearer(org.token(who))));
    }
}
