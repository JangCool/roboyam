package com.community.menu.dto.response;

import java.util.List;

/**
 * 관리자 메뉴 노드 — admin-web {@code AdminMenuConfigNodeDto}.
 *
 * @param id         저장된 노드 ID. 아직 저장한 적 없는 기본 구성은 null
 * @param settings   {@code URL} 만 — {@code {href, newTab}}
 * @param permission {@code URL} 만(관리자 화면 항목의 권한은 화면이 정해 여기 오지 않는다)
 * @param children   {@code SECTION}·{@code FOLDER} 만(그 밖에는 null)
 */
public record AdminMenuNodeView(String id, String kind, String labelKey, String label, String labelEn, String icon, boolean visible, String targetType,
                                String targetKey, Link settings, String permission, List<AdminMenuNodeView> children) {

    /** 외부 링크 설정 */
    public record Link(String href, boolean newTab) {
    }
}
