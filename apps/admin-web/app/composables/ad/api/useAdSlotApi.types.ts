import type { AdAdvertiser, AdBookingStatus, AdStatPlacement, AdType } from '~/composables/ad/api/useAdApi.types';
import type { BizAmount, BizDomain, BizPaymentMethod, BizPeriod, BizTakedownCause } from '~/composables/bizapplication/api/useBizApplicationApi.types';

/**
 * 광고 자리 관리(`/ads/slots`) — 서버와 주고받는 모양(목업 단계 — 계약 `docs/features/ad-slot-manager-contract.md` 「API 후보」).
 * 광고는 **자리마다가 아니라 종류마다** 관리된다 — 같은 종류의 자리를 누르면 같은 목록이다(드로어 위 「나가는 곳」).
 * 광고 종류는 광고 일정표의 유형(`AdType` = 서버 `BizProduct`) 그대로다.
 */

/** 빈 자리를 애드센스가 채우는가 — 나가는 자리를 다 채움 · 일부만(한 줄의 가젯 자리, 날개 양쪽 등) · 비어 있음(애드센스 끔 또는 단위 없음) · 애드센스 틀이 없는 종류(핫딜 자리 등) */
export type AdSlotAdSense = 'FILLS' | 'PARTIAL' | 'EMPTY' | 'NA';

/** 종류 하나의 요약 — 미리보기 이름표(종류 이름 · 진행 중 수)와 드로어 머리 */
export interface AdSlotSummary {
    type: AdType;
    /** 지금 나가는 수 */
    running: number;
    /** 같은 시각에 함께 나갈 수 있는 수(요금표 동시 한도) */
    limit: number;
    adsense: AdSlotAdSense;
    /** 이 종류가 나가는 곳(노출 자리 — 광고 성과의 자리 이름 `ad.statPlacement.<값>`) */
    places: AdStatPlacement[];
}

/** `GET /api/admin/ads/slots` — 종류마다 요약(`AdType` 차례) */
export interface AdSlotSummaryView {
    types: AdSlotSummary[];
}

/** 드로어 상태 거르기 — 전체(빈 값) · 진행 중 · 예약 · 입금 대기 · 내림 */
export type AdSlotStatusFilter = Extract<AdBookingStatus, 'RUNNING' | 'SCHEDULED' | 'AWAITING_DEPOSIT' | 'TAKEN_DOWN'>;
export const AD_SLOT_STATUS_FILTERS: readonly AdSlotStatusFilter[] = ['RUNNING', 'SCHEDULED', 'AWAITING_DEPOSIT', 'TAKEN_DOWN'];

/** 광고 카드 한 장 — 드로어 카드와 상세가 같이 쓴다(상세는 따로 받지 않는다) */
export interface AdSlotCard {
    /** 광고 건 id(= 신청 건 id) — 「기존 서랍에서 열기」는 이 id 로 광고 건 상세(`/api/admin/ads/{id}`)를 받는다 */
    id: string;
    type: AdType;
    /** 카드 제목 — 배너는 대체 글(없으면 광고주 이름), 한 줄은 광고 글 */
    title: string;
    status: AdBookingStatus;
    /** 내림 까닭(긴급·사업자 정지·광고주 정지·중단 — 상태 태그 「정지 중」·「중단」을 가른다) */
    takedownCause: BizTakedownCause | null;
    /** 지금 소재 그림 — 한 줄 광고·그림 없는 핫딜 자리는 null */
    imageUrl: string | null;
    /** 한 줄 광고 글(배너는 null) */
    text: string | null;
    period: BizPeriod;
    /** 끝까지 남은 날(오늘 KST 부터 끝 날짜까지, 끝 포함 — 끝났으면 0) */
    remainingDays: number;
    /** 최근 7일 노출·클릭(같은 방문자 하루 1번, 봇 제외 — 광고 성과와 같은 셈) */
    impressions7d: number;
    clicks7d: number;
    /** 광고주 — 광고주 줄도 사업자도 없이 이름만 있는 건은 id 가 null(일정표 광고주 규칙) */
    advertiser: { id: string | null; name: AdAdvertiser['name']; kind: AdAdvertiser['kind'] };
    paymentMethod: BizPaymentMethod;
    amount: BizAmount;
    /** 업무 도메인 — 「심사에서 열기」 */
    domain: BizDomain;
    /** 소재 링크 — 없으면 null */
    linkUrl: string | null;
    /** 링크가 예시 주소(example.com 등)인가 — 서버가 판정한다 */
    exampleUrl: boolean;
}

/** 거르기마다 건수(빈 키 = 전체) */
export type AdSlotCounts = Record<AdSlotStatusFilter | 'ALL', number>;

/** `GET /api/admin/ads/slots/{type}?status=` — 그 종류의 광고(진행 중 → 예약 → 입금 대기 → 내림, 같은 상태는 시작이 빠른 차례) */
export interface AdSlotListView {
    type: AdType;
    items: AdSlotCard[];
    counts: AdSlotCounts;
}

/** `POST /api/admin/ads/takedown` — 여러 건을 사유 하나로 한 번에 내린다(기존 단건 긴급 내림과 같은 규칙: 사유 5~200자 · 진행·예약 건만 · 감사) */
export interface AdSlotTakedownRequest {
    ids: string[];
    reason: string;
    notifyAdvertiser: boolean;
}

/** 내리기 결과 — 내린 수와 못 내린 건(이미 상태가 바뀐 건 등, 서버 문구 그대로) */
export interface AdSlotTakedownResult {
    takenDown: number;
    failed: { id: string; message: string }[];
}
