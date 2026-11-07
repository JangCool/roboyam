package com.community.groupbuy.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicInsert;

/**
 * 공구 상태 이력 — shared V101.
 * 넣기는 저장소의 JDBC 한 문장이다(결제 확정의 JDBC 세이브포인트 안에서도 넣는다 — 그 안에서 JPA 문장이 실패하면 업무 트랜잭션 전체가 롤백 전용). 이 엔티티는 질의 경로(Q 클래스)에 쓴다.
 * {@code @DynamicInsert}: 값을 주지 않은 칸은 INSERT 에서 빠져 DB 기본값을 받는다(예전 INSERT 문장의 칸 목록과 같다).
 */
@Entity
@DynamicInsert
@Table(name = "group_buy_status_history")
public class GroupBuyStatusHistoryEntity {

    @Id
    private Long id;
    private Long groupBuyId;
    @Enumerated(EnumType.STRING)
    private GroupBuyStatus fromStatus;
    @Enumerated(EnumType.STRING)
    private GroupBuyStatus toStatus;
    private String reason;
    private Long actorId;
    private Instant createdAt;

    protected GroupBuyStatusHistoryEntity() {
    }
}
