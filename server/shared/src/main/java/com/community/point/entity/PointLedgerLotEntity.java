package com.community.point.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** 원장 한 줄이 어느 묶음에 얼마(±) 반영됐는지 — {@code point_ledger_lots}(V85). <b>읽기 전용 매핑</b>(추가만, DB 트리거가 수정을 막는다) */
@Entity
@Table(name = "point_ledger_lots")
@IdClass(PointLedgerLotId.class)
public class PointLedgerLotEntity {

    @Id
    private Long ledgerId;

    @Id
    private Long lotId;

    private long amount;

    protected PointLedgerLotEntity() {
    }

    public Long getLedgerId() {
        return ledgerId;
    }

    public Long getLotId() {
        return lotId;
    }

    public long getAmount() {
        return amount;
    }
}
