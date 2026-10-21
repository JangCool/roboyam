// MOCK: 감사 로그 API(`GET /api/admin/audit-logs`·`/options`)의 메모리 목업 — `useAuditApi.ts` 가 이 파일의 함수를 부른다.
// 서버가 붙으면 `audit/api/mock/` 와 함께 지운다.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` (쉼표로 여러 개) ─
//   audit-error          목록을 읽는 요청이 `500 INTERNAL` 로 실패(오류 상태와 다시 시도)
//   audit-empty          기록이 하나도 없는 상태(거른 결과가 없을 때의 빈 상태는 거르기로 확인한다)
//   audit-options-error  거르기 고르개 목록 요청이 `500 INTERNAL` 로 실패(고르개는 비어 있어도 목록은 보인다)
//
// ── 흉내 ─
//   최근 2주 동안의 기록 약 220건(발생 시각 역순). 작업·대상 종류·결과가 섞여 있고, 바뀐 내용이 있는 줄(펼치면 변경 내용)과 보기만 한 줄(펼치면 "조회 기록입니다")이 있다.
//   기록한 사람은 최고 관리자·운영진·시스템(배치)·탈퇴한 사람(이름·메일 없음)이다. 새로고침해도 같은 기록이 보인다(난수 고정).
//   거르기는 서버처럼 시각 범위·사용자(아이디·이메일·이름의 일부)·작업·대상 종류·대상(아이디·이름의 일부)·결과를 모두 적용한다.

import { MOCK_ERRORS, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { AuditChange, AuditLogView, AuditOptions, AuditPage, AuditQuery } from '~/composables/audit/api/useAuditApi.types';

const MINUTE = 60_000;

/** 서버가 라벨을 주는 작업 — 코드 · 이름 · 대상 종류 */
const ACTIONS = [
    { value: 'LOGIN', label: '로그인', targetType: null },
    { value: 'LOGIN_FAIL', label: '로그인 실패', targetType: null },
    { value: 'LOGOUT', label: '로그아웃', targetType: null },
    { value: 'PROFILE_UPDATE', label: '내 정보 수정', targetType: 'ADMIN' },
    { value: 'DISPLAY_UPDATE', label: '표기 설정 수정', targetType: 'ADMIN' },
    { value: 'MEMBER_STATUS_CHANGE', label: '회원 상태 변경', targetType: 'MEMBER' },
    { value: 'BATCH_TOGGLE', label: '배치 켜기·끄기', targetType: 'BATCH' },
    { value: 'BATCH_RUN', label: '배치 지금 실행', targetType: 'BATCH' },
    { value: 'AUDIT_VIEW', label: '감사 로그 조회', targetType: null },
] as const;

const TARGET_TYPES = [
    { value: 'ADMIN', label: '관리자' },
    { value: 'MEMBER', label: '회원' },
    { value: 'BATCH', label: '배치' },
];

interface Actor {
    id: string;
    email: string | null;
    name: string | null;
    ip: string | null;
}

const ACTORS: Actor[] = [
    { id: '1001', email: 'admin@example.com', name: '김관리', ip: '203.0.113.24' },
    { id: '1002', email: 'staff@example.com', name: '박운영', ip: '198.51.100.7' },
    { id: 'SYSTEM', email: null, name: '시스템', ip: null },
    // 탈퇴한 사람 — 이름·메일이 없어 화면이 아이디로 대신한다
    { id: '1099', email: null, name: null, ip: '192.0.2.88' },
];

const MEMBERS = [
    { id: '2041', name: '이하늘' },
    { id: '2057', name: '최민준' },
    { id: '2063', name: '정서윤' },
];

const BATCHES = [
    { id: 'audit-retention', name: '감사 로그 정리' },
    { id: 'account-expiry', name: '계정 기간 만료' },
    { id: 'verification-token-cleanup', name: '이메일 인증 토큰 정리' },
];

/** 같은 입력에 같은 값을 주는 작은 난수 */
function noise(seed: number): number {
    const x = Math.sin(seed * 78.233) * 43758.5453;
    return x - Math.floor(x);
}

function pick<T>(list: readonly T[], seed: number): T {
    return list[Math.floor(noise(seed) * list.length) % list.length]!;
}

function changesJson(changes: AuditChange[]): string {
    return JSON.stringify(changes);
}

/** 기록 한 건을 만든다 — 작업에 따라 대상·바뀐 내용·결과가 정해진다 */
function makeRow(auditNo: number, occurredMs: number): AuditLogView {
    const seed = auditNo;
    const action = pick(ACTIONS, seed * 3 + 1);
    // 시스템이 하는 일은 배치 쪽뿐이고, 사람이 하는 일은 사람이 한다
    const actor = action.value === 'BATCH_RUN' && noise(seed + 7) < 0.4 ? ACTORS[2]! : pick(ACTORS.filter((a) => a.id !== 'SYSTEM'), seed * 5 + 2);
    let result: 'OK' | 'FAIL' = 'OK';
    let message: string | null = null;
    let targetId: string | null = null;
    let targetName: string | null = null;
    let changes: string | null = null;

    switch (action.value) {
        case 'LOGIN_FAIL':
            result = 'FAIL';
            message = '이메일 또는 비밀번호가 맞지 않아요.';
            break;
        case 'PROFILE_UPDATE':
            targetId = actor.id;
            targetName = actor.name;
            changes = changesJson([{ field: 'nickname', before: actor.name === '김관리' ? '관리자' : '운영자', after: actor.name }]);
            break;
        case 'DISPLAY_UPDATE':
            targetId = actor.id;
            targetName = actor.name;
            changes = changesJson([
                { field: 'timeFormat', before: 'H24', after: 'H12' },
                { field: 'dateFormat', before: 'YMD_DOT', after: 'YMD_DASH' },
            ]);
            break;
        case 'MEMBER_STATUS_CHANGE': {
            const member = pick(MEMBERS, seed + 11);
            targetId = member.id;
            targetName = member.name;
            changes = changesJson([{ field: 'status', before: 'ACTIVE', after: 'SUSPENDED' }]);
            if (noise(seed + 13) < 0.2) {
                result = 'FAIL';
                message = '이미 탈퇴한 회원이에요.';
                changes = null;
            }
            break;
        }
        case 'BATCH_TOGGLE': {
            const batch = pick(BATCHES, seed + 17);
            targetId = batch.id;
            targetName = batch.name;
            const on = noise(seed + 19) < 0.5;
            changes = changesJson([{ field: 'enabled', before: on ? 'false' : 'true', after: on ? 'true' : 'false' }]);
            break;
        }
        case 'BATCH_RUN': {
            const batch = pick(BATCHES, seed + 23);
            targetId = batch.id;
            targetName = batch.name;
            if (noise(seed + 29) < 0.15) {
                result = 'FAIL';
                message = '데이터베이스 연결 시간이 초과됐습니다.';
            }
            break;
        }
        default:
            break;
    }

    const targetType = action.targetType;
    return {
        auditNo,
        occurredAt: new Date(occurredMs).toISOString(),
        actorId: actor.id,
        actorEmail: actor.email,
        actorName: actor.name,
        actorIp: actor.ip,
        action: action.value,
        actionName: action.label,
        targetType,
        targetTypeName: targetType ? (TARGET_TYPES.find((t) => t.value === targetType)?.label ?? null) : null,
        targetId,
        targetName,
        result,
        message,
        changes,
    };
}

/** 최근 2주, 불규칙한 간격으로 쌓은 기록 — 발생 시각 역순 */
function buildRows(now: number): AuditLogView[] {
    const out: AuditLogView[] = [];
    let at = now - 90_000;
    let no = 220;
    while (no > 0) {
        out.push(makeRow(no, at));
        at -= Math.round((8 + noise(no * 7) * 150) * MINUTE);
        no--;
    }
    return out;
}

const ROWS: AuditLogView[] = buildRows(Date.now());

/** `GET /api/admin/audit-logs/options` */
export async function mockAuditOptions(): Promise<AuditOptions> {
    await mockDelay();
    if (mockFlags().has('audit-options-error')) throw MOCK_ERRORS.internal();
    return { actions: ACTIONS.map(({ value, label }) => ({ value, label })), targetTypes: TARGET_TYPES };
}

function includes(text: string | null, term: string): boolean {
    return !!text && text.toLowerCase().includes(term);
}

/** `GET /api/admin/audit-logs` */
export async function mockAuditSearch(query: AuditQuery): Promise<AuditPage> {
    await mockDelay();
    if (mockFlags().has('audit-error')) throw MOCK_ERRORS.internal();
    const all = mockFlags().has('audit-empty') ? [] : ROWS;
    const from = query.from ? Date.parse(query.from) : null;
    const to = query.to ? Date.parse(query.to) : null;
    const actor = query.actor?.trim().toLowerCase();
    const target = query.target?.trim().toLowerCase();

    const filtered = all.filter((row) => {
        const at = Date.parse(row.occurredAt);
        if (from !== null && at < from) return false;
        if (to !== null && at > to) return false;
        if (actor && !(includes(row.actorId, actor) || includes(row.actorEmail, actor) || includes(row.actorName, actor))) return false;
        if (query.action && row.action !== query.action) return false;
        if (query.targetType && row.targetType !== query.targetType) return false;
        if (target && !(includes(row.targetId, target) || includes(row.targetName, target))) return false;
        if (query.result && row.result !== query.result) return false;
        return true;
    });
    const size = query.size && query.size > 0 ? query.size : 20;
    const page = query.page && query.page > 0 ? query.page : 0;
    return { items: filtered.slice(page * size, page * size + size), total: filtered.length, page, size };
}
