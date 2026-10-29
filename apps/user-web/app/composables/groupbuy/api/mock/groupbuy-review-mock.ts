// MOCK: 공구 리뷰(`/api/group-buys/{id}/reviews` · `/api/me/orders/{orderNo}/reviews` · `/api/me/group-buy-reviews…`)의 메모리 목업 — `useGroupBuyReviewApi.ts` 가 부른다.
// 서버가 붙으면 이 파일을 지운다. 새로고침하면 처음 상태로 돌아간다.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` ─
//   review-empty   리뷰 없음                  review-fail     목록 읽기 실패(500)
//   review-save-fail 쓰기·고치기 실패(500)     review-mine-hidden  내 리뷰 하나가 숨김 처리됨
//   review-expired  내 주문 줄이 모두 기한 지남  report-already  신고하면 409 ALREADY_REPORTED
//
// ── 목업 규칙(가정 — 계약 「리뷰 규칙」) ─
//   · 공구 ID 와 관계없이 같은 상품 리뷰 묶음 하나를 보인다(1차·2차 구매가 섞여 있다). 한 쪽 10개.
//   · 내 주문 줄: 주문이 배송완료면 줄마다 쓸 수 있음(전부 취소·반품한 줄은 대상 아님), 이미 쓴 줄은 「썼음」. 기한은 배송완료일 + 90일.
//   · 작성 30일 안에만 고칠 수 있다. 사진은 브라우저 안 주소로만 보인다(올리지 않는다).

import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { MyGroupBuyOrderDetail } from '~/composables/groupbuy/api/useGroupBuyOrderApi.types';
import type {
    GroupBuyReview,
    GroupBuyReviewPage,
    GroupBuyReviewPhoto,
    GroupBuyReviewReport,
    GroupBuyReviewRequest,
    GroupBuyReviewSort,
    GroupBuyReviewStats,
    GroupBuyReviewTarget,
    MyGroupBuyReview,
    MyGroupBuyReviewPage,
} from '~/composables/groupbuy/api/useGroupBuyReviewApi.types';

const PAGE_SIZE = 10;
const DAY = 24 * 60 * 60 * 1000;
const PHOTO = (seed: string) => `https://picsum.photos/seed/${seed}/400/400`;

interface Stored extends MyGroupBuyReview {
    itemId: string | null;
    deleted: boolean;
}

let store: Stored[] | null = null;
let seq = 700;

function iso(agoMs: number): string {
    return new Date(Date.now() - agoMs).toISOString();
}

function r(over: Partial<Stored> & Pick<Stored, 'id' | 'rating' | 'body' | 'createdAt'>): Stored {
    return {
        nickname: '쌀***',
        mine: false,
        photos: [],
        skuName: '20kg',
        roundNo: 1,
        updatedAt: null,
        hidden: null,
        reply: null,
        actions: { edit: false, delete: false, report: false },
        groupBuy: { id: '0', title: '2026 햅쌀 신동진' },
        orderNo: '',
        editableUntil: new Date(new Date(over.createdAt).getTime() + 30 * DAY).toISOString(),
        itemId: null,
        deleted: false,
        ...over,
    };
}

function seed(): Stored[] {
    if (mockFlags().has('review-empty')) return [];
    const list: Stored[] = [
        r({ id: '601', rating: 5, nickname: '밥***', roundNo: 2, body: '작년 1차 때보다 더 찰져요. 도정일이 받기 이틀 전이라 밥맛이 확실히 달라요.', photos: [{ id: 'p1', url: PHOTO('rice1') }, { id: 'p2', url: PHOTO('rice2') }], createdAt: iso(2 * DAY), reply: { body: '두 번째 구매 감사해요! 다음 차수도 같은 농가에서 받아요.', repliedAt: iso(1 * DAY), edited: false } }),
        r({ id: '602', rating: 4, nickname: '도***', roundNo: 2, skuName: '10kg', body: '맛은 좋은데 포대 입구가 살짝 터져서 왔어요. 쌀은 쏟아지지 않았어요.', createdAt: iso(3 * DAY) }),
        r({ id: '603', rating: 2, nickname: '여***', roundNo: 2, body: '배송이 예정일보다 사흘 늦었어요. 쌀은 괜찮은데 안내가 없어서 아쉬웠어요.', createdAt: iso(4 * DAY), reply: { body: '발송이 늦어 죄송해요. 지연 안내 문자가 늦게 나간 것을 확인했고 고쳤어요.', repliedAt: iso(3 * DAY), edited: true } }),
        r({ id: '604', rating: 5, nickname: null, roundNo: 1, body: '1차 구매. 가족이 다 만족했어요. 다음에도 살게요.', photos: [{ id: 'p3', url: PHOTO('rice3') }], createdAt: iso(200 * DAY) }),
        r({ id: '605', rating: 3, nickname: '공***', roundNo: 1, skuName: '10kg', body: '보통이에요. 마트 쌀과 큰 차이는 모르겠어요.', createdAt: iso(210 * DAY) }),
        r({ id: '606', rating: 1, nickname: '장***', roundNo: 1, body: '벌레가 나왔어요. 반품 요청했고 환불은 받았어요.', photos: [{ id: 'p4', url: PHOTO('bug1') }], createdAt: iso(220 * DAY), reply: { body: '불편을 드려 죄송해요. 해당 차수 보관 창고를 바꿨어요.', repliedAt: iso(219 * DAY), edited: false } }),
    ];
    for (let i = 0; i < 8; i++) list.push(r({ id: String(620 + i), rating: (i % 3 === 0 ? 4 : 5) as 4 | 5, nickname: '단***', roundNo: 1, body: `1차 구매 후기 ${i + 1} — 밥이 맛있어요. 다음에도 살게요.`, createdAt: iso((230 + i * 3) * DAY) }));
    list.push(
        r({
            id: '690',
            rating: 4,
            nickname: '나***',
            mine: true,
            roundNo: 2,
            body: '포장이 꼼꼼했어요. 20kg 는 혼자 들기 무거우니 10kg 두 개를 추천해요.',
            createdAt: iso(5 * DAY),
            orderNo: 'GB-DEMO-0001',
            itemId: 'mock-item',
            hidden: mockFlags().has('review-mine-hidden') ? { reasonCode: 'PRIVACY', memo: '본문에 휴대전화 번호가 있어 숨겼어요.' } : null,
        }),
    );
    return list;
}

function all(): Stored[] {
    if (!store) store = seed();
    return store;
}

function view(s: Stored, loggedIn: boolean): MyGroupBuyReview {
    const own = s.mine && loggedIn;
    const { itemId: _i, deleted: _d, ...rest } = s;
    return mockClone({ ...rest, mine: own, hidden: own ? s.hidden : null, actions: { edit: own && Date.now() < new Date(s.editableUntil).getTime(), delete: own, report: loggedIn && !own } });
}

function stats(list: Stored[]): GroupBuyReviewPage['summary'] {
    const shown = list.filter((s) => !s.hidden);
    const distribution = { 1: 0, 2: 0, 3: 0, 4: 0, 5: 0 } as GroupBuyReviewPage['summary']['distribution'];
    for (const s of shown) distribution[s.rating]++;
    const sum = shown.reduce((n, s) => n + s.rating, 0);
    const photos = [...shown].sort(sortBy('LATEST')).flatMap((s) => s.photos.map((p) => ({ reviewId: s.id, url: p.url }))).slice(0, 12);
    return { average: shown.length ? Math.round((sum / shown.length) * 10) / 10 : null, count: shown.length, distribution, photoCount: shown.filter((s) => s.photos.length > 0).length, photos };
}

function sortBy(sort: GroupBuyReviewSort): (a: Stored, b: Stored) => number {
    const latest = (a: Stored, b: Stored) => b.createdAt.localeCompare(a.createdAt);
    if (sort === 'RATING_HIGH') return (a, b) => b.rating - a.rating || latest(a, b);
    if (sort === 'RATING_LOW') return (a, b) => a.rating - b.rating || latest(a, b);
    if (sort === 'PHOTO') return (a, b) => Number(b.photos.length > 0) - Number(a.photos.length > 0) || latest(a, b);
    return latest;
}

function failIf(flag: string): void {
    if (mockFlags().has(flag)) throw MOCK_ERRORS.internal();
}

function live(): Stored[] {
    return all().filter((s) => !s.deleted);
}

function find(id: string): Stored {
    const found = live().find((s) => s.id === id);
    if (!found) throw new ApiError({ status: 404, code: 'GROUP_BUY_REVIEW_NOT_FOUND', message: '리뷰를 찾을 수 없어요.' });
    return found;
}

function validate(body: GroupBuyReviewRequest): string {
    const text = body.body.trim();
    const fields: Record<string, string> = {};
    if (!(body.rating >= 1 && body.rating <= 5)) fields.rating = '별점을 골라 주세요.';
    if (text.length < 10 || text.length > 1000) fields.body = '리뷰는 10~1,000자로 써 주세요.';
    if (body.photoIds.length > 5) fields.photoIds = '사진은 5장까지 붙일 수 있어요.';
    if (Object.keys(fields).length) throw MOCK_ERRORS.validation(fields);
    return text;
}

const uploaded = new Map<string, GroupBuyReviewPhoto>();

function photosOf(ids: string[], keep: GroupBuyReviewPhoto[]): GroupBuyReviewPhoto[] {
    return ids.map((id) => uploaded.get(id) ?? keep.find((p) => p.id === id)).filter((p): p is GroupBuyReviewPhoto => !!p);
}

export async function mockReviewList(loggedIn: boolean, sort: GroupBuyReviewSort, photoOnly: boolean, page: number): Promise<GroupBuyReviewPage> {
    await mockDelay();
    failIf('review-fail');
    const list = live();
    // 숨긴 남의 리뷰는 빼고, 내 숨긴 리뷰는 나에게만
    const visible = list.filter((s) => !s.hidden || (s.mine && loggedIn));
    const matched = visible.filter((s) => !photoOnly || s.photos.length > 0).sort(sortBy(sort));
    return { summary: stats(list), items: matched.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE).map((s) => view(s, loggedIn)), total: matched.length, page, hasMore: (page + 1) * PAGE_SIZE < matched.length };
}

/** 목록 카드의 평균 별점·수 — 공구마다 조금씩 다른 값을 지어낸다 */
export function mockReviewStats(groupBuyId: string): GroupBuyReviewStats | null {
    if (mockFlags().has('review-empty')) return null;
    const n = [...groupBuyId].reduce((sum, c, i) => sum + c.charCodeAt(0) * (i + 1), 0);
    if (n % 5 === 0) return { average: null, count: 0 };
    return { average: Math.round((3.6 + (Math.floor(n / 7) % 13) / 10) * 10) / 10, count: 3 + (n % 40) };
}

export async function mockReviewTargets(order: MyGroupBuyOrderDetail): Promise<GroupBuyReviewTarget[]> {
    await mockDelay();
    const delivered = order.shipment?.deliveredAt ?? null;
    const until = delivered ? new Date(new Date(delivered).getTime() + 90 * DAY).toISOString().slice(0, 10) : null;
    const expired = mockFlags().has('review-expired');
    return order.items.map((item) => {
        const written = live().find((s) => s.mine && s.itemId === item.id);
        if (written) return { itemId: item.id, state: 'WRITTEN', writableUntil: until, reviewId: written.id };
        if (!delivered || order.status !== 'DELIVERED' || item.qty - item.canceledQty - item.returnedQty <= 0) return { itemId: item.id, state: 'NONE', writableUntil: null, reviewId: null };
        return { itemId: item.id, state: expired ? 'EXPIRED' : 'WRITABLE', writableUntil: until, reviewId: null };
    });
}

export async function mockReviewCreate(order: MyGroupBuyOrderDetail, itemId: string, body: GroupBuyReviewRequest): Promise<MyGroupBuyReview> {
    await mockDelay();
    failIf('review-save-fail');
    const item = order.items.find((i) => i.id === itemId);
    if (!item) throw new ApiError({ status: 404, code: 'GROUP_BUY_ORDER_NOT_FOUND', message: '주문 줄을 찾을 수 없어요.' });
    if (live().some((s) => s.mine && s.itemId === itemId)) throw new ApiError({ status: 409, code: 'GROUP_BUY_REVIEW_EXISTS', message: '이 상품은 이미 리뷰를 썼어요.' });
    const text = validate(body);
    const created = r({ id: String(++seq), rating: body.rating as Stored['rating'], body: text, photos: photosOf(body.photoIds, []), nickname: '나***', mine: true, skuName: item.skuName, roundNo: order.groupBuy.roundNo, createdAt: new Date().toISOString(), groupBuy: { id: order.groupBuy.id, title: order.groupBuy.title }, orderNo: order.orderNo, itemId });
    all().unshift(created);
    return view(created, true);
}

export async function mockReviewUpdate(id: string, body: GroupBuyReviewRequest): Promise<MyGroupBuyReview> {
    await mockDelay();
    failIf('review-save-fail');
    const s = find(id);
    if (Date.now() >= new Date(s.editableUntil).getTime()) throw new ApiError({ status: 409, code: 'GROUP_BUY_REVIEW_EDIT_EXPIRED', message: '작성한 지 30일이 지나 고칠 수 없어요. 지우고 다시 쓸 수는 없어요.' });
    const text = validate(body);
    Object.assign(s, { rating: body.rating, body: text, photos: photosOf(body.photoIds, s.photos), updatedAt: new Date().toISOString() });
    return view(s, true);
}

export async function mockReviewRemove(id: string): Promise<void> {
    await mockDelay();
    failIf('review-save-fail');
    find(id).deleted = true;
}

export async function mockMyReviews(page: number): Promise<MyGroupBuyReviewPage> {
    await mockDelay();
    failIf('review-fail');
    const mine = live().filter((s) => s.mine).sort(sortBy('LATEST'));
    return { items: mine.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE).map((s) => view(s, true)), total: mine.length, page, hasMore: (page + 1) * PAGE_SIZE < mine.length };
}

export async function mockReviewPhoto(file: File): Promise<GroupBuyReviewPhoto> {
    await mockDelay();
    if (!/^image\/(jpeg|png|webp)$/.test(file.type)) throw new ApiError({ status: 400, code: 'VALIDATION', message: 'JPG·PNG·WEBP 사진만 올릴 수 있어요.' });
    const photo = { id: `up-${++seq}`, url: URL.createObjectURL(file) };
    uploaded.set(photo.id, photo);
    return photo;
}

export async function mockReviewReport(_id: string, _body: GroupBuyReviewReport): Promise<void> {
    await mockDelay();
    if (mockFlags().has('report-already')) throw new ApiError({ status: 409, code: 'ALREADY_REPORTED', message: '이미 신고한 리뷰예요.' });
}

export type { GroupBuyReview };
