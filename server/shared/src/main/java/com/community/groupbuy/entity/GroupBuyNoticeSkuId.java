package com.community.groupbuy.entity;

import java.io.Serializable;
import java.util.Objects;

/** {@link GroupBuyNoticeSkuEntity} 의 복합 키(고시 양식 + 상품 단품) */
public class GroupBuyNoticeSkuId implements Serializable {

    private Long noticeId;
    private Long productSkuId;

    protected GroupBuyNoticeSkuId() {
    }

    public GroupBuyNoticeSkuId(Long noticeId, Long productSkuId) {
        this.noticeId = noticeId;
        this.productSkuId = productSkuId;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof GroupBuyNoticeSkuId that && Objects.equals(noticeId, that.noticeId) && Objects.equals(productSkuId, that.productSkuId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(noticeId, productSkuId);
    }
}
