/**
 * 위장 홍보 의심 후보(`deal_suspicions` + `kind`) — 계약 「목업 가정」·05 §3. **서버에 아직 없다(목업)**. 대형몰로 표시한 도메인은 세지 않는다.
 *   - 회원 후보(`MEMBER`): 같은 회원이 `suspicionWindowDays` 안에 같은 판매처 글을 `suspicionMinPosts` 건 이상.
 *   - 판매처 후보(`STORE`): 같은 비대형몰을 `storeSuspicionWindowDays` 안에 서로 다른 회원 `storeSuspicionMembers` 명 이상이 올리고, 그 회원들의 다른 핫딜 비율이 `storeSuspicionOtherRatioPercent`% 미만.
 * 운영자 목록만 있고 자동 제재는 없다. 제재는 기존 글 관리(숨김)·회원 관리·신고 화면으로 간다.
 * 판매처 호스트 칸 이름은 두 종류 모두 `host`(계약 판매처 후보 줄과 같은 이름 — 1차 목업의 `domain` 을 바꿨다).
 */
import type { DealView, HotdealBoardRef } from '~/composables/hotdeal/api/useHotdealApi.types';

export type DealSuspicionStatus = 'OPEN' | 'DISMISSED';

export const DEAL_SUSPICION_STATUSES: readonly DealSuspicionStatus[] = ['OPEN', 'DISMISSED'];

/** 후보 종류 — 회원 · 판매처 */
export type DealSuspicionKind = 'MEMBER' | 'STORE';

/** 종류 거르기 — 전체 · 회원 · 판매처 */
export type DealSuspicionKindFilter = DealSuspicionKind | 'ALL';

export const DEAL_SUSPICION_KIND_FILTERS: readonly DealSuspicionKindFilter[] = ['ALL', 'MEMBER', 'STORE'];

export interface DealSuspicionMember {
    id: string;
    nickname: string;
    withdrawn: boolean;
}

interface DealSuspicionBase {
    id: string;
    /** 판매처 호스트(정규화) */
    host: string;
    /** 판매처 이름(마지막 글의 쇼핑몰 이름 — 관리자 덧붙임) */
    storeName: string;
    /** 기간 안 이 판매처 글 수(회원 후보는 그 회원의 글만) */
    postCount: number;
    /** 감지 기준 기간(일) — 설정 값 */
    windowDays: number;
    firstDetectedAt: string;
    lastPostAt: string;
    status: DealSuspicionStatus;
    dismissedBy: string | null;
    dismissedAt: string | null;
    /** 무시할 때 적은 메모 */
    note: string | null;
}

/** 회원 후보 한 줄 */
export interface DealMemberSuspicion extends DealSuspicionBase {
    kind: 'MEMBER';
    member: DealSuspicionMember;
    /** 같은 판매처에 글을 올린 다른 회원 수(기간 안) */
    othersCount: number;
}

/** 판매처 후보 한 줄 — 계약 `{ kind: STORE, host, storeName, memberCount, otherRatioPercent, windowDays, posts[] }`(글 목록은 상세에) */
export interface DealStoreSuspicion extends DealSuspicionBase {
    kind: 'STORE';
    /** 기간 안 이 판매처 글을 올린 서로 다른 회원 수 */
    memberCount: number;
    /** 그 회원들의 기간 안 핫딜 중 이 판매처가 아닌 글의 비율(내림 정수 %) */
    otherRatioPercent: number;
}

export type DealSuspicion = DealMemberSuspicion | DealStoreSuspicion;

/** `GET /api/admin/hotdeal-suspicions?status&kind&page&size` — 마지막 글이 최근인 것부터 */
export interface DealSuspicionPage {
    total: number;
    page: number;
    size: number;
    items: DealSuspicion[];
    /** 상태별 건수(종류 조건 포함) */
    counts: Record<DealSuspicionStatus, number>;
    /** 종류별 건수(상태 조건 포함 — 관리자 덧붙임) */
    kindCounts: Record<DealSuspicionKindFilter, number>;
}

/** 후보의 판매처 글 한 줄 */
export interface DealSuspicionPost {
    postId: string;
    title: string;
    board: HotdealBoardRef;
    /** 올린 회원 — 판매처 후보 서랍이 회원별로 묶는다 */
    author: DealSuspicionMember;
    dealPrice: number;
    view: DealView;
    createdAt: string;
    /** 처리 대기 신고 수 */
    openReports: number;
}

/** 판매처 후보의 회원 한 명(관리자 덧붙임) — 기간 안 이 판매처 글 수 · 다른 판매처 핫딜 수 */
export interface DealStoreSuspicionMember {
    member: DealSuspicionMember;
    postCount: number;
    otherDealCount: number;
}

/** `GET /api/admin/hotdeal-suspicions/{id}` — 회원 후보는 그 회원의 그 판매처 글 최근 10개, 판매처 후보는 그 판매처 글 최근 30개 + 회원 요약 */
export interface DealSuspicionDetail {
    suspicion: DealSuspicion;
    posts: DealSuspicionPost[];
    /** 판매처 후보만(회원 후보는 빈 목록) */
    members: DealStoreSuspicionMember[];
}

/** `POST /api/admin/hotdeal-suspicions/{id}/dismiss` — 메모 선택 0~200자 */
export interface DealSuspicionDismissRequest {
    note?: string;
}
