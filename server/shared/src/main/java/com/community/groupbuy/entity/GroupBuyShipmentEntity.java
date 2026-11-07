package com.community.groupbuy.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicInsert;

/**
 * 발송 — shared V102.
 * 읽기는 저장소의 한 줄 모양(프로젝션), 상태 전이·수량은 저장소의 조건부 UPDATE 다 — 이 엔티티는 새 줄 넣기({@code persist})와 질의 경로(Q 클래스)에 쓴다.
 * {@code @DynamicInsert}: 값을 주지 않은 칸은 INSERT 에서 빠져 DB 기본값을 받는다(예전 INSERT 문장의 칸 목록과 같다).
 */
@Entity
@DynamicInsert
@Table(name = "group_buy_shipments")
public class GroupBuyShipmentEntity {

    @Id
    private Long id;
    private Long participationId;
    private Long groupBuyId;
    private String kind;
    private Long requestId;
    private String carrierCode;
    private String trackingNo;
    private Instant shippedAt;
    private Instant deliveredAt;
    private Boolean escrowApplicable;
    private Instant escrowLogisticsAt;
    private Instant escrowCompletedAt;
    private Long createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    protected GroupBuyShipmentEntity() {
    }

    /** 새 발송(처음 발송 ORIGINAL · 교환 출고 EXCHANGE — 요청 ID) — 발송 시각 = 지금 */
    public static GroupBuyShipmentEntity create(long id, long participationId, long groupBuyId, String kind, Long requestId, String carrierCode, String trackingNo,
                                                Long createdBy, Instant now) {
        GroupBuyShipmentEntity s = new GroupBuyShipmentEntity();
        s.id = id;
        s.participationId = participationId;
        s.groupBuyId = groupBuyId;
        s.kind = kind;
        s.requestId = requestId;
        s.carrierCode = carrierCode;
        s.trackingNo = trackingNo;
        s.shippedAt = now;
        s.createdBy = createdBy;
        s.createdAt = now;
        s.updatedAt = now;
        return s;
    }
}
