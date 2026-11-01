// MOCK: 회원 핫딜(1차) 관리자 API(`/api/admin/hotdeals/**` · `/api/admin/hotdeal-domains/**` · `/api/admin/hotdeal-suspicions/**` · `/api/admin/hotdeal-settings/**` · `/api/admin/hotdeal-site-affiliates/**`)의 메모리 목업 —
// `useHotdealApi`·`useHotdealDomainApi`·`useHotdealSuspicionApi`·`useHotdealSettingsApi`·`useHotdealSiteAffiliateApi` 의 `// MOCK:` 줄이 부른다. 서버가 붙으면 이 폴더(`hotdeal/api/mock/`)째 지운다.
// 모양은 계약 `docs/features/hotdeal-contract.md` 「데이터 모양」, 화면 흐름은 03-hotdeal 7-1 · 05-hotdeal-review. 메모리 상태는 새로고침하면 처음으로 돌아간다(화면 전환 사이에는 남는다).
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` (쉼표로 여러 개) ─
//   empty            목록이 모두 비었다(핫딜 글 · 링크 규칙 · 의심 후보)
//   error            목록·설정 불러오기 실패(500) — 화면의 오류 + 다시 시도
//   detail-error     서랍(핫딜 글·의심 후보 상세) 불러오기 실패(500)
//   slow             모든 요청이 2.5초 걸린다(대기 Skeleton 확인)
//   forbidden        조치 권한이 없다 — 화면은 단추를 숨기고(`useHotdealPermission`), 조치 요청은 403 PERMISSION_DENIED
//   conflict         링크 규칙 저장이 409 HOTDEAL_DOMAIN_DUPLICATE · 사이트 제휴 몰 저장이 409 HOTDEAL_SITE_AFFILIATE_DUPLICATE(둘 다 + `fields.domain`, 실제로 같은 도메인이 있어도 같은 오류)
//   stale            종료·재개·무시가 409 HOTDEAL_STATE_CHANGED(그 사이 다른 운영자가 처리) → 화면은 토스트 + 새로 받기
//   save-error       핫딜 설정 저장 실패(500)
//   revisions-empty  수정 이력이 하나도 없다(서랍 탭 빈 상태 · 목록의 「링크 바뀜」·「가격 수정 N회」 표시도 없음)
//   revisions-error  수정 이력 불러오기 실패(500) — 탭 안 오류 + 다시 시도
//   link-changed     모든 글 수정 이력 맨 위에 「링크 바뀜」(호스트가 바뀐 줄)이 하나 더 있다 → 목록 표시·서랍 강조 확인
//   impact-zero      차단 미리 보기가 늘 「진행 중 글 0건」 → 확인 없이 바로 저장되는 흐름
//   store-suspicion  의심 후보가 판매처 후보만 있다(회원 후보 0건)
//   site-affiliate        제휴 링크 방식이 사이트 제휴(`SITE`)로 시작한다 → 설정의 「사이트 제휴 몰」 표 · 링크 규칙 위 안내 · 글 서랍 「구매 링크」 안내
//   site-affiliate-empty  `SITE` 로 시작하고 사이트 제휴 몰 표가 비었다(빈 상태 문구)
//   affiliate-impact-zero 제휴 방식 전환 영향이 늘 0건(확인 창에 「영향받는 진행 중 글이 없어요」)
//   (신고 사건의 핫딜 조치 스위치 `report-deal` 은 `report/api/mock/report-deal-mock.ts`)
//
// ── 목업 가정 ─
//   · 핫딜 게시판은 `hotdeal`(말머리 로봇·AI·메이킹·기타)과 `hotdeal-global` 둘. 이미지는 OG 사본을 흉내 낸 SVG 데이터 주소(외부 요청 없음).
//   · 상태 계산 차례: ENDED(저장) > EXPIRED(마감 지남 · 마감 없으면 등록 + autoExpireDays) > FLAGGED(의심 시각 있음) > ACTIVE. 자동 종료 예정 = 의심 시각 + 설정의 유예 시간.
//   · 「가격 수정 N회」 = 딜가·정상가를 바꾼 수정 줄 수. 「링크 바뀜」 = 호스트가 바뀐 수정 줄이 하나라도 있음. 작성자 되돌리기 기한 = 작성자 종료 + 24시간(시드는 아직 안 씀).
//   · 「차단 도메인 진행 중」 = 진행 중(ACTIVE·FLAGGED) 글의 상품·최종 호스트가 지금 규칙에서 차단(규칙을 바꾸면 바로 따라 바뀐다).
//   · 운영자 이름은 「관리자(나)」로 둔다. 문구는 서버 몫이라 한국어로만 준다(i18n.md 5절).
//   · 제휴 링크(D5): 사이트 제휴 몰 시드 3개(쿠팡 파트너스 TEMPLATE · 11번가 QUERY · 알리 TEMPLATE 꺼짐 — 모두 **가짜 값**). 글의 `affiliateKind` 는 읽을 때 지금 방식으로 계산 —
//     `SITE` 면 최종 호스트(없으면 상품 호스트)가 켠 몰이면 SITE, 아니면 null. 전환 영향 = 진행 중(ACTIVE·FLAGGED) 글 중 개인 제휴 N · 그중 켠 몰 M · 켠 몰로 가는 전체.
//     시드의 진행 중 개인 제휴 글 3건(쿠팡 2 · 알리 1) → 처음 값으로 N 3 · M 2.

import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type {
    AdminDealDetail,
    AdminDealPage,
    AdminDealQuery,
    AdminDealRow,
    DealEndedBy,
    DealPriceBasis,
    DealRevision,
    DealRevisionChange,
    DealRevisionListView,
    DealStock,
    DealView,
    DealViewFilter,
    HotdealBoardOption,
    HotdealBoardOptionsView,
    SameDealRef,
} from '~/composables/hotdeal/api/useHotdealApi.types';
import type { DealDomain, DealDomainImpact, DealDomainListView, DealDomainRequest, DomainRuleFilter } from '~/composables/hotdeal/api/useHotdealDomainApi.types';
import type {
    DealSuspicion,
    DealSuspicionDetail,
    DealSuspicionKindFilter,
    DealSuspicionMember,
    DealSuspicionPage,
    DealSuspicionPost,
    DealSuspicionStatus,
    DealStoreSuspicionMember,
} from '~/composables/hotdeal/api/useHotdealSuspicionApi.types';
import type { HotdealAffiliateImpact, HotdealSettingsRequest, HotdealSettingsView } from '~/composables/hotdeal/api/useHotdealSettingsApi.types';
import type { SiteAffiliateListView, SiteAffiliateStore, SiteAffiliateStoreRequest } from '~/composables/hotdeal/api/useHotdealSiteAffiliateApi.types';
import type { AffiliateMode, PostDeal } from '~/composables/hotdeal/api/useHotdealApi.types';
import UtilHotdeal from '~/utils/util-hotdeal';

const HOUR = 60 * 60 * 1000;
const DAY = 24 * HOUR;
const at = (ms: number) => new Date(Date.now() + ms).toISOString();
const ME = '관리자(나)';
const MANAGER = '운영자 김핫딜';

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

const MEMBERS: readonly DealSuspicionMember[] = [
    { id: '3101', nickname: '로봇덕후', withdrawn: false },
    { id: '3102', nickname: '알뜰공돌이', withdrawn: false },
    { id: '3103', nickname: '납땜장인', withdrawn: false },
    { id: '3104', nickname: '할인헌터', withdrawn: false },
    { id: '3105', nickname: '딥러닝초보', withdrawn: false },
    { id: '3106', nickname: '메이커맘', withdrawn: false },
    { id: '3107', nickname: '탈퇴한회원', withdrawn: true },
    { id: '3108', nickname: '부품수집가', withdrawn: false },
    { id: '3109', nickname: '첫핫딜', withdrawn: false },
];

const boardRef = (i: 0 | 1) => {
    const b = BOARDS[i]!;
    return { id: b.id, slug: b.slug, name: b.name, nameEn: b.nameEn };
};

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
    /** 딜 칸 수정 이력(최근 것부터) */
    revisions: DealRevision[];
}

/** 수정 이력 시드 한 줄 — 몇 시간 전 · 누가 · 바뀐 칸 [칸, 전, 후] · 호스트 바뀜 */
interface RevSeed {
    agoH: number;
    by?: 'AUTHOR' | 'MANAGER';
    changes: [DealRevisionChange['field'], DealRevisionChange['before'], DealRevisionChange['after']][];
    hostChanged?: boolean;
}

interface Seed {
    title: string;
    board: 0 | 1;
    prefix: number | null;
    store: string;
    host: string;
    path: string;
    /** 리디렉션 뒤 최종 호스트(단축·제휴 링크) */
    final?: string;
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
    keeps?: number;
    reports?: number;
    affiliate?: boolean;
    noImage?: boolean;
    coupon?: string;
    basisKind?: DealPriceBasis;
    basis?: string;
    duty?: boolean;
    likes?: number;
    revs?: RevSeed[];
}

const SEEDS: Seed[] = [
    {
        title: '라즈베리파이 5 8GB 스타터 키트 역대가',
        board: 0,
        prefix: 0,
        store: '쿠팡',
        host: 'coupang.com',
        path: '/vp/products/8123',
        deal: 128000,
        normal: 159000,
        ship: 0,
        stock: 'IN_STOCK',
        ageH: 2,
        author: 0,
        color: '#c2410c',
        endsInH: 46,
        likes: 14,
        revs: [{ agoH: 1, changes: [['dealPrice', 131000, 128000]] }],
    },
    {
        title: '서보모터 MG996R 10개 묶음',
        board: 0,
        prefix: 0,
        store: '11번가',
        host: '11st.co.kr',
        path: '/products/5531?prdNo=5531&utm_source=share',
        deal: 23900,
        normal: 32000,
        ship: 3000,
        stock: 'LOW',
        ageH: 5,
        author: 1,
        color: '#dc2626',
        reqs: [2, 1, 0],
        flaggedAgoH: 3,
        revs: [
            {
                agoH: 3.5,
                changes: [
                    ['dealPrice', 21900, 23900],
                    ['stockState', 'IN_STOCK', 'LOW'],
                ],
            },
        ],
    },
    { title: 'ESP32-S3 개발보드 2+1 (알리)', board: 1, prefix: 0, store: '알리익스프레스', host: 'aliexpress.com', path: '/item/1005', deal: 9800, normal: 15000, ship: null, stock: 'UNKNOWN', ageH: 8, author: 2, color: '#ea580c', coupon: '앱 전용 쿠폰 적용가', basisKind: 'OVERSEAS', duty: true, affiliate: true },
    { title: '3D 프린터 필라멘트 PLA 1kg 4롤', board: 0, prefix: 2, store: 'G마켓', host: 'gmarket.co.kr', path: '/item?goodscode=2201', deal: 39900, normal: 56000, ship: 0, stock: 'IN_STOCK', ageH: 11, author: 5, color: '#16a34a', endsInH: -6 },
    {
        title: '젯슨 오린 나노 슈퍼 개발자 키트 품절 임박',
        board: 0,
        prefix: 1,
        store: '디바이스마트',
        host: 'devicemart.co.kr',
        path: '/goods/view?no=1',
        deal: 389000,
        normal: 429000,
        ship: 0,
        stock: 'LOW',
        ageH: 14,
        author: 4,
        color: '#0f766e',
        reqs: [3, 1, 0],
        flaggedAgoH: 6,
        keeps: 2,
        keptAgoH: 9,
        reports: 1,
        revs: [
            { agoH: 9, changes: [['dealPrice', 379000, 389000]] },
            { agoH: 12, changes: [['dealPrice', 369000, 379000], ['stockState', 'IN_STOCK', 'LOW']] },
        ],
    },
    { title: '로봇청소기 부품 세트(사이드 브러시 6개)', board: 0, prefix: 3, store: '쿠팡', host: 'link.coupang.com', path: '/a/bX2', final: 'coupang.com', deal: 12900, normal: 19900, ship: 0, stock: 'IN_STOCK', ageH: 20, author: 3, color: '#b45309', affiliate: true },
    { title: '아두이노 우노 R4 WiFi 정품', board: 0, prefix: 2, store: '엘레파츠', host: 'eleparts.co.kr', path: '/goods/view?no=77', deal: 41000, normal: 48000, ship: 3500, stock: 'IN_STOCK', ageH: 26, author: 6, color: '#0369a1', ended: 'AUTHOR', endedAgoH: 4 },
    { title: '리튬 배터리 18650 8개', board: 0, prefix: 3, store: '배터리몰', host: 'smartstore.naver.com', path: '/batterymall/products/3', deal: 21000, normal: null, ship: 3000, stock: 'SOLD_OUT', ageH: 30, author: 3, color: '#15803d', ended: 'AUTO', endedAgoH: 10, reqs: [4, 0, 1] },
    {
        title: 'AI 스피커 개발 키트 반값',
        board: 0,
        prefix: 1,
        store: '메이커스샵',
        host: 'makers-shop.kr',
        path: '/p/ai-kit',
        deal: 59000,
        normal: 118000,
        ship: 0,
        stock: 'UNKNOWN',
        ageH: 33,
        author: 3,
        color: '#7c3aed',
        reports: 2,
        basisKind: 'LISTED',
        basis: '정가 대비(공식몰 표기)',
        revs: [
            {
                agoH: 30,
                changes: [
                    ['productUrl', 'https://smartstore.naver.com/makerskit/products/71', 'https://makers-shop.kr/p/ai-kit'],
                    ['storeName', '스마트스토어', '메이커스샵'],
                    ['dealPrice', 64000, 59000],
                ],
                hostChanged: true,
            },
        ],
    },
    { title: 'AI 스피커 개발 키트 + 확장보드', board: 0, prefix: 1, store: '메이커스샵', host: 'makers-shop.kr', path: '/p/ai-kit-plus', deal: 69000, normal: 128000, ship: 0, stock: 'UNKNOWN', ageH: 52, author: 3, color: '#6d28d9' },
    { title: '라즈베리파이 5 8GB 스타터 키트', board: 0, prefix: 0, store: '쿠팡', host: 'coupang.com', path: '/vp/products/8123', deal: 131000, normal: 159000, ship: 0, stock: 'IN_STOCK', ageH: 60, author: 1, color: '#c2410c' },
    { title: '드론 프레임 키트 F450 (해외 직배송)', board: 1, prefix: 0, store: '아마존', host: 'amazon.com', path: '/dp/B0F450', deal: 45000, normal: 62000, ship: 8000, stock: 'IN_STOCK', ageH: 72, author: 0, color: '#1d4ed8', endsInH: 30, basisKind: 'OVERSEAS', duty: false },
    { title: '납땜 인두 스테이션 정밀형', board: 0, prefix: 2, store: '옥션', host: 'auction.co.kr', path: '/itemPage?no=99', deal: 54000, normal: 69000, ship: 0, stock: 'IN_STOCK', ageH: 80, author: 2, color: '#9a3412', ended: 'MANAGER', endedAgoH: 20 },
    { title: '스텝모터 드라이버 TMC2209 5개', board: 0, prefix: 0, store: '알리익스프레스', host: 'aliexpress.com', path: '/item/1007', deal: 14500, normal: 21000, ship: null, stock: 'UNKNOWN', ageH: 96, author: 2, color: '#c2410c', noImage: true },
    { title: '로봇 팔 키트 6축 학습용', board: 0, prefix: 0, store: '메이커스샵', host: 'makers-shop.kr', path: '/p/arm6', deal: 99000, normal: 149000, ship: 0, stock: 'IN_STOCK', ageH: 110, author: 3, color: '#5b21b6', endsInH: -30 },
    {
        title: '오실로스코프 입문형 할인',
        board: 0,
        prefix: 2,
        store: '11번가',
        host: '11st.co.kr',
        path: '/products/9001?prdNo=9001',
        deal: 219000,
        normal: 259000,
        ship: 0,
        stock: 'IN_STOCK',
        ageH: 130,
        author: 4,
        color: '#b91c1c',
        reqs: [1, 0, 0],
        keptAgoH: 40,
        keeps: 1,
        basisKind: 'CARD',
        basis: '삼성카드 7% 청구 할인 적용',
        revs: [
            { agoH: 40, changes: [['stockState', 'LOW', 'IN_STOCK']] },
            { agoH: 100, by: 'MANAGER', changes: [['endsAt', null, at(10 * DAY)]] },
        ],
    },
    { title: '라이다 센서 LD19 특가', board: 1, prefix: 0, store: '알리익스프레스', host: 'aliexpress.com', path: '/item/1009', deal: 52000, normal: 71000, ship: null, stock: 'LOW', ageH: 150, author: 0, color: '#ea580c', ended: 'AUTHOR', endedAgoH: 70 },
    { title: 'AI 비전 카메라 모듈 2개', board: 0, prefix: 1, store: '디바이스마트', host: 'devicemart.co.kr', path: '/goods/view?no=8', deal: 33000, normal: null, ship: 3000, stock: 'IN_STOCK', ageH: 170, author: 5, color: '#0d9488' },
    { title: 'CNC 조각기 3018 프로', board: 0, prefix: 2, store: 'G마켓', host: 'gmarket.co.kr', path: '/item?goodscode=3018', deal: 189000, normal: 239000, ship: 0, stock: 'UNKNOWN', ageH: 200, author: 6, color: '#166534', endsInH: -100 },
    { title: '점퍼 케이블 세트 120개', board: 0, prefix: 3, store: '쿠팡', host: 'coupang.com', path: '/vp/products/120', deal: 4900, normal: 8900, ship: 0, stock: 'IN_STOCK', ageH: 230, author: 1, color: '#a16207', likes: 23 },
    { title: '서보 제어 보드 PCA9685 3개', board: 0, prefix: 0, store: '엘레파츠', host: 'eleparts.co.kr', path: '/goods/view?no=9685', deal: 11000, normal: 15000, ship: 3500, stock: 'IN_STOCK', ageH: 260, author: 2, color: '#075985' },
    { title: '해외 직구 그리퍼 키트', board: 1, prefix: 0, store: '아마존', host: 'amazon.com', path: '/dp/B0GRIP', deal: 27000, normal: 35000, ship: 8000, stock: 'UNKNOWN', ageH: 300, author: 4, color: '#1e40af', ended: 'AUTO', endedAgoH: 200, reqs: [3, 0, 0] },
    // ↓ 보강 목업 — 차단 도메인 진행 중 · 링크 바뀜 · 쿠팡 단축(제휴) · 판매처 후보 · 기간 종료
    { title: '치프로봇 서보 20개 묶음 초특가', board: 0, prefix: 0, store: '치프로봇', host: 'cheap-robot.shop', path: '/goods/servo20', deal: 19000, normal: 60000, ship: 2500, stock: 'IN_STOCK', ageH: 7, author: 2, color: '#be123c', basisKind: 'LISTED', basis: '판매처 정가 기준' },
    {
        title: '스마트 로봇 키트 땡처리',
        board: 0,
        prefix: 0,
        store: '치프로봇',
        host: 'cheap-robot.shop',
        path: '/goods/kit-sale',
        deal: 29000,
        normal: 89000,
        ship: 0,
        stock: 'IN_STOCK',
        ageH: 18,
        author: 4,
        color: '#9f1239',
        reqs: [2, 1, 0],
        flaggedAgoH: 2,
        reports: 1,
        revs: [
            {
                agoH: 4,
                changes: [
                    ['productUrl', 'https://www.11st.co.kr/products/7788?prdNo=7788', 'https://cheap-robot.shop/goods/kit-sale'],
                    ['storeName', '11번가', '치프로봇'],
                ],
                hostChanged: true,
            },
            { agoH: 10, changes: [['dealPrice', 32000, 29000], ['normalPrice', 79000, 89000]] },
        ],
    },
    { title: '로봇 키트 쿠팡 단축 링크 특가', board: 0, prefix: 0, store: '쿠팡', host: 'coupa.ng', path: '/bQx7', final: 'coupang.com', deal: 45900, normal: 59900, ship: 0, stock: 'IN_STOCK', ageH: 9, author: 7, color: '#c2410c', affiliate: true },
    { title: '로보아울렛 교육용 휴머노이드 키트', board: 0, prefix: 0, store: '로보아울렛', host: 'robo-outlet.kr', path: '/p/humanoid', deal: 129000, normal: 199000, ship: 0, stock: 'IN_STOCK', ageH: 15, author: 7, color: '#4338ca' },
    { title: '로보아울렛 라인트레이서 세트', board: 0, prefix: 0, store: '로보아울렛', host: 'robo-outlet.kr', path: '/p/linetracer', deal: 39000, normal: 55000, ship: 3000, stock: 'IN_STOCK', ageH: 40, author: 8, color: '#3730a3' },
    { title: '로보아울렛 코딩 로봇 2대', board: 0, prefix: 3, store: '로보아울렛', host: 'robo-outlet.kr', path: '/p/coding2', deal: 58000, normal: 84000, ship: 0, stock: 'LOW', ageH: 90, author: 5, color: '#312e81' },
    { title: '멀티미터 자동 범위형 (마감 없음)', board: 0, prefix: 2, store: 'G마켓', host: 'gmarket.co.kr', path: '/item?goodscode=777', deal: 18900, normal: 25000, ship: 0, stock: 'UNKNOWN', ageH: 33 * 24, author: 1, color: '#15803d' },
];

/** 수정 이력이 가격 수정으로 세는 칸 */
const PRICE_FIELDS = new Set<DealRevisionChange['field']>(['dealPrice', 'normalPrice']);

function buildRevisions(seed: Seed, postId: string, author: string): DealRevision[] {
    return (seed.revs ?? [])
        .map((r, i) => ({
            id: `${postId}-r${i + 1}`,
            at: at(-r.agoH * HOUR),
            actor: r.by === 'MANAGER' ? { kind: 'MANAGER' as const, name: MANAGER } : { kind: 'AUTHOR' as const, name: author },
            changes: r.changes.map(([field, before, after]) => ({ field, before, after })),
            hostChanged: r.hostChanged === true,
        }))
        .sort((a, b) => Date.parse(b.at) - Date.parse(a.at));
}

function build(seed: Seed, i: number): StoredDeal {
    const member = MEMBERS[seed.author]!;
    const createdAt = at(-seed.ageH * HOUR);
    const [s, p, l] = seed.reqs ?? [0, 0, 0];
    const flaggedAt = seed.flaggedAgoH !== undefined ? at(-seed.flaggedAgoH * HOUR) : null;
    const discount = seed.normal && seed.normal > seed.deal ? Math.floor(((seed.normal - seed.deal) / seed.normal) * 100) : null;
    const productUrl = `https://${seed.host}${seed.path}`;
    const postId = String(70100 + i);
    const endedAt = seed.ended ? at(-(seed.endedAgoH ?? 1) * HOUR) : null;
    return {
        productKey: productUrl.replace(/[?&]utm_[^&]*/g, ''),
        endedByName: seed.ended === 'AUTHOR' ? member.nickname : seed.ended === 'MANAGER' ? MANAGER : null,
        endReason: seed.ended === 'MANAGER' ? '판매처에서 행사가 끝났어요(가격 확인).' : null,
        keptAt: seed.keptAgoH !== undefined ? at(-seed.keptAgoH * HOUR) : null,
        revisions: buildRevisions(seed, postId, member.nickname),
        row: {
            postId,
            title: seed.title,
            board: boardRef(seed.board),
            prefix: seed.prefix === null ? null : (BOARDS[seed.board]!.prefixes[seed.prefix] ?? null),
            author: { id: member.id, nickname: member.nickname, withdrawn: member.withdrawn },
            createdAt,
            openReports: seed.reports ?? 0,
            blockedHost: null,
            deal: {
                source: 'MEMBER',
                storeName: seed.store,
                productUrl,
                productHost: seed.host,
                finalHost: seed.final ?? null,
                normalPrice: seed.normal,
                dealPrice: seed.deal,
                discountRate: discount,
                shippingFee: seed.ship,
                couponNote: seed.coupon ?? null,
                priceBasisKind: seed.basisKind ?? null,
                dutyIncluded: seed.duty === true,
                priceBasis: seed.basis ?? null,
                endsAt: seed.endsInH === undefined || seed.endsInH === null ? null : at(seed.endsInH * HOUR),
                autoExpireAt: null,
                stockState: seed.stock,
                affiliate: seed.affiliate === true,
                image: seed.noImage ? null : { url: svg(seed.store.slice(0, 1), seed.color), source: 'OG' },
                status: seed.ended ? 'ENDED' : 'ACTIVE',
                view: 'ACTIVE',
                endedBy: seed.ended ?? null,
                endedAt,
                // 작성자 종료 + 24시간(D2) — 시드는 아직 되돌리기를 쓰지 않았다
                reopenUntil: seed.ended === 'AUTHOR' && endedAt ? new Date(Date.parse(endedAt) + UtilHotdeal.AUTHOR_REOPEN_HOURS * HOUR).toISOString() : null,
                endRequests: { total: s + p + l, bySoldOut: s, byPriceChanged: p, byLinkDead: l, mine: null },
                flaggedAt,
                autoEndAt: null,
                keepCount: seed.keeps ?? 0,
                keepMax: 0,
                revision: { priceEdits: 0, linkChanged: false, lastEditedAt: null },
                hot: false,
                sameDealCount: 0,
                affiliateKind: null,
                siteProgramName: null,
            },
        },
    };
}

let deals: StoredDeal[] = SEEDS.map(build);
const LIKES = new Map(SEEDS.map((s, i) => [String(70100 + i), s.likes ?? 0]));

/** `?mock=link-changed` 가 이력 맨 위에 더하는 「링크 바뀜」 줄 */
function syntheticLinkChange(d: StoredDeal): DealRevision {
    const deal = d.row.deal;
    return {
        id: `${d.row.postId}-link`,
        at: at(-20 * 60 * 1000),
        actor: { kind: 'AUTHOR', name: d.row.author.nickname },
        changes: [
            { field: 'productUrl', before: 'https://www.coupang.com/vp/products/1', after: deal.productUrl },
            { field: 'storeName', before: '쿠팡', after: deal.storeName },
        ],
        hostChanged: true,
    };
}

function revisionsOf(d: StoredDeal, on: Set<string>): DealRevision[] {
    if (on.has('revisions-empty')) return [];
    return on.has('link-changed') ? [syntheticLinkChange(d), ...d.revisions] : d.revisions;
}

/** 지금 규칙에서 차단된 호스트(상품·최종 중 먼저 걸린 것) */
function blockedHostOf(hosts: readonly (string | null)[]): string | null {
    for (const h of hosts) {
        if (h && UtilHotdeal.match(h, domains).match?.rule === 'BLOCK') return h;
    }
    return null;
}

/** 읽을 때 계산하는 칸(상태 · 자동 종료 예정 · 기간 종료 · 차단 · 수정 요약 · 같은 딜 수)을 채운 사본 */
function view(d: StoredDeal, on: Set<string> = mockFlags()): AdminDealRow {
    const row = mockClone(d.row);
    const deal = row.deal;
    const now = Date.now();
    deal.autoExpireAt = !deal.endsAt && settings.autoExpireDays > 0 ? new Date(Date.parse(row.createdAt) + settings.autoExpireDays * DAY).toISOString() : null;
    const deadline = deal.endsAt ?? deal.autoExpireAt;
    let v: DealView = 'ACTIVE';
    if (deal.status === 'ENDED') v = 'ENDED';
    else if (deadline && Date.parse(deadline) <= now) v = 'EXPIRED';
    else if (deal.flaggedAt) v = 'FLAGGED';
    deal.view = v;
    deal.autoEndAt = v === 'FLAGGED' && deal.flaggedAt && settings.endGraceHours > 0 ? new Date(Date.parse(deal.flaggedAt) + settings.endGraceHours * HOUR).toISOString() : null;
    deal.keepMax = settings.keepMaxCount;
    deal.hot = (LIKES.get(row.postId) ?? 0) >= settings.hotLikeThreshold;
    const revs = revisionsOf(d, on);
    deal.revision = {
        priceEdits: revs.filter((r) => r.changes.some((c) => PRICE_FIELDS.has(c.field))).length,
        linkChanged: revs.some((r) => r.hostChanged),
        lastEditedAt: revs[0]?.at ?? null,
    };
    row.blockedHost = v === 'ACTIVE' || v === 'FLAGGED' ? blockedHostOf([deal.productHost, deal.finalHost]) : null;
    deal.sameDealCount = deals.filter((o) => o !== d && o.productKey === d.productKey && o.row.deal.status === 'ACTIVE').length;
    // 구매 링크 제휴 종류 — 지금 방식으로 계산(저장된 링크는 그대로, D5)
    if (settingsNow().affiliateMode === 'SITE') {
        const store = siteStoreOf(deal);
        deal.affiliateKind = store ? 'SITE' : null;
        deal.siteProgramName = store?.programName ?? null;
    } else {
        deal.affiliateKind = deal.affiliate ? 'MEMBER' : null;
        deal.siteProgramName = null;
    }
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
    const empty = { total: 0, page: query.page, size: query.size, items: [], counts: { ACTIVE: 0, FLAGGED: 0, ENDED: 0, EXPIRED: 0, ALL: 0 }, blockedActiveCount: 0 };
    if (on.has('empty')) return empty;
    const q = (query.q ?? '').trim().toLowerCase();
    const from = query.createdFrom ? Date.parse(query.createdFrom) : null;
    const to = query.createdTo ? Date.parse(query.createdTo) : null;
    const base = deals
        .map((d) => view(d, on))
        .filter((r) => !query.board || r.board.id === query.board)
        .filter((r) => !q || r.title.toLowerCase().includes(q) || r.deal.storeName.toLowerCase().includes(q) || r.author.nickname.toLowerCase().includes(q))
        .filter((r) => (from === null || Date.parse(r.createdAt) >= from) && (to === null || Date.parse(r.createdAt) <= to))
        .sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt));
    const blockedActiveCount = base.filter((r) => r.blockedHost).length;
    const rows = query.blockedActive ? base.filter((r) => r.blockedHost) : base;
    const counts = { ...empty.counts, ALL: rows.length };
    for (const r of rows) counts[r.deal.view] += 1;
    const picked = query.view === 'ALL' ? rows : rows.filter((r) => r.deal.view === (query.view as DealViewFilter));
    const start = query.page * query.size;
    return { total: picked.length, page: query.page, size: query.size, items: picked.slice(start, start + query.size), counts, blockedActiveCount };
}

export async function mockHotdealDetail(postId: string): Promise<AdminDealDetail> {
    const on = await gate('detail-error');
    const d = findDeal(postId);
    const row = view(d, on);
    const sameDeals: SameDealRef[] = deals
        .filter((o) => o !== d && o.productKey === d.productKey && o.row.deal.status === 'ACTIVE')
        .map((o) => view(o, on))
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
        affiliateMode: settingsNow().affiliateMode,
    };
}

export async function mockHotdealRevisions(postId: string): Promise<DealRevisionListView> {
    const on = await gate('revisions-error');
    const d = findDeal(postId);
    return { items: mockClone(revisionsOf(d, on)), retentionMonths: 6 };
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
    d.row.deal.reopenUntil = null;
    d.endedByName = ME;
    d.endReason = reason?.trim() || null;
}

export async function mockHotdealReopen(postId: string, reason?: string): Promise<void> {
    await gateAction();
    checkReason(reason);
    const d = findDeal(postId);
    if (d.row.deal.status !== 'ENDED') throw stateChanged();
    // 재개는 운영진만 — 의심·종료 요청·keep 횟수를 리셋한다(계약 「권한·정책」)
    Object.assign(d.row.deal, {
        status: 'ACTIVE',
        endedBy: null,
        endedAt: null,
        reopenUntil: null,
        flaggedAt: null,
        keepCount: 0,
        endRequests: { total: 0, bySoldOut: 0, byPriceChanged: 0, byLinkDead: 0, mine: null },
    });
    d.endedByName = null;
    d.endReason = null;
    d.keptAt = null;
}

/** 신고 사건의 [핫딜 종료](`action: DEAL_END`) 목업이 부른다 — 이 목업에 있는 글이면 운영진 종료로 바꾼다(없으면 아무것도 하지 않는다) */
export function mockHotdealEndFromReport(postId: string | null, note: string): void {
    const d = postId ? deals.find((x) => x.row.postId === postId) : undefined;
    if (!d || d.row.deal.status === 'ENDED') return;
    Object.assign(d.row.deal, { status: 'ENDED', endedBy: 'MANAGER', endedAt: new Date().toISOString(), reopenUntil: null });
    d.endedByName = ME;
    d.endReason = note.slice(0, UtilHotdeal.REASON_MAX) || null;
}

// ── 링크 규칙 ─

const SHORTENERS = ['bit.ly', 'han.gl', 'vo.la', 'me2.do', 't.co', 'goo.gl', 'tinyurl.com', 'url.kr'];
/** 대형몰 — 같은 딜 판정 쿼리(`keyParams`)는 목업 값 */
const COMMON: [string, string[]][] = [
    ['coupang.com', ['itemId', 'vendorItemId']],
    ['11st.co.kr', ['prdNo']],
    ['gmarket.co.kr', ['goodscode']],
    ['auction.co.kr', ['itemno']],
    ['smartstore.naver.com', []],
    ['aliexpress.com', []],
    ['amazon.com', []],
];
/** 제휴 네트워크(목업 값 — 서버 시드에서 실제 도메인을 확인한다, 05 §4-2) */
const AFFILIATE_NETWORKS: [string, string][] = [
    ['linkprice.com', '제휴 네트워크 — 링크프라이스(목업 값)'],
    ['adpick.co.kr', '제휴 네트워크 — 애드픽(목업 값)'],
    ['tenping.kr', '제휴 네트워크 — 텐핑(목업 값)'],
];

let domainSeq = 500;
let domains: DealDomain[] = [
    ...SHORTENERS.map((domain) => ({ id: String(++domainSeq), domain, rule: 'BLOCK' as const, common: false, keyParams: [], note: '단축 주소', updatedBy: null, updatedAt: at(-30 * DAY) })),
    ...COMMON.map(([domain, keyParams]) => ({ id: String(++domainSeq), domain, rule: null, common: true, keyParams, note: '대형몰', updatedBy: null, updatedAt: at(-30 * DAY) })),
    ...AFFILIATE_NETWORKS.map(([domain, note]) => ({ id: String(++domainSeq), domain, rule: 'AFFILIATE' as const, common: false, keyParams: [], note, updatedBy: null, updatedAt: at(-30 * DAY) })),
    { id: String(++domainSeq), domain: 'coupa.ng', rule: 'AFFILIATE', common: false, keyParams: [], note: '쿠팡 파트너스 단축 — 공식 여부 확인 필요', updatedBy: null, updatedAt: at(-30 * DAY) },
    { id: String(++domainSeq), domain: 'link.coupang.com', rule: 'AFFILIATE', common: true, keyParams: [], note: '쿠팡 파트너스 링크', updatedBy: MANAGER, updatedAt: at(-3 * DAY) },
    { id: String(++domainSeq), domain: 'cheap-robot.shop', rule: 'BLOCK', common: false, keyParams: [], note: '위장 홍보 확인 — 차단', updatedBy: MANAGER, updatedAt: at(-1 * DAY) },
];

export async function mockDomainList(rule: DomainRuleFilter, q: string): Promise<DealDomainListView> {
    const on = await gate('error');
    const affiliateMode = settingsNow().affiliateMode;
    if (on.has('empty')) return { items: [], affiliateMode };
    const items = UtilHotdeal.filterDomains(domains, rule, q).sort((a, b) => a.domain.localeCompare(b.domain));
    return { items: mockClone(items), affiliateMode };
}

export async function mockDomainImpact(raw: string): Promise<DealDomainImpact> {
    const on = await gate();
    const domain = UtilHotdeal.normalizeDomain(raw);
    if (!domain) throw MOCK_ERRORS.validation({ domain: '도메인 형식을 확인해 주세요. 예: coupang.com' });
    if (on.has('impact-zero')) return { domain, activeCount: 0, samples: [] };
    const hit = (h: string | null) => h !== null && (h === domain || h.endsWith(`.${domain}`));
    const rows = deals
        .map((d) => view(d, on))
        .filter((r) => (r.deal.view === 'ACTIVE' || r.deal.view === 'FLAGGED') && (hit(r.deal.productHost) || hit(r.deal.finalHost)))
        .sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt));
    return { domain, activeCount: rows.length, samples: rows.slice(0, 3).map((r) => ({ postId: r.postId, title: r.title, storeName: r.deal.storeName })) };
}

function checkDomain(body: DealDomainRequest, selfId: string | null, on: Set<string>): string {
    const domain = UtilHotdeal.normalizeDomain(body.domain);
    const fields: Record<string, string> = {};
    if (!domain) fields.domain = '도메인 형식을 확인해 주세요. 예: coupang.com';
    if (body.rule === null && !body.common) fields.common = '규칙이 없으면 대형몰이어야 해요. 차단·제휴를 고르거나 대형몰을 켜 주세요.';
    if (body.keyParams.length > UtilHotdeal.KEY_PARAMS_MAX) fields.keyParams = `같은 딜 판정 쿼리는 ${UtilHotdeal.KEY_PARAMS_MAX}개까지예요.`;
    else if (body.keyParams.some((k) => UtilHotdeal.isTrackingParam(k))) fields.keyParams = '추적 파라미터(utm_* · fbclid · ref …)는 늘 지워서 남길 수 없어요.';
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
    const item: DealDomain = { id: String(++domainSeq), domain, rule: body.rule, common: body.common, keyParams: [...body.keyParams], note: body.note?.trim() || null, updatedBy: ME, updatedAt: new Date().toISOString() };
    domains = [...domains, item];
    return mockClone(item);
}

export async function mockDomainUpdate(id: string, body: DealDomainRequest): Promise<DealDomain> {
    const on = await gate();
    if (on.has('forbidden')) throw MOCK_ERRORS.forbidden();
    const found = domains.find((d) => d.id === id);
    if (!found) throw MOCK_ERRORS.notFound('HOTDEAL_DOMAIN_NOT_FOUND', '규칙을 찾을 수 없어요. 이미 지워졌을 수 있어요.');
    const domain = checkDomain(body, id, on);
    Object.assign(found, { domain, rule: body.rule, common: body.common, keyParams: [...body.keyParams], note: body.note?.trim() || null, updatedBy: ME, updatedAt: new Date().toISOString() });
    return mockClone(found);
}

export async function mockDomainDelete(id: string): Promise<void> {
    const on = await gate();
    if (on.has('forbidden')) throw MOCK_ERRORS.forbidden();
    if (!domains.some((d) => d.id === id)) throw MOCK_ERRORS.notFound('HOTDEAL_DOMAIN_NOT_FOUND', '규칙을 찾을 수 없어요. 이미 지워졌을 수 있어요.');
    domains = domains.filter((d) => d.id !== id);
}

// ── 의심 후보 ─

const member = (i: number): DealSuspicionMember => ({ ...MEMBERS[i]! });
const OPEN_FIELDS = { status: 'OPEN' as const, dismissedBy: null, dismissedAt: null, note: null };

let suspicions: DealSuspicion[] = [
    { id: 's-1', kind: 'MEMBER', member: member(3), host: 'makers-shop.kr', storeName: '메이커스샵', postCount: 5, othersCount: 0, windowDays: 7, firstDetectedAt: at(-2 * DAY), lastPostAt: at(-33 * HOUR), ...OPEN_FIELDS },
    { id: 's-2', kind: 'MEMBER', member: member(2), host: 'eleparts.co.kr', storeName: '엘레파츠', postCount: 3, othersCount: 1, windowDays: 7, firstDetectedAt: at(-4 * DAY), lastPostAt: at(-3 * DAY), ...OPEN_FIELDS },
    { id: 's-3', kind: 'MEMBER', member: member(6), host: 'cheap-robot.shop', storeName: '치프로봇', postCount: 4, othersCount: 0, windowDays: 7, firstDetectedAt: at(-6 * DAY), lastPostAt: at(-5 * DAY), ...OPEN_FIELDS },
    {
        id: 's-4',
        kind: 'MEMBER',
        member: member(4),
        host: 'devicemart.co.kr',
        storeName: '디바이스마트',
        postCount: 3,
        othersCount: 4,
        windowDays: 7,
        firstDetectedAt: at(-12 * DAY),
        lastPostAt: at(-8 * DAY),
        status: 'DISMISSED',
        dismissedBy: MANAGER,
        dismissedAt: at(-7 * DAY),
        note: '여러 회원이 같이 올리는 큰 부품몰이라 홍보로 보기 어려움',
    },
    { id: 'st-1', kind: 'STORE', host: 'robo-outlet.kr', storeName: '로보아울렛', postCount: 7, memberCount: 4, otherRatioPercent: 8, windowDays: 30, firstDetectedAt: at(-3 * DAY), lastPostAt: at(-15 * HOUR), ...OPEN_FIELDS },
    { id: 'st-2', kind: 'STORE', host: 'gadget-mall.co.kr', storeName: '가젯몰', postCount: 4, memberCount: 3, otherRatioPercent: 15, windowDays: 30, firstDetectedAt: at(-5 * DAY), lastPostAt: at(-2 * DAY), ...OPEN_FIELDS },
    {
        id: 'st-3',
        kind: 'STORE',
        host: 'eleparts.co.kr',
        storeName: '엘레파츠',
        postCount: 6,
        memberCount: 5,
        otherRatioPercent: 18,
        windowDays: 30,
        firstDetectedAt: at(-20 * DAY),
        lastPostAt: at(-10 * DAY),
        status: 'DISMISSED',
        dismissedBy: MANAGER,
        dismissedAt: at(-9 * DAY),
        note: '부품 전문몰이라 여러 회원이 원래 자주 올림',
    },
];

/** 판매처 후보 목업 글의 작성자 차례(실제 시드 글 + 채움 글) */
const STORE_FILLER_AUTHORS: Record<string, number[]> = { 'st-1': [7, 8, 5, 0, 7], 'st-2': [1, 3, 8, 3], 'st-3': [2, 5, 6, 0, 1, 2] };

export async function mockSuspicionList(status: DealSuspicionStatus, kind: DealSuspicionKindFilter, page: number, size: number): Promise<DealSuspicionPage> {
    const on = await gate('error');
    const counts: Record<DealSuspicionStatus, number> = { OPEN: 0, DISMISSED: 0 };
    const kindCounts: Record<DealSuspicionKindFilter, number> = { ALL: 0, MEMBER: 0, STORE: 0 };
    if (on.has('empty')) return { total: 0, page, size, items: [], counts, kindCounts };
    const pool = on.has('store-suspicion') ? suspicions.filter((s) => s.kind === 'STORE') : suspicions;
    for (const s of pool) if (kind === 'ALL' || s.kind === kind) counts[s.status] += 1;
    for (const s of pool) {
        if (s.status !== status) continue;
        kindCounts.ALL += 1;
        kindCounts[s.kind] += 1;
    }
    const picked = pool.filter((s) => s.status === status && (kind === 'ALL' || s.kind === kind)).sort((a, b) => Date.parse(b.lastPostAt) - Date.parse(a.lastPostAt));
    return { total: picked.length, page, size, items: mockClone(picked.slice(page * size, page * size + size)), counts, kindCounts };
}

function postOf(r: AdminDealRow): DealSuspicionPost {
    return { postId: r.postId, title: r.title, board: r.board, author: { id: r.author.id ?? '', nickname: r.author.nickname, withdrawn: r.author.withdrawn }, dealPrice: r.deal.dealPrice, view: r.deal.view, createdAt: r.createdAt, openReports: r.openReports };
}

export async function mockSuspicionDetail(id: string): Promise<DealSuspicionDetail> {
    const on = await gate('detail-error');
    const s = suspicions.find((x) => x.id === id);
    if (!s) throw MOCK_ERRORS.notFound('HOTDEAL_SUSPICION_NOT_FOUND', '의심 후보를 찾을 수 없어요.');
    const seq = Number(s.id.replace(/\D/g, '')) * 10 + (s.kind === 'STORE' ? 500 : 0);
    // 실제 글(판매처가 같은 것 — 회원 후보는 작성자도 같은 것) + 목업 글로 건수를 채운다
    const real = deals
        .map((d) => view(d, on))
        .filter((r) => r.deal.productHost === s.host && (s.kind === 'STORE' || r.author.id === s.member.id))
        .map(postOf);
    const authors = s.kind === 'STORE' ? (STORE_FILLER_AUTHORS[s.id] ?? [0]) : [];
    const filler: DealSuspicionPost[] = Array.from({ length: Math.max(0, s.postCount - real.length) }, (_, i) => ({
        postId: String(69000 + seq + i),
        title: `${s.storeName} 특가 모음 ${i + 1}탄`,
        board: boardRef(0),
        author: s.kind === 'STORE' ? member(authors[i % authors.length] ?? 0) : { ...s.member },
        dealPrice: 19000 + i * 7000,
        view: i === 0 ? 'ENDED' : 'ACTIVE',
        createdAt: new Date(Date.parse(s.lastPostAt) - (i + 1) * 20 * HOUR).toISOString(),
        openReports: i === 1 ? 1 : 0,
    }));
    const max = s.kind === 'STORE' ? UtilHotdeal.STORE_SUSPICION_POSTS : UtilHotdeal.SUSPICION_POSTS;
    const posts = [...real, ...filler].sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt)).slice(0, max);
    let members: DealStoreSuspicionMember[] = [];
    if (s.kind === 'STORE') {
        const byId = new Map<string, DealStoreSuspicionMember>();
        for (const p of posts) {
            const m = byId.get(p.author.id) ?? { member: p.author, postCount: 0, otherDealCount: deals.filter((d) => d.row.author.id === p.author.id && d.row.deal.productHost !== s.host).length };
            m.postCount += 1;
            byId.set(p.author.id, m);
        }
        members = [...byId.values()].sort((a, b) => b.postCount - a.postCount);
    }
    return { suspicion: mockClone(s), posts, members };
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
    ...(Object.fromEntries(UtilHotdeal.SETTINGS.map((s) => [s.field, s.initial])) as Omit<HotdealSettingsView, 'ogFetchEnabled' | 'affiliateMode' | 'rewardPoints' | 'rewardDailyLimit' | 'updatedAt' | 'updatedBy'>),
    ogFetchEnabled: true,
    affiliateMode: 'MEMBER',
    // 포인트 정책의 `dealEndRequestPoints`·`dealEndRequestDailyLimit`(읽기 전용)
    rewardPoints: 3,
    rewardDailyLimit: 5,
    updatedAt: null,
    updatedBy: null,
};
let settingsSeeded = false;

/** 지금 설정 — 처음 읽을 때 한 번 `?mock=site-affiliate`(·`site-affiliate-empty`)면 사이트 제휴로 시작한다 */
function settingsNow(): HotdealSettingsView {
    if (!settingsSeeded) {
        settingsSeeded = true;
        const on = mockFlags();
        if (on.has('site-affiliate') || on.has('site-affiliate-empty')) settings = { ...settings, affiliateMode: 'SITE' };
    }
    return settings;
}

export async function mockSettingsGet(): Promise<HotdealSettingsView> {
    await gate('error');
    return mockClone(settingsNow());
}

export async function mockSettingsSave(body: HotdealSettingsRequest): Promise<HotdealSettingsView> {
    const on = await gate('save-error');
    if (on.has('forbidden')) throw MOCK_ERRORS.forbidden();
    const fields: Record<string, string> = {};
    for (const spec of UtilHotdeal.SETTINGS) {
        const v = body[spec.field];
        if (!Number.isInteger(v) || v < spec.min || v > spec.max) fields[spec.field] = `${UtilHotdeal.groupDigits(spec.min)}~${UtilHotdeal.groupDigits(spec.max)} 사이의 정수로 입력해 주세요.`;
    }
    if (typeof body.ogFetchEnabled !== 'boolean') fields.ogFetchEnabled = '켬·끔 중 하나여야 해요.';
    if (!UtilHotdeal.AFFILIATE_MODES.includes(body.affiliateMode)) fields.affiliateMode = '개인 제휴·사이트 제휴 중 하나여야 해요.';
    if (Object.keys(fields).length) throw MOCK_ERRORS.validation(fields);
    settings = { ...settingsNow(), ...body, updatedAt: new Date().toISOString(), updatedBy: ME };
    return mockClone(settings);
}

export async function mockAffiliateImpact(mode: AffiliateMode): Promise<HotdealAffiliateImpact> {
    const on = await gate();
    if (on.has('forbidden')) throw MOCK_ERRORS.forbidden();
    if (!UtilHotdeal.AFFILIATE_MODES.includes(mode)) throw MOCK_ERRORS.validation({ mode: '개인 제휴·사이트 제휴 중 하나여야 해요.' });
    if (on.has('affiliate-impact-zero')) return { mode, memberAffiliateActive: 0, siteStoreMatched: 0, siteStoreActive: 0 };
    const active = deals.map((d) => view(d, on).deal).filter((deal) => deal.view === 'ACTIVE' || deal.view === 'FLAGGED');
    return {
        mode,
        memberAffiliateActive: active.filter((deal) => deal.affiliate).length,
        siteStoreMatched: active.filter((deal) => deal.affiliate && siteStoreOf(deal)).length,
        siteStoreActive: active.filter((deal) => siteStoreOf(deal)).length,
    };
}

// ── 사이트 제휴 몰(D5) ─

let siteSeq = 800;
/** 시드 — 모두 **가짜 값**(실제 제휴 프로그램의 링크 모양·코드는 서버 단계·법무 확인 뒤 운영자가 넣는다) */
let siteStores: SiteAffiliateStore[] = [
    { id: String(++siteSeq), domain: 'coupang.com', programName: '쿠팡 파트너스', transform: { type: 'TEMPLATE', template: 'https://link.coupang.com/re/MOCKDEEPLINK?lptag=AF0000000&pageKey={url}' }, enabled: true, updatedBy: MANAGER, updatedAt: at(-2 * DAY) },
    { id: String(++siteSeq), domain: '11st.co.kr', programName: '11번가 제휴(목업)', transform: { type: 'QUERY', query: 'partner=roboyam&pcode=RB0001' }, enabled: true, updatedBy: MANAGER, updatedAt: at(-2 * DAY) },
    { id: String(++siteSeq), domain: 'aliexpress.com', programName: 'AliExpress Portals(목업)', transform: { type: 'TEMPLATE', template: 'https://s.click.aliexpress.com/deep_link.htm?aff_short_key=MOCK00&dl_target_url={url}' }, enabled: false, updatedBy: null, updatedAt: at(-5 * DAY) },
];
let siteStoresSeeded = false;

/** 지금 몰 표 — 처음 읽을 때 한 번 `?mock=site-affiliate-empty` 면 비운다 */
function siteStoresNow(): SiteAffiliateStore[] {
    if (!siteStoresSeeded) {
        siteStoresSeeded = true;
        if (mockFlags().has('site-affiliate-empty')) siteStores = [];
    }
    return siteStores;
}

/** 글의 구매 링크가 걸리는 켠 몰 — 최종 호스트 먼저, 없으면 상품 호스트 */
function siteStoreOf(deal: Pick<PostDeal, 'productHost' | 'finalHost'>): SiteAffiliateStore | null {
    const stores = siteStoresNow();
    return UtilHotdeal.matchSiteStore(deal.finalHost, stores) ?? UtilHotdeal.matchSiteStore(deal.productHost, stores);
}

export async function mockSiteAffiliateList(): Promise<SiteAffiliateListView> {
    await gate('error');
    return { items: mockClone([...siteStoresNow()].sort((a, b) => a.domain.localeCompare(b.domain))) };
}

/** 저장 검사 — 칸 400 → 중복 409. 맞으면 저장할 모양(도메인 정규화 · 쿼리 앞 `?` 떼기 · 앞뒤 공백 떼기) */
function checkSiteStore(body: SiteAffiliateStoreRequest, selfId: string | null, on: Set<string>): Omit<SiteAffiliateStore, 'id' | 'updatedBy' | 'updatedAt'> {
    const domain = UtilHotdeal.normalizeDomain(body.domain);
    const fields: Record<string, string> = {};
    if (!domain) fields.domain = '도메인 형식을 확인해 주세요. 예: coupang.com';
    const program = UtilHotdeal.programProblem(body.programName);
    if (program === 'REQUIRED') fields.programName = '제휴 프로그램 이름을 입력해 주세요.';
    else if (program === 'TOO_LONG') fields.programName = `제휴 프로그램 이름은 ${UtilHotdeal.SITE_PROGRAM_MAX}자까지 쓸 수 있어요.`;
    const t = body.transform;
    if (t.type === 'TEMPLATE') {
        const p = UtilHotdeal.templateProblem(t.template);
        if (p === 'NO_PLACEHOLDER' || p === 'REQUIRED') fields.template = '링크 틀에 원래 상품 주소 자리 {url} 이 있어야 해요.';
        else if (p === 'MULTIPLE_PLACEHOLDERS') fields.template = '{url} 은 한 번만 쓸 수 있어요.';
        else if (p) fields.template = 'http(s):// 로 시작하는 1000자 이하 주소여야 해요.';
    } else if (t.type === 'QUERY') {
        if (UtilHotdeal.queryProblem(t.query)) fields.query = '붙일 쿼리는 이름=값 을 & 로 이은 모양이어야 해요. 예: partner=roboyam&pcode=1';
    } else fields.transform = '변환 방식을 골라 주세요.';
    if (typeof body.enabled !== 'boolean') fields.enabled = '켬·끔 중 하나여야 해요.';
    if (Object.keys(fields).length) throw MOCK_ERRORS.validation(fields);
    if (on.has('conflict') || siteStoresNow().some((s) => s.domain === domain && s.id !== selfId)) {
        throw new ApiError({ status: 409, code: 'HOTDEAL_SITE_AFFILIATE_DUPLICATE', message: '이미 사이트 제휴 몰로 있는 도메인이에요.', fields: { domain: `「${domain}」은 이미 표에 있어요. 그 줄을 고쳐 주세요.` } });
    }
    const transform = t.type === 'TEMPLATE' ? { type: 'TEMPLATE' as const, template: t.template.trim() } : { type: 'QUERY' as const, query: UtilHotdeal.normalizeQuery(t.query) };
    return { domain: domain as string, programName: body.programName.trim(), transform, enabled: body.enabled };
}

export async function mockSiteAffiliateCreate(body: SiteAffiliateStoreRequest): Promise<SiteAffiliateStore> {
    const on = await gate();
    if (on.has('forbidden')) throw MOCK_ERRORS.forbidden();
    const item: SiteAffiliateStore = { id: String(++siteSeq), ...checkSiteStore(body, null, on), updatedBy: ME, updatedAt: new Date().toISOString() };
    siteStores = [...siteStoresNow(), item];
    return mockClone(item);
}

export async function mockSiteAffiliateUpdate(id: string, body: SiteAffiliateStoreRequest): Promise<SiteAffiliateStore> {
    const on = await gate();
    if (on.has('forbidden')) throw MOCK_ERRORS.forbidden();
    const found = siteStoresNow().find((s) => s.id === id);
    if (!found) throw MOCK_ERRORS.notFound('HOTDEAL_SITE_AFFILIATE_NOT_FOUND', '사이트 제휴 몰을 찾을 수 없어요. 이미 지워졌을 수 있어요.');
    // 켬/끔만 바꾼 PUT 은 `conflict` 스위치를 타지 않는다(같은 도메인 그대로)
    Object.assign(found, checkSiteStore(body, id, body.domain === found.domain ? new Set([...on].filter((f) => f !== 'conflict')) : on), { updatedBy: ME, updatedAt: new Date().toISOString() });
    return mockClone(found);
}

export async function mockSiteAffiliateDelete(id: string): Promise<void> {
    const on = await gate();
    if (on.has('forbidden')) throw MOCK_ERRORS.forbidden();
    if (!siteStoresNow().some((s) => s.id === id)) throw MOCK_ERRORS.notFound('HOTDEAL_SITE_AFFILIATE_NOT_FOUND', '사이트 제휴 몰을 찾을 수 없어요. 이미 지워졌을 수 있어요.');
    siteStores = siteStores.filter((s) => s.id !== id);
}
