package com.polygis.access;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.polygis.group.UserGroup;
import com.polygis.group.UserGroupRepository;
import com.polygis.org.OrgUnit;
import com.polygis.org.OrgUnitRepository;
import com.polygis.user.User;
import com.polygis.user.UserRepository;
import com.polygis.user.UserStatus;

/** Names for subjects, and the search behind "share with…". */
@Component
public class SubjectDirectory {

    public record SubjectDto(SubjectKind kind, UUID id, String name, String detail) {
    }

    private final UserRepository users;
    private final OrgUnitRepository units;
    private final UserGroupRepository groups;

    public SubjectDirectory(UserRepository users, OrgUnitRepository units, UserGroupRepository groups) {
        this.users = users;
        this.units = units;
        this.groups = groups;
    }

    /** Describes each subject; ones that no longer exist are named "(removed)". */
    @Transactional(readOnly = true)
    public Map<SubjectRef, SubjectDto> describe(Collection<SubjectRef> subjects) {
        Map<SubjectKind, List<UUID>> byKind = subjects.stream().collect(Collectors.groupingBy(SubjectRef::kind,
                Collectors.mapping(SubjectRef::id, Collectors.toList())));
        Map<SubjectRef, SubjectDto> result = new HashMap<>();
        users.findAllById(byKind.getOrDefault(SubjectKind.USER, List.of())).forEach(u ->
                result.put(SubjectRef.user(u.getId()), user(u)));
        units.findAllById(byKind.getOrDefault(SubjectKind.UNIT, List.of())).forEach(u ->
                result.put(SubjectRef.unit(u.getId()), unit(u)));
        groups.findAllById(byKind.getOrDefault(SubjectKind.GROUP, List.of())).forEach(g ->
                result.put(SubjectRef.group(g.getId()), group(g)));
        subjects.forEach(s -> result.putIfAbsent(s, new SubjectDto(s.kind(), s.id(), "(removed)", null)));
        return result;
    }

    /** People, units and groups whose name matches, a few of each. */
    @Transactional(readOnly = true)
    public List<SubjectDto> search(String q) {
        String term = q == null ? "" : q.trim().toLowerCase();
        List<SubjectDto> result = new ArrayList<>();
        users.search(term, PageRequest.of(0, 8, Sort.by("email"))).stream()
                .filter(u -> u.getStatus() != UserStatus.DISABLED).map(SubjectDirectory::user).forEach(result::add);
        units.findAll().stream().filter(u -> u.getName().toLowerCase().contains(term)).limit(8)
                .map(SubjectDirectory::unit).forEach(result::add);
        groups.search(term).stream().limit(8).map(SubjectDirectory::group).forEach(result::add);
        return result;
    }

    private static SubjectDto user(User u) {
        return new SubjectDto(SubjectKind.USER, u.getId(), u.displayName(), u.getEmail());
    }

    private static SubjectDto unit(OrgUnit u) {
        return new SubjectDto(SubjectKind.UNIT, u.getId(), u.getName(), null);
    }

    private static SubjectDto group(UserGroup g) {
        return new SubjectDto(SubjectKind.GROUP, g.getId(), g.getName(), g.getKind());
    }
}
