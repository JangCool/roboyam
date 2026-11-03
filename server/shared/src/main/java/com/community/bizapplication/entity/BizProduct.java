package com.community.bizapplication.entity;

import java.util.Optional;

/**
 * 요금표·자리의 단위(biz_price_rules.product) — 관리자 화면은 이 이름 그대로({@code BizAdType}), 공개 화면은 광고 종류 {@code WIDE·STRIP·SQUARE·SMALL·WING·TEXT_LINE} ·
 * 노출 자리 {@code HOME_TOP·CATEGORY_TOP} 이름으로 주고받는다({@link #publicName()}·{@link #ofPublic}). {@link #HOTDEAL_POST} 는 건당이고 한도가 없다.
 */
public enum BizProduct {
    WIDE_BANNER("WIDE"),
    STRIP_BANNER("STRIP"),
    SQUARE_BANNER("SQUARE"),
    SMALL_BANNER("SMALL"),
    /** 날개 배너 — 본문 바깥 좌우 세로 160×600(4:15), V116(ad-contract 「공개 노출 목업」 확정 2026-11-01) */
    WING_BANNER("WING"),
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
        return this == WIDE_BANNER || this == STRIP_BANNER || this == SQUARE_BANNER || this == SMALL_BANNER || this == WING_BANNER;
    }

    /** 배너 규격 비율 허용 폭 ±3%(docs/plans/biz-ad-hotdeal/02-ad.md 미디어 업로드 — 공개 자리는 규격 틀에 맞춰 잘라 보인다). 화면(user-web `UtilBusiness`·admin `UtilAd`)과 같은 값 */
    public static final double BANNER_RATIO_TOLERANCE = 0.03;

    /**
     * 배너 그림이 이 상품의 규격 비율(넓은 1200×300 · 띠 1200×100 · 네모 300×250 · 작은 300×100 · 날개 160×600)과 맞는지(±3%).
     * 배너가 아니거나 크기를 모르는 파일(옛 파일)이면 true.
     */
    public boolean bannerRatioOk(Integer width, Integer height) {
        double ratio = switch (this) {
            case WIDE_BANNER -> 1200.0 / 300;
            case STRIP_BANNER -> 1200.0 / 100;
            case SQUARE_BANNER -> 300.0 / 250;
            case SMALL_BANNER -> 300.0 / 100;
            case WING_BANNER -> 160.0 / 600;
            default -> 0;
        };
        if (ratio == 0 || width == null || height == null || width <= 0 || height <= 0) {
            return true;
        }
        return Math.abs((double) width / height / ratio - 1) <= BANNER_RATIO_TOLERANCE + 1e-9; // 경계(딱 3%)는 부동소수 오차 없이 통과
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
