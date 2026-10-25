// MOCK: 내 제재(`GET /api/me/sanctions`)·경고 확인(`POST /api/me/sanctions/{id}/acknowledge`)·이의제기(`POST /api/me/appeals`)의 메모리 목업 — `useSanctionApi.ts` 가 부른다.
// 서버가 붙으면 `sanction/api/mock/` 와 함께 지운다. 새로고침하면 처음 상태로 돌아간다.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` (처음 상태를 고른다. 없으면 로그인 정보 `restriction` 을 따른다) ─
//   sanction-warning    확인하지 않은 경고 1회        sanction-warning2   확인하지 않은 경고 2회째
//   sanction-suspended  정지 7일 진행 중               sanction-banned     영구 정지
//   sanction-history    제한 없음 + 지난 이력(무효가 된 경고·기각된 정지)
//   sanction-fail       읽기 실패(500)                 sanction-ack-fail   경고 확인 실패(500)
//   appeal-fail         이의제기 접수 실패(500)         appeal-already      접수하면 409 APPEAL_ALREADY
//
// ── 목업 규칙(가정 — 계약 「목업 가정」) ─
//   · 스위치가 없으면: 로그인 정보가 영구 정지면 영구 정지, 기간 정지면 그 끝(`until`)까지의 정지 7일, 확인하지 않은 경고 수(`pendingNotices`)만큼 경고, 그 밖에는 이력 없음.
//   · 경고 확인·이의제기는 이 목업 안에서만 바뀐다 — 로그인 정보(`restriction`)는 서버 값이라 그대로다(전역 배너·로그인 직후 안내가 그대로 보일 수 있다).
//   · 이의제기는 접수 즉시 「검토 중」이고 답변은 오지 않는다(답변 화면은 `sanction-history` 의 지난 이력으로 본다).

import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { Restriction } from '~/composables/auth/api/useAuthApi.types';
import type { AppealCreated, AppealRequest, MySanction, MySanctions } from '~/composables/sanction/api/useSanctionApi.types';

const DAY = 24 * 60 * 60 * 1000;
let store: MySanctions | null = null;
let appealNo = 40;

function iso(offsetMs: number): string {
    return new Date(Date.now() + offsetMs).toISOString();
}

function warning(id: string, no: number, warningNo: number, ago: number, acknowledged: boolean): MySanction {
    return {
        id,
        no,
        kind: 'WARNING',
        status: 'ACTIVE',
        reason: warningNo === 1 ? '욕설·비방 — 댓글에서 다른 회원을 비하했어요.' : '도배 — 같은 글을 여러 게시판에 반복해서 올렸어요.',
        warningNo,
        startsAt: iso(-ago),
        endsAt: null,
        createdAt: iso(-ago),
        acknowledged,
        appeal: null,
    };
}

function initial(restriction: Restriction | null): MySanctions {
    const flags = mockFlags();
    if (flags.has('sanction-banned') || (!hasStateFlag(flags) && restriction?.status === 'BANNED')) {
        const ban: MySanction = { id: '9003', no: 31, kind: 'BAN', status: 'ACTIVE', reason: '불법 촬영물 공유 — 단계를 건너뛰어 영구 정지했어요.', warningNo: null, startsAt: iso(-2 * DAY), endsAt: null, createdAt: iso(-2 * DAY), acknowledged: true, appeal: null };
        return { status: 'BANNED', warningCount: 2, current: ban, items: [ban, warning('9002', 22, 2, 20 * DAY, true), warning('9001', 12, 1, 40 * DAY, true)] };
    }
    if (flags.has('sanction-suspended') || (!hasStateFlag(flags) && restriction?.status === 'SUSPENDED')) {
        const until = restriction?.status === 'SUSPENDED' && restriction.until ? restriction.until : iso(5 * DAY);
        const start = new Date(Date.parse(until) - 7 * DAY).toISOString();
        const suspension: MySanction = { id: '9004', no: 33, kind: 'SUSPEND_7', status: 'ACTIVE', reason: '광고 — 경고 뒤에도 홍보 글을 계속 올렸어요.', warningNo: null, startsAt: start, endsAt: until, createdAt: start, acknowledged: true, appeal: null };
        return { status: 'SUSPENDED', warningCount: 2, current: suspension, items: [suspension, warning('9002', 22, 2, 15 * DAY, true), warning('9001', 12, 1, 30 * DAY, true)] };
    }
    if (flags.has('sanction-warning2')) {
        return { status: 'ACTIVE', warningCount: 2, current: null, items: [warning('9002', 22, 2, 1 * 60 * 60 * 1000, false), warning('9001', 12, 1, 20 * DAY, true)] };
    }
    const pending = flags.has('sanction-warning') ? 1 : hasStateFlag(flags) ? 0 : Math.min(2, restriction?.pendingNotices ?? 0);
    if (pending > 0) {
        const items = pending === 2 ? [warning('9002', 22, 2, 60 * 60 * 1000, false), warning('9001', 12, 1, 3 * DAY, false)] : [warning('9001', 12, 1, 60 * 60 * 1000, false)];
        return { status: 'ACTIVE', warningCount: pending, current: null, items };
    }
    if (flags.has('sanction-history')) {
        const voided: MySanction = {
            ...warning('9001', 12, 1, 60 * DAY, true),
            status: 'VOIDED',
            appeal: { id: '701', no: 7, status: 'ACCEPTED', reason: '맥락상 농담이었고 상대도 웃으며 넘어갔어요. 다시 확인해 주세요.', answer: '대화 흐름을 다시 보니 비방 의도가 없었어요. 경고를 무효로 했어요.', createdAt: iso(-59 * DAY), answeredAt: iso(-58 * DAY) },
        };
        const ended: MySanction = {
            id: '9005',
            no: 18,
            kind: 'SUSPEND_3',
            status: 'ENDED',
            reason: '도배 — 같은 댓글을 짧은 시간에 여러 번 남겼어요.',
            warningNo: null,
            startsAt: iso(-30 * DAY),
            endsAt: iso(-27 * DAY),
            createdAt: iso(-30 * DAY),
            acknowledged: true,
            appeal: { id: '702', no: 9, status: 'REJECTED', reason: '네트워크 오류로 같은 댓글이 여러 번 올라간 것 같아요.', answer: '같은 댓글이 몇 분 간격으로 손으로 올라간 기록이 있어 제재를 유지해요.', createdAt: iso(-29 * DAY), answeredAt: iso(-28 * DAY) },
        };
        const pendingAppeal: MySanction = {
            ...warning('9006', 25, 1, 10 * DAY, true),
            appeal: { id: '703', no: 11, status: 'PENDING', reason: '제 글이 아닌데 신고 대상으로 잘못 연결된 것 같아요.', answer: null, createdAt: iso(-9 * DAY), answeredAt: null },
        };
        return { status: 'ACTIVE', warningCount: 1, current: null, items: [pendingAppeal, ended, voided] };
    }
    return { status: 'ACTIVE', warningCount: 0, current: null, items: [] };
}

/** 처음 상태를 고르는 스위치가 있는가 */
function hasStateFlag(flags: Set<string>): boolean {
    return ['sanction-warning', 'sanction-warning2', 'sanction-suspended', 'sanction-banned', 'sanction-history'].some((f) => flags.has(f));
}

function ensure(restriction: Restriction | null): MySanctions {
    if (!store) store = initial(restriction);
    return store;
}

export async function mockMySanctions(restriction: Restriction | null): Promise<MySanctions> {
    await mockDelay();
    if (mockFlags().has('sanction-fail')) throw MOCK_ERRORS.internal();
    return mockClone(ensure(restriction));
}

export async function mockAcknowledge(restriction: Restriction | null, id: string): Promise<void> {
    await mockDelay();
    if (mockFlags().has('sanction-ack-fail')) throw MOCK_ERRORS.internal();
    const target = ensure(restriction).items.find((s) => s.id === id);
    if (!target) throw new ApiError({ status: 404, code: 'SANCTION_NOT_FOUND', message: '없는 제재예요.' });
    target.acknowledged = true;
}

export async function mockAppeal(restriction: Restriction | null, request: AppealRequest): Promise<AppealCreated> {
    await mockDelay();
    const flags = mockFlags();
    if (flags.has('appeal-fail')) throw MOCK_ERRORS.internal();
    const target = ensure(restriction).items.find((s) => s.id === request.sanctionId);
    if (!target) throw new ApiError({ status: 404, code: 'SANCTION_NOT_FOUND', message: '없는 제재예요.' });
    if (target.status === 'VOIDED') throw new ApiError({ status: 409, code: 'SANCTION_NOT_APPEALABLE', message: '이미 무효가 된 제재에는 이의제기를 할 수 없어요.' });
    if (target.appeal || flags.has('appeal-already')) throw new ApiError({ status: 409, code: 'APPEAL_ALREADY', message: '이미 이의제기를 접수했어요. 한 제재에는 한 번만 접수할 수 있어요.' });
    const length = Array.from(request.reason.trim()).length;
    if (length < 10 || length > 500) throw MOCK_ERRORS.validation({ reason: '이의 사유는 10자 이상 500자 이하로 적어 주세요.' });
    appealNo += 1;
    const created: AppealCreated = { id: String(800 + appealNo), no: appealNo, status: 'PENDING' };
    target.appeal = { id: created.id, no: created.no, status: 'PENDING', reason: request.reason.trim(), answer: null, createdAt: new Date().toISOString(), answeredAt: null };
    return mockClone(created);
}
