package com.community.groupbuy.dto;

import java.util.List;

/**
 * 상품정보제공고시 양식 하나(계약 결정 87) — 품목 코드(카탈로그), 제목(예 「본체」), 적용 단품 이름(비면 공구 전체 = 기본 상품 — MAIN 단품만 덮는다, 추가 구성상품은 단품마다 따로 양식 필수. 단품 ID 는 DRAFT 수정 때 바뀌므로 이름으로 가리킨다),
 * 항목 값. 값을 비운 채 저장할 수는 있지만 모집 시작 때 모든 고시 항목이 「값」 또는 「제공 불가 + 사유」여야 한다.
 * {@code values} 에는 판매자 추가 항목({@link Value#custom} — 키 없이 이름·내용·차례, 선택 사항)도 함께 담는다.
 */
public record NoticeDraft(String categoryCode, String title, List<String> skuNames, List<Value> values) {

    /**
     * 항목 값.
     * <ul>
     *   <li>고시 항목: {@code itemKey} + {@code value} — {@code unavailable} 이면 값 없이 {@code unavailableReason}(구체적 사유, 일반원칙 ③).</li>
     *   <li>판매자 추가 항목({@code custom = true}): 키 없이 {@code name}(1~40자)·{@code value}(1~1,000자 평문)·{@code sortOrder}(차례 — 비면 넣은 차례). 고시 항목과 같은 이름은 안 된다.
     *       이름과 내용이 모두 빈 줄은 저장하지 않는다.</li>
     * </ul>
     */
    public record Value(String itemKey, String value, boolean unavailable, String unavailableReason, boolean custom, String name, Integer sortOrder) {

        /** 고시 항목 값 */
        public Value(String itemKey, String value, boolean unavailable, String unavailableReason) {
            this(itemKey, value, unavailable, unavailableReason, false, null, null);
        }

        /** 판매자 추가 항목 */
        public static Value custom(String name, String value, Integer sortOrder) {
            return new Value(null, value, false, null, true, name, sortOrder);
        }
    }
}
