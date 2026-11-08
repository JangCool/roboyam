/**
 * 캐시 관리 API(`useCacheAdminApi`)가 주고받는 모양 — 서버 DTO 이름 후보 그대로 둔다(⚠ 화면 확정 전 목업, 서버 API 없음 · 2026-11-08).
 * 캐시 이름·대상 설명·갱신 기록의 「무엇을 저장해서」·운영자 이름은 서버가 요청 언어로 풀어 준 글이다(배치 이름과 같은 관례) — 화면 번역 파일에 두지 않는다.
 */

/**
 * 분류 — 공개 화면(공개 API 응답 — 배치·목록) · 가젯(가젯 데이터 — 유형별, 서버 `HomeGadgetKinds`) · 설정 사본(설정 표를 읽어 둔 것) · 내부 규칙(서버가 판정에 쓰는 규칙표).
 * 가젯 데이터 캐시의 항목 키는 그 가젯의 설정 지문이다 — 같은 설정의 가젯이면 홈·가젯 페이지·게시판 레이아웃 어디에 있든 같은 항목을 함께 쓴다(배치 캐시와 따로).
 */
export type CacheKind = 'PUBLIC_VIEW' | 'GADGET' | 'SETTINGS_COPY' | 'INTERNAL_RULE';

/** 가젯을 놓는 곳 — 홈 · 가젯 페이지 · 게시판 레이아웃 */
export type CacheGadgetPlace = 'HOME' | 'GADGET_PAGE' | 'BOARD_LAYOUT';

/** 적용 서버 — 공개 사이트 api 서버(여러 대) · 관리자 서버 */
export type CacheServerRole = 'API' | 'ADMIN';

/** 회원별 처리 — 공용(누가 봐도 같은 응답) · 공용 + 회원 덧입힘(공용 부분만 캐시하고 차단·메모·메뉴 거름은 꺼낸 뒤 회원 기준으로 덧입힌다) */
export type CacheMemberMode = 'SHARED' | 'SHARED_WITH_MEMBER_OVERLAY';

/** 상태 — 정상(모든 서버가 지금 세대를 반영) · 갱신 대기(아직 확인하지 않은 서버가 있음) */
export type CacheStatus = 'OK' | 'PENDING';

/** 갱신 기록의 계기 — 수동(관리자가 「지금 갱신」·「전체 갱신」) · 자동(관리자 화면에서 관련 설정 저장). TTL 만료는 기록하지 않는다 */
export type CacheRefreshTrigger = 'MANUAL' | 'AUTO_SAVE';

/** 서버 한 대(노드)의 요약 — 위쪽 요약의 서버 수와 서랍의 서버별 상태가 쓴다 */
export interface CacheNodeSummaryView {
    /** 서버 이름(노드 ID) — `api-1` · `api-2` · `admin-1` */
    nodeId: string;
    role: CacheServerRole;
    /** 이 서버가 마지막으로 세대 번호를 확인한 시각(ISO-8601 UTC) — 몇 초마다 확인한다 */
    lastCheckedAt: string | null;
}

/** 캐시 목록 한 줄 — 여러 서버 값은 서버가 합쳐 준다(항목 수·적중·누락은 합, 마지막 갱신은 가장 늦은 서버) */
export interface CacheView {
    /** 캐시 키 — `home.gadgets` */
    cacheKey: string;
    /** 이름(서버가 요청 언어로) */
    displayName: string;
    /** 대상 — API 경로(`GET /api/home/gadgets`) 또는 설명(서버 글) */
    target: string;
    /** `target` 이 API 경로인가 — 경로면 고정폭 글자로 보인다 */
    targetIsPath: boolean;
    kind: CacheKind;
    /** 이 캐시를 두는 서버 종류(카탈로그 차례: API → ADMIN) */
    servers: CacheServerRole[];
    /** 지금 TTL(초) */
    ttlSeconds: number;
    /** 기본 TTL(초) — 코드에 적힌 값 */
    defaultTtlSeconds: number;
    /** 허용 범위(초) — 캐시마다 다르다. 판정은 서버가 한다(400 `fields.ttlSeconds`) */
    minTtlSeconds: number;
    maxTtlSeconds: number;
    memberMode: CacheMemberMode;
    /** 지금 들고 있는 항목 수(모든 서버 합) */
    entryCount: number;
    /** 적중·누락 수(모든 서버 합, 서버를 띄운 뒤부터 센 값) */
    hitCount: number;
    missCount: number;
    /** 이 캐시의 지금 세대 번호(DB) — 「지금 갱신」마다 1 오른다 */
    generation: number;
    /** 마지막으로 비운 시각 — 가장 늦은 서버 기준(그 서버까지 비워야 「모두 비웠다」). 한 번도 안 비웠으면 null(서버를 띄운 뒤 그대로) */
    lastRefreshedAt: string | null;
    /** 반영 상태 — 정상 · 갱신 대기(세대 번호나 켜기/끄기를 아직 따라가지 않은 서버가 있음). 꺼짐 표시는 `effectiveEnabled` 로 가른다 */
    status: CacheStatus;
    /** 이 캐시의 켜기/끄기(저장된 값) — 전체가 꺼져 있어도 값은 남는다 */
    enabled: boolean;
    /** 실제로 쓰는가 = 전체 켜짐 && 이 캐시 켜짐. 꺼지면 요청마다 원본(DB)을 읽고 담긴 항목은 비운다 */
    effectiveEnabled: boolean;
    /** 아직 지금 세대를 반영하지 않은 서버(갱신 대기일 때만) */
    pendingNodeIds: string[];
    /** 갱신 대기가 시작된 시각(마지막 갱신 요청) — 오래 걸리면 화면이 안내한다. 대기가 아니면 null */
    pendingSince: string | null;
    /** 가젯 데이터 캐시면 그 가젯 종류(`POST_LIST` …), 아니면 null */
    gadgetKind: string | null;
    /**
     * 쓰이는 곳 — 이 캐시의 값이 공개 화면 어디에 보이고(화면 영역), 어디서 규칙으로 쓰이며(동작), 서버 안에서만 쓰이는지(서버 내부). 서버 캐시 정의(코드)에 함께 적는 고정 정보.
     * 미리보기의 대표 쪽은 첫 「화면 영역·동작」 줄 중 경로가 있는 것이다(`UtilCache.representativePath`).
     */
    usages: CacheUsageView[];
    /** 관련 관리 화면 — 이 캐시의 데이터를 운영자가 바꾸는 관리자 화면(관리자 주소) */
    adminLinks: CacheAdminLinkView[];
}

/** 쓰이는 곳의 종류 — 화면 영역(값이 그대로 보인다) · 동작(누르거나 쓸 때 규칙이 적용된다 — 단추·입력칸) · 서버 내부(화면 없이 서버에서만) */
export type CacheUsageKind = 'AREA' | 'ACTION' | 'SERVER';

/** 쓰이는 곳 한 줄 */
export interface CacheUsageView {
    kind: CacheUsageKind;
    /** 이름(서버 글) */
    label: string;
    /** 한 줄 설명(서버 글) */
    description: string;
    /**
     * 공개 사이트 경로(쿼리 포함 가능 — `/write?board=hotdeal`) — 미리보기로 열 쪽. 서버 내부이거나 특정 글·상품을 열어야 보이는 곳(예시 경로를 정할 수 없을 때)은 null
     */
    path: string | null;
    /**
     * 글·상품 하나를 열어야 보이는 곳의 예시 경로 규칙(`path` 가 null 일 때만) — 미리보기가 예시 하나의 번호를 골라 `template` 의 `{id}` 에 넣어 연다.
     * 지금은 화면이 기존 관리자 API 로 예시를 고른다(`useCacheSamples`). 서버 단계에서 캐시 API 가 `sampleId` 를 직접 주면 그 값을 쓴다.
     */
    sample: CacheUsageSample | null;
    /** 로그인한 회원에게만 보이는 쪽인가 */
    loginRequired: boolean;
}

/** 예시의 종류 — 진행 중인 핫딜 글 · 판매 중인 포인트 상점 상품 · 모집 중인 공동구매 */
export type CacheSampleKind = 'HOTDEAL_POST' | 'POINT_SHOP_ITEM' | 'GROUP_BUY';

/** 예시 경로 규칙 */
export interface CacheUsageSample {
    kind: CacheSampleKind;
    /** 공개 사이트 경로 틀 — `{id}` 자리에 예시 번호(`/posts/{id}` · `/point-shop/{id}` · `/group-buys/{id}`) */
    template: string;
    /** 서버가 고른 예시 번호(서버 단계 — 지금은 늘 null, 화면이 고른다) */
    sampleId: string | null;
}

/** 관련 관리 화면 */
export interface CacheAdminLinkView {
    /** 이름(서버 글) — 화면은 관리자 메뉴에 같은 주소가 있으면 메뉴 이름을 쓴다 */
    label: string;
    /** 관리자 화면 주소(`/settings/terms` …) */
    path: string;
}

/** 캐시하지 않는 가젯 종류 — 목록 아래 안내에 보인다(이유는 서버 글) */
export interface CacheExclusionView {
    /** 가젯 종류(`AD` …) — 공개 화면 영역 키는 `nocache:<종류>` */
    gadgetKind: string;
    displayName: string;
    /** 왜 캐시하지 않는가(서버 글) */
    reason: string;
    /** 따로 캐시하지 않고 함께 담기는 캐시(바로가기·안내 글은 배치 캐시). 없으면 빈 배열 */
    includedIn: string[];
}

/** 적용 영역 미리보기에서 고를 수 있는 쪽 */
export interface CachePreviewPageView {
    /** 공개 사이트 경로(`/` · `/latest` · `/boards/{slug}` · `/p/{key}`) */
    path: string;
    /** 이름(서버 글 — 게시판·가젯 페이지 이름 포함) */
    label: string;
}

/** 이 가젯 유형을 놓은 곳 한 줄(가젯 데이터 캐시만 — 「쓰이는 곳」 `CacheUsageView` 와 다르다: 배치에 놓인 가젯 수를 센다) */
export interface CacheGadgetPlacementView {
    place: CacheGadgetPlace;
    /** 쪽 이름(서버 글 — 「홈」 · 가젯 페이지 이름 · 「게시판 레이아웃 — 테마」) */
    name: string;
    /** 그 쪽 경로(미리보기로 열 수 있으면). 게시판 레이아웃처럼 여러 게시판에 같이 쓰면 대표 게시판 경로 */
    path: string | null;
    /** 그 쪽에 놓인 이 종류 가젯 수 */
    count: number;
}

/** 캐시 관리 화면 한 번에 받는 것 — 위쪽 요약 + 목록 */
export interface CacheOverviewView {
    /** 이 캐시를 쓰는 서버들(지금 살아 있는 것) */
    nodes: CacheNodeSummaryView[];
    /** 각 서버가 세대 번호를 확인하는 주기(초) — 설정 값, 화면에서는 읽기 전용 */
    pollSeconds: number;
    caches: CacheView[];
    /** 캐시하지 않는 가젯 종류 */
    /** 전체 캐시 스위치(DB 값) — 끄면 모든 캐시가 꺼진다 */
    globalEnabled: boolean;
    uncachedGadgets: CacheExclusionView[];
    /** 적용 영역 미리보기에서 고를 수 있는 쪽 */
    previewPages: CachePreviewPageView[];
    /** 공개 사이트 주소(사이트 공개 주소 설정) — 빌드한 관리자 화면이 미리보기 iframe origin 으로 쓴다(`useUserWebOrigin.setFromServer`). 모르면 null */
    publicUrl: string | null;
}

/** 서랍의 서버별 상태 한 줄 */
export interface CacheNodeStateView {
    nodeId: string;
    role: CacheServerRole;
    /** 이 서버가 반영한 세대 번호 — 캐시의 `generation` 보다 작으면 갱신 대기 */
    appliedGeneration: number;
    /** 이 서버가 따라간 켜짐 상태 — 캐시의 `effectiveEnabled` 와 다르면 갱신 대기 */
    appliedEnabled: boolean;
    /** 이 서버가 마지막으로 비운 시각. 띄운 뒤 안 비웠으면 null */
    lastClearedAt: string | null;
    /** 마지막으로 세대 번호를 확인한 시각 */
    lastCheckedAt: string | null;
    entryCount: number;
    hitCount: number;
    missCount: number;
}

/** 갱신 기록 한 줄 */
export interface CacheRefreshLogView {
    /** 기록 번호 */
    id: string;
    /** 요청한 시각 */
    requestedAt: string;
    trigger: CacheRefreshTrigger;
    /** 「전체 갱신」으로 함께 비웠는가 */
    all: boolean;
    /** 자동(저장)일 때 무엇을 저장해서인지(서버 글, 예: 「게시판 설정 저장 — 자유게시판」). 수동이면 null */
    source: string | null;
    /** 누가 — 운영자 이름 */
    operatorName: string | null;
    /** 이 갱신으로 오른 세대 번호 */
    generation: number;
}

/** 설정 변경의 종류 — TTL · 이 캐시 켜기/끄기 · 전체 캐시 켜기/끄기 */
export type CacheSettingChangeKind = 'TTL' | 'ENABLED' | 'GLOBAL_ENABLED';

/** 설정 변경 이력 한 줄(TTL 변경과 켜기/끄기를 합친 것 — 최근순) */
export interface CacheSettingChangeView {
    id: string;
    changedAt: string;
    kind: CacheSettingChangeKind;
    /** TTL 일 때만 */
    fromSeconds: number | null;
    toSeconds: number | null;
    /** 켜기/끄기일 때만 — 바꾼 뒤 값 */
    enabled: boolean | null;
    operatorName: string | null;
}

/** 캐시 상세(서랍) — 목록 한 줄 + 서버별 상태 + 최근 기록 */
export interface CacheDetailView extends CacheView {
    nodes: CacheNodeStateView[];
    /** 최근 갱신 기록(최근순, 서버가 자른 몇 건) */
    refreshes: CacheRefreshLogView[];
    /** 설정 변경 이력(TTL · 이 캐시 켜기/끄기 · 전체 켜기/끄기, 최근순) */
    settingChanges: CacheSettingChangeView[];
    /** 가젯 데이터 캐시면 이 가젯 유형을 놓은 곳(아니면 빈 배열) */
    gadgetPlacements: CacheGadgetPlacementView[];
    /** 가젯 데이터 캐시면 서로 다른 설정 수(= 서버 한 대가 가득 찼을 때의 항목 수), 아니면 null */
    distinctConfigs: number | null;
}

/** `PUT /api/admin/caches/settings` · `PUT /api/admin/caches/{cacheKey}/enabled` 본문 */
export interface CacheEnabledRequest {
    enabled: boolean;
}

/** `PUT /api/admin/caches/{cacheKey}/ttl` 본문 */
export interface CacheTtlRequest {
    ttlSeconds: number;
}

/** 「지금 갱신」·「전체 갱신」 응답(202) — 올린 세대 번호와 바뀐 목록 줄 */
export interface CacheRefreshResultView {
    requestedAt: string;
    /** 갱신한 캐시들의 바뀐 줄(전체 갱신이면 전부) — 화면이 목록에 바로 바꿔 끼운다 */
    caches: CacheView[];
}
