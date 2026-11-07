package com.community.menu.entity;

import com.community.menu.dto.AdminMenuNode;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 관리자 메뉴 노드 하나 — admin 스트림 V2 의 {@code admin_menu_nodes}. 두 모드(normal·settings)의 트리: SECTION(최상위만) → SCREEN 또는 FOLDER → 하위 SCREEN.
 * 부모는 ID 칸으로만 둔다(트리는 저장소가 한 번에 읽어 조립한다 — 연관을 따라 읽지 않는다). 부모가 지워지면 하위도 지워진다(ON DELETE CASCADE).
 * 저장은 트리 전체 교체뿐이라(지우고 차례대로 넣는다) 바꾸는 메서드가 없다. 구조 규칙·이름·아이콘·주소 검사는 서비스가 끝낸다.
 */
@Entity
@Table(name = "admin_menu_nodes")
public class AdminMenuNodeEntity {

    @Id
    private Long id;

    /** {@code normal} · {@code settings} */
    private String mode;
    /** 부모 노드 ID(최상위는 null) */
    private Long parentId;
    /** {@code SECTION} · {@code FOLDER} · {@code SCREEN} */
    private String kind;
    private int sortOrder;
    private String labelKey;
    private String label;
    private String labelEn;
    private String icon;
    private boolean visible;
    /** {@code SCREEN} 만 — {@code ADMIN_SCREEN} · {@code URL} */
    private String targetType;
    private String targetKey;
    private String href;
    private boolean newTab;
    /** {@code URL} 항목이 고른 권한 코드(없으면 null) */
    private String permission;

    protected AdminMenuNodeEntity() {
    }

    /** 검사를 마친 노드 하나를 그 자리(모드·부모·차례)에 둔다 — 하위 노드는 따로 넣는다 */
    public static AdminMenuNodeEntity create(long id, String mode, Long parentId, int sortOrder, AdminMenuNode node) {
        AdminMenuNodeEntity row = new AdminMenuNodeEntity();
        row.id = id;
        row.mode = mode;
        row.parentId = parentId;
        row.kind = node.kind();
        row.sortOrder = sortOrder;
        row.labelKey = node.labelKey();
        row.label = node.label();
        row.labelEn = node.labelEn();
        row.icon = node.icon();
        row.visible = node.visible();
        row.targetType = node.targetType();
        row.targetKey = node.targetKey();
        row.href = node.href();
        row.newTab = node.newTab();
        row.permission = node.permission();
        return row;
    }
}
