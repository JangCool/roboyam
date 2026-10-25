package com.community.permission.entity;

/**
 * 권한 묶음(세부 유형) 23개 — {@code docs/domain/operator-permissions.md} 의 "역할 유형" 표에서 권한이 하나도 없던 금칙어({@code badword})를 뺐다(2026-10-24). 운영자 역할 하나는 세부 유형 하나에 속하고
 * 그 유형의 권한만 후보로 고른다. 종합 관리({@link #GENERAL})는 유형 제한 없이 모든 권한 중에서 고른다.
 * id 는 admin-web 의 {@code util-permission-catalog.ts} {@code TYPES} 와 같고, 역할 유형 식별자는 {@code op_<id>} 다.
 */
public enum PermissionType {

    NOTICE("notice", PermissionArea.CONTENT),
    POST("post", PermissionArea.CONTENT),
    BOARD("board", PermissionArea.CONTENT),
    SITE_LAYOUT("siteLayout", PermissionArea.CONTENT),
    REPORT("report", PermissionArea.SAFETY),
    SANCTION("sanction", PermissionArea.SAFETY),
    STAFF_AUDIT("staffAudit", PermissionArea.SAFETY),
    MEMBER_VIEW("memberView", PermissionArea.MEMBER),
    NICKNAME_IMAGE("nicknameImage", PermissionArea.MEMBER),
    GRADE_POINT("gradePoint", PermissionArea.MEMBER),
    CLUB("club", PermissionArea.CLUB),
    HOTDEAL("hotdeal", PermissionArea.HOTDEAL),
    GB_PLAN("gbPlan", PermissionArea.GROUPBUY),
    GB_ORDER("gbOrder", PermissionArea.GROUPBUY),
    GB_SETTLE("gbSettle", PermissionArea.GROUPBUY),
    GB_CONFIG("gbConfig", PermissionArea.GROUPBUY),
    BIZ_REVIEW("bizReview", PermissionArea.BIZ_AD),
    BIZ_PRODUCT("bizProduct", PermissionArea.BIZ_AD),
    AD_PROMO("adPromo", PermissionArea.BIZ_AD),
    OPERATOR_ROLE("operatorRole", PermissionArea.SYSTEM),
    AUDIT_BATCH("auditBatch", PermissionArea.SYSTEM),
    SITE_INTEGRATION("siteIntegration", PermissionArea.SYSTEM),
    GENERAL("general", PermissionArea.GENERAL);

    private final String id;
    private final PermissionArea area;

    PermissionType(String id, PermissionArea area) {
        this.id = id;
        this.area = area;
    }

    public String id() {
        return id;
    }

    public PermissionArea area() {
        return area;
    }

    /** 종합 관리 유형인가 — 유형 제한 없이 권한을 고르고 최고 관리자만 만들고 고친다 */
    public boolean isGeneral() {
        return this == GENERAL;
    }
}
