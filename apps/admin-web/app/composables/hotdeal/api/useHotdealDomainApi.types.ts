/**
 * 핫딜 링크 규칙(`deal_domains`) — 계약 `docs/features/hotdeal-contract.md` 「링크 판정」·03-hotdeal 1-1·05 §4-2·3. 서버 DTO 는 이 칸 이름 그대로.
 * 도메인은 서버가 소문자·스킴·www.·포트·경로를 떼고 저장한다(화면 미리보기는 `UtilHotdeal.normalizeDomain` — 같은 규칙).
 * DB CHECK: 규칙이 없으면(null) 대형몰이어야 한다(`common = true`).
 */
import type { AffiliateMode, DomainRule } from '~/composables/hotdeal/api/useHotdealApi.types';

export interface DealDomain {
    id: string;
    /** 정규화된 도메인(유일) */
    domain: string;
    rule: DomainRule;
    /** 대형몰 — 위장 홍보 후보 집계에서 뺀다 */
    common: boolean;
    /**
     * 같은 딜 판정(`product_url_key`)에 남길 쿼리 이름(예 `11st.co.kr → prdNo`). 비면 추적 파라미터만 지우고 나머지 쿼리를 다 남긴다.
     * 추적 파라미터(`utm_*`·`fbclid`·`gclid`·`igshid`·`ref`·`tag`·`aff*`·`spm`·`_trkparms`·`sub_id`)는 이 목록과 상관없이 늘 지운다(서버 규칙 3).
     */
    keyParams: string[];
    /** 메모 0~100자 */
    note: string | null;
    /** 마지막으로 고친 운영자 표시 이름(시드는 null) */
    updatedBy: string | null;
    updatedAt: string;
}

/** 규칙 거르기 — 전체 · 차단 · 제휴 · 규칙 없음(대형몰만) · 대형몰 */
export type DomainRuleFilter = 'ALL' | 'BLOCK' | 'AFFILIATE' | 'NONE' | 'COMMON';

export const DOMAIN_RULE_FILTERS: readonly DomainRuleFilter[] = ['ALL', 'BLOCK', 'AFFILIATE', 'NONE', 'COMMON'];

/** `GET /api/admin/hotdeal-domains?q=&rule=` — 작은 표라 쪽 나눔 없이 전부(도메인 차례) */
export interface DealDomainListView {
    items: DealDomain[];
    /** 지금 제휴 링크 방식(관리자 덧붙임 — `SITE` 면 화면 위에 「제휴 규칙 링크는 개인 제휴로 받지 않아요」 안내) */
    affiliateMode: AffiliateMode;
}

/**
 * `POST /api/admin/hotdeal-domains` · `PUT /{id}` — 도메인은 서버가 정규화한다.
 * 400 `fields.domain`(형식) · `fields.common`(규칙 없음인데 대형몰 아님) · `fields.keyParams`(이름 형식·개수·추적 파라미터) · `fields.note`(100자 넘음) · 409 `HOTDEAL_DOMAIN_DUPLICATE` + `fields.domain`(이미 있는 도메인).
 */
export interface DealDomainRequest {
    domain: string;
    rule: DomainRule;
    common: boolean;
    keyParams: string[];
    note: string | null;
}

/** 차단 미리 보기에 함께 보이는 진행 중 글 몇 개(관리자 덧붙임) */
export interface DealDomainImpactPost {
    postId: string;
    title: string;
    storeName: string;
}

/**
 * `GET /api/admin/hotdeal-domains/impact?domain=` — 규칙을 차단으로 바꾸거나 새로 차단할 때 저장 전 「이 도메인의 진행 중 글 N건」.
 * 세는 것: 상품·최종 호스트가 이 도메인(하위 도메인 포함)인 진행 중(ACTIVE·FLAGGED) 공개 글. 저장해도 글은 그대로다(서버 규칙 7).
 */
export interface DealDomainImpact {
    /** 정규화한 도메인 */
    domain: string;
    activeCount: number;
    /** 최근 것부터 3개까지 */
    samples: DealDomainImpactPost[];
}
