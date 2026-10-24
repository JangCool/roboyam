package com.community.permission.entity;

import static com.community.permission.entity.PermissionType.*;
import static com.community.permission.entity.PermissionUnit.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 운영자 권한 카탈로그 — <b>코드가 원천</b>이다(서버가 뜰 때 {@code permissions} 표와 맞춘다). 코드는 {@code 영역.행위}(또는 {@code 영역.대상.행위})이고
 * 행위는 {@code read}(조회) · {@code manage}(처리) · {@code settings}(설정) · 그 밖의 동사(민감 조치)다.
 * 목록·분류·{@code featureReady} 는 admin-web 의 {@code util-permission-catalog.ts} {@code PERMISSIONS} 와 같다(서로 어긋나면 화면이 모르는 코드를 받는다).
 *
 * <p>{@code featureReady} 는 그 권한이 하는 일의 화면(기능)이 이미 있는가 — 표시용("기능 준비 중")이고 false 여도 역할에 줄 수 있다.
 * 최고 관리자(루트 계정)는 역할이 아니라 계정의 속성이라 이 목록에 없고, 모든 권한을 가진다.
 */
public enum Permission {

    NOTICE_READ("notice.read", NOTICE, READ, false),
    NOTICE_MANAGE("notice.manage", NOTICE, MANAGE, false),
    NOTICE_SETTINGS("notice.settings", NOTICE, CONFIG, false),
    POST_READ("post.read", POST, READ, false),
    POST_MANAGE("post.manage", POST, MANAGE, false),
    POST_BULK_DELETE("post.bulk-delete", POST, SENSITIVE, false),
    BOARD_READ("board.read", BOARD, READ, true),
    BOARD_MANAGE("board.manage", BOARD, MANAGE, true),
    BOARD_SETTINGS("board.settings", BOARD, CONFIG, true),
    BOARD_STAFF_MANAGE("board.staff.manage", BOARD, MANAGE, true),
    BOARD_STAFF_LIMIT("board.staff.limit", BOARD, CONFIG, true),
    BOARD_DELETE("board.delete", BOARD, SENSITIVE, true),
    LAYOUT_READ("layout.read", SITE_LAYOUT, READ, true),
    LAYOUT_SETTINGS("layout.settings", SITE_LAYOUT, CONFIG, true),
    /** 관리자 사이트 메뉴 구성 저장 — 모든 운영자의 작업 화면을 바꾸므로 공개 사이트 메뉴 저장({@code layout.settings})과 따로 둔다 */
    ADMIN_MENU_MANAGE("admin-menu.manage", SITE_LAYOUT, CONFIG, true),
    REPORT_READ("report.read", REPORT, READ, false),
    REPORT_MANAGE("report.manage", REPORT, MANAGE, false),
    SANCTION_READ("sanction.read", SANCTION, READ, false),
    SANCTION_MANAGE("sanction.manage", SANCTION, MANAGE, false),
    APPEAL_MANAGE("appeal.manage", SANCTION, MANAGE, false),
    SANCTION_BAN("sanction.ban", SANCTION, SENSITIVE, false),
    BANNEDWORD_READ("bannedword.read", BADWORD, READ, false),
    BANNEDWORD_SETTINGS("bannedword.settings", BADWORD, CONFIG, false),
    KEEPER_ACTION_READ("keeper-action.read", STAFF_AUDIT, READ, false),
    KEEPER_ACTION_RESTORE("keeper-action.restore", STAFF_AUDIT, SENSITIVE, false),
    MEMBER_READ("member.read", MEMBER_VIEW, READ, true),
    MEMBER_PII("member.pii", MEMBER_VIEW, SENSITIVE, true),
    NICKNAME_IMAGE_READ("nickname-image.read", NICKNAME_IMAGE, READ, false),
    NICKNAME_IMAGE_MANAGE("nickname-image.manage", NICKNAME_IMAGE, MANAGE, false),
    GRADE_READ("grade.read", GRADE_POINT, READ, true),
    GRADE_MANAGE("grade.manage", GRADE_POINT, CONFIG, true),
    GRADE_OVERRIDE("grade.override", GRADE_POINT, SENSITIVE, false),
    POINT_READ("point.read", GRADE_POINT, READ, true),
    POINT_SETTINGS("point.settings", GRADE_POINT, CONFIG, true),
    POINT_ADJUST("point.adjust", GRADE_POINT, SENSITIVE, true),
    DORMANCY_READ("dormancy.read", GRADE_POINT, READ, false),
    DORMANCY_SETTINGS("dormancy.settings", GRADE_POINT, CONFIG, false),
    GROUP_READ("group.read", CLUB, READ, false),
    GROUP_MANAGE("group.manage", CLUB, MANAGE, false),
    GROUP_SETTINGS("group.settings", CLUB, CONFIG, false),
    HOTDEAL_READ("hotdeal.read", HOTDEAL, READ, false),
    HOTDEAL_MANAGE("hotdeal.manage", HOTDEAL, MANAGE, false),
    HOTDEAL_DOMAIN_MANAGE("hotdeal.domain.manage", HOTDEAL, CONFIG, false),
    COMMERCE_PRODUCT_READ("commerce.product.read", GB_PLAN, READ, false),
    COMMERCE_PRODUCT_MANAGE("commerce.product.manage", GB_PLAN, MANAGE, false),
    COMMERCE_PRODUCT_SETTINGS("commerce.product.settings", GB_PLAN, CONFIG, false),
    COMMERCE_PRODUCT_PRICE("commerce.product.price", GB_PLAN, SENSITIVE, false),
    COMMERCE_STOCK_ADJUST("commerce.stock.adjust", GB_PLAN, SENSITIVE, false),
    COMMERCE_ORDER_READ("commerce.order.read", GB_ORDER, READ, false),
    COMMERCE_ORDER_MANAGE("commerce.order.manage", GB_ORDER, MANAGE, false),
    COMMERCE_REFUND_APPROVE("commerce.refund.approve", GB_ORDER, SENSITIVE, false),
    COMMERCE_ADDRESS_REVEAL("commerce.address.reveal", GB_ORDER, SENSITIVE, false),
    COMMERCE_SETTLEMENT_READ("commerce.settlement.read", GB_SETTLE, READ, false),
    COMMERCE_EXPORT("commerce.export", GB_SETTLE, SENSITIVE, false),
    COMMERCE_SETTINGS("commerce.settings", GB_CONFIG, CONFIG, false),
    BUSINESS_READ("business.read", BIZ_REVIEW, READ, false),
    BUSINESS_MANAGE("business.manage", BIZ_REVIEW, MANAGE, false),
    DEAL_ORDER_READ("deal-order.read", BIZ_PRODUCT, READ, false),
    DEAL_ORDER_MANAGE("deal-order.manage", BIZ_PRODUCT, MANAGE, false),
    DEAL_ORDER_SETTINGS("deal-order.settings", BIZ_PRODUCT, CONFIG, false),
    DEAL_ORDER_REFUND("deal-order.refund", BIZ_PRODUCT, SENSITIVE, false),
    AD_READ("ad.read", AD_PROMO, READ, false),
    AD_MANAGE("ad.manage", AD_PROMO, MANAGE, false),
    PROMO_MANAGE("promo.manage", AD_PROMO, MANAGE, false),
    AD_SETTINGS("ad.settings", AD_PROMO, CONFIG, false),
    AD_TAKEDOWN("ad.takedown", AD_PROMO, SENSITIVE, false),
    OPERATOR_READ("operator.read", OPERATOR_ROLE, READ, true),
    ROLE_READ("role.read", OPERATOR_ROLE, READ, true),
    OPERATOR_MANAGE("operator.manage", OPERATOR_ROLE, MANAGE, true),
    ROLE_MANAGE("role.manage", OPERATOR_ROLE, CONFIG, true),
    OPERATOR_LOCK("operator.lock", OPERATOR_ROLE, SENSITIVE, true),
    ROLE_GRANT("role.grant", OPERATOR_ROLE, SENSITIVE, true),
    TEAM_READ("team.read", OPERATOR_ROLE, READ, true),
    TEAM_MANAGE("team.manage", OPERATOR_ROLE, MANAGE, true),
    AUDIT_READ("audit.read", AUDIT_BATCH, READ, true),
    BATCH_READ("batch.read", AUDIT_BATCH, READ, true),
    BATCH_MANAGE("batch.manage", AUDIT_BATCH, MANAGE, true),
    BATCH_RUN("batch.run", AUDIT_BATCH, SENSITIVE, true),
    SITE_READ("site.read", SITE_INTEGRATION, READ, true),
    SITE_MANAGE("site.manage", SITE_INTEGRATION, CONFIG, true),
    SITE_SECRET("site.secret", SITE_INTEGRATION, SENSITIVE, true),
    NOTIFICATION_READ("notification.read", SITE_INTEGRATION, READ, true),
    NOTIFICATION_MANAGE("notification.manage", SITE_INTEGRATION, CONFIG, true),
    /** 알림 템플릿(메일·Slack·Mattermost·카카오톡 겉모양)·문구(대상 × 채널 × 언어별 내용)·대상별 선택/규칙 조회 */
    TEMPLATE_READ("template.read", SITE_INTEGRATION, READ, true),
    /** 알림 템플릿·문구 만들기·고치기·지우기, 대상별 선택/규칙 저장, 시험 발송 */
    TEMPLATE_MANAGE("template.manage", SITE_INTEGRATION, CONFIG, true),
    /** 약관(이용약관·개인정보처리방침) 게시본·초안·「보기」 링크 옵션 조회 — docs/features/terms-contract.md */
    TERMS_READ("terms.read", SITE_INTEGRATION, READ, true),
    /** 약관 초안 저장·삭제, 새 버전 게시, 「보기」 링크 옵션 저장 */
    TERMS_MANAGE("terms.manage", SITE_INTEGRATION, CONFIG, true);

    private static final Map<String, Permission> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(Permission::code, Function.identity()));

    private final String code;
    private final PermissionType type;
    private final PermissionUnit unit;
    private final boolean featureReady;

    Permission(String code, PermissionType type, PermissionUnit unit, boolean featureReady) {
        this.code = code;
        this.type = type;
        this.unit = unit;
        this.featureReady = featureReady;
    }

    /** 화면·DB 와 주고받는 권한 코드({@code 영역.행위}) */
    public String code() {
        return code;
    }

    public PermissionType type() {
        return type;
    }

    public PermissionUnit unit() {
        return unit;
    }

    public boolean featureReady() {
        return featureReady;
    }

    /** 코드로 찾는다. 모르는 코드면 비어 있다 */
    public static Optional<Permission> ofCode(String code) {
        return Optional.ofNullable(BY_CODE.get(code));
    }

    /** 모든 운영자 권한 코드 — 최고 관리자가 받는 목록 */
    public static List<String> allCodes() {
        return Arrays.stream(values()).map(Permission::code).toList();
    }

    /** 세부 유형의 운영자 역할이 고를 수 있는 권한 후보 — 그 유형에 속한 권한 전부. 종합 관리 유형은 유형 제한 없이 모든 권한이다 */
    public static List<Permission> candidatesOf(PermissionType type) {
        if (type.isGeneral()) {
            return List.of(values());
        }
        return Arrays.stream(values()).filter(p -> p.type == type).toList();
    }
}
