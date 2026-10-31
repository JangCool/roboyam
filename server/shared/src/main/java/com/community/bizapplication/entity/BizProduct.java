package com.community.bizapplication.entity;

import java.util.Optional;

/**
 * 요금표·자리의 단위(biz_price_rules.product) — 관리자 화면은 이 이름 그대로({@code BizAdType}), 공개 화면은 광고 종류 {@code WIDE·STRIP·SQUARE·SMALL·TEXT_LINE} ·
 * 노출 자리 {@code HOME_TOP·CATEGORY_TOP} 이름으로 주고받는다({@link #publicName()}·{@link #ofPublic}). {@link #HOTDEAL_POST} 는 건당이고 한도가 없다.
 */
public enum BizProduct {
    WIDE_BANNER("WIDE"),
    STRIP_BANNER("STRIP"),
    SQUARE_BANNER("SQUARE"),
    SMALL_BANNER("SMALL"),
    TEXT_LINE("TEXT_LINE"),
    HOTDEAL_TOP("HOME_TOP"),
    HOTDEAL_CATEGORY("CATEGORY_TOP"),
    HOTDEAL_POST(null);

    private final String publicName;

    BizProduct(String publicName) {
        this.publicName = publicName;
    }

    /** 공개 화면의 이름(광고 종류·노출 자리). 핫딜 게시는 null */
    public String publicName() {
        return publicName;
    }

    public boolean banner() {
        return this == WIDE_BANNER || this == STRIP_BANNER || this == SQUARE_BANNER || this == SMALL_BANNER;
    }

    public boolean adProduct() {
        return banner() || this == TEXT_LINE;
    }

    public boolean placement() {
        return this == HOTDEAL_TOP || this == HOTDEAL_CATEGORY;
    }

    /** 공개 이름 → 상품(모르면 비어 있다) */
    public static Optional<BizProduct> ofPublic(String name) {
        if (name == null) {
            return Optional.empty();
        }
        for (BizProduct p : values()) {
            if (name.equals(p.publicName)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }
}
