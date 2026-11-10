package com.community.cache.definition;

/**
 * 캐시 키(= Spring Cache 이름) — 공개 화면의 {@code data-cache-area} 값(user-web {@code util-cache-area.ts})과 같아야 한다. 정의는 {@link CacheCatalog}.
 */
public final class CacheNames {

    // 공개 화면
    public static final String HOME_LAYOUT = "home.layout";
    public static final String PAGES_LAYOUT = "pages.layout";
    public static final String BOARDS_LAYOUT = "boards.layout";
    public static final String HOME_LATEST = "home.latest";
    public static final String HOME_POPULAR = "home.popular";
    public static final String MENU_PUBLIC = "menu.public";
    // 가젯 데이터 — 유형별(서버 GadgetKinds)
    public static final String GADGET_PREFIX = "gadget.";
    public static final String GADGET_POST_LIST = "gadget.POST_LIST";
    public static final String GADGET_HIGHLIGHT = "gadget.HIGHLIGHT";
    public static final String GADGET_GALLERY = "gadget.GALLERY";
    public static final String GADGET_BOARD_CARDS = "gadget.BOARD_CARDS";
    public static final String GADGET_GROUPS = "gadget.GROUPS";
    public static final String GADGET_GROUP_BUY = "gadget.GROUP_BUY";
    public static final String GADGET_HOT_DEALS = "gadget.HOT_DEALS";
    // 설정 사본
    public static final String SITE_INFO = "site.info";
    public static final String SITE_NOTICE_BAR = "site.noticeBar";
    public static final String SITE_POPUPS = "site.popups";
    public static final String TERMS_PUBLIC = "terms.public";
    /** 오픈소스 라이선스 고지의 안내 문구·직접 추가 항목(자동 수집 목록은 정적 파일이라 캐시가 아니다) */
    public static final String OSS_NOTICE = "oss.notice";
    public static final String NOTICE_GLOBAL = "notice.global";
    public static final String BOARD_CATALOG = "board.catalog";
    public static final String STATS_COLLECTION = "stats.collection";
    // 내부 규칙
    public static final String COMMERCE_RATE_LIMITS = "commerce.rateLimits";
    public static final String HOTDEAL_RULES = "hotdeal.rules";
    public static final String HOTDEAL_SETTINGS = "hotdeal.settings";
    public static final String NOTIFICATION_TEMPLATES = "notification.templates";
    public static final String POINT_POLICY = "point.policy";

    /** 값 하나만 두는 캐시(사본)의 항목 키 */
    public static final String SINGLE = "all";

    private CacheNames() {
    }

    /** 가젯 종류의 가젯 데이터 캐시 이름 */
    public static String gadget(String gadgetKind) {
        return GADGET_PREFIX + gadgetKind;
    }
}
