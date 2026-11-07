package com.community.menu.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 관리자 메뉴 구성 상태(행이 하나뿐이다, {@code id = 1}) — admin 스트림 V2 의 {@code admin_menu_state}. {@code version} 은 저장마다 1 씩 오르고
 * (409 {@code MENU_VERSION_CONFLICT} 판정), {@code updatedAt} 이 null 이면 아직 저장한 적이 없다(화면 레지스트리의 기본 구성을 쓴다).
 * 저장은 행을 쓰기 잠금으로 읽은 트랜잭션 안에서 {@link #markSaved} 로만 한다(변경 감지).
 */
@Entity
@Table(name = "admin_menu_state")
public class AdminMenuStateEntity {

    /** 행 번호 — 행은 하나뿐이라 항상 1 이다 */
    public static final int ROW_ID = 1;

    @Id
    private Integer id;

    private long version;
    private Instant updatedAt;
    private Long updatedBy;

    protected AdminMenuStateEntity() {
    }

    /** 저장했다 — 버전을 올리고 저장한 사람·시각을 남긴다 */
    public void markSaved(long operatorId, Instant now) {
        version++;
        updatedAt = now;
        updatedBy = operatorId;
    }

    public long getVersion() {
        return version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
