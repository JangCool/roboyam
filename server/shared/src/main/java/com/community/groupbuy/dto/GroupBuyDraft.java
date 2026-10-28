package com.community.groupbuy.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 공구 등록·수정 값(관리자). 모집 시작 뒤에는 설명·이미지(요약·사양 포함)만 바꿀 수 있고, 나머지 칸이 다르면 409 {@code GROUP_BUY_LOCKED_FIELD}(10-2).
 *
 * @param shipPlannedAt          발송 예정일(마감 후 발주 공구 필수 — 결정 51)
 * @param shipWithinBusinessDays 결제 후 N영업일 안 발송(즉시 발송 공구 — 없으면 공구 설정 기본값)
 */
public record GroupBuyDraft(String title, String productName, String summary, String spec, String description, Long normalPrice, String normalPriceBasis,
                            String normalPriceSource, long groupPrice, String shippingPolicyCode, int minQty, int maxQty, Integer maxQtyPerUser, Instant startAt,
                            Instant endAt, String extensionMode, String fulfillment, LocalDate shipPlannedAt, Integer shipWithinBusinessDays,
                            String withdrawalRestriction, String returnPolicy, boolean roundLimitShared, List<OptionDraft> options, List<ImageRef> images) {

    /** 옵션 — 한도 없음은 {@code maxQty} null */
    public record OptionDraft(String name, long extraPrice, Integer maxQty) {
    }

    /** 대표 이미지(저장소 키 — 올리기·다시 인코딩은 관리자 이미지 API, 결정 2) */
    public record ImageRef(String imageKey, String thumbKey) {
    }
}
