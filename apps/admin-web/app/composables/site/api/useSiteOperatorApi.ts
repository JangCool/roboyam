import type { SiteOperatorRequest, SiteOperatorResponse } from '~/composables/site/api/useSiteOperatorApi.types';

/**
 * 운영 주체 API — 서버 주소 문자열(`/api/admin/site/operator`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 계약: `docs/features/terms-operator-contract.md` 「서버 구현 결과」(권한은 일반 설정의 다른 구획과 같다).
 *   - `GET /api/admin/site/operator` → 200 `SiteOperatorResponse`(단계·운영자 정보·사업자 정보 + 전환 이력 최신 먼저)  (`site.read`)
 *   - `PUT /api/admin/site/operator` `SiteOperatorRequest` → 200 저장된 모양  (`site.manage`)
 *       · 400 VALIDATION — `fields.stage`·`operatorName`·`contactEmail`·`phone`·`privacyOfficerName`·`privacyOfficerEmail`·`hostingProvider`·`mailProvider`·
 *         `business.businessName`·`business.representative`·`business.registrationNumber`·`business.address`·`business.mailOrderNumber`·`business.mailOrderAuthority`·`business.corporateNumber`
 *       · 409 COMMERCE_SALES_OPEN — 판매가 켜져 있는 동안 개인 단계로 바꾸거나 통신판매업 신고번호를 비우려 했다(칸 없음, 서버 문구)
 *       · 단계가 바뀌면 서버가 이력 한 줄과 감사 `SITE_OPERATOR_STAGE_CHANGE`, 칸만 바뀌면 `SITE_OPERATOR_UPDATE` 를 남긴다. 지금 게시된 약관은 바뀌지 않는다(새 버전 게시가 필요).
 *       · 개인 단계로 저장하면 서버가 사업자 칸을 비운다(`business` 는 null 로 보낸다).
 */
const PATH = {
    root: '/api/admin/site/operator',
};

export function useSiteOperatorApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 운영 주체 값과 단계 전환 이력 */
        get: (): Promise<SiteOperatorResponse> => client.get<SiteOperatorResponse>(PATH.root),

        /** 운영 주체를 저장한다 — 저장된 모양(이력 포함)을 돌려준다 */
        save: (body: SiteOperatorRequest): Promise<SiteOperatorResponse> => client.put<SiteOperatorResponse>(PATH.root, body),
    };
}
