package com.community.menu.entity;

import com.community.menu.dto.MenuNode;
import com.community.menu.service.MenuSettingsJson;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 메뉴 노드 하나 — V43__menus.sql 의 {@code menu_nodes}. 트리 저장은 그룹의 노드를 모두 지우고 다시 넣는다(전체 교체)라 고치는 도메인 메서드가 없다.
 * 화면과 주고받는 모양은 {@link MenuNode} 이고 이 엔티티는 저장 형태일 뿐이다.
 */
@Entity
@Table(name = "menu_nodes")
public class MenuNodeEntity {

    @Id
    private Long id;

    private String setKey;
    private Long parentId;
    private String kind;
    private int sortOrder;
    private String label;
    private String nameEn;
    private boolean visible;
    private String audience;
    private String minGrade;
    private boolean collapsible;
    private boolean collapsed;
    private boolean autoAdd;
    private String targetType;
    private String targetId;
    private String targetKey;

    /** 유형별 설정 JSON 글자(모르는 유형은 받은 그대로) */
    @JdbcTypeCode(SqlTypes.JSON)
    private String settings;

    private boolean mobileTab;

    /** 상단 메뉴 첫 줄 구분선 NONE · BEFORE · AFTER(V105) */
    private String divider;

    /** 항목 설명(메뉴 설명 줄) — 폴더·없음은 null(V105) */
    private String description;

    protected MenuNodeEntity() {
    }

    /** 정리·검사를 마친 노드를 저장 형태로 — 하위 노드는 부르는 쪽이 따로 넣는다 */
    public static MenuNodeEntity of(long id, String setKey, Long parentId, int sortOrder, MenuNode node) {
        MenuNodeEntity entity = new MenuNodeEntity();
        entity.id = id;
        entity.setKey = setKey;
        entity.parentId = parentId;
        entity.kind = node.kind();
        entity.sortOrder = sortOrder;
        entity.label = node.label();
        entity.nameEn = node.nameEn();
        entity.visible = node.visible();
        entity.audience = node.audience();
        entity.minGrade = node.minGrade();
        entity.collapsible = node.collapsible();
        entity.collapsed = node.collapsed();
        entity.autoAdd = node.autoAdd();
        entity.targetType = node.targetType();
        entity.targetId = node.targetId();
        entity.targetKey = node.targetKey();
        entity.settings = MenuSettingsJson.write(node.settings());
        entity.mobileTab = node.mobileTab();
        entity.divider = node.divider();
        entity.description = node.isItem() ? node.description() : null;
        return entity;
    }

    /** 저장 형태 → 노드(하위 없음) */
    public MenuNode toNode() {
        return new MenuNode(id, kind, label, nameEn, visible, audience, minGrade, collapsible, collapsed, autoAdd, targetType, targetId, targetKey,
                MenuSettingsJson.read(settings), mobileTab, java.util.List.of(), divider, description);
    }

    public Long getId() {
        return id;
    }

    public String getSetKey() {
        return setKey;
    }

    public Long getParentId() {
        return parentId;
    }

    public String getKind() {
        return kind;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isAutoAdd() {
        return autoAdd;
    }
}
