package com.community.menu.dto.request;

import java.util.List;

/**
 * {@code PUT /api/admin/admin-menu} — 두 모드(일반·설정) 전체 교체. 한 화면은 두 모드 통틀어 한 번이라 한 번에 받는다.
 *
 * @param version 받을 때의 {@code version} 그대로 — 다르면 409 {@code MENU_VERSION_CONFLICT}
 */
public record AdminMenuSaveRequest(String version, Modes modes) {

    public record Modes(List<AdminMenuNodeRequest> normal, List<AdminMenuNodeRequest> settings) {
    }
}
