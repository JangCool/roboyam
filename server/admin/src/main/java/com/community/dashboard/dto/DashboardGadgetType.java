package com.community.dashboard.dto;

import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * 관리자 대시보드 가젯 종류 34 — 화면 종류 목록(admin-web {@code useDashboardLayoutApi.types.ts} 의 {@code DASHBOARD_GADGET_TYPES}, 차례도 같다 · 칸 값은 {@code util-dashboard-layout.ts} 의 {@code KINDS_12})의 서버 사본. 한쪽을 고치면 같이 고친다.
 *
 * <ul>
 *   <li>{@code permissions}: 이 중 하나라도 있으면 쓸 수 있다(비면 누구나). 가젯 데이터 묶음 조회가 종류마다 이것으로 거른다.</li>
 *   <li>{@code minW}: 48칸 격자의 최소 폭 — 종류표의 12칸 기준 값을 화면과 같이 {@code ceil(minW × 4 × 2 / 3)} 한 값(3 → 8, 2 → 6).</li>
 *   <li>{@code data}: 데이터 묶음 조회({@code GET /api/admin/dashboard/gadgets}) 대상인가 — 처리할 일·커머스 처리할 일은 기존 API 를, 메모·바로가기는 서버 데이터가 없다.</li>
 * </ul>
 */
public enum DashboardGadgetType {

    TODO(List.of(Permission.AD_READ, Permission.HOTDEAL_READ, Permission.BUSINESS_READ), false, true, Set.of(), 3, false),
    COMMERCE_TODO(List.of(Permission.COMMERCE_ORDER_READ, Permission.COMMERCE_PRODUCT_READ, Permission.COMMERCE_PAYMENT_READ), false, true, Set.of(), 3, false),
    TODAY_SCHEDULE(List.of(Permission.AD_READ, Permission.COMMERCE_PRODUCT_READ, Permission.POINTSHOP_READ, Permission.BATCH_READ), false, true, Set.of(), 3, true),
    REPORTS(List.of(Permission.REPORT_READ), false, true, Set.of(Option.LIMIT), 3, true),
    POST_ACTIVITY(List.of(Permission.POST_READ), false, true, Set.of(), 3, true),
    RECENT_POSTS(List.of(Permission.POST_READ), false, true, Set.of(Option.LIMIT), 3, true),
    BOARD_RANKING(List.of(Permission.POST_READ), false, true, Set.of(), 3, true),
    POPULAR_NOW(List.of(Permission.POPULAR_READ), false, true, Set.of(Option.LIMIT), 3, true),
    VISITORS(List.of(Permission.SITE_READ), false, true, Set.of(), 3, true),
    // 방문 분석(site-analytics-contract 2026-11-04) — 인기 페이지(limit 5|10) · 유입 경로. 화면 DASHBOARD_GADGET_TYPES 와 같은 차례(방문 현황 바로 뒤)
    POPULAR_PAGES(List.of(Permission.SITE_READ), false, true, Set.of(Option.LIMIT), 3, true),
    REFERRERS(List.of(Permission.SITE_READ), false, true, Set.of(), 3, true),
    GROUP_REQUESTS(List.of(Permission.GROUP_READ), false, true, Set.of(Option.LIMIT), 3, true),
    MEMBER_STATS(List.of(Permission.MEMBER_READ), false, true, Set.of(), 3, true),
    SIGNUP_TREND(List.of(Permission.MEMBER_READ), false, true, Set.of(Option.DAYS), 3, true),
    RECENT_SIGNUPS(List.of(Permission.MEMBER_READ), false, true, Set.of(Option.LIMIT), 3, true),
    SANCTIONS(List.of(Permission.SANCTION_READ), false, true, Set.of(), 3, true),
    NICKNAME_IMAGES(List.of(Permission.MEMBER_READ), false, true, Set.of(), 3, true),
    HOTDEAL_STATUS(List.of(Permission.HOTDEAL_READ), false, true, Set.of(), 3, true),
    HOTDEAL_TOP(List.of(Permission.HOTDEAL_READ), false, true, Set.of(), 3, true),
    AD_PERFORMANCE(List.of(Permission.AD_READ), false, true, Set.of(), 3, true),
    AD_SCHEDULE(List.of(Permission.AD_READ), false, true, Set.of(), 3, true),
    GROUPBUY_PROGRESS(List.of(Permission.COMMERCE_PRODUCT_READ), false, true, Set.of(Option.LIMIT), 3, true),
    GROUPBUY_QNA(List.of(Permission.COMMERCE_PRODUCT_READ), false, true, Set.of(Option.LIMIT), 3, true),
    SALES_SUMMARY(List.of(Permission.COMMERCE_PAYMENT_READ), false, true, Set.of(), 3, true),
    ORDER_TREND(List.of(Permission.COMMERCE_PAYMENT_READ), false, true, Set.of(Option.DAYS), 3, true),
    RECONCILE(List.of(Permission.COMMERCE_PAYMENT_READ), false, true, Set.of(), 3, true),
    POINT_SUMMARY(List.of(Permission.POINT_READ), false, true, Set.of(), 3, true),
    POINTSHOP(List.of(Permission.POINTSHOP_READ), false, true, Set.of(), 3, true),
    BATCH_HEALTH(List.of(Permission.BATCH_READ), false, true, Set.of(), 3, true),
    NOTIFICATION_HEALTH(List.of(Permission.NOTIFICATION_READ), false, true, Set.of(), 3, true),
    AUDIT_RECENT(List.of(Permission.AUDIT_READ), false, true, Set.of(Option.LIMIT), 3, true),
    OPERATOR_SECURITY(List.of(Permission.OPERATOR_READ), false, true, Set.of(), 3, true),
    MEMO(List.of(), true, false, Set.of(Option.TEXT), 2, false),
    SHORTCUTS(List.of(), true, false, Set.of(Option.LINKS), 2, false);

    /** ⚙ 설정의 종류별 칸(제목·배경색은 모든 종류, 자동 새로 고침은 {@code refreshable} 인 종류) */
    public enum Option {
        LIMIT,
        DAYS,
        TEXT,
        LINKS
    }

    /** 격자 칸 수 */
    public static final int COLUMNS = 48;

    private final List<Permission> permissions;
    private final boolean multiple;
    private final boolean refreshable;
    private final Set<Option> options;
    private final int minW;
    private final boolean data;

    DashboardGadgetType(List<Permission> permissions, boolean multiple, boolean refreshable, Set<Option> options, int minW12, boolean data) {
        this.permissions = permissions;
        this.multiple = multiple;
        this.refreshable = refreshable;
        this.options = options;
        // 화면 계산 그대로: 12칸 기준 최소 폭을 48칸(×4)으로 늘리되 3분의 2 만큼만(3 → 8, 2 → 6)
        this.minW = (int) Math.ceil(minW12 * (COLUMNS / 12) * 2 / 3.0);
        this.data = data;
    }

    /** 이름이 같은 종류(대소문자 구분). 없으면 null */
    public static DashboardGadgetType of(String name) {
        if (name == null) {
            return null;
        }
        return Arrays.stream(values()).filter(t -> t.name().equals(name)).findFirst().orElse(null);
    }

    /** 이 운영자가 쓸 수 있나 — 필요 권한 중 하나라도 있으면(권한이 없는 종류는 누구나) */
    public boolean allowed(AdminActor actor) {
        return permissions.isEmpty() || permissions.stream().anyMatch(actor::can);
    }

    public List<Permission> permissions() {
        return permissions;
    }

    public boolean multiple() {
        return multiple;
    }

    public boolean refreshable() {
        return refreshable;
    }

    public boolean has(Option option) {
        return options.contains(option);
    }

    public int minW() {
        return minW;
    }

    public boolean data() {
        return data;
    }
}
