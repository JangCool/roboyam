// MOCK: 캐시 관리 — 서버에 Spring Cache + Caffeine 과 캐시 관리 API 가 아직 없어(화면 확정 전, 2026-11-08) 이 탭의 메모리에만 둔다. 새로 고치면 처음 값으로 돌아간다.
// 흉내 내는 서버 동작: 「지금 갱신」은 DB 의 세대 번호를 올리고, 각 서버(api-1 · api-2 · admin-1)가 확인 주기(3초)마다 세대 번호를 보고 자기 메모리 캐시를 비운다.
//   그래서 갱신은 「즉시」가 아니라 「몇 초 안에」 닿고, 아직 확인하지 않은 서버가 있는 동안 캐시는 「갱신 대기」다(서버마다 1~4초 뒤 따라잡는다).
//   처음 목록의 `home.popular` 는 api-2 가 한참 확인하지 않은(오래 걸리는) 상태로 시작해 15초 뒤 따라잡는다.
// 캐시 이름·대상 설명·「무엇을 저장해서」 글은 서버가 요청 언어로 풀어 줄 글이라 한국어 그대로 둔다(i18n.md 5절 — 목업 문구는 서버 몫).
// 서버 API 가 생기면 이 파일과 `useCacheAdminApi` 의 MOCK 줄을 지운다.

import { ApiError } from '~/composables/common/useApiClient';
import type {
    CacheDetailView,
    CacheExclusionView,
    CacheAdminLinkView,
    CacheGadgetPlacementView,
    CacheSampleKind,
    CacheUsageView,
    CachePreviewPageView,
    CacheKind,
    CacheMemberMode,
    CacheNodeStateView,
    CacheOverviewView,
    CacheRefreshLogView,
    CacheRefreshResultView,
    CacheRefreshTrigger,
    CacheServerRole,
    CacheSettingChangeView,
    CacheView,
} from '~/composables/cache/api/useCacheAdminApi.types';

/** 서버가 세대 번호를 확인하는 주기(초) */
const POLL_SECONDS = 3;
/** 비운 뒤 항목 수가 다시 가득 차기까지(밀리초) — 목업이 항목 수를 늘려 보이는 속도 */
const REFILL_MS = 40_000;

const NODES: { nodeId: string; role: CacheServerRole }[] = [
    { nodeId: 'api-1', role: 'API' },
    { nodeId: 'api-2', role: 'API' },
    { nodeId: 'admin-1', role: 'ADMIN' },
];

/** 허용 범위(초) — 분류마다 다르게 둔다(목업 가정) */
const RANGE: Record<CacheKind, { min: number; max: number }> = {
    PUBLIC_VIEW: { min: 5, max: 3600 },
    GADGET: { min: 5, max: 3600 },
    SETTINGS_COPY: { min: 5, max: 600 },
    INTERNAL_RULE: { min: 5, max: 300 },
};

interface CatalogEntry {
    cacheKey: string;
    displayName: string;
    target: string;
    targetIsPath: boolean;
    kind: CacheKind;
    servers: CacheServerRole[];
    defaultTtlSeconds: number;
    memberMode: CacheMemberMode;
    /** 서버 한 대가 가득 찼을 때의 항목 수 */
    entries: number;
    /** 서버 한 대의 적중·누락(띄운 뒤 센 값) */
    hits: number;
    misses: number;
    /** 처음 TTL — 기본값과 다르게 시작하는 캐시만 */
    ttlSeconds?: number;
    /** 가젯 데이터 캐시의 가젯 종류 */
    gadgetKind?: string;
}

const API: CacheServerRole[] = ['API'];
const BOTH: CacheServerRole[] = ['API', 'ADMIN'];

/** 캐시 카탈로그 25개(공개 화면 6 · 가젯 7 · 설정 사본 7 · 내부 규칙 5) — 차례가 목록 차례다 */
const CATALOG: CatalogEntry[] = [
    // 새 대상(공개 화면 응답) — 배치는 가젯 데이터와 따로 캐시한다(배치 캐시에는 가젯 구성·위치·설정만, 내용은 아래 가젯 데이터 캐시)
    { cacheKey: 'home.layout', displayName: '홈 배치', target: 'GET /api/home/gadgets — 가젯 구성·위치·설정', targetIsPath: true, kind: 'PUBLIC_VIEW', servers: API, defaultTtlSeconds: 300, memberMode: 'SHARED', entries: 4, hits: 182_340, misses: 212 },
    { cacheKey: 'pages.layout', displayName: '가젯 페이지 배치', target: 'GET /api/pages/{key} — 머리·가젯 구성', targetIsPath: true, kind: 'PUBLIC_VIEW', servers: API, defaultTtlSeconds: 300, memberMode: 'SHARED', entries: 8, hits: 41_820, misses: 180 },
    { cacheKey: 'boards.layout', displayName: '게시판 레이아웃', target: 'GET /api/boards/{slug}/layout', targetIsPath: true, kind: 'PUBLIC_VIEW', servers: API, defaultTtlSeconds: 300, ttlSeconds: 600, memberMode: 'SHARED', entries: 37, hits: 254_900, misses: 410 },
    { cacheKey: 'home.latest', displayName: '최신글 모음', target: 'GET /api/home/latest', targetIsPath: true, kind: 'PUBLIC_VIEW', servers: API, defaultTtlSeconds: 60, ttlSeconds: 30, memberMode: 'SHARED_WITH_MEMBER_OVERLAY', entries: 6, hits: 96_410, misses: 3_870 },
    { cacheKey: 'home.popular', displayName: '인기글', target: 'GET /api/home/popular', targetIsPath: true, kind: 'PUBLIC_VIEW', servers: API, defaultTtlSeconds: 120, memberMode: 'SHARED_WITH_MEMBER_OVERLAY', entries: 8, hits: 88_120, misses: 1_540 },
    { cacheKey: 'menu.public', displayName: '공개 메뉴', target: 'GET /api/menu', targetIsPath: true, kind: 'PUBLIC_VIEW', servers: API, defaultTtlSeconds: 300, memberMode: 'SHARED_WITH_MEMBER_OVERLAY', entries: 2, hits: 311_005, misses: 96 },
    // 가젯 데이터 — 유형별(서버 `HomeGadgetKinds`). 항목 키 = 가젯 설정 지문(같은 설정이면 어느 쪽 가젯이든 한 항목을 함께 쓴다). 글 줄이 있는 종류만 회원 덧입힘(차단·메모)
    { cacheKey: 'gadget.POST_LIST', displayName: '글 목록 가젯', target: '최신·인기·조회·추천·댓글 정렬의 글 목록', targetIsPath: false, kind: 'GADGET', gadgetKind: 'POST_LIST', servers: API, defaultTtlSeconds: 60, memberMode: 'SHARED_WITH_MEMBER_OVERLAY', entries: 9, hits: 520_120, misses: 6_310 },
    { cacheKey: 'gadget.HIGHLIGHT', displayName: '하이라이트 가젯', target: '운영자가 고른 하이라이트 글', targetIsPath: false, kind: 'GADGET', gadgetKind: 'HIGHLIGHT', servers: API, defaultTtlSeconds: 120, memberMode: 'SHARED_WITH_MEMBER_OVERLAY', entries: 2, hits: 181_400, misses: 1_020 },
    { cacheKey: 'gadget.GALLERY', displayName: '갤러리 가젯', target: '사진이 있는 최신 글', targetIsPath: false, kind: 'GADGET', gadgetKind: 'GALLERY', servers: API, defaultTtlSeconds: 60, memberMode: 'SHARED_WITH_MEMBER_OVERLAY', entries: 3, hits: 120_880, misses: 2_400 },
    { cacheKey: 'gadget.BOARD_CARDS', displayName: '게시판 카드 가젯', target: '게시판마다 최신 글 몇 줄을 담은 카드', targetIsPath: false, kind: 'GADGET', gadgetKind: 'BOARD_CARDS', servers: API, defaultTtlSeconds: 120, memberMode: 'SHARED_WITH_MEMBER_OVERLAY', entries: 2, hits: 64_330, misses: 610 },
    { cacheKey: 'gadget.GROUPS', displayName: '소모임 가젯', target: '모집 중·활발한 소모임', targetIsPath: false, kind: 'GADGET', gadgetKind: 'GROUPS', servers: API, defaultTtlSeconds: 300, memberMode: 'SHARED', entries: 1, hits: 90_012, misses: 140 },
    { cacheKey: 'gadget.GROUP_BUY', displayName: '공동구매 가젯', target: '진행 중인 공동구매(마감 임박·새로·참여 많은 순)', targetIsPath: false, kind: 'GADGET', gadgetKind: 'GROUP_BUY', servers: API, defaultTtlSeconds: 60, memberMode: 'SHARED', entries: 2, hits: 88_540, misses: 2_010 },
    { cacheKey: 'gadget.HOT_DEALS', displayName: '핫딜 가젯', target: '핫딜 게시판의 최신 핫딜', targetIsPath: false, kind: 'GADGET', gadgetKind: 'HOT_DEALS', servers: API, defaultTtlSeconds: 60, memberMode: 'SHARED_WITH_MEMBER_OVERLAY', entries: 1, hits: 30_220, misses: 900 },
    // 지금 서버에 직접 만든 사본으로 있는 것 — 같은 틀로 옮긴다(기본 TTL 은 지금 값)
    { cacheKey: 'site.info', displayName: '사이트 정보·테마·매니페스트', target: 'GET /api/site/info · /api/site/theme', targetIsPath: true, kind: 'SETTINGS_COPY', servers: API, defaultTtlSeconds: 20, memberMode: 'SHARED', entries: 3, hits: 402_118, misses: 5_210 },
    { cacheKey: 'site.noticeBar', displayName: '공지 띠', target: 'GET /api/site/notice-bar', targetIsPath: true, kind: 'SETTINGS_COPY', servers: API, defaultTtlSeconds: 20, memberMode: 'SHARED', entries: 1, hits: 198_442, misses: 4_980 },
    { cacheKey: 'site.popups', displayName: '사이트 팝업', target: 'GET /api/site/popups', targetIsPath: true, kind: 'SETTINGS_COPY', servers: API, defaultTtlSeconds: 20, memberMode: 'SHARED', entries: 1, hits: 197_001, misses: 4_990 },
    { cacheKey: 'terms.public', displayName: '약관', target: 'GET /api/terms/**', targetIsPath: true, kind: 'SETTINGS_COPY', servers: API, defaultTtlSeconds: 20, memberMode: 'SHARED', entries: 5, hits: 3_204, misses: 1_102 },
    { cacheKey: 'notice.global', displayName: '게시판 전체 공지', target: '모든 게시판 목록 맨 위에 붙는 전체 공지', targetIsPath: false, kind: 'SETTINGS_COPY', servers: API, defaultTtlSeconds: 15, ttlSeconds: 30, memberMode: 'SHARED', entries: 1, hits: 120_377, misses: 3_880 },
    { cacheKey: 'board.catalog', displayName: '게시판·정책·등급 목록', target: '게시판 목록과 게시판별 정책, 회원 등급표', targetIsPath: false, kind: 'SETTINGS_COPY', servers: BOTH, defaultTtlSeconds: 15, memberMode: 'SHARED', entries: 3, hits: 640_210, misses: 7_330 },
    { cacheKey: 'stats.collection', displayName: '통계 수집 설정', target: '방문·광고 통계를 모을지와 모으는 범위', targetIsPath: false, kind: 'SETTINGS_COPY', servers: BOTH, defaultTtlSeconds: 30, memberMode: 'SHARED', entries: 1, hits: 280_001, misses: 1_800 },
    { cacheKey: 'commerce.rateLimits', displayName: '요청 종류별 1분 한도', target: '주문·결제·환불 요청의 1분 한도표', targetIsPath: false, kind: 'INTERNAL_RULE', servers: BOTH, defaultTtlSeconds: 15, memberMode: 'SHARED', entries: 1, hits: 12_440, misses: 2_310 },
    { cacheKey: 'hotdeal.rules', displayName: '핫딜 링크 규칙·제휴 몰', target: '핫딜 링크 판정 규칙과 제휴 몰 목록', targetIsPath: false, kind: 'INTERNAL_RULE', servers: BOTH, defaultTtlSeconds: 15, memberMode: 'SHARED', entries: 2, hits: 8_702, misses: 1_950 },
    { cacheKey: 'hotdeal.settings', displayName: '핫딜 설정', target: '핫딜 게시판의 판정·표시 설정', targetIsPath: false, kind: 'INTERNAL_RULE', servers: BOTH, defaultTtlSeconds: 15, memberMode: 'SHARED', entries: 1, hits: 9_015, misses: 1_940 },
    { cacheKey: 'notification.templates', displayName: '알림 문구·템플릿', target: '알림 이벤트별 채널 문구와 메일·메신저 템플릿', targetIsPath: false, kind: 'INTERNAL_RULE', servers: BOTH, defaultTtlSeconds: 15, memberMode: 'SHARED', entries: 46, hits: 33_870, misses: 4_120 },
    { cacheKey: 'point.policy', displayName: '포인트 정책', target: '활동별 포인트 지급·차감 규칙과 하루 한도', targetIsPath: false, kind: 'INTERNAL_RULE', servers: BOTH, defaultTtlSeconds: 15, memberMode: 'SHARED', entries: 1, hits: 71_330, misses: 2_200 },
];

/** 캐시하지 않는 가젯 종류 — 이유는 서버 글 */
const UNCACHED: CacheExclusionView[] = [
    { gadgetKind: 'QUICK_LINKS', displayName: '바로가기', reason: '설정 값뿐이라 따로 캐시하지 않고 배치 캐시에 함께 담깁니다.', includedIn: ['home.layout', 'pages.layout', 'boards.layout'] },
    { gadgetKind: 'NOTICE_TEXT', displayName: '안내 글', reason: '설정 값뿐이라 따로 캐시하지 않고 배치 캐시에 함께 담깁니다.', includedIn: ['home.layout', 'pages.layout', 'boards.layout'] },
    { gadgetKind: 'AD', displayName: '광고 배너', reason: '노출 집계와 순환(볼 때마다 다른 광고) 때문에 캐시하지 않습니다(GET /api/ads).', includedIn: [] },
    { gadgetKind: 'AD_TEXT_LINE', displayName: '한 줄 광고', reason: '노출 집계와 순환(볼 때마다 다른 광고) 때문에 캐시하지 않습니다(GET /api/ads).', includedIn: [] },
    { gadgetKind: 'BOARD_BODY', displayName: '게시판 본문', reason: '게시판 레이아웃의 본문 칸은 게시판 목록·글 보기 화면 그대로라 가젯 캐시에 해당하지 않습니다.', includedIn: [] },
];

/** 미리보기에서 고를 수 있는 쪽 — 로컬 데모 데이터 기준(자유게시판 `free`, 가젯 페이지 「커뮤니티」 `/p/community`) */
const PREVIEW_PAGES: CachePreviewPageView[] = [
    { path: '/', label: '홈' },
    { path: '/latest', label: '최신글' },
    { path: '/popular', label: '인기글' },
    { path: '/boards/free', label: '게시판 목록 — 자유게시판' },
    { path: '/boards/hotdeal', label: '게시판 목록 — 핫딜' },
    { path: '/p/community', label: '가젯 페이지 — 커뮤니티' },
    { path: '/boards', label: '게시판 모아보기' },
    { path: '/me/points', label: '내 정보 — 포인트(로그인)' },
    { path: '/notifications', label: '알림(로그인)' },
    { path: '/terms', label: '이용약관' },
    { path: '/signup', label: '회원 가입' },
    { path: '/write', label: '글쓰기(로그인)' },
    { path: '/write?board=hotdeal', label: '핫딜 글쓰기(로그인)' },
    { path: '/point-shop', label: '포인트 상점' },
    { path: '/group-buys', label: '공동구매' },
];

/** 이 가젯 유형을 놓은 곳(가젯 종류마다) — 같은 설정이면 어느 쪽에 있든 한 항목을 함께 쓴다 */
const PLACEMENTS: Record<string, CacheGadgetPlacementView[]> = {
    POST_LIST: [
        { place: 'HOME', name: '홈', path: '/', count: 3 },
        { place: 'GADGET_PAGE', name: '커뮤니티', path: '/p/community', count: 4 },
        { place: 'GADGET_PAGE', name: '팁·강좌', path: '/p/tips', count: 2 },
        { place: 'BOARD_LAYOUT', name: '게시판 레이아웃 — 기본 테마', path: '/boards/free', count: 1 },
    ],
    HIGHLIGHT: [
        { place: 'HOME', name: '홈', path: '/', count: 1 },
        { place: 'GADGET_PAGE', name: '팁·강좌', path: '/p/tips', count: 1 },
    ],
    GALLERY: [
        { place: 'HOME', name: '홈', path: '/', count: 1 },
        { place: 'GADGET_PAGE', name: '커뮤니티', path: '/p/community', count: 1 },
        { place: 'BOARD_LAYOUT', name: '게시판 레이아웃 — 기본 테마', path: '/boards/free', count: 1 },
    ],
    BOARD_CARDS: [
        { place: 'GADGET_PAGE', name: '커뮤니티', path: '/p/community', count: 2 },
    ],
    GROUPS: [{ place: 'HOME', name: '홈', path: '/', count: 1 }],
    GROUP_BUY: [
        { place: 'HOME', name: '홈', path: '/', count: 1 },
        { place: 'BOARD_LAYOUT', name: '게시판 레이아웃 — 기본 테마', path: '/boards/free', count: 1 },
    ],
    HOT_DEALS: [{ place: 'GADGET_PAGE', name: '커뮤니티', path: '/p/community', count: 1 }],
};

/** 쓰이는 곳 한 줄 줄임 */
function area(label: string, description: string, path: string | null, loginRequired = false): CacheUsageView {
    return { kind: 'AREA', label, description, path, sample: null, loginRequired };
}
function action(label: string, description: string, path: string | null, loginRequired = false): CacheUsageView {
    return { kind: 'ACTION', label, description, path, sample: null, loginRequired };
}
function server(label: string, description: string): CacheUsageView {
    return { kind: 'SERVER', label, description, path: null, sample: null, loginRequired: false };
}
/** 글·상품 하나를 열어야 보이는 곳 — 경로 대신 예시 경로 규칙(미리보기가 예시 번호를 골라 연다) */
function sampled(kind: 'AREA' | 'ACTION', label: string, description: string, sampleKind: CacheSampleKind, template: string, loginRequired = false): CacheUsageView {
    return { kind, label, description, path: null, sample: { kind: sampleKind, template, sampleId: null }, loginRequired };
}

/** 관련 관리 화면 — 주소는 관리자 메뉴(`util-admin-menu.ts`)에 실제로 있는 것만 */
const LINK = {
    theme: { label: '테마·레이아웃', path: '/settings/theme' },
    general: { label: '일반', path: '/settings/general' },
    menus: { label: '공개 메뉴', path: '/menus/public' },
    boards: { label: '게시판 관리', path: '/boards' },
    grades: { label: '등급', path: '/grades' },
    notices: { label: '공지 관리', path: '/notices' },
    popups: { label: '사이트 팝업', path: '/content/popups' },
    highlights: { label: '하이라이트', path: '/content/highlights' },
    popular: { label: '인기글', path: '/content/popular' },
    popularSettings: { label: '인기글 설정', path: '/content/popular/settings' },
    terms: { label: '약관', path: '/settings/terms' },
    analytics: { label: '통계 수집', path: '/settings/analytics' },
    commerce: { label: '커머스 설정', path: '/settings/commerce' },
    hotdealDomains: { label: '링크 규칙', path: '/hotdeals/domains' },
    hotdealSettings: { label: '핫딜 설정', path: '/hotdeals/settings' },
    memberMessages: { label: '회원 알림 문구', path: '/settings/notification-messages/member' },
    mailTemplates: { label: '메일 템플릿', path: '/settings/notification-templates/mail' },
    pointPolicy: { label: '포인트 정책', path: '/points/policy' },
    groups: { label: '소모임', path: '/groups' },
    groupBuys: { label: '공동구매', path: '/group-buys' },
    hotdeals: { label: '핫딜 글', path: '/hotdeals' },
} satisfies Record<string, CacheAdminLinkView>;

/**
 * 캐시마다 쓰이는 곳·관련 관리 화면(서버 단계에서는 캐시 정의 코드에 함께 적는 고정 정보). 가젯 데이터 캐시의 화면 영역은 놓은 곳(`PLACEMENTS`)에서 만든다.
 * 근거: 서버 코드에서 쓰는 곳(코디네이터 확인) + 공개 사이트의 `data-cache-area` 를 붙인 요소. 요소가 없는 쓰임은 「서버 내부」로 둔다.
 */
const USE: Record<string, { usages: CacheUsageView[]; links: CacheAdminLinkView[] }> = {
    'home.layout': { usages: [area('홈 격자', '가젯이 놓인 칸·크기·차례와 가젯마다의 설정(바로가기·안내 글은 내용까지)', '/')], links: [LINK.theme] },
    'pages.layout': { usages: [area('가젯 페이지', '페이지 머리(이름·설명·글쓰기 단추)와 가젯 배치', '/p/community')], links: [LINK.theme] },
    'boards.layout': { usages: [area('게시판 레이아웃', '게시판 목록·글 보기를 감싸는 가젯 배치(본문만인 레이아웃이면 상자가 없다)', '/boards/free')], links: [LINK.theme] },
    'home.latest': { usages: [area('최신글 목록', '켜진 모든 게시판의 최신 글 목록', '/latest')], links: [LINK.boards] },
    'home.popular': { usages: [area('인기글 목록', '기간 탭(실시간·어제·주간)별 순위 목록', '/popular')], links: [LINK.popular, LINK.popularSettings] },
    'menu.public': {
        usages: [area('윗줄·사이드 메뉴', '테마에 따라 윗줄 메뉴·로고 옆 링크·사이드 메뉴', '/'), area('게시판 모아보기', '메뉴 차례로 묶은 게시판 바로가기', '/boards')],
        links: [LINK.menus],
    },
    'site.info': {
        usages: [
            area('로고·사이트 이름', '머리글의 로고 그림과 사이트 이름', '/'),
            area('테마', '사이트 전체 색·셸 모양 — 미리보기 상자는 로고·이름 자리에 그린다', '/'),
            server('웹 앱 매니페스트', '홈 화면에 추가할 때의 이름·아이콘(manifest) — 화면 상자 없음'),
        ],
        links: [LINK.general, LINK.theme],
    },
    'site.noticeBar': { usages: [area('공지 띠', '메뉴 줄 아래·본문 위의 공지 띠(모든 화면)', '/')], links: [LINK.general] },
    'site.popups': { usages: [area('사이트 팝업', '들어오면 가운데 뜨는 팝업 창 — 띄울 팝업이 있을 때만 보인다', '/')], links: [LINK.popups] },
    'terms.public': {
        usages: [
            area('바닥글 약관 링크', '이용약관·개인정보처리방침·운영정책 링크', '/'),
            area('약관 화면', '이용약관 본문(`/privacy`·`/policy` 도 같은 캐시)', '/terms'),
            action('가입 약관 동의', '가입 마지막 단계의 약관 동의 — 앞 단계를 채워야 보인다', '/signup'),
        ],
        links: [LINK.terms],
    },
    'notice.global': { usages: [area('전체 공지 줄', '모든 게시판 목록 맨 위에 붙는 전체 공지', '/boards/free')], links: [LINK.notices] },
    'board.catalog': {
        usages: [
            area('게시판 머리', '게시판 이름·설명·색', '/boards/free'),
            area('게시판 모아보기', '게시판 이름과 묶음', '/boards'),
            action('게시판 글쓰기 단추', '이 게시판에 쓸 수 있는지(등급·정책)에 따라 켜지고 막힌다', '/boards/free'),
            action('글쓰기 게시판 고르기', '쓸 수 있는 게시판만 고를 수 있다', '/write', true),
        ],
        links: [LINK.boards, LINK.grades],
    },
    'stats.collection': {
        usages: [
            server('방문 기록 수집 여부', '공개 화면 스크립트가 방문 신호를 보낼지, 서버가 방문을 모을지(VisitCollector · PublicSiteService)'),
            server('알림 발송 통계', '알림을 보낼 때 발송 통계를 남길지(NotificationSendStats)'),
        ],
        links: [LINK.analytics],
    },
    'commerce.rateLimits': {
        usages: [
            sampled('ACTION', '포인트 상점 [구매]', '상품 화면의 [구매]와 구매 확인 단추 — 1분 한도를 넘으면 오류 안내', 'POINT_SHOP_ITEM', '/point-shop/{id}'),
            sampled('ACTION', '공동구매 [주문하기]', '공동구매 상세의 [주문하기] — 1분 한도를 넘으면 오류 안내', 'GROUP_BUY', '/group-buys/{id}'),
            sampled('ACTION', '주문 [결제]', '주문 화면(/group-buys/번호/order)의 [결제] — 주문 화면은 상세에서 옵션·수량을 고르고 [주문하기]로 들어가야 열린다(바로 열면 「고른 상품이 없다」 안내만). 미리보기는 가장 가까운 쪽인 상세의 [주문하기]까지 연다', 'GROUP_BUY', '/group-buys/{id}'),
        ],
        links: [LINK.commerce],
    },
    'hotdeal.rules': {
        usages: [
            sampled('ACTION', '딜 카드 「구매하러 가기」', '핫딜 글 보기의 단추 — 제휴 링크로 바꿔 보낸다', 'HOTDEAL_POST', '/posts/{id}'),
            action('핫딜 글쓰기 링크 칸', '붙여 넣은 상품 링크를 링크 규칙·제휴 몰로 검사한다', '/write?board=hotdeal', true),
        ],
        links: [LINK.hotdealDomains],
    },
    'hotdeal.settings': {
        usages: [
            area('핫딜 게시판 목록', '목록 줄의 가격·몰·종료 표시', '/boards/hotdeal'),
            area('홈 핫딜 가젯', '핫딜 가젯의 딜 표시', '/p/community'),
            sampled('AREA', '핫딜 글 딜 카드', '글 보기의 딜 카드(가격·종료·연장)', 'HOTDEAL_POST', '/posts/{id}'),
            server('자동 종료·만료 안내 배치', '기한이 지난 딜을 닫고 작성자에게 알린다'),
        ],
        links: [LINK.hotdealSettings, LINK.hotdeals],
    },
    'notification.templates': {
        usages: [
            area('알림 종 패널', '머리글 알림 종을 눌러 여는 알림 목록', '/', true),
            area('알림 화면', '알림 전체 목록', '/notifications', true),
            server('메일 본문', '알림 메일의 제목·본문(메일 템플릿)'),
        ],
        links: [LINK.memberMessages, LINK.mailTemplates],
    },
    'point.policy': {
        usages: [
            area('내 포인트', '내 정보 › 포인트의 잔액·소멸 예정·적립 규칙·내역', '/me/points', true),
            server('글·댓글·추천 지급', '쓸 때 정책대로 지급·차감한다 — 글쓰기 화면에 포인트 안내가 없어 화면 상자는 없다'),
            server('포인트 소멸 안내 배치', '소멸 예정 포인트를 미리 알린다'),
        ],
        links: [LINK.pointPolicy],
    },
};

/** 가젯 데이터 캐시의 쓰이는 곳 — 놓은 곳마다 화면 영역 한 줄 */
const GADGET_LINKS: Record<string, CacheAdminLinkView[]> = {
    HIGHLIGHT: [LINK.highlights],
    GROUPS: [LINK.groups],
    GROUP_BUY: [LINK.groupBuys],
    HOT_DEALS: [LINK.hotdeals, LINK.hotdealSettings],
};

const PLACE_LABEL: Record<string, string> = { HOME: '홈', GADGET_PAGE: '가젯 페이지', BOARD_LAYOUT: '게시판 레이아웃' };

function usagesOf(def: CatalogEntry): CacheUsageView[] {
    if (def.gadgetKind) return (PLACEMENTS[def.gadgetKind] ?? []).map((p) => area(p.place === 'HOME' ? '홈' : `${PLACE_LABEL[p.place]} — ${p.name}`, `${def.displayName} ${p.count}개`, p.path));
    return USE[def.cacheKey]?.usages ?? [];
}

function linksOf(def: CatalogEntry): CacheAdminLinkView[] {
    if (def.gadgetKind) return [LINK.theme, ...(GADGET_LINKS[def.gadgetKind] ?? [])];
    return USE[def.cacheKey]?.links ?? [];
}

interface MockNode {
    nodeId: string;
    role: CacheServerRole;
    appliedGeneration: number;
    /** 밀리초. null = 띄운 뒤 안 비움 */
    lastClearedAt: number | null;
    /** 이 시각이 지나면 지금 세대를 따라잡는다(갱신 대기 중일 때만) */
    applyAt: number | null;
    entries: number;
    hits: number;
    misses: number;
    /** 이 서버가 따라간 켜짐 상태 */
    appliedEnabled: boolean;
    /** 이 시각이 지나면 켜짐 상태를 따라간다(켜기/끄기 대기 중일 때만) */
    enabledApplyAt: number | null;
}

interface MockCache {
    def: CatalogEntry;
    ttlSeconds: number;
    generation: number;
    /** 마지막 갱신 요청 시각(대기 중일 때만, 밀리초) */
    pendingSince: number | null;
    nodes: MockNode[];
    refreshes: CacheRefreshLogView[];
    /** 이 캐시의 켜기/끄기(저장 값) */
    enabled: boolean;
    /** 설정 변경 이력(TTL · 켜기/끄기 — 전체 켜기/끄기는 `globalChanges` 와 합쳐 보인다) */
    settingChanges: CacheSettingChangeView[];
}

/** 전체 캐시 스위치(DB 값) — 켜진 채로 시작한다 */
let globalEnabled = true;
/** 전체 켜기/끄기 이력(모든 캐시의 설정 변경 이력에 함께 보인다) */
const globalChanges: CacheSettingChangeView[] = [];
/** 꺼진 채로 시작하는 캐시(목업) */
const SEED_OFF = new Set(['gadget.GALLERY', 'terms.public']);

let caches: MockCache[] | null = null;
let seq = 1000;

const MIN = 60_000;
const HOUR = 60 * MIN;

function iso(ms: number | null): string | null {
    return ms == null ? null : new Date(ms).toISOString();
}

function nextId(): string {
    seq += 1;
    return String(seq);
}

/** 설정 변경 이력 한 줄 */
function setting(at: number, kind: CacheSettingChangeView['kind'], values: { fromSeconds?: number; toSeconds?: number; enabled?: boolean }, operatorName: string): CacheSettingChangeView {
    return { id: nextId(), changedAt: iso(at)!, kind, fromSeconds: values.fromSeconds ?? null, toSeconds: values.toSeconds ?? null, enabled: values.enabled ?? null, operatorName };
}

/** 실제로 쓰는가 = 전체 켜짐 && 이 캐시 켜짐 */
function effective(cache: MockCache): boolean {
    return globalEnabled && cache.enabled;
}

/** 켜짐 상태가 바뀌었다 — 서버마다 1~4초 뒤 따라간다(TTL 처럼 DB 값, 확인 주기마다 읽는다) */
function scheduleEnabled(cache: MockCache, now: number): void {
    cache.pendingSince = now;
    for (const node of cache.nodes) node.enabledApplyAt = now + Math.round((0.4 + Math.random()) * POLL_SECONDS * 1000);
}

/** 따라가지 않은 서버 — 세대 번호가 낮거나 켜짐 상태가 다르다 */
function behind(cache: MockCache, node: MockNode): boolean {
    return node.appliedGeneration < cache.generation || node.appliedEnabled !== effective(cache);
}

/** 처음 갱신 기록 — 공개 화면은 관련 설정 저장(자동)이 대부분, 나머지는 가끔 수동 */
const SEED_REFRESHES: Record<string, { ago: number; trigger: CacheRefreshTrigger; source?: string; operator: string }[]> = {
    'home.layout': [
        { ago: 12 * MIN, trigger: 'AUTO_SAVE', source: '홈 구성 저장', operator: '운영팀 김로보' },
        { ago: 3 * HOUR, trigger: 'MANUAL', operator: '최고 관리자' },
        { ago: 26 * HOUR, trigger: 'AUTO_SAVE', source: '홈 구성 저장', operator: '운영팀 김로보' },
    ],
    'pages.layout': [{ ago: 2 * HOUR, trigger: 'AUTO_SAVE', source: '가젯 페이지 저장 — 커뮤니티', operator: '운영팀 이얌' }],
    // 가젯 설정을 바꾸면 그 종류의 가젯 데이터 캐시도 비운다(설정 지문이 바뀐 항목만 새로 생기지만 옛 항목을 바로 치운다)
    'gadget.POST_LIST': [{ ago: 12 * MIN, trigger: 'AUTO_SAVE', source: '홈 구성 저장 — 글 목록 가젯 설정', operator: '운영팀 김로보' }],
    'gadget.HIGHLIGHT': [{ ago: 5 * HOUR, trigger: 'AUTO_SAVE', source: '하이라이트 저장', operator: '운영팀 이얌' }],
    'gadget.GROUP_BUY': [{ ago: 1 * HOUR, trigger: 'AUTO_SAVE', source: '공동구매 저장 — 마감 연장', operator: '운영팀 김로보' }],
    'home.latest': [{ ago: 5 * HOUR, trigger: 'MANUAL', operator: '최고 관리자' }],
    'home.popular': [{ ago: 25_000, trigger: 'MANUAL', operator: '최고 관리자' }, { ago: 9 * HOUR, trigger: 'AUTO_SAVE', source: '인기글 설정 저장', operator: '운영팀 김로보' }],
    'boards.layout': [
        { ago: 40 * MIN, trigger: 'AUTO_SAVE', source: '게시판 설정 저장 — 자유게시판', operator: '운영팀 이얌' },
        { ago: 4 * HOUR, trigger: 'AUTO_SAVE', source: '게시판 설정 저장 — 핫딜', operator: '운영팀 김로보' },
    ],
    'menu.public': [{ ago: 1 * HOUR, trigger: 'AUTO_SAVE', source: '공개 메뉴 저장', operator: '운영팀 김로보' }],
    'site.info': [{ ago: 30 * HOUR, trigger: 'AUTO_SAVE', source: '사이트 기본 정보 저장', operator: '최고 관리자' }],
    'site.noticeBar': [{ ago: 3 * HOUR, trigger: 'AUTO_SAVE', source: '공지 띠 저장', operator: '운영팀 이얌' }],
    'notice.global': [{ ago: 50 * MIN, trigger: 'AUTO_SAVE', source: '전체 공지 저장 — 이용 안내', operator: '운영팀 이얌' }],
    'board.catalog': [{ ago: 40 * MIN, trigger: 'AUTO_SAVE', source: '게시판 설정 저장 — 자유게시판', operator: '운영팀 이얌' }],
    'point.policy': [{ ago: 2 * 24 * HOUR, trigger: 'MANUAL', operator: '최고 관리자' }],
};

/** 처음 TTL 변경 이력 — 기본값과 다르게 시작하는 캐시 */
const SEED_TTL: Record<string, { ago: number; from: number; to: number; operator: string }[]> = {
    'home.latest': [{ ago: 2 * 24 * HOUR, from: 60, to: 30, operator: '최고 관리자' }],
    'boards.layout': [
        { ago: 6 * 24 * HOUR, from: 300, to: 900, operator: '운영팀 김로보' },
        { ago: 1 * 24 * HOUR, from: 900, to: 600, operator: '최고 관리자' },
    ],
    'notice.global': [{ ago: 3 * HOUR, from: 15, to: 30, operator: '운영팀 이얌' }],
};

function seed(now: number): MockCache[] {
    return CATALOG.map((def) => {
        const logs = SEED_REFRESHES[def.cacheKey] ?? [];
        const generation = 3 + logs.length;
        const lastAt = logs[0] ? now - logs[0].ago : null;
        const nodes: MockNode[] = NODES.filter((n) => def.servers.includes(n.role)).map((n, i) => {
            // 서버마다 적중·누락이 조금씩 다르다(관리자 서버는 덜 쓴다)
            const share = n.role === 'ADMIN' ? 0.15 : i === 0 ? 0.52 : 0.48;
            return {
                nodeId: n.nodeId,
                role: n.role,
                appliedGeneration: generation,
                // 비운 시각은 서버마다 1~3초씩 어긋난다(각자 확인 주기에 따라 비운다)
                lastClearedAt: lastAt == null ? null : lastAt + 800 + i * 1100,
                applyAt: null,
                entries: def.entries,
                hits: Math.round(def.hits * share),
                misses: Math.round(def.misses * share),
                appliedEnabled: !SEED_OFF.has(def.cacheKey),
                enabledApplyAt: null,
            };
        });
        const cache: MockCache = {
            def,
            ttlSeconds: def.ttlSeconds ?? def.defaultTtlSeconds,
            generation,
            pendingSince: null,
            nodes,
            refreshes: logs.map((log, i) => ({
                id: nextId(),
                requestedAt: iso(now - log.ago)!,
                trigger: log.trigger,
                all: false,
                source: log.source ?? null,
                operatorName: log.operator,
                generation: generation - i,
            })),
            enabled: !SEED_OFF.has(def.cacheKey),
            settingChanges: [
                ...(SEED_OFF.has(def.cacheKey) ? [setting(now - 2 * HOUR, 'ENABLED', { enabled: false }, '최고 관리자')] : []),
                ...(SEED_TTL[def.cacheKey] ?? []).map((c) => setting(now - c.ago, 'TTL', { fromSeconds: c.from, toSeconds: c.to }, c.operator)).reverse(),
            ],
        };
        // 하나는 「갱신 대기」로 시작한다 — api-2 가 25초째 확인하지 않았고 15초 뒤 따라잡는다(오래 걸리는 안내가 보이게)
        if (def.cacheKey === 'home.popular') {
            const lagging = cache.nodes.find((n) => n.nodeId === 'api-2');
            if (lagging) {
                lagging.appliedGeneration = generation - 1;
                lagging.lastClearedAt = now - 9 * HOUR;
                lagging.applyAt = now + 15_000;
                cache.pendingSince = now - 25_000;
            }
        }
        return cache;
    });
}

function state(now: number): MockCache[] {
    caches ??= seed(now);
    // 확인 시각이 지난 서버는 지금 세대를 따라잡는다(자기 캐시를 비운다)
    for (const cache of caches) {
        for (const node of cache.nodes) {
            if (node.applyAt != null && now >= node.applyAt) {
                node.appliedGeneration = cache.generation;
                node.lastClearedAt = node.applyAt;
                node.applyAt = null;
                // 비운 뒤 처음 요청들은 누락이다
                node.misses += cache.def.entries;
            }
            if (node.enabledApplyAt != null && now >= node.enabledApplyAt) {
                const on = effective(cache);
                // 꺼지면 담긴 항목을 비운다 · 켜지면 빈 채로 다시 차오른다
                if (node.appliedEnabled !== on) node.lastClearedAt = node.enabledApplyAt;
                node.appliedEnabled = on;
                node.enabledApplyAt = null;
            }
        }
        if (cache.nodes.every((n) => !behind(cache, n))) cache.pendingSince = null;
    }
    return caches;
}

function find(cacheKey: string, now: number): MockCache {
    const cache = state(now).find((c) => c.def.cacheKey === cacheKey);
    if (!cache) throw new ApiError({ status: 404, code: 'CACHE_NOT_FOUND', message: '그 캐시를 찾을 수 없습니다. 목록을 다시 조회해 주세요.' });
    return cache;
}

/** 비운 뒤 다시 차오르는 항목 수 */
function entriesOf(node: MockNode, now: number): number {
    // 꺼진 서버는 담지 않는다(요청마다 원본을 읽는다)
    if (!node.appliedEnabled) return 0;
    if (node.lastClearedAt == null) return node.entries;
    const filled = Math.floor((node.entries * (now - node.lastClearedAt)) / REFILL_MS);
    return Math.max(0, Math.min(node.entries, filled));
}

/** 서버의 마지막 확인 시각 — 확인 주기 안에서 서버마다 어긋난다 */
function checkedAt(nodeIndex: number, now: number): number {
    const period = POLL_SECONDS * 1000;
    return now - ((now + nodeIndex * 977) % period);
}

function view(cache: MockCache, now: number): CacheView {
    const pending = cache.nodes.filter((n) => behind(cache, n));
    const cleared = cache.nodes.map((n) => n.lastClearedAt);
    // 가장 늦은 서버 기준 — 한 대라도 안 비웠으면(띄운 뒤 그대로) null
    const lastRefreshedAt = cleared.some((c) => c == null) ? null : Math.min(...(cleared as number[]));
    const range = RANGE[cache.def.kind];
    return {
        cacheKey: cache.def.cacheKey,
        displayName: cache.def.displayName,
        target: cache.def.target,
        targetIsPath: cache.def.targetIsPath,
        kind: cache.def.kind,
        servers: [...cache.def.servers],
        ttlSeconds: cache.ttlSeconds,
        defaultTtlSeconds: cache.def.defaultTtlSeconds,
        minTtlSeconds: range.min,
        maxTtlSeconds: range.max,
        memberMode: cache.def.memberMode,
        entryCount: cache.nodes.reduce((sum, n) => sum + entriesOf(n, now), 0),
        hitCount: cache.nodes.reduce((sum, n) => sum + n.hits, 0),
        missCount: cache.nodes.reduce((sum, n) => sum + n.misses, 0),
        generation: cache.generation,
        lastRefreshedAt: iso(lastRefreshedAt),
        status: pending.length ? 'PENDING' : 'OK',
        enabled: cache.enabled,
        effectiveEnabled: effective(cache),
        pendingNodeIds: pending.map((n) => n.nodeId),
        pendingSince: pending.length ? iso(cache.pendingSince ?? now) : null,
        gadgetKind: cache.def.gadgetKind ?? null,
        usages: usagesOf(cache.def).map((u) => ({ ...u })),
        adminLinks: linksOf(cache.def).map((l) => ({ ...l })),
    };
}

function detail(cache: MockCache, now: number): CacheDetailView {
    const nodes: CacheNodeStateView[] = cache.nodes.map((n) => ({
        nodeId: n.nodeId,
        role: n.role,
        appliedGeneration: n.appliedGeneration,
        appliedEnabled: n.appliedEnabled,
        lastClearedAt: iso(n.lastClearedAt),
        // 따라잡지 못한 서버는 확인도 늦다(그래서 대기다)
        lastCheckedAt: iso(behind(cache, n) && cache.pendingSince != null ? cache.pendingSince - 2_000 : checkedAt(NODES.findIndex((x) => x.nodeId === n.nodeId), now)),
        entryCount: entriesOf(n, now),
        hitCount: n.hits,
        missCount: n.misses,
    }));
    const kind = cache.def.gadgetKind;
    return {
        ...view(cache, now),
        nodes,
        refreshes: cache.refreshes.slice(0, 20),
        settingChanges: [...cache.settingChanges, ...globalChanges].sort((a, b) => Date.parse(b.changedAt) - Date.parse(a.changedAt)).slice(0, 20),
        gadgetPlacements: kind ? (PLACEMENTS[kind] ?? []).map((u) => ({ ...u })) : [],
        distinctConfigs: kind ? cache.def.entries : null,
    };
}

/** 세대 번호를 올리고 서버마다 1~4초 뒤 따라잡게 한다 */
function bump(cache: MockCache, now: number, all: boolean, operatorName: string): void {
    cache.generation += 1;
    cache.pendingSince = now;
    for (const node of cache.nodes) node.applyAt = now + Math.round((0.4 + Math.random()) * POLL_SECONDS * 1000);
    cache.refreshes.unshift({ id: nextId(), requestedAt: iso(now)!, trigger: 'MANUAL', all, source: null, operatorName, generation: cache.generation });
}

function wait(ms = 250): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
}

export default class CacheAdminMock {
    static async overview(): Promise<CacheOverviewView> {
        await wait();
        const now = Date.now();
        return {
            nodes: NODES.map((n, i) => ({ nodeId: n.nodeId, role: n.role, lastCheckedAt: iso(checkedAt(i, now)) })),
            pollSeconds: POLL_SECONDS,
            globalEnabled,
            caches: state(now).map((c) => view(c, now)),
            uncachedGadgets: UNCACHED.map((u) => ({ ...u, includedIn: [...u.includedIn] })),
            previewPages: PREVIEW_PAGES.map((p) => ({ ...p })),
            // 개발 서버는 runtimeConfig(NUXT_PUBLIC_USER_WEB_ORIGIN)가 먼저다 — 목업은 모른다
            publicUrl: null,
        };
    }

    static async detail(cacheKey: string): Promise<CacheDetailView> {
        await wait();
        const now = Date.now();
        return detail(find(cacheKey, now), now);
    }

    static async refresh(cacheKey: string, operatorName: string): Promise<CacheRefreshResultView> {
        await wait();
        const now = Date.now();
        const cache = find(cacheKey, now);
        if (!effective(cache)) throw new ApiError({ status: 409, code: 'CACHE_DISABLED', message: '꺼진 캐시는 비울 항목이 없습니다. 켠 뒤에 갱신해 주세요.' });
        bump(cache, now, false, operatorName);
        return { requestedAt: iso(now)!, caches: [view(cache, now)] };
    }

    static async refreshAll(operatorName: string): Promise<CacheRefreshResultView> {
        await wait(400);
        const now = Date.now();
        const all = state(now);
        // 꺼진 캐시는 비울 항목이 없어 건너뛴다
        for (const cache of all) if (effective(cache)) bump(cache, now, true, operatorName);
        return { requestedAt: iso(now)!, caches: all.map((c) => view(c, now)) };
    }

    static async updateTtl(cacheKey: string, ttlSeconds: number, operatorName: string): Promise<CacheView> {
        await wait();
        const now = Date.now();
        const cache = find(cacheKey, now);
        const range = RANGE[cache.def.kind];
        if (!Number.isInteger(ttlSeconds) || ttlSeconds < range.min || ttlSeconds > range.max) {
            throw new ApiError({
                status: 400,
                code: 'VALIDATION',
                message: '입력값을 확인해 주세요.',
                fields: { ttlSeconds: `이 캐시의 TTL 은 ${range.min}초에서 ${range.max}초 사이여야 합니다.` },
            });
        }
        if (ttlSeconds !== cache.ttlSeconds) {
            cache.settingChanges.unshift(setting(now, 'TTL', { fromSeconds: cache.ttlSeconds, toSeconds: ttlSeconds }, operatorName));
            cache.ttlSeconds = ttlSeconds;
        }
        return view(cache, now);
    }

    /** 전체 캐시 켜기/끄기 — 바뀐 개요 전체를 돌려준다. 항목별 값은 그대로 남는다 */
    static async setGlobalEnabled(enabled: boolean, operatorName: string): Promise<CacheOverviewView> {
        await wait();
        const now = Date.now();
        const all = state(now);
        if (enabled !== globalEnabled) {
            globalEnabled = enabled;
            globalChanges.unshift(setting(now, 'GLOBAL_ENABLED', { enabled }, operatorName));
            // 저장 값이 켜진 캐시만 실제 상태가 바뀐다
            for (const cache of all) if (cache.enabled) scheduleEnabled(cache, now);
        }
        return CacheAdminMock.overview();
    }

    /** 이 캐시 켜기/끄기 — 전체가 꺼져 있으면 409 `CACHE_GLOBAL_DISABLED`(값을 바꿀 수 없다) */
    static async setEnabled(cacheKey: string, enabled: boolean, operatorName: string): Promise<CacheView> {
        await wait();
        const now = Date.now();
        const cache = find(cacheKey, now);
        if (!globalEnabled) throw new ApiError({ status: 409, code: 'CACHE_GLOBAL_DISABLED', message: '전체 캐시가 꺼져 있어 바꿀 수 없습니다. 전체를 켠 뒤 바꿔 주세요.' });
        if (enabled !== cache.enabled) {
            cache.enabled = enabled;
            cache.settingChanges.unshift(setting(now, 'ENABLED', { enabled }, operatorName));
            scheduleEnabled(cache, now);
        }
        return view(cache, now);
    }
}
