// MOCK: 회원 핫딜(1차) 관리자 API(`/api/admin/hotdeals/**` · `/api/admin/hotdeal-domains/**` · `/api/admin/hotdeal-suspicions/**` · `/api/admin/hotdeal-settings`)의 메모리 목업 —
// `useHotdealApi`·`useHotdealDomainApi`·`useHotdealSuspicionApi`·`useHotdealSettingsApi` 의 `// MOCK:` 줄이 부른다. 서버가 붙으면 이 폴더(`hotdeal/api/mock/`)째 지운다.
// 모양은 계약 `docs/features/hotdeal-contract.md` 「데이터 모양」, 화면 흐름은 03-hotdeal 7-1. 메모리 상태는 새로고침하면 처음으로 돌아간다(화면 전환 사이에는 남는다).
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` (쉼표로 여러 개) ─
//   empty        목록이 모두 비었다(핫딜 글 · 링크 규칙 · 의심 후보)
//   error        목록·설정 불러오기 실패(500) — 화면의 오류 + 다시 시도
//   detail-error 서랍(핫딜 글·의심 후보 상세) 불러오기 실패(500)
//   slow         모든 요청이 2.5초 걸린다(대기 Skeleton 확인)
//   forbidden    조치 권한이 없다 — 화면은 단추를 숨기고(`useHotdealPermission`), 조치 요청은 403 PERMISSION_DENIED
//   conflict     링크 규칙 저장이 409 HOTDEAL_DOMAIN_DUPLICATE + `fields.domain`(실제로 같은 도메인이 있어도 같은 오류)
//   stale        종료·재개·무시가 409 HOTDEAL_STATE_CHANGED(그 사이 다른 운영자가 처리) → 화면은 토스트 + 새로 받기
//   save-error   핫딜 설정 저장 실패(500)
//
// ── 목업 가정 ─
//   · 핫딜 게시판은 `hotdeal`(말머리 로봇·AI·메이킹·기타)과 `hotdeal-global` 둘. 이미지는 OG 를 흉내 낸 SVG 데이터 주소(외부 요청 없음).
//   · 상태 계산 차례: ENDED(저장) > EXPIRED(마감 지남) > FLAGGED(의심 시각 있음) > ACTIVE. 자동 종료 예정 = 의심 시각 + 설정의 유예 시간.
//   · 운영자 이름은 「관리자(나)」로 둔다. 문구는 서버 몫이라 한국어로만 준다(i18n.md 5절).

import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type {
    AdminDealDetail,
    AdminDealPage,
    AdminDealQuery,
    AdminDealRow,
    DealEndedBy,
    DealStock,
    DealView,
    DealViewFilter,
    HotdealBoardOption,
    HotdealBoardOptionsView,
    SameDealRef,
} from '~/composables/hotdeal/api/useHotdealApi.types';
import type { DealDomain, DealDomainListView, DealDomainRequest, DomainRuleFilter } from '~/composables/hotdeal/api/useHotdealDomainApi.types';
import type { DealSuspicion, DealSuspicionDetail, DealSuspicionPage, DealSuspicionPost, DealSuspicionStatus } from '~/composables/hotdeal/api/useHotdealSuspicionApi.types';
import type { HotdealSettingsRequest, HotdealSettingsView } from '~/composables/hotdeal/api/useHotdealSettingsApi.types';
import UtilHotdeal from '~/utils/util-hotdeal';

const HOUR = 60 * 60 * 1000;
const DAY = 24 * HOUR;
const at = (ms: number) => new Date(Date.now() + ms).toISOString();
const ME = '관리자(나)';

/** 목업에서 조치 권한이 없다고 흉내 낼 것인가(`?mock=forbidden`) — `useHotdealPermission` 이 읽는다 */
export function mockHotdealForbidden(): boolean {
    return mockFlags().has('forbidden');
}

async function gate(fail?: string): Promise<Set<string>> {
    const on = mockFlags();
    if (on.has('slow')) await new Promise((resolve) => setTimeout(resolve, 2500));
    else await mockDelay();
    if (fail && on.has(fail)) throw MOCK_ERRORS.internal();
    return on;
}

/** 조치 요청 — 권한·그 사이 바뀜 스위치 */
async function gateAction(): Promise<Set<string>> {
    const on = await gate();
    if (on.has('forbidden')) throw MOCK_ERRORS.forbidden();
    if (on.has('stale')) throw stateChanged();
    return on;
}

const stateChanged = () => new ApiError({ status: 409, code: 'HOTDEAL_STATE_CHANGED', message: '그 사이 다른 운영자가 처리했어요. 최신 상태로 다시 불러올게요.' });
const len = (text: string) => Array.from(text.trim()).length;

// ── 게시판 ─

const BOARDS: HotdealBoardOption[] = [
    {
        id: '901',
        slug: 'hotdeal',
        name: '핫딜',
        nameEn: 'Hot deals',
        active: true,
        prefixes: [
            { id: '9011', name: '로봇' },
            { id: '9012', name: 'AI' },
            { id: '9013', name: '메이킹' },
            { id: '9014', name: '기타' },
        ],
    },
    { id: '902', slug: 'hotdeal-global', name: '해외 핫딜', nameEn: 'Global deals', active: true, prefixes: [{ id: '9021', name: '직구' }] },
];

const MEMBERS = [
    { id: '3101', nickname: '로봇덕후' },
    { id: '3102', nickname: '알뜰공돌이' },
    { id: '3103', nickname: '납땜장인', withdrawn: false },
    { id: '3104', nickname: '할인헌터', withdrawn: false },
    { id: '3105', nickname: '딥러닝초보', withdrawn: false },
    { id: '3106', nickname: '메이커맘' },
    { id: '3107', nickname: '탈퇴한회원', withdrawn: true },
] as const;

/** OG 이미지 흉내 — 색 네모 + 글자 한 자 */
function svg(letter: string, color: string): string {
    const body = `<svg xmlns="http://www.w3.org/2000/svg" width="120" height="120"><rect width="120" height="120" rx="12" fill="${color}"/><text x="60" y="76" font-size="48" text-anchor="middle" fill="#fff" font-family="sans-serif">${letter}</text></svg>`;
    return `data:image/svg+xml;utf8,${encodeURIComponent(body)}`;
}

// ── 핫딜 글 ─

interface StoredDeal {
    row: AdminDealRow;
    endedByName: string | null;
    endReason: string | null;
    keptAt: string | null;
    /** 같은 딜 판정 열쇠(`product_url_key`) */
    productKey: string;
}

interface Seed {
    title: string;
    board: 0 | 1;
    prefix: number | null;
    store: string;
    host: string;
    path: string;
    deal: number;
    normal: number | null;
    ship: number | null;
    stock: DealStock;
    /** 몇 시간 전에 올렸나 */
    ageH: number;
    author: number;
    color: string;
    endsInH?: number | null;
    ended?: DealEndedBy;
    endedAgoH?: number;
    reqs?: [number, number, number];
    flaggedAgoH?: number;
    keptAgoH?: number;
    reports?: number;
    affiliate?: boolean;
    noImage?: boolean;
    coupon?: string;
    basis?: string;
}

const SEEDS: Seed[] = [
    { title: '[쿠팡] 라즈베리파이 5 8GB 스타터 키트 역대가', board: 0, prefix: 0, store: '쿠팡', host: 'coupang.com', path: '/vp/products/8123', deal: 128000, normal: 159000, ship: 0, stock: 'IN_STOCK', ageH: 2, author: 0, color: '#c2410c', endsInH: 46 },
    { title: '[11번가] 서보모터 MG996R 10개 묶음', board: 0, prefix: 0, store: '11번가', host: '11st.co.kr', path: '/products/5531', deal: 23900, normal: 32000, ship: 3000, stock: 'LOW', ageH: 5, author: 1, color: '#dc2626', reqs: [2, 1, 0], flaggedAgoH: 3 },
    { title: 'ESP32-S3 개발보드 2+1 (알리)', board: 1, prefix: 0, store: '알리익스프레스', host: 'aliexpress.com', path: '/item/1005', deal: 9800, normal: 15000, ship: null, stock: 'UNKNOWN', ageH: 8, author: 2, color: '#ea580c', coupon: '앱 전용 쿠폰 적용가' },
    { title: '[G마켓] 3D 프린터 필라멘트 PLA 1kg 4롤', board: 0, prefix: 2, store: 'G마켓', host: 'gmarket.co.kr', path: '/item?goodscode=2201', deal: 39900, normal: 56000, ship: 0, stock: 'IN_STOCK', ageH: 11, author: 5, color: '#16a34a', endsInH: -6 },
    { title: '젯슨 오린 나노 슈퍼 개발자 키트 품절 임박', board: 0, prefix: 1, store: '디바이스마트', host: 'devicemart.co.kr', path: '/goods/view?no=1', deal: 389000, normal: 429000, ship: 0, stock: 'LOW', ageH: 14, author: 4, color: '#0f766e', reqs: [3, 1, 0], flaggedAgoH: 6, reports: 1 },
    { title: '[쿠팡] 로봇청소기 부품 세트(사이드 브러시 6개)', board: 0, prefix: 3, store: '쿠팡', host: 'link.coupang.com', path: '/a/bX2', deal: 12900, normal: 19900, ship: 0, stock: 'IN_STOCK', ageH: 20, author: 3, color: '#b45309', affiliate: true },
    { title: '아두이노 우노 R4 WiFi 정품', board: 0, prefix: 2, store: '엘레파츠', host: 'eleparts.co.kr', path: '/goods/view?no=77', deal: 41000, normal: 48000, ship: 3500, stock: 'IN_STOCK', ageH: 26, author: 6, color: '#0369a1', ended: 'AUTHOR', endedAgoH: 4 },
    { title: '[스마트스토어] 리튬 배터리 18650 8개', board: 0, prefix: 3, store: '배터리몰', host: 'smartstore.naver.com', path: '/batterymall/products/3', deal: 21000, normal: null, ship: 3000, stock: 'SOLD_OUT', ageH: 30, author: 3, color: '#15803d', ended: 'AUTO', endedAgoH: 10, reqs: [4, 0, 1] },
    { title: 'AI 스피커 개발 키트 반값', board: 0, prefix: 1, store: '메이커스샵', host: 'makers-shop.kr', path: '/p/ai-kit', deal: 59000, normal: 118000, ship: 0, stock: 'UNKNOWN', ageH: 33, author: 3, color: '#7c3aed', reports: 2, basis: '정가 대비(공식몰 표기)' },
    { title: 'AI 스피커 개발 키트 + 확장보드', board: 0, prefix: 1, store: '메이커스샵', host: 'makers-shop.kr', path: '/p/ai-kit-plus', deal: 69000, normal: 128000, ship: 0, stock: 'UNKNOWN', ageH: 52, author: 3, color: '#6d28d9' },
    { title: '[쿠팡] 라즈베리파이 5 8GB 스타터 키트', board: 0, prefix: 0, store: '쿠팡', host: 'coupang.com', path: '/vp/products/8123', deal: 131000, normal: 159000, ship: 0, stock: 'IN_STOCK', ageH: 60, author: 1, color: '#c2410c' },
    { title: '드론 프레임 키트 F450 (해외 직배송)', board: 1, prefix: 0, store: '아마존', host: 'amazon.com', path: '/dp/B0F450', deal: 45000, normal: 62000, ship: 8000, stock: 'IN_STOCK', ageH: 72, author: 0, color: '#1d4ed8', endsInH: 30 },
    { title: '납땜 인두 스테이션 정밀형', board: 0, prefix: 2, store: '옥션', host: 'auction.co.kr', path: '/itemPage?no=99', deal: 54000, normal: 69000, ship: 0, stock: 'IN_STOCK', ageH: 80, author: 2, color: '#9a3412', ended: 'MANAGER', endedAgoH: 20 },
    { title: '스텝모터 드라이버 TMC2209 5개', board: 0, prefix: 0, store: '알리익스프레스', host: 'aliexpress.com', path: '/item/1007', deal: 14500, normal: 21000, ship: null, stock: 'UNKNOWN', ageH: 96, author: 2, color: '#c2410c', noImage: true },
    { title: '로봇 팔 키트 6축 학습용', board: 0, prefix: 0, store: '메이커스샵', host: 'makers-shop.kr', path: '/p/arm6', deal: 99000, normal: 149000, ship: 0, stock: 'IN_STOCK', ageH: 110, author: 3, color: '#5b21b6', endsInH: -30 },
    { title: '[11번가] 오실로스코프 입문형 할인', board: 0, prefix: 2, store: '11번가', host: '11st.co.kr', path: '/products/9001', deal: 219000, normal: 259000, ship: 0, stock: 'IN_STOCK', ageH: 130, author: 4, color: '#b91c1c', reqs: [1, 0, 0], keptAgoH: 40 },
    { title: '라이다 센서 LD19 특가', board: 1, prefix: 0, store: '알리익스프레스', host: 'aliexpress.com', path: '/item/1009', deal: 52000, normal: 71000, ship: null, stock: 'LOW', ageH: 150, author: 0, color: '#ea580c', ended: 'AUTHOR', endedAgoH: 70 },
    { title: 'AI 비전 카메라 모듈 2개', board: 0, prefix: 1, store: '디바이스마트', host: 'devicemart.co.kr', path: '/goods/view?no=8', deal: 33000, normal: null, ship: 3000, stock: 'IN_STOCK', ageH: 170, author: 5, color: '#0d9488' },
    { title: 'CNC 조각기 3018 프로', board: 0, prefix: 2, store: 'G마켓', host: 'gmarket.co.kr', path: '/item?goodscode=3018', deal: 189000, normal: 239000, ship: 0, stock: 'UNKNOWN', ageH: 200, author: 6, color: '#166534', endsInH: -100 },
    { title: '점퍼 케이블 세트 120개', board: 0, prefix: 3, store: '쿠팡', host: 'coupang.com', path: '/vp/products/120', deal: 4900, normal: 8900, ship: 0, stock: 'IN_STOCK', ageH: 230, author: 1, color: '#a16207' },
    { title: '서보 제어 보드 PCA9685 3개', board: 0, prefix: 0, store: '엘레파츠', host: 'eleparts.co.kr', path: '/goods/view?no=9685', deal: 11000, normal: 15000, ship: 3500, stock: 'IN_STOCK', ageH: 260, author: 2, color: '#075985' },
    { title: '해외 직구 그리퍼 키트', board: 1, prefix: 0, store: '아마존', host: 'amazon.com', path: '/dp/B0GRIP', deal: 27000, normal: 35000, ship: 8000, stock: 'UNKNOWN', ageH: 300, author: 4, color: '#1e40af', ended: 'AUTO', endedAgoH: 200, reqs: [3, 0, 0] },
];

function build(seed: Seed, i: number): StoredDeal {
    const member = MEMBERS[seed.author]!;
    const board = BOARDS[seed.board]!;
    const createdAt = at(-seed.ageH * HOUR);
    const [s, p, l] = seed.reqs ?? [0, 0, 0];
    const flaggedAt = seed.flaggedAgoH !== undefined ? at(-seed.flaggedAgoH * HOUR) : null;
    const discount = seed.normal && seed.normal > seed.deal ? Math.floor(((seed.normal - seed.deal) / seed.normal) * 100) : null;
    const productUrl = `https://${seed.host}${seed.path}`;
    return {
        productKey: productUrl,
        endedByName: seed.ended === 'AUTHOR' ? member.nickname : seed.ended === 'MANAGER' ? '운영자 김핫딜' : null,
        endReason: seed.ended === 'MANAGER' ? '판매처에서 행사가 끝났어요(가격 확인).' : null,
        keptAt: seed.keptAgoH !== undefined ? at(-seed.keptAgoH * HOUR) : null,
        row: {
            postId: String(70100 + i),
            title: seed.title,
            board: { id: board.id, slug: board.slug, name: board.name, nameEn: board.nameEn },
            prefix: seed.prefix === null ? null : (board.prefixes[seed.prefix] ?? null),
            author: { id: member.id, nickname: member.nickname, withdrawn: 'withdrawn' in member && member.withdrawn === true },
            createdAt,
            openReports: seed.reports ?? 0,
            deal: {
                source: 'MEMBER',
                storeName: seed.store,
                productUrl,
                productHost: seed.host,
                normalPrice: seed.normal,
                dealPrice: seed.deal,
                discountRate: discount,
                shippingFee: seed.ship,
                couponNote: seed.coupon ?? null,
                priceBasis: seed.basis ?? null,
                startsAt: null,
                endsAt: seed.endsInH === undefined || seed.endsInH === null ? null : at(seed.endsInH * HOUR),
                stockState: seed.stock,
                affiliate: seed.affiliate === true,
                image: seed.noImage ? null : { url: svg(seed.store.slice(0, 1), seed.color), source: 'OG' },
                status: seed.ended ? 'ENDED' : 'ACTIVE',
                view: 'ACTIVE',
                endedBy: seed.ended ?? null,
                endedAt: seed.ended ? at(-(seed.endedAgoH ?? 1) * HOUR) : null,
                endRequests: { total: s + p + l, bySoldOut: s, byPriceChanged: p, byLinkDead: l, mine: null },
                flaggedAt,
                autoEndAt: null,
                sameDealCount: 0,
            },
        },
    };
}

let deals: StoredDeal[] = SEEDS.map(build);

/** 읽을 때 계산하는 칸(상태 · 자동 종료 예정 · 같은 딜 수)을 채운 사본 */
function view(d: StoredDeal): AdminDealRow {
    const row = mockClone(d.row);
    const deal = row.deal;
    const now = Date.now();
    let v: DealView = 'ACTIVE';
    if (deal.status === 'ENDED') v = 'ENDED';
    else if (deal.endsAt && Date.parse(deal.endsAt) <= now) v = 'EXPIRED';
    else if (deal.flaggedAt) v = 'FLAGGED';
    deal.view = v;
    deal.autoEndAt = v === 'FLAGGED' && deal.flaggedAt && settings.endGraceHours > 0 ? new Date(Date.parse(deal.flaggedAt) + settings.endGraceHours * HOUR).toISOString() : null;
    deal.sameDealCount = deals.filter((o) => o !== d && o.productKey === d.productKey && o.row.deal.status === 'ACTIVE').length;
    return row;
}

function findDeal(postId: string): StoredDeal {
    const d = deals.find((x) => x.row.postId === postId);
    if (!d) throw MOCK_ERRORS.notFound('POST_NOT_FOUND', '글을 찾을 수 없어요. 이미 지워졌을 수 있어요.');
    return d;
}

export async function mockHotdealBoards(): Promise<HotdealBoardOptionsView> {
    await gate();
    return { boards: mockClone(BOARDS) };
}

export async function mockHotdealList(query: AdminDealQuery): Promise<AdminDealPage> {
    const on = await gate('error');
    const empty = { total: 0, page: query.page, size: query.size, items: [], counts: { ACTIVE: 0, FLAGGED: 0, ENDED: 0, EXPIRED: 0, ALL: 0 } };
    if (on.has('empty')) return empty;
    const q = (query.q ?? '').trim().toLowerCase();
    const from = query.createdFrom ? Date.parse(query.createdFrom) : null;
    const to = query.createdTo ? Date.parse(query.createdTo) : null;
    const rows = deals
        .map(view)
        .filter((r) => !query.board || r.board.id === query.board)
        .filter((r) => !q || r.title.toLowerCase().includes(q) || r.deal.storeName.toLowerCase().includes(q) || r.author.nickname.toLowerCase().includes(q))
        .filter((r) => (from === null || Date.parse(r.createdAt) >= from) && (to === null || Date.parse(r.createdAt) <= to))
        .sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt));
    const counts = { ...empty.counts, ALL: rows.length };
    for (const r of rows) counts[r.deal.view] += 1;
    const picked = query.view === 'ALL' ? rows : rows.filter((r) => r.deal.view === (query.view as DealViewFilter));
    const start = query.page * query.size;
    return { total: picked.length, page: query.page, size: query.size, items: picked.slice(start, start + query.size), counts };
}

export async function mockHotdealDetail(postId: string): Promise<AdminDealDetail> {
    await gate('detail-error');
    const d = findDeal(postId);
    const row = view(d);
    const sameDeals: SameDealRef[] = deals
        .filter((o) => o !== d && o.productKey === d.productKey && o.row.deal.status === 'ACTIVE')
        .map(view)
        .sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt))
        .slice(0, 5)
        .map((r) => ({ postId: r.postId, title: r.title, dealPrice: r.deal.dealPrice, createdAt: r.createdAt, view: r.deal.view }));
    return {
        row,
        endedByName: row.deal.status === 'ENDED' ? d.endedByName : null,
        endReason: row.deal.status === 'ENDED' ? d.endReason : null,
        keptAt: d.keptAt,
        // MOCK: 공개 사이트(user-web 개발 서버) 주소 — 실제는 서버가 사이트 공개 주소로 만든다
        publicUrl: `http://localhost:3000/posts/${row.postId}`,
        sameDeals,
    };
}

function checkReason(reason: string | undefined): void {
    if (reason && len(reason) > UtilHotdeal.REASON_MAX) throw MOCK_ERRORS.validation({ reason: `사유는 ${UtilHotdeal.REASON_MAX}자까지 쓸 수 있어요.` });
}

export async function mockHotdealEnd(postId: string, reason?: string): Promise<void> {
    await gateAction();
    checkReason(reason);
    const d = findDeal(postId);
    if (d.row.deal.status === 'ENDED') throw stateChanged();
    d.row.deal.status = 'ENDED';
    d.row.deal.endedBy = 'MANAGER';
    d.row.deal.endedAt = new Date().toISOString();
    d.endedByName = ME;
    d.endReason = reason?.trim() || null;
}

export async function mockHotdealReopen(postId: string, reason?: string): Promise<void> {
    await gateAction();
    checkReason(reason);
    const d = findDeal(postId);
    if (d.row.deal.status !== 'ENDED') throw stateChanged();
    // 재개는 운영진만 — 의심·종료 요청을 리셋한다(계약 「권한·정책」)
    Object.assign(d.row.deal, { status: 'ACTIVE', endedBy: null, endedAt: null, flaggedAt: null, endRequests: { total: 0, bySoldOut: 0, byPriceChanged: 0, byLinkDead: 0, mine: null } });
    d.endedByName = null;
    d.endReason = null;
    d.keptAt = null;
}

// ── 링크 규칙 ─

const SHORTENERS = ['bit.ly', 'han.gl', 'coupa.ng', 'vo.la', 'me2.do', 't.co', 'goo.gl', 'tinyurl.com', 'url.kr'];
let domainSeq = 500;
let domains: DealDomain[] = [
    ...SHORTENERS.map((domain) => ({ id: String(++domainSeq), domain, rule: 'BLOCK' as const, common: false, note: '단축 주소', updatedBy: null, updatedAt: at(-30 * DAY) })),
    ...['coupang.com', '11st.co.kr', 'gmarket.co.kr', 'auction.co.kr', 'smartstore.naver.com', 'aliexpress.com', 'amazon.com'].map((domain) => ({
        id: String(++domainSeq),
        domain,
        rule: null,
        common: true,
        note: '대형몰',
        updatedBy: null,
        updatedAt: at(-30 * DAY),
    })),
    { id: String(++domainSeq), domain: 'link.coupang.com', rule: 'AFFILIATE', common: true, note: '쿠팡 파트너스 링크', updatedBy: '운영자 김핫딜', updatedAt: at(-3 * DAY) },
];

export async function mockDomainList(rule: DomainRuleFilter, q: string): Promise<DealDomainListView> {
    const on = await gate('error');
    if (on.has('empty')) return { items: [] };
    const items = UtilHotdeal.filterDomains(domains, rule, q).sort((a, b) => a.domain.localeCompare(b.domain));
    return { items: mockClone(items) };
}

function checkDomain(body: DealDomainRequest, selfId: string | null, on: Set<string>): string {
    const domain = UtilHotdeal.normalizeDomain(body.domain);
    const fields: Record<string, string> = {};
    if (!domain) fields.domain = '도메인 형식을 확인해 주세요. 예: coupang.com';
    if (body.rule === null && !body.common) fields.common = '규칙이 없으면 대형몰이어야 해요. 차단·제휴를 고르거나 대형몰을 켜 주세요.';
    if (body.note && len(body.note) > UtilHotdeal.NOTE_MAX) fields.note = `메모는 ${UtilHotdeal.NOTE_MAX}자까지 쓸 수 있어요.`;
    if (Object.keys(fields).length) throw MOCK_ERRORS.validation(fields);
    if (on.has('conflict') || domains.some((d) => d.domain === domain && d.id !== selfId)) {
        throw new ApiError({ status: 409, code: 'HOTDEAL_DOMAIN_DUPLICATE', message: '이미 규칙이 있는 도메인이에요.', fields: { domain: `「${domain}」은 이미 규칙이 있어요. 목록에서 고쳐 주세요.` } });
    }
    return domain as string;
}

export async function mockDomainCreate(body: DealDomainRequest): Promise<DealDomain> {
    const on = await gate();
    if (on.has('forbidden')) throw MOCK_ERRORS.forbidden();
    const domain = checkDomain(body, null, on);
    const item: DealDomain = { id: String(++domainSeq), domain, rule: body.rule, common: body.common, note: body.note?.trim() || null, updatedBy: ME, updatedAt: new Date().toISOString() };
    domains = [...domains, item];
    return mockClone(item);
}

export async function mockDomainUpdate(id: string, body: DealDomainRequest): Promise<DealDomain> {
    const on = await gate();
    if (on.has('forbidden')) throw MOCK_ERRORS.forbidden();
    const found = domains.find((d) => d.id === id);
    if (!found) throw MOCK_ERRORS.notFound('HOTDEAL_DOMAIN_NOT_FOUND', '규칙을 찾을 수 없어요. 이미 지워졌을 수 있어요.');
    const domain = checkDomain(body, id, on);
    Object.assign(found, { domain, rule: body.rule, common: body.common, note: body.note?.trim() || null, updatedBy: ME, updatedAt: new Date().toISOString() });
    return mockClone(found);
}

export async function mockDomainDelete(id: string): Promise<void> {
    const on = await gate();
    if (on.has('forbidden')) throw MOCK_ERRORS.forbidden();
    if (!domains.some((d) => d.id === id)) throw MOCK_ERRORS.notFound('HOTDEAL_DOMAIN_NOT_FOUND', '규칙을 찾을 수 없어요. 이미 지워졌을 수 있어요.');
    domains = domains.filter((d) => d.id !== id);
}

// ── 의심 후보 ─

let suspicions: DealSuspicion[] = [
    {
        id: 's-1',
        member: { id: '3104', nickname: '할인헌터', withdrawn: false },
        domain: 'makers-shop.kr',
        storeName: '메이커스샵',
        postCount: 5,
        othersCount: 0,
        windowDays: 7,
        firstDetectedAt: at(-2 * DAY),
        lastPostAt: at(-33 * HOUR),
        status: 'OPEN',
        dismissedBy: null,
        dismissedAt: null,
        note: null,
    },
    {
        id: 's-2',
        member: { id: '3103', nickname: '납땜장인', withdrawn: false },
        domain: 'eleparts.co.kr',
        storeName: '엘레파츠',
        postCount: 3,
        othersCount: 1,
        windowDays: 7,
        firstDetectedAt: at(-4 * DAY),
        lastPostAt: at(-3 * DAY),
        status: 'OPEN',
        dismissedBy: null,
        dismissedAt: null,
        note: null,
    },
    {
        id: 's-3',
        member: { id: '3107', nickname: '탈퇴한회원', withdrawn: true },
        domain: 'cheap-robot.shop',
        storeName: '치프로봇',
        postCount: 4,
        othersCount: 0,
        windowDays: 7,
        firstDetectedAt: at(-6 * DAY),
        lastPostAt: at(-5 * DAY),
        status: 'OPEN',
        dismissedBy: null,
        dismissedAt: null,
        note: null,
    },
    {
        id: 's-4',
        member: { id: '3105', nickname: '딥러닝초보', withdrawn: false },
        domain: 'devicemart.co.kr',
        storeName: '디바이스마트',
        postCount: 3,
        othersCount: 4,
        windowDays: 7,
        firstDetectedAt: at(-12 * DAY),
        lastPostAt: at(-8 * DAY),
        status: 'DISMISSED',
        dismissedBy: '운영자 김핫딜',
        dismissedAt: at(-7 * DAY),
        note: '여러 회원이 같이 올리는 큰 부품몰이라 홍보로 보기 어려움',
    },
];


export async function mockSuspicionList(status: DealSuspicionStatus, page: number, size: number): Promise<DealSuspicionPage> {
    const on = await gate('error');
    const counts: Record<DealSuspicionStatus, number> = { OPEN: 0, DISMISSED: 0 };
    if (on.has('empty')) return { total: 0, page, size, items: [], counts };
    for (const s of suspicions) counts[s.status] += 1;
    const picked = suspicions.filter((s) => s.status === status).sort((a, b) => Date.parse(b.lastPostAt) - Date.parse(a.lastPostAt));
    return { total: picked.length, page, size, items: mockClone(picked.slice(page * size, page * size + size)), counts };
}

export async function mockSuspicionDetail(id: string): Promise<DealSuspicionDetail> {
    await gate('detail-error');
    const s = suspicions.find((x) => x.id === id);
    if (!s) throw MOCK_ERRORS.notFound('HOTDEAL_SUSPICION_NOT_FOUND', '의심 후보를 찾을 수 없어요.');
    // 실제 글(작성자·판매처가 같은 것) + 목업 글로 건수를 채운다
    const real: DealSuspicionPost[] = deals
        .filter((d) => d.row.author.id === s.member.id && d.row.deal.productHost === s.domain)
        .map(view)
        .map((r) => ({ postId: r.postId, title: r.title, board: r.board, dealPrice: r.deal.dealPrice, view: r.deal.view, createdAt: r.createdAt, openReports: r.openReports }));
    const filler: DealSuspicionPost[] = Array.from({ length: Math.max(0, s.postCount - real.length) }, (_, i) => ({
        postId: String(69000 + Number(s.id.replace(/\D/g, '')) * 10 + i),
        title: `${s.storeName} 특가 모음 ${i + 1}탄`,
        board: { id: BOARDS[0]!.id, slug: BOARDS[0]!.slug, name: BOARDS[0]!.name, nameEn: BOARDS[0]!.nameEn },
        dealPrice: 19000 + i * 7000,
        view: i === 0 ? 'ENDED' : 'ACTIVE',
        createdAt: new Date(Date.parse(s.lastPostAt) - (i + 1) * 20 * HOUR).toISOString(),
        openReports: i === 1 ? 1 : 0,
    }));
    const posts = [...real, ...filler].sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt)).slice(0, UtilHotdeal.SUSPICION_POSTS);
    return { suspicion: mockClone(s), posts };
}

export async function mockSuspicionDismiss(id: string, note?: string): Promise<void> {
    await gateAction();
    if (note && len(note) > UtilHotdeal.REASON_MAX) throw MOCK_ERRORS.validation({ note: `메모는 ${UtilHotdeal.REASON_MAX}자까지 쓸 수 있어요.` });
    const s = suspicions.find((x) => x.id === id);
    if (!s || s.status !== 'OPEN') throw stateChanged();
    suspicions = suspicions.map((x) => (x.id === id ? { ...x, status: 'DISMISSED', dismissedBy: ME, dismissedAt: new Date().toISOString(), note: note?.trim() || null } : x));
}

// ── 설정 ─

let settings: HotdealSettingsView = {
    endRequestThreshold: 3,
    endGraceHours: 12,
    endRequestDailyLimit: 20,
    suspicionMinPosts: 3,
    suspicionWindowDays: 7,
    // 포인트 정책의 `dealEndRequestPoints`(읽기 전용)
    rewardPoints: 3,
    updatedAt: null,
    updatedBy: null,
};

export async function mockSettingsGet(): Promise<HotdealSettingsView> {
    await gate('error');
    return mockClone(settings);
}

export async function mockSettingsSave(body: HotdealSettingsRequest): Promise<HotdealSettingsView> {
    const on = await gate('save-error');
    if (on.has('forbidden')) throw MOCK_ERRORS.forbidden();
    const fields: Record<string, string> = {};
    for (const spec of UtilHotdeal.SETTINGS) {
        const v = body[spec.field];
        if (!Number.isInteger(v) || v < spec.min || v > spec.max) fields[spec.field] = `${spec.min}~${spec.max} 사이의 정수로 입력해 주세요.`;
    }
    if (Object.keys(fields).length) throw MOCK_ERRORS.validation(fields);
    settings = { ...settings, ...body, updatedAt: new Date().toISOString(), updatedBy: ME };
    return mockClone(settings);
}
