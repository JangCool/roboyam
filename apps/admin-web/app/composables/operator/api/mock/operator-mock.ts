// MOCK: 운영자 API(`GET·POST /api/admin/operators`, `PUT …/{operatorId}`·`…/status`·`POST …/rejection`, `GET·POST /api/admin/operator-invitations`, `DELETE …/{invitationId}`)의 메모리 목업 — `useOperatorApi.ts` 가 이 파일의 함수를 부른다.
// 서버가 붙으면 `operator/api/mock/` 와 함께 지운다. 운영자가 받은 역할은 역할 목업(`role-mock.ts`)이 들고 있다.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` (쉼표로 여러 개) ─
//   operator-error        목록을 읽는 요청이 `500 INTERNAL` 로 실패(오류 상태와 다시 시도)
//   operator-empty        운영자·대기 중인 초대가 하나도 없는 상태(탭마다 빈 상태)
//   operator-save-error   저장(정보·상태)·추가·초대·반려·초대 취소 요청이 `500 INTERNAL` 로 실패(폼의 오류 문구)
//   operator-last-super   상태 저장이 늘 `409 OPERATOR_LAST_SUPER`("마지막 활성 최고 관리자라 막을 수 없다") — 루트 계정은 화면에서 이미 막혀 있어 서버의 마지막 방어선 문구를 확인하는 용도
//
// ── 흉내 ─
//   운영자 8명: 루트 최고 관리자 1(김관리) · 활성 2(박운영 역할 2개 · 이배치 역할 3개) · 신규(승인 대기) 2 · 잠김 1 · 만료 1 · 사용 중지 1. 로그인 목업의 두 계정(1001·1002)과 같은 사람이다.
//   바꾼 것은 메모리에만 남아 새로고침하면 처음으로 돌아간다. 새로 만든 계정은 승인된 상태이고, 초대는 운영자 목록에는 더하지 않고 대기 중인 초대 목록에 쌓는다(받은 사람이 링크에서 수락하면 운영자가 생긴다고 본다).
//   대기 중인 초대 3건(유효 2 · 기한 지남 1): 유효기간은 보낸 뒤 72시간(가정, `UtilOperator.INVITE_VALID_HOURS`). 같은 이메일로 유효한 초대가 이미 있으면 409 INVITATION_DUPLICATED(기한이 지난 초대는 새 초대가 대신한다), 없는 초대를 취소하면 404 INVITATION_NOT_FOUND.
//   신청 반려: 승인 대기 건만 반려할 수 있고(아니면 409 OPERATOR_NOT_PENDING), 사유는 선택(최대 200자)이다. 반려한 신청은 목록에서 사라진다(보관 방식은 미정 — 같은 이메일로 다시 신청할 수 있다고 본다).
//   서버 규칙 흉내: 이름 필수·20자(400 VALIDATION + `fields.operatorName`), 이메일 형식·중복(409 OPERATOR_EMAIL_DUPLICATED), 기간 거꾸로(400 OPERATOR_PERIOD_REVERSED),
//   루트 계정 변경(409 OPERATOR_ROOT_PROTECTED), 없는 역할(400 OPERATOR_ROLE_NOT_FOUND), 새로 주는 역할이 내 권한 밖(403 PERMISSION_ESCALATION)·종합 관리 역할은 최고 관리자만(403 ROLE_GENERAL_SUPER_ONLY),
//   필요한 권한이 없으면 403 PERMISSION_DENIED(잠금·해제 `operator.lock`, 그 밖의 변경 `operator.manage`, 역할 변경 `role.grant`) — "나"는 로그인 목업(`admin-auth-mock.ts`)의 계정이다.

import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import { mockCurrentAdmin } from '~/composables/auth/api/mock/admin-auth-mock';
import { mockAssignedRoleIds, mockRoleBrief, mockRolePermissions, mockRoleTypeIdOf, mockSetAssignedRoleIds } from '~/composables/role/api/mock/role-mock';
import type { OperatorCreateRequest, OperatorInvitationView, OperatorInviteRequest, OperatorPage, OperatorRejectRequest, OperatorStatusRequest, OperatorTab, OperatorUpdateRequest, OperatorView } from '~/composables/operator/api/useOperatorApi.types';
import UtilPermissionCatalog from '~/utils/util-permission-catalog';
import UtilOperator from '~/utils/util-operator';
import UtilRole from '~/utils/util-role';

const DAY = 86_400_000;

/** 역할을 뺀 운영자 원본 — 역할은 역할 목업의 할당표에서 붙인다 */
type StoredOperator = Omit<OperatorView, 'roles' | 'periodOver'>;

const now = Date.now();
const ago = (days: number) => new Date(now - days * DAY).toISOString();

const OPERATORS: StoredOperator[] = [
    { operatorId: '1001', email: 'admin@example.com', operatorName: '김관리', isSuper: true, accountStartAt: null, accountEndAt: null, approved: true, locked: false, expired: false, enabled: true, createdAt: '2026-01-02T00:30:00Z' },
    { operatorId: '1002', email: 'staff@example.com', operatorName: '박운영', isSuper: false, accountStartAt: null, accountEndAt: null, approved: true, locked: false, expired: false, enabled: true, createdAt: '2026-03-14T01:20:00Z' },
    { operatorId: '1003', email: 'batch@example.com', operatorName: '이배치', isSuper: false, accountStartAt: ago(30), accountEndAt: new Date(now + 60 * DAY).toISOString(), approved: true, locked: false, expired: false, enabled: true, createdAt: ago(40) },
    { operatorId: '1004', email: 'new1@example.com', operatorName: '정대기', isSuper: false, accountStartAt: null, accountEndAt: null, approved: false, locked: false, expired: false, enabled: true, createdAt: ago(1) },
    { operatorId: '1005', email: 'new2@example.com', operatorName: '한신규', isSuper: false, accountStartAt: null, accountEndAt: null, approved: false, locked: false, expired: false, enabled: true, createdAt: ago(2) },
    { operatorId: '1006', email: 'locked@staff.example.com', operatorName: '윤잠금', isSuper: false, accountStartAt: null, accountEndAt: null, approved: true, locked: true, expired: false, enabled: true, createdAt: ago(90) },
    { operatorId: '1007', email: 'expired@example.com', operatorName: '서만료', isSuper: false, accountStartAt: ago(120), accountEndAt: ago(10), approved: true, locked: false, expired: true, enabled: true, createdAt: ago(130) },
    { operatorId: '1008', email: 'off@example.com', operatorName: '오중지', isSuper: false, accountStartAt: null, accountEndAt: null, approved: true, locked: false, expired: false, enabled: false, createdAt: ago(200) },
];

let nextId = 2000;

const HOUR = 3_600_000;

/** 대기 중인 초대 원본 — 역할은 아이디만 들고 응답을 만들 때 이름을 붙인다 */
interface StoredInvitation {
    invitationId: string;
    email: string;
    roleIds: string[];
    invitedAt: string;
    expiresAt: string;
}

const hoursAgo = (hours: number) => new Date(now - hours * HOUR).toISOString();
const expiryOf = (invitedAt: string) => new Date(Date.parse(invitedAt) + UtilOperator.INVITE_VALID_HOURS * HOUR).toISOString();
const seedInvitation = (invitationId: string, email: string, roleIds: string[], hours: number): StoredInvitation => ({ invitationId, email, roleIds, invitedAt: hoursAgo(hours), expiresAt: expiryOf(hoursAgo(hours)) });

const INVITATIONS: StoredInvitation[] = [
    seedInvitation('3001', 'invite1@example.com', ['ROLE_OPERATOR_VIEWER'], 5),
    seedInvitation('3002', 'invite2@example.com', ['ROLE_AUDIT_VIEWER', 'ROLE_BATCH_OPERATOR'], 70),
    seedInvitation('3003', 'invite3@example.com', [], 100),
];
let nextInvitationId = 3100;

function periodOver(o: StoredOperator): boolean {
    const t = Date.now();
    return (o.accountStartAt !== null && Date.parse(o.accountStartAt) > t) || (o.accountEndAt !== null && Date.parse(o.accountEndAt) < t);
}

function toView(o: StoredOperator): OperatorView {
    const roles = o.isSuper ? [] : mockAssignedRoleIds(o.operatorId).flatMap((id) => mockRoleBrief(id) ?? []);
    return { ...o, roles, periodOver: periodOver(o) };
}

function invitationView(i: StoredInvitation): OperatorInvitationView {
    return { invitationId: i.invitationId, email: i.email, roles: i.roleIds.flatMap((id) => mockRoleBrief(id) ?? []), invitedAt: i.invitedAt, expiresAt: i.expiresAt, expired: Date.parse(i.expiresAt) < Date.now() };
}

function find(operatorId: string): StoredOperator {
    const operator = OPERATORS.find((o) => o.operatorId === operatorId);
    if (!operator) throw new ApiError({ status: 404, code: 'OPERATOR_NOT_FOUND', message: '없는 운영자예요.' });
    return operator;
}

function inTab(o: StoredOperator, tab: OperatorTab): boolean {
    const blocked = o.approved && (o.locked || o.expired || !o.enabled || periodOver(o));
    if (tab === 'PENDING') return !o.approved;
    if (tab === 'BLOCKED') return blocked;
    return o.approved && !blocked;
}

function actorCan(code: string): boolean {
    const actor = mockCurrentAdmin();
    return actor !== null && (actor.isSuper || actor.permissions.includes(code));
}

function requirePermission(code: string): void {
    if (!actorCan(code)) throw new ApiError({ status: 403, code: 'PERMISSION_DENIED', message: '이 작업을 할 권한이 없어요.' });
}

function rootProtected(): ApiError {
    return new ApiError({ status: 409, code: 'OPERATOR_ROOT_PROTECTED', message: '최고 관리자 계정은 역할·상태·기간을 바꿀 수 없어요.' });
}

/** 새로 주는 역할이 있는지, 내 권한 안인지 본다. 이미 가진 역할(`keep`)은 그대로 둘 수 있다 */
function checkRoles(roleIds: string[], keep: readonly string[]): void {
    for (const id of roleIds) {
        if (!mockRoleBrief(id)) throw new ApiError({ status: 400, code: 'OPERATOR_ROLE_NOT_FOUND', message: `없는 역할이에요: ${id}` });
    }
    const added = roleIds.filter((id) => !keep.includes(id));
    if (added.length) requirePermission('role.grant');
    for (const id of added) {
        if (mockRoleTypeIdOf(id) === UtilRole.TYPE_GENERAL && !mockCurrentAdmin()?.isSuper) {
            throw new ApiError({ status: 403, code: 'ROLE_GENERAL_SUPER_ONLY', message: '종합 관리 역할은 최고 관리자만 줄 수 있어요.' });
        }
        if (mockRolePermissions(id).some((code) => !actorCan(code))) {
            throw new ApiError({ status: 403, code: 'PERMISSION_ESCALATION', message: '내가 가진 권한 밖의 권한이 든 역할은 줄 수 없어요.' });
        }
    }
}

function validateName(name: string): void {
    const trimmed = name.trim();
    if (!trimmed) throw new ApiError({ status: 400, code: 'VALIDATION', message: '입력한 내용을 다시 확인해 주세요.', fields: { operatorName: '이름을 입력해 주세요.' } });
    if (trimmed.length > 20) throw new ApiError({ status: 400, code: 'VALIDATION', message: '입력한 내용을 다시 확인해 주세요.', fields: { operatorName: '이름은 20자까지예요.' } });
}

function validateEmail(email: string): string {
    const value = email.trim().toLowerCase();
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) throw new ApiError({ status: 400, code: 'VALIDATION', message: '입력한 내용을 다시 확인해 주세요.', fields: { email: '이메일 형식이 올바르지 않아요.' } });
    if (OPERATORS.some((o) => o.email === value)) throw new ApiError({ status: 409, code: 'OPERATOR_EMAIL_DUPLICATED', message: '이미 등록된 이메일이에요.', fields: { email: '이미 등록된 이메일이에요.' } });
    return value;
}

/** `GET /api/admin/operators` */
export async function mockOperatorList(tab: OperatorTab, keyword: string): Promise<OperatorPage> {
    await mockDelay();
    if (mockFlags().has('operator-error')) throw MOCK_ERRORS.internal();
    const all = mockFlags().has('operator-empty') ? [] : OPERATORS;
    const k = keyword.trim().toLowerCase();
    const items = all
        .filter((o) => inTab(o, tab))
        .filter((o) => !k || o.email.toLowerCase().includes(k) || o.operatorName.toLowerCase().includes(k))
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
        .map(toView);
    return {
        items,
        total: items.length,
        counts: { active: all.filter((o) => inTab(o, 'ACTIVE')).length, pending: all.filter((o) => inTab(o, 'PENDING')).length, blocked: all.filter((o) => inTab(o, 'BLOCKED')).length },
    };
}

/** `POST /api/admin/operators` — 승인된 상태로 만든다 */
export async function mockOperatorCreate(body: OperatorCreateRequest): Promise<OperatorView> {
    await mockDelay();
    if (mockFlags().has('operator-save-error')) throw MOCK_ERRORS.internal();
    requirePermission('operator.manage');
    validateName(body.operatorName);
    const email = validateEmail(body.email);
    checkRoles(body.roleIds, []);

    const operator: StoredOperator = { operatorId: String(nextId++), email, operatorName: body.operatorName.trim(), isSuper: false, accountStartAt: null, accountEndAt: null, approved: true, locked: false, expired: false, enabled: true, createdAt: new Date().toISOString() };
    OPERATORS.push(operator);
    mockSetAssignedRoleIds(operator.operatorId, body.roleIds);
    return toView(operator);
}

/** `POST /api/admin/operator-invitations` — 메일을 보낸 것으로 친다(운영자 목록은 그대로, 대기 중인 초대 목록에 쌓는다) */
export async function mockOperatorInvite(body: OperatorInviteRequest): Promise<void> {
    await mockDelay();
    if (mockFlags().has('operator-save-error')) throw MOCK_ERRORS.internal();
    requirePermission('operator.manage');
    const email = validateEmail(body.email);
    checkRoles(body.roleIds, []);
    // 유효한 초대가 이미 있으면 막는다 — 기한이 지난 초대는 새 초대가 대신한다
    const existing = INVITATIONS.find((i) => i.email === email);
    if (existing && Date.parse(existing.expiresAt) >= Date.now()) {
        throw new ApiError({ status: 409, code: 'INVITATION_DUPLICATED', message: '이미 초대한 이메일이에요. 대기 중인 초대를 취소한 뒤 다시 보내 주세요.', fields: { email: '이미 초대한 이메일이에요.' } });
    }
    if (existing) INVITATIONS.splice(INVITATIONS.indexOf(existing), 1);
    const invitedAt = new Date().toISOString();
    INVITATIONS.push({ invitationId: String(nextInvitationId++), email, roleIds: [...body.roleIds], invitedAt, expiresAt: expiryOf(invitedAt) });
}

/** `GET /api/admin/operator-invitations` — 아직 수락하지 않은 초대(기한이 지난 것 포함), 최근 초대가 앞 */
export async function mockOperatorInvitations(): Promise<OperatorInvitationView[]> {
    await mockDelay();
    if (mockFlags().has('operator-error')) throw MOCK_ERRORS.internal();
    if (mockFlags().has('operator-empty')) return [];
    return [...INVITATIONS].sort((a, b) => b.invitedAt.localeCompare(a.invitedAt)).map(invitationView);
}

/** `DELETE /api/admin/operator-invitations/{invitationId}` — 초대 링크를 못 쓰게 한다 */
export async function mockOperatorCancelInvitation(invitationId: string): Promise<void> {
    await mockDelay();
    if (mockFlags().has('operator-save-error')) throw MOCK_ERRORS.internal();
    requirePermission('operator.manage');
    const invitation = INVITATIONS.find((i) => i.invitationId === invitationId);
    if (!invitation) throw new ApiError({ status: 404, code: 'INVITATION_NOT_FOUND', message: '이미 없어진 초대예요. 목록을 새로 고쳐 주세요.' });
    INVITATIONS.splice(INVITATIONS.indexOf(invitation), 1);
}

/** `PUT /api/admin/operators/{operatorId}` */
export async function mockOperatorUpdate(operatorId: string, body: OperatorUpdateRequest): Promise<OperatorView> {
    await mockDelay();
    if (mockFlags().has('operator-save-error')) throw MOCK_ERRORS.internal();
    requirePermission('operator.manage');
    const operator = find(operatorId);
    if (operator.isSuper) throw rootProtected();
    validateName(body.operatorName);
    if (body.accountStartAt && body.accountEndAt && Date.parse(body.accountStartAt) > Date.parse(body.accountEndAt)) {
        throw new ApiError({ status: 400, code: 'OPERATOR_PERIOD_REVERSED', message: '시작이 끝보다 뒤예요. 그러면 아무 때도 쓸 수 없는 계정이 돼요.' });
    }
    if (body.roleIds) checkRoles(body.roleIds, mockAssignedRoleIds(operatorId));

    operator.operatorName = body.operatorName.trim();
    operator.accountStartAt = body.accountStartAt;
    operator.accountEndAt = body.accountEndAt;
    if (body.roleIds) mockSetAssignedRoleIds(operatorId, body.roleIds);
    return toView(operator);
}

/** `PUT /api/admin/operators/{operatorId}/status` */
export async function mockOperatorChangeStatus(operatorId: string, body: OperatorStatusRequest): Promise<OperatorView> {
    await mockDelay();
    if (mockFlags().has('operator-save-error')) throw MOCK_ERRORS.internal();
    const operator = find(operatorId);
    if (operator.isSuper) throw rootProtected();
    if (body.locked !== undefined && body.locked !== operator.locked) requirePermission('operator.lock');
    if (body.approved !== undefined || body.expired !== undefined || body.enabled !== undefined) requirePermission('operator.manage');

    const next = { approved: body.approved ?? operator.approved, locked: body.locked ?? operator.locked, enabled: body.enabled ?? operator.enabled };
    const wasUsable = operator.approved && !operator.locked && operator.enabled;
    const willBeUsable = next.approved && !next.locked && next.enabled;
    if (mockFlags().has('operator-last-super') && wasUsable && !willBeUsable) {
        throw new ApiError({ status: 409, code: 'OPERATOR_LAST_SUPER', message: '마지막 활성 최고 관리자예요. 막으면 아무도 제품을 관리할 수 없어요.' });
    }

    operator.approved = next.approved;
    operator.locked = next.locked;
    operator.enabled = next.enabled;
    operator.expired = body.expired ?? operator.expired;
    return toView(operator);
}

/** `POST /api/admin/operators/{operatorId}/rejection` — 승인 대기(신청) 건을 반려한다. 사유는 선택이고, 반려한 신청은 목록에서 사라진다 */
export async function mockOperatorReject(operatorId: string, body: OperatorRejectRequest): Promise<void> {
    await mockDelay();
    if (mockFlags().has('operator-save-error')) throw MOCK_ERRORS.internal();
    requirePermission('operator.manage');
    const operator = find(operatorId);
    if (operator.isSuper) throw rootProtected();
    if (operator.approved) throw new ApiError({ status: 409, code: 'OPERATOR_NOT_PENDING', message: '이미 승인된 계정이라 반려할 수 없어요. 목록을 새로 고쳐 주세요.' });
    if (body.reason.length > UtilOperator.REJECT_REASON_MAX) {
        throw new ApiError({ status: 400, code: 'VALIDATION', message: '입력한 내용을 다시 확인해 주세요.', fields: { reason: `사유는 ${UtilOperator.REJECT_REASON_MAX}자까지예요.` } });
    }
    OPERATORS.splice(OPERATORS.indexOf(operator), 1);
    mockSetAssignedRoleIds(operatorId, []);
}
