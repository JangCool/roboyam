package com.community.groupbuy.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 운영 환불 승인 대기(결정 70) — shared V102.
 * 읽기는 저장소의 한 줄 모양(프로젝션), 상태 전이·수량은 저장소의 조건부 UPDATE 다 — 이 엔티티는 새 줄 넣기({@code persist})와 질의 경로(Q 클래스)에 쓴다.
 * {@code @DynamicInsert}: 값을 주지 않은 칸은 INSERT 에서 빠져 DB 기본값을 받는다(예전 INSERT 문장의 칸 목록과 같다).
 */
@Entity
@DynamicInsert
@Table(name = "group_buy_admin_refunds")
public class GroupBuyAdminRefundEntity {

    @Id
    private Long id;
    private Long participationId;
    private String kind;
    private Long amount;
    @JdbcTypeCode(SqlTypes.JSON)
    private String lines;
    private Boolean sellerFault;
    private String reason;
    private String status;
    private Long requestedBy;
    private Long decidedBy;
    private Long refundId;
    private Instant createdAt;
    private Instant decidedAt;

    protected GroupBuyAdminRefundEntity() {
    }

    /** 승인 대기(AWAITING_APPROVAL) 운영 환불 */
    public static GroupBuyAdminRefundEntity awaiting(long id, long participationId, String kind, long amount, String linesJson, boolean sellerFault, String reason,
                                                     long requestedBy, Instant now) {
        GroupBuyAdminRefundEntity r = new GroupBuyAdminRefundEntity();
        r.id = id;
        r.participationId = participationId;
        r.kind = kind;
        r.amount = amount;
        r.lines = linesJson;
        r.sellerFault = sellerFault;
        r.reason = reason;
        r.status = "AWAITING_APPROVAL";
        r.requestedBy = requestedBy;
        r.createdAt = now;
        return r;
    }
}
