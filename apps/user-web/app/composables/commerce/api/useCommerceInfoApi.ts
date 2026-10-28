import type { CommerceInfo } from '~/composables/commerce/api/useCommerceInfoApi.types';

/**
 * 커머스 공개 정보 API — 서버 주소 문자열(`/api/commerce/info`)이 있는 **유일한 곳**이다. 상태는 없다(공유 상태는 `useCommerceInfo`).
 * 계약 `docs/features/commerce-groupbuy-contract.md` 결정 18·56. 로그인 없이 누구나.
 *  - `GET /api/commerce/info` → `CommerceInfo`(판매 스위치 `salesOpen` · 사업자 정보 완비 `ready` · 사업자 칸)
 *
 */
const PATH = {
    info: '/api/commerce/info',
};

export function useCommerceInfoApi() {
    const client = useApiClient();

    return {
        info: (): Promise<CommerceInfo> =>
            client.get<CommerceInfo>(PATH.info),
    };
}
