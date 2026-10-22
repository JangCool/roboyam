package com.community.menu.dto.request;

import java.util.List;

/**
 * 관리자 메뉴 저장 요청의 노드 — admin-web {@code AdminMenuConfigNodeDto}. 검사는 {@code AdminMenuService}.
 *
 * @param id         지금 있는 노드 ID(문자열). 새 노드는 null
 * @param kind       {@code SECTION}(최상위만) · {@code FOLDER}(최상위·묶음 안) · {@code SCREEN}
 * @param labelKey   기본 이름의 i18n 키(레지스트리의 묶음·부모 항목 것만) — 화면 항목은 무시
 * @param targetType {@code SCREEN} 만 — {@code ADMIN_SCREEN} · {@code URL}
 * @param settings   {@code URL} 만 — {@code {href, newTab}}
 * @param permission {@code URL} 만 — 고르면 그 권한이 있는 운영자에게만 보인다(관리자 화면 항목의 권한은 레지스트리가 정한다)
 * @param children   {@code SECTION}·{@code FOLDER} 만
 */
public record AdminMenuNodeRequest(String id, String kind, String labelKey, String label, String labelEn, String icon, Boolean visible, String targetType,
                                   String targetKey, Link settings, String permission, List<AdminMenuNodeRequest> children) {

    /** 외부 링크 설정 */
    public record Link(String href, Boolean newTab) {
    }
}
