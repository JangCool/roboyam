package com.community.groupbuy.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
    // ── 배송 추적(V272 — 결정 90, 쓰기는 ParcelTrackingStore) ──
    /** 마지막 조회 단계(0 스캔 없음 ~ 6 배송 완료) */
    private Integer trackingLevel;
    private Boolean trackingComplete;
    /** 이력 jsonb {@code [{time,kind,where,level}]} — 기사 이름·전화 없음 */
    @JdbcTypeCode(SqlTypes.JSON)
    private String trackingEvents;
    private Instant trackingCheckedAt;
    private Instant trackingNextAt;
    private Integer trackingFailCount;
    private String trackingErrorCode;
    /** 하루 조회 수의 KST 날짜 */
    private LocalDate trackingDay;
    private Integer trackingDayCount;
    /** 택배사가 준 완료 시각 */
    private Instant trackingCompletedAt;
    /** 완료를 받았지만 자동 배송완료를 하지 않은 사유(BEFORE_SHIPPED · IN_FUTURE) */
    private String trackingHoldReason;
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
