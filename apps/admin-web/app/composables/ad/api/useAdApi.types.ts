import type { BizAdType, BizAmount, BizContractFile, BizDeposit, BizDomain, BizPause, BizPauseChannel, BizPaymentMethod, BizPeriod, BizQuote, BizRefundRequest, BizRefundSummary, BizTakedownCause } from '~/composables/bizapplication/api/useBizApplicationApi.types';
import type { SettingHistoryItem, SettingHistoryPage } from '~/composables/common/useSettingHistory.types';

/**
 * 광고 일정표(관리자) — 광고 건 · 유형별 한도 · 날짜별 남은 칸 · 운영자 등록. 서버와 주고받는 모양(서버 `AdAdminView`·`AdAdminRequest`, `AdAdvertiserService.Option`·`Revealed` — V115).
 * 장부 = 신청 건이라 ID·번호·처리 기록 코드(`BizHistoryAction`)는 신청 건과 같다.
 * 광고 건은 사업자 신청 건이 승인되면 생기거나(`APPLICATION`), 운영자가 직접 등록한다(`OPERATOR` — 늘 수동 결제, 광고 심사에서 승인).
 * 기간은 반열림 `[startAt, endAt)` — 화면은 「~10/10 00:00」 처럼 끝 시각을 적고 그 시각은 포함하지 않는다.
 */

export type AdType = BizAdType;

/**
 * 장부에 나올 수 있는 상태 — `SUBMITTED` = 운영자 등록 뒤 광고 심사 대기(04-ia-review §2 — 자리는 승인 때부터 차지한다), `CANCELED` = 승인 뒤 취소.
 * `RUNNING`·`ENDED` 는 서버가 시각으로 계산한다(저장 상태는 SCHEDULED).
 */
export type AdBookingStatus = 'SUBMITTED' | 'AWAITING_PAYMENT' | 'AWAITING_DEPOSIT' | 'SCHEDULED' | 'RUNNING' | 'ENDED' | 'TAKEN_DOWN' | 'CANCELED';
export const AD_BOOKING_STATUSES: readonly AdBookingStatus[] = ['SUBMITTED', 'AWAITING_PAYMENT', 'AWAITING_DEPOSIT', 'SCHEDULED', 'RUNNING', 'ENDED', 'TAKEN_DOWN', 'CANCELED'];

export interface AdTypeSummary {
    type: AdType;
    /** 같은 시각에 함께 나갈 수 있는 수 */
    limit: number;
    running: number;
    scheduled: number;
    /** 결제·입금 대기(자리를 이미 잡고 있다) */
    awaitingPayment: number;
    /** 광고주 일시 정지(끝나지 않음 — 자리를 계속 잡고 있다) */
    paused: number;
    /** 그 밖의 내림(광고주 중단 환불 심사 중 · 긴급 내림 · 사업자 정지 — 되돌리거나 환불 승인 전까지 자리를 잡고 있다) */
    takenDown: number;
}

/** 날짜별 남은 칸 — 0 이면 꽉 참 */
export interface AdDaySlot {
    date: string;
    remaining: number;
    limit: number;
}

/** 광고주 — 사업자이거나 계정 없는 광고주(운영자가 만든다). `id` 는 `adv-…`(광고주)·`biz-…`(광고주와 연결되지 않은 승인 사업자) */
export interface AdAdvertiser {
    id: string;
    kind: 'BUSINESS' | 'ADVERTISER';
    name: string;
    businessId: string | null;
    maskedRegNo: string | null;
    /** 가린 연락처(없으면 가린 이메일) */
    contactMasked: string | null;
}

/** 광고주 연락처 평문(`ad.contact.reveal`) — `hideAt` 이 지나면 화면이 다시 가린다 */
export interface AdAdvertiserRevealed {
    contactName: string | null;
    contactPhone: string | null;
    email: string | null;
    hideAt: string;
}

export interface AdBookingRow {
    id: string;
    type: AdType;
    advertiser: AdAdvertiser;
    period: BizPeriod;
    status: AdBookingStatus;
    /** 승인 전이면 희망 방식 */
    paymentMethod: BizPaymentMethod;
    /** 같은 자리에 여럿이 돌 때의 비중(1~10) */
    weight: number;
    source: 'APPLICATION' | 'OPERATOR';
    /** 신청 건 번호(BA-…) */
    applicationNo: string | null;
    lastActionAt: string;
    /** 업무 도메인 — 「심사에서 열기」 주소(AD → `/ad-reviews`, HOTDEAL → `/hotdeal-reviews`) */
    domain: BizDomain;
    /** 내림(`TAKEN_DOWN`) 까닭 — 달력 점·표 상태 「정지 중」(`ADVERTISER_PAUSE`)·「중단」(`ADVERTISER_STOP`)을 긴급 내림과 가른다(biz-pause-contract). 내려간 적 없으면 null */
    takedownCause: BizTakedownCause | null;
}

export interface AdCalendar {
    summary: AdTypeSummary;
    days: AdDaySlot[];
    /** 그 달에 걸친 장부 건(이름·상태·기간 — 달력 칸에 그린다) */
    bookings: AdBookingRow[];
}

export interface AdCreativeVersion {
    version: number;
    imageUrl: string | null;
    text: string | null;
    linkUrl: string;
    altText: string | null;
    /** 심사 승인 시각·승인자 — 심사 대기(`SUBMITTED`)는 null */
    approvedAt: string | null;
    approvedBy: string | null;
    current: boolean;
}

export interface AdHistory {
    at: string;
    /** 신청 건 처리 기록 코드(`BizHistoryAction`) — `UtilBizApplicationMessages.historyAction` 으로 번역한다 */
    action: string;
    actor: string;
    note: string | null;
}

/** 긴급 내림 뒤 광고주 메일 결과 — 메일 서버가 없으면 FAILED, 받을 주소가 없으면 NO_ADDRESS */
export type AdTakedownMail = 'SENT' | 'FAILED' | 'NO_ADDRESS';
export const AD_TAKEDOWN_MAILS: readonly AdTakedownMail[] = ['SENT', 'FAILED', 'NO_ADDRESS'];

/** 내림 — `cause` EMERGENCY(긴급)·SUSPENDED(사업자 정지)·ADVERTISER_PAUSE·ADVERTISER_STOP(광고주 요청 정지·중단), `mail` 광고주 메일 결과(보내지 않았으면 null) */
export interface AdTakedown {
    at: string;
    cause: BizTakedownCause;
    /** 운영자가 광고주 대신 정지·중단했으면 요청 경로(biz-pause-contract) */
    channel: BizPauseChannel | null;
    /** 운영자 대신 처리 메모(관리자에게만) */
    memo: string | null;
    reason: string | null;
    by: string | null;
    mailSent: boolean;
    mail: AdTakedownMail | null;
    /** 신청 건 상세와 같은 정지 칸(정지할 수 있는 종류의 결제 뒤 건만 — 상세 맨 위 `pause` 와 같은 값) */
    pause: BizPause | null;
}

/** 운영자 등록 건의 등록자 — `me` 면 지금 운영자가 등록했다(사업자 신청 건은 null) */
export interface AdRegistrant {
    id: string;
    name: string;
    me: boolean;
}

export interface AdBookingDetail extends AdBookingRow {
    /** 확정 금액 → 제안 금액 → 요금표 계산 순 */
    amount: BizAmount;
    /** 운영자 등록 때 적은 계약 금액 제안(공급가) */
    proposedSupply: number | null;
    creatives: AdCreativeVersion[];
    onlinePayment: { paidAt: string; pgTxId: string } | null;
    contracts: BizContractFile[];
    deposit: BizDeposit | null;
    takedown: AdTakedown | null;
    /** 가장 최근 환불(신청 건 상세와 같은 모양) */
    refund: BizRefundRequest | null;
    memo: string | null;
    registrant: AdRegistrant | null;
    history: AdHistory[];
    /** 광고주 요청 일시 정지·중단 정보(신청 건 상세 `pause` 와 같은 모양 — 정지할 수 있는 종류의 결제 뒤 건만, 아니면 null) */
    pause: BizPause | null;
    /** 환불 합계(신청 건 상세 `refundSummary` 와 같은 값 — 결제 전이면 null) */
    refundSummary: BizRefundSummary | null;
}

export interface AdBookingQuery {
    type: AdType;
    status?: AdBookingStatus;
    q?: string;
    page: number;
    /** 100 까지 */
    size: number;
}

export interface AdBookingPage {
    items: AdBookingRow[];
    total: number;
}

/** 기간 판정 — 그 기간 가장 빠듯한 날의 남은 칸, 꽉 찬 날, 요금표 계산값 */
export interface AdAvailability {
    remaining: number;
    limit: number;
    fullDates: string[];
    quote: BizQuote;
}

/** 새 광고주 — 같은 이름 400 `fields.name`, 연락처 숫자 9~11자리 `fields.contactPhone`, 이메일 형식 `fields.email` */
export interface AdAdvertiserCreateRequest {
    name: string;
    contactName: string;
    contactPhone: string;
    email: string;
    memo: string;
    /** 연결할 사업자 신원 ID(선택 — 이미 연결된 사업자면 400 `fields.businessId`) */
    businessId?: string;
}

/** 운영자가 올린 광고 이미지(`POST /images` — PNG·JPG·GIF·WEBP 2MB) */
export interface AdImage {
    id: string;
    fileName: string;
    size: number;
    mimeType: string;
    url: string;
}

/** 소재 — 배너는 `imageFileId`(올린 이미지)·`linkUrl`(https)·`altText`, 한 줄은 `text`(40자)·`linkUrl` */
export interface AdManualCreative {
    imageFileId: string | null;
    text: string | null;
    linkUrl: string;
    altText: string | null;
}

/**
 * 운영자 등록 = 신청 건 `SUBMITTED`(source=OPERATOR) — 광고 심사에서 승인 → 계약서 사본 + 입금 확인 → [예약 확정].
 * `supply` 는 계약 금액 제안(승인 때 확정, 요금표와 다르면 승인 창에서 사유). 계약서는 오프라인으로 먼저 받았을 때만 미리 붙인다(선택).
 * 광고 종류(배너 넷·한 줄)만 — 핫딜 노출 자리는 400 `fields.type`. 자리가 없으면 409 `BIZ_SLOT_FULL`(등록 때는 잠그지 않고 안내 판정).
 */
export interface AdManualCreateRequest {
    advertiserId: string;
    type: AdType;
    period: BizPeriod;
    weight: number;
    creative: AdManualCreative;
    supply: number;
    contractFileIds: string[];
    memo: string;
}

export interface AdTakedownRequest {
    reason: string;
    notifyMail: boolean;
}

/** 되돌릴 때 보상(F5) — 중단 시간만큼 끝 시각 연장(자리 없으면 409 `BIZ_SLOT_FULL`) · 일할 환불 */
export type AdRestoreCompensation = 'EXTEND' | 'PRORATE_REFUND';

export interface AdRestoreRequest {
    compensation: AdRestoreCompensation;
    reason: string;
}

/* ── 광고 자리 설정(`/ads/placements`) ── */

/** 글 보기 광고 유형 — 한 줄(기본)·띠 배너·네모 배너 */
export type AdPostInlineType = 'TEXT_LINE' | 'STRIP_BANNER' | 'SQUARE_BANNER';

/** 글 카드와 댓글 카드 사이(기본) · 본문 바로 아래 */
export type AdPostInlinePosition = 'BETWEEN_CARDS' | 'AFTER_BODY';

/** 날개 배너를 둘 화면 — 홈 · 가젯 페이지 · 게시판 목록 · 글 보기 */
export type AdWingScreen = 'HOME' | 'PAGE' | 'BOARD' | 'POST' | 'FEED' | 'GROUP_BUY' | 'GROUPS';

export interface AdPostInlineSetting {
    enabled: boolean;
    adType: AdPostInlineType;
    position: AdPostInlinePosition;
}

export interface AdWingSetting {
    left: boolean;
    right: boolean;
    screens: AdWingScreen[];
}

export interface AdBoardListSetting {
    enabled: boolean;
    /** 일반 글(공지·고정 제외) 몇 번째 뒤 — 1~20 */
    after: number;
    /** 한 쪽 광고 수 — 1~2(V155, 기본 1) */
    count: number;
    /** 첫 광고와 둘째 광고 사이 일반 글 수 — 3~20(V155, 기본 10) */
    gap: number;
}

/** 19금 게시판·19금 글에도 광고(목록 한 줄·글 보기·핫딜 노출·날개)를 낼지 — 기본 false. 게시판 「광고 숨김」·소모임은 이 값과 상관없이 늘 빠진다 */
export interface AdAdultSetting {
    enabled: boolean;
}

/**
 * 환불 묶음(V127 — 탭 `?tab=refund`) — 진행 중 중단의 중도 해지 수수료율 0~30 정수 %(기본 10). 광고·사업자 핫딜 공통이고,
 * 신청 건을 낼 때(사업자 신청·운영자 등록) 그때 비율이 그 건에 고정된다(바꿔도 이미 낸 건은 그대로). 시작 전 취소·운영 귀책 보상에는 붙지 않는다.
 */
export interface AdRefundSetting {
    stopFeeRate: number;
}

/** `PUT /api/admin/ads/placements` 본문 — `refund` 는 서버에선 선택(빠지면 그대로)이지만 화면은 늘 보낸다(400 `fields.refund.stopFeeRate`) */
export interface AdPlacementsRequest {
    postInline: AdPostInlineSetting;
    wings: AdWingSetting;
    boardList: AdBoardListSetting;
    adult: AdAdultSetting;
    refund: AdRefundSetting;
}

/** `GET`·`PUT /api/admin/ads/placements` 응답 — 저장한 적 없으면 서버 기본값 + `updatedAt/updatedBy: null` */
export interface AdPlacementsView extends AdPlacementsRequest {
    updatedAt: string | null;
    updatedBy: { id: string; name: string } | null;
}

/**
 * 광고 자리 설정 변경 이력의 칸 코드(`changes[].field`) — 서버가 이 차례로 준다.
 * 값 글자: 켜기 칸은 `"true"`/`"false"` · 유형·위치는 enum 이름 · `wings.screens` 는 HOME·PAGE·BOARD·POST 차례 쉼표 이음(빈 목록은 `""`) · `boardList.after`·`refund.stopFeeRate` 는 숫자 글자.
 */
export type AdPlacementHistoryField =
    | 'postInline.enabled'
    | 'postInline.adType'
    | 'postInline.position'
    | 'wings.left'
    | 'wings.right'
    | 'wings.screens'
    | 'boardList.enabled'
    | 'boardList.after'
    | 'boardList.count'
    | 'boardList.gap'
    | 'adult.enabled'
    | 'refund.stopFeeRate';

/** `GET /api/admin/ads/placements/history` 한 줄 — 메일 서버 설정 이력과 같은 모양(공용 `SettingHistoryItem`, 출처는 늘 `ADMIN`) */
export type AdPlacementHistoryItem = SettingHistoryItem;
export type AdPlacementHistoryPage = SettingHistoryPage<AdPlacementHistoryItem>;

/* ── 광고 성과(`/ads/performance`·일정표 서랍 「성과」 탭) — 서버 DTO 정본 shared `ad/dto/AdStatsView`·admin `ad/dto/AdStatsAdminView`. 집계 원천 shared V118 `ad_daily_stats(application_id, version, day, placement, impressions, clicks)` ── */

/** 노출 자리 — `ad_daily_stats.placement` 값. 화면 이름은 `ad.statPlacement.<값>` */
export type AdStatPlacement = 'HOME' | 'PAGE' | 'BOARD' | 'POST_INLINE' | 'BOARD_LIST' | 'WING_LEFT' | 'WING_RIGHT' | 'HOTDEAL_TOP' | 'HOTDEAL_CATEGORY';
export const AD_STAT_PLACEMENTS: readonly AdStatPlacement[] = ['HOME', 'PAGE', 'BOARD', 'POST_INLINE', 'BOARD_LIST', 'WING_LEFT', 'WING_RIGHT', 'HOTDEAL_TOP', 'HOTDEAL_CATEGORY'];

/**
 * 노출·클릭 수 — 같은 방문자·같은 소재 버전·같은 자리는 KST 하루 노출 1번·클릭 1번, 봇 제외.
 * 클릭은 그날 노출 여부를 보지 않아 클릭률이 드물게 100% 를 넘을 수 있다(화면은 그대로 계산 — `UtilAdStats.ctr`, 노출 0 이면 「—」).
 */
export interface AdStatCounts {
    impressions: number;
    clicks: number;
}

/** 그날 상태 — 하루 중 일부라도 광고주 일시 정지(`ADVERTISER_PAUSE`)가 걸쳤으면 PAUSED, 그 밖의 내림(긴급·사업자 정지·광고주 중단)이 걸쳤으면 TAKEN_DOWN(내림이 우선) */
/** 진행 중인데 노출이 멈춘 까닭(사업자 핫딜 글 건 — 서버 `BizDealPostReader.exposureStop`) */
export type AdExposureStop = 'DEAL_ENDED' | 'POST_HIDDEN' | 'PREFIX_MISSING' | 'NOT_POSTED';

export type AdStatDayState = 'SHOWING' | 'PAUSED' | 'TAKEN_DOWN';

/** 날짜 하나(`YYYY-MM-DD`, KST) */
export interface AdStatDay extends AdStatCounts {
    date: string;
    state: AdStatDayState;
}

/** 운영자 성과의 날짜 하나(합계 — 건마다 상태가 달라 `state` 가 없다) */
export interface AdStatDayTotal extends AdStatCounts {
    date: string;
}

/** 범위와 겹치는 정지·내림 구간(처리 기록에서 계산) — `to` 가 null 이면 아직 멈춰 있다 */
export interface AdStatDowntime {
    from: string;
    to: string | null;
    cause: BizTakedownCause;
}

/** 날짜 범위(`YYYY-MM-DD` KST, 양끝 포함) */
export interface AdStatRange {
    from: string;
    to: string;
}

export interface AdStatPlacementRow extends AdStatCounts {
    placement: AdStatPlacement;
}

export interface AdStatVersionRow extends AdStatCounts {
    version: number;
    approvedAt: string | null;
    current: boolean;
}

/**
 * 한 건의 성과 — `GET /api/admin/ads/{id}/stats?from&to`(`ad.read`, 장부 유형 줄이 없는 건 404). 사업자 쪽은 `GET /api/business/applications/{id}/stats`(본인 건만).
 * `range` 를 빼면 기본 = 시작 날짜 ~ min(끝 날짜, 오늘). `days` 는 range 안 모든 날짜(0 포함), `byPlacement` 는 0 인 자리를 빼고 노출 차례.
 */
export interface AdApplicationStats {
    range: AdStatRange;
    /** 응답을 만든 시각 — 오늘 숫자는 계속 늘어난다(「오늘은 집계 중」) */
    asOf: string;
    totals: AdStatCounts;
    days: AdStatDay[];
    byPlacement: AdStatPlacementRow[];
    /** 소재 버전 차례(오래된 것 먼저) */
    byVersion: AdStatVersionRow[];
    downtimes: AdStatDowntime[];
    /** 진행 중 사업자 핫딜 글 건의 노출 자리가 지금 나가지 않는 까닭(공개 노출 판정과 같음 — 딜 종료·글 숨김·말머리 없음·게시 전), 노출 중이거나 다른 종류면 null */
    exposureStop: AdExposureStop | null;
}

/** 운영자 성과 거르기 — 기간은 필수, 유형·광고주는 선택 */
export interface AdStatsQuery {
    from: string;
    to: string;
    type?: AdType;
    advertiserId?: string;
}

export interface AdStatTypeRow extends AdStatCounts {
    type: AdType;
    /** 기간 안에 노출이 있었던 건 수 */
    bookings: number;
}

export interface AdStatAdvertiserRow extends AdStatCounts {
    advertiser: Pick<AdAdvertiser, 'id' | 'name' | 'kind'>;
    bookings: number;
}

/**
 * 운영자 성과 — `GET /api/admin/ads/stats?from&to&type&advertiserId`(`ad.read`).
 * `totals.bookings` = 기간 안에 노출이 1 이상인 건 수. `byType`·`byPlacement` 는 0 인 줄을 빼고 노출 차례, `topAdvertisers` 는 노출 차례 상위 10.
 */
export interface AdStatsOverview {
    asOf: string;
    range: AdStatRange;
    /** 거른 광고주(주소에 `advertiserId` 만 있어도 이름을 보이게 — 거르지 않았으면 null) */
    advertiser: Pick<AdAdvertiser, 'id' | 'name' | 'kind'> | null;
    totals: AdStatCounts & { bookings: number };
    days: AdStatDayTotal[];
    byType: AdStatTypeRow[];
    byPlacement: AdStatPlacementRow[];
    topAdvertisers: AdStatAdvertiserRow[];
}

/** 건별 표 정렬 — 노출 · 클릭 · 클릭률(노출 0 인 건은 클릭률 정렬에서 늘 뒤) */
export type AdStatsSort = 'impressions' | 'clicks' | 'ctr';
export const AD_STATS_SORTS: readonly AdStatsSort[] = ['impressions', 'clicks', 'ctr'];

export interface AdStatsBookingQuery extends AdStatsQuery {
    sort: AdStatsSort;
    /** 기본 desc(서버 후보 — 노트에 없던 칸, 화면 판단) */
    dir: 'asc' | 'desc';
    page: number;
    /** 100 까지 */
    size: number;
}

/** 건별 한 줄 — 일정표 줄(`AdBookingRow`) + 기간 안 숫자 */
export type AdStatsBookingRow = AdBookingRow & AdStatCounts & {
    /** 진행 중 사업자 핫딜 글 건의 노출 자리가 지금 나가지 않는 까닭(공개 노출 판정과 같음 — 딜 종료·글 숨김·말머리 없음·게시 전), 노출 중이거나 다른 종류면 null */
    exposureStop: AdExposureStop | null;
};

/** `GET /api/admin/ads/stats/bookings?from&to&type&advertiserId&sort&dir&page&size` — 기간과 겹치고 시작한 건(노출 0 포함) */
export interface AdStatsBookingPage {
    items: AdStatsBookingRow[];
    total: number;
}
