// MOCK: 공구 Q&A 관리자 API(`/api/admin/group-buy-qna…`)의 메모리 목업 — `useGroupBuyQnaAdminApi.ts` 가 부른다.
// 서버가 붙으면 이 파일을 지운다. 새로고침하면 처음 상태로 돌아간다.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` ─
//   qna-empty     질문 없음                qna-fail      목록 읽기 실패(500)
//   qna-forbidden 목록 403                 detail-fail   상세 읽기 실패(500)
//   answer-fail   답변 저장 실패(500)       hide-fail     숨김·숨김 풀기 실패(500)
//
// ── 목업 규칙(가정 — 계약 「Q&A 규칙」·결정 60) ─
//   · 답변 기한 2영업일 — 목업은 주말을 빼지 않고 하루 = 1영업일로 센다.
//   · 답변을 고치면 이전 내용이 이력 맨 위에 쌓인다. 숨기면 열린 신고는 처리된 것으로 비운다(신고 처리 = 숨김과 같은 길).
//   · 작성자가 지운 질문은 「전체」 탭에만 보이고 답변·숨김을 할 수 없다.

import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { GroupBuyAnswerRequest, GroupBuyQnaDetail, GroupBuyQnaListResponse, GroupBuyQnaOperator, GroupBuyQnaQuery, GroupBuyQnaRow } from '~/composables/groupbuy/api/useGroupBuyQnaAdminApi.types';

const HOUR = 60 * 60 * 1000;
const DAY = 24 * HOUR;
const ME: GroupBuyQnaOperator = { id: '1', name: '운영자' };
const OTHER: GroupBuyQnaOperator = { id: '2', name: '김매니저' };

let store: GroupBuyQnaDetail[] | null = null;

function iso(agoMs: number): string {
    return new Date(Date.now() - agoMs).toISOString();
}

const RICE = { id: '12', title: '햅쌀 10kg·20kg 산지 공동구매', roundNo: 2 };
const APPLE = { id: '15', title: '부사 사과 5kg', roundNo: 1 };

function make(over: Partial<GroupBuyQnaDetail> & Pick<GroupBuyQnaDetail, 'id' | 'body' | 'createdAt'>): GroupBuyQnaDetail {
    const base: GroupBuyQnaDetail = {
        groupBuy: RICE,
        member: { id: '1001', nickname: '쌀사랑', withdrawn: false },
        preview: '',
        secret: false,
        forcedSecret: false,
        status: 'OPEN',
        hidden: false,
        deleted: false,
        reportCount: 0,
        answeredAt: null,
        elapsedBusinessDays: 0,
        overdue: false,
        updatedAt: null,
        deletedAt: null,
        retainUntil: '',
        answer: null,
        revisions: [],
        hiddenInfo: null,
        reports: [],
        ...over,
    };
    return refresh(base);
}

/** 파생 값 — 미리보기 · 경과 영업일 · 기한 · 신고 수 · 보관 만료일 */
function refresh(d: GroupBuyQnaDetail): GroupBuyQnaDetail {
    d.preview = d.body.length > 60 ? `${d.body.slice(0, 60)}…` : d.body;
    d.elapsedBusinessDays = d.status === 'OPEN' ? Math.floor((Date.now() - new Date(d.createdAt).getTime()) / DAY) : 0;
    d.overdue = d.status === 'OPEN' && !d.hidden && !d.deleted && d.elapsedBusinessDays >= 2;
    d.reportCount = d.reports.reduce((n, r) => n + r.count, 0);
    const from = new Date(d.answer?.updatedAt ?? d.answeredAt ?? d.createdAt);
    from.setFullYear(from.getFullYear() + 3);
    d.retainUntil = from.toISOString().slice(0, 10);
    return d;
}

function seed(): GroupBuyQnaDetail[] {
    if (mockFlags().has('qna-empty')) return [];
    return [
        make({ id: '906', body: '제주도도 배송비가 같나요?', member: { id: '1006', nickname: '여행가', withdrawn: false }, createdAt: iso(3 * HOUR) }),
        make({ id: '907', body: '받는 사람을 바꾸려면 어떻게 하나요? 제 번호는 010-1234-5678 이에요.', member: { id: '1007', nickname: '나무늘보', withdrawn: false }, secret: true, forcedSecret: true, createdAt: iso(1 * HOUR) }),
        make({ id: '910', groupBuy: APPLE, body: '사과 크기가 어느 정도인가요? 선물용으로 쓰려고요.', member: { id: '1010', nickname: '사과농부', withdrawn: false }, createdAt: iso(3 * DAY) }),
        make({ id: '911', groupBuy: APPLE, body: '박스에 흠집이 있으면 교환되나요?', member: { id: '1011', nickname: '꼼꼼이', withdrawn: false }, createdAt: iso(2 * DAY + 2 * HOUR) }),
        make({
            id: '905',
            body: '여기보다 ○○마켓이 더 싸요. 거기서 사세요 www.example.com',
            member: { id: '1005', nickname: '장사꾼', withdrawn: false },
            createdAt: iso(8 * HOUR),
            reports: [{ reason: 'SPAM_AD', count: 3 }, { reason: 'OTHER', count: 1 }],
        }),
        make({
            id: '902',
            body: '도정일이 언제인가요? 받는 날 기준 2주 안이면 좋겠어요.',
            member: { id: '1002', nickname: '도담', withdrawn: false },
            createdAt: iso(30 * HOUR),
            status: 'ANSWERED',
            answeredAt: iso(26 * HOUR),
            answer: { body: '주문 마감 다음 날 도정해서 바로 보내 드려요. 도정일은 포대 겉면에 찍혀 있어요.', answeredAt: iso(26 * HOUR), answeredBy: OTHER, updatedAt: iso(20 * HOUR), updatedBy: ME },
            revisions: [{ id: 'r2', body: '마감 다음 날 도정해요.', writtenAt: iso(26 * HOUR), writtenBy: OTHER, replacedAt: iso(20 * HOUR), replacedBy: ME }],
        }),
        make({
            id: '903',
            body: '저희 집이 3층인데 계단으로 올려 주시나요?',
            member: { id: '1003', nickname: '쌀알', withdrawn: false },
            secret: true,
            createdAt: iso(20 * HOUR),
            status: 'ANSWERED',
            answeredAt: iso(18 * HOUR),
            answer: { body: '택배 기사님이 문 앞까지 올려 드려요.', answeredAt: iso(18 * HOUR), answeredBy: ME, updatedAt: null, updatedBy: null },
        }),
        make({
            id: '904',
            body: '현미도 같이 하나요?',
            member: { id: null, nickname: '탈퇴한 회원', withdrawn: true },
            createdAt: iso(12 * HOUR),
            status: 'ANSWERED',
            answeredAt: iso(10 * HOUR),
            answer: { body: '이번 공구는 백미만 해요.', answeredAt: iso(10 * HOUR), answeredBy: ME, updatedAt: null, updatedBy: null },
        }),
        make({
            id: '899',
            body: '욕설이 섞인 문의(숨김 예시)',
            member: { id: '1099', nickname: '화난손님', withdrawn: false },
            createdAt: iso(4 * DAY),
            hidden: true,
            hiddenInfo: { reason: '욕설·비방 — 다른 회원을 비하함', hiddenAt: iso(3 * DAY), hiddenBy: OTHER },
        }),
        make({ id: '898', body: '혹시 다음 주에 다시 하나요?', member: { id: '1098', nickname: '기다림', withdrawn: false }, createdAt: iso(5 * DAY), deleted: true, deletedAt: iso(4 * DAY) }),
    ];
}

function all(): GroupBuyQnaDetail[] {
    if (!store) store = seed();
    return store;
}

function find(id: string): GroupBuyQnaDetail {
    const found = all().find((d) => d.id === id);
    if (!found) throw new ApiError({ status: 404, code: 'GROUP_BUY_QUESTION_NOT_FOUND', message: '문의를 찾을 수 없어요.' });
    return refresh(found);
}

function row(d: GroupBuyQnaDetail): GroupBuyQnaRow {
    const { body: _b, updatedAt: _u, deletedAt: _dl, retainUntil: _r, answer: _a, revisions: _rv, hiddenInfo: _h, reports: _rp, ...rest } = d;
    return mockClone(rest);
}

function inTab(d: GroupBuyQnaDetail, tab: GroupBuyQnaQuery['tab']): boolean {
    if (tab === 'ALL') return true;
    if (d.deleted) return false;
    if (tab === 'HIDDEN') return d.hidden;
    return !d.hidden && d.status === (tab ?? 'OPEN');
}

function failIf(flag: string): void {
    if (mockFlags().has(flag)) throw MOCK_ERRORS.internal();
}

export async function mockQnaAdminList(query: GroupBuyQnaQuery): Promise<GroupBuyQnaListResponse> {
    await mockDelay();
    failIf('qna-fail');
    if (mockFlags().has('qna-forbidden')) throw new ApiError({ status: 403, code: 'FORBIDDEN', message: '이 화면을 볼 권한이 없어요.' });
    const list = all().map(refresh);
    const q = query.q?.trim().toLowerCase() ?? '';
    const matched = list
        .filter((d) => inTab(d, query.tab))
        .filter((d) => !query.overdue || d.overdue)
        .filter((d) => !q || d.groupBuy.title.toLowerCase().includes(q) || d.groupBuy.id === q || d.member.nickname.toLowerCase().includes(q) || (d.member.id !== null && d.member.id === q))
        .sort((a, b) => ((query.tab ?? 'OPEN') === 'OPEN' ? a.createdAt.localeCompare(b.createdAt) : b.createdAt.localeCompare(a.createdAt)));
    const live = list.filter((d) => !d.deleted);
    return {
        items: matched.slice(query.page * query.size, (query.page + 1) * query.size).map(row),
        total: matched.length,
        counts: { OPEN: live.filter((d) => !d.hidden && d.status === 'OPEN').length, ANSWERED: live.filter((d) => !d.hidden && d.status === 'ANSWERED').length, HIDDEN: live.filter((d) => d.hidden).length },
        overdueCount: list.filter((d) => d.overdue).length,
    };
}

export async function mockQnaAdminDetail(id: string): Promise<GroupBuyQnaDetail> {
    await mockDelay();
    failIf('detail-fail');
    return mockClone(find(id));
}

export async function mockQnaAnswer(id: string, body: GroupBuyAnswerRequest): Promise<GroupBuyQnaDetail> {
    await mockDelay();
    failIf('answer-fail');
    const d = find(id);
    const text = body.body.trim();
    if (!text) throw MOCK_ERRORS.validation({ body: '답변을 입력해 주세요.' });
    if (text.length > 2000) throw MOCK_ERRORS.validation({ body: '답변은 2,000자까지 쓸 수 있어요.' });
    if (d.deleted) throw new ApiError({ status: 409, code: 'GROUP_BUY_QUESTION_DELETED', message: '작성자가 지운 문의에는 답변할 수 없어요.' });
    const now = new Date().toISOString();
    if (d.answer) {
        if (d.answer.body === text) return mockClone(d);
        d.revisions.unshift({ id: `r${Date.now()}`, body: d.answer.body, writtenAt: d.answer.updatedAt ?? d.answer.answeredAt, writtenBy: d.answer.updatedBy ?? d.answer.answeredBy, replacedAt: now, replacedBy: ME });
        d.answer = { ...d.answer, body: text, updatedAt: now, updatedBy: ME };
    } else {
        d.answer = { body: text, answeredAt: now, answeredBy: ME, updatedAt: null, updatedBy: null };
        d.answeredAt = now;
        d.status = 'ANSWERED';
    }
    return mockClone(refresh(d));
}

function checkReason(reason: string): void {
    const n = reason.trim().length;
    if (n < 5 || n > 200) throw MOCK_ERRORS.validation({ reason: '사유는 5~200자로 적어 주세요.' });
}

export async function mockQnaHide(id: string, reason: string): Promise<GroupBuyQnaDetail> {
    await mockDelay();
    failIf('hide-fail');
    checkReason(reason);
    const d = find(id);
    if (d.deleted) throw new ApiError({ status: 409, code: 'GROUP_BUY_QUESTION_DELETED', message: '작성자가 지운 문의예요.' });
    d.hidden = true;
    d.hiddenInfo = { reason: reason.trim(), hiddenAt: new Date().toISOString(), hiddenBy: ME };
    d.reports = [];
    return mockClone(refresh(d));
}

export async function mockQnaUnhide(id: string, reason: string): Promise<GroupBuyQnaDetail> {
    await mockDelay();
    failIf('hide-fail');
    checkReason(reason);
    const d = find(id);
    d.hidden = false;
    d.hiddenInfo = null;
    return mockClone(refresh(d));
}
