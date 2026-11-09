package com.community.cache.definition;

import static com.community.cache.definition.CacheKind.GADGET;
import static com.community.cache.definition.CacheKind.INTERNAL_RULE;
import static com.community.cache.definition.CacheKind.PUBLIC_VIEW;
import static com.community.cache.definition.CacheKind.SETTINGS_COPY;
import static com.community.cache.definition.CacheMemberMode.SHARED;
import static com.community.cache.definition.CacheMemberMode.SHARED_WITH_MEMBER_OVERLAY;

import com.community.cache.definition.CacheDefinition.AdminLink;
import com.community.cache.definition.CacheDefinition.Usage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 캐시 카탈로그 25개(공개 화면 6 · 가젯 7 · 설정 사본 7 · 내부 규칙 5) — 코드가 원천이다. 차례가 관리자 목록 차례다(docs/features/cache-admin-contract.md 「캐시 목록」).
 * TTL·켜기/끄기·세대 번호는 DB({@code cache_settings})에 두고, 행이 없으면 이 정의의 기본값(TTL 기본값 · 켜짐 · 세대 0)이다.
 */
public final class CacheCatalog {

    private static final List<CacheServerRole> API = List.of(CacheServerRole.API);
    private static final List<CacheServerRole> BOTH = List.of(CacheServerRole.API, CacheServerRole.ADMIN);
    private static final long SMALL = 100;
    private static final long LARGE = 1_000;

    // 관련 관리 화면(관리자 메뉴 util-admin-menu.ts 에 있는 주소만)
    private static final AdminLink THEME = new AdminLink("theme", "/settings/theme");
    private static final AdminLink GENERAL = new AdminLink("general", "/settings/general");
    private static final AdminLink MENUS = new AdminLink("menus", "/menus/public");
    private static final AdminLink BOARDS = new AdminLink("boards", "/boards");
    private static final AdminLink GRADES = new AdminLink("grades", "/grades");
    private static final AdminLink NOTICES = new AdminLink("notices", "/notices");
    private static final AdminLink POPUPS = new AdminLink("popups", "/content/popups");
    private static final AdminLink HIGHLIGHTS = new AdminLink("highlights", "/content/highlights");
    private static final AdminLink POPULAR = new AdminLink("popular", "/content/popular");
    private static final AdminLink POPULAR_SETTINGS = new AdminLink("popularSettings", "/content/popular/settings");
    private static final AdminLink TERMS = new AdminLink("terms", "/settings/terms");
    private static final AdminLink ANALYTICS = new AdminLink("analytics", "/settings/analytics");
    private static final AdminLink COMMERCE = new AdminLink("commerce", "/settings/commerce");
    private static final AdminLink HOTDEAL_DOMAINS = new AdminLink("hotdealDomains", "/hotdeals/domains");
    private static final AdminLink HOTDEAL_SETTINGS = new AdminLink("hotdealSettings", "/hotdeals/settings");
    private static final AdminLink MEMBER_MESSAGES = new AdminLink("memberMessages", "/settings/notification-messages/member");
    private static final AdminLink MAIL_TEMPLATES = new AdminLink("mailTemplates", "/settings/notification-templates/mail");
    private static final AdminLink POINT_POLICY = new AdminLink("pointPolicy", "/points/policy");
    private static final AdminLink GROUPS = new AdminLink("groups", "/groups");
    private static final AdminLink GROUP_BUYS = new AdminLink("groupBuys", "/group-buys");
    private static final AdminLink HOTDEALS = new AdminLink("hotdeals", "/hotdeals");

    private static final List<CacheDefinition> ALL = List.of(
            // 공개 화면 — 배치 캐시에는 가젯 구성·위치·설정만(내용은 가젯 데이터 캐시). 바로가기·안내 글은 설정 값뿐이라 배치 캐시에 함께 담긴다
            view(CacheNames.HOME_LAYOUT, 300, SHARED, List.of(area("/")), List.of(THEME)),
            view(CacheNames.PAGES_LAYOUT, 300, SHARED, List.of(area("/p/community")), List.of(THEME)),
            view(CacheNames.BOARDS_LAYOUT, 300, SHARED, List.of(area("/boards/free")), List.of(THEME)),
            view(CacheNames.HOME_LATEST, 60, SHARED_WITH_MEMBER_OVERLAY, List.of(area("/latest")), List.of(BOARDS)),
            view(CacheNames.HOME_POPULAR, 120, SHARED_WITH_MEMBER_OVERLAY, List.of(area("/popular")), List.of(POPULAR, POPULAR_SETTINGS)),
            view(CacheNames.MENU_PUBLIC, 300, SHARED_WITH_MEMBER_OVERLAY, List.of(area("/"), area("/boards")), List.of(MENUS)),
            // 가젯 데이터 — 항목 키 = 가젯 설정 지문. 글 줄(차단·메모)·내 상태가 있는 종류만 회원 덧입힘
            gadget("POST_LIST", 60, SHARED_WITH_MEMBER_OVERLAY, List.of(THEME)),
            gadget("HIGHLIGHT", 120, SHARED_WITH_MEMBER_OVERLAY, List.of(THEME, HIGHLIGHTS)),
            gadget("GALLERY", 60, SHARED, List.of(THEME)),
            gadget("BOARD_CARDS", 120, SHARED_WITH_MEMBER_OVERLAY, List.of(THEME)),
            gadget("GROUPS", 300, SHARED_WITH_MEMBER_OVERLAY, List.of(THEME, GROUPS)),
            gadget("GROUP_BUY", 60, SHARED, List.of(THEME, GROUP_BUYS)),
            gadget("HOT_DEALS", 60, SHARED_WITH_MEMBER_OVERLAY, List.of(THEME, HOTDEALS, HOTDEAL_SETTINGS)),
            // 설정 사본 — 예전에 서버마다 직접 만든 메모리 사본(TTL_SECONDS)을 옮긴 것(기본 TTL 은 그때 값)
            copy(CacheNames.SITE_INFO, SETTINGS_COPY, 20, true, API, List.of(area("/"), area("/"), server()), List.of(GENERAL, THEME)),
            copy(CacheNames.SITE_NOTICE_BAR, SETTINGS_COPY, 20, true, API, List.of(area("/")), List.of(GENERAL)),
            copy(CacheNames.SITE_POPUPS, SETTINGS_COPY, 20, true, API, List.of(area("/")), List.of(POPUPS)),
            copy(CacheNames.TERMS_PUBLIC, SETTINGS_COPY, 20, true, API, List.of(area("/"), area("/terms"), action("/signup", false)), List.of(TERMS)),
            copy(CacheNames.NOTICE_GLOBAL, SETTINGS_COPY, 15, false, API, List.of(area("/boards/free")), List.of(NOTICES)),
            copy(CacheNames.BOARD_CATALOG, SETTINGS_COPY, 15, false, BOTH,
                    List.of(area("/boards/free"), area("/boards"), action("/boards/free", false), action("/write", true)), List.of(BOARDS, GRADES)),
            copy(CacheNames.STATS_COLLECTION, SETTINGS_COPY, 30, false, BOTH, List.of(server(), server()), List.of(ANALYTICS)),
            // 내부 규칙
            copy(CacheNames.COMMERCE_RATE_LIMITS, INTERNAL_RULE, 15, false, BOTH,
                    List.of(sample(CacheUsageKind.ACTION, CacheSampleKind.POINT_SHOP_ITEM, "/point-shop/{id}"),
                            sample(CacheUsageKind.ACTION, CacheSampleKind.GROUP_BUY, "/group-buys/{id}"),
                            sample(CacheUsageKind.ACTION, CacheSampleKind.GROUP_BUY, "/group-buys/{id}")), List.of(COMMERCE)),
            copy(CacheNames.HOTDEAL_RULES, INTERNAL_RULE, 15, false, BOTH,
                    List.of(sample(CacheUsageKind.ACTION, CacheSampleKind.HOTDEAL_POST, "/posts/{id}"), action("/write?board=hotdeal", true)), List.of(HOTDEAL_DOMAINS)),
            copy(CacheNames.HOTDEAL_SETTINGS, INTERNAL_RULE, 15, false, BOTH,
                    List.of(area("/boards/hotdeal"), area("/p/community"), sample(CacheUsageKind.AREA, CacheSampleKind.HOTDEAL_POST, "/posts/{id}"), server()),
                    List.of(HOTDEAL_SETTINGS, HOTDEALS)),
            copy(CacheNames.NOTIFICATION_TEMPLATES, INTERNAL_RULE, 15, false, BOTH, List.of(areaLogin("/"), areaLogin("/notifications"), server()),
                    List.of(MEMBER_MESSAGES, MAIL_TEMPLATES)),
            copy(CacheNames.POINT_POLICY, INTERNAL_RULE, 15, false, BOTH, List.of(areaLogin("/me/points"), server(), server()), List.of(POINT_POLICY)));

    private static final Map<String, CacheDefinition> BY_KEY;

    static {
        Map<String, CacheDefinition> map = new LinkedHashMap<>();
        for (CacheDefinition d : ALL) {
            if (map.put(d.key(), d) != null) {
                throw new IllegalStateException("캐시 키가 겹친다: " + d.key());
            }
        }
        BY_KEY = map;
    }

    private CacheCatalog() {
    }

    /** 모든 정의(목록 차례) */
    public static List<CacheDefinition> all() {
        return ALL;
    }

    public static Optional<CacheDefinition> find(String key) {
        return Optional.ofNullable(key == null ? null : BY_KEY.get(key));
    }

    /** 이 서버 종류가 두는 정의 */
    public static List<CacheDefinition> on(CacheServerRole role) {
        List<CacheDefinition> out = new ArrayList<>();
        for (CacheDefinition d : ALL) {
            if (d.on(role)) {
                out.add(d);
            }
        }
        return out;
    }

    /** 가젯 데이터 캐시를 두는 가젯 종류인가 */
    public static boolean cachedGadget(String gadgetKind) {
        return gadgetKind != null && BY_KEY.containsKey(CacheNames.gadget(gadgetKind));
    }

    /** 가젯 데이터 캐시 키 모두 */
    public static List<String> gadgetKeys() {
        return ALL.stream().filter(d -> d.gadgetKind() != null).map(CacheDefinition::key).toList();
    }

    // ── 줄임 ─────────────────────────────────────────────────────────────────

    private static CacheDefinition view(String key, int ttl, CacheMemberMode mode, List<Usage> usages, List<AdminLink> links) {
        return new CacheDefinition(key, PUBLIC_VIEW, API, ttl, mode, true, null, usages, links, LARGE);
    }

    private static CacheDefinition gadget(String gadgetKind, int ttl, CacheMemberMode mode, List<AdminLink> links) {
        return new CacheDefinition(CacheNames.gadget(gadgetKind), GADGET, API, ttl, mode, false, gadgetKind, List.of(), links, LARGE);
    }

    private static CacheDefinition copy(String key, CacheKind kind, int ttl, boolean targetIsPath, List<CacheServerRole> servers, List<Usage> usages,
                                        List<AdminLink> links) {
        return new CacheDefinition(key, kind, servers, ttl, SHARED, targetIsPath, null, usages, links, SMALL);
    }

    private static Usage area(String path) {
        return new Usage(CacheUsageKind.AREA, path, null, null, false);
    }

    private static Usage areaLogin(String path) {
        return new Usage(CacheUsageKind.AREA, path, null, null, true);
    }

    private static Usage action(String path, boolean loginRequired) {
        return new Usage(CacheUsageKind.ACTION, path, null, null, loginRequired);
    }

    private static Usage server() {
        return new Usage(CacheUsageKind.SERVER, null, null, null, false);
    }

    private static Usage sample(CacheUsageKind kind, CacheSampleKind sampleKind, String template) {
        return new Usage(kind, null, sampleKind, template, false);
    }
}
