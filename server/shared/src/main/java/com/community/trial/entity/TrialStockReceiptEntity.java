package com.community.trial.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 운영자 입고(운영자가 대신 보낼 때) — {@code trial_stock_receipts}(V241, 모집마다 한 줄). 받은 수량·때 · 광고주 반송 송장 · 보관 기한 알림 */
@Entity
@Table(name = "trial_stock_receipts")
public class TrialStockReceiptEntity {

    @Id
    private Long id;
    private Long trialId;
    private int quantity;
    private String note;
    private Instant receivedAt;
    private Long receivedBy;
    private String returnCarrier;
    private String returnTrackingNo;
    private Instant returnShippedAt;
    private Long returnShippedBy;
    private Instant keepNotifiedAt;

    protected TrialStockReceiptEntity() {
    }

    public static TrialStockReceiptEntity receive(long id, long trialId, int quantity, String note, long operatorId, Instant now) {
        TrialStockReceiptEntity e = new TrialStockReceiptEntity();
        e.id = id;
        e.trialId = trialId;
        e.quantity = quantity;
        e.note = note;
        e.receivedAt = now;
        e.receivedBy = operatorId;
        return e;
    }

    /** 수량·메모 고치기(더 받았을 때) */
    public void change(int quantity, String note, long operatorId, Instant now) {
        this.quantity = quantity;
        this.note = note;
        this.receivedBy = operatorId;
        this.receivedAt = now;
    }

    public void shipBack(String carrier, String trackingNo, long operatorId, Instant now) {
        this.returnCarrier = carrier;
        this.returnTrackingNo = trackingNo;
        this.returnShippedAt = now;
        this.returnShippedBy = operatorId;
    }

    public void markKeepNotified(Instant now) {
        this.keepNotifiedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getTrialId() {
        return trialId;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getNote() {
        return note;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public String getReturnCarrier() {
        return returnCarrier;
    }

    public String getReturnTrackingNo() {
        return returnTrackingNo;
    }

    public Instant getReturnShippedAt() {
        return returnShippedAt;
    }

    public Instant getKeepNotifiedAt() {
        return keepNotifiedAt;
    }
}
