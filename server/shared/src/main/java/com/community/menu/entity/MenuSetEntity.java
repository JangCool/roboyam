package com.community.menu.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 메뉴 그룹(메뉴 세트) — V43__menus.sql 의 {@code menu_sets}. 키는 사람이 정한 코드(PK)이고 만든 뒤 바꾸지 않는다.
 * {@code version} 은 그 그룹 트리의 버전이다 — 트리가 바뀔 때마다({@link #treeChanged}) 1 씩 오른다(JPA 낙관적 잠금이 아니다 — 행 잠금 아래에서 앱이 견준다).
 */
@Entity
@Table(name = "menu_sets")
@DynamicUpdate
public class MenuSetEntity {

    @Id
    private String setKey;

    private String name;
    private String nameEn;
    private String placement;
    private boolean enabled;
    private String description;
    private boolean isSystem;
    private long version;
    private Instant createdAt;
    private Long createdBy;
    private Instant updatedAt;
    private Long updatedBy;

    protected MenuSetEntity() {
    }

    /** 운영자가 만든 그룹(기본 그룹이 아니다) — 빈 트리, 버전 1 */
    public static MenuSetEntity create(String setKey, String name, String nameEn, String placement, boolean enabled, String description, long operatorId,
                                       Instant now) {
        MenuSetEntity set = new MenuSetEntity();
        set.setKey = setKey;
        set.name = name;
        set.nameEn = nameEn;
        set.placement = placement;
        set.enabled = enabled;
        set.description = description;
        set.isSystem = false;
        set.version = 1;
        set.createdAt = now;
        set.createdBy = operatorId;
        set.updatedAt = now;
        set.updatedBy = operatorId;
        return set;
    }

    /** 정보 바꾸기 — 기본 그룹의 위치는 부르는 쪽이 지금 값을 넘긴다 */
    public void change(String name, String nameEn, String placement, boolean enabled, String description, long operatorId, Instant now) {
        this.name = name;
        this.nameEn = nameEn;
        this.placement = placement;
        this.enabled = enabled;
        this.description = description;
        this.updatedAt = now;
        this.updatedBy = operatorId;
    }

    /** 트리가 바뀌었다 — 버전을 올린다. 운영자가 아닌 일(게시판 자동 추가·삭제)은 {@code operatorId} 가 null 이라 저장한 사람을 바꾸지 않는다 */
    public void treeChanged(Long operatorId, Instant now) {
        this.version += 1;
        this.updatedAt = now;
        if (operatorId != null) {
            this.updatedBy = operatorId;
        }
    }

    public String getSetKey() {
        return setKey;
    }

    public String getName() {
        return name;
    }

    public String getNameEn() {
        return nameEn;
    }

    public String getPlacement() {
        return placement;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getDescription() {
        return description;
    }

    public boolean isSystem() {
        return isSystem;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
