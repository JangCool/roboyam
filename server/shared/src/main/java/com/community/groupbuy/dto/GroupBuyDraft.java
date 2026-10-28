package com.community.groupbuy.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 공구 등록·수정 값(관리자 — 결정 89: 공구 = 상품 하나 + 판매 조건). 모집 시작 뒤에는 공급가만 고칠 수 있고(돈 관련 감사), 나머지 칸이 다르면 409 {@code GROUP_BUY_LOCKED_FIELD}(10-2).
 * 단품은 상품 단품마다 추가금·한도·판매 여부·공급가, 추가 구성상품은 다른 상품의 단품을 연결(결정 88·89).
 *
 * @param productId              파는 상품
 * @param shipPlannedAt          발송 예정일(마감 후 발주 공구 필수 — 결정 51)
 * @param shipWithinBusinessDays 결제 후 N영업일 안 발송(즉시 발송 공구 — 없으면 공구 설정 기본값)
 * @param skus                   상품 단품별 설정 — 적지 않은 상품 단품은 추가금 0·한도 없음·판매 여부와 공급가는 상품 단품의 기본값
 * @param addons                 추가 구성상품(다른 상품의 단품)
 * @param marginConfirmed        판매가 < 공급가(역마진)를 확인했다 — 모집 뒤 공급가를 고칠 때
 */
public record GroupBuyDraft(long productId, String title, long groupPrice, String shippingPolicyCode, int minQty, int maxQty, Integer maxQtyPerUser, Instant startAt,
                            Instant endAt, String extensionMode, String fulfillment, LocalDate shipPlannedAt, Integer shipWithinBusinessDays, String returnPolicy,
                            boolean roundLimitShared, List<SkuDraft> skus, List<AddonDraft> addons, boolean marginConfirmed) {

    /** 옵션 축 — 이름(예 색상)과 값(예 블랙·화이트), 차례대로(상품 등록 값에서 쓴다) */
    public record AxisDraft(String name, List<String> values) {
    }

    /** MAIN 공구 단품 설정 — {@code maxQty} null = 단품 한도 없음, {@code supplyPrice} null = 상품 단품의 기본 공급가 */
    public record SkuDraft(long productSkuId, long extraPrice, Integer maxQty, boolean onSale, Long supplyPrice) {
    }

    /** 추가 구성상품 — 다른 상품의 단품·이 공구에서의 가격·한도(null = 없음)·공급가(null = 기본 공급가) */
    public record AddonDraft(long productSkuId, long price, Integer maxQty, Long supplyPrice) {
    }

    /** 대표 이미지(저장소 키 — 올리기·다시 인코딩은 관리자 이미지 API, 결정 2) */
    public record ImageRef(String imageKey, String thumbKey) {
    }
}
