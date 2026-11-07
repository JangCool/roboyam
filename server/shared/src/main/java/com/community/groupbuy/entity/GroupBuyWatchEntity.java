package com.community.groupbuy.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicInsert;

/**
 * 관심 등록(마감 임박·재입고·다음 차수) — shared V101.
 * 읽기는 저장소의 한 줄 모양(프로젝션), 상태 전이·수량은 저장소의 조건부 UPDATE 다 — 이 엔티티는 새 줄 넣기({@code persist})와 질의 경로(Q 클래스)에 쓴다.
 * {@code @DynamicInsert}: 값을 주지 않은 칸은 INSERT 에서 빠져 DB 기본값을 받는다(예전 INSERT 문장의 칸 목록과 같다).
 */
@Entity
@DynamicInsert
@Table(name = "group_buy_watches")
public class GroupBuyWatchEntity {

    @Id
    private Long id;
    private Long groupBuyId;
    private Long userId;
    @Enumerated(EnumType.STRING)
    private WatchKind kind;
    private Long skuId;
    private Instant lastNotifiedAt;
    private Instant createdAt;

    protected GroupBuyWatchEntity() {
    }
}
