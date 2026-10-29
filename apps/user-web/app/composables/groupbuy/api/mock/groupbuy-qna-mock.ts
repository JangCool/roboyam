// MOCK: 공구 Q&A(`/api/group-buys/{id}/questions` · `/api/group-buy-questions/{qid}`)와 질문 신고의 메모리 목업 — `useGroupBuyQnaApi.ts` 가 부른다.
// 서버가 붙으면 이 파일을 지운다. 새로고침하면 처음 상태로 돌아간다.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` ─
//   qna-empty        질문 없음                     qna-fail         목록 읽기 실패(500)
//   qna-restricted   이용 제한(정지) — 쓰기 막힘     qna-closed       중단된 공구 — 쓰기 막힘
//   qna-prev         「이전 차수 문의 보기」 링크     ask-fail         쓰기·고치기 실패(500)
//   ask-too-fast     쓰기 429(속도 제한)            report-already   신고하면 409 ALREADY_REPORTED
//
// ── 목업 규칙(가정 — 계약 「Q&A 규칙」) ─
//   · 한 쪽 10개, 새 글이 위. 로그인했으면 「내 문의」 하나(답변 대기)가 처음부터 있다.
//   · 본문에 전화번호·이메일·주소 모양이 보이면 비밀글로 바꿔 저장하고 `forcedSecret` 을 싣는다(서버 규칙과 같은 모양 — 판정은 서버 몫).
//   · 답변 전에는 고치기·지우기, 답변 뒤에는 공개 → 비밀글 전환과 지우기만. 지운 질문은 목록에서 빠진다(서버는 보관 기간 동안 감춘 채 남긴다).
//   · 운영자 답변은 이 목업에서 오지 않는다(답변 모양은 처음 데이터로 본다).

import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { GroupBuyQnaPage, GroupBuyQuestion, GroupBuyQuestionReport, GroupBuyQuestionRequest } from '~/composables/groupbuy/api/useGroupBuyQnaApi.types';

const PAGE_SIZE = 10;
const HOUR = 60 * 60 * 1000;
const PHONE = /01[016789][-.\s]?\d{3,4}[-.\s]?\d{4}/;
const EMAIL = /[\w.+-]+@[\w-]+\.[\w.]+/;
const ADDRESS = /\S+(시|도|구|군)\s+\S+(로|길|동)\s*\d+/;

interface Stored extends GroupBuyQuestion {
    groupBuyId: string;
    deleted: boolean;
}

const stores = new Map<string, Stored[]>();
let seq = 500;

function iso(agoMs: number): string {
    return new Date(Date.now() - agoMs).toISOString();
}

function q(groupBuyId: string, over: Partial<Stored> & Pick<Stored, 'id' | 'body' | 'createdAt'>): Stored {
    return { groupBuyId, nickname: '쌀***', mine: false, secret: false, forcedSecret: false, status: 'OPEN', hidden: false, updatedAt: null, answer: null, deleted: false, actions: { edit: false, delete: false, makeSecret: false, report: false }, ...over };
}

function seed(groupBuyId: string, loggedIn: boolean): Stored[] {
    if (mockFlags().has('qna-empty')) return [];
    const list: Stored[] = [
        q(groupBuyId, { id: '901', nickname: '밥***', body: '20kg 는 포대 한 개로 오나요, 10kg 두 개로 오나요?', createdAt: iso(50 * HOUR), status: 'ANSWERED', answer: { body: '20kg 한 포대로 보내 드려요. 포대 안에 소분 봉투는 없어요.', answeredAt: iso(46 * HOUR), edited: false } }),
        q(groupBuyId, { id: '902', nickname: '도***', body: '도정일이 언제인가요? 받는 날 기준 2주 안이면 좋겠어요.', createdAt: iso(30 * HOUR), status: 'ANSWERED', answer: { body: '주문 마감 다음 날 도정해서 바로 보내 드려요. 도정일은 포대 겉면에 찍혀 있어요.', answeredAt: iso(26 * HOUR), edited: true } }),
        q(groupBuyId, { id: '903', nickname: '쌀***', body: null, secret: true, createdAt: iso(20 * HOUR), status: 'ANSWERED', answer: null }),
        q(groupBuyId, { id: '904', nickname: null, body: '현미도 같이 하나요?', createdAt: iso(12 * HOUR), status: 'ANSWERED', answer: { body: '이번 공구는 백미만 해요. 현미는 다음 차수에 검토할게요.', answeredAt: iso(10 * HOUR), edited: false } }),
        q(groupBuyId, { id: '905', nickname: '장***', body: null, hidden: true, createdAt: iso(8 * HOUR) }),
        q(groupBuyId, { id: '906', nickname: '여***', body: '제주도도 배송비가 같나요?', createdAt: iso(3 * HOUR) }),
    ];
    for (let i = 0; i < 9; i++) list.unshift(q(groupBuyId, { id: String(800 + i), nickname: '공***', body: `예전 문의 ${i + 1} — 박스 크기가 궁금해요.`, createdAt: iso((60 + i * 5) * HOUR), status: 'ANSWERED', answer: { body: '가로 40cm · 세로 60cm 정도예요.', answeredAt: iso((58 + i * 5) * HOUR), edited: false } }));
    if (loggedIn) list.push(q(groupBuyId, { id: '907', nickname: '나***', mine: true, body: '받는 사람을 바꾸려면 어떻게 하나요?', createdAt: iso(1 * HOUR) }));
    return list;
}

function storeOf(groupBuyId: string, loggedIn: boolean): Stored[] {
    let list = stores.get(groupBuyId);
    if (!list) {
        list = seed(groupBuyId, loggedIn);
        stores.set(groupBuyId, list);
    }
    return list;
}

function findStored(questionId: string): Stored {
    for (const list of stores.values()) {
        const found = list.find((s) => s.id === questionId && !s.deleted);
        if (found) return found;
    }
    throw new ApiError({ status: 404, code: 'GROUP_BUY_QUESTION_NOT_FOUND', message: '문의를 찾을 수 없어요.' });
}

/** 회원이 보는 모양 — 볼 수 없는 것은 비우고, 할 수 있는 일을 정한다 */
function view(s: Stored, loggedIn: boolean): GroupBuyQuestion {
    const own = s.mine && loggedIn;
    const visible = own || (!s.secret && !s.hidden);
    const answered = s.status === 'ANSWERED';
    const { groupBuyId: _g, deleted: _d, ...rest } = s;
    return mockClone({
        ...rest,
        mine: own,
        body: visible ? s.body : null,
        answer: visible ? s.answer : null,
        actions: {
            edit: own && !answered && !s.hidden,
            delete: own,
            makeSecret: own && answered && !s.secret,
            report: loggedIn && !own && visible,
        },
    });
}

function validate(body: GroupBuyQuestionRequest): string {
    const text = body.body.trim();
    if (!text) throw MOCK_ERRORS.validation({ body: '문의 내용을 입력해 주세요.' });
    if (text.length > 1000) throw MOCK_ERRORS.validation({ body: '문의는 1,000자까지 쓸 수 있어요.' });
    return text;
}

function personal(text: string): boolean {
    return PHONE.test(text) || EMAIL.test(text) || ADDRESS.test(text);
}

function failIf(flag: string): void {
    if (mockFlags().has(flag)) throw MOCK_ERRORS.internal();
}

export async function mockQnaList(groupBuyId: string, loggedIn: boolean, mine: boolean, page: number): Promise<GroupBuyQnaPage> {
    await mockDelay();
    failIf('qna-fail');
    const flags = mockFlags();
    const all = storeOf(groupBuyId, loggedIn)
        .filter((s) => !s.deleted && (!mine || (loggedIn && s.mine)))
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
    const items = all.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE).map((s) => view(s, loggedIn));
    const askBlock = !loggedIn ? 'LOGIN_REQUIRED' : flags.has('qna-restricted') ? 'RESTRICTED' : flags.has('qna-closed') ? 'CLOSED' : null;
    return {
        items,
        total: all.length,
        page,
        hasMore: (page + 1) * PAGE_SIZE < all.length,
        canAsk: askBlock === null,
        askBlock,
        // 목업은 앞 차수 ID 를 몰라 같은 공구로 잇는다
        previousRound: flags.has('qna-prev') ? { id: groupBuyId, roundNo: 1 } : null,
    };
}

export async function mockAsk(groupBuyId: string, body: GroupBuyQuestionRequest): Promise<GroupBuyQuestion> {
    await mockDelay();
    failIf('ask-fail');
    if (mockFlags().has('ask-too-fast')) throw new ApiError({ status: 429, code: 'GROUP_BUY_QUESTION_TOO_FAST', message: '문의를 너무 자주 쓰고 있어요. 잠시 후 다시 써 주세요.' });
    const text = validate(body);
    const forced = !body.secret && personal(text);
    const created = q(groupBuyId, { id: String(++seq), nickname: '나***', mine: true, body: text, secret: body.secret || forced, forcedSecret: forced, createdAt: new Date().toISOString() });
    storeOf(groupBuyId, true).push(created);
    return view(created, true);
}

export async function mockUpdate(questionId: string, body: GroupBuyQuestionRequest): Promise<GroupBuyQuestion> {
    await mockDelay();
    failIf('ask-fail');
    const s = findStored(questionId);
    if (s.status === 'ANSWERED') throw new ApiError({ status: 409, code: 'GROUP_BUY_QUESTION_ANSWERED', message: '답변이 달린 문의는 고칠 수 없어요.' });
    const text = validate(body);
    const forced = !body.secret && personal(text);
    Object.assign(s, { body: text, secret: body.secret || forced, forcedSecret: forced, updatedAt: new Date().toISOString() });
    return view(s, true);
}

export async function mockMakeSecret(questionId: string): Promise<GroupBuyQuestion> {
    await mockDelay();
    failIf('ask-fail');
    const s = findStored(questionId);
    s.secret = true;
    return view(s, true);
}

export async function mockRemove(questionId: string): Promise<void> {
    await mockDelay();
    failIf('ask-fail');
    findStored(questionId).deleted = true;
}

export async function mockReport(_questionId: string, _body: GroupBuyQuestionReport): Promise<void> {
    await mockDelay();
    if (mockFlags().has('report-already')) throw new ApiError({ status: 409, code: 'ALREADY_REPORTED', message: '이미 신고한 문의예요.' });
}
