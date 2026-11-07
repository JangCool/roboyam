package com.community.groupbuy.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicInsert;

/**
 * 상품 카테고리(두 단) — shared V101.
 * 읽기는 저장소의 한 줄 모양(프로젝션), 상태 전이·수량은 저장소의 조건부 UPDATE 다 — 이 엔티티는 새 줄 넣기({@code persist})와 질의 경로(Q 클래스)에 쓴다.
 * {@code @DynamicInsert}: 값을 주지 않은 칸은 INSERT 에서 빠져 DB 기본값을 받는다(예전 INSERT 문장의 칸 목록과 같다).
 */
@Entity
@DynamicInsert
@Table(name = "group_buy_categories")
public class GroupBuyCategoryEntity {

    @Id
    private Long id;
    private Long parentId;
    private String name;
    private Integer sortOrder;
    private Boolean hidden;
    private Instant createdAt;
    private Instant updatedAt;

    protected GroupBuyCategoryEntity() {
    }

    public static GroupBuyCategoryEntity create(long id, Long parentId, String name, int sortOrder, Instant now) {
        GroupBuyCategoryEntity c = new GroupBuyCategoryEntity();
        c.id = id;
        c.parentId = parentId;
        c.name = name;
        c.sortOrder = sortOrder;
        c.createdAt = now;
        c.updatedAt = now;
        return c;
    }
}
