package com.example.chat.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "group_members")
@IdClass(GroupMember.Pk.class)
public class GroupMember {

    @Id
    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Id
    @Column(nullable = false, length = 40)
    private String username;

    @Column(nullable = false, length = 20)
    private String role = "MEMBER";

    @Column(name = "joined_at", nullable = false)
    private long joinedAt;

    public GroupMember() {
    }

    public GroupMember(Long groupId, String username, String role) {
        this.groupId = groupId;
        this.username = username;
        this.role = role != null ? role : "MEMBER";
        this.joinedAt = System.currentTimeMillis();
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public long getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(long joinedAt) {
        this.joinedAt = joinedAt;
    }

    public static class Pk implements Serializable {
        private Long groupId;
        private String username;

        public Pk() {
        }

        public Pk(Long groupId, String username) {
            this.groupId = groupId;
            this.username = username;
        }

        public Long getGroupId() {
            return groupId;
        }

        public void setGroupId(Long groupId) {
            this.groupId = groupId;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Pk pk)) {
                return false;
            }
            return Objects.equals(groupId, pk.groupId) && Objects.equals(username, pk.username);
        }

        @Override
        public int hashCode() {
            return Objects.hash(groupId, username);
        }
    }
}
