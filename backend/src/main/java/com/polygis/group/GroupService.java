package com.polygis.group;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.polygis.audit.AuditService;
import com.polygis.common.ApiException;
import com.polygis.group.GroupDtos.*;
import com.polygis.user.User;
import com.polygis.user.UserRepository;

/**
 * Groups are managed by holders of {@code group:manage} and by the group's own admins. A group always keeps at
 * least one admin.
 */
@Service
public class GroupService {

    private final UserGroupRepository groups;
    private final UserGroupMemberRepository members;
    private final UserRepository users;
    private final AuditService audit;

    public GroupService(UserGroupRepository groups, UserGroupMemberRepository members, UserRepository users,
                        AuditService audit) {
        this.groups = groups;
        this.members = members;
        this.users = users;
        this.audit = audit;
    }

    /** Every group for managers; otherwise the groups the caller belongs to. */
    @Transactional(readOnly = true)
    public List<GroupDto> list(UUID callerId, boolean canManageAll) {
        List<UserGroup> list = canManageAll ? groups.findAllByOrderByNameAsc() : groups.findForMember(callerId);
        return list.stream().map(g -> dto(g, callerId, canManageAll)).toList();
    }

    @Transactional
    public GroupDto create(UUID callerId, GroupRequest req) {
        UserGroup g = new UserGroup();
        g.setName(req.name().trim());
        if (req.kind() != null) g.setKind(req.kind());
        groups.save(g);
        UserGroupMember admin = new UserGroupMember();
        admin.setGroupId(g.getId());
        admin.setUserId(callerId);
        admin.setRole(UserGroupMember.ADMIN);
        members.save(admin);
        audit.record("group.created", "group", g.getId(), g.getKind() + " " + g.getName());
        return dto(g, callerId, true);
    }

    @Transactional
    public GroupDto update(UUID callerId, boolean canManageAll, UUID id, GroupRequest req) {
        UserGroup g = requireManageable(callerId, canManageAll, id);
        g.setName(req.name().trim());
        if (req.kind() != null) g.setKind(req.kind());
        audit.record("group.updated", "group", id, g.getName());
        return dto(g, callerId, canManageAll);
    }

    @Transactional
    public void delete(UUID callerId, boolean canManageAll, UUID id) {
        UserGroup g = requireManageable(callerId, canManageAll, id);
        groups.delete(g);
        audit.record("group.deleted", "group", id, g.getName());
    }

    @Transactional
    public GroupDto putMember(UUID callerId, boolean canManageAll, UUID id, GroupMemberRequest req) {
        UserGroup g = requireManageable(callerId, canManageAll, id);
        users.findById(req.userId()).orElseThrow(() -> ApiException.notFound("User"));
        UserGroupMember m = members.findById(new UserGroupMember.Key(id, req.userId())).orElseGet(() -> {
            UserGroupMember n = new UserGroupMember();
            n.setGroupId(id);
            n.setUserId(req.userId());
            return n;
        });
        String role = req.role() == null ? UserGroupMember.MEMBER : req.role();
        if (UserGroupMember.ADMIN.equals(m.getRole()) && !UserGroupMember.ADMIN.equals(role)) requireAnotherAdmin(id);
        m.setRole(role);
        members.save(m);
        audit.record("group.member.set", "user", req.userId(), g.getName() + " as " + role);
        return dto(g, callerId, canManageAll);
    }

    @Transactional
    public GroupDto removeMember(UUID callerId, boolean canManageAll, UUID id, UUID userId) {
        UserGroup g = requireManageable(callerId, canManageAll, id);
        UserGroupMember m = members.findById(new UserGroupMember.Key(id, userId))
                .orElseThrow(() -> ApiException.notFound("Member"));
        if (UserGroupMember.ADMIN.equals(m.getRole())) requireAnotherAdmin(id);
        members.delete(m);
        audit.record("group.member.removed", "user", userId, g.getName());
        return dto(g, callerId, canManageAll);
    }

    private void requireAnotherAdmin(UUID groupId) {
        if (members.countByGroupIdAndRole(groupId, UserGroupMember.ADMIN) <= 1) {
            throw ApiException.conflict("last_admin", "A group needs at least one admin");
        }
    }

    private UserGroup requireManageable(UUID callerId, boolean canManageAll, UUID id) {
        UserGroup g = groups.findById(id).orElseThrow(() -> ApiException.notFound("Group"));
        if (!canManageAll && !isAdmin(callerId, id)) {
            throw ApiException.forbidden("not_group_admin", "Only the group's admins can change it");
        }
        return g;
    }

    private boolean isAdmin(UUID callerId, UUID groupId) {
        return members.findById(new UserGroupMember.Key(groupId, callerId))
                .map(m -> UserGroupMember.ADMIN.equals(m.getRole())).orElse(false);
    }

    private GroupDto dto(UserGroup g, UUID callerId, boolean canManageAll) {
        List<UserGroupMember> list = members.findByGroupId(g.getId());
        Map<UUID, User> people = users.findAllById(list.stream().map(UserGroupMember::getUserId).toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        List<GroupMemberDto> dtos = list.stream().filter(m -> people.containsKey(m.getUserId()))
                .map(m -> new GroupMemberDto(m.getUserId(), people.get(m.getUserId()).getEmail(),
                        people.get(m.getUserId()).displayName(), m.getRole()))
                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name())).toList();
        boolean canManage = canManageAll || list.stream().anyMatch(m -> m.getUserId().equals(callerId)
                && UserGroupMember.ADMIN.equals(m.getRole()));
        return new GroupDto(g.getId(), g.getKind(), g.getName(), dtos, canManage);
    }
}
