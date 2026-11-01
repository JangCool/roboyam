/**
 * 위장 홍보 의심 후보(`deal_suspicions`) — 03-hotdeal 1-1 「같은 회원이 N일 안에 같은 판매처(대형몰 제외)를 M건 이상」. **서버에 아직 없다(목업)**.
 * 운영자 목록만 있고 자동 제재는 없다. 제재는 기존 신고·회원 화면으로 간다.
 */
import type { DealView, HotdealBoardRef } from '~/composables/hotdeal/api/useHotdealApi.types';

export type DealSuspicionStatus = 'OPEN' | 'DISMISSED';

export const DEAL_SUSPICION_STATUSES: readonly DealSuspicionStatus[] = ['OPEN', 'DISMISSED'];

export interface DealSuspicionMember {
    id: string;
    nickname: string;
    withdrawn: boolean;
}

/** 후보 한 줄 */
export interface DealSuspicion {
    id: string;
    member: DealSuspicionMember;
    /** 판매처 호스트(정규화) */
    domain: string;
    /** 판매처 이름(마지막 글의 쇼핑몰 이름 — 관리자 덧붙임) */
    storeName: string;
    /** 기간 안 이 회원의 이 판매처 글 수 */
    postCount: number;
    /** 같은 판매처에 글을 올린 다른 회원 수(기간 안) */
    othersCount: number;
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

/** `GET /api/admin/hotdeal-suspicions?status&page&size` — 마지막 글이 최근인 것부터 */
export interface DealSuspicionPage {
    total: number;
    page: number;
    size: number;
    items: DealSuspicion[];
    counts: Record<DealSuspicionStatus, number>;
}

/** 그 회원의 그 판매처 글 한 줄 */
export interface DealSuspicionPost {
    postId: string;
    title: string;
    board: HotdealBoardRef;
    dealPrice: number;
    view: DealView;
    createdAt: string;
    /** 처리 대기 신고 수 */
    openReports: number;
}

/** `GET /api/admin/hotdeal-suspicions/{id}` — 최근 글 10개까지 */
export interface DealSuspicionDetail {
    suspicion: DealSuspicion;
    posts: DealSuspicionPost[];
}

/** `POST /api/admin/hotdeal-suspicions/{id}/dismiss` — 메모 선택 0~200자 */
export interface DealSuspicionDismissRequest {
    note?: string;
}
