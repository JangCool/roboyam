// MOCK: 공구 리뷰 관리자 API(`/api/admin/group-buy-reviews…`)의 메모리 목업 — `useGroupBuyReviewAdminApi.ts` 가 부른다.
// 서버가 붙으면 이 파일을 지운다. 새로고침하면 처음 상태로 돌아간다.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` ─
//   review-empty  리뷰 없음     review-fail  목록 읽기 실패(500)     detail-fail  상세 읽기 실패(500)
//   reply-fail    답글 저장 실패(500)        hide-fail    숨김·숨김 풀기 실패(500)
//
// ── 목업 규칙(가정 — 계약 「리뷰 규칙」) ─
//   · 답글을 고치면 이전 내용은 남기지 않는다(답글 수정 시각·사람만) — 이력은 작성자 리뷰 수정만(사용자 결정 대기 가정).
//   · 숨기면 열린 신고는 처리된 것으로 비운다. 숨김은 사유 코드로만 — 별점은 사유가 될 수 없다.

import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { GroupBuyQnaOperator } from '~/composables/groupbuy/api/useGroupBuyQnaAdminApi.types';
import type { GroupBuyReviewDetail, GroupBuyReviewHideRequest, GroupBuyReviewListResponse, GroupBuyReviewQuery, GroupBuyReviewRow } from '~/composables/groupbuy/api/useGroupBuyReviewAdminApi.types';

const DAY = 24 * 60 * 60 * 1000;
const ME: GroupBuyQnaOperator = { id: '1', name: '운영자' };
const OTHER: GroupBuyQnaOperator = { id: '2', name: '김매니저' };
const PHOTO = (seed: string) => `https://picsum.photos/seed/${seed}/400/400`;
const RICE = { id: '31', name: '2026 햅쌀 신동진' };
const R1 = { id: '11', title: '햅쌀 1차 공동구매', roundNo: 1 };
const R2 = { id: '12', title: '햅쌀 2차 공동구매', roundNo: 2 };

let store: GroupBuyReviewDetail[] | null = null;

function iso(agoMs: number): string {
    return new Date(Date.now() - agoMs).toISOString();
}

function make(over: Partial<GroupBuyReviewDetail> & Pick<GroupBuyReviewDetail, 'id' | 'rating' | 'body' | 'createdAt'>): GroupBuyReviewDetail {
    return refresh({
        product: RICE,
        groupBuy: R2,
        orderId: `9${over.id}`,
        orderNo: `GB-2610${over.id}`,
        skuName: '20kg',
        member: { id: `20${over.id}`, nickname: '회원', withdrawn: false },
        preview: '',
        photoCount: 0,
        hidden: false,
        deleted: false,
        reportCount: 0,
        hasReply: false,
        updatedAt: null,
        photos: [],
        deletedAt: null,
        revisions: [],
        answer: null,
        hiddenInfo: null,
        reports: [],
        ...over,
    });
}

function refresh(d: GroupBuyReviewDetail): GroupBuyReviewDetail {
    d.preview = d.body.length > 60 ? `${d.body.slice(0, 60)}…` : d.body;
    d.photoCount = d.photos.length;
    d.reportCount = d.reports.reduce((n, r) => n + r.count, 0);
    d.hasReply = !!d.answer;
    return d;
}

function seed(): GroupBuyReviewDetail[] {
    if (mockFlags().has('review-empty')) return [];
    return [
        make({ id: '601', rating: 5, member: { id: '2601', nickname: '밥심', withdrawn: false }, body: '작년 1차 때보다 더 찰져요. 도정일이 받기 이틀 전이라 밥맛이 확실히 달라요.', photos: [{ id: 'p1', url: PHOTO('rice1') }, { id: 'p2', url: PHOTO('rice2') }], createdAt: iso(2 * DAY), answer: { body: '두 번째 구매 감사해요!', answeredAt: iso(1 * DAY), answeredBy: OTHER, updatedAt: null, updatedBy: null } }),
        make({ id: '602', rating: 4, skuName: '10kg', member: { id: '2602', nickname: '도담', withdrawn: false }, body: '맛은 좋은데 포대 입구가 살짝 터져서 왔어요. 쌀은 쏟아지지 않았어요.', createdAt: iso(3 * DAY) }),
        make({ id: '603', rating: 2, member: { id: '2603', nickname: '여행가', withdrawn: false }, body: '배송이 예정일보다 사흘 늦었어요. 안내가 없어서 아쉬웠어요.', createdAt: iso(4 * DAY), updatedAt: iso(3 * DAY), revisions: [{ id: 'rv1', rating: 1, body: '배송 늦음. 최악.', photoCount: 0, writtenAt: iso(4 * DAY), replacedAt: iso(3 * DAY) }] }),
        make({ id: '607', rating: 3, member: { id: '2607', nickname: '알뜰이', withdrawn: false }, body: '여기보다 ○○마켓 쿠폰 쓰면 더 싸요. 링크 남겨요 www.example.com', createdAt: iso(1 * DAY), reports: [{ reason: 'SPAM_AD', count: 2 }] }),
        make({ id: '608', rating: 5, member: { id: '2608', nickname: '나무늘보', withdrawn: false }, body: '잘 받았어요! 문의는 010-9876-5432 로 주세요.', createdAt: iso(5 * 60 * 60 * 1000), reports: [{ reason: 'RIGHTS', count: 1 }] }),
        make({ id: '606', rating: 1, groupBuy: R1, member: { id: '2606', nickname: '꼼꼼이', withdrawn: false }, body: '벌레가 나왔어요. 반품 요청했고 환불은 받았어요.', photos: [{ id: 'p4', url: PHOTO('bug1') }], createdAt: iso(220 * DAY), answer: { body: '불편을 드려 죄송해요. 해당 차수 보관 창고를 바꿨어요.', answeredAt: iso(219 * DAY), answeredBy: ME, updatedAt: null, updatedBy: null } }),
        make({ id: '605', rating: 3, groupBuy: R1, skuName: '10kg', member: { id: null, nickname: '탈퇴한 회원', withdrawn: true }, body: '보통이에요. 마트 쌀과 큰 차이는 모르겠어요.', createdAt: iso(210 * DAY) }),
        make({ id: '599', rating: 1, groupBuy: R1, member: { id: '2599', nickname: '화난손님', withdrawn: false }, body: '욕설이 섞인 리뷰(숨김 예시)', createdAt: iso(230 * DAY), hidden: true, hiddenInfo: { reasonCode: 'ABUSE', memo: '판매자·다른 회원 비하 표현', hiddenAt: iso(229 * DAY), hiddenBy: OTHER } }),
        make({ id: '598', rating: 4, groupBuy: R1, member: { id: '2598', nickname: '기다림', withdrawn: false }, body: '작성자가 지운 리뷰 예시예요.', createdAt: iso(240 * DAY), deleted: true, deletedAt: iso(235 * DAY) }),
    ];
}


function all(): GroupBuyReviewDetail[] {
    if (!store) store = seed();
    return store;
}

function find(id: string): GroupBuyReviewDetail {
    const found = all().find((d) => d.id === id);
    if (!found) throw new ApiError({ status: 404, code: 'GROUP_BUY_REVIEW_NOT_FOUND', message: '리뷰를 찾을 수 없어요.' });
    return refresh(found);
}

function row(d: GroupBuyReviewDetail): GroupBuyReviewRow {
    const { body: _b, photos: _p, deletedAt: _d, revisions: _r, answer: _a, hiddenInfo: _h, reports: _rp, ...rest } = d;
    return mockClone(rest);
}

function inTab(d: GroupBuyReviewDetail, tab: GroupBuyReviewQuery['tab']): boolean {
    if (tab === 'ALL') return true;
    if (d.deleted) return false;
    if (tab === 'HIDDEN') return d.hidden;
    if (tab === 'REPORTED') return !d.hidden && d.reportCount > 0;
    return !d.hidden;
}

function failIf(flag: string): void {
    if (mockFlags().has(flag)) throw MOCK_ERRORS.internal();
}

export async function mockReviewAdminList(query: GroupBuyReviewQuery): Promise<GroupBuyReviewListResponse> {
    await mockDelay();
    failIf('review-fail');
    const list = all().map(refresh);
    const q = query.q?.trim().toLowerCase() ?? '';
    const matched = list
        .filter((d) => inTab(d, query.tab ?? 'VISIBLE'))
        .filter((d) => !query.rating || d.rating === query.rating)
        .filter((d) => !query.photo || d.photoCount > 0)
        .filter((d) => !query.noReply || !d.hasReply)
        .filter((d) => !q || [d.product.name, d.groupBuy.title, d.member.nickname, d.member.id ?? '', d.orderNo].some((v) => v.toLowerCase().includes(q)))
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
    const live = list.filter((d) => !d.deleted);
    return {
        items: matched.slice(query.page * query.size, (query.page + 1) * query.size).map(row),
        total: matched.length,
        counts: { VISIBLE: live.filter((d) => !d.hidden).length, REPORTED: live.filter((d) => !d.hidden && d.reportCount > 0).length, HIDDEN: live.filter((d) => d.hidden).length },
    };
}

export async function mockReviewAdminDetail(id: string): Promise<GroupBuyReviewDetail> {
    await mockDelay();
    failIf('detail-fail');
    return mockClone(find(id));
}

export async function mockReviewReply(id: string, body: string): Promise<GroupBuyReviewDetail> {
    await mockDelay();
    failIf('reply-fail');
    const d = find(id);
    const text = body.trim();
    if (!text || text.length > 2000) throw MOCK_ERRORS.validation({ body: '답글은 1~2,000자로 써 주세요.' });
    if (d.deleted) throw new ApiError({ status: 409, code: 'GROUP_BUY_REVIEW_DELETED', message: '작성자가 지운 리뷰에는 답글을 달 수 없어요.' });
    const now = new Date().toISOString();
    d.answer = d.answer ? { ...d.answer, body: text, updatedAt: now, updatedBy: ME } : { body: text, answeredAt: now, answeredBy: ME, updatedAt: null, updatedBy: null };
    return mockClone(refresh(d));
}

export async function mockReviewHide(id: string, body: GroupBuyReviewHideRequest): Promise<GroupBuyReviewDetail> {
    await mockDelay();
    failIf('hide-fail');
    const memo = body.memo?.trim() || null;
    if (body.reasonCode === 'OTHER' && (!memo || memo.length < 5)) throw MOCK_ERRORS.validation({ memo: '기타는 메모를 5~200자로 적어 주세요.' });
    if (memo && memo.length > 200) throw MOCK_ERRORS.validation({ memo: '메모는 200자까지예요.' });
    const d = find(id);
    if (d.deleted) throw new ApiError({ status: 409, code: 'GROUP_BUY_REVIEW_DELETED', message: '작성자가 지운 리뷰예요.' });
    d.hidden = true;
    d.hiddenInfo = { reasonCode: body.reasonCode, memo, hiddenAt: new Date().toISOString(), hiddenBy: ME };
    d.reports = [];
    return mockClone(refresh(d));
}

export async function mockReviewUnhide(id: string, reason: string): Promise<GroupBuyReviewDetail> {
    await mockDelay();
    failIf('hide-fail');
    const n = reason.trim().length;
    if (n < 5 || n > 200) throw MOCK_ERRORS.validation({ reason: '사유는 5~200자로 적어 주세요.' });
    const d = find(id);
    d.hidden = false;
    d.hiddenInfo = null;
    return mockClone(refresh(d));
}
