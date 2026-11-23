package com.community.menu.service;

import com.community.menu.dto.AdminMenuNode;
import com.community.permission.entity.Permission;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 관리자 화면 레지스트리 — admin-web {@code UtilAdminMenu.ITEMS}(메뉴 정의)를 서버에 옮긴 것이다. 잎 화면의 키 → 주소·권한, 그리고 저장한 적 없을 때 돌려줄 <b>기본 구성</b>
 * (모드·묶음·부모 항목·아이콘·차례)이 여기서 나온다. 화면을 더하면 admin-web 메뉴 정의와 함께 한 줄 더한다.
 *
 * <p>화면 항목의 권한은 이 표가 정하고 저장 요청으로 바꿀 수 없다. 이번 단계에서 관리자 셸은 여전히 admin-web 의 메뉴 정의를 읽는다(구성은 저장만 된다).
 */
public final class AdminScreenRegistry {

    public static final String NORMAL = "normal";
    public static final String SETTINGS = "settings";
    public static final List<String> MODES = List.of(NORMAL, SETTINGS);

    /**
     * 잎 화면 하나.
     *
     * @param permission 보는 데 필요한 권한(없으면 null — 로그인한 누구나)
     * @param superOnly  최고 관리자(루트) 전용 — 권한 코드로 위임하지 않는 화면(로그인 보안). admin-web 메뉴 정의의 {@code superOnly: true} 와 같다
     */
    public record Screen(String key, String path, String labelKey, String icon, Permission permission, boolean superOnly) {
    }

    /** 기본 구성의 노드 — 묶음·부모 항목·화면 */
    private record Def(String kind, String mode, String labelKey, String icon, Screen screen, List<Def> children) {
    }

    private static final List<Def> ITEMS = List.of(
            leaf(NORMAL, s("dashboard", "/", "menu.dashboard", "pi pi-home", null)),
            section(NORMAL, "menu.visitStats", "pi pi-chart-line",
                    leaf(s("visitOverview", "/analytics/visits", "menu.visitOverview", "pi pi-chart-line", Permission.ANALYTICS_READ)),
                    // 3단계 월별 보기(site-analytics-contract 「결정 ④ 화면 확정」 ⓒ — 개요 바로 아래). 저장한 메뉴 구성에는 admin V10 가 넣는다
                    leaf(s("visitMonthly", "/analytics/visits/monthly", "menu.visitMonthly", "pi pi-calendar", Permission.ANALYTICS_READ)),
                    leaf(s("visitPages", "/analytics/visits/pages", "menu.visitPages", "pi pi-file", Permission.ANALYTICS_READ)),
                    leaf(s("visitReferrers", "/analytics/visits/referrers", "menu.visitReferrers", "pi pi-directions", Permission.ANALYTICS_READ)),
                    leaf(s("visitFlows", "/analytics/visits/flows", "menu.visitFlows", "pi pi-sitemap", Permission.ANALYTICS_READ)),
                    leaf(s("visitTime", "/analytics/visits/time", "menu.visitTime", "pi pi-calendar-clock", Permission.ANALYTICS_READ)),
                    leaf(s("visitRealtime", "/analytics/visits/realtime", "menu.visitRealtime", "pi pi-bolt", Permission.ANALYTICS_READ)),
                    // 2단계(site-analytics-contract 「2단계」, 화면 확정 2026-11-04) — 화면 util-admin-menu.ts 와 같은 키·주소·아이콘·차례
                    leaf(s("visitEnvironment", "/analytics/visits/environment", "menu.visitEnvironment", "pi pi-mobile", Permission.ANALYTICS_READ)),
                    leaf(s("visitSearch", "/analytics/visits/search", "menu.visitSearch", "pi pi-search", Permission.ANALYTICS_READ)),
                    leaf(s("visitBots", "/analytics/visits/bots", "menu.visitBots", "pi pi-microchip-ai", Permission.ANALYTICS_READ)),
                    folder("menu.visitManage", "pi pi-cog",
                            s("visitHealth", "/analytics/visits/health", "menu.visitHealth", "pi pi-wave-pulse", Permission.ANALYTICS_READ),
                            s("visitRequests", "/analytics/visits/requests", "menu.visitRequests", "pi pi-eraser", Permission.ANALYTICS_READ),
                            // 통계 수집(site-analytics-contract) — 방문·알림·게시판 집계를 항목별로 켜고 끈다(모두 기본 꺼짐). 조회 site.read, 저장은 화면 안에서 site.manage.
                            // admin-web UtilAdminMenu 의 analyticsSettings 와 같은 키·주소·문구 키·아이콘·권한·차례(조직 구성 다음 — 약관은 「운영·약관」 묶음으로 옮겼다)
                            s("analyticsSettings", "/settings/analytics", "menu.analyticsSettings", "pi pi-chart-bar", Permission.ANALYTICS_READ))),
            section(NORMAL, "menu.member", "pi pi-users",
                    leaf(s("members", "/members", "menu.members", "pi pi-user", Permission.MEMBER_READ)),
                    // 탈퇴 관리(member-withdrawal-contract 「관리자」) — 주소가 /members 아래지만 다른 화면. admin-web util-admin-menu 와 같은 키·주소·권한·아이콘·차례
                    leaf(s("memberWithdrawals", "/members/withdrawals", "menu.memberWithdrawals", "pi pi-user-minus", Permission.MEMBER_READ)),
                    leaf(s("grades", "/grades", "menu.grades", "pi pi-trophy", Permission.GRADE_READ)),
                    // 회원 칭호(member-title-contract 「화면」 — 칭호·확인할 일·이달의 작가 뽑기·주고 뺀 기록 탭) — 등급 바로 아래, 보기 title.read(화면 안에서 title.manage·title.review).
                    // 저장한 메뉴 구성에는 admin V11 이 넣는다. admin-web util-admin-menu 와 같은 키·주소·권한·아이콘·차례
                    leaf(s("memberTitles", "/members/titles", "menu.memberTitles", "pi pi-star", Permission.TITLE_READ)),
                    folder("menu.imageReviews", "pi pi-image",
                            s("nicknameImages", "/members/nickname-images", "menu.nicknameImages", "pi pi-image", Permission.NICKNAME_IMAGE_READ),
                            // 아바타 올리기 심사(commerce-motion-avatar-contract) — 닉네임 이미지 심사와 같은 권한. admin-web util-admin-menu 와 같은 키·주소·권한·아이콘·차례
                            s("avatarUploads", "/members/avatar-uploads", "menu.avatarUploads", "pi pi-upload", Permission.NICKNAME_IMAGE_READ),
                            s("nicknameImageSettings", "/settings/nickname-image", "menu.nicknameImageSettings", "pi pi-image", Permission.NICKNAME_IMAGE_READ))),
            section(NORMAL, "menu.safety", "pi pi-shield",
                    leaf(s("reports", "/reports", "menu.reports", "pi pi-flag", Permission.REPORT_READ)),
                    // 제재·이의제기(docs/features/member-sanction-contract.md) — 둘 다 보기 sanction.read(처리는 화면 안에서 sanction.manage·sanction.ban·appeal.manage)
                    leaf(s("sanctions", "/sanctions", "menu.sanctions", "pi pi-ban", Permission.SANCTION_READ)),
                    // 게시판 제재(docs/features/board-sanction-contract.md) — 이력·반복 검토, 보기 sanction.read(해제·무효·검토 닫기는 화면 안에서 sanction.manage)
                    leaf(s("boardSanctions", "/board-sanctions", "menu.boardSanctions", "pi pi-hammer", Permission.SANCTION_READ)),
                    // 스텝 처리 감독(docs/features/board-staff-action-contract.md) — 모든 게시판의 매니저·스텝 글·댓글 처리, 보기 keeper-action.read(되돌리기는 화면 안에서 keeper-action.restore)
                    leaf(s("staffActions", "/staff-actions", "menu.staffActions", "pi pi-eye", Permission.KEEPER_ACTION_READ)),
                    leaf(s("appeals", "/appeals", "menu.appeals", "pi pi-comments", Permission.SANCTION_READ))),
            section(NORMAL, "menu.boardContent", "pi pi-list",
                    leaf(s("boards", "/boards", "menu.boards", "pi pi-th-large", Permission.BOARD_READ)),
                    folder("menu.boardDefaults", "pi pi-sliders-v",
                            s("boardDefaultsGeneral", "/boards/defaults/general", "menu.boardDefaultsGeneral", null, Permission.BOARD_READ),
                            s("boardDefaultsReport", "/boards/defaults/report", "menu.boardDefaultsReport", null, Permission.BOARD_READ),
                            s("boardDefaultsSecurity", "/boards/defaults/security", "menu.boardDefaultsSecurity", null, Permission.BOARD_READ),
                            s("boardDefaultsNotifications", "/boards/defaults/notifications", "menu.boardDefaultsNotifications", null, Permission.BOARD_READ),
                            s("boardDefaultsPrivacy", "/boards/defaults/privacy", "menu.boardDefaultsPrivacy", null, Permission.BOARD_READ)),
                    leaf(s("posts", "/posts", "menu.posts", "pi pi-file-edit", Permission.POST_READ)),
                    // 공지 관리(docs/features/notice-admin-contract.md) — 공지 게시판 글 쓰기·고치기·고정·삭제, 보기 post.read(화면 안에서 쓰기 post.manage)
                    leaf(s("notices", "/notices", "menu.notices", "pi pi-megaphone", Permission.POST_READ)),
                    leaf(s("highlights", "/content/highlights", "menu.highlights", "pi pi-sparkles", Permission.HIGHLIGHT_READ)),
                    folder("menu.popular", "pi pi-thumbs-up",
                            s("popularPosts", "/content/popular", "menu.popularPosts", "pi pi-thumbs-up", Permission.POPULAR_READ),
                            s("popularSettings", "/content/popular/settings", "menu.popularSettings", "pi pi-sliders-h", Permission.POPULAR_READ)),
                    // 팝업 관리(docs/features/site-popup-contract.md) — 보기 site.read(바꾸기는 화면 안에서 site.manage)
                    leaf(s("sitePopups", "/content/popups", "menu.popups", "pi pi-window-maximize", Permission.POPUP_READ))),
            section(NORMAL, "menu.group", "pi pi-comments",
                    leaf(s("groups", "/groups", "menu.groups", "pi pi-users", Permission.GROUP_READ)),
                    leaf(s("groupRequests", "/group-requests", "menu.groupRequests", "pi pi-inbox", Permission.GROUP_READ)),
                    leaf(s("groupCategories", "/group-categories", "menu.groupCategories", "pi pi-tags", Permission.GROUP_READ)),
                    leaf(s("groupSettings", "/group-settings", "menu.groupSettings", "pi pi-sliders-h", Permission.GROUP_READ))),
            section(NORMAL, "menu.aiMember", "pi pi-microchip-ai",
                    leaf(s("aiMembers", "/ai-members", "menu.aiMembers", "pi pi-users", Permission.AI_MEMBER_READ)),
                    leaf(s("aiMemberDrafts", "/ai-members/drafts", "menu.aiMemberDrafts", "pi pi-inbox", Permission.AI_MEMBER_READ)),
                    leaf(s("aiMemberLogs", "/ai-members/logs", "menu.aiMemberLogs", "pi pi-history", Permission.AI_MEMBER_READ)),
                    // 프롬프트(2026-11-11 — 지시문·분위기·뉴스 글·글 모양, 저장 ai-member.manage). admin 스트림 V9 가 저장한 구성에도 넣는다
                    leaf(s("aiMemberPrompts", "/ai-members/prompts", "menu.aiMemberPrompts", "pi pi-pen-to-square", Permission.AI_MEMBER_READ)),
                    leaf(s("aiMemberSettings", "/ai-members/settings", "menu.aiMemberSettings", "pi pi-cog", Permission.AI_MEMBER_READ))),
            section(NORMAL, "menu.pointAndShop", "pi pi-wallet",
                    folder("menu.point", "pi pi-wallet",
                            s("points", "/points", "menu.points", "pi pi-receipt", Permission.POINT_READ),
                            s("pointPolicy", "/points/policy", "menu.pointPolicy", "pi pi-percentage", Permission.POINT_READ)),
                    folder("menu.pointShop", "pi pi-shopping-bag",
                            s("pointShopItems", "/point-shop/items", "menu.pointShopItems", "pi pi-box", Permission.POINTSHOP_READ),
                            s("pointShopPurchases", "/point-shop/purchases", "menu.pointShopPurchases", "pi pi-receipt", Permission.POINTSHOP_READ),
                            s("pointShopSettings", "/point-shop/settings", "menu.pointShopSettings", "pi pi-sliders-h", Permission.POINTSHOP_READ)),
                    folder("menu.emoticon", "pi pi-face-smile",
                            s("emoticonReviews", "/robotikon/reviews", "menu.emoticonReviews", "pi pi-check-square", Permission.EMOTICON_READ),
                            s("emoticonPacks", "/robotikon/packs", "menu.emoticonPacks", "pi pi-images", Permission.EMOTICON_READ),
                            // 판매 내역(S3 — 조회·예외 환불 emoticon.sales) · 작가 정산(S4 — 조회·확정·보류·지급 기록 emoticon.settlement, 계좌 원문 보기는 화면 안에서 emoticon.payout.pii)
                            s("emoticonSales", "/robotikon/sales", "menu.emoticonSales", "pi pi-receipt", Permission.EMOTICON_SALES),
                            s("emoticonSettlements", "/robotikon/settlements", "menu.emoticonSettlements", "pi pi-money-bill", Permission.EMOTICON_SETTLEMENT),
                            s("emoticonSettings", "/robotikon/settings", "menu.emoticonSettings", "pi pi-sliders-h", Permission.EMOTICON_READ)),
                    leaf(s("rewardEvents", "/reward-events", "menu.rewardEvents", "pi pi-gift", Permission.REWARD_EVENT_READ))),
            section(NORMAL, "menu.ad", "pi pi-megaphone",
                    leaf(s("adReviews", "/ad-reviews", "menu.adReviews", "pi pi-inbox", Permission.AD_READ)),
                    // 광고 자리 관리(ad-slot-manager-contract — 공개 화면 미리보기에서 자리를 눌러 광고를 찾는다. 조회 ad.read, 내리기는 화면 안에서 ad.takedown · 등록 ad.manage)
                    leaf(s("adSlots", "/ads/slots", "menu.adSlots", "pi pi-th-large", Permission.AD_READ)),
                    leaf(s("ads", "/ads", "menu.ads", "pi pi-calendar", Permission.AD_READ)),
                    // 광고 성과(ad-performance-contract — 읽기 전용, 조회 ad.read · 새 권한 없음)
                    leaf(s("adPerformance", "/ads/performance", "menu.adPerformance", "pi pi-chart-bar", Permission.AD_READ)),
                    // 광고주 관리(ad-advertiser-contract — 조회 ad.read, 처리는 화면 안에서 ad.manage · 연락처 평문 ad.contact.reveal)
                    leaf(s("adAdvertisers", "/ad-advertisers", "menu.adAdvertisers", "pi pi-address-book", Permission.AD_READ)),
                    folder("menu.trial", "pi pi-box",
                            // 체험단(trial-group-contract 「구현 결정」 10) — 모집 목록·만들기·뽑기·입고·발송·확인할 일(trial.manage), 체험단 설정(trial.settings).
                            // 저장한 메뉴 구성에는 admin V12 가 넣는다. admin-web util-admin-menu 와 같은 키·주소·권한·아이콘·차례
                            s("adTrials", "/ads/trials", "menu.adTrials", "pi pi-box", Permission.TRIAL_READ),
                            s("adTrialSettings", "/ads/trials/settings", "menu.adTrialSettings", "pi pi-cog", Permission.TRIAL_READ)),
                    // 노출 자리 설정(글 보기 중간 · 날개 · 목록 한 줄) — 조회 ad.read, 저장은 화면 안에서 ad.settings
                    leaf(s("adPlacements", "/ads/placements", "menu.adPlacements", "pi pi-sliders-h", Permission.AD_READ))),
            section(NORMAL, "menu.bizHotdeal", "pi pi-briefcase",
                    leaf(s("businessReviews", "/business-reviews", "menu.businessReviews", "pi pi-inbox", Permission.BUSINESS_READ)),
                    leaf(s("businesses", "/businesses", "menu.businesses", "pi pi-building", Permission.BUSINESS_READ)),
                    folder("menu.hotdeal", "pi pi-ticket",
                            s("hotdealReviews", "/hotdeal-reviews", "menu.hotdealReviews", "pi pi-inbox", Permission.HOTDEAL_READ),
                            s("hotdeals", "/hotdeals", "menu.hotdeals", "pi pi-ticket", Permission.HOTDEAL_READ),
                            s("hotdealDomains", "/hotdeals/domains", "menu.hotdealDomains", "pi pi-link", Permission.HOTDEAL_READ),
                            s("hotdealSuspicions", "/hotdeals/suspicions", "menu.hotdealSuspicions", "pi pi-exclamation-triangle", Permission.HOTDEAL_READ),
                            s("hotdealSettings", "/hotdeals/settings", "menu.hotdealSettings", "pi pi-sliders-h", Permission.HOTDEAL_READ))),
            section(NORMAL, "menu.groupBuy", "pi pi-shop",
                    leaf(s("groupBuys", "/group-buys", "menu.groupBuys", "pi pi-shopping-bag", Permission.COMMERCE_PRODUCT_READ)),
                    folder("menu.groupBuyProductMenu", "pi pi-tag",
                            s("groupBuyProducts", "/group-buy-products", "menu.groupBuyProducts", "pi pi-tag", Permission.COMMERCE_PRODUCT_READ),
                            s("groupBuyOptionTypes", "/group-buy-option-types", "menu.groupBuyOptionTypes", "pi pi-list-check", Permission.COMMERCE_PRODUCT_READ),
                            s("groupBuyCategories", "/group-buy-categories", "menu.groupBuyCategories", "pi pi-sitemap", Permission.COMMERCE_PRODUCT_READ),
                            s("groupBuyStock", "/group-buy-stock", "menu.groupBuyStock", "pi pi-warehouse", Permission.COMMERCE_PRODUCT_READ)),
                    folder("menu.groupBuyOrderMenu", "pi pi-truck",
                            s("groupBuyOrders", "/group-buy-orders", "menu.groupBuyOrders", "pi pi-truck", Permission.COMMERCE_ORDER_READ),
                            s("groupBuyRequests", "/group-buy-requests", "menu.groupBuyRequests", "pi pi-replay", Permission.COMMERCE_ORDER_READ)),
                    folder("menu.groupBuyVoiceMenu", "pi pi-comments",
                            s("groupBuyQna", "/group-buy-qna", "menu.groupBuyQna", "pi pi-comments", Permission.COMMERCE_ORDER_READ),
                            s("groupBuyReviews", "/group-buy-reviews", "menu.groupBuyReviews", "pi pi-star", Permission.COMMERCE_ORDER_READ))),
            section(NORMAL, "menu.payment", "pi pi-credit-card",
                    leaf(s("paymentOrders", "/payments/orders", "menu.paymentOrders", "pi pi-receipt", Permission.COMMERCE_PAYMENT_READ)),
                    leaf(s("paymentWebhooks", "/payments/webhooks", "menu.paymentWebhooks", "pi pi-inbox", Permission.COMMERCE_PAYMENT_READ)),
                    leaf(s("paymentAnomalies", "/payments/anomalies", "menu.paymentAnomalies", "pi pi-exclamation-triangle", Permission.COMMERCE_PAYMENT_READ)),
                    folder("menu.reconcile", "pi pi-arrow-right-arrow-left",
                            s("paymentReconcileDays", "/payments/reconcile-days", "menu.paymentReconcileDays", "pi pi-calendar", Permission.COMMERCE_PAYMENT_READ),
                            s("paymentReconciliation", "/payments/reconciliation", "menu.paymentReconciliation", "pi pi-arrow-right-arrow-left", Permission.COMMERCE_PAYMENT_READ)),
                    leaf(s("commerceSettings", "/settings/commerce", "menu.commerceSettings", "pi pi-sliders-h", Permission.COMMERCE_SETTINGS))),
            section(SETTINGS, "menu.site", "pi pi-cog",
                    leaf(s("siteGeneral", "/settings/general", "menu.general", "pi pi-sliders-h", Permission.SITE_READ)),
                    leaf(s("siteOperator", "/settings/operator", "menu.siteOperator", "pi pi-id-card", Permission.SITE_READ)),
                    leaf(s("terms", "/settings/terms", "menu.terms", "pi pi-file-check", Permission.TERMS_READ)),
                    // 오픈소스 라이선스 고지(oss-licenses-contract) — 안내 문구·직접 추가 항목·자동 수집 목록. 조회 site.read, 저장은 화면 안에서 site.manage.
                    // admin-web UtilAdminMenu 의 settingsSite 묶음과 같은 키·주소·권한·아이콘·차례(약관 다음)
                    leaf(s("ossLicenses", "/settings/oss-licenses", "menu.ossLicenses", "pi pi-code", Permission.SITE_READ))),
            section(SETTINGS, "menu.screenLayout", "pi pi-palette",
                    leaf(s("themeLayout", "/settings/theme", "menu.themeLayout", "pi pi-objects-column", Permission.LAYOUT_READ)),
                    leaf(s("menusPublic", "/menus/public", "menu.menusPublic", "pi pi-globe", Permission.LAYOUT_READ))),
            section(SETTINGS, "menu.operatorsAndPermissions", "pi pi-shield",
                    leaf(s("operators", "/settings/operators", "menu.operators", "pi pi-user-edit", Permission.OPERATOR_READ)),
                    folder("menu.roles", "pi pi-key",
                            s("rolesMember", "/settings/roles/member", "menu.rolesMember", null, Permission.ROLE_READ),
                            s("rolesOperator", "/settings/roles/operator", "menu.rolesOperator", null, Permission.ROLE_READ),
                            s("rolesGrade", "/settings/roles/grade", "menu.rolesGrade", null, Permission.ROLE_READ)),
                    leaf(s("teams", "/settings/teams", "menu.teams", "pi pi-sitemap", Permission.TEAM_READ)),
                    leaf(s("menusAdmin", "/menus/admin", "menu.menusAdmin", "pi pi-desktop", Permission.LAYOUT_READ)),
                    // 로그인 보안(관리자 2차 인증 — commerce-foundation-contract 「0-3」) — 루트 전용, 권한 코드 없음
                    leaf(superOnly("loginSecurity", "/settings/login-security", "menu.loginSecurity", "pi pi-lock"))),
            section(SETTINGS, "menu.notificationMail", "pi pi-bell",
                    leaf(s("mailSettings", "/settings/mail", "menu.mailSettings", "pi pi-server", Permission.SITE_READ)),
                    leaf(s("notificationChannels", "/settings/notification-channels", "menu.notificationChannels", "pi pi-send", Permission.NOTIFICATION_READ)),
                    folder("menu.notificationTemplates", "pi pi-palette",
                            s("mailTemplates", "/settings/notification-templates/mail", "menu.mailTemplates", null, Permission.TEMPLATE_READ),
                            s("slackTemplates", "/settings/notification-templates/slack", "menu.slackTemplates", null, Permission.TEMPLATE_READ),
                            s("mattermostTemplates", "/settings/notification-templates/mattermost", "menu.mattermostTemplates", null, Permission.TEMPLATE_READ),
                            s("kakaoTemplates", "/settings/notification-templates/kakao", "menu.kakaoTemplates", null, Permission.TEMPLATE_READ)),
                    folder("menu.notificationMessages", "pi pi-file-edit",
                            s("operatorMessages", "/settings/notification-messages/operator", "menu.operatorMessages", null, Permission.TEMPLATE_READ),
                            s("memberMessages", "/settings/notification-messages/member", "menu.memberMessages", null, Permission.TEMPLATE_READ),
                            s("requiredMails", "/settings/notification-messages/required-mails", "menu.requiredMails", null, Permission.TEMPLATE_READ)),
                    folder("menu.events", "pi pi-bolt",
                            s("operatorEvents", "/settings/notifications/operator", "menu.eventsOperator", null, Permission.NOTIFICATION_READ),
                            s("memberEvents", "/settings/notifications/member", "menu.eventsMember", null, Permission.NOTIFICATION_READ))),
            section(SETTINGS, "menu.integrations", "pi pi-link",
                    leaf(s("socialLogin", "/settings/social-login", "menu.socialLogin", "pi pi-sign-in", Permission.SITE_READ)),
                    // 포트원 설정(본인인증 칸 — commerce-foundation-contract 「0-4」) — 조회 site.read, 저장·지우기는 화면 안에서 site.secret
                    leaf(s("portone", "/settings/portone", "menu.portone", "pi pi-credit-card", Permission.SITE_READ)),
                    // 공공데이터포털(국세청 사업자 조회 서비스 키 — business-contract) — 조회·연결 시험 site.read, 저장·지우기는 화면 안에서 site.secret
                    leaf(s("publicData", "/settings/public-data", "menu.publicData", "pi pi-building-columns", Permission.SITE_READ)),
                    // 주소 검색(address-search-contract) — 우편번호 찾기 제공처·자체 주소 DB 올리기. 조회 site.read, 저장·올리기는 화면 안에서 site.manage
                    leaf(s("addressSearch", "/settings/address", "menu.addressSearch", "pi pi-map-marker", Permission.SITE_READ)),
                    // 휴일 관리(사이트 쉬는 날 표 하나 — 공공데이터 공휴일·회사 휴무일, 사용자 지시 2026-11-05) — 조회 site.read, 회사 휴무일 더하기·지우기·지금 받기는 화면 안에서 site.manage.
                    // admin-web util-admin-menu 와 같은 키·주소·문구 키·아이콘·권한·차례
                    leaf(s("holidays", "/settings/holidays", "menu.holidays", "pi pi-calendar", Permission.SITE_READ))),
            section(SETTINGS, "menu.batchAndLogs", "pi pi-history",
                    leaf(s("batches", "/settings/batches", "menu.batches", "pi pi-clock", Permission.BATCH_READ)),
                    leaf(s("batchLogs", "/settings/batch-logs", "menu.batchLogs", "pi pi-list", Permission.BATCH_READ)),
                    // 캐시 관리(docs/features/cache-admin-contract.md) — 보기 cache.read, 바꾸기 cache.manage
                    leaf(s("caches", "/settings/caches", "menu.caches", "pi pi-database", Permission.CACHE_READ)),
                    leaf(s("audit", "/settings/audit", "menu.audit", "pi pi-eye", Permission.AUDIT_READ))));

    private static final Map<String, Screen> SCREENS = collectScreens();
    /**
     * 메뉴 정리(2026-11-23 — docs/features/menu-contract.md 「관리자 메뉴 정리」) 전의 묶음 이름 키. 기본 구성에는 없지만 <b>그 전에 저장한 메뉴 구성</b>이 쓰고 있어
     * 다시 저장할 때 거절하지 않는다(이름 문구도 admin-web {@code menu.ts} 에 남긴다).
     */
    private static final Set<String> LEGACY_LABEL_KEYS = Set.of("menu.siteMenu", "menu.board", "menu.content", "menu.rewardEvent", "menu.business", "menu.system", "menu.legal",
            "menu.theme", "menu.memberSettings", "menu.mail", "menu.notifications", "menu.commerce");
    private static final Set<String> LABEL_KEYS = collectLabelKeys();

    private AdminScreenRegistry() {
    }

    /** 화면 키로 찾는다 */
    public static Optional<Screen> screen(String key) {
        return key == null ? Optional.empty() : Optional.ofNullable(SCREENS.get(key));
    }

    /** 묶음·부모 항목의 기본 이름 키인가(옛 묶음 이름 키 포함) */
    public static boolean isGroupLabelKey(String labelKey) {
        return LABEL_KEYS.contains(labelKey) || LEGACY_LABEL_KEYS.contains(labelKey);
    }

    /** 저장한 적 없을 때의 구성(모드별, ID 없음) — admin-web {@code UtilAdminMenuConfig.seed()} 와 같은 모양 */
    public static Map<String, List<AdminMenuNode>> defaults() {
        Map<String, List<AdminMenuNode>> out = new LinkedHashMap<>();
        MODES.forEach(mode -> out.put(mode, new ArrayList<>()));
        for (Def def : ITEMS) {
            out.get(def.mode()).add(toNode(def, false));
        }
        return out;
    }

    private static AdminMenuNode toNode(Def def, boolean underFolder) {
        if (def.screen() != null) {
            return new AdminMenuNode(null, AdminMenuNode.SCREEN, null, null, null, underFolder ? null : def.screen().icon(), true, AdminMenuNode.ADMIN_SCREEN,
                    def.screen().key(), null, true, null, List.of());
        }
        boolean folder = AdminMenuNode.FOLDER.equals(def.kind());
        List<AdminMenuNode> children = def.children().stream().map(child -> toNode(child, folder)).toList();
        return new AdminMenuNode(null, def.kind(), def.labelKey(), null, null, def.icon(), true, null, null, null, true, null, children);
    }

    private static Screen s(String key, String path, String labelKey, String icon, Permission permission) {
        return new Screen(key, path, labelKey, icon, permission, false);
    }

    /** 최고 관리자(루트) 전용 화면 — 권한 코드가 없다 */
    private static Screen superOnly(String key, String path, String labelKey, String icon) {
        return new Screen(key, path, labelKey, icon, null, true);
    }

    private static Def leaf(Screen screen) {
        return new Def(AdminMenuNode.SCREEN, null, null, null, screen, List.of());
    }

    private static Def leaf(String mode, Screen screen) {
        return new Def(AdminMenuNode.SCREEN, mode, null, null, screen, List.of());
    }

    private static Def section(String mode, String labelKey, String icon, Def... children) {
        return new Def(AdminMenuNode.SECTION, mode, labelKey, icon, null, List.of(children));
    }

    private static Def folder(String labelKey, String icon, Screen... screens) {
        return new Def(AdminMenuNode.FOLDER, null, labelKey, icon, null, java.util.Arrays.stream(screens).map(AdminScreenRegistry::leaf).toList());
    }

    private static Map<String, Screen> collectScreens() {
        Map<String, Screen> out = new LinkedHashMap<>();
        collect(ITEMS, out, new HashSet<>());
        return Map.copyOf(out);
    }

    private static Set<String> collectLabelKeys() {
        Set<String> keys = new HashSet<>();
        collect(ITEMS, new LinkedHashMap<>(), keys);
        return Set.copyOf(keys);
    }

    private static void collect(List<Def> defs, Map<String, Screen> screens, Set<String> labelKeys) {
        for (Def def : defs) {
            if (def.screen() != null) {
                if (screens.put(def.screen().key(), def.screen()) != null) {
                    throw new IllegalStateException("관리자 화면 키가 겹친다: " + def.screen().key());
                }
            } else {
                labelKeys.add(def.labelKey());
                collect(def.children(), screens, labelKeys);
            }
        }
    }
}
