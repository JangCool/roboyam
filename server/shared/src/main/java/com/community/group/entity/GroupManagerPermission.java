package com.community.group.entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 매니저가 맡을 수 있는 일(3단계) — 이 5개가 전부이고 키 이름·순서는 고정이다. 비트 값은 서버 내부 값({@code group_members.manager_perms})이라 바꾸지 않는다.
 */
public enum GroupManagerPermission {
    APPLICATIONS(1),
    KICK(2),
    RECRUITING(4),
    EVENTS(8),
    UNPUBLISH(16);

    private final int bit;

    GroupManagerPermission(int bit) {
        this.bit = bit;
    }

    public int bit() {
        return bit;
    }

    /** 키 이름 → 맡은 일(대소문자 그대로, 모르는 키는 비어 있다) */
    public static Optional<GroupManagerPermission> of(String key) {
        for (GroupManagerPermission p : values()) {
            if (p.name().equals(key)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    /** 맡은 일 묶음 → 비트 집합 */
    public static int maskOf(Collection<GroupManagerPermission> permissions) {
        int mask = 0;
        for (GroupManagerPermission p : permissions) {
            mask |= p.bit;
        }
        return mask;
    }

    /** 비트 집합 → 키 목록(고정 순서) */
    public static List<String> keys(int bits) {
        List<String> out = new ArrayList<>();
        for (GroupManagerPermission p : values()) {
            if ((bits & p.bit) != 0) {
                out.add(p.name());
            }
        }
        return out;
    }
}
