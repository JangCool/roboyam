package com.community.site.entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * 운영 주체 단계(docs/features/terms-operator-contract.md) — 개인(사업자등록 전) → 개인사업자 → 법인. 약관 속 호칭은 개인·개인사업자 「운영자」, 법인 「회사」.
 * 켤 수 있는 기능: 개인 = 무료 커뮤니티·애드센스, 개인사업자 이상 = 광고 직접 판매, 통신판매업 신고까지 마치면 공구·충전·상점(판매 스위치 조건 — {@code CommerceSalesService}).
 */
public enum SiteOperatorStage {

    INDIVIDUAL,
    SOLE_PROPRIETOR,
    CORPORATION;

    /** 사업자 단계인가(개인사업자·법인) — 사업자 칸이 있다 */
    public boolean business() {
        return this != INDIVIDUAL;
    }

    /** 약관 속 호칭(약관 본문이 한국어다) */
    public String alias() {
        return this == CORPORATION ? "회사" : "운영자";
    }

    /** 이름으로 찾는다. 모르면 비어 있다 */
    public static Optional<SiteOperatorStage> of(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(stage -> stage.name().equals(name)).findFirst();
    }
}
