package com.community.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 대사 이슈 한 줄 — shared V97 의 {@code reconciliation_issues}. 열린 이슈는 (종류, 결제) 하나(부분 유일 색인 {@code ux_reconciliation_issues_open}) —
 * 넣기는 {@code INSERT … ON CONFLICT (type, payment_id) WHERE status = 'OPEN' DO NOTHING}(네이티브)이고 닫기는 저장소의 조건부 UPDATE 다. 자동 보정하지 않는다(commerce.md 16-1).
 */
@Entity
@Table(name = "reconciliation_issues")
public class ReconciliationIssueEntity {

    @Id
    private Long id;

    /** 이슈 종류 — {@code ReconciliationIssueStore.Type} 이름(DB CHECK 와 같은 목록). 칸 이름 {@code type} 은 HQL 함수 이름과 겹쳐 속성 이름을 달리 둔다 */
    @Column(name = "type")
    private String issueType;
    private String paymentId;
    private Long paymentOrderId;
    /** 발견한 날(KST) */
    private LocalDate detectedDay;
    /** jsonb — 비었으면 null */
    @JdbcTypeCode(SqlTypes.JSON)
    private String detail;
    /** OPEN · RESOLVED */
    private String status;
    private String resolveNote;
    private Long resolvedBy;
    private Instant resolvedAt;
    private Instant createdAt;

    protected ReconciliationIssueEntity() {
    }
}
