package com.community.groupbuy.dto;

import java.util.List;

/**
 * 상품정보제공고시 양식 하나(계약 결정 87) — 품목 코드(카탈로그), 제목(예 「본체」), 적용 옵션 이름(비면 공구 전체 = 기본 상품 — 옵션 ID 는 DRAFT 수정 때 바뀌므로 이름으로 가리킨다),
 * 항목 값. 값을 비운 채 저장할 수는 있지만 모집 시작 때 모든 항목이 「값」 또는 「제공 불가 + 사유」여야 한다.
 */
public record NoticeDraft(String categoryCode, String title, List<String> optionNames, List<Value> values) {

    /** 항목 값 — {@code unavailable} 이면 {@code value} 없이 {@code unavailableReason}(구체적 사유, 일반원칙 ③) */
    public record Value(String itemKey, String value, boolean unavailable, String unavailableReason) {
    }
}
