package com.community.group.entity;

import java.util.ArrayList;
import java.util.List;

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
