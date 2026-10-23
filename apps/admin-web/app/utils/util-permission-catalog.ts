/**
 * 권한 카탈로그 — 업무 영역(큰 주제) → 권한 묶음(세부 유형) → 권한 코드(`영역.행위`). 정책 출처는 `docs/domain/operator-permissions.md` 의 "역할 유형" 표다: 표의 모든 세부 유형이 `TYPES` 의 하나로 대응하고, 표의 "다루는 일·설정·민감 조치"가 권한이 된다.
 *
 * 권한은 네 단위로 나눈다: 조회(보기) · 처리(승인·삭제·상태 변경 같은 업무) · 설정(규칙·구성 변경) · 민감 조치(되돌리기 어려운 일). 설정도 권한으로 위임할 수 있다(문서).
 * **화면이 아직 없는 기능의 권한도 작성해 둔다**(`featureReady: false` — "기능 준비 중" 표시): 역할을 미리 설계해 둘 수 있게 모두 역할에 줄 수 있다. 화면이 생기면 `featureReady` 를 켜고 메뉴에 `permission` 을 연결한다.
 * 코드 이름 규칙: `영역.행위`(예: `report.manage`) 또는 `영역.대상.행위`(예: `commerce.order.manage`). 행위는 `read`(조회) · `manage`(처리) · `settings`(설정) · 그 밖의 동사(`approve`·`adjust`·`price` … 민감 조치).
 * ⚠ 원천은 서버 enum `Permission.java`(서버가 뜰 때 `permissions` 표와 맞춘다)다. 이 파일의 `PERMISSIONS` 는 그 사본이라 목록·순서·`featureReady` 를 서버와 같게 둔다(목록 API `GET /api/admin/permissions` 는 아직 없다).
 *
 * 운영자 역할은 영역(대분류) → 세부 유형(`TYPES` 24개) 두 단계로 나뉜다. 역할 하나는 세부 유형 하나에 속하고 그 유형의 권한만 후보로 고른다. 종합 관리 유형은 유형 제한 없이 모든 권한 중에서 고르며 최고 관리자만 만들고 고친다(권한 상승 방지).
 * 최고 관리자는 역할이 아니라 루트 계정의 속성(`isSuper`)이라 이 목록에 없다. `codes()` 는 모든 운영자 권한 코드(최고 관리자가 받는 목록)다.
 *
 * 문구: 영역 `permission.area.<id>` · 권한 묶음(세부 유형) `permission.type.<id>` · 단위 `permission.unit.<UNIT>` · 권한 이름·설명은 정의의 `nameKey`·`descKey`.
 */

/** 권한 단위 — 조회 · 처리 · 설정 · 민감 조치 */
export type PermissionUnit = 'READ' | 'MANAGE' | 'CONFIG' | 'SENSITIVE';

export interface PermissionDef {
    /** `영역.행위` */
    code: string;
    unit: PermissionUnit;
    /** 속한 역할 유형의 id */
    type: string;
    nameKey: string;
    descKey: string;
    /** 이 권한이 하는 일의 화면(기능)이 이미 있는가 — 표시용("기능 준비 중")이다. false 여도 역할에 줄 수 있다(미리 역할을 설계해 둘 수 있게). 메뉴 연결은 화면이 있는 권한만 뜻이 있다 */
    featureReady: boolean;
}

export interface RoleTypeDef {
    id: string;
    /** 속한 영역의 id */
    area: string;
    nameKey: string;
}

export interface RoleAreaDef {
    id: string;
    nameKey: string;
}

/** 단위 하나에 속한 권한 묶음 */
export interface PermissionUnitGroup {
    unit: PermissionUnit;
    items: PermissionDef[];
}

// ── 회원 역할(공개 사이트 회원의 능력) — 운영자 권한과 다른 체계다(정책: operator-permissions.md "회원 역할 (제안)"). 코드·유형·묶음이 운영자 쪽과 섞이지 않는다 ─

/** 역할의 축 — 운영자 역할(관리자 앱 권한) · 회원 역할(공개 사이트 능력) */
export type RoleKind = 'OPERATOR' | 'MEMBER';

/** 회원 권한 묶음 — 이용(사이트 전체 능력) · 게시판 운영(게시판 단위 역할 전용). 운영자의 조회·처리·설정·민감 조치 4단위와 다르다 */
export type MemberPermissionGroup = 'USE' | 'BOARD';

/** 부여 방식 — 자동(활동 기준·가입) · 지정(게시판별) · 수동(운영자가 회원에게) */
export type RoleGrantMode = 'AUTO' | 'APPOINTED' | 'MANUAL';

/** 범위 — 사이트 전체 · 게시판 하나 */
export type RoleScope = 'SITE' | 'BOARD';

/** 권한 고르기·목록이 쓰는 공통 모양 — 운영자 권한(`PermissionDef`)과 회원 권한(`MemberPermissionDef`)이 모두 이 모양이다 */
export interface PermissionItem {
    code: string;
    type: string;
    nameKey: string;
    descKey: string;
    /** false 면 아직 화면(사이트 기능)이 없어 "기능 준비 중" 표시가 붙는다 — 비활성이 아니라 체크할 수 있다. 없으면 true */
    featureReady?: boolean;
}

export interface MemberPermissionDef extends PermissionItem {
    group: MemberPermissionGroup;
    featureReady: boolean;
    /** 게시판 매니저에게만 후보로 나온다(스텝에는 안 나온다) */
    managerOnly?: boolean;
}

/** 화면이 그리는 권한 묶음 한 덩어리 — 운영자는 단위(조회·처리·설정·민감 조치), 회원은 이용 · 게시판 운영 */
export interface PermissionGroupView {
    key: string;
    labelKey: string;
    hintKey: string;
    icon: string;
    items: PermissionItem[];
}

export default class UtilPermissionCatalog {
    /** 종합 관리 유형의 id — 유형 제한 없이 고르고 최고 관리자만 만들고 고친다 */
    static readonly GENERAL_TYPE = 'general';
    /** 종합 영역의 id — 영역을 가리킬 때 쓴다(세부 유형 `GENERAL_TYPE` 과 같은 글자) */
    static readonly GENERAL_AREA = 'general';

    /** 보여 주는 차례 */
    static readonly UNITS: readonly PermissionUnit[] = ['READ', 'MANAGE', 'CONFIG', 'SENSITIVE'];

    /** 단위별 아이콘 — 글자(이름)와 함께 쓴다 */
    static readonly UNIT_ICONS: Record<PermissionUnit, string> = {
        READ: 'pi pi-eye',
        MANAGE: 'pi pi-check-square',
        CONFIG: 'pi pi-sliders-h',
        SENSITIVE: 'pi pi-exclamation-triangle',
    };

    static readonly AREAS: readonly RoleAreaDef[] = [
        { id: 'content', nameKey: 'permission.area.content' },
        { id: 'safety', nameKey: 'permission.area.safety' },
        { id: 'member', nameKey: 'permission.area.member' },
        { id: 'club', nameKey: 'permission.area.club' },
        { id: 'hotdeal', nameKey: 'permission.area.hotdeal' },
        { id: 'groupbuy', nameKey: 'permission.area.groupbuy' },
        { id: 'bizAd', nameKey: 'permission.area.bizAd' },
        { id: 'system', nameKey: 'permission.area.system' },
        { id: 'general', nameKey: 'permission.area.general' },
    ];

    /** 권한 묶음(세부 유형) — 문서의 "역할 유형" 표 그대로(24개, 종합 관리 포함). 영역(`AREAS`) 아래에 있고 권한 코드가 이 묶음에 속한다 */
    static readonly TYPES: readonly RoleTypeDef[] = [
        { id: 'notice', area: 'content', nameKey: 'permission.type.notice' },
        { id: 'post', area: 'content', nameKey: 'permission.type.post' },
        { id: 'board', area: 'content', nameKey: 'permission.type.board' },
        { id: 'siteLayout', area: 'content', nameKey: 'permission.type.siteLayout' },
        { id: 'report', area: 'safety', nameKey: 'permission.type.report' },
        { id: 'sanction', area: 'safety', nameKey: 'permission.type.sanction' },
        { id: 'badword', area: 'safety', nameKey: 'permission.type.badword' },
        { id: 'staffAudit', area: 'safety', nameKey: 'permission.type.staffAudit' },
        { id: 'memberView', area: 'member', nameKey: 'permission.type.memberView' },
        { id: 'nicknameImage', area: 'member', nameKey: 'permission.type.nicknameImage' },
        { id: 'gradePoint', area: 'member', nameKey: 'permission.type.gradePoint' },
        { id: 'club', area: 'club', nameKey: 'permission.type.club' },
        { id: 'hotdeal', area: 'hotdeal', nameKey: 'permission.type.hotdeal' },
        { id: 'gbPlan', area: 'groupbuy', nameKey: 'permission.type.gbPlan' },
        { id: 'gbOrder', area: 'groupbuy', nameKey: 'permission.type.gbOrder' },
        { id: 'gbSettle', area: 'groupbuy', nameKey: 'permission.type.gbSettle' },
        { id: 'gbConfig', area: 'groupbuy', nameKey: 'permission.type.gbConfig' },
        { id: 'bizReview', area: 'bizAd', nameKey: 'permission.type.bizReview' },
        { id: 'bizProduct', area: 'bizAd', nameKey: 'permission.type.bizProduct' },
        { id: 'adPromo', area: 'bizAd', nameKey: 'permission.type.adPromo' },
        { id: 'operatorRole', area: 'system', nameKey: 'permission.type.operatorRole' },
        { id: 'auditBatch', area: 'system', nameKey: 'permission.type.auditBatch' },
        { id: 'siteIntegration', area: 'system', nameKey: 'permission.type.siteIntegration' },
        { id: 'general', area: 'general', nameKey: 'permission.type.general' },
    ];

    /**
     * 운영자 권한 — 표의 모든 세부 유형에 대응한다. 화면이 있는 것(`featureReady: true`):
     *  · 운영자·역할 관리: 조회 `operator.read`·`role.read` / 처리 `operator.manage` / 설정 `role.manage` / 민감 `operator.lock`(운영자 잠금·해제)·`role.grant`(역할 변경 = 권한 상승). 조직 구성(팀)은 조회 `team.read` / 처리 `team.manage`(팀 만들기·고치기·지우기·옮기기·팀원 관리)
     *  · 감사·배치: 조회 `audit.read`·`batch.read` / 처리 `batch.manage` / 민감 `batch.run`(배치 즉시 실행). 이 유형의 설정 단위("배치 사용 여부")는 처리(`batch.manage`)에 포함했다.
     *  · 회원 조회: 조회 `member.read` / 민감 `member.pii`(전체 이메일 열람 — 없으면 이메일이 `ab***@gmail.com` 으로 가려진다).
     *  · 등급·포인트·휴면(등급 설정·포인트 내역·포인트 정책 화면): 조회 `grade.read` / 설정 `grade.manage`(등급 승급 조건 변경). 포인트는 조회 `point.read`(원장·정책 보기) / 설정 `point.settings`(지급 정책 저장) / 민감 `point.adjust`(수동 지급·차감).
     *  · 게시판: 조회 `board.read` / 처리 `board.manage`·`board.staff.manage` / 설정 `board.settings`·`board.staff.limit` / 민감 `board.delete`(게시판 목록·설정·기본 설정·매니저/스텝).
     *  · 사이트 화면 구성: 조회 `layout.read` / 설정 `layout.settings`(공개 사이트 메뉴)·`admin-menu.manage`(관리자 사이트 메뉴 구성 저장).
     *  · 사이트·연동(설정 메뉴의 「일반」·「소셜 로그인」·「메일 서버」·「알림」 화면): 조회 `site.read`(일반·소셜 로그인·메일 서버·설정 이력 보기) / 설정 `site.manage`(사이트 이름·소개, 표시 기본값 변경) / 민감 `site.secret`(소셜 로그인 키 저장·삭제, 메일 서버 저장·시험 발송). 알림 이벤트 관리는 조회 `notification.read` / 설정 `notification.manage`(이벤트별 사용 여부·이메일 전달 변경 — 받는 사람은 코드에 고정이라 바꾸지 않는다).
     * 나머지는 화면이 아직 없는 기능의 권한이다(`featureReady: false`). 목록·순서·`featureReady` 는 서버 `Permission.java` 가 원천이다 — 바꿀 때 두 곳을 같게 한다.
     */
    static readonly PERMISSIONS: readonly PermissionDef[] = [
        { code: 'notice.read', unit: 'READ', type: 'notice', nameKey: 'permission.item.noticeRead', descKey: 'permission.item.noticeReadDesc', featureReady: false },
        { code: 'notice.manage', unit: 'MANAGE', type: 'notice', nameKey: 'permission.item.noticeManage', descKey: 'permission.item.noticeManageDesc', featureReady: false },
        { code: 'notice.settings', unit: 'CONFIG', type: 'notice', nameKey: 'permission.item.noticeSettings', descKey: 'permission.item.noticeSettingsDesc', featureReady: false },
        { code: 'post.read', unit: 'READ', type: 'post', nameKey: 'permission.item.postRead', descKey: 'permission.item.postReadDesc', featureReady: false },
        { code: 'post.manage', unit: 'MANAGE', type: 'post', nameKey: 'permission.item.postManage', descKey: 'permission.item.postManageDesc', featureReady: false },
        { code: 'post.bulk-delete', unit: 'SENSITIVE', type: 'post', nameKey: 'permission.item.postBulkDelete', descKey: 'permission.item.postBulkDeleteDesc', featureReady: false },
        { code: 'board.read', unit: 'READ', type: 'board', nameKey: 'permission.item.boardRead', descKey: 'permission.item.boardReadDesc', featureReady: true },
        { code: 'board.manage', unit: 'MANAGE', type: 'board', nameKey: 'permission.item.boardManage', descKey: 'permission.item.boardManageDesc', featureReady: true },
        { code: 'board.settings', unit: 'CONFIG', type: 'board', nameKey: 'permission.item.boardSettings', descKey: 'permission.item.boardSettingsDesc', featureReady: true },
        { code: 'board.staff.manage', unit: 'MANAGE', type: 'board', nameKey: 'permission.item.boardStaffManage', descKey: 'permission.item.boardStaffManageDesc', featureReady: true },
        { code: 'board.staff.limit', unit: 'CONFIG', type: 'board', nameKey: 'permission.item.boardStaffLimit', descKey: 'permission.item.boardStaffLimitDesc', featureReady: true },
        { code: 'board.delete', unit: 'SENSITIVE', type: 'board', nameKey: 'permission.item.boardDelete', descKey: 'permission.item.boardDeleteDesc', featureReady: true },
        // 사이트 화면 구성 — 조회(공개 사이트 메뉴·관리자 메뉴 보기) · 공개 사이트 메뉴 저장. 관리자 메뉴 저장은 모든 운영자의 작업 화면을 바꾸므로 따로 둔다(`admin-menu.manage`)
        { code: 'layout.read', unit: 'READ', type: 'siteLayout', nameKey: 'permission.item.layoutRead', descKey: 'permission.item.layoutReadDesc', featureReady: true },
        { code: 'layout.settings', unit: 'CONFIG', type: 'siteLayout', nameKey: 'permission.item.layoutSettings', descKey: 'permission.item.layoutSettingsDesc', featureReady: true },
        { code: 'admin-menu.manage', unit: 'CONFIG', type: 'siteLayout', nameKey: 'permission.item.adminMenuManage', descKey: 'permission.item.adminMenuManageDesc', featureReady: true },
        { code: 'report.read', unit: 'READ', type: 'report', nameKey: 'permission.item.reportRead', descKey: 'permission.item.reportReadDesc', featureReady: false },
        { code: 'report.manage', unit: 'MANAGE', type: 'report', nameKey: 'permission.item.reportManage', descKey: 'permission.item.reportManageDesc', featureReady: false },
        { code: 'sanction.read', unit: 'READ', type: 'sanction', nameKey: 'permission.item.sanctionRead', descKey: 'permission.item.sanctionReadDesc', featureReady: false },
        { code: 'sanction.manage', unit: 'MANAGE', type: 'sanction', nameKey: 'permission.item.sanctionManage', descKey: 'permission.item.sanctionManageDesc', featureReady: false },
        { code: 'appeal.manage', unit: 'MANAGE', type: 'sanction', nameKey: 'permission.item.appealManage', descKey: 'permission.item.appealManageDesc', featureReady: false },
        { code: 'sanction.ban', unit: 'SENSITIVE', type: 'sanction', nameKey: 'permission.item.sanctionBan', descKey: 'permission.item.sanctionBanDesc', featureReady: false },
        { code: 'bannedword.read', unit: 'READ', type: 'badword', nameKey: 'permission.item.bannedwordRead', descKey: 'permission.item.bannedwordReadDesc', featureReady: false },
        { code: 'bannedword.settings', unit: 'CONFIG', type: 'badword', nameKey: 'permission.item.bannedwordSettings', descKey: 'permission.item.bannedwordSettingsDesc', featureReady: false },
        { code: 'keeper-action.read', unit: 'READ', type: 'staffAudit', nameKey: 'permission.item.keeperActionRead', descKey: 'permission.item.keeperActionReadDesc', featureReady: false },
        { code: 'keeper-action.restore', unit: 'SENSITIVE', type: 'staffAudit', nameKey: 'permission.item.keeperActionRestore', descKey: 'permission.item.keeperActionRestoreDesc', featureReady: false },
        { code: 'member.read', unit: 'READ', type: 'memberView', nameKey: 'permission.memberRead', descKey: 'permission.memberReadDesc', featureReady: true },
        { code: 'member.pii', unit: 'SENSITIVE', type: 'memberView', nameKey: 'permission.memberPii', descKey: 'permission.memberPiiDesc', featureReady: true },
        { code: 'nickname-image.read', unit: 'READ', type: 'nicknameImage', nameKey: 'permission.item.nicknameImageRead', descKey: 'permission.item.nicknameImageReadDesc', featureReady: false },
        { code: 'nickname-image.manage', unit: 'MANAGE', type: 'nicknameImage', nameKey: 'permission.item.nicknameImageManage', descKey: 'permission.item.nicknameImageManageDesc', featureReady: false },
        { code: 'grade.read', unit: 'READ', type: 'gradePoint', nameKey: 'permission.gradeRead', descKey: 'permission.gradeReadDesc', featureReady: true },
        { code: 'grade.manage', unit: 'CONFIG', type: 'gradePoint', nameKey: 'permission.gradeManage', descKey: 'permission.gradeManageDesc', featureReady: true },
        { code: 'grade.override', unit: 'SENSITIVE', type: 'gradePoint', nameKey: 'permission.item.gradeOverride', descKey: 'permission.item.gradeOverrideDesc', featureReady: false },
        { code: 'point.read', unit: 'READ', type: 'gradePoint', nameKey: 'permission.item.pointRead', descKey: 'permission.item.pointReadDesc', featureReady: true },
        { code: 'point.settings', unit: 'CONFIG', type: 'gradePoint', nameKey: 'permission.item.pointSettings', descKey: 'permission.item.pointSettingsDesc', featureReady: true },
        { code: 'point.adjust', unit: 'SENSITIVE', type: 'gradePoint', nameKey: 'permission.item.pointAdjust', descKey: 'permission.item.pointAdjustDesc', featureReady: true },
        { code: 'dormancy.read', unit: 'READ', type: 'gradePoint', nameKey: 'permission.item.dormancyRead', descKey: 'permission.item.dormancyReadDesc', featureReady: false },
        { code: 'dormancy.settings', unit: 'CONFIG', type: 'gradePoint', nameKey: 'permission.item.dormancySettings', descKey: 'permission.item.dormancySettingsDesc', featureReady: false },
        { code: 'group.read', unit: 'READ', type: 'club', nameKey: 'permission.item.groupRead', descKey: 'permission.item.groupReadDesc', featureReady: false },
        { code: 'group.manage', unit: 'MANAGE', type: 'club', nameKey: 'permission.item.groupManage', descKey: 'permission.item.groupManageDesc', featureReady: false },
        { code: 'group.settings', unit: 'CONFIG', type: 'club', nameKey: 'permission.item.groupSettings', descKey: 'permission.item.groupSettingsDesc', featureReady: false },
        { code: 'hotdeal.read', unit: 'READ', type: 'hotdeal', nameKey: 'permission.item.hotdealRead', descKey: 'permission.item.hotdealReadDesc', featureReady: false },
        { code: 'hotdeal.manage', unit: 'MANAGE', type: 'hotdeal', nameKey: 'permission.item.hotdealManage', descKey: 'permission.item.hotdealManageDesc', featureReady: false },
        { code: 'hotdeal.domain.manage', unit: 'CONFIG', type: 'hotdeal', nameKey: 'permission.item.hotdealDomainManage', descKey: 'permission.item.hotdealDomainManageDesc', featureReady: false },
        { code: 'commerce.product.read', unit: 'READ', type: 'gbPlan', nameKey: 'permission.item.commerceProductRead', descKey: 'permission.item.commerceProductReadDesc', featureReady: false },
        { code: 'commerce.product.manage', unit: 'MANAGE', type: 'gbPlan', nameKey: 'permission.item.commerceProductManage', descKey: 'permission.item.commerceProductManageDesc', featureReady: false },
        { code: 'commerce.product.settings', unit: 'CONFIG', type: 'gbPlan', nameKey: 'permission.item.commerceProductSettings', descKey: 'permission.item.commerceProductSettingsDesc', featureReady: false },
        { code: 'commerce.product.price', unit: 'SENSITIVE', type: 'gbPlan', nameKey: 'permission.item.commerceProductPrice', descKey: 'permission.item.commerceProductPriceDesc', featureReady: false },
        { code: 'commerce.stock.adjust', unit: 'SENSITIVE', type: 'gbPlan', nameKey: 'permission.item.commerceStockAdjust', descKey: 'permission.item.commerceStockAdjustDesc', featureReady: false },
        { code: 'commerce.order.read', unit: 'READ', type: 'gbOrder', nameKey: 'permission.item.commerceOrderRead', descKey: 'permission.item.commerceOrderReadDesc', featureReady: false },
        { code: 'commerce.order.manage', unit: 'MANAGE', type: 'gbOrder', nameKey: 'permission.item.commerceOrderManage', descKey: 'permission.item.commerceOrderManageDesc', featureReady: false },
        { code: 'commerce.refund.approve', unit: 'SENSITIVE', type: 'gbOrder', nameKey: 'permission.item.commerceRefundApprove', descKey: 'permission.item.commerceRefundApproveDesc', featureReady: false },
        { code: 'commerce.address.reveal', unit: 'SENSITIVE', type: 'gbOrder', nameKey: 'permission.item.commerceAddressReveal', descKey: 'permission.item.commerceAddressRevealDesc', featureReady: false },
        { code: 'commerce.settlement.read', unit: 'READ', type: 'gbSettle', nameKey: 'permission.item.commerceSettlementRead', descKey: 'permission.item.commerceSettlementReadDesc', featureReady: false },
        { code: 'commerce.export', unit: 'SENSITIVE', type: 'gbSettle', nameKey: 'permission.item.commerceExport', descKey: 'permission.item.commerceExportDesc', featureReady: false },
        { code: 'commerce.settings', unit: 'CONFIG', type: 'gbConfig', nameKey: 'permission.item.commerceSettings', descKey: 'permission.item.commerceSettingsDesc', featureReady: false },
        { code: 'business.read', unit: 'READ', type: 'bizReview', nameKey: 'permission.item.businessRead', descKey: 'permission.item.businessReadDesc', featureReady: false },
        { code: 'business.manage', unit: 'MANAGE', type: 'bizReview', nameKey: 'permission.item.businessManage', descKey: 'permission.item.businessManageDesc', featureReady: false },
        { code: 'deal-order.read', unit: 'READ', type: 'bizProduct', nameKey: 'permission.item.dealOrderRead', descKey: 'permission.item.dealOrderReadDesc', featureReady: false },
        { code: 'deal-order.manage', unit: 'MANAGE', type: 'bizProduct', nameKey: 'permission.item.dealOrderManage', descKey: 'permission.item.dealOrderManageDesc', featureReady: false },
        { code: 'deal-order.settings', unit: 'CONFIG', type: 'bizProduct', nameKey: 'permission.item.dealOrderSettings', descKey: 'permission.item.dealOrderSettingsDesc', featureReady: false },
        { code: 'deal-order.refund', unit: 'SENSITIVE', type: 'bizProduct', nameKey: 'permission.item.dealOrderRefund', descKey: 'permission.item.dealOrderRefundDesc', featureReady: false },
        { code: 'ad.read', unit: 'READ', type: 'adPromo', nameKey: 'permission.item.adRead', descKey: 'permission.item.adReadDesc', featureReady: false },
        { code: 'ad.manage', unit: 'MANAGE', type: 'adPromo', nameKey: 'permission.item.adManage', descKey: 'permission.item.adManageDesc', featureReady: false },
        { code: 'promo.manage', unit: 'MANAGE', type: 'adPromo', nameKey: 'permission.item.promoManage', descKey: 'permission.item.promoManageDesc', featureReady: false },
        { code: 'ad.settings', unit: 'CONFIG', type: 'adPromo', nameKey: 'permission.item.adSettings', descKey: 'permission.item.adSettingsDesc', featureReady: false },
        { code: 'ad.takedown', unit: 'SENSITIVE', type: 'adPromo', nameKey: 'permission.item.adTakedown', descKey: 'permission.item.adTakedownDesc', featureReady: false },
        { code: 'operator.read', unit: 'READ', type: 'operatorRole', nameKey: 'permission.operatorRead', descKey: 'permission.operatorReadDesc', featureReady: true },
        { code: 'role.read', unit: 'READ', type: 'operatorRole', nameKey: 'permission.roleRead', descKey: 'permission.roleReadDesc', featureReady: true },
        { code: 'operator.manage', unit: 'MANAGE', type: 'operatorRole', nameKey: 'permission.operatorManage', descKey: 'permission.operatorManageDesc', featureReady: true },
        { code: 'role.manage', unit: 'CONFIG', type: 'operatorRole', nameKey: 'permission.roleManage', descKey: 'permission.roleManageDesc', featureReady: true },
        { code: 'operator.lock', unit: 'SENSITIVE', type: 'operatorRole', nameKey: 'permission.operatorLock', descKey: 'permission.operatorLockDesc', featureReady: true },
        { code: 'role.grant', unit: 'SENSITIVE', type: 'operatorRole', nameKey: 'permission.roleGrant', descKey: 'permission.roleGrantDesc', featureReady: true },
        { code: 'team.read', unit: 'READ', type: 'operatorRole', nameKey: 'permission.teamRead', descKey: 'permission.teamReadDesc', featureReady: true },
        { code: 'team.manage', unit: 'MANAGE', type: 'operatorRole', nameKey: 'permission.teamManage', descKey: 'permission.teamManageDesc', featureReady: true },
        { code: 'audit.read', unit: 'READ', type: 'auditBatch', nameKey: 'permission.auditRead', descKey: 'permission.auditReadDesc', featureReady: true },
        { code: 'batch.read', unit: 'READ', type: 'auditBatch', nameKey: 'permission.batchRead', descKey: 'permission.batchReadDesc', featureReady: true },
        { code: 'batch.manage', unit: 'MANAGE', type: 'auditBatch', nameKey: 'permission.batchManage', descKey: 'permission.batchManageDesc', featureReady: true },
        { code: 'batch.run', unit: 'SENSITIVE', type: 'auditBatch', nameKey: 'permission.batchRun', descKey: 'permission.batchRunDesc', featureReady: true },
        { code: 'site.read', unit: 'READ', type: 'siteIntegration', nameKey: 'permission.siteRead', descKey: 'permission.siteReadDesc', featureReady: true },
        { code: 'site.manage', unit: 'CONFIG', type: 'siteIntegration', nameKey: 'permission.siteManage', descKey: 'permission.siteManageDesc', featureReady: true },
        { code: 'site.secret', unit: 'SENSITIVE', type: 'siteIntegration', nameKey: 'permission.item.siteSecret', descKey: 'permission.item.siteSecretDesc', featureReady: true },
        { code: 'notification.read', unit: 'READ', type: 'siteIntegration', nameKey: 'permission.notificationRead', descKey: 'permission.notificationReadDesc', featureReady: true },
        { code: 'notification.manage', unit: 'CONFIG', type: 'siteIntegration', nameKey: 'permission.notificationManage', descKey: 'permission.notificationManageDesc', featureReady: true },
        // ⚠ 메일 템플릿(`/settings/mail-templates`) — **화면 목업 확인용 후보**다. 서버 `Permission.java` 에는 아직 없다(서버 단계에서 같은 코드·단위로 만들고 `AdminScreenRegistry` 도 맞춘다).
        //   그때까지 서버가 이 코드를 운영자 권한 목록에 주지 않으므로 화면은 최고 관리자에게만 보이고, 역할에 체크해 저장하면 서버가 모르는 코드로 거절할 수 있다.
        //   설정 단위(CONFIG)는 `site.manage`·`notification.manage` 와 같은 분류(설정 메뉴의 구성 변경)다.
        { code: 'template.read', unit: 'READ', type: 'siteIntegration', nameKey: 'permission.templateRead', descKey: 'permission.templateReadDesc', featureReady: true },
        { code: 'template.manage', unit: 'CONFIG', type: 'siteIntegration', nameKey: 'permission.templateManage', descKey: 'permission.templateManageDesc', featureReady: true },
    ];

    // ── 회원 역할 ─ (구현 가정: 문서의 "회원 역할 (제안)" 대로 — 확정 전)

    /** 게시판 매니저·스텝의 역할 아이디 — 스텝에게는 `managerOnly` 권한이 후보로 나오지 않는다 */
    static readonly BOARD_STAFF_ROLE = 'ROLE_BOARD_STAFF';

    /** 회원 권한 묶음의 차례와 아이콘 */
    static readonly MEMBER_GROUPS: readonly MemberPermissionGroup[] = ['USE', 'BOARD'];
    static readonly MEMBER_GROUP_ICONS: Record<MemberPermissionGroup, string> = { USE: 'pi pi-globe', BOARD: 'pi pi-shield' };

    /**
     * 회원 권한 — 이용(사이트 전체 능력, 게시판 단위가 아닌 역할이 고른다)과 게시판 운영(게시판 단위 역할 전용).
     * 사이트 기능이 아직 없는 능력은 `featureReady: false`("기능 준비 중" 표시, 역할에 줄 수는 있다) — 소모임 개설 · 핫딜 등록 · 공동구매 구매 · 광고 신청.
     * 게시판 규칙(게시판별 최소 등급 등)은 게시판 설정에 두고 역할 권한과 **둘 다 만족해야** 한다(안). 정지는 역할이 아니라 상태에 따른 제한이다.
     */
    static readonly MEMBER_PERMISSIONS: readonly MemberPermissionDef[] = [
        { code: 'post.write', group: 'USE', type: 'memberSite', nameKey: 'permission.memberPostWrite', descKey: 'permission.memberPostWriteDesc', featureReady: true },
        { code: 'comment.write', group: 'USE', type: 'memberSite', nameKey: 'permission.memberCommentWrite', descKey: 'permission.memberCommentWriteDesc', featureReady: true },
        { code: 'message.send', group: 'USE', type: 'memberSite', nameKey: 'permission.memberMessageSend', descKey: 'permission.memberMessageSendDesc', featureReady: true },
        { code: 'file.attach', group: 'USE', type: 'memberSite', nameKey: 'permission.memberFileAttach', descKey: 'permission.memberFileAttachDesc', featureReady: true },
        { code: 'post.react', group: 'USE', type: 'memberSite', nameKey: 'permission.memberPostReact', descKey: 'permission.memberPostReactDesc', featureReady: true },
        { code: 'report.create', group: 'USE', type: 'memberSite', nameKey: 'permission.memberReportCreate', descKey: 'permission.memberReportCreateDesc', featureReady: true },
        { code: 'nickname-image.request', group: 'USE', type: 'memberSite', nameKey: 'permission.memberNicknameImage', descKey: 'permission.memberNicknameImageDesc', featureReady: true },
        { code: 'shop.use', group: 'USE', type: 'memberSite', nameKey: 'permission.memberShopUse', descKey: 'permission.memberShopUseDesc', featureReady: true },
        { code: 'group.create', group: 'USE', type: 'memberSite', nameKey: 'permission.memberGroupCreate', descKey: 'permission.memberGroupCreateDesc', featureReady: false },
        { code: 'hotdeal.write', group: 'USE', type: 'memberSite', nameKey: 'permission.memberHotdealWrite', descKey: 'permission.memberHotdealWriteDesc', featureReady: false },
        { code: 'commerce.purchase', group: 'USE', type: 'memberSite', nameKey: 'permission.memberCommercePurchase', descKey: 'permission.memberCommercePurchaseDesc', featureReady: false },
        { code: 'ad.apply', group: 'USE', type: 'memberSite', nameKey: 'permission.memberAdApply', descKey: 'permission.memberAdApplyDesc', featureReady: false },
        { code: 'board.post.hide', group: 'BOARD', type: 'memberBoard', nameKey: 'permission.memberBoardPostHide', descKey: 'permission.memberBoardPostHideDesc', featureReady: true },
        { code: 'board.post.delete', group: 'BOARD', type: 'memberBoard', nameKey: 'permission.memberBoardPostDelete', descKey: 'permission.memberBoardPostDeleteDesc', featureReady: true },
        { code: 'board.comment.delete', group: 'BOARD', type: 'memberBoard', nameKey: 'permission.memberBoardCommentDelete', descKey: 'permission.memberBoardCommentDeleteDesc', featureReady: true },
        { code: 'board.sanction.issue', group: 'BOARD', type: 'memberBoard', nameKey: 'permission.memberBoardSanction', descKey: 'permission.memberBoardSanctionDesc', featureReady: true },
        { code: 'board.notice.pin', group: 'BOARD', type: 'memberBoard', nameKey: 'permission.memberBoardNoticePin', descKey: 'permission.memberBoardNoticePinDesc', featureReady: true, managerOnly: true },
        { code: 'board.staff.appoint', group: 'BOARD', type: 'memberBoard', nameKey: 'permission.memberBoardStaffAppoint', descKey: 'permission.memberBoardStaffAppointDesc', featureReady: true, managerOnly: true },
        { code: 'board.staff-action.restore', group: 'BOARD', type: 'memberBoard', nameKey: 'permission.memberBoardStaffRestore', descKey: 'permission.memberBoardStaffRestoreDesc', featureReady: true, managerOnly: true },
    ];

    /** 모든 권한 코드 — 최고 관리자가 받는 목록 */
    static codes(): string[] {
        return UtilPermissionCatalog.PERMISSIONS.map((p) => p.code);
    }

    static permissionOf(code: string): PermissionDef | undefined {
        return UtilPermissionCatalog.PERMISSIONS.find((p) => p.code === code);
    }

    static typeOf(typeId: string): RoleTypeDef | undefined {
        return UtilPermissionCatalog.TYPES.find((type) => type.id === typeId);
    }

    static typesOfArea(areaId: string): RoleTypeDef[] {
        return UtilPermissionCatalog.TYPES.filter((type) => type.area === areaId);
    }

    /**
     * 세부 유형의 운영자 역할이 고를 수 있는 권한 후보 — 그 유형에 속한 권한 전부. 종합 관리 유형은 유형 제한 없이 모든 권한이다.
     * @param {string} typeId - 세부 유형 id(`TYPES`).
     * @returns {PermissionDef[]} 권한 후보(화면이 아직 없는 권한 포함 — 기능 준비 중 표시가 붙는다).
     */
    static candidatesOfType(typeId: string): PermissionDef[] {
        if (typeId === UtilPermissionCatalog.GENERAL_TYPE) return [...UtilPermissionCatalog.PERMISSIONS];
        return UtilPermissionCatalog.PERMISSIONS.filter((p) => p.type === typeId);
    }

    /**
     * 영역의 운영자 권한 전부 — 그 영역의 모든 세부 유형의 조회·처리·설정·민감 조치(화면이 아직 없는 권한 포함). 권한이 카탈로그에 더해지면 따라 늘어난다.
     * @param {string} areaId - 영역 id(`AREAS`).
     * @returns {PermissionDef[]} 영역의 권한(카탈로그 차례). 모르는 영역·종합이면 빈 목록.
     */
    static candidatesOfArea(areaId: string): PermissionDef[] {
        return UtilPermissionCatalog.PERMISSIONS.filter((p) => UtilPermissionCatalog.typeOf(p.type)?.area === areaId);
    }

    /**
     * 회원 권한 하나.
     * @param {string} code - 회원 권한 코드.
     * @returns {MemberPermissionDef | undefined} 정의, 모르는 코드면 undefined.
     */
    static memberPermissionOf(code: string): MemberPermissionDef | undefined {
        return UtilPermissionCatalog.MEMBER_PERMISSIONS.find((p) => p.code === code);
    }

    /**
     * 운영자 권한 코드들을 단위(조회·처리·설정·민감 조치)별 묶음으로 — 단위 차례를 지키고 비어 있는 단위와 모르는 코드는 뺀다.
     * @param {readonly string[]} codes - 권한 코드들.
     * @returns {PermissionUnitGroup[]} 단위별 묶음.
     */
    static groupByUnit(codes: readonly string[]): PermissionUnitGroup[] {
        const set = new Set(codes);
        return UtilPermissionCatalog.UNITS.map((unit) => ({ unit, items: UtilPermissionCatalog.PERMISSIONS.filter((p) => p.unit === unit && set.has(p.code)) })).filter((group) => group.items.length > 0);
    }

    /**
     * 이 회원 역할이 고를 수 있는 권한 후보 — 사이트 범위 역할(등급·회원 유형·사용자가 추가한 회원 유형)은 이용 묶음(준비 중 포함, 비활성), 게시판 범위 역할(매니저·스텝)은 게시판 운영 묶음이다.
     * 게시판 스텝에게는 매니저 전용 권한이 후보로 나오지 않는다.
     * @param {RoleScope} scope - 역할의 범위(`SITE` 사이트 전체 · `BOARD` 게시판 하나).
     * @param {string} [roleId] - 수정 중인 역할 아이디(스텝 판정에 쓴다).
     * @returns {MemberPermissionDef[]} 권한 후보.
     */
    static memberCandidatesOf(scope: RoleScope, roleId?: string): MemberPermissionDef[] {
        const group: MemberPermissionGroup = scope === 'BOARD' ? 'BOARD' : 'USE';
        const staff = roleId === UtilPermissionCatalog.BOARD_STAFF_ROLE;
        return UtilPermissionCatalog.MEMBER_PERMISSIONS.filter((p) => p.group === group && !(staff && p.managerOnly));
    }

    /**
     * 권한 코드들을 화면이 그릴 묶음으로 — 운영자는 조회·처리·설정·민감 조치, 회원은 이용 · 게시판 운영. 비어 있는 묶음과 모르는 코드는 뺀다.
     * @param {RoleKind} kind - 역할의 축.
     * @param {readonly string[]} codes - 권한 코드들.
     * @returns {PermissionGroupView[]} 묶음들.
     */
    static viewGroups(kind: RoleKind, codes: readonly string[]): PermissionGroupView[] {
        if (kind === 'OPERATOR') {
            return UtilPermissionCatalog.groupByUnit(codes).map((group) => ({
                key: group.unit,
                labelKey: `permission.unit.${group.unit}`,
                hintKey: `permission.unitHint.${group.unit}`,
                icon: UtilPermissionCatalog.UNIT_ICONS[group.unit],
                items: group.items,
            }));
        }
        const set = new Set(codes);
        return UtilPermissionCatalog.MEMBER_GROUPS.map((group) => ({
            key: group,
            labelKey: `permission.memberGroup.${group}`,
            hintKey: `permission.memberGroupHint.${group}`,
            icon: UtilPermissionCatalog.MEMBER_GROUP_ICONS[group],
            items: UtilPermissionCatalog.MEMBER_PERMISSIONS.filter((p) => p.group === group && set.has(p.code)),
        })).filter((group) => group.items.length > 0);
    }

    /**
     * `codes` 중 `can` 이 허락하지 않는 것 — 권한 상승 검사. 비어 있어야 "내가 가진 권한 안"이다.
     * @param {readonly string[]} codes - 주려는 권한 코드들.
     * @param {(code: string) => boolean} can - 지금 운영자가 그 권한을 가졌는가.
     * @returns {string[]} 내 권한 밖의 코드들.
     */
    static outside(codes: readonly string[], can: (code: string) => boolean): string[] {
        return codes.filter((code) => !can(code));
    }
}
