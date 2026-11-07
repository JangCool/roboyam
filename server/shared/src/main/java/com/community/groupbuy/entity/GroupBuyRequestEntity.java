package com.community.groupbuy.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.DynamicInsert;

/**
 * 취소·반품 요청 — shared V102 · V103 · V104.
 * 읽기는 저장소의 한 줄 모양(프로젝션), 상태 전이·수량은 저장소의 조건부 UPDATE 다 — 이 엔티티는 새 줄 넣기({@code persist})와 질의 경로(Q 클래스)에 쓴다.
 * {@code @DynamicInsert}: 값을 주지 않은 칸은 INSERT 에서 빠져 DB 기본값을 받는다(예전 INSERT 문장의 칸 목록과 같다).
 */
@Entity
@DynamicInsert
@Table(name = "group_buy_requests")
public class GroupBuyRequestEntity {

    @Id
    private Long id;
    private Long participationId;
    private Long groupBuyId;
    private Long userId;
    @Enumerated(EnumType.STRING)
    private RequestType type;
    @Enumerated(EnumType.STRING)
    private RequestStatus status;
    @Enumerated(EnumType.STRING)
    private CollectionMethod collectionMethod;
    @Enumerated(EnumType.STRING)
    private Resolution resolution;
    private Boolean sellerFault;
    private String memo;
    private Long previewAmount;
    private Long feePaymentOrderId;
    private Long feeAmount;
    private Long feePaidAmount;
    private Long feeRefundedAmount;
    private Instant closesAt;
    private String pickupCarrierCode;
    private String pickupTrackingNo;
    private Instant receivedAt;
    private Long compensationAmount;
    private String rejectReasonCode;
    private String rejectNote;
    private Long decidedBy;
    private Instant decidedAt;
    private Instant completedAt;
    private Instant createdAt;
    private Instant updatedAt;
    private LocalDate defectKnownOn;
    private String deadlineAlertLevel;

    protected GroupBuyRequestEntity() {
    }

    /** 새 요청 — 반품비 낸 액·돌려준 액·보상은 DB 기본값(0) */
    public static GroupBuyRequestEntity create(long id, long participationId, long groupBuyId, long userId, RequestType type, RequestStatus status,
                                               CollectionMethod collectionMethod, Resolution resolution, boolean sellerFault, String memo, long previewAmount, long feeAmount,
                                               Instant closesAt, LocalDate defectKnownOn, Instant now) {
        GroupBuyRequestEntity r = new GroupBuyRequestEntity();
        r.id = id;
        r.participationId = participationId;
        r.groupBuyId = groupBuyId;
        r.userId = userId;
        r.type = type;
        r.status = status;
        r.collectionMethod = collectionMethod;
        r.resolution = resolution;
        r.sellerFault = sellerFault;
        r.memo = memo;
        r.previewAmount = previewAmount;
        r.feeAmount = feeAmount;
        r.closesAt = closesAt;
        r.defectKnownOn = defectKnownOn;
        r.createdAt = now;
        r.updatedAt = now;
        return r;
    }
}
