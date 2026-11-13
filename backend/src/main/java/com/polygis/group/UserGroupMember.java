package com.polygis.group;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_group_members")
@IdClass(UserGroupMember.Key.class)
@Getter
@Setter
public class UserGroupMember {

    public static final String ADMIN = "admin";
    public static final String MEMBER = "member";

    @Id
    @Column(name = "group_id")
    private UUID groupId;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false, length = 20)
    private String role = MEMBER;

    @NoArgsConstructor
    @EqualsAndHashCode
    @Getter
    @Setter
    public static class Key implements Serializable {
        private UUID groupId;
        private UUID userId;

        public Key(UUID groupId, UUID userId) {
            this.groupId = groupId;
            this.userId = userId;
        }
    }
}
