// MOCK: 포인트 API(`GET /api/admin/points/ledger`, `GET /api/admin/points/member-candidates`, `POST /api/admin/members/{id}/points`, `GET·PUT /api/admin/settings/points`)의 메모리 목업 — `usePointApi.ts` 가 이 파일의 함수를 부른다.
// 서버가 붙으면 `point/api/mock/` 과 함께 지운다. 응답 모양·오류 코드는 `usePointApi.ts` 머리말(후보 계약)을 따른다.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` (쉼표로 여러 개) ─
//   point-error            원장 조회가 `500 INTERNAL` 로 실패(표 자리의 오류 상태와 다시 시도)
//   point-empty            원장이 비어 있다(빈 상태)
//   point-adjust-error     조정이 `500 INTERNAL` 로 실패(대화상자 안 오류 문구)
//   point-settings-error   정책 조회가 `500 INTERNAL` 로 실패(오류 상태와 다시 시도)
//   point-settings-reject  정책 저장이 `400 VALIDATION` + `fields.signupPoints`·`fields.nicknameTicketPrice` 로 거절(서버 문구가 칸 아래에)
//
// ── 흉내 ─
//   회원 6명의 최근 90일 원장(출석·글·댓글·추천 보너스·인기글·핫딜 종료 요청·회수·교환권·운영자 조정·가입)을 고정 씨앗으로 만든다 — 새로고침해도 같은 줄이 나온다.
//   서버 규칙 흉내: 잔액은 0 아래로 내려가지 않는다(회수·차감은 0P 까지, 반영 0 이면 줄을 만들지 않는다), 조정 delta 0 아닌 정수 −10000~10000 · 사유 1~100자(400 VALIDATION + `fields.delta|reason`),
//   정책 값마다 범위(400 VALIDATION + `fields.<칸>`), 없는 회원 404 `MEMBER_NOT_FOUND`, 권한이 없으면 403 `PERMISSION_DENIED`(조회 `point.read`, 정책 변경 `point.settings`, 조정 `point.adjust`).
//   운영자 조정의 처리자는 "나"(목업 이름 김관리)다. 바꾼 것은 메모리에만 남아 새로고침하면 처음으로 돌아간다.

import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type {
    PointAdjustRequest,
    PointAdjustResponse,
    PointLedgerItem,
    PointLedgerKind,
    PointLedgerPage,
    PointLedgerQuery,
    PointMemberCandidate,
    PointReasonCode,
    PointSettingsResponse,
    PointSettingsValues,
} from '~/composables/point/api/usePointApi.types';
import UtilPoint from '~/utils/util-point';

type Can = (code: string) => boolean;

/** 서버가 한국어로 주는 사유 이름 — 화면 번역 키로 옮기지 않는다(서버 몫, `i18n.md` 5장) */
const REASON_LABEL: Record<PointReasonCode, string> = {
    ATTENDANCE: '출석체크',
    POST: '글 작성',
    COMMENT: '댓글 작성',
    POPULAR: '인기글 보상',
    POST_REVOKE: '글 삭제로 회수',
    COMMENT_REVOKE: '댓글 삭제로 회수',
    TICKET_PURCHASE: '닉네임 변경권 교환',
    ADMIN_ADJUST: '운영 조정',
    DEAL_END_REQUEST: '핫딜 종료 요청 보상',
    LIKE_BONUS: '추천 보너스',
    LIKE_BONUS_REVOKE: '삭제로 회수 · 추천 보너스',
    SIGNUP: '회원가입',
};

const ME = '김관리';

interface StoredMember {
    memberId: string;
    nickname: string;
    email: string | null;
    balance: number;
    /** 가입한 지 며칠 — 90 보다 작으면 그날 가입 포인트 줄이 생긴다 */
    joinedDaysAgo: number;
    /** 하루 활동량(0~1) */
    activity: number;
}

const MEMBERS: StoredMember[] = [
    { memberId: '7311000000000010021', nickname: '고양이집사', email: 'cat***@gmail.com', balance: 0, joinedDaysAgo: 400, activity: 0.9 },
    { memberId: '7311000000000010034', nickname: '새벽코딩', email: 'dawn***@naver.com', balance: 0, joinedDaysAgo: 220, activity: 0.7 },
    { memberId: '7311000000000010057', nickname: '라면왕', email: 'ramen***@kakao.com', balance: 0, joinedDaysAgo: 150, activity: 0.5 },
    { memberId: '7311000000000010078', nickname: '봄날의곰', email: 'bear***@gmail.com', balance: 0, joinedDaysAgo: 60, activity: 0.6 },
    { memberId: '7311000000000010102', nickname: '핫딜헌터', email: 'deal***@daum.net', balance: 0, joinedDaysAgo: 35, activity: 0.8 },
    { memberId: '7311000000000010119', nickname: '조용한독자', email: null, balance: 0, joinedDaysAgo: 12, activity: 0.2 },
];

/** 최신이 위 */
let ledger: PointLedgerItem[] | null = null;
let nextEntry = 900000;

let settings: PointSettingsResponse = { ...UtilPoint.DEFAULTS, updatedAt: '2026-10-06T01:20:00Z', updatedBy: '김관리' };

/** 고정 씨앗 난수(mulberry32) — 새로고침해도 같은 원장 */
function seeded(seed: number): () => number {
    let a = seed;
    return () => {
        a |= 0;
        a = (a + 0x6d2b79f5) | 0;
        let r = Math.imul(a ^ (a >>> 15), 1 | a);
        r = (r + Math.imul(r ^ (r >>> 7), 61 | r)) ^ r;
        return ((r ^ (r >>> 14)) >>> 0) / 4294967296;
    };
}

/** 한 줄을 반영한다 — 차감·회수는 0P 까지, 반영 0 이면 줄을 만들지 않는다 */
function apply(rows: PointLedgerItem[], member: StoredMember, reasonCode: PointReasonCode, want: number, at: Date, note: string | null = null, actor: string | null = null): PointLedgerItem | null {
    const applied = want >= 0 ? want : -Math.min(-want, member.balance);
    if (applied === 0) return null;
    member.balance += applied;
    const kind: PointLedgerKind = reasonCode === 'TICKET_PURCHASE' ? 'USE' : applied > 0 ? 'EARN' : 'REVOKE';
    const row: PointLedgerItem = {
        entryId: String(nextEntry++),
        memberId: member.memberId,
        memberNickname: member.nickname,
        kind,
        reasonCode,
        reason: REASON_LABEL[reasonCode],
        note,
        delta: applied,
        balanceAfter: member.balance,
        actorNickname: actor,
        createdAt: at.toISOString(),
    };
    rows.push(row);
    return row;
}

/** 최근 90일 원장을 만든다(오래된 것부터 쌓고 마지막에 뒤집는다) */
function store(): PointLedgerItem[] {
    if (ledger) return ledger;
    const rand = seeded(20261022);
    const rows: PointLedgerItem[] = [];
    const now = Date.now();
    const DAY = 86_400_000;
    const at = (daysAgo: number, hour: number) => new Date(now - daysAgo * DAY - (23 - hour) * 3_600_000 - Math.floor(rand() * 3_000_000));
    for (let day = 90; day >= 0; day--) {
        for (const member of MEMBERS) {
            if (day > member.joinedDaysAgo) continue;
            if (day === member.joinedDaysAgo) {
                // 당시 설정의 가입 포인트(지금 기본은 0 이라 지급 안 함 — 예전 운영 기록이 남은 모양)
                apply(rows, member, 'SIGNUP', 100, at(day, 1));
                continue;
            }
            if (rand() < member.activity * 0.8) apply(rows, member, 'ATTENDANCE', settings.attendancePoints, at(day, 2));
            const posts = Math.floor(rand() * 3 * member.activity);
            for (let i = 0; i < posts; i++) apply(rows, member, 'POST', settings.postPoints, at(day, 4 + i));
            const comments = Math.floor(rand() * 5 * member.activity);
            for (let i = 0; i < comments; i++) apply(rows, member, 'COMMENT', settings.commentPoints, at(day, 8 + i));
            if (rand() < 0.12 * member.activity) apply(rows, member, 'LIKE_BONUS', settings.likeBonusPoints, at(day, 14));
            if (rand() < 0.03 * member.activity) apply(rows, member, 'POPULAR', settings.popularRewardPoints, at(day, 0));
            if (member.nickname === '핫딜헌터' && rand() < 0.25) apply(rows, member, 'DEAL_END_REQUEST', settings.dealEndRequestPoints, at(day, 15));
            if (rand() < 0.04) apply(rows, member, 'POST_REVOKE', -settings.postPoints, at(day, 17));
            if (rand() < 0.05) apply(rows, member, 'COMMENT_REVOKE', -settings.commentPoints, at(day, 18));
            if (rand() < 0.015) apply(rows, member, 'LIKE_BONUS_REVOKE', -settings.likeBonusPoints, at(day, 18));
            if (member.balance >= 1500 && rand() < 0.04) apply(rows, member, 'TICKET_PURCHASE', -settings.nicknameTicketPrice, at(day, 20));
            if (rand() < 0.01) apply(rows, member, 'ADMIN_ADJUST', 500, at(day, 21), '이벤트 당첨 보상이에요', ME);
            if (rand() < 0.006) apply(rows, member, 'ADMIN_ADJUST', -300, at(day, 21), '중복 지급된 포인트를 회수해요', '박운영');
        }
    }
    // 잔액이 적은 회원에게 큰 차감 — 0P 까지만 반영된 줄(요청 −500, 실제는 덜 반영)
    const small = MEMBERS.find((m) => m.nickname === '조용한독자');
    if (small) apply(rows, small, 'ADMIN_ADJUST', -500, new Date(now - 3_600_000), '도배 글 정리로 회수해요', ME);
    rows.sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt));
    ledger = rows;
    return ledger;
}

const denied = (): ApiError => new ApiError({ status: 403, code: 'PERMISSION_DENIED', message: '이 작업을 할 권한이 없어요.' });
const validation = (fields: Record<string, string>): ApiError => new ApiError({ status: 400, code: 'VALIDATION', message: '입력값을 확인해 주세요.', fields });

function matches(member: { memberId: string; nickname: string; email: string | null }, q: string): boolean {
    const needle = q.trim().toLowerCase();
    if (!needle) return true;
    return member.nickname.toLowerCase().includes(needle) || (member.email ?? '').toLowerCase().includes(needle) || member.memberId.includes(needle);
}

export async function mockPointLedger(can: Can, query: PointLedgerQuery): Promise<PointLedgerPage> {
    await mockDelay();
    const flags = mockFlags();
    if (flags.has('point-error')) throw MOCK_ERRORS.internal();
    if (!can('point.read')) throw denied();
    const empty = { items: [], total: 0, summary: { earned: 0, spent: 0, adjustCount: 0 } };
    if (flags.has('point-empty')) return empty;
    const from = query.from ? Date.parse(query.from) : null;
    const to = query.to ? Date.parse(query.to) : null;
    if (from !== null && to !== null && from > to) throw validation({ from: '시작이 끝보다 늦어요.' });
    const reasons = query.reasons?.length ? new Set(query.reasons) : null;
    const memberIds = query.q ? new Set(MEMBERS.filter((m) => matches(m, query.q ?? '')).map((m) => m.memberId)) : null;
    const found = store().filter((row) => {
        const time = Date.parse(row.createdAt);
        if (from !== null && time < from) return false;
        if (to !== null && time > to) return false;
        if (query.kind && row.kind !== query.kind) return false;
        if (reasons && !reasons.has(row.reasonCode)) return false;
        if (memberIds && !memberIds.has(row.memberId)) return false;
        return true;
    });
    const summary = found.reduce(
        (sum, row) => ({
            earned: sum.earned + Math.max(0, row.delta),
            spent: sum.spent + Math.max(0, -row.delta),
            adjustCount: sum.adjustCount + (row.reasonCode === 'ADMIN_ADJUST' ? 1 : 0),
        }),
        { earned: 0, spent: 0, adjustCount: 0 },
    );
    const start = query.page * query.size;
    return { items: found.slice(start, start + query.size), total: found.length, summary };
}

export async function mockPointMemberCandidates(can: Can, q: string): Promise<PointMemberCandidate[]> {
    await mockDelay();
    if (!can('point.adjust')) throw denied();
    store();
    return MEMBERS.filter((m) => matches(m, q))
        .slice(0, 20)
        .map((m) => ({ memberId: m.memberId, nickname: m.nickname, email: m.email, balance: m.balance }));
}

export async function mockPointAdjust(can: Can, memberId: string, body: PointAdjustRequest): Promise<PointAdjustResponse> {
    await mockDelay();
    if (mockFlags().has('point-adjust-error')) throw MOCK_ERRORS.internal();
    if (!can('point.adjust')) throw denied();
    const fields: Record<string, string> = {};
    if (!Number.isInteger(body.delta) || body.delta === 0 || Math.abs(body.delta) > UtilPoint.AMOUNT_MAX) fields.delta = '조정 포인트는 0 이 아닌 −10,000 ~ 10,000 사이 정수예요.';
    const reasonProblem = UtilPoint.reasonProblem(body.reason);
    if (reasonProblem) fields.reason = reasonProblem === 'REQUIRED' ? '사유를 입력해 주세요.' : '사유는 100자까지 쓸 수 있어요.';
    if (Object.keys(fields).length) throw validation(fields);
    const rows = store();
    const member = MEMBERS.find((m) => m.memberId === memberId);
    if (!member) throw new ApiError({ status: 404, code: 'MEMBER_NOT_FOUND', message: '회원을 찾을 수 없어요.' });
    const added: PointLedgerItem[] = [];
    // 감사 기록 `POINT_ADJUST`(대상 MEMBER + 회원 ID, 반영된 값 · 사유)는 서버가 남긴다. 차감이 반영되면 회원에게 알림(`POINTS_REVOKED`)도 서버가 보낸다
    const row = apply(added, member, 'ADMIN_ADJUST', body.delta, new Date(), UtilPoint.reasonText(body.reason), ME);
    if (row) rows.unshift(row);
    return { applied: row?.delta ?? 0, balance: member.balance };
}

export async function mockPointSettings(can: Can): Promise<PointSettingsResponse> {
    await mockDelay();
    if (mockFlags().has('point-settings-error')) throw MOCK_ERRORS.internal();
    if (!can('point.read')) throw denied();
    return { ...settings };
}

export async function mockPointSettingsSave(can: Can, body: PointSettingsValues): Promise<PointSettingsResponse> {
    await mockDelay();
    if (!can('point.settings')) throw denied();
    const fields: Record<string, string> = {};
    for (const field of UtilPoint.FIELDS) {
        const value = body[field];
        const [min, max] = UtilPoint.limitsOf(field);
        if (!Number.isInteger(value) || value < min || value > max) fields[field] = `${min.toLocaleString('ko-KR')} ~ ${max.toLocaleString('ko-KR')} 사이 정수로 입력해 주세요.`;
    }
    if (mockFlags().has('point-settings-reject')) {
        fields.signupPoints = '가입 포인트는 출석 포인트의 100배를 넘을 수 없어요.';
        fields.nicknameTicketPrice = '닉네임 변경권 가격을 확인해 주세요.';
    }
    if (Object.keys(fields).length) throw validation(fields);
    settings = { ...body, updatedAt: new Date().toISOString(), updatedBy: ME };
    return { ...settings };
}
