package com.community.point.entity;

import java.io.Serializable;
import java.util.Objects;

/** {@link PointLedgerLotEntity} 의 복합 키(원장 줄 + 묶음) */
public class PointLedgerLotId implements Serializable {

    private Long ledgerId;
    private Long lotId;

    protected PointLedgerLotId() {
    }

    public PointLedgerLotId(Long ledgerId, Long lotId) {
        this.ledgerId = ledgerId;
        this.lotId = lotId;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PointLedgerLotId that && Objects.equals(ledgerId, that.ledgerId) && Objects.equals(lotId, that.lotId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ledgerId, lotId);
    }
}
