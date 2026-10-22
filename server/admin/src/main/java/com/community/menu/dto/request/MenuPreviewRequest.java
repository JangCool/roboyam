package com.community.menu.dto.request;

import java.util.List;

/**
 * {@code POST /api/admin/menu-sets/{key}/preview} — 저장 전 트리를 이 사람 기준으로 걸러 본다(저장하지 않는다).
 *
 * @param viewer {@code { kind: 'GUEST'|'MEMBER'|'STAFF', gradeKey? }}
 */
public record MenuPreviewRequest(List<MenuNodeRequest> nodes, Viewer viewer) {

    /** 보는 사람 — {@code MEMBER} 는 등급 키(없으면 첫 등급) */
    public record Viewer(String kind, String gradeKey) {
    }
}
