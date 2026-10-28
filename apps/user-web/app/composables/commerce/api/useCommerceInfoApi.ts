import { mockCommerceInfo } from '~/composables/commerce/api/mock/commerce-mock';
import type { CommerceInfo } from '~/composables/commerce/api/useCommerceInfoApi.types';

/**
 * 커머스 공개 정보 API — 서버 주소 문자열(`/api/commerce/info`)이 있는 **유일한 곳**이다. 상태는 없다(공유 상태는 `useCommerceInfo`).
 * 계약 `docs/features/commerce-groupbuy-contract.md` 결정 18·56. 로그인 없이 누구나.
 *  - `GET /api/commerce/info` → `CommerceInfo`(판매 스위치 `salesOpen` · 사업자 정보 완비 `ready` · 사업자 칸)
 *
 * ⚠ MOCK: 서버에 API 가 없어(2b-4) `./mock/commerce-mock.ts` 를 부른다. 서버가 붙으면 `// MOCK:` 줄과 목업 import 를 지우고 `// REAL:` 줄을 살린다.
 */
const PATH = {
    info: '/api/commerce/info',
};

export function useCommerceInfoApi() {
    // REAL: const client = useApiClient();
    void PATH;

    return {
        info: (): Promise<CommerceInfo> =>
            // MOCK:
            mockCommerceInfo(),
        // REAL: client.get<CommerceInfo>(PATH.info),
    };
}
