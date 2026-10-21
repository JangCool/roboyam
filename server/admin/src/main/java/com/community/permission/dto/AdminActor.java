package com.community.permission.dto;

import com.community.permission.entity.Permission;
import java.util.Set;

/**
 * 이 요청을 한 운영자 — 요청마다 DB 에서 현재 상태(승인·잠금·사용·기간)와 역할의 권한 합집합을 읽어 만든다(캐시하지 않는다).
 * 컨트롤러 인자로 받는다. 최고 관리자(루트)는 모든 권한을 가진다.
 *
 * @param permissions 받은 운영자 역할들의 권한 코드 합집합. 최고 관리자는 모든 운영자 권한 코드
 * @param ip          요청자 IP(감사 기록용)
 */
public record AdminActor(long operatorId, String operatorName, String email, boolean root, Set<String> permissions, String ip) {

    public AdminActor {
        permissions = Set.copyOf(permissions);
    }

    public boolean can(String code) {
        return root || permissions.contains(code);
    }

    public boolean can(Permission permission) {
        return can(permission.code());
    }

    /** 이메일을 로그에 남기지 않는다 */
    @Override
    public String toString() {
        return "AdminActor[operatorId=" + operatorId + ", root=" + root + ", permissions=" + permissions.size() + "개]";
    }
}
