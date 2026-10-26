import type { RequestLimitsSaveRequest, RequestLimitsView } from '~/composables/commerce/api/useCommerceSettingsApi.types';

/**
 * 커머스 설정 API — 서버 주소 문자열(`/api/admin/commerce/…`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 정책: `docs/domain/commerce.md` 12-4, 계약 `docs/features/commerce-foundation-contract.md` 「0-5」. 권한 `commerce.settings`(없으면 403 `PERMISSION_DENIED`), 쓰기는 CSRF.
 * 오류는 모두 `ApiError`(useApiClient)로 던져진다. 응답은 `Cache-Control: no-store`.
 *
 *  - `GET /api/admin/commerce/request-limits` → `RequestLimitsView`(`items` 는 공구 참여 · 충전 요청 · 상점 구매 · 환불 신청 차례)
 *  - `PUT /api/admin/commerce/request-limits` `{ limits: { <코드>: 0~100 } }`(네 종류 모두) → 같은 모양.
 *      400 `VALIDATION` `fields.limits.<코드>` · 403 `PERMISSION_DENIED`. 저장하면 서버가 감사(전→후)에 남기고, 서버들은 설정 사본을 15초 안에 따라온다.
 * 지금은 판매·결제 공통 설정 중 요청 제한만 있다 — 판매 스위치·사업자 정보 등은 이 파일에 주소를 더한다.
 */
const PATH = {
    root: '/api/admin/commerce',
    requestLimits: () => `${PATH.root}/request-limits`,
};

export function useCommerceSettingsApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 요청 제한(종류별 1분 한도) */
        requestLimits: (): Promise<RequestLimitsView> => client.get<RequestLimitsView>(PATH.requestLimits()),

        /** 요청 제한 저장(네 종류를 한 번에) */
        saveRequestLimits: (body: RequestLimitsSaveRequest): Promise<RequestLimitsView> => client.put<RequestLimitsView>(PATH.requestLimits(), body),
    };
}
