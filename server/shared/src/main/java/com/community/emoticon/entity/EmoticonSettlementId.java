package com.community.emoticon.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/** {@link EmoticonSettlementEntity} 의 복합 키(작가 + 판매 달) */
public class EmoticonSettlementId implements Serializable {

    private Long creatorId;
    private LocalDate salesMonth;

    protected EmoticonSettlementId() {
    }

    public EmoticonSettlementId(Long creatorId, LocalDate salesMonth) {
        this.creatorId = creatorId;
        this.salesMonth = salesMonth;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof EmoticonSettlementId that && Objects.equals(creatorId, that.creatorId) && Objects.equals(salesMonth, that.salesMonth);
    }

    @Override
    public int hashCode() {
        return Objects.hash(creatorId, salesMonth);
    }
}
