// MOCK: 역할 API(`GET·POST /api/admin/roles`, `PUT·DELETE …/{roleId}`, `PUT …/{roleId}/permissions`)의 메모리 목업 — `useRoleApi.ts` 가 이 파일의 함수를 부른다.
// 서버가 붙으면 `role/api/mock/` 와 함께 지운다. 운영자 목업(`operator-mock.ts`)이 "누가 어떤 역할을 가졌나"를 이 파일에서 읽고 쓴다(역할을 쓰는 운영자 수·삭제 거절이 같은 값을 봐야 해서).
// 역할 유형은 `role-store.ts` 가 들고 있고(고정) 역할 유형 목업(`role-type-mock.ts`)과 이 파일이 같은 목록을 본다.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` (쉼표로 여러 개) ─
//   role-error        역할 목록을 읽는 요청이 `500 INTERNAL` 로 실패(오류 상태와 다시 시도)
//   role-empty        역할이 하나도 없는 상태(빈 상태)
//   role-save-error   만들기·수정·권한 저장 요청이 `500 INTERNAL` 로 실패(다이얼로그의 오류 문구)
//   role-in-use       삭제가 늘 `409 ROLE_IN_USE`(쓰는 운영자·회원이 있다는 거절) — 실제 사용자가 없는 역할도 거절된다
//
// ── 흉내 ─
//   역할은 **역할 유형** 아래에 있다. 운영자 역할은 영역(대분류) → 세부 유형(24개) 두 단계이고 역할 하나는 세부 유형 하나에 속한다(역할 유형 id = `op_<세부 유형 id>`).
//   시드: 시스템(운영자·역할 관리 3 · 감사·배치 2 · 사이트·연동 1) · 종합(종합 관리 보조 1) · 업무별 담당 역할(콘텐츠 4 · 커뮤니티 안전 4 · 회원 5 · 소모임 1 · 핫딜 1 · 공동구매 4 · 사업자·광고 3).
//   업무별 역할의 기본 권한 세트는 **목업 값**이다(그 유형의 조회+처리+설정 중심, 민감 조치는 일부만 — 예: 주문·배송 담당은 환불 승인 포함, 정산·증빙 담당은 CSV 내보내기 없음). 기능(화면)이 아직 없는 권한도 줄 수 있다.
//   종합 관리 유형에는 영역별 관리자(`<영역 이름> 관리자`, `ROLE_AREA_<영역>_ADMIN`)를 둔다 — 종합을 뺀 영역마다 그 영역의 권한 전부를 카탈로그에서 계산해 준다(`areaAdminRoles`). 이미 같은 이름의 시드(소모임·핫딜 관리자)는 건너뛴다.
//   예전의 영역 단위 관리자(콘텐츠 관리자·공동구매 관리자 등)는 세부 유형별 역할로 나눴다 — 여러 유형에 걸친 권한은 운영자에게 역할을 둘 이상 줘서 합친다(합집합).
//   회원 역할: 회원 유형(일반 회원 · 사업자 회원, 내장) · 게시판 단위(게시판 매니저 · 게시판 스텝, 내장) · 사용자 정의(베타 테스터 · 우수 회원 · 이벤트 참가자, 운영자가 추가한 역할) ·
//   **등급 역할은 정적 시드가 아니다** — 등급 설정(`grade-mock.ts`)의 등급 목록에서 `ROLE_GRADE_<gradeKey>` 로 매번 만든다(등급 유형에 속하고 내장 취급: 이름·설명은 `409 ROLE_DERIVED` 로 막고 권한만 편집,
//   등급을 추가하면 생기고 지우면 사라진다). 권한은 등급 키별로 목업이 들고 있고 새 등급은 기본 권한 세트(일반 등급과 같은 세트)를 복사한다.
//   회원 역할 이름은 회원 조회 화면이 Tag 로 쓰므로 안정적으로 둔다. 회원 역할의 `memberCount` 는 해당 역할을 가진 회원 수 예시(고정값, "이벤트 참가자"만 0 이라 지울 수 있다). 바꾼 것은 메모리에만 남아 새로고침하면 처음으로 돌아간다.
//   서버 규칙 흉내: 역할 아이디 형식·중복(409 ROLE_DUPLICATED), 이름 필수·50자, 없는 유형(404 ROLE_TYPE_NOT_FOUND), **등급 유형에는 역할을 만들 수 없음(409 ROLE_TYPE_DERIVED)**,
//   제품이 정한 회원 유형(회원 유형·게시판 단위)에도 만들 수 없음(409 ROLE_TYPE_FIXED), 종합 관리 유형(`op_general`)의 역할은 최고 관리자만 만들고 고친다(403 ROLE_GENERAL_SUPER_ONLY),
//   그 유형의 권한 후보 밖의 권한(400 ROLE_TYPE_MISMATCH — 운영자 역할은 그 세부 유형의 권한, 종합 관리는 전부),
//   **운영자 역할: 내가 가진 권한 밖은 줄 수 없다(403 PERMISSION_ESCALATION)** — "나"는 로그인 목업(`admin-auth-mock.ts`)의 계정이다. 회원 역할에는 이 규칙이 없다(정책 제안). 화면(기능)이 아직 없는 권한도 줄 수 있다.
//   내장 역할은 삭제할 수 없다(409 ROLE_BUILTIN). 필요한 권한이 없으면 403 PERMISSION_DENIED(`role.manage` — 만들기·수정·삭제·회원 역할의 권한 바꾸기, `role.grant` — 운영자 역할의 권한 바꾸기).

import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import { mockCurrentAdmin } from '~/composables/auth/api/mock/admin-auth-mock';
import { findStoredRoleType } from '~/composables/role/api/mock/role-store';
import type { RoleCreateRequest, RolePermissionsRequest, RoleUpdateRequest, RoleView } from '~/composables/role/api/useRoleApi.types';
import { mockGradeSnapshot } from '~/composables/grade/api/mock/grade-mock';
import UtilGrade from '~/utils/util-grade';
import UtilPermissionCatalog from '~/utils/util-permission-catalog';
import UtilRole from '~/utils/util-role';
import koPermission from '~~/i18n/locales/ko/permission';

/** 저장된 역할 — 사용 수(`operatorCount`·`memberCount`)는 응답을 만들 때 센다(회원 수는 고정 예시) */
type StoredRole = Omit<RoleView, 'operatorCount' | 'memberCount'>;

/** 운영자 역할의 고정 칸 — 범위 사이트 전체 · 부여 방식 수동(운영자가 운영자에게) */
const OP = { roleKind: 'OPERATOR', scope: 'SITE', grantMode: 'MANUAL' } as const;
/** 회원 역할 — 사이트 범위 */
const SITE = { roleKind: 'MEMBER', scope: 'SITE' } as const;

/** 회원 역할의 사이트 전체 능력(이용) 8개 중 일부씩 */
const USE_NEWBIE = ['post.write', 'comment.write', 'post.react', 'report.create'];
const USE_NORMAL = [...USE_NEWBIE, 'message.send', 'file.attach', 'shop.use'];
const USE_REGULAR = [...USE_NORMAL, 'nickname-image.request'];

/** 운영자 역할의 유형 아이디 — 세부 유형마다 하나 */
const OPT = (catalogTypeId: string) => UtilRole.operatorTypeIdOf(catalogTypeId);

/** 목록에 쌓인 순서가 곧 보이는 순서다(유형 안에서) */
const ROLES: StoredRole[] = [
    // ── 시스템(운영자) ──
    { roleId: 'ROLE_AUDIT_VIEWER', roleName: '감사·배치 조회', description: '감사 로그와 배치 기록을 봅니다.', roleTypeId: OPT('auditBatch'), builtin: true, permissionCodes: ['audit.read', 'batch.read'], ...OP },
    { roleId: 'ROLE_BATCH_OPERATOR', roleName: '배치 운영', description: '배치를 켜고 끄고 즉시 실행합니다.', roleTypeId: OPT('auditBatch'), builtin: false, permissionCodes: ['batch.read', 'batch.manage', 'batch.run'], ...OP },
    { roleId: 'ROLE_OPERATOR_VIEWER', roleName: '운영자 조회', description: '운영자와 역할을 보기만 합니다.', roleTypeId: OPT('operatorRole'), builtin: false, permissionCodes: ['operator.read', 'role.read'], ...OP },
    { roleId: 'ROLE_OPERATOR_ADMIN', roleName: '운영자 관리', description: '운영자를 등록·승인하고 잠금을 다룹니다. 역할은 바꾸지 못합니다.', roleTypeId: OPT('operatorRole'), builtin: false, permissionCodes: ['operator.read', 'role.read', 'operator.manage', 'operator.lock'], ...OP },
    { roleId: 'ROLE_ACCESS_ADMIN', roleName: '권한 관리', description: '역할을 만들고 권한을 줍니다.', roleTypeId: OPT('operatorRole'), builtin: false, permissionCodes: ['operator.read', 'role.read', 'role.manage', 'role.grant'], ...OP },
    { roleId: 'ROLE_SITE_ADMIN', roleName: '사이트·연동 관리자', description: '사이트 기본 정보와 표시 기본값을 보고 바꿉니다. 키·비밀은 못 바꿉니다.', roleTypeId: OPT('siteIntegration'), builtin: false, permissionCodes: ['site.read', 'site.manage'], ...OP },
    // ── 종합(운영자) ──
    { roleId: 'ROLE_GENERAL_SUB', roleName: '종합 관리 보조', description: '최고 관리자를 돕는 역할입니다. 여러 업무 영역의 권한을 함께 갖습니다.', roleTypeId: OPT('general'), builtin: false, permissionCodes: ['audit.read', 'batch.read', 'batch.manage', 'operator.read', 'operator.manage', 'role.read'], ...OP },
    // ── 콘텐츠(운영자) ──
    { roleId: 'ROLE_NOTICE_ADMIN', roleName: '공지·주요글 관리자', description: '공지와 하이라이트를 올리고 노출 규칙을 정합니다.', roleTypeId: OPT('notice'), builtin: false, permissionCodes: ['notice.read', 'notice.manage', 'notice.settings'], ...OP },
    { roleId: 'ROLE_POST_ADMIN', roleName: '게시글 관리자', description: '글을 찾아 숨기고 삭제하고 옮깁니다. 일괄 삭제는 못 합니다.', roleTypeId: OPT('post'), builtin: false, permissionCodes: ['post.read', 'post.manage'], ...OP },
    { roleId: 'ROLE_BOARD_ADMIN', roleName: '게시판 구성 관리자', description: '게시판을 만들고 기본값을 정하며 게시판 매니저·스텝을 지정합니다. 게시판 삭제는 못 합니다.', roleTypeId: OPT('board'), builtin: false, permissionCodes: ['board.read', 'board.manage', 'board.settings', 'board.staff.manage', 'board.staff.limit'], ...OP },
    { roleId: 'ROLE_LAYOUT_ADMIN', roleName: '사이트 화면 관리자', description: '공개 사이트의 메뉴와 홈 화면 구성을 바꿉니다.', roleTypeId: OPT('siteLayout'), builtin: false, permissionCodes: ['layout.read', 'layout.settings'], ...OP },
    // ── 커뮤니티 안전(운영자) ──
    { roleId: 'ROLE_REPORT_ADMIN', roleName: '신고·검토 담당', description: '신고를 처리하고 공개 전 검토 대기를 승인·반려합니다.', roleTypeId: OPT('report'), builtin: false, permissionCodes: ['report.read', 'report.manage'], ...OP },
    { roleId: 'ROLE_SANCTION_ADMIN', roleName: '제재·이의제기 담당', description: '경고·정지를 주고 풀며 이의제기를 판단합니다. 영구 정지까지 줄 수 있습니다.', roleTypeId: OPT('sanction'), builtin: false, permissionCodes: ['sanction.read', 'sanction.manage', 'appeal.manage', 'sanction.ban'], ...OP },
    { roleId: 'ROLE_BADWORD_ADMIN', roleName: '금칙어 관리자', description: '금칙어를 보고 사전을 바꿉니다.', roleTypeId: OPT('badword'), builtin: false, permissionCodes: ['bannedword.read', 'bannedword.settings'], ...OP },
    { roleId: 'ROLE_STAFF_AUDITOR', roleName: '스텝 처리 감독', description: '게시판 스텝이 처리한 내역을 봅니다. 처리 복구는 못 합니다.', roleTypeId: OPT('staffAudit'), builtin: false, permissionCodes: ['keeper-action.read'], ...OP },
    // ── 회원(운영자) ──
    { roleId: 'ROLE_MEMBER_ADMIN', roleName: '회원 조회 관리자', description: '회원 목록과 상세를 보고 전체 이메일까지 열람합니다.', roleTypeId: OPT('memberView'), builtin: false, permissionCodes: ['member.read', 'member.pii'], ...OP },
    { roleId: 'ROLE_MEMBER_LOOKUP', roleName: '회원 조회 담당', description: '회원 목록과 상세를 봅니다. 전체 이메일은 보지 못합니다.', roleTypeId: OPT('memberView'), builtin: false, permissionCodes: ['member.read'], ...OP },
    { roleId: 'ROLE_NICKNAME_ADMIN', roleName: '닉네임 이미지 심사 담당', description: '닉네임 이미지 신청을 승인·반려합니다.', roleTypeId: OPT('nicknameImage'), builtin: false, permissionCodes: ['nickname-image.read', 'nickname-image.manage'], ...OP },
    { roleId: 'ROLE_RANK_ADMIN', roleName: '등급 관리자', description: '등급 승급 조건을 보고 바꿉니다.', roleTypeId: OPT('gradePoint'), builtin: false, permissionCodes: ['grade.read', 'grade.manage'], ...OP },
    { roleId: 'ROLE_POINT_ADMIN', roleName: '포인트·휴면 관리자', description: '포인트 규칙과 휴면 기준을 보고 바꿉니다. 포인트 수동 조정은 못 합니다.', roleTypeId: OPT('gradePoint'), builtin: false, permissionCodes: ['point.read', 'point.settings', 'dormancy.read', 'dormancy.settings'], ...OP },
    // ── 소모임·핫딜(운영자) ──
    { roleId: 'ROLE_CLUB_ADMIN', roleName: '소모임 관리자', description: '소모임 개설 신청을 처리하고 규칙·한도를 정합니다.', roleTypeId: OPT('club'), builtin: false, permissionCodes: ['group.read', 'group.manage', 'group.settings'], ...OP },
    { roleId: 'ROLE_HOTDEAL_ADMIN', roleName: '핫딜 관리자', description: '핫딜을 운영하고 금지·제휴 도메인을 정합니다.', roleTypeId: OPT('hotdeal'), builtin: false, permissionCodes: ['hotdeal.read', 'hotdeal.manage', 'hotdeal.domain.manage'], ...OP },
    // ── 공동구매(운영자) — "공구 담당"은 상품·공구 기획 + 주문·배송 역할 둘을 주는 식이다 ──
    { roleId: 'ROLE_GB_PLANNER', roleName: '상품·공구 기획 담당', description: '상품을 등록하고 공구를 만들어 공개·종료합니다. 가격 변경까지 할 수 있지만 재고 조정은 못 합니다.', roleTypeId: OPT('gbPlan'), builtin: false, permissionCodes: ['commerce.product.read', 'commerce.product.manage', 'commerce.product.settings', 'commerce.product.price'], ...OP },
    { roleId: 'ROLE_GB_ORDER', roleName: '주문·배송 담당', description: '주문 상태와 송장, 취소·반품을 다루고 환불을 승인합니다. 배송지 열람은 못 합니다.', roleTypeId: OPT('gbOrder'), builtin: false, permissionCodes: ['commerce.order.read', 'commerce.order.manage', 'commerce.refund.approve'], ...OP },
    { roleId: 'ROLE_GB_SETTLE', roleName: '정산·증빙 담당', description: '매출과 환불 내역을 봅니다. CSV 내보내기는 못 합니다.', roleTypeId: OPT('gbSettle'), builtin: false, permissionCodes: ['commerce.settlement.read'], ...OP },
    { roleId: 'ROLE_GB_CONFIG', roleName: '공구 설정 관리자', description: '판매 스위치·사업자 정보·택배사·결제 상태를 설정합니다.', roleTypeId: OPT('gbConfig'), builtin: false, permissionCodes: ['commerce.settings'], ...OP },
    // ── 사업자·광고(운영자) ──
    { roleId: 'ROLE_BIZ_REVIEW', roleName: '사업자 심사 담당', description: '사업자 신청을 승인·반려하고 정지합니다.', roleTypeId: OPT('bizReview'), builtin: false, permissionCodes: ['business.read', 'business.manage'], ...OP },
    { roleId: 'ROLE_BIZ_PRODUCT', roleName: '입점 상품·주문 담당', description: '입점권 주문과 노출 취소를 다루고 입점 상품·가격을 정합니다. 환불은 못 합니다.', roleTypeId: OPT('bizProduct'), builtin: false, permissionCodes: ['deal-order.read', 'deal-order.manage', 'deal-order.settings'], ...OP },
    { roleId: 'ROLE_AD_ADMIN', roleName: '광고·홍보 담당', description: '광고 일정과 신청, 직접홍보 심사를 다루고 유형·자리를 정합니다. 긴급 내림은 못 합니다.', roleTypeId: OPT('adPromo'), builtin: false, permissionCodes: ['ad.read', 'ad.manage', 'promo.manage', 'ad.settings'], ...OP },

    // ── 회원 유형(회원) — 이름은 회원 조회 화면이 쓰므로 바꾸지 않는다 ──
    { roleId: 'ROLE_MEMBER_GENERAL', roleName: '일반 회원', description: '가입하면 기본으로 갖는 회원 유형입니다.', roleTypeId: UtilRole.TYPE_KIND, grantMode: 'AUTO', builtin: true, permissionCodes: [...USE_REGULAR], ...SITE },
    { roleId: 'ROLE_MEMBER_BUSINESS', roleName: '사업자 회원', description: '사업자 승인을 받은 회원입니다. 읽기·프로필·허용 게시판 위주로 제한됩니다.', roleTypeId: UtilRole.TYPE_KIND, grantMode: 'AUTO', builtin: true, permissionCodes: ['report.create', 'nickname-image.request'], ...SITE },

    // ── 게시판 단위(회원) — 게시판 하나를 맡도록 지정받는다 ──
    { roleId: 'ROLE_BOARD_MANAGER', roleName: '게시판 매니저', description: '게시판 하나를 맡아 운영합니다. 스텝 지정도 합니다.', roleTypeId: UtilRole.TYPE_BOARD, grantMode: 'APPOINTED', scope: 'BOARD', roleKind: 'MEMBER', builtin: true, permissionCodes: ['board.post.hide', 'board.post.delete', 'board.comment.delete', 'board.sanction.issue', 'board.notice.pin', 'board.staff.appoint', 'board.staff-action.restore'] },
    { roleId: UtilPermissionCatalog.BOARD_STAFF_ROLE, roleName: '게시판 스텝', description: '맡은 게시판에서 글·댓글을 정리하고 가벼운 제재를 합니다.', roleTypeId: UtilRole.TYPE_BOARD, grantMode: 'APPOINTED', scope: 'BOARD', roleKind: 'MEMBER', builtin: true, permissionCodes: ['board.post.hide', 'board.post.delete', 'board.comment.delete', 'board.sanction.issue'] },

    // ── 사용자 정의(회원) — 운영자가 만들어 회원에게 수동으로 주는 역할 ──
    { roleId: 'ROLE_BETA_TESTER', roleName: '베타 테스터', description: '새 기능을 먼저 써 보는 회원입니다.', roleTypeId: UtilRole.TYPE_CUSTOM, grantMode: 'MANUAL', builtin: false, permissionCodes: ['post.write', 'comment.write', 'file.attach'], ...SITE },
    { roleId: 'ROLE_VIP_MEMBER', roleName: '우수 회원', description: '운영자가 따로 정한 우수 회원입니다.', roleTypeId: UtilRole.TYPE_CUSTOM, grantMode: 'MANUAL', builtin: false, permissionCodes: [...USE_NORMAL, 'nickname-image.request'], ...SITE },
    { roleId: 'ROLE_EVENT_GUEST', roleName: '이벤트 참가자', description: '이벤트 기간에만 글과 댓글을 쓰게 한 회원입니다.', roleTypeId: UtilRole.TYPE_CUSTOM, grantMode: 'MANUAL', builtin: false, permissionCodes: ['post.write', 'comment.write'], ...SITE },
];

/**
 * 종합 관리 유형의 영역별 관리자 역할 — 종합을 뺀 영역마다 `<영역 이름> 관리자`를 만들고 그 영역의 권한을 카탈로그에서 계산해 전부 준다(`candidatesOfArea` — 카탈로그에 권한이 더해지면 따라간다).
 * 영역 이름은 한국어 문구 파일(`permission.area`)에서 읽는다(목업 역할 이름은 서버 몫의 한국어 값). 일반 역할(`builtin: false`)로 둔다 — 기존 종합 관리 보조 등 운영자 역할 시드와 같은 관례.
 * `taken` 에 이미 있는 이름(예: 소모임 관리자·핫딜 관리자 — 그 영역의 권한을 이미 전부 가진 시드 역할)은 같은 이름이 겹치지 않게 건너뛴다.
 */
function areaAdminRoles(taken: ReadonlySet<string>): StoredRole[] {
    const areaNames: Record<string, string> = koPermission.area;
    const roles: StoredRole[] = [];
    for (const area of UtilPermissionCatalog.AREAS) {
        if (area.id === UtilPermissionCatalog.GENERAL_AREA) continue;
        const areaName = areaNames[area.id] ?? area.id;
        const roleName = `${areaName} 관리자`;
        if (taken.has(roleName)) continue;
        roles.push({
            roleId: `ROLE_AREA_${area.id.toUpperCase()}_ADMIN`,
            roleName,
            description: `${areaName} 영역의 모든 권한을 가진 관리자 역할입니다.`,
            roleTypeId: UtilRole.TYPE_GENERAL,
            builtin: false,
            permissionCodes: UtilPermissionCatalog.candidatesOfArea(area.id).map((p) => p.code),
            ...OP,
        });
    }
    return roles;
}
ROLES.push(...areaAdminRoles(new Set(ROLES.map((r) => r.roleName))));

/**
 * 등급 역할의 권한 — 등급 키별로 목업이 들고 있다. 등급 역할은 **정적 시드가 아니라 등급 설정의 등급 목록에서 만들어진다**(`gradeRoles()`): 등급을 추가하면 역할이 생기고(기본 권한 세트 복사) 지우면 사라진다.
 * 역할 아이디는 `ROLE_GRADE_<gradeKey>` 로 파생한다.
 */
const GRADE_PERMS: Record<string, string[]> = {
    SEEDLING: USE_NEWBIE,
    REGULAR: USE_NORMAL,
    LOYAL: USE_REGULAR,
    VETERAN: [...USE_REGULAR],
};
/** 새 등급이 처음 받는 권한 세트 */
const GRADE_PERMS_DEFAULT = USE_NORMAL;

/** 등급 목록에서 파생한 등급 역할(등급 유형 — 이름·승급 조건은 읽기 전용, 권한만 고친다). 서열 순서 */
function gradeRoles(): StoredRole[] {
    return mockGradeSnapshot().map((grade) => ({
        roleId: UtilGrade.roleIdOf(grade.gradeKey),
        roleName: grade.label,
        description: grade.level === 1 ? '가입하면 바로 갖는 기본 회원 등급입니다. 이름·뱃지는 등급 설정에서 바꿔요.' : '승급 조건을 채우면 자동으로 올라가는 회원 등급입니다. 이름·승급 조건은 등급 설정에서 바꿔요.',
        roleTypeId: UtilRole.TYPE_GRADE,
        grantMode: 'AUTO' as const,
        builtin: true,
        permissionCodes: [...(GRADE_PERMS[grade.gradeKey] ?? GRADE_PERMS_DEFAULT)],
        ...SITE,
    }));
}

/** 모든 역할 — 등급 역할(등급 목록에서 파생)이 맨 앞, 나머지는 저장 순서 */
function allRoles(): StoredRole[] {
    return [...gradeRoles(), ...ROLES];
}

/** 회원 역할을 가진 회원 수 예시(고정) — 등급 역할은 등급 설정의 회원 수를 쓴다 */
const MEMBER_COUNTS: Record<string, number> = {
    ROLE_MEMBER_GENERAL: 18200,
    ROLE_MEMBER_BUSINESS: 160,
    ROLE_BOARD_MANAGER: 38,
    ROLE_BOARD_STAFF: 112,
    ROLE_BETA_TESTER: 24,
    ROLE_VIP_MEMBER: 41,
    ROLE_EVENT_GUEST: 0,
};

/** 운영자 id → 받은 운영자 역할 id들. 운영자 목업이 읽고 쓴다 */
const assignments: Record<string, string[]> = {
    '1002': ['ROLE_AUDIT_VIEWER', 'ROLE_OPERATOR_VIEWER'],
    '1003': ['ROLE_AUDIT_VIEWER', 'ROLE_BATCH_OPERATOR', 'ROLE_OPERATOR_VIEWER'],
};

function operatorCountOf(roleId: string): number {
    return Object.values(assignments).filter((ids) => ids.includes(roleId)).length;
}

function memberCountOf(role: StoredRole): number {
    const key = UtilGrade.gradeKeyOfRole(role.roleId, role.roleTypeId);
    return key !== null ? (mockGradeSnapshot().find((g) => g.gradeKey === key)?.memberCount ?? 0) : (MEMBER_COUNTS[role.roleId] ?? 0);
}

function view(role: StoredRole): RoleView {
    return { ...role, permissionCodes: [...role.permissionCodes], operatorCount: role.roleKind === 'OPERATOR' ? operatorCountOf(role.roleId) : 0, memberCount: role.roleKind === 'MEMBER' ? memberCountOf(role) : 0 };
}

function find(roleId: string): StoredRole {
    const role = allRoles().find((r) => r.roleId === roleId);
    if (!role) throw new ApiError({ status: 404, code: 'ROLE_NOT_FOUND', message: '없는 역할이에요.' });
    return role;
}

/** 지금 로그인한 운영자가 이 권한을 가졌는가 */
function actorCan(code: string): boolean {
    const actor = mockCurrentAdmin();
    return actor !== null && (actor.isSuper || actor.permissions.includes(code));
}

function requirePermission(code: string): void {
    if (!actorCan(code)) throw new ApiError({ status: 403, code: 'PERMISSION_DENIED', message: '이 작업을 할 권한이 없어요.' });
}

/** 종합 관리 유형의 운영자 역할은 최고 관리자만 만들고 고친다 */
function requireSuperFor(role: { roleKind: string; roleTypeId: string }): void {
    if (role.roleKind === 'OPERATOR' && UtilRole.isGeneralType(role.roleTypeId) && !mockCurrentAdmin()?.isSuper) {
        throw new ApiError({ status: 403, code: 'ROLE_GENERAL_SUPER_ONLY', message: '종합 관리 역할은 최고 관리자만 만들고 고칠 수 있어요.' });
    }
}

/**
 * 새로 주는 권한이 그 역할이 고를 수 있는 후보 안인지 본다. 운영자 역할은 내 권한 안인지도 본다 — 이미 가진 것(`keep`)은 내 권한 밖이어도 그대로 둘 수 있다.
 * 회원 역할은 권한 상승 규칙이 없다. 화면(기능)이 아직 없는 권한도 줄 수 있다.
 */
function checkPermissions(role: StoredRole, codes: string[], keep: readonly string[]): void {
    if (role.roleKind === 'MEMBER') {
        const candidates = new Set(UtilPermissionCatalog.memberCandidatesOf(role.scope, role.roleId).map((p) => p.code));
        for (const code of codes) {
            if (!UtilPermissionCatalog.memberPermissionOf(code)) throw new ApiError({ status: 400, code: 'PERMISSION_UNKNOWN', message: `없는 권한이에요: ${code}` });
            if (!candidates.has(code)) throw new ApiError({ status: 400, code: 'ROLE_TYPE_MISMATCH', message: `이 역할에는 줄 수 없는 권한이에요: ${code}` });
        }
        return;
    }
    const candidates = new Set(UtilRole.operatorCandidatesOf(role.roleTypeId).map((p) => p.code));
    for (const code of codes) {
        if (!UtilPermissionCatalog.permissionOf(code)) throw new ApiError({ status: 400, code: 'PERMISSION_UNKNOWN', message: `없는 권한이에요: ${code}` });
        if (!candidates.has(code)) throw new ApiError({ status: 400, code: 'ROLE_TYPE_MISMATCH', message: `이 역할 유형에서는 줄 수 없는 권한이에요: ${code}` });
    }
    const added = codes.filter((code) => !keep.includes(code));
    if (added.some((code) => !actorCan(code))) {
        throw new ApiError({ status: 403, code: 'PERMISSION_ESCALATION', message: '내가 가진 권한 밖의 권한은 줄 수 없어요.' });
    }
}

/** `GET /api/admin/roles` */
export async function mockRoleList(): Promise<RoleView[]> {
    await mockDelay();
    if (mockFlags().has('role-error')) throw MOCK_ERRORS.internal();
    if (mockFlags().has('role-empty')) return [];
    return allRoles().map(view);
}

/** `POST /api/admin/roles` */
export async function mockRoleCreate(body: RoleCreateRequest): Promise<RoleView> {
    await mockDelay();
    if (mockFlags().has('role-save-error')) throw MOCK_ERRORS.internal();
    requirePermission('role.manage');

    const fields: Record<string, string> = {};
    if (!/^ROLE_[A-Z0-9_]+$/.test(body.roleId) || body.roleId.length > 30 || body.roleId === 'ROLE_') fields.roleId = '역할 아이디는 ROLE_ 로 시작하는 대문자·숫자·밑줄이고 30자까지예요.';
    if (!body.roleName.trim()) fields.roleName = '역할 이름을 입력해 주세요.';
    else if (body.roleName.trim().length > 50) fields.roleName = '역할 이름은 50자까지예요.';
    if (body.description.length > 200) fields.description = '설명은 200자까지예요.';
    if (Object.keys(fields).length) throw new ApiError({ status: 400, code: 'VALIDATION', message: '입력한 내용을 다시 확인해 주세요.', fields });

    const type = findStoredRoleType(body.roleTypeId);
    if (!type) throw new ApiError({ status: 404, code: 'ROLE_TYPE_NOT_FOUND', message: '없는 역할 유형이에요.' });
    if (type.derived) throw new ApiError({ status: 409, code: 'ROLE_TYPE_DERIVED', message: '등급 역할은 등급 설정에서 만들어요.' });
    if (!type.creatable) throw new ApiError({ status: 409, code: 'ROLE_TYPE_FIXED', message: '이 유형의 역할은 제품이 정해 두어서 새로 만들 수 없어요.' });

    let created: StoredRole;
    if (type.axis === 'MEMBER') {
        // 회원 역할(사용자 정의) — 사이트 범위, 운영자가 회원에게 직접 준다
        created = { roleId: body.roleId, roleName: body.roleName.trim(), description: body.description.trim() || null, roleTypeId: type.roleTypeId, grantMode: 'MANUAL', builtin: false, permissionCodes: [], ...SITE };
    } else {
        requireSuperFor({ roleKind: 'OPERATOR', roleTypeId: type.roleTypeId });
        created = { roleId: body.roleId, roleName: body.roleName.trim(), description: body.description.trim() || null, roleTypeId: type.roleTypeId, builtin: false, permissionCodes: [], ...OP };
    }
    if (allRoles().some((r) => r.roleId === body.roleId)) throw new ApiError({ status: 409, code: 'ROLE_DUPLICATED', message: `이미 있는 역할 아이디예요: ${body.roleId}`, fields: { roleId: '이미 쓰는 아이디예요.' } });
    ROLES.push(created);
    return view(created);
}

/** `PUT /api/admin/roles/{roleId}` — 이름·설명 */
export async function mockRoleUpdate(roleId: string, body: RoleUpdateRequest): Promise<RoleView> {
    await mockDelay();
    if (mockFlags().has('role-save-error')) throw MOCK_ERRORS.internal();
    requirePermission('role.manage');
    const role = find(roleId);
    requireSuperFor(role);
    if (role.roleTypeId === UtilRole.TYPE_GRADE) throw new ApiError({ status: 409, code: 'ROLE_DERIVED', message: '등급 역할의 이름·설명은 등급 설정에서 바꿔요.' });

    const fields: Record<string, string> = {};
    if (!body.roleName.trim()) fields.roleName = '역할 이름을 입력해 주세요.';
    else if (body.roleName.trim().length > 50) fields.roleName = '역할 이름은 50자까지예요.';
    if (body.description.length > 200) fields.description = '설명은 200자까지예요.';
    if (Object.keys(fields).length) throw new ApiError({ status: 400, code: 'VALIDATION', message: '입력한 내용을 다시 확인해 주세요.', fields });

    role.roleName = body.roleName.trim();
    role.description = body.description.trim() || null;
    return view(role);
}

/** `PUT /api/admin/roles/{roleId}/permissions` — 통째로 바꾼다. 운영자 역할은 `role.grant`, 회원 역할은 `role.manage` */
export async function mockRoleSetPermissions(roleId: string, body: RolePermissionsRequest): Promise<RoleView> {
    await mockDelay();
    if (mockFlags().has('role-save-error')) throw MOCK_ERRORS.internal();
    const role = find(roleId);
    requirePermission(role.roleKind === 'MEMBER' ? 'role.manage' : 'role.grant');
    requireSuperFor(role);
    checkPermissions(role, body.permissionCodes, role.permissionCodes);
    role.permissionCodes = [...new Set(body.permissionCodes)];
    // 등급 역할은 등급 목록에서 매번 만들어지므로 권한은 등급 키별 저장소에 남긴다
    const gradeKey = UtilGrade.gradeKeyOfRole(role.roleId, role.roleTypeId);
    if (gradeKey !== null) GRADE_PERMS[gradeKey] = [...role.permissionCodes];
    return view(role);
}

/** `DELETE /api/admin/roles/{roleId}` */
export async function mockRoleDelete(roleId: string): Promise<void> {
    await mockDelay();
    requirePermission('role.manage');
    const role = find(roleId);
    requireSuperFor(role);
    if (role.builtin) throw new ApiError({ status: 409, code: 'ROLE_BUILTIN', message: '제품이 제공하는 역할은 지울 수 없어요. 권한은 조정할 수 있어요.' });
    const { operatorCount, memberCount } = view(role);
    if (mockFlags().has('role-in-use') || operatorCount > 0 || memberCount > 0) {
        const message = role.roleKind === 'MEMBER' ? `이 역할을 가진 회원이 ${Math.max(memberCount, 1)}명 있어요. 먼저 다른 역할로 바꿔 주세요.` : `이 역할을 쓰는 운영자가 ${Math.max(operatorCount, 1)}명 있어요. 먼저 다른 역할로 바꿔 주세요.`;
        throw new ApiError({ status: 409, code: 'ROLE_IN_USE', message, extras: { operatorCount, memberCount } });
    }
    ROLES.splice(ROLES.indexOf(role), 1);
}

// ── 역할 유형 목업이 쓰는 것 ─

/** 한 역할 유형에 든 역할 수 */
export function mockRoleCountOfType(roleTypeId: string): number {
    return allRoles().filter((r) => r.roleTypeId === roleTypeId).length;
}

// ── 운영자 목업이 쓰는 것 — 운영자 역할만 다룬다(회원 역할은 운영자에게 줄 수 없다) ─

/** 운영자 역할의 이름 — 모르는 id·회원 역할이면 undefined */
export function mockRoleBrief(roleId: string): { roleId: string; roleName: string } | undefined {
    const role = ROLES.find((r) => r.roleId === roleId && r.roleKind === 'OPERATOR');
    return role ? { roleId: role.roleId, roleName: role.roleName } : undefined;
}

/** 역할이 가진 권한 코드 — 모르는 id 면 빈 목록 */
export function mockRolePermissions(roleId: string): string[] {
    return [...(ROLES.find((r) => r.roleId === roleId)?.permissionCodes ?? [])];
}

/** 운영자 역할의 역할 유형 id — 모르는 id·회원 역할이면 undefined */
export function mockRoleTypeIdOf(roleId: string): string | undefined {
    return ROLES.find((r) => r.roleId === roleId && r.roleKind === 'OPERATOR')?.roleTypeId;
}

export function mockAssignedRoleIds(operatorId: string): string[] {
    return [...(assignments[operatorId] ?? [])];
}

export function mockSetAssignedRoleIds(operatorId: string, roleIds: string[]): void {
    assignments[operatorId] = [...roleIds];
}
