/**
 * 사이트 제휴 몰(`deal_site_affiliates`) — 계약 `docs/features/hotdeal-contract.md` D5 · 데이터 모양 `SiteAffiliateStore`. **서버에 아직 없다(목업)**.
 * `affiliateMode = SITE` 일 때 `/go/deal/{postId}` 가 클릭 시점에 상품·최종 호스트가 이 표의 켠 몰(접미사 일치 · 더 구체적인 것 우선)이면 개인 제휴 파라미터를 떼고 이 방식으로 바꾼다.
 * 도메인은 서버가 링크 규칙과 같은 규칙으로 정규화한다(화면 미리보기는 `UtilHotdeal.normalizeDomain`). 감사 `HOTDEAL_SITE_AFFILIATE_CREATE/UPDATE/DELETE`.
 */

/**
 * 변환 방식 — 링크 틀(`TEMPLATE` — `{url}` 자리표시자 1개 필수, 자리에 원래 상품 주소를 URL 인코딩해 넣는다) ·
 * 붙일 쿼리(`QUERY` — `k=v&…`, 원래 상품 주소에 붙이고 같은 이름이 있으면 바꾼다).
 */
export type SiteAffiliateTransform = { type: 'TEMPLATE'; template: string } | { type: 'QUERY'; query: string };

export type SiteAffiliateTransformType = SiteAffiliateTransform['type'];

export const SITE_AFFILIATE_TRANSFORM_TYPES: readonly SiteAffiliateTransformType[] = ['TEMPLATE', 'QUERY'];

export interface SiteAffiliateStore {
    id: string;
    /** 정규화된 몰 도메인(유일 — 하위 도메인에도 걸린다) */
    domain: string;
    /** 제휴 프로그램 이름 1~40자(예 쿠팡 파트너스) — 공개 카드 「로보얌 제휴 링크」 옆에 쓸 수 있다 */
    programName: string;
    transform: SiteAffiliateTransform;
    /** 끄면 이 몰도 제휴 없이 원래 상품 링크로 간다(줄은 남는다) */
    enabled: boolean;
    /** 마지막으로 고친 운영자 표시 이름(관리자 덧붙임 — 시드는 null) */
    updatedBy: string | null;
    updatedAt: string;
}

/** `GET /api/admin/hotdeal-site-affiliates` — 작은 표라 쪽 나눔 없이 전부(도메인 차례) */
export interface SiteAffiliateListView {
    items: SiteAffiliateStore[];
}

/**
 * `POST /api/admin/hotdeal-site-affiliates` · `PUT /{id}` — 켬/끔 단추도 같은 PUT(줄 전체)으로 보낸다.
 * 400 `fields.domain`(형식) · `fields.programName`(비었거나 40자 넘음) · `fields.template`(`{url}` 없음·둘 이상·http(s) 아님) · `fields.query`(`k=v&…` 형식 아님)
 * · 409 `HOTDEAL_SITE_AFFILIATE_DUPLICATE` + `fields.domain`(이미 있는 도메인) · 404 `HOTDEAL_SITE_AFFILIATE_NOT_FOUND`.
 */
export interface SiteAffiliateStoreRequest {
    domain: string;
    programName: string;
    transform: SiteAffiliateTransform;
    enabled: boolean;
}
