// MOCK: 회원 핫딜(1차)의 메모리 목업 — 딜 글 저장소 + 핫딜 API(`link-check`·`prefill`·종료·되돌리기·「아직 진행 중」·종료 요청·같은 딜·이미지 올리기·가격 허위 신고).
// `useHotdealApi.ts`·`useReportApi.ts` 와 게시판 목업(`composables/board/api/mock/board-deal-mock.ts` — 게시판·글 목록·글 상세·쓰기 모양으로 바꾼다)이 부른다.
// 서버가 붙으면 이 파일과 게시판 목업을 함께 지운다. 새로고침하면 처음 상태로 돌아간다. 이 파일은 다른 업무를 import 하지 않는다(게시판 → 핫딜 한 방향).
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름`(쉼표로 여럿) ─
//   deal-empty        핫딜 목록이 비었다(글이 하나도 없음)        deal-error      목록·글 상세·판정이 실패(500)
//   ·· 내 글(/posts/mock-deal-mine) 상태
//   deal-flagged      의심 상태(3명 알림, 12시간 뒤 자동 종료)    deal-keep-limit  의심 + 「아직 진행 중」 2번을 다 씀 → 「운영자 확인 중」
//   deal-ended        작성자 종료 1시간 전(되돌리기 23시간 남음)   deal-reopen      작성자 종료 20시간 전(되돌리기 4시간 남음)
//   deal-reopen-expired  작성자 종료 30시간 전(되돌리기 기한 지남)   deal-reopen-used  작성자 종료 2시간 전 + 이미 한 번 되돌림
//   deal-expired      마감이 지났다
//   deal-link-changed 내 글·mock-deal-2 「링크 바뀜」(D3)           deal-price-edited  내 글·mock-deal-2 「가격 수정 2회」(D3)
//   ·· 다른 사람 글 · 알리기
//   deal-sent         mock-deal-2 에 내가 이미 「품절」을 알렸다
//   deal-request-denied  종료 요청 자격 미달(403 DEAL_END_REQUEST_NOT_ALLOWED — 쓰기 등급·이메일 인증·정지)
//   deal-author-limit    같은 작성자 글 기간 한도(429 DEAL_END_REQUEST_AUTHOR_LIMIT — 7일 3회)
//   ·· 글쓰기·고치기
//   deal-blocked      링크 판정이 늘 차단(BLOCK)                     deal-affiliate  링크 판정이 늘 제휴(AFFILIATE)
//   deal-redirect     링크가 다른 호스트로 리디렉션(최종 호스트 `smartstore.naver.com`, 그 호스트면 `coupang.com`)
//   deal-og-none      OG 이미지 없음(NONE)   deal-og-failed  OG 첫 번째 가져오기 실패(FAILED — 다시 시도하면 된다)   deal-og-off  OG 가져오기가 설정으로 꺼짐(OFF)
//   deal-same         링크 판정에 「진행 중인 같은 딜」 2건
//   deal-daily-limit  등록 → 429 DEAL_DAILY_POST_LIMIT(하루 5건)    deal-store-interval  등록 → 429 DEAL_SAME_STORE_TOO_SOON(10분 간격, 7분 남음)
//   deal-save-warning 고치기 저장 → 저장됨 + `warnings[DEAL_LINK_BLOCKED]`(차단 도메인 진행 중)
//   ·· 제휴 링크 방식(D5 — 기본은 `MEMBER` 개인 제휴 허용)
//   deal-site-affiliate  `SITE` 사이트 제휴 — 쓰기 「제휴 링크예요」 체크 숨김 · 개인 제휴 링크(AFFILIATE, 최종 호스트 포함 — 예 `link.coupang.com`·`coupa.ng`·`linkprice.com`)는
//                        칸 오류 · 사이트 제휴 몰(쿠팡·11번가, 가짜 프로그램) 링크는 「구매 링크는 로보얌 제휴 링크로 바뀝니다」 · 카드·목록은 그 몰 글이 「제휴」(로보얌 제휴 문구), 그 밖 표시 없음
//   deal-site-affiliate-nostore  `SITE` 인데 사이트 제휴 몰 표가 비었다 → 모든 글 제휴 표시 없음(개인 제휴 링크 칸 오류는 같다)
//   ·· 보는 사람
//   deal-sprout       새싹(글쓰기 GRADE_TOO_LOW·종료 요청 403 DEAL_END_REQUEST_NOT_ALLOWED)   deal-guest  비로그인   deal-unverified  이메일 미인증(글쓰기·종료 요청 막힘)
//   ·· 모양
//   deal-card · deal-gallery   핫딜 게시판 기본 모양을 카드형·갤러리형으로(기본 목록형, 보기 전환 단추는 목록형·갤러리형일 때)
//   deal-gadget-off   홈 목업 가젯 `HOT_DEALS` 를 넣지 않는다
//
// ── 목업 규칙(가정 — 계약 「목업 가정」·05 §3 기본값) ─
//   · 링크 규칙: 접미사 일치 + 더 구체적인 규칙 우선, 리디렉션 체인(호스트 + 최종 호스트)의 모든 호스트에 적용(차단 > 제휴). 단축 주소 8개는 BLOCK(메모 「단축 주소」),
//     `blocked-mall.example` BLOCK, `coupa.ng`(→ `coupang.com`)·`link.coupang.com`·`s.click.aliexpress.com`·링크프라이스·애드픽·텐핑 AFFILIATE,
//     대형몰(`coupang.com`·`aliexpress.com`·`11st.co.kr`·`gmarket.co.kr`·`smartstore.naver.com`)은 규칙 없음 + common. 호스트 `noimage` 가 들어가면 OG 이미지 없음.
//   · 같은 딜 키 = 호스트 + 경로(쿼리 뗌). 진행 중(ACTIVE·FLAGGED)만 센다.
//   · 종료 요청: 서로 다른 회원 3명 → FLAGGED, 유예 12시간 → 자동 종료(목업은 배치가 없어 시각만 보인다). 작성자는 자기 글에 못 한다. 끝난 딜은 못 한다.
//   · keep 최대 2번 · 작성자 되돌리기 종료 뒤 24시간 안 1번 · 추천 10 이상 HOT · 마감 없는 딜 30일 뒤 기간 종료 · 금액 상한 1억 · 할인율 90% 이상이면 가격 기준 필수.
//   · 재고 「품절」로 저장 = 작성자 종료. 고치기 저장 때 링크를 바꾸지 않았는데 지금 규칙이 차단이면 저장 + 경고(링크를 차단 도메인으로 바꾸면 칸 오류).
//   · 딜 칸 수정 이력: 딜가·정상가·배송비가 바뀌면 「가격 수정」 +1, 호스트가 바뀌면 「링크 바뀜」.
//   · 제휴 링크 방식(D5): `affiliateKind` 는 읽을 때 지금 방식으로 계산 — `MEMBER` 면 `affiliate` 글 = MEMBER, `SITE` 면 사이트 제휴 몰(최종 호스트 우선, 접미사 일치) 글 = SITE, 그 밖 null
//     (기존 개인 제휴 글도 글은 남고 표시만 바뀐다 — 계약 「제휴 방식 전환 때 기존 글」). 저장된 `productUrl`·`affiliate` 는 바꾸지 않는다.
//     `buyUrl` 은 늘 `/go/deal/{postId}`(서버 302 — 목업에는 그 주소를 받는 곳이 없어 눌러도 열리지 않는다). 고치기에서 링크를 그대로 두면 `SITE` 여도 칸 오류 없음(차단과 같은 규칙).

import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type {
    AffiliateMode,
    DealEndReason,
    DealEndRequests,
    DealImageUpload,
    DealInput,
    DealLinkCheck,
    DealPrefill,
    DealSameRef,
    DealView,
    DealWarning,
    DomainRule,
    PostDeal,
} from '~/composables/hotdeal/api/useHotdealApi.types';

/** 종료 요청 문턱(명) · 의심 유예 — 계약 목업 가정 */
export const MOCK_END_THRESHOLD = 3;
const HOUR = 3_600_000;
const DAY = 86_400_000;
const GRACE_MS = 12 * HOUR;
/** 05 §3 기본값 — `keepMaxCount` · `hotLikeThreshold` · `autoExpireDays` · `priceMaxWon` · `discountWarnPercent` · 하루 글 · 같은 몰 간격 · 같은 작성자 요청 한도 */
const KEEP_MAX = 2;
const HOT_LIKES = 10;
const AUTO_EXPIRE_DAYS = 30;
const PRICE_MAX = 100_000_000;
const DISCOUNT_WARN = 90;
const DAILY_POST_LIMIT = 5;
const SAME_STORE_MINUTES = 10;
const AUTHOR_LIMIT = 3;
const AUTHOR_DAYS = 7;
/** 작성자 되돌리기 기한(D2) */
const REOPEN_MS = 24 * HOUR;

/** 목업 딜 글 ID 앞머리 — 게시판·글·댓글·신고 API 의 목업 분기가 이것으로 가른다 */
const ID_PREFIX = 'mock-deal-';

/** 목업 핫딜 게시판의 말머리(로봇·AI·메이킹·기타) */
export const MOCK_DEAL_PREFIXES: ReadonlyArray<{ id: string; name: string }> = [
    { id: 'mock-prefix-robot', name: '로봇' },
    { id: 'mock-prefix-ai', name: 'AI' },
    { id: 'mock-prefix-making', name: '메이킹' },
    { id: 'mock-prefix-etc', name: '기타' },
];

const SHORTENERS = ['bit.ly', 'han.gl', 'vo.la', 'me2.do', 't.co', 'goo.gl', 'tinyurl.com', 'url.kr'];
const RULES: ReadonlyArray<{ domain: string; rule: DomainRule | null; common: boolean; shortener?: boolean }> = [
    ...SHORTENERS.map((domain) => ({ domain, rule: 'BLOCK' as const, common: false, shortener: true })),
    { domain: 'blocked-mall.example', rule: 'BLOCK', common: false },
    // 05 §4-2 — 쿠팡 공식 단축은 차단이 아니라 제휴(공식 단축 여부 확인 필요) · 제휴 네트워크 시드
    { domain: 'coupa.ng', rule: 'AFFILIATE', common: true },
    { domain: 'link.coupang.com', rule: 'AFFILIATE', common: true },
    { domain: 's.click.aliexpress.com', rule: 'AFFILIATE', common: true },
    { domain: 'linkprice.com', rule: 'AFFILIATE', common: false },
    { domain: 'adpick.co.kr', rule: 'AFFILIATE', common: false },
    { domain: 'tenping.kr', rule: 'AFFILIATE', common: false },
    { domain: 'coupang.com', rule: null, common: true },
    { domain: 'aliexpress.com', rule: null, common: true },
    { domain: '11st.co.kr', rule: null, common: true },
    { domain: 'gmarket.co.kr', rule: null, common: true },
    { domain: 'smartstore.naver.com', rule: null, common: true },
];
/** 목업 리디렉션 — 호스트 → 최종 호스트 */
const REDIRECTS: ReadonlyArray<[string, string]> = [['coupa.ng', 'coupang.com']];
/** 호스트 → 쇼핑몰 이름 표(접미사 일치) */
const STORE_NAMES: ReadonlyArray<[string, string]> = [
    ['coupang.com', '쿠팡'],
    ['aliexpress.com', '알리익스프레스'],
    ['11st.co.kr', '11번가'],
    ['gmarket.co.kr', 'G마켓'],
    ['smartstore.naver.com', '네이버 스마트스토어'],
    ['devicemart.co.kr', '디바이스마트'],
    ['eleparts.co.kr', '엘레파츠'],
    ['amazon.com', 'Amazon'],
    ['mechasolution.com', '메카솔루션'],
    ['yes24.com', '예스24'],
];
/** 목업 사이트 제휴 몰 표(D5 `SiteAffiliateStore` — 가짜 프로그램 이름, 켠 것만). `deal-site-affiliate-nostore` 면 비운다 */
const SITE_STORES: ReadonlyArray<{ domain: string; programName: string }> = [
    { domain: 'coupang.com', programName: '쿠팡 파트너스(목업)' },
    { domain: '11st.co.kr', programName: '11번가 제휴(목업)' },
];

/** 지금 제휴 링크 방식(D5) — 스위치로만 `SITE` */
export function mockDealAffiliateMode(): AffiliateMode {
    const flags = mockFlags();
    return flags.has('deal-site-affiliate') || flags.has('deal-site-affiliate-nostore') ? 'SITE' : 'MEMBER';
}

/** 사이트 제휴 몰(접미사 일치, 최종 호스트 우선) — `SITE` 가 아니거나 표에 없으면 null */
function siteStoreOf(host: string, finalHost: string | null): { domain: string; programName: string } | null {
    if (mockDealAffiliateMode() !== 'SITE' || mockFlags().has('deal-site-affiliate-nostore')) return null;
    for (const h of [finalHost, host]) {
        const hit = h ? suffixMatch(h, SITE_STORES, (s) => s.domain) : null;
        if (hit) return hit;
    }
    return null;
}

/** 저장하는 딜 — 읽을 때 `view`·할인율·같은 딜 수·자동 종료·기간 종료·되돌리기 기한·HOT·제휴 표시 종류(지금 방식)·구매 주소를 계산한다 */
interface StoredDeal
    extends Omit<PostDeal, 'view' | 'discountRate' | 'sameDealCount' | 'autoEndAt' | 'endRequests' | 'autoExpireAt' | 'reopenUntil' | 'keepMax' | 'hot' | 'buyUrl' | 'affiliateKind' | 'siteProgramName'> {
    endRequests: Omit<DealEndRequests, 'mine'>;
    /** 내가 보낸 사유 */
    mine: DealEndReason | null;
    /** 작성자 되돌리기를 이미 썼다(D2 — 글마다 1번) */
    reopenUsed: boolean;
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

/* ── 가짜 이미지(내려받지 않게 SVG 데이터 주소 — 서버에서는 우리 서버 사본 주소) ── */
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

/** 최종 호스트(목업 리디렉션 표) — 같으면 null. 처음 채우는 목업 글이 쓴다 */
function fixedFinalHostOf(host: string): string | null {
    const final = host ? (suffixMatch(host, REDIRECTS, (r) => r[0])?.[1] ?? null) : null;
    return final && final !== host ? final : null;
}

/** 최종 호스트(목업 리디렉션 표 + `deal-redirect`) — 같으면 null. 링크 판정·새 글·고친 글이 쓴다 */
function finalHostOf(host: string): string | null {
    if (!host) return null;
    const final = fixedFinalHostOf(host) ?? (mockFlags().has('deal-redirect') ? (host === 'smartstore.naver.com' ? 'coupang.com' : 'smartstore.naver.com') : null);
    return final && final !== host ? final : null;
}

/** 리디렉션 체인 판정 — 차단 > 제휴 > 규칙 없음. 대형몰은 체인 중 하나라도 */
function judge(host: string, finalHost: string | null): { rule: DomainRule | null; common: boolean; shortener: boolean } {
    const matches = [host, finalHost].filter((h): h is string => Boolean(h)).map((h) => suffixMatch(h, RULES, (r) => r.domain));
    const block = matches.find((m) => m?.rule === 'BLOCK');
    const rule: DomainRule | null = block ? 'BLOCK' : matches.some((m) => m?.rule === 'AFFILIATE') ? 'AFFILIATE' : null;
    return { rule, common: matches.some((m) => m?.common), shortener: Boolean(block?.shortener) };
}

/* ── 저장소 ── */
let posts: MockDealPost[] | null = null;
let seq = 0;

function deal(partial: Partial<StoredDeal> & Pick<StoredDeal, 'productUrl' | 'storeName' | 'dealPrice'>): StoredDeal {
    const host = hostOf(partial.productUrl);
    return {
        source: 'MEMBER',
        productHost: host,
        finalHost: fixedFinalHostOf(host),
        normalPrice: null,
        shippingFee: null,
        couponNote: null,
        priceBasisKind: null,
        dutyIncluded: false,
        priceBasis: null,
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
        keepCount: 0,
        revision: { priceEdits: 0, linkChanged: false, lastEditedAt: null },
        reopenUsed: false,
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
        title: 'ESP32-S3 DevKitC 2개 묶음 (19,800원)',
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
            priceBasisKind: 'COUPON',
            priceBasis: '11/1 14시 기준',
            image: { url: fakeImage('ESP32-S3', 200), source: 'OG' },
        }),
    });
    const endMine = (agoMs: number, used = false) => {
        mine.deal.status = 'ENDED';
        mine.deal.endedBy = 'AUTHOR';
        mine.deal.endedAt = iso(-agoMs);
        mine.deal.reopenUsed = used;
    };
    if (flags.has('deal-flagged') || flags.has('deal-keep-limit')) {
        mine.deal.flaggedAt = iso(-2 * HOUR);
        mine.deal.endRequests = { total: 3, bySoldOut: 2, byPriceChanged: 1, byLinkDead: 0 };
        if (flags.has('deal-keep-limit')) mine.deal.keepCount = KEEP_MAX;
    } else if (flags.has('deal-ended')) endMine(HOUR);
    else if (flags.has('deal-reopen')) endMine(20 * HOUR);
    else if (flags.has('deal-reopen-expired')) endMine(30 * HOUR);
    else if (flags.has('deal-reopen-used')) endMine(2 * HOUR, true);
    else if (flags.has('deal-expired')) mine.deal.endsAt = iso(-DAY);

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
                priceBasisKind: 'LISTED',
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
                endsAt: iso(3 * DAY),
                couponNote: '연간 결제 전용 코드는 결제 화면에서 자동 적용',
                priceBasisKind: 'COUPON',
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
                priceBasisKind: 'CARD',
                priceBasis: '○○카드 청구 할인 포함',
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
                priceBasisKind: 'OVERSEAS',
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
                endsAt: iso(30 * HOUR),
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
        post('13', {
            title: 'Jetson Orin Nano 개발자 키트 해외 직구',
            prefixId: 'mock-prefix-ai',
            authorName: '직구러',
            mine: false,
            createdAt: iso(-6 * HOUR),
            likeCount: 14,
            commentCount: 8,
            viewCount: 940,
            deal: deal({
                productUrl: 'https://www.amazon.com/dp/B0EXAMPLE1',
                storeName: 'Amazon',
                normalPrice: 420000,
                dealPrice: 349000,
                stockState: 'LOW',
                endsAt: iso(20 * HOUR),
                priceBasisKind: 'OVERSEAS',
                dutyIncluded: true,
                priceBasis: '환율 1,380원 기준',
                image: { url: fakeImage('Jetson', 90), source: 'OG' },
            }),
        }),
        post('14', {
            title: 'AI 코딩 도우미 3개월 무료',
            prefixId: 'mock-prefix-ai',
            authorName: '개발새발',
            mine: false,
            createdAt: iso(-9 * HOUR),
            likeCount: 25,
            commentCount: 11,
            viewCount: 1300,
            deal: deal({
                productUrl: 'https://ai-tools.example/coding/trial',
                storeName: 'AI Tools',
                normalPrice: 45000,
                dealPrice: 0,
                shippingFee: 0,
                endsAt: iso(2 * DAY + 6 * HOUR),
                priceBasisKind: 'MEMBER',
                priceBasis: '신규 가입 회원 한정',
                image: { url: fakeImage('Code AI', 250), source: 'UPLOAD' },
            }),
        }),
        post('15', {
            title: '아두이노 센서 키트 37종',
            prefixId: 'mock-prefix-making',
            authorName: '센서모아',
            mine: false,
            createdAt: iso(-11 * HOUR),
            likeCount: 11,
            commentCount: 5,
            viewCount: 520,
            deal: deal({
                productUrl: 'https://www.eleparts.co.kr/goods/view?no=3737',
                storeName: '엘레파츠',
                normalPrice: 39000,
                dealPrice: 24900,
                shippingFee: 3000,
                stockState: 'SOLD_OUT',
                image: { url: fakeImage('Sensor 37', 60), source: 'OG' },
                status: 'ENDED',
                endedBy: 'AUTHOR',
                endedAt: iso(-2 * HOUR),
            }),
        }),
        post('16', {
            title: '스텝모터 NEMA17 4개 세트',
            prefixId: 'mock-prefix-making',
            authorName: '프린터수리',
            mine: false,
            createdAt: iso(-14 * HOUR),
            likeCount: 2,
            commentCount: 1,
            viewCount: 120,
            deal: deal({
                productUrl: 'https://www.mechasolution.com/shop/goods/goods_view.php?goodsno=1717',
                storeName: '메카솔루션',
                normalPrice: 56000,
                dealPrice: 41000,
                shippingFee: 3500,
                image: { url: fakeImage('NEMA17', 210), source: 'OG' },
            }),
        }),
        post('17', {
            title: 'ROS 2 입문서 30% 할인',
            prefixId: 'mock-prefix-robot',
            authorName: '책벌레',
            mine: false,
            createdAt: iso(-16 * HOUR),
            likeCount: 8,
            commentCount: 3,
            viewCount: 330,
            deal: deal({
                productUrl: 'https://www.yes24.com/Product/Goods/2222',
                storeName: '예스24',
                normalPrice: 38000,
                dealPrice: 26600,
                shippingFee: 0,
                priceBasisKind: 'LISTED',
                image: { url: fakeImage('ROS 2', 30), source: 'OG' },
            }),
        }),
        post('18', {
            title: '라즈베리파이 카메라 모듈 3',
            prefixId: 'mock-prefix-etc',
            authorName: '찰칵',
            mine: false,
            createdAt: iso(-18 * HOUR),
            likeCount: 4,
            commentCount: 0,
            viewCount: 180,
            deal: deal({
                productUrl: 'https://www.devicemart.co.kr/goods/view?no=3333',
                storeName: '디바이스마트',
                normalPrice: 42000,
                dealPrice: 35900,
                image: { url: fakeImage('Cam 3', 320), source: 'OG' },
            }),
        }),
        post('19', {
            title: '6축 로봇 팔 교육용 키트',
            prefixId: 'mock-prefix-robot',
            authorName: '팔운동',
            mine: false,
            createdAt: iso(-22 * HOUR),
            likeCount: 16,
            commentCount: 7,
            viewCount: 760,
            deal: deal({
                productUrl: 'https://coupa.ng/arm6axis',
                storeName: '쿠팡',
                normalPrice: 189000,
                dealPrice: 139000,
                shippingFee: 0,
                stockState: 'IN_STOCK',
                affiliate: true,
                image: { url: fakeImage('Arm 6', 0), source: 'OG' },
            }),
        }),
    ];
    // D3 — 딜 칸 수정 이력 스위치(내 글·mock-deal-2)
    for (const p of [mine, list[1]!]) {
        if (flags.has('deal-link-changed')) p.deal.revision = { ...p.deal.revision, linkChanged: true, lastEditedAt: iso(-HOUR) };
        if (flags.has('deal-price-edited')) p.deal.revision = { ...p.deal.revision, priceEdits: 2, lastEditedAt: iso(-HOUR) };
    }
    return (posts = list);
}

/** 마감이 없을 때 기간 종료 시각(등록 + 30일) */
function autoExpireOf(p: MockDealPost): number | null {
    return p.deal.endsAt ? null : Date.parse(p.createdAt) + AUTO_EXPIRE_DAYS * DAY;
}

function viewOf(p: MockDealPost, now: number): DealView {
    const d = p.deal;
    if (d.status === 'ENDED') return 'ENDED';
    const until = d.endsAt ? Date.parse(d.endsAt) : autoExpireOf(p);
    if (until !== null && until < now) return 'EXPIRED';
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

function sameRefOf(p: MockDealPost): DealSameRef {
    return { postId: p.id, title: p.title, dealPrice: p.deal.dealPrice, shippingFee: p.deal.shippingFee, createdAt: p.createdAt };
}

/** 작성자 되돌리기 기한 — 작성자 종료 · 아직 안 씀 · 24시간 안 */
function reopenUntilOf(d: StoredDeal, now: number): string | null {
    if (d.status !== 'ENDED' || d.endedBy !== 'AUTHOR' || d.reopenUsed || !d.endedAt) return null;
    const until = Date.parse(d.endedAt) + REOPEN_MS;
    return until > now ? new Date(until).toISOString() : null;
}

/** 저장한 딜 → 응답 모양(`PostDeal`) */
export function mockDealView(p: MockDealPost, now: number = Date.now()): PostDeal {
    const d = p.deal;
    const view = viewOf(p, now);
    const key = urlKey(d.productUrl);
    const sameDealCount = seed().filter((o) => o.id !== p.id && urlKey(o.deal.productUrl) === key && isLive(viewOf(o, now))).length;
    const discountRate = d.normalPrice !== null && d.normalPrice > d.dealPrice && d.normalPrice > 0 ? Math.floor(((d.normalPrice - d.dealPrice) / d.normalPrice) * 100) || null : null;
    const autoExpire = autoExpireOf(p);
    const { mine, endRequests, reopenUsed: _used, ...rest } = d;
    // 제휴 표시 종류는 지금 방식으로 계산(D5) — SITE 면 사이트 제휴 몰 글만, 기존 개인 제휴 글은 몰이 아니면 표시 없음
    const site = siteStoreOf(d.productHost, d.finalHost);
    const affiliateKind = mockDealAffiliateMode() === 'SITE' ? (site ? 'SITE' : null) : d.affiliate ? 'MEMBER' : null;
    return mockClone({
        ...rest,
        // MOCK: 서버 경유 구매 주소(`GET /go/deal/{postId}` 302) — 목업에는 받는 곳이 없다
        buyUrl: `/go/deal/${encodeURIComponent(p.id)}`,
        affiliateKind,
        siteProgramName: affiliateKind === 'SITE' ? (site?.programName ?? null) : null,
        discountRate,
        view,
        autoExpireAt: autoExpire !== null ? new Date(autoExpire).toISOString() : null,
        reopenUntil: reopenUntilOf(d, now),
        endRequests: { ...endRequests, mine },
        autoEndAt: view === 'FLAGGED' && d.flaggedAt ? new Date(Date.parse(d.flaggedAt) + GRACE_MS).toISOString() : null,
        keepMax: KEEP_MAX,
        hot: p.likeCount >= HOT_LIKES,
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

/** 보낸 딜 칸을 저장 모양으로 — 새 글·고친 글. 품절 저장 = 작성자 종료(05 §4-6), 고친 글은 수정 이력(D3)을 센다 */
function storedOf(input: DealInput, before: StoredDeal | null): StoredDeal {
    const host = hostOf(input.productUrl);
    const finalHost = finalHostOf(host);
    const { rule } = judge(host, finalHost);
    const next = deal({
        ...(before ?? {}),
        productUrl: input.productUrl,
        productHost: host,
        finalHost,
        storeName: input.storeName,
        normalPrice: input.normalPrice,
        dealPrice: input.dealPrice,
        shippingFee: input.shippingFee,
        couponNote: input.couponNote,
        priceBasisKind: input.priceBasisKind,
        dutyIncluded: input.priceBasisKind === 'OVERSEAS' && input.dutyIncluded,
        priceBasis: input.priceBasis,
        endsAt: input.endsAt,
        stockState: input.stockState,
        // 제휴 도메인(체인 포함)이면 서버가 켠다. 사이트 제휴 방식(D5)이면 개인 제휴 표시를 받지 않는다(고치기에서 링크를 그대로 두면 지금 값 유지)
        affiliate:
            mockDealAffiliateMode() === 'SITE' ? (before && before.productUrl === input.productUrl ? before.affiliate : false) : rule === 'AFFILIATE' ? true : input.affiliate,
        image: input.image,
    });
    if (before) {
        const priceChanged = before.dealPrice !== next.dealPrice || before.normalPrice !== next.normalPrice || before.shippingFee !== next.shippingFee;
        next.revision = {
            priceEdits: before.revision.priceEdits + (priceChanged ? 1 : 0),
            linkChanged: before.revision.linkChanged || before.productHost !== next.productHost,
            lastEditedAt: new Date().toISOString(),
        };
    }
    if (next.stockState === 'SOLD_OUT' && next.status === 'ACTIVE') {
        next.status = 'ENDED';
        next.endedBy = 'AUTHOR';
        next.endedAt = new Date().toISOString();
    }
    return next;
}

/**
 * 딜 칸 서버 검사(목업) — 계약 「필드·검증」. 오류 키는 `deal.*`
 * @param input - 보낸 딜
 * @param before - 고치기면 지금 딜(링크를 바꾸지 않았으면 차단이어도 칸 오류 대신 경고 — `mockDealSaveWarnings`)
 * @returns 칸 오류(없으면 빈 객체)
 */
export function mockDealFieldErrors(input: DealInput | null | undefined, before: StoredDeal | null = null): Record<string, string> {
    if (!input) return { deal: '핫딜 게시판에는 딜 정보가 필요해요.' };
    const out: Record<string, string> = {};
    const url = input.productUrl.trim();
    const host = hostOf(url);
    if (!/^https?:\/\//i.test(url) || !host) out['deal.productUrl'] = '상품 링크를 http(s) 주소로 적어 주세요.';
    else if (url.length > 1000) out['deal.productUrl'] = '상품 링크는 1000자까지예요.';
    else if (!before || before.productUrl !== url) {
        const flags = mockFlags();
        const j = flags.has('deal-blocked') ? { rule: 'BLOCK' as const, shortener: false } : flags.has('deal-affiliate') ? { rule: 'AFFILIATE' as const, shortener: false } : judge(host, finalHostOf(host));
        if (j.rule === 'BLOCK') out['deal.productUrl'] = j.shortener ? '단축 주소는 원래 주소로 바꿔 주세요.' : '이 쇼핑몰 링크는 올릴 수 없어요.';
        // 사이트 제휴 방식(D5)이면 개인 제휴 링크(최종 호스트 포함)는 받지 않는다 — 서버 코드 `DEAL_MEMBER_AFFILIATE_OFF`(목업은 다른 칸 오류와 함께 `400 VALIDATION`)
        else if (j.rule === 'AFFILIATE' && mockDealAffiliateMode() === 'SITE') out['deal.productUrl'] = '지금은 개인 제휴 링크를 받지 않아요 — 일반 상품 링크를 넣어 주세요.';
    }
    const store = input.storeName.trim();
    if (!store || store.length > 40) out['deal.storeName'] = '쇼핑몰 이름을 1~40자로 적어 주세요.';
    const maxText = `${PRICE_MAX.toLocaleString()}원까지 적을 수 있어요.`;
    if (!Number.isInteger(input.dealPrice) || input.dealPrice < 0) out['deal.dealPrice'] = '딜가를 0 이상의 정수로 적어 주세요.';
    else if (input.dealPrice > PRICE_MAX) out['deal.dealPrice'] = maxText;
    if (input.normalPrice !== null && (!Number.isInteger(input.normalPrice) || input.normalPrice < input.dealPrice)) out['deal.normalPrice'] = '정상가는 딜가 이상이어야 해요.';
    else if (input.normalPrice !== null && input.normalPrice > PRICE_MAX) out['deal.normalPrice'] = maxText;
    if (input.shippingFee !== null && input.shippingFee > PRICE_MAX) out['deal.shippingFee'] = maxText;
    const rate = input.normalPrice !== null && input.normalPrice > input.dealPrice ? Math.floor(((input.normalPrice - input.dealPrice) / input.normalPrice) * 100) : 0;
    if (!input.priceBasisKind && input.dealPrice === 0) out['deal.priceBasisKind'] = '0원 딜은 가격 기준을 골라 주세요.';
    else if (!input.priceBasisKind && rate >= DISCOUNT_WARN) out['deal.priceBasisKind'] = `할인율이 ${DISCOUNT_WARN}% 이상이면 가격 기준을 골라 주세요.`;
    if (input.endsAt && (Date.parse(input.endsAt) < Date.now() || Date.parse(input.endsAt) > Date.now() + 365 * DAY)) out['deal.endsAt'] = '마감은 지금 이후 365일 안으로 정해 주세요.';
    if ((input.couponNote ?? '').length > 100) out['deal.couponNote'] = '100자까지예요.';
    if ((input.priceBasis ?? '').length > 100) out['deal.priceBasis'] = '100자까지예요.';
    return out;
}

/** 글쓰기 한도(05 §3 — 하루 5건 · 같은 판매처 10분) — 스위치로만 걸린다 */
export function mockDealPostLimit(): void {
    const flags = mockFlags();
    if (flags.has('deal-daily-limit'))
        throw new ApiError({ status: 429, code: 'DEAL_DAILY_POST_LIMIT', message: `오늘은 핫딜을 ${DAILY_POST_LIMIT}건까지 올릴 수 있어요. 내일 다시 올려 주세요.`, extras: { limit: DAILY_POST_LIMIT } });
    if (flags.has('deal-store-interval'))
        throw new ApiError({
            status: 429,
            code: 'DEAL_SAME_STORE_TOO_SOON',
            message: `같은 쇼핑몰 딜은 ${SAME_STORE_MINUTES}분 간격으로 올릴 수 있어요. 7분 뒤에 올려 주세요.`,
            retryAfterSeconds: 7 * 60,
            extras: { intervalMinutes: SAME_STORE_MINUTES, remainingMinutes: 7 },
        });
}

/** 고치기 저장의 경고 — 지금 규칙으로 다시 판정해 차단이면(또는 `deal-save-warning`) `DEAL_LINK_BLOCKED` */
export function mockDealSaveWarnings(id: string): DealWarning[] {
    const p = find(id);
    const blocked = judge(p.deal.productHost, p.deal.finalHost).rule === 'BLOCK';
    if (!blocked && !mockFlags().has('deal-save-warning')) return [];
    return [{ code: 'DEAL_LINK_BLOCKED', message: '저장했어요. 다만 이 링크의 쇼핑몰은 지금 차단 도메인이라 운영자가 확인할 수 있어요.' }];
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

/** 지금 딜(고치기 검사용) */
export function mockDealStored(id: string): StoredDeal {
    return find(id).deal;
}

/** 딜 글을 고친다 — 딜 칸은 보냈을 때만, 끝난 딜(ENDED)은 고칠 수 없다(409, 가정) */
export function mockDealPatch(id: string, body: { title: string; content: string; prefixId: string | null; deal?: DealInput }): MockDealPost {
    const p = find(id);
    if (body.deal && p.deal.status === 'ENDED') throw new ApiError({ status: 409, code: 'DEAL_ENDED', message: '종료된 딜은 고칠 수 없어요.' });
    p.title = body.title;
    p.content = body.content;
    p.prefixId = body.prefixId;
    p.updatedAt = new Date().toISOString();
    if (body.deal) p.deal = storedOf(body.deal, p.deal);
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
    const finalHost = finalHostOf(host);
    const j = judge(host, finalHost);
    const rule: DomainRule | null = flags.has('deal-blocked') ? 'BLOCK' : flags.has('deal-affiliate') ? 'AFFILIATE' : j.rule;
    const shortener = rule === 'BLOCK' && !flags.has('deal-blocked') && j.shortener;
    const storeName = storeNameOf(finalHost ?? host) ?? storeNameOf(host);
    const key = urlKey(url);
    const now = Date.now();

    let og: DealLinkCheck['og'];
    if (rule === 'BLOCK') og = { status: 'NONE', imageUrl: null, title: null, storeName: null };
    else if (flags.has('deal-og-off')) og = { status: 'OFF', imageUrl: null, title: null, storeName: null };
    else if (flags.has('deal-og-failed') && !ogFailedOnce.has(key)) {
        ogFailedOnce.add(key);
        og = { status: 'FAILED', imageUrl: null, title: null, storeName: null };
    } else if (flags.has('deal-og-none') || host.includes('noimage')) og = { status: 'NONE', imageUrl: null, title: '링크에서 가져온 상품 이름(이미지 없음)', storeName };
    else og = { status: 'OK', imageUrl: fakeImage(storeName ?? host.split('.')[0] ?? 'Deal', (key.length * 37) % 360), title: 'ESP32-S3 DevKitC-1 N8R8 개발 보드', storeName: storeName ?? host };

    let sameDeals: DealSameRef[] = seed()
        .filter((p) => urlKey(p.deal.productUrl) === key && isLive(viewOf(p, now)))
        .map(sameRefOf);
    if (flags.has('deal-same') && sameDeals.length === 0) {
        sameDeals = seed()
            .filter((p) => isLive(viewOf(p, now)) && !p.mine)
            .slice(0, 2)
            .map(sameRefOf);
    }
    // 제휴 링크 방식(D5) — 사이트 제휴 몰이면 그 프로그램(차단 링크는 null)
    const affiliateMode = mockDealAffiliateMode();
    const site = rule === 'BLOCK' ? null : siteStoreOf(host, finalHost);
    return mockClone({ host, finalHost, rule, common: j.common, shortener, storeName, og, sameDeals, affiliateMode, siteAffiliate: site ? { programName: site.programName } : null });
}

/** 끝난 딜에서 새 딜 올리기 — 링크·몰·이미지·제목(가격·마감은 비운다) */
export async function mockDealPrefill(fromPostId: string): Promise<DealPrefill> {
    await mockDelay();
    const p = find(fromPostId);
    return mockClone({ fromPostId: p.id, title: p.title, storeName: p.deal.storeName, productUrl: p.deal.productUrl, image: p.deal.image });
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

/** 작성자 되돌리기(D2) — 작성자 종료 뒤 24시간 안 1번, ENDED → ACTIVE(재고 품절이면 모름으로 — 가정) */
export async function mockDealReopen(postId: string): Promise<PostDeal> {
    await mockDelay();
    const p = find(postId);
    const d = p.deal;
    if (!p.mine) throw new ApiError({ status: 403, code: 'FORBIDDEN', message: '작성자만 할 수 있어요.' });
    if (d.status !== 'ENDED' || d.endedBy !== 'AUTHOR') throw new ApiError({ status: 403, code: 'FORBIDDEN', message: '운영진이 종료했거나 자동 종료된 딜은 운영진만 다시 열 수 있어요.' });
    if (d.reopenUsed) throw new ApiError({ status: 409, code: 'DEAL_REOPEN_USED', message: '이 딜은 이미 한 번 다시 진행 중으로 돌렸어요.' });
    if (!reopenUntilOf(d, Date.now())) throw new ApiError({ status: 409, code: 'DEAL_REOPEN_EXPIRED', message: '종료하고 24시간이 지나 다시 진행 중으로 돌릴 수 없어요.' });
    d.status = 'ACTIVE';
    d.endedBy = null;
    d.endedAt = null;
    d.reopenUsed = true;
    if (d.stockState === 'SOLD_OUT') d.stockState = 'UNKNOWN';
    return mockDealView(p);
}

/** 「아직 진행 중이에요」 — FLAGGED → ACTIVE(요청 수는 리셋하지 않는다 — `end_flag_cleared_at`), 최대 2번 */
export async function mockDealKeep(postId: string): Promise<PostDeal> {
    await mockDelay();
    const p = find(postId);
    if (!p.mine) throw new ApiError({ status: 403, code: 'FORBIDDEN', message: '작성자만 할 수 있어요.' });
    if (viewOf(p, Date.now()) !== 'FLAGGED') throw new ApiError({ status: 409, code: 'DEAL_NOT_FLAGGED', message: '품절 알림이 들어온 딜이 아니에요.' });
    if (KEEP_MAX > 0 && p.deal.keepCount >= KEEP_MAX) throw new ApiError({ status: 409, code: 'DEAL_KEEP_LIMIT', message: `「아직 진행 중」은 ${KEEP_MAX}번까지예요. 운영자가 확인하고 있어요.` });
    p.deal.flaggedAt = null;
    p.deal.keepCount += 1;
    return mockDealView(p);
}

/** 종료 요청 접수(지연 없이) — 알리기·가격 허위 신고(D4)가 같이 쓴다 */
function acceptEndRequest(postId: string, reason: DealEndReason): MockDealPost {
    const flags = mockFlags();
    if (flags.has('deal-guest')) throw new ApiError({ status: 401, code: 'UNAUTHENTICATED', message: '로그인이 필요해요.' });
    if (flags.has('deal-sprout') || flags.has('deal-unverified') || flags.has('deal-request-denied'))
        throw new ApiError({ status: 403, code: 'DEAL_END_REQUEST_NOT_ALLOWED', message: '이 게시판에 글을 쓸 수 있는 회원(일반 등급 이상·이메일 인증)만 알릴 수 있어요.' });
    const p = find(postId);
    if (p.mine) throw new ApiError({ status: 403, code: 'DEAL_END_REQUEST_OWN', message: '내 글에는 알릴 수 없어요.' });
    if (p.deal.mine) throw new ApiError({ status: 409, code: 'DEAL_END_REQUEST_EXISTS', message: '이미 알린 딜이에요.' });
    if (!isLive(viewOf(p, Date.now()))) throw new ApiError({ status: 409, code: 'DEAL_ENDED', message: '이미 끝난 딜이에요.' });
    if (flags.has('deal-author-limit'))
        throw new ApiError({
            status: 429,
            code: 'DEAL_END_REQUEST_AUTHOR_LIMIT',
            message: `같은 작성자의 글에는 ${AUTHOR_DAYS}일 동안 ${AUTHOR_LIMIT}번까지 알릴 수 있어요.`,
            extras: { limit: AUTHOR_LIMIT, days: AUTHOR_DAYS },
        });
    const r = p.deal.endRequests;
    r.total += 1;
    if (reason === 'SOLD_OUT') r.bySoldOut += 1;
    else if (reason === 'PRICE_CHANGED') r.byPriceChanged += 1;
    else r.byLinkDead += 1;
    p.deal.mine = reason;
    if (r.total >= MOCK_END_THRESHOLD && !p.deal.flaggedAt) p.deal.flaggedAt = new Date().toISOString();
    return p;
}

/** 종료 요청(품절·가격 변경 알리기) — 회원만·자격·작성자 불가·한 번만·끝난 딜 불가·같은 작성자 기간 한도. 문턱에 닿으면 FLAGGED */
export async function mockDealEndRequest(postId: string, reason: DealEndReason): Promise<PostDeal> {
    await mockDelay();
    return mockDealView(acceptEndRequest(postId, reason));
}

/** 목업 딜 글 신고 — D4: `DEAL_FALSE_PRICE` 는 종료 요청 `PRICE_CHANGED` 1건으로 접수, 그 밖 사유는 받은 척 */
export async function mockDealReport(postId: string, reason: string): Promise<void> {
    await mockDelay();
    if (reason === 'DEAL_FALSE_PRICE') acceptEndRequest(postId, 'PRICE_CHANGED');
}

/** 같은 딜(진행 중) — 이 글 제외 */
export async function mockDealSame(postId: string): Promise<DealSameRef[]> {
    await mockDelay();
    const p = find(postId);
    const key = urlKey(p.deal.productUrl);
    const now = Date.now();
    return mockClone(
        seed()
            .filter((o) => o.id !== p.id && urlKey(o.deal.productUrl) === key && isLive(viewOf(o, now)))
            .map(sameRefOf),
    );
}

/** 이미지 올리기 — 목업은 이 브라우저 안의 objectURL(새로고침하면 사라진다) */
export async function mockDealImageUpload(file: File): Promise<DealImageUpload> {
    await mockDelay();
    if (!file.type.startsWith('image/')) throw new ApiError({ status: 400, code: 'VALIDATION', message: '이미지 파일만 올릴 수 있어요.', fields: { file: '이미지 파일만 올릴 수 있어요.' } });
    return { id: `mock-image-${++seq}`, url: URL.createObjectURL(file) };
}
