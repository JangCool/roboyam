package com.community.groupbuy.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicInsert;

/**
 * 공급 불가 회원 선택(결정 53) — shared V102.
 * 읽기는 저장소의 한 줄 모양(프로젝션), 상태 전이·수량은 저장소의 조건부 UPDATE 다 — 이 엔티티는 새 줄 넣기({@code persist})와 질의 경로(Q 클래스)에 쓴다.
 * {@code @DynamicInsert}: 값을 주지 않은 칸은 INSERT 에서 빠져 DB 기본값을 받는다(예전 INSERT 문장의 칸 목록과 같다).
 */
@Entity
@DynamicInsert
@Table(name = "group_buy_supply_choices")
public class GroupBuySupplyChoiceEntity {

    @Id
    private Long id;
    private Long supplyFailureId;
    private Long participationId;
    private Long userId;
    @Enumerated(EnumType.STRING)
    private SupplyChoice choice;
    private Instant deadline;
    private Instant decidedAt;
    private Boolean defaulted;
    private Instant createdAt;

    protected GroupBuySupplyChoiceEntity() {
    }
}
