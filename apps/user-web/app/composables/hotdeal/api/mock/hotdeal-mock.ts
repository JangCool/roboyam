// MOCK: 회원 핫딜(1차)의 메모리 목업 — 딜 글 저장소 + 핫딜 API(`link-check`·종료·「아직 진행 중」·종료 요청·같은 딜·이미지 올리기).
// `useHotdealApi.ts` 와 게시판 목업(`composables/board/api/mock/board-deal-mock.ts` — 게시판·글 목록·글 상세·쓰기 모양으로 바꾼다)이 부른다.
// 서버가 붙으면 이 파일과 게시판 목업을 함께 지운다. 새로고침하면 처음 상태로 돌아간다. 이 파일은 다른 업무를 import 하지 않는다(게시판 → 핫딜 한 방향).
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름`(쉼표로 여럿) ─
//   deal-empty        핫딜 목록이 비었다(글이 하나도 없음)        deal-error      목록·글 상세·판정이 실패(500)
//   deal-flagged      내 글(mock-deal-mine)이 의심 상태(3명 알림, 12시간 뒤 자동 종료)
//   deal-ended        내 글이 작성자 종료                            deal-expired    내 글의 마감이 지났다
//   deal-sent         다른 사람 글(mock-deal-2)에 내가 이미 「품절」을 알렸다
//   deal-blocked      링크 판정이 늘 차단(BLOCK)                     deal-affiliate  링크 판정이 늘 제휴(AFFILIATE)
//   deal-og-none      OG 이미지 없음(NONE)                          deal-og-failed  OG 첫 번째 가져오기 실패(FAILED — 다시 시도하면 된다)
//   deal-same         링크 판정에 「진행 중인 같은 딜」 2건
//   deal-sprout       보는 사람이 새싹(글쓰기 GRADE_TOO_LOW·종료 요청 403)   deal-guest  보는 사람이 비로그인   deal-unverified  이메일 미인증
//   deal-card · deal-gallery   핫딜 게시판 기본 모양을 카드형·갤러리형으로(기본 목록형, 보기 전환 단추는 목록형·갤러리형일 때)
//   deal-gadget-off   홈 목업 가젯 `HOT_DEALS` 를 넣지 않는다
//
// ── 목업 규칙(가정 — 계약 「목업 가정」) ─
//   · 링크 규칙: 접미사 일치 + 더 구체적인 규칙 우선. 단축 주소 9개는 BLOCK(메모 「단축 주소」), `blocked-mall.example` BLOCK, `link.coupang.com`·`s.click.aliexpress.com` AFFILIATE,
//     대형몰(`coupang.com`·`aliexpress.com`·`11st.co.kr`·`gmarket.co.kr`·`smartstore.naver.com`)은 규칙 없음 + common. 호스트 `noimage` 가 들어가면 OG 이미지 없음.
//   · 같은 딜 키 = 호스트 + 경로(쿼리 뗌). 진행 중(ACTIVE·FLAGGED)만 센다.
//   · 종료 요청: 서로 다른 회원 3명 → FLAGGED, 유예 12시간 → 자동 종료(목업은 배치가 없어 시각만 보인다). 작성자는 자기 글에 못 한다. 끝난 딜은 못 한다.

import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type {
    DealEndReason,
    DealEndRequests,
    DealImageUpload,
    DealInput,
    DealLinkCheck,
    DealSameRef,
    DealStock,
    DealView,
    DomainRule,
    PostDeal,
} from '~/composables/hotdeal/api/useHotdealApi.types';

/** 종료 요청 문턱(명) · 의심 유예(시간) — 계약 목업 가정 */
export const MOCK_END_THRESHOLD = 3;
const GRACE_MS = 12 * 3_600_000;
const HOUR = 3_600_000;
const DAY = 86_400_000;

/** 목업 딜 글 ID 앞머리 — 게시판·글·댓글·신고 API 의 목업 분기가 이것으로 가른다 */
const ID_PREFIX = 'mock-deal-';

/** 목업 핫딜 게시판의 말머리(로봇·AI·메이킹·기타) */
export const MOCK_DEAL_PREFIXES: ReadonlyArray<{ id: string; name: string }> = [
    { id: 'mock-prefix-robot', name: '로봇' },
    { id: 'mock-prefix-ai', name: 'AI' },
    { id: 'mock-prefix-making', name: '메이킹' },
    { id: 'mock-prefix-etc', name: '기타' },
];

const SHORTENERS = ['bit.ly', 'han.gl', 'coupa.ng', 'vo.la', 'me2.do', 't.co', 'goo.gl', 'tinyurl.com', 'url.kr'];
const RULES: ReadonlyArray<{ domain: string; rule: DomainRule | null; common: boolean; shortener?: boolean }> = [
    ...SHORTENERS.map((domain) => ({ domain, rule: 'BLOCK' as const, common: false, shortener: true })),
    { domain: 'blocked-mall.example', rule: 'BLOCK', common: false },
    { domain: 'link.coupang.com', rule: 'AFFILIATE', common: true },
    { domain: 's.click.aliexpress.com', rule: 'AFFILIATE', common: true },
    { domain: 'coupang.com', rule: null, common: true },
    { domain: 'aliexpress.com', rule: null, common: true },
    { domain: '11st.co.kr', rule: null, common: true },
    { domain: 'gmarket.co.kr', rule: null, common: true },
    { domain: 'smartstore.naver.com', rule: null, common: true },
];
/** 호스트 → 쇼핑몰 이름 표(접미사 일치) */
const STORE_NAMES: ReadonlyArray<[string, string]> = [
    ['coupang.com', '쿠팡'],
    ['aliexpress.com', '알리익스프레스'],
    ['11st.co.kr', '11번가'],
    ['gmarket.co.kr', 'G마켓'],
    ['smartstore.naver.com', '네이버 스마트스토어'],
    ['devicemart.co.kr', '디바이스마트'],
    ['eleparts.co.kr', '엘레파츠'],
];

/** 저장하는 딜 — 읽을 때 `view`·할인율·같은 딜 수·자동 종료 시각을 계산한다 */
interface StoredDeal extends Omit<PostDeal, 'view' | 'discountRate' | 'sameDealCount' | 'autoEndAt' | 'endRequests'> {
    endRequests: Omit<DealEndRequests, 'mine'>;
    /** 내가 보낸 사유 */
    mine: DealEndReason | null;
}

/** 목업 딜 글 — 게시판 목업이 글 줄·글 상세로 바꾼다 */
export interface MockDealPost {
    id: string;
    title: string;
    content: string;
    prefixId: string | null;
    /** 다른 회원 글의 글쓴이 이름(내 글은 보는 사람 이름) */
    authorName: string;
    mine: boolean;
    createdAt: string;
    updatedAt: string | null;
    likeCount: number;
    liked: boolean;
    commentCount: number;
    viewCount: number;
    deal: StoredDeal;
}

/* ── 가짜 이미지(내려받지 않게 SVG 데이터 주소) ── */
function fakeImage(label: string, hue: number): string {
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="400" height="400" viewBox="0 0 400 400"><rect width="400" height="400" fill="hsl(${hue},55%,88%)"/><circle cx="200" cy="170" r="80" fill="hsl(${hue},45%,62%)"/><text x="200" y="320" font-family="sans-serif" font-size="34" font-weight="700" text-anchor="middle" fill="hsl(${hue},40%,28%)">${label}</text></svg>`;
    return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
}

function hostOf(url: string): string {
    try {
        const host = new URL(url.trim()).hostname.toLowerCase();
        return host.startsWith('www.') ? host.slice(4) : host;
    } catch {
        return '';
    }
}

function urlKey(url: string): string {
    try {
        const u = new URL(url.trim());
        return `${hostOf(url)}${u.pathname.replace(/\/+$/, '')}`;
    } catch {
        return url.trim();
    }
}

function suffixMatch<T>(host: string, items: ReadonlyArray<T>, domainOf: (item: T) => string): T | null {
    let best: T | null = null;
    for (const item of items) {
        const d = domainOf(item);
        if ((host === d || host.endsWith(`.${d}`)) && (!best || d.length > domainOf(best).length)) best = item;
    }
    return best;
}

function storeNameOf(host: string): string | null {
    return suffixMatch(host, STORE_NAMES, (s) => s[0])?.[1] ?? null;
}

/* ── 저장소 ── */
let posts: MockDealPost[] | null = null;
let seq = 0;

function deal(partial: Partial<StoredDeal> & Pick<StoredDeal, 'productUrl' | 'storeName' | 'dealPrice'>): StoredDeal {
    return {
        source: 'MEMBER',
        productHost: hostOf(partial.productUrl),
        normalPrice: null,
        shippingFee: null,
        couponNote: null,
        priceBasis: null,
        startsAt: null,
        endsAt: null,
        stockState: 'UNKNOWN',
        affiliate: false,
        image: null,
        status: 'ACTIVE',
        endedBy: null,
        endedAt: null,
        flaggedAt: null,
        endRequests: { total: 0, bySoldOut: 0, byPriceChanged: 0, byLinkDead: 0 },
        mine: null,
        ...partial,
    };
}

function seed(): MockDealPost[] {
    if (posts) return posts;
    const flags = mockFlags();
    if (flags.has('deal-empty')) return (posts = []);
    const now = Date.now();
    const iso = (offset: number) => new Date(now + offset).toISOString();
    const post = (id: string, p: Omit<MockDealPost, 'id' | 'updatedAt' | 'liked' | 'content'> & { content?: string }): MockDealPost => ({
        id: `${ID_PREFIX}${id}`,
        updatedAt: null,
        liked: false,
        content: p.content ?? '목업 핫딜 글이에요. 실제 상품·가격과 관계없어요.\n\n구매 전에 쇼핑몰에서 가격을 다시 확인해 주세요.',
        ...p,
    });

    const mine = post('mine', {
        title: 'ESP32-S3 DevKitC 2개 묶음 (19,800원/무배)',
        prefixId: 'mock-prefix-making',
        authorName: '',
        mine: true,
        createdAt: iso(-3 * HOUR),
        likeCount: 7,
        commentCount: 3,
        viewCount: 210,
        deal: deal({
            productUrl: 'https://www.devicemart.co.kr/goods/view?no=12345',
            storeName: '디바이스마트',
            normalPrice: 26000,
            dealPrice: 19800,
            shippingFee: 0,
            endsAt: iso(3 * DAY),
            stockState: 'IN_STOCK',
            couponNote: '앱 첫 구매 쿠폰 2,000원 추가',
            priceBasis: '11/1 14시 기준',
            image: { url: fakeImage('ESP32-S3', 200), source: 'OG' },
        }),
    });
    if (flags.has('deal-flagged')) {
        mine.deal.flaggedAt = iso(-2 * HOUR);
        mine.deal.endRequests = { total: 3, bySoldOut: 2, byPriceChanged: 1, byLinkDead: 0 };
    } else if (flags.has('deal-ended')) {
        mine.deal.status = 'ENDED';
        mine.deal.endedBy = 'AUTHOR';
        mine.deal.endedAt = iso(-HOUR);
    } else if (flags.has('deal-expired')) {
        mine.deal.endsAt = iso(-DAY);
    }

    const list: MockDealPost[] = [
        mine,
        post('2', {
            title: '라즈베리파이 5 8GB 역대가',
            prefixId: 'mock-prefix-etc',
            authorName: '부품수집가',
            mine: false,
            createdAt: iso(-5 * HOUR),
            likeCount: 18,
            commentCount: 9,
            viewCount: 880,
            deal: deal({
                productUrl: 'https://www.eleparts.co.kr/goods/view?no=8000',
                storeName: '엘레파츠',
                normalPrice: 120000,
                dealPrice: 98000,
                shippingFee: 3000,
                endsAt: iso(5 * HOUR),
                stockState: 'LOW',
                image: { url: fakeImage('RPi 5', 340), source: 'OG' },
                mine: flags.has('deal-sent') ? 'SOLD_OUT' : null,
                endRequests: flags.has('deal-sent') ? { total: 1, bySoldOut: 1, byPriceChanged: 0, byLinkDead: 0 } : { total: 0, bySoldOut: 0, byPriceChanged: 0, byLinkDead: 0 },
            }),
        }),
        post('3', {
            title: '물걸레 로봇청소기 하프 프라이스 딜',
            prefixId: 'mock-prefix-robot',
            authorName: '집안일로봇',
            mine: false,
            createdAt: iso(-26 * HOUR),
            likeCount: 42,
            commentCount: 31,
            viewCount: 3120,
            deal: deal({
                productUrl: 'https://link.coupang.com/a/robot-cleaner?src=1',
                storeName: '쿠팡',
                normalPrice: 399000,
                dealPrice: 239000,
                shippingFee: 0,
                stockState: 'IN_STOCK',
                affiliate: true,
                image: { url: fakeImage('Robot', 150), source: 'OG' },
            }),
        }),
        post('4', {
            title: 'AI 그림 도구 연간 구독 40% 할인',
            prefixId: 'mock-prefix-ai',
            authorName: '프롬프트장인',
            mine: false,
            createdAt: iso(-8 * HOUR),
            likeCount: 12,
            commentCount: 4,
            viewCount: 640,
            deal: deal({
                productUrl: 'https://ai-tools.example/pricing/yearly',
                storeName: 'AI Tools',
                normalPrice: 150000,
                dealPrice: 90000,
                shippingFee: 0,
                endsAt: iso(2 * DAY),
                couponNote: '연간 결제 전용 코드는 결제 화면에서 자동 적용',
                image: { url: fakeImage('AI Art', 270), source: 'UPLOAD' },
            }),
        }),
        post('5', {
            title: '[쿠팡] 로봇청소기 같은 상품 카드 할인 더 됨',
            prefixId: 'mock-prefix-robot',
            authorName: '알뜰살뜰',
            mine: false,
            createdAt: iso(-20 * HOUR),
            likeCount: 5,
            commentCount: 2,
            viewCount: 410,
            deal: deal({
                productUrl: 'https://link.coupang.com/a/robot-cleaner?card=1',
                storeName: '쿠팡',
                normalPrice: 399000,
                dealPrice: 229000,
                shippingFee: 0,
                affiliate: true,
                priceBasis: '카드 청구 할인 포함',
                image: { url: fakeImage('Robot', 160), source: 'OG' },
            }),
        }),
        post('6', {
            title: '서보모터 MG996R 10개',
            prefixId: 'mock-prefix-robot',
            authorName: '서보덕후',
            mine: false,
            createdAt: iso(-30 * HOUR),
            likeCount: 9,
            commentCount: 6,
            viewCount: 720,
            deal: deal({
                productUrl: 'https://ko.aliexpress.com/item/1005001.html',
                storeName: '알리익스프레스',
                normalPrice: 32000,
                dealPrice: 21500,
                shippingFee: 0,
                stockState: 'LOW',
                image: { url: fakeImage('Servo', 20), source: 'OG' },
                flaggedAt: iso(-4 * HOUR),
                endRequests: { total: 3, bySoldOut: 2, byPriceChanged: 1, byLinkDead: 0 },
            }),
        }),
        post('7', {
            title: 'PLA 필라멘트 1kg 4롤 묶음',
            prefixId: 'mock-prefix-making',
            authorName: '출력소',
            mine: false,
            createdAt: iso(-3 * DAY),
            likeCount: 22,
            commentCount: 12,
            viewCount: 1500,
            deal: deal({
                productUrl: 'https://www.11st.co.kr/products/777',
                storeName: '11번가',
                normalPrice: 76000,
                dealPrice: 49900,
                shippingFee: 0,
                image: { url: fakeImage('PLA', 100), source: 'OG' },
                status: 'ENDED',
                endedBy: 'MANAGER',
                endedAt: iso(-DAY),
            }),
        }),
        post('8', {
            title: '엣지 AI 개발자 키트 특가',
            prefixId: 'mock-prefix-ai',
            authorName: '엣지러',
            mine: false,
            createdAt: iso(-4 * DAY),
            likeCount: 35,
            commentCount: 20,
            viewCount: 2400,
            deal: deal({
                productUrl: 'https://www.gmarket.co.kr/item?goodscode=42',
                storeName: 'G마켓',
                normalPrice: 359000,
                dealPrice: 299000,
                image: { url: fakeImage('Edge AI', 120), source: 'OG' },
                status: 'ENDED',
                endedBy: 'AUTO',
                endedAt: iso(-2 * DAY),
                endRequests: { total: 4, bySoldOut: 4, byPriceChanged: 0, byLinkDead: 0 },
            }),
        }),
        post('9', {
            title: '아두이노 우노 R4 호환보드 3개',
            prefixId: 'mock-prefix-making',
            authorName: '납땜왕',
            mine: false,
            createdAt: iso(-5 * DAY),
            likeCount: 4,
            commentCount: 1,
            viewCount: 300,
            deal: deal({
                productUrl: 'https://www.devicemart.co.kr/goods/view?no=999',
                storeName: '디바이스마트',
                normalPrice: 45000,
                dealPrice: 33000,
                shippingFee: 3000,
                endsAt: iso(-DAY),
                image: { url: fakeImage('UNO R4', 180), source: 'OG' },
            }),
        }),
        post('10', {
            title: '2D 라이다 센서 정가 대비 저렴',
            prefixId: 'mock-prefix-robot',
            authorName: '슬램입문',
            mine: false,
            createdAt: iso(-10 * HOUR),
            likeCount: 3,
            commentCount: 0,
            viewCount: 150,
            deal: deal({ productUrl: 'https://www.11st.co.kr/products/5050', storeName: '11번가', dealPrice: 118000 }),
        }),
        post('11', {
            title: '음성 인식 AI 스피커 모듈',
            prefixId: 'mock-prefix-ai',
            authorName: '말하는로봇',
            mine: false,
            createdAt: iso(-12 * HOUR),
            likeCount: 6,
            commentCount: 2,
            viewCount: 260,
            deal: deal({
                productUrl: 'https://www.gmarket.co.kr/item?goodscode=88',
                storeName: 'G마켓',
                normalPrice: 39000,
                dealPrice: 27900,
                shippingFee: 2500,
                endsAt: iso(2 * DAY),
                image: { url: fakeImage('Voice', 300), source: 'OG' },
            }),
        }),
        post('12', {
            title: 'N100 미니 PC 16GB/512GB',
            prefixId: 'mock-prefix-etc',
            authorName: '홈서버',
            mine: false,
            createdAt: iso(-2 * DAY),
            likeCount: 30,
            commentCount: 17,
            viewCount: 2100,
            deal: deal({
                productUrl: 'https://smartstore.naver.com/minipc/products/1',
                storeName: '네이버 스마트스토어',
                normalPrice: 259000,
                dealPrice: 199000,
                shippingFee: 0,
                stockState: 'IN_STOCK',
                image: { url: fakeImage('Mini PC', 230), source: 'OG' },
            }),
        }),
    ];
    return (posts = list);
}

function viewOf(d: StoredDeal, now: number): DealView {
    if (d.status === 'ENDED') return 'ENDED';
    if (d.endsAt && Date.parse(d.endsAt) < now) return 'EXPIRED';
    if (d.flaggedAt) return 'FLAGGED';
    return 'ACTIVE';
}

function isLive(view: DealView): boolean {
    return view === 'ACTIVE' || view === 'FLAGGED';
}

function find(id: string): MockDealPost {
    const p = seed().find((x) => x.id === id);
    if (!p) throw new ApiError({ status: 404, code: 'POST_NOT_FOUND', message: '글을 찾을 수 없어요.' });
    return p;
}

/** 저장한 딜 → 응답 모양(`PostDeal`) */
export function mockDealView(p: MockDealPost, now: number = Date.now()): PostDeal {
    const d = p.deal;
    const view = viewOf(d, now);
    const key = urlKey(d.productUrl);
    const sameDealCount = seed().filter((o) => o.id !== p.id && urlKey(o.deal.productUrl) === key && isLive(viewOf(o.deal, now))).length;
    const discountRate = d.normalPrice !== null && d.normalPrice > d.dealPrice && d.normalPrice > 0 ? Math.floor(((d.normalPrice - d.dealPrice) / d.normalPrice) * 100) || null : null;
    const { mine, endRequests, ...rest } = d;
    return mockClone({
        ...rest,
        discountRate,
        view,
        endRequests: { ...endRequests, mine },
        autoEndAt: view === 'FLAGGED' && d.flaggedAt ? new Date(Date.parse(d.flaggedAt) + GRACE_MS).toISOString() : null,
        sameDealCount,
    });
}

/** 목업 딜 글인가 — 글·댓글·신고 API 의 목업 분기 */
export function isMockDealPostId(id: string): boolean {
    return id.startsWith(ID_PREFIX);
}

/** 목업 딜 글 전부(지금 상태) — 게시판 목업이 줄로 바꾼다 */
export function mockDealPostsAll(): MockDealPost[] {
    return seed();
}

/** 목업 딜 글 하나 — 없으면 404 */
export function mockDealPost(id: string): MockDealPost {
    return find(id);
}

/** 보낸 딜 칸을 저장 모양으로 — 새 글·고친 글 */
function storedOf(input: DealInput, before: StoredDeal | null): StoredDeal {
    const host = hostOf(input.productUrl);
    const rule = suffixMatch(host, RULES, (r) => r.domain);
    return deal({
        ...(before ?? {}),
        productUrl: input.productUrl,
        productHost: host,
        storeName: input.storeName,
        normalPrice: input.normalPrice,
        dealPrice: input.dealPrice,
        shippingFee: input.shippingFee,
        couponNote: input.couponNote,
        priceBasis: input.priceBasis,
        endsAt: input.endsAt,
        stockState: input.stockState as DealStock,
        // 제휴 도메인이면 서버가 켠다
        affiliate: rule?.rule === 'AFFILIATE' ? true : input.affiliate,
        image: input.image,
    });
}

/**
 * 딜 칸 서버 검사(목업) — 계약 「필드·검증」. 오류 키는 `deal.*`
 * @returns 칸 오류(없으면 빈 객체)
 */
export function mockDealFieldErrors(input: DealInput | null | undefined): Record<string, string> {
    if (!input) return { deal: '핫딜 게시판에는 딜 정보가 필요해요.' };
    const out: Record<string, string> = {};
    const url = input.productUrl.trim();
    const host = hostOf(url);
    if (!/^https?:\/\//i.test(url) || !host) out['deal.productUrl'] = '상품 링크를 http(s) 주소로 적어 주세요.';
    else if (url.length > 1000) out['deal.productUrl'] = '상품 링크는 1000자까지예요.';
    else {
        const rule = mockFlags().has('deal-blocked') ? { rule: 'BLOCK' as const, shortener: false } : suffixMatch(host, RULES, (r) => r.domain);
        if (rule?.rule === 'BLOCK') out['deal.productUrl'] = rule.shortener ? '단축 주소는 원래 주소로 바꿔 주세요.' : '이 쇼핑몰 링크는 올릴 수 없어요.';
    }
    const store = input.storeName.trim();
    if (!store || store.length > 40) out['deal.storeName'] = '쇼핑몰 이름을 1~40자로 적어 주세요.';
    if (!Number.isInteger(input.dealPrice) || input.dealPrice < 0) out['deal.dealPrice'] = '딜가를 0 이상의 정수로 적어 주세요.';
    if (input.normalPrice !== null && (!Number.isInteger(input.normalPrice) || input.normalPrice < input.dealPrice)) out['deal.normalPrice'] = '정상가는 딜가 이상이어야 해요.';
    if (input.endsAt && (Date.parse(input.endsAt) < Date.now() || Date.parse(input.endsAt) > Date.now() + 365 * DAY)) out['deal.endsAt'] = '마감은 지금 이후 365일 안으로 정해 주세요.';
    if ((input.couponNote ?? '').length > 100) out['deal.couponNote'] = '100자까지예요.';
    if ((input.priceBasis ?? '').length > 100) out['deal.priceBasis'] = '100자까지예요.';
    return out;
}

/** 새 딜 글을 저장한다(게시판 목업의 글쓰기가 검사 뒤 부른다) */
export function mockDealInsert(body: { title: string; content: string; prefixId: string | null; deal: DealInput }): MockDealPost {
    const now = new Date().toISOString();
    const created: MockDealPost = {
        id: `${ID_PREFIX}new-${++seq}`,
        title: body.title,
        content: body.content,
        prefixId: body.prefixId,
        authorName: '',
        mine: true,
        createdAt: now,
        updatedAt: null,
        likeCount: 0,
        liked: false,
        commentCount: 0,
        viewCount: 0,
        deal: storedOf(body.deal, null),
    };
    seed().unshift(created);
    return created;
}

/** 딜 글을 고친다 — 딜 칸은 보냈을 때만, 끝난 딜(ENDED)은 고칠 수 없다(409, 가정) */
export function mockDealPatch(id: string, body: { title: string; content: string; prefixId: string | null; deal?: DealInput }): MockDealPost {
    const p = find(id);
    p.title = body.title;
    p.content = body.content;
    p.prefixId = body.prefixId;
    p.updatedAt = new Date().toISOString();
    if (body.deal) {
        if (p.deal.status === 'ENDED') throw new ApiError({ status: 409, code: 'DEAL_ENDED', message: '종료된 딜은 고칠 수 없어요.' });
        p.deal = storedOf(body.deal, p.deal);
    }
    return p;
}

export function mockDealDeleteRow(id: string): void {
    const list = seed();
    const i = list.findIndex((x) => x.id === id);
    if (i >= 0) list.splice(i, 1);
}

/* ── 핫딜 API ── */

/** 실패 스위치 — 목록·상세·판정 */
export function mockDealFailIfAsked(): void {
    if (mockFlags().has('deal-error')) throw MOCK_ERRORS.internal();
}

/** OG 를 이미 한 번 실패한 링크(`deal-og-failed` — 첫 번째만 실패, 다시 시도하면 된다) */
const ogFailedOnce = new Set<string>();

export async function mockLinkCheck(url: string): Promise<DealLinkCheck> {
    await mockDelay();
    mockDealFailIfAsked();
    const flags = mockFlags();
    const host = hostOf(url);
    if (!host) throw MOCK_ERRORS.validation({ url: '상품 링크를 http(s) 주소로 적어 주세요.' });
    const matched = suffixMatch(host, RULES, (r) => r.domain);
    const rule: DomainRule | null = flags.has('deal-blocked') ? 'BLOCK' : flags.has('deal-affiliate') ? 'AFFILIATE' : (matched?.rule ?? null);
    const shortener = rule === 'BLOCK' && !flags.has('deal-blocked') && Boolean(matched?.shortener);
    const storeName = storeNameOf(host);
    const key = urlKey(url);
    const now = Date.now();

    let og: DealLinkCheck['og'];
    if (rule === 'BLOCK') og = { status: 'NONE', imageUrl: null, title: null, storeName: null };
    else if (flags.has('deal-og-failed') && !ogFailedOnce.has(key)) {
        ogFailedOnce.add(key);
        og = { status: 'FAILED', imageUrl: null, title: null, storeName: null };
    } else if (flags.has('deal-og-none') || host.includes('noimage')) og = { status: 'NONE', imageUrl: null, title: '링크에서 가져온 상품 이름(이미지 없음)', storeName };
    else og = { status: 'OK', imageUrl: fakeImage(storeName ?? host.split('.')[0] ?? 'Deal', (key.length * 37) % 360), title: 'ESP32-S3 DevKitC-1 N8R8 개발 보드', storeName: storeName ?? host };

    let sameDeals: DealSameRef[] = seed()
        .filter((p) => urlKey(p.deal.productUrl) === key && isLive(viewOf(p.deal, now)))
        .map((p) => ({ postId: p.id, title: p.title, dealPrice: p.deal.dealPrice, createdAt: p.createdAt }));
    if (flags.has('deal-same') && sameDeals.length === 0) {
        sameDeals = seed()
            .filter((p) => isLive(viewOf(p.deal, now)) && !p.mine)
            .slice(0, 2)
            .map((p) => ({ postId: p.id, title: p.title, dealPrice: p.deal.dealPrice, createdAt: p.createdAt }));
    }
    return mockClone({ host, rule, common: Boolean(matched?.common), shortener, storeName, og, sameDeals });
}

/** 작성자 종료 — ACTIVE·FLAGGED → ENDED(AUTHOR) */
export async function mockDealEnd(postId: string): Promise<PostDeal> {
    await mockDelay();
    const p = find(postId);
    if (!p.mine) throw new ApiError({ status: 403, code: 'FORBIDDEN', message: '작성자만 종료할 수 있어요.' });
    if (p.deal.status === 'ENDED') throw new ApiError({ status: 409, code: 'DEAL_ENDED', message: '이미 종료된 딜이에요.' });
    p.deal.status = 'ENDED';
    p.deal.endedBy = 'AUTHOR';
    p.deal.endedAt = new Date().toISOString();
    return mockDealView(p);
}

/** 「아직 진행 중이에요」 — FLAGGED → ACTIVE(요청 수는 리셋하지 않는다 — `end_flag_cleared_at`) */
export async function mockDealKeep(postId: string): Promise<PostDeal> {
    await mockDelay();
    const p = find(postId);
    if (!p.mine) throw new ApiError({ status: 403, code: 'FORBIDDEN', message: '작성자만 할 수 있어요.' });
    if (viewOf(p.deal, Date.now()) !== 'FLAGGED') throw new ApiError({ status: 409, code: 'DEAL_NOT_FLAGGED', message: '품절 알림이 들어온 딜이 아니에요.' });
    p.deal.flaggedAt = null;
    return mockDealView(p);
}

/** 종료 요청(품절·가격 변경 알리기) — 회원만·작성자 불가·한 번만·끝난 딜 불가. 문턱에 닿으면 FLAGGED */
export async function mockDealEndRequest(postId: string, reason: DealEndReason): Promise<PostDeal> {
    await mockDelay();
    const flags = mockFlags();
    if (flags.has('deal-guest')) throw new ApiError({ status: 401, code: 'UNAUTHENTICATED', message: '로그인이 필요해요.' });
    if (flags.has('deal-sprout')) throw new ApiError({ status: 403, code: 'GRADE_TOO_LOW', message: '일반 등급부터 알릴 수 있어요.' });
    const p = find(postId);
    if (p.mine) throw new ApiError({ status: 403, code: 'DEAL_END_REQUEST_OWN', message: '내 글에는 알릴 수 없어요.' });
    if (p.deal.mine) throw new ApiError({ status: 409, code: 'DEAL_END_REQUEST_EXISTS', message: '이미 알린 딜이에요.' });
    if (!isLive(viewOf(p.deal, Date.now()))) throw new ApiError({ status: 409, code: 'DEAL_ENDED', message: '이미 끝난 딜이에요.' });
    const r = p.deal.endRequests;
    r.total += 1;
    if (reason === 'SOLD_OUT') r.bySoldOut += 1;
    else if (reason === 'PRICE_CHANGED') r.byPriceChanged += 1;
    else r.byLinkDead += 1;
    p.deal.mine = reason;
    if (r.total >= MOCK_END_THRESHOLD && !p.deal.flaggedAt) p.deal.flaggedAt = new Date().toISOString();
    return mockDealView(p);
}

/** 같은 딜(진행 중) — 이 글 제외 */
export async function mockDealSame(postId: string): Promise<DealSameRef[]> {
    await mockDelay();
    const p = find(postId);
    const key = urlKey(p.deal.productUrl);
    const now = Date.now();
    return mockClone(
        seed()
            .filter((o) => o.id !== p.id && urlKey(o.deal.productUrl) === key && isLive(viewOf(o.deal, now)))
            .map((o) => ({ postId: o.id, title: o.title, dealPrice: o.deal.dealPrice, createdAt: o.createdAt })),
    );
}

/** 이미지 올리기 — 목업은 이 브라우저 안의 objectURL(새로고침하면 사라진다) */
export async function mockDealImageUpload(file: File): Promise<DealImageUpload> {
    await mockDelay();
    if (!file.type.startsWith('image/')) throw new ApiError({ status: 400, code: 'VALIDATION', message: '이미지 파일만 올릴 수 있어요.', fields: { file: '이미지 파일만 올릴 수 있어요.' } });
    return { id: `mock-image-${++seq}`, url: URL.createObjectURL(file) };
}
