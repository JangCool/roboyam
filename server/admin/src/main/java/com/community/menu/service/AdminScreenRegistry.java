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
            // 방문자 통계 묶음(site-analytics-contract 「「방문자 통계」 화면」) — 대시보드 바로 아래, 모두 site.read. 수집 설정(/settings/analytics)은 설정 메뉴 그대로.
            // admin-web util-admin-menu 와 같은 키·주소·권한·아이콘·차례
            section(NORMAL, "menu.visitStats", "pi pi-chart-line",
                    leaf(s("visitOverview", "/analytics/visits", "menu.visitOverview", "pi pi-chart-line", Permission.SITE_READ)),
                    leaf(s("visitPages", "/analytics/visits/pages", "menu.visitPages", "pi pi-file", Permission.SITE_READ)),
                    leaf(s("visitReferrers", "/analytics/visits/referrers", "menu.visitReferrers", "pi pi-directions", Permission.SITE_READ)),
                    leaf(s("visitFlows", "/analytics/visits/flows", "menu.visitFlows", "pi pi-sitemap", Permission.SITE_READ)),
                    leaf(s("visitTime", "/analytics/visits/time", "menu.visitTime", "pi pi-calendar-clock", Permission.SITE_READ)),
                    leaf(s("visitRealtime", "/analytics/visits/realtime", "menu.visitRealtime", "pi pi-bolt", Permission.SITE_READ)),
                    // 2단계(site-analytics-contract 「2단계」, 화면 확정 2026-11-04) — 화면 util-admin-menu.ts 와 같은 키·주소·아이콘·차례
                    leaf(s("visitEnvironment", "/analytics/visits/environment", "menu.visitEnvironment", "pi pi-mobile", Permission.SITE_READ)),
                    leaf(s("visitSearch", "/analytics/visits/search", "menu.visitSearch", "pi pi-search", Permission.SITE_READ)),
                    leaf(s("visitBots", "/analytics/visits/bots", "menu.visitBots", "pi pi-microchip-ai", Permission.SITE_READ)),
                    leaf(s("visitHealth", "/analytics/visits/health", "menu.visitHealth", "pi pi-wave-pulse", Permission.SITE_READ)),
                    leaf(s("visitRequests", "/analytics/visits/requests", "menu.visitRequests", "pi pi-eraser", Permission.SITE_READ))),
            section(NORMAL, "menu.member", "pi pi-users",
                    leaf(s("members", "/members", "menu.members", "pi pi-user", Permission.MEMBER_READ)),
                    // 탈퇴 관리(member-withdrawal-contract 「관리자」) — 주소가 /members 아래지만 다른 화면. admin-web util-admin-menu 와 같은 키·주소·권한·아이콘·차례
                    leaf(s("memberWithdrawals", "/members/withdrawals", "menu.memberWithdrawals", "pi pi-user-minus", Permission.MEMBER_READ)),
                    leaf(s("grades", "/grades", "menu.grades", "pi pi-trophy", Permission.GRADE_READ)),
                    leaf(s("nicknameImages", "/members/nickname-images", "menu.nicknameImages", "pi pi-image", Permission.NICKNAME_IMAGE_READ)),
                    // 아바타 올리기 심사(commerce-motion-avatar-contract) — 닉네임 이미지 심사와 같은 권한. admin-web util-admin-menu 와 같은 키·주소·권한·아이콘·차례
                    leaf(s("avatarUploads", "/members/avatar-uploads", "menu.avatarUploads", "pi pi-upload", Permission.NICKNAME_IMAGE_READ))),
            section(NORMAL, "menu.siteMenu", "pi pi-bars",
                    leaf(s("menusPublic", "/menus/public", "menu.menusPublic", "pi pi-globe", Permission.LAYOUT_READ)),
                    leaf(s("menusAdmin", "/menus/admin", "menu.menusAdmin", "pi pi-desktop", Permission.LAYOUT_READ))),
            section(NORMAL, "menu.board", "pi pi-list",
                    leaf(s("boards", "/boards", "menu.boards", "pi pi-th-large", Permission.BOARD_READ)),
                    folder("menu.boardDefaults", "pi pi-sliders-v",
                            s("boardDefaultsGeneral", "/boards/defaults/general", "menu.boardDefaultsGeneral", null, Permission.BOARD_READ),
                            s("boardDefaultsReport", "/boards/defaults/report", "menu.boardDefaultsReport", null, Permission.BOARD_READ),
                            s("boardDefaultsSecurity", "/boards/defaults/security", "menu.boardDefaultsSecurity", null, Permission.BOARD_READ),
                            s("boardDefaultsNotifications", "/boards/defaults/notifications", "menu.boardDefaultsNotifications", null, Permission.BOARD_READ),
                            s("boardDefaultsPrivacy", "/boards/defaults/privacy", "menu.boardDefaultsPrivacy", null, Permission.BOARD_READ))),
            // 커뮤니티 안전 묶음 — 신고 관리(docs/features/report-admin-contract.md). admin-web UtilAdminMenu 와 같은 키·주소·권한
            section(NORMAL, "menu.safety", "pi pi-shield",
                    leaf(s("reports", "/reports", "menu.reports", "pi pi-flag", Permission.REPORT_READ)),
                    // 제재·이의제기(docs/features/member-sanction-contract.md) — 둘 다 보기 sanction.read(처리는 화면 안에서 sanction.manage·sanction.ban·appeal.manage)
                    leaf(s("sanctions", "/sanctions", "menu.sanctions", "pi pi-ban", Permission.SANCTION_READ)),
                    // 게시판 제재(docs/features/board-sanction-contract.md) — 이력·반복 검토, 보기 sanction.read(해제·무효·검토 닫기는 화면 안에서 sanction.manage)
                    leaf(s("boardSanctions", "/board-sanctions", "menu.boardSanctions", "pi pi-hammer", Permission.SANCTION_READ)),
                    // 스텝 처리 감독(docs/features/board-staff-action-contract.md) — 모든 게시판의 매니저·스텝 글·댓글 처리, 보기 keeper-action.read(되돌리기는 화면 안에서 keeper-action.restore)
                    leaf(s("staffActions", "/staff-actions", "menu.staffActions", "pi pi-eye", Permission.KEEPER_ACTION_READ)),
                    leaf(s("appeals", "/appeals", "menu.appeals", "pi pi-comments", Permission.SANCTION_READ))),
            // 콘텐츠 묶음 — 게시글 관리(docs/features/post-admin-contract.md)·하이라이트·인기글 관리·인기글 설정(docs/features/popular-highlight-contract.md)·팝업 관리.
            // admin-web UtilAdminMenu 와 같은 키·주소·권한
            section(NORMAL, "menu.content", "pi pi-folder-open",
                    leaf(s("posts", "/posts", "menu.posts", "pi pi-file-edit", Permission.POST_READ)),
                    // 공지 관리(docs/features/notice-admin-contract.md) — 공지 게시판 글 쓰기·고치기·고정·삭제, 보기 post.read(화면 안에서 쓰기 post.manage)
                    leaf(s("notices", "/notices", "menu.notices", "pi pi-megaphone", Permission.POST_READ)),
                    leaf(s("highlights", "/content/highlights", "menu.highlights", "pi pi-sparkles", Permission.HIGHLIGHT_READ)),
                    leaf(s("popularPosts", "/content/popular", "menu.popularPosts", "pi pi-thumbs-up", Permission.POPULAR_READ)),
                    leaf(s("popularSettings", "/content/popular/settings", "menu.popularSettings", "pi pi-sliders-h", Permission.POPULAR_READ)),
                    // 팝업 관리(docs/features/site-popup-contract.md) — 콘텐츠 묶음 맨 끝, 보기 site.read(바꾸기는 화면 안에서 site.manage)
                    leaf(s("sitePopups", "/content/popups", "menu.popups", "pi pi-window-maximize", Permission.SITE_READ))),
            section(NORMAL, "menu.point", "pi pi-wallet",
                    leaf(s("points", "/points", "menu.points", "pi pi-receipt", Permission.POINT_READ)),
                    leaf(s("pointPolicy", "/points/policy", "menu.pointPolicy", "pi pi-percentage", Permission.POINT_READ))),
            // 포인트 상점 묶음(커머스 1-2 — commerce-stage1-contract 「1-2 화면 목업」) — 포인트 묶음 바로 다음, 공동구매와 따로. 세 화면 모두 조회 pointshop.read
            // (화면 안에서 저장 pointshop.manage · 예외 환불 pointshop.refund). admin-web UtilAdminMenu 의 pointShop 묶음과 같은 키·주소·권한·아이콘·차례
            section(NORMAL, "menu.pointShop", "pi pi-shopping-bag",
                    leaf(s("pointShopItems", "/point-shop/items", "menu.pointShopItems", "pi pi-box", Permission.POINTSHOP_READ)),
                    leaf(s("pointShopPurchases", "/point-shop/purchases", "menu.pointShopPurchases", "pi pi-receipt", Permission.POINTSHOP_READ)),
                    leaf(s("pointShopSettings", "/point-shop/settings", "menu.pointShopSettings", "pi pi-sliders-h", Permission.POINTSHOP_READ))),
            // 로보티콘 묶음(docs/features/emoticon-contract.md S1) — 포인트 상점 묶음 바로 다음. 세 화면 모두 조회 emoticon.read(화면 안에서 승인·반려·판매 중단·운영 팩 emoticon.review ·
            // 설정 저장 emoticon.settings). admin-web UtilAdminMenu 의 emoticon 묶음과 같은 키·주소·권한·아이콘·차례 — 판매 내역·작가 정산(emoticon.sales·emoticon.settlement)은 S3·S4 에서 더했다
            section(NORMAL, "menu.emoticon", "pi pi-face-smile",
                    leaf(s("emoticonReviews", "/robotikon/reviews", "menu.emoticonReviews", "pi pi-check-square", Permission.EMOTICON_READ)),
                    leaf(s("emoticonPacks", "/robotikon/packs", "menu.emoticonPacks", "pi pi-images", Permission.EMOTICON_READ)),
                    // 판매 내역(S3 — 조회·예외 환불 emoticon.sales) · 작가 정산(S4 — 조회·확정·보류·지급 기록 emoticon.settlement, 계좌 원문 보기는 화면 안에서 emoticon.payout.pii)
                    leaf(s("emoticonSales", "/robotikon/sales", "menu.emoticonSales", "pi pi-receipt", Permission.EMOTICON_SALES)),
                    leaf(s("emoticonSettlements", "/robotikon/settlements", "menu.emoticonSettlements", "pi pi-money-bill", Permission.EMOTICON_SETTLEMENT)),
                    leaf(s("emoticonSettings", "/robotikon/settings", "menu.emoticonSettings", "pi pi-sliders-h", Permission.EMOTICON_READ))),
            // 보상 이벤트 묶음(docs/features/event-admin-contract.md 「화면」) — 로보티콘 묶음 바로 다음. 조회 reward-event.read(화면 안 처리는 reward-event.manage·reward-event.grant·
            // point.adjust.large). admin-web util-admin-menu 의 rewardEvent 묶음과 같은 키·주소·권한·아이콘·차례
            section(NORMAL, "menu.rewardEvent", "pi pi-gift",
                    leaf(s("rewardEvents", "/reward-events", "menu.rewardEvents", "pi pi-gift", Permission.REWARD_EVENT_READ))),
            // 공구 묶음(커머스 2b — commerce-groupbuy-contract 「화면 목록」·결정 89) — 결제 묶음 앞. 상품·옵션 종류·카테고리·공구·수량은 조회 commerce.product.read,
            // 주문·요청은 commerce.order.read(화면 안의 처리는 각 권한). admin-web util-admin-menu 의 groupBuy 묶음과 같은 키·주소·권한·아이콘·차례
            section(NORMAL, "menu.groupBuy", "pi pi-shop",
                    leaf(s("groupBuyProducts", "/group-buy-products", "menu.groupBuyProducts", "pi pi-tag", Permission.COMMERCE_PRODUCT_READ)),
                    leaf(s("groupBuyOptionTypes", "/group-buy-option-types", "menu.groupBuyOptionTypes", "pi pi-list-check", Permission.COMMERCE_PRODUCT_READ)),
                    leaf(s("groupBuyCategories", "/group-buy-categories", "menu.groupBuyCategories", "pi pi-sitemap", Permission.COMMERCE_PRODUCT_READ)),
                    leaf(s("groupBuys", "/group-buys", "menu.groupBuys", "pi pi-shopping-bag", Permission.COMMERCE_PRODUCT_READ)),
                    leaf(s("groupBuyStock", "/group-buy-stock", "menu.groupBuyStock", "pi pi-warehouse", Permission.COMMERCE_PRODUCT_READ)),
                    leaf(s("groupBuyOrders", "/group-buy-orders", "menu.groupBuyOrders", "pi pi-truck", Permission.COMMERCE_ORDER_READ)),
                    leaf(s("groupBuyRequests", "/group-buy-requests", "menu.groupBuyRequests", "pi pi-replay", Permission.COMMERCE_ORDER_READ)),
                    leaf(s("groupBuyQna", "/group-buy-qna", "menu.groupBuyQna", "pi pi-comments", Permission.COMMERCE_ORDER_READ)),
                    leaf(s("groupBuyReviews", "/group-buy-reviews", "menu.groupBuyReviews", "pi pi-star", Permission.COMMERCE_ORDER_READ))),
            // 결제 묶음(커머스 2a — commerce-payment-contract 결정 13·「2a-1 화면 목업」) — 포인트 상점 묶음 다음. 다섯 화면 모두 조회 commerce.payment.read
            // (화면 안의 처리 commerce.reconcile.manage). admin-web UtilAdminMenu 의 payment 묶음과 같은 키·주소·권한·아이콘·차례. PG 설정은 설정 › 포트원 그대로
            section(NORMAL, "menu.payment", "pi pi-credit-card",
                    leaf(s("paymentOrders", "/payments/orders", "menu.paymentOrders", "pi pi-receipt", Permission.COMMERCE_PAYMENT_READ)),
                    leaf(s("paymentWebhooks", "/payments/webhooks", "menu.paymentWebhooks", "pi pi-inbox", Permission.COMMERCE_PAYMENT_READ)),
                    leaf(s("paymentAnomalies", "/payments/anomalies", "menu.paymentAnomalies", "pi pi-exclamation-triangle", Permission.COMMERCE_PAYMENT_READ)),
                    leaf(s("paymentReconcileDays", "/payments/reconcile-days", "menu.paymentReconcileDays", "pi pi-calendar", Permission.COMMERCE_PAYMENT_READ)),
                    leaf(s("paymentReconciliation", "/payments/reconciliation", "menu.paymentReconciliation", "pi pi-arrow-right-arrow-left", Permission.COMMERCE_PAYMENT_READ))),
            // 소모임 묶음(docs/features/group-contract.md 「관리자 화면」) — 포인트 상점 묶음 다음. 네 화면 모두 조회 group.read(화면 안에서 처리 group.manage ·
            // 설정 group.settings). admin-web UtilAdminMenu 의 group 묶음과 같은 키·주소·권한·아이콘·차례
            section(NORMAL, "menu.group", "pi pi-comments",
                    leaf(s("groups", "/groups", "menu.groups", "pi pi-users", Permission.GROUP_READ)),
                    leaf(s("groupRequests", "/group-requests", "menu.groupRequests", "pi pi-inbox", Permission.GROUP_READ)),
                    leaf(s("groupCategories", "/group-categories", "menu.groupCategories", "pi pi-tags", Permission.GROUP_READ)),
                    leaf(s("groupSettings", "/group-settings", "menu.groupSettings", "pi pi-sliders-h", Permission.GROUP_READ))),
            // 메뉴·권한·알림은 업무(사업자 / 광고 / 핫딜)별, 신청 건 서버 엔진은 하나(docs/plans/biz-ad-hotdeal/04-ia-review.md 1-2 · S7). 차례: 소모임 → 사업자 → 광고 → 핫딜.
            // admin-web UtilAdminMenu 의 business · ad · hotdeal 묶음과 같은 키·주소·권한·차례
            // 사업자 묶음(docs/features/business-contract.md) — 인증 심사 · 사업자 목록 모두 조회 business.read(화면 안에서 처리 business.manage ·
            // 평문 열람 business.reveal · 정지 해제 business.unsuspend)
            section(NORMAL, "menu.business", "pi pi-briefcase",
                    leaf(s("businessReviews", "/business-reviews", "menu.businessReviews", "pi pi-inbox", Permission.BUSINESS_READ)),
                    leaf(s("businesses", "/businesses", "menu.businesses", "pi pi-building", Permission.BUSINESS_READ))),
            // 광고 묶음(biz-application-contract · ad-contract) — 광고 심사(AD_* 신청 건, 처리 ad.manage · 환불 deal-order.refund) · 광고 일정표(처리 ad.manage · ad.takedown · ad.restore) · 광고 성과(조회만) · 노출 자리 설정(저장 ad.settings)
            section(NORMAL, "menu.ad", "pi pi-megaphone",
                    leaf(s("adReviews", "/ad-reviews", "menu.adReviews", "pi pi-inbox", Permission.AD_READ)),
                    leaf(s("ads", "/ads", "menu.ads", "pi pi-calendar", Permission.AD_READ)),
                    // 광고 성과(ad-performance-contract — 읽기 전용, 조회 ad.read · 새 권한 없음)
                    leaf(s("adPerformance", "/ads/performance", "menu.adPerformance", "pi pi-chart-bar", Permission.AD_READ)),
                    // 광고주 관리(ad-advertiser-contract — 조회 ad.read, 처리는 화면 안에서 ad.manage · 연락처 평문 ad.contact.reveal)
                    leaf(s("adAdvertisers", "/ad-advertisers", "menu.adAdvertisers", "pi pi-address-book", Permission.AD_READ)),
                    // 노출 자리 설정(글 보기 중간 · 날개 · 목록 한 줄) — 조회 ad.read, 저장은 화면 안에서 ad.settings
                    leaf(s("adPlacements", "/ads/placements", "menu.adPlacements", "pi pi-sliders-h", Permission.AD_READ))),
            // 핫딜 묶음 — 핫딜 심사(HOTDEAL_* 신청 건, 처리 hotdeal.manage · 환불 deal-order.refund) · 회원 핫딜 1차(hotdeal-contract): 핫딜 글(종료·재개 hotdeal.manage) ·
            // 링크 규칙(처리 hotdeal.domain.manage) · 의심 후보(무시 hotdeal.manage) · 핫딜 설정(조회·저장 hotdeal.manage — 사이트 제휴 몰 표 포함)
            section(NORMAL, "menu.hotdeal", "pi pi-ticket",
                    leaf(s("hotdealReviews", "/hotdeal-reviews", "menu.hotdealReviews", "pi pi-inbox", Permission.HOTDEAL_READ)),
                    leaf(s("hotdeals", "/hotdeals", "menu.hotdeals", "pi pi-ticket", Permission.HOTDEAL_READ)),
                    leaf(s("hotdealDomains", "/hotdeals/domains", "menu.hotdealDomains", "pi pi-link", Permission.HOTDEAL_READ)),
                    leaf(s("hotdealSuspicions", "/hotdeals/suspicions", "menu.hotdealSuspicions", "pi pi-exclamation-triangle", Permission.HOTDEAL_READ)),
                    leaf(s("hotdealSettings", "/hotdeals/settings", "menu.hotdealSettings", "pi pi-sliders-h", Permission.HOTDEAL_MANAGE))),
            section(SETTINGS, "menu.system", "pi pi-cog",
                    leaf(s("siteGeneral", "/settings/general", "menu.general", "pi pi-sliders-h", Permission.SITE_READ)),
                    leaf(s("socialLogin", "/settings/social-login", "menu.socialLogin", "pi pi-sign-in", Permission.SITE_READ)),
                    // 포트원 설정(본인인증 칸 — commerce-foundation-contract 「0-4」) — 조회 site.read, 저장·지우기는 화면 안에서 site.secret
                    leaf(s("portone", "/settings/portone", "menu.portone", "pi pi-credit-card", Permission.SITE_READ)),
                    // 공공데이터포털(국세청 사업자 조회 서비스 키 — business-contract) — 조회·연결 시험 site.read, 저장·지우기는 화면 안에서 site.secret
                    leaf(s("publicData", "/settings/public-data", "menu.publicData", "pi pi-building-columns", Permission.SITE_READ)),
                    // 로그인 보안(관리자 2차 인증 — commerce-foundation-contract 「0-3」) — 루트 전용, 권한 코드 없음
                    leaf(superOnly("loginSecurity", "/settings/login-security", "menu.loginSecurity", "pi pi-lock")),
                    // 휴일 관리(사이트 쉬는 날 표 하나 — 공공데이터 공휴일·회사 휴무일, 사용자 지시 2026-11-05) — 조회 site.read, 회사 휴무일 더하기·지우기·지금 받기는 화면 안에서 site.manage.
                    // admin-web util-admin-menu 와 같은 키·주소·문구 키·아이콘·권한·차례(로그인 보안 다음)
                    leaf(s("holidays", "/settings/holidays", "menu.holidays", "pi pi-calendar", Permission.SITE_READ)),
                    // 주소 검색(address-search-contract) — 우편번호 찾기 제공처·자체 주소 DB 올리기. 조회 site.read, 저장·올리기는 화면 안에서 site.manage
                    leaf(s("addressSearch", "/settings/address", "menu.addressSearch", "pi pi-map-marker", Permission.SITE_READ)),
                    leaf(s("teams", "/settings/teams", "menu.teams", "pi pi-sitemap", Permission.TEAM_READ)),
                    // 통계 수집(site-analytics-contract) — 방문·알림·게시판 집계를 항목별로 켜고 끈다(모두 기본 꺼짐). 조회 site.read, 저장은 화면 안에서 site.manage.
                    // admin-web UtilAdminMenu 의 analyticsSettings 와 같은 키·주소·문구 키·아이콘·권한·차례(조직 구성 다음 — 약관은 「운영·약관」 묶음으로 옮겼다)
                    leaf(s("analyticsSettings", "/settings/analytics", "menu.analyticsSettings", "pi pi-chart-bar", Permission.SITE_READ))),
            // 운영·약관 묶음(terms-operator-contract, 사용자 지시 2026-11-07) — 시스템 묶음 다음. 운영 주체(개인·개인사업자·법인, 조회 site.read · 저장은 화면 안에서 site.manage) →
            // 약관(terms-contract — 이용약관·개인정보처리방침·운영정책 게시, terms.read). admin-web UtilAdminMenu 의 settingsLegal 묶음과 같은 키·주소·권한·아이콘·차례
            section(SETTINGS, "menu.legal", "pi pi-book",
                    leaf(s("siteOperator", "/settings/operator", "menu.siteOperator", "pi pi-id-card", Permission.SITE_READ)),
                    leaf(s("terms", "/settings/terms", "menu.terms", "pi pi-file-check", Permission.TERMS_READ))),
            // 테마 묶음(site-theme-layout-contract) — 테마 고르기 · 그 테마의 홈 구성 · 메뉴 설정을 한 화면에서. 조회 site.read, 저장은 화면 안에서 site.manage.
            // admin-web UtilAdminMenu 의 settingsTheme 와 같은 키·주소·권한·아이콘·차례
            section(SETTINGS, "menu.theme", "pi pi-palette",
                    leaf(s("themeLayout", "/settings/theme", "menu.themeLayout", "pi pi-objects-column", Permission.SITE_READ))),
            // 회원 설정 묶음 — 닉네임 이미지 신청 조건(member-social-contract 「닉네임 그림」). admin-web UtilAdminMenu 와 같은 키·주소·권한
            section(SETTINGS, "menu.memberSettings", "pi pi-users",
                    leaf(s("nicknameImageSettings", "/settings/nickname-image", "menu.nicknameImageSettings", "pi pi-image", Permission.NICKNAME_IMAGE_READ))),
            // 메일 묶음 — 메일 서버만(message-template-contract 「개정 3」·「개정 4」 — 메일 종류·템플릿·레이아웃은 알림·이벤트 묶음의 템플릿·문구로 옮겼다)
            section(SETTINGS, "menu.mail", "pi pi-envelope",
                    leaf(s("mailSettings", "/settings/mail", "menu.mailSettings", "pi pi-server", Permission.SITE_READ))),
            // 알림·이벤트 묶음(개정 4) — 알림 도구 · 템플릿(메일·Slack·Mattermost·카카오톡) · 문구(운영자·회원·필수 안내 메일) · 이벤트(운영자·회원). admin-web UtilAdminMenu 와 같은 키·주소·권한·차례
            section(SETTINGS, "menu.notifications", "pi pi-bell",
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
            // 커머스 묶음(커머스 0-5) — 커머스 설정(지금은 「요청 제한」 구획). admin-web UtilAdminMenu 의 settingsCommerce 와 같은 키·주소·권한·차례
            section(SETTINGS, "menu.commerce", "pi pi-shopping-cart",
                    leaf(s("commerceSettings", "/settings/commerce", "menu.commerceSettings", "pi pi-sliders-h", Permission.COMMERCE_SETTINGS))),
            section(SETTINGS, "menu.operatorsAndPermissions", "pi pi-shield",
                    leaf(s("operators", "/settings/operators", "menu.operators", "pi pi-user-edit", Permission.OPERATOR_READ)),
                    folder("menu.roles", "pi pi-key",
                            s("rolesMember", "/settings/roles/member", "menu.rolesMember", null, Permission.ROLE_READ),
                            s("rolesOperator", "/settings/roles/operator", "menu.rolesOperator", null, Permission.ROLE_READ),
                            s("rolesGrade", "/settings/roles/grade", "menu.rolesGrade", null, Permission.ROLE_READ))),
            section(SETTINGS, "menu.batchAndLogs", "pi pi-history",
                    leaf(s("batches", "/settings/batches", "menu.batches", "pi pi-clock", Permission.BATCH_READ)),
                    leaf(s("batchLogs", "/settings/batch-logs", "menu.batchLogs", "pi pi-list", Permission.BATCH_READ)),
                    // 캐시 관리(docs/features/cache-admin-contract.md) — 보기 cache.read, 바꾸기 cache.manage
                    leaf(s("caches", "/settings/caches", "menu.caches", "pi pi-database", Permission.CACHE_READ)),
                    leaf(s("audit", "/settings/audit", "menu.audit", "pi pi-eye", Permission.AUDIT_READ))));

    private static final Map<String, Screen> SCREENS = collectScreens();
    private static final Set<String> LABEL_KEYS = collectLabelKeys();

    private AdminScreenRegistry() {
    }

    /** 화면 키로 찾는다 */
    public static Optional<Screen> screen(String key) {
        return key == null ? Optional.empty() : Optional.ofNullable(SCREENS.get(key));
    }

    /** 묶음·부모 항목의 기본 이름 키인가 */
    public static boolean isGroupLabelKey(String labelKey) {
        return LABEL_KEYS.contains(labelKey);
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
