package com.community.groupbuy.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicInsert;

/**
 * 계좌 송금 — shared V102.
 * 읽기는 저장소의 한 줄 모양(프로젝션), 상태 전이·수량은 저장소의 조건부 UPDATE 다 — 이 엔티티는 새 줄 넣기({@code persist})와 질의 경로(Q 클래스)에 쓴다.
 * {@code @DynamicInsert}: 값을 주지 않은 칸은 INSERT 에서 빠져 DB 기본값을 받는다(예전 INSERT 문장의 칸 목록과 같다).
 */
@Entity
@DynamicInsert
@Table(name = "commerce_bank_transfers")
public class CommerceBankTransferEntity {

    @Id
    private Long id;
    private String sourceKind;
    private Long sourceId;
    private Long participationId;
    private Long userId;
    private Long amount;
    private String status;
    private String bankEnc;
    private String accountNoEnc;
    private String holderEnc;
    private Instant accountErasedAt;
    private String reason;
    private Long createdBy;
    private Instant createdAt;
    private Long sentBy;
    private Instant sentAt;

    protected CommerceBankTransferEntity() {
    }
}
