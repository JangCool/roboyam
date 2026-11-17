package com.community.trial.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;

/**
 * 체험단 모집 — {@code trials}(V241, trial-group-contract 「구현 결정」 2). 광고 신청 건(kind TRIAL) 한 건과 1:1. 요금(단위·단가·발송비)은 만들 때의 요금표 값 사본이고
 * 금액은 뽑기를 마친 뒤 확정한다({@link #select}).
 */
@Entity
@Table(name = "trials")
public class TrialEntity {

    @Id
    private Long id;
    private Long advertiserId;
    private Long bizApplicationId;
    private String title;
    private String productName;
    private long productPrice;
    @Enumerated(EnumType.STRING)
    private TrialForm form;
    private Long reviewBoardId;
    private int recruitCount;
    private int reviewerShare;
    private int reviewerGoldHours;
    private int reviewerSilverHours;
    private Instant applyStartAt;
    private Instant applyEndAt;
    private int reviewDays;
    private Integer returnDays;
    @Enumerated(EnumType.STRING)
    private TrialShipper shipper;
    @Enumerated(EnumType.STRING)
    private TrialStatus status;
    private String description;
    private String imageKey;
    private String priceUnit;
    private long unitPrice;
    private long shippingFeePerPerson;
    private Integer selectedCount;
    private Long amount;
    private Long createdBy;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant selectedAt;
    private Long selectedBy;
    private Instant closedAt;
    private Instant cancelledAt;

    protected TrialEntity() {
    }

    /** 모집 칸 묶음(만들기·고치기) */
    public record Fields(long advertiserId, String title, String productName, long productPrice, TrialForm form, long reviewBoardId, int recruitCount, int reviewerShare,
                         int reviewerGoldHours, int reviewerSilverHours, Instant applyStartAt, Instant applyEndAt, int reviewDays, Integer returnDays, TrialShipper shipper,
                         String description, String imageKey) {
    }

    public static TrialEntity create(long id, long bizApplicationId, Fields f, String priceUnit, long unitPrice, long shippingFeePerPerson, long operatorId, Instant now) {
        TrialEntity e = new TrialEntity();
        e.id = id;
        e.bizApplicationId = bizApplicationId;
        e.apply(f);
        e.status = TrialStatus.DRAFT;
        e.priceUnit = priceUnit;
        e.unitPrice = unitPrice;
        e.shippingFeePerPerson = shippingFeePerPerson;
        e.createdBy = operatorId;
        e.createdAt = now;
        e.updatedAt = now;
        return e;
    }

    /** 칸 고치기(뽑기 전 — 부르는 쪽이 상태를 본다) */
    public void edit(Fields f, Instant now) {
        apply(f);
        this.updatedAt = now;
    }

    private void apply(Fields f) {
        this.advertiserId = f.advertiserId();
        this.title = f.title();
        this.productName = f.productName();
        this.productPrice = f.productPrice();
        this.form = f.form();
        this.reviewBoardId = f.reviewBoardId();
        this.recruitCount = f.recruitCount();
        this.reviewerShare = f.reviewerShare();
        this.reviewerGoldHours = f.reviewerGoldHours();
        this.reviewerSilverHours = f.reviewerSilverHours();
        this.applyStartAt = f.applyStartAt();
        this.applyEndAt = f.applyEndAt();
        this.reviewDays = f.reviewDays();
        this.returnDays = f.form() == TrialForm.LEND ? f.returnDays() : null;
        this.shipper = f.shipper();
        this.description = f.description() == null ? "" : f.description();
        this.imageKey = f.imageKey();
    }

    public void changeStatus(TrialStatus next, Instant now) {
        this.status = next;
        this.updatedAt = now;
        if (next == TrialStatus.CLOSED) {
            this.closedAt = now;
        } else if (next == TrialStatus.CANCELLED) {
            this.cancelledAt = now;
        }
    }

    /** 뽑기 확정 — 인원·금액을 굳히고 진행 중으로 */
    public void select(int count, long confirmedAmount, long operatorId, Instant now) {
        this.selectedCount = count;
        this.amount = confirmedAmount;
        this.selectedAt = now;
        this.selectedBy = operatorId;
        this.status = TrialStatus.RUNNING;
        this.updatedAt = now;
    }

    public boolean lend() {
        return form == TrialForm.LEND;
    }

    public boolean operatorShips() {
        return shipper == TrialShipper.OPERATOR;
    }

    /** 리뷰어 몫 인원(내림 — 리뷰어 몫 % × 인원) */
    public int reviewerSlots() {
        return recruitCount * reviewerShare / 100;
    }

    /** 그 단계가 신청을 열 수 있는 때 — 금·은은 일반 시작보다 먼저(시간), 동·칭호 없음은 일반 시작 */
    public Instant applyOpenFor(String reviewerTier) {
        if ("GOLD".equals(reviewerTier)) {
            return applyStartAt.minus(Duration.ofHours(reviewerGoldHours));
        }
        if ("SILVER".equals(reviewerTier)) {
            return applyStartAt.minus(Duration.ofHours(reviewerSilverHours));
        }
        return applyStartAt;
    }

    public Long getId() {
        return id;
    }

    public Long getAdvertiserId() {
        return advertiserId;
    }

    public Long getBizApplicationId() {
        return bizApplicationId;
    }

    public String getTitle() {
        return title;
    }

    public String getProductName() {
        return productName;
    }

    public long getProductPrice() {
        return productPrice;
    }

    public TrialForm getForm() {
        return form;
    }

    public Long getReviewBoardId() {
        return reviewBoardId;
    }

    public int getRecruitCount() {
        return recruitCount;
    }

    public int getReviewerShare() {
        return reviewerShare;
    }

    public int getReviewerGoldHours() {
        return reviewerGoldHours;
    }

    public int getReviewerSilverHours() {
        return reviewerSilverHours;
    }

    public Instant getApplyStartAt() {
        return applyStartAt;
    }

    public Instant getApplyEndAt() {
        return applyEndAt;
    }

    public int getReviewDays() {
        return reviewDays;
    }

    public Integer getReturnDays() {
        return returnDays;
    }

    public TrialShipper getShipper() {
        return shipper;
    }

    public TrialStatus getStatus() {
        return status;
    }

    public String getDescription() {
        return description;
    }

    public String getImageKey() {
        return imageKey;
    }

    public String getPriceUnit() {
        return priceUnit;
    }

    public long getUnitPrice() {
        return unitPrice;
    }

    public long getShippingFeePerPerson() {
        return shippingFeePerPerson;
    }

    public Integer getSelectedCount() {
        return selectedCount;
    }

    public Long getAmount() {
        return amount;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getSelectedAt() {
        return selectedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }
}
