import { mockDomainCreate, mockDomainDelete, mockDomainImpact, mockDomainList, mockDomainUpdate } from '~/composables/hotdeal/api/mock/hotdeal-mock';
import type { DealDomain, DealDomainImpact, DealDomainListView, DealDomainRequest, DomainRuleFilter } from '~/composables/hotdeal/api/useHotdealDomainApi.types';

/**
 * 핫딜 링크 규칙 API(관리자) — 서버 주소(`/api/admin/hotdeal-domains…`)가 있는 **유일한 곳**이다. 상태는 없다. **서버에 아직 없다(목업)**.
 * 권한: 조회 `hotdeal.read`, 추가·고치기·지우기 `hotdeal.domain.manage`(카탈로그 `featureReady=false` — 서버에서 켜야 한다).
 *   - `GET    /api/admin/hotdeal-domains?q=&rule=` → `DealDomainListView`(도메인 차례, 쪽 나눔 없음)
 *   - `GET    /api/admin/hotdeal-domains/impact?domain=` → `DealDomainImpact`(차단으로 바꿀 때 「진행 중 글 N건」 미리 보기 — 저장하지 않는다)
 *   - `POST   /api/admin/hotdeal-domains` · `PUT /{id}` `DealDomainRequest` → `DealDomain`
 *       400 `fields.domain`·`fields.common`(규칙 없음이면 대형몰 필수 — DB CHECK)·`fields.keyParams`·`fields.note` · 409 `HOTDEAL_DOMAIN_DUPLICATE` + `fields.domain`
 *   - `DELETE /api/admin/hotdeal-domains/{id}` → 204 · `404 HOTDEAL_DOMAIN_NOT_FOUND`
 * 감사 `HOTDEAL_DOMAIN_CREATE`·`HOTDEAL_DOMAIN_UPDATE`·`HOTDEAL_DOMAIN_DELETE`. 판정은 캐시 없이 요청마다 읽는다(03-hotdeal 1-1).
 *
 * ⚠ MOCK: 모든 메서드가 `./mock/hotdeal-mock.ts` 를 부른다.
 */
const PATH = {
    root: '/api/admin/hotdeal-domains',
    impact: '/api/admin/hotdeal-domains/impact',
    one: (id: string) => `${PATH.root}/${encodeURIComponent(id)}`,
};

export function useHotdealDomainApi() {
    // REAL: const auth = useAdminAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    void PATH; // MOCK: 서버가 붙으면 이 줄을 지운다

    return {
        list: (rule: DomainRuleFilter, q: string): Promise<DealDomainListView> =>
            // MOCK:
            mockDomainList(rule, q),
        // REAL: client.get<DealDomainListView>(PATH.root, { query: { rule: rule === 'ALL' ? undefined : rule, q: q || undefined } }),

        /** 차단 미리 보기 — 이 도메인의 진행 중 글 수 */
        impact: (domain: string): Promise<DealDomainImpact> =>
            // MOCK:
            mockDomainImpact(domain),
        // REAL: client.get<DealDomainImpact>(PATH.impact, { query: { domain } }),

        create: (body: DealDomainRequest): Promise<DealDomain> =>
            // MOCK:
            mockDomainCreate(body),
        // REAL: client.post<DealDomain>(PATH.root, body),

        update: (id: string, body: DealDomainRequest): Promise<DealDomain> =>
            // MOCK:
            mockDomainUpdate(id, body),
        // REAL: client.put<DealDomain>(PATH.one(id), body),

        remove: (id: string): Promise<void> =>
            // MOCK:
            mockDomainDelete(id),
        // REAL: client.delete<void>(PATH.one(id)),
    };
}
