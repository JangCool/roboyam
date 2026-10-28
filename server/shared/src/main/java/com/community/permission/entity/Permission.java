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
    /** 하이라이트 목록·글 찾기·후보·의견 목록 조회 — docs/features/popular-highlight-contract.md */
    HIGHLIGHT_READ("highlight.read", NOTICE, READ, true),
    /** 하이라이트 만들기·고치기·순서·노출·지우기·대표 이미지 올리기(폼의 토론 칸은 글의 토론을 연다·바꾼다·닫는다) */
    HIGHLIGHT_MANAGE("highlight.manage", NOTICE, MANAGE, true),
    /** 인기글 관리(기간별 순위·뺀 글) 조회, 인기글 설정 조회 */
    POPULAR_READ("popular.read", NOTICE, READ, true),
    /** 인기글에서 빼기·다시 넣기 */
    POPULAR_MANAGE("popular.manage", NOTICE, MANAGE, true),
    /** 인기글 설정(점수식·조건·주 시작 요일) 저장 */
    POPULAR_SETTINGS("popular.settings", NOTICE, CONFIG, true),
    /** 게시글 관리 조회 — 글 목록·글 상세(본문·조치 기록)·게시판 고르기, 글의 찬반 토론 단건·토론 의견 목록·대기 고정 요청(docs/features/post-admin-contract.md·post-debate-contract.md) */
    POST_READ("post.read", POST, READ, true),
    /** 게시글 관리 처리 — 숨기기·숨김 해제·삭제·이동·공지 지정·고정·고정 해제·일괄 숨김/해제, 글의 토론 열기·설정·닫기, 토론 의견 고정·숨김, 의견 고정 요청 승인·거절 */
    POST_MANAGE("post.manage", POST, MANAGE, true),
    /** 지운 글 복구(파기 전) — 회수한 포인트를 되돌린다 */
    POST_RESTORE("post.restore", POST, SENSITIVE, true),
    /** 일괄 삭제({@code post.manage} 와 함께) */
    POST_BULK_DELETE("post.bulk-delete", POST, SENSITIVE, true),
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
    /** 신고 관리 조회 — 사건 목록·요약·상세(대상 원문·신고 당시 스냅샷·신고자·작성자 카드·처리 기록). 상세를 열어도 검토 시작은 {@code report.manage} 가 있을 때만(docs/features/report-admin-contract.md) */
    REPORT_READ("report.read", REPORT, READ, true),
    /** 신고 처리 — 조치(숨기기·삭제·자동 숨김 확정)·무시(허위 표시)·일괄 무시·다시 열기 */
    REPORT_MANAGE("report.manage", REPORT, MANAGE, true),
    SANCTION_READ("sanction.read", SANCTION, READ, false),
    SANCTION_MANAGE("sanction.manage", SANCTION, MANAGE, false),
    APPEAL_MANAGE("appeal.manage", SANCTION, MANAGE, false),
    SANCTION_BAN("sanction.ban", SANCTION, SENSITIVE, false),
    KEEPER_ACTION_READ("keeper-action.read", STAFF_AUDIT, READ, false),
    KEEPER_ACTION_RESTORE("keeper-action.restore", STAFF_AUDIT, SENSITIVE, false),
    MEMBER_READ("member.read", MEMBER_VIEW, READ, true),
    MEMBER_PII("member.pii", MEMBER_VIEW, SENSITIVE, true),
    /** 탈퇴 회원 연락용 이메일(분리 보관) 열람 — 사유 + 감사(commerce.md 17-2, 커머스 0-4) */
    MEMBER_WITHDRAWN_CONTACT("member.withdrawn.contact", MEMBER_VIEW, SENSITIVE, true),
    NICKNAME_IMAGE_READ("nickname-image.read", NICKNAME_IMAGE, READ, true),
    NICKNAME_IMAGE_MANAGE("nickname-image.manage", NICKNAME_IMAGE, MANAGE, true),
    GRADE_READ("grade.read", GRADE_POINT, READ, true),
    GRADE_MANAGE("grade.manage", GRADE_POINT, CONFIG, true),
    GRADE_OVERRIDE("grade.override", GRADE_POINT, SENSITIVE, false),
    POINT_READ("point.read", GRADE_POINT, READ, true),
    POINT_SETTINGS("point.settings", GRADE_POINT, CONFIG, true),
    POINT_ADJUST("point.adjust", GRADE_POINT, SENSITIVE, true),
    /** 문턱({@code point_settings.adjust_large_threshold}, 기본 ±10,000P)을 넘는 수동 조정(최대 ±100,000P) — commerce.md 13-2, 커머스 1-1 에서 기능이 생겼다 */
    POINT_ADJUST_LARGE("point.adjust.large", GRADE_POINT, SENSITIVE, true),
    /** 유상(충전) 포인트 환불 승인 */
    POINT_REFUND_APPROVE("point.refund.approve", GRADE_POINT, SENSITIVE, false),
    /** 고액 충전 포인트 환불(1회 100,000원 이상) */
    POINT_REFUND_LARGE("point.refund.large", GRADE_POINT, SENSITIVE, false),
    /** 포인트 상점 품목·구매 내역·설정 조회(충전 상품은 3단계) — 커머스 1-2 에서 기능이 생겼다(0-5 의 shop.read 를 V86 이 옮겼다) */
    POINTSHOP_READ("pointshop.read", GRADE_POINT, READ, true),
    /** 포인트 상점 품목 만들기·고치기·아바타 묶음 가격·판매·설정 저장 */
    POINTSHOP_MANAGE("pointshop.manage", GRADE_POINT, MANAGE, true),
    /** 포인트 상점 구매 예외 환불(셀프 환불 조건 밖 — 사유·감사 돈 관련 5년) */
    POINTSHOP_REFUND("pointshop.refund", GRADE_POINT, SENSITIVE, true),
    DORMANCY_READ("dormancy.read", GRADE_POINT, READ, false),
    DORMANCY_SETTINGS("dormancy.settings", GRADE_POINT, CONFIG, false),
    GROUP_READ("group.read", CLUB, READ, true),
    GROUP_MANAGE("group.manage", CLUB, MANAGE, true),
    GROUP_SETTINGS("group.settings", CLUB, CONFIG, true),
    HOTDEAL_READ("hotdeal.read", HOTDEAL, READ, false),
    HOTDEAL_MANAGE("hotdeal.manage", HOTDEAL, MANAGE, false),
    HOTDEAL_DOMAIN_MANAGE("hotdeal.domain.manage", HOTDEAL, CONFIG, false),
    COMMERCE_PRODUCT_READ("commerce.product.read", GB_PLAN, READ, true),
    COMMERCE_PRODUCT_MANAGE("commerce.product.manage", GB_PLAN, MANAGE, true),
    COMMERCE_PRODUCT_SETTINGS("commerce.product.settings", GB_PLAN, CONFIG, false),
    COMMERCE_PRODUCT_PRICE("commerce.product.price", GB_PLAN, SENSITIVE, true),
    COMMERCE_STOCK_ADJUST("commerce.stock.adjust", GB_PLAN, SENSITIVE, true),
    COMMERCE_ORDER_READ("commerce.order.read", GB_ORDER, READ, true),
    COMMERCE_ORDER_MANAGE("commerce.order.manage", GB_ORDER, MANAGE, true),
    COMMERCE_REFUND_APPROVE("commerce.refund.approve", GB_ORDER, SENSITIVE, true),
    COMMERCE_ADDRESS_REVEAL("commerce.address.reveal", GB_ORDER, SENSITIVE, true),
    /** 공구 중단(참여자 전원 전액 환불) — commerce.md 13-2, 2b-4 에서 준비됨 */
    COMMERCE_GROUPBUY_CANCEL("commerce.groupbuy.cancel", GB_ORDER, SENSITIVE, true),
    /** 고액 공구 환불(1회 100,000원 이상) */
    COMMERCE_REFUND_LARGE("commerce.refund.large", GB_ORDER, SENSITIVE, true),
    COMMERCE_SETTLEMENT_READ("commerce.settlement.read", GB_SETTLE, READ, false),
    COMMERCE_EXPORT("commerce.export", GB_SETTLE, SENSITIVE, true),
    /** 결제 주문 조회(결제 다섯 화면) — commerce.md 13-2, 2a-3 에서 준비됨 */
    COMMERCE_PAYMENT_READ("commerce.payment.read", GB_SETTLE, READ, true),
    /** 결제 이상·대사 처리(다시 시도·수동 처리 완료·포트원에서 다시 확인·이슈 처리·다시 대사) — 2a-3 에서 준비됨 */
    COMMERCE_RECONCILE_MANAGE("commerce.reconcile.manage", GB_SETTLE, SENSITIVE, true),
    COMMERCE_SETTINGS("commerce.settings", GB_CONFIG, CONFIG, true),
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
