/**
 * 핫딜 링크 규칙(`deal_domains`) — 계약 `docs/features/hotdeal-contract.md` 「링크 판정」·03-hotdeal 1-1. **서버에 아직 없다(목업)**.
 * 도메인은 서버가 소문자·스킴·www.·포트·경로를 떼고 저장한다(화면 미리보기는 `UtilHotdeal.normalizeDomain` — 같은 규칙).
 * DB CHECK: 규칙이 없으면(null) 대형몰이어야 한다(`common = true`).
 */
import type { DomainRule } from '~/composables/hotdeal/api/useHotdealApi.types';

export interface DealDomain {
    id: string;
    /** 정규화된 도메인(유일) */
    domain: string;
    rule: DomainRule;
    /** 대형몰 — 위장 홍보 후보 집계에서 뺀다 */
    common: boolean;
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
}

/**
 * `POST /api/admin/hotdeal-domains` · `PUT /{id}` — 도메인은 서버가 정규화한다.
 * 400 `fields.domain`(형식) · `fields.common`(규칙 없음인데 대형몰 아님) · `fields.note`(100자 넘음) · 409 `HOTDEAL_DOMAIN_DUPLICATE` + `fields.domain`(이미 있는 도메인).
 */
export interface DealDomainRequest {
    domain: string;
    rule: DomainRule;
    common: boolean;
    note: string | null;
}
