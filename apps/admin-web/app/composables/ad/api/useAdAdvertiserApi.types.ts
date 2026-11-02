import type { AdAdvertiser, AdBookingRow } from '~/composables/ad/api/useAdApi.types';
import type { BusinessServiceStatus, BusinessStatus } from '~/composables/business/api/useBusinessApi.types';

/**
 * 광고 › 광고주 관리(`/ad-advertisers`) — 서버와 주고받을 모양(⚠ 목업 — 서버 없음, 모양은 후보).
 * 광고주 = 계정 없는 광고주(운영자가 직접 등록, `adv-<id>`) · 사업자 신원에 1:1 로 붙은 광고주(`adv-<id>` + `businessId`) · 광고주 줄이 없는 승인 사업자(`biz-<신원 id>`).
 * ID 모양은 일정표 `AdAdvertiser.id` 와 같다(ad-contract 「광고주 ID」).
 */

export type AdAdvertiserKind = AdAdvertiser['kind'];

/** 직접 등록 광고주의 상태 — 보관하면 목록 기본 거르기·운영자 등록의 광고주 찾기에서 빠진다(기록·성과는 그대로). 사업자 광고주는 늘 ACTIVE(사업자 신원 상태를 따른다) */
export type AdAdvertiserState = 'ACTIVE' | 'ARCHIVED';

/** 연결된 사업자 신원 — 사업자 목록 서랍(`/businesses?id=`)으로 간다. 이름·등록번호·연락처의 원천 */
export interface AdAdvertiserBusiness {
    id: string;
    /** 상호(광고주 이름 = 상호) */
    name: string;
    maskedRegNo: string;
    status: BusinessStatus;
    /** 광고(`AD`) 서비스 줄 상태 — 핫딜만 쓰는 사업자는 `OFF` */
    adService: BusinessServiceStatus;
}

/** 광고 건 수 — 진행 중 · 예약(시작 전) · 대기(심사·결제·입금 대기) · 전체(취소 포함) */
export interface AdAdvertiserCounts {
    running: number;
    scheduled: number;
    waiting: number;
    total: number;
}

export interface AdAdvertiserRow {
    id: string;
    kind: AdAdvertiserKind;
    name: string;
    /** 가린 연락처(광고 담당자 → 없으면 가린 이메일 → 사업자는 신원 연락처) */
    contactMasked: string | null;
    business: AdAdvertiserBusiness | null;
    state: AdAdvertiserState;
    counts: AdAdvertiserCounts;
    /** 누적 결제(VAT 포함, 온라인 결제 + 입금 확인 합) — 환불은 빼지 않는다 */
    paidTotal: number;
    /** 최근 광고일 = 결제 뒤 건(예약·진행·종료·내림) 중 이미 시작한 건의 가장 늦은 시작 시각(앞으로 시작할 예약은 빼서 미래 날짜가 나오지 않는다). 없으면 null */
    lastAdAt: string | null;
    createdAt: string;
}

export type AdAdvertiserSort = 'lastAdAt' | 'paidTotal' | 'name';

export interface AdAdvertiserQuery {
    kind?: AdAdvertiserKind;
    /** 기본 ACTIVE — `ALL` 은 보관 포함 */
    state?: AdAdvertiserState | 'ALL';
    /** 이름(상호) · 등록번호 끝자리 */
    q?: string;
    sort?: AdAdvertiserSort;
    dir?: 'asc' | 'desc';
    page: number;
    size: number;
}

export interface AdAdvertiserPage {
    items: AdAdvertiserRow[];
    total: number;
    /** 거르기 전 종류별 수(탭 숫자) — 보관 거르기는 따른다 */
    counts: Record<AdAdvertiserKind | 'ALL', number>;
}

/** 가린 값 — 칸이 비었으면 null */
export interface AdAdvertiserMasked {
    contactName: string | null;
    contactPhone: string | null;
    email: string | null;
}

/** 결제·환불 합계(VAT 포함) — 결제 = 온라인 + 수동(입금 확인), 환불 = 승인된 환불 합 */
export interface AdAdvertiserMoney {
    paid: number;
    online: number;
    manual: number;
    refunded: number;
}

/** 광고주 서랍의 광고 건 한 줄 — 일정표 `Row` + 그 건의 결제·환불 */
export type AdAdvertiserBooking = AdBookingRow & { paid: number; refunded: number };

/** 연락처 평문 열람 기록(`ad_advertiser_views`) */
export interface AdAdvertiserView {
    at: string;
    actor: string;
    reason: string;
}

export type AdAdvertiserAction = 'CREATE' | 'UPDATE' | 'ARCHIVE' | 'UNARCHIVE';

/** 처리 기록 — `fields` 는 바뀐 칸 이름(연락처 값은 남기지 않는다) */
export interface AdAdvertiserEvent {
    at: string;
    action: AdAdvertiserAction;
    actor: string;
    fields: string[];
}

export interface AdAdvertiserDetail extends AdAdvertiserRow {
    masked: AdAdvertiserMasked;
    /** 가린 값의 원천 — 광고 담당자 칸(ADVERTISER) · 사업자 신원 연락처(BUSINESS — 광고 담당자를 비운 사업자) */
    contactSource: 'ADVERTISER' | 'BUSINESS' | null;
    memo: string | null;
    /** 최근 시작 차례 최대 50건(`counts.total` 이 더 크면 「일정표에서 더 보기」) */
    bookings: AdAdvertiserBooking[];
    money: AdAdvertiserMoney;
    /** 최근 20건 */
    views: AdAdvertiserView[];
    history: AdAdvertiserEvent[];
    /** 서버 판정 — 직접 등록이고 광고 건이 하나도 없을 때만 지울 수 있다 */
    deletable: boolean;
    /** 서버 판정 — 직접 등록이고 끝나지 않은 건(진행·예약·대기)이 없을 때만 보관할 수 있다 */
    archivable: boolean;
}

/** 새 광고주(직접 등록) — 기존 `POST /api/admin/ads/advertisers` 와 같은 칸 */
export interface AdAdvertiserCreateBody {
    name: string;
    contactName: string;
    contactPhone: string;
    email: string;
    memo: string;
}

/**
 * 고치기 — 빠진 칸은 그대로, 빈 글자는 지우기, 값은 바꾸기(연락처는 저장 때 다시 암호화).
 * 사업자 광고주는 `name` 을 보내면 400 `fields.name`(이름 = 상호, 사업자 정보에서 바뀐다).
 */
export interface AdAdvertiserUpdateBody {
    name?: string;
    contactName?: string;
    contactPhone?: string;
    email?: string;
    memo?: string;
}
