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
 * 공구(판매 조건·수량 열·상태) — shared V101.
 * 읽기는 저장소의 한 줄 모양(프로젝션), 상태 전이·수량은 저장소의 조건부 UPDATE 다 — 이 엔티티는 새 줄 넣기({@code persist})와 질의 경로(Q 클래스)에 쓴다.
 * {@code @DynamicInsert}: 값을 주지 않은 칸은 INSERT 에서 빠져 DB 기본값을 받는다(예전 INSERT 문장의 칸 목록과 같다).
 */
@Entity
@DynamicInsert
@Table(name = "group_buys")
public class GroupBuyEntity {

    @Id
    private Long id;
    private Long productId;
    private String title;
    private Long groupPrice;
    private String shippingPolicyCode;
    private Long shippingFee;
    private Long freeShippingOver;
    private Long returnShippingFee;
    private String returnFeeMode;
    private Integer minQty;
    private Integer maxQty;
    private Integer initialMaxQty;
    private Integer addedQty;
    private Integer maxQtyPerUser;
    private Integer currentQty;
    private Integer reservedQty;
    private Instant startAt;
    private Instant endAt;
    private String extensionMode;
    private Instant extendedUntil;
    private Instant closingAt;
    private String closeReason;
    private String fulfillment;
    private LocalDate shipPlannedAt;
    private Integer shipWithinBusinessDays;
    private String returnPolicy;
    @Enumerated(EnumType.STRING)
    private GroupBuyStatus status;
    private Instant publishedAt;
    private String hostType;
    private Boolean pointPaymentAllowed;
    private String allowedPointKinds;
    private Long freePointCapPerParticipation;
    private Long roundRootId;
    private Integer roundNo;
    private Boolean roundLimitShared;
    private Integer revisionNo;
    private Integer version;
    private Long createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    protected GroupBuyEntity() {
    }

    /** 새 공구(DRAFT) — 1차면 {@code roundRootId = id}. 상태·수량 열·이력 번호는 DB 기본값(DRAFT · 0) */
    public static GroupBuyEntity draft(long id, long productId, String title, long groupPrice, String shippingPolicyCode, int minQty, int maxQty, Integer maxQtyPerUser,
                                       Instant startAt, Instant endAt, String extensionMode, String fulfillment, LocalDate shipPlannedAt, Integer shipWithinBusinessDays,
                                       String returnPolicy, long roundRootId, int roundNo, boolean roundLimitShared, Long createdBy, Instant now) {
        GroupBuyEntity g = new GroupBuyEntity();
        g.id = id;
        g.productId = productId;
        g.title = title;
        g.groupPrice = groupPrice;
        g.shippingPolicyCode = shippingPolicyCode;
        g.minQty = minQty;
        g.maxQty = maxQty;
        g.maxQtyPerUser = maxQtyPerUser;
        g.startAt = startAt;
        g.endAt = endAt;
        g.extensionMode = extensionMode;
        g.fulfillment = fulfillment;
        g.shipPlannedAt = shipPlannedAt;
        g.shipWithinBusinessDays = shipWithinBusinessDays;
        g.returnPolicy = returnPolicy;
        g.roundRootId = roundRootId;
        g.roundNo = roundNo;
        g.roundLimitShared = roundLimitShared;
        g.createdBy = createdBy;
        g.createdAt = now;
        g.updatedAt = now;
        return g;
    }
}
