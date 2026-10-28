import { mockAddressConfig, mockAddressSearch } from '~/composables/address/api/mock/address-mock';
import type { AddressSearchConfig, AddressSearchPage } from '~/composables/address/api/useAddressApi.types';

/**
 * 주소 검색 API — 서버 주소 문자열(`/api/address/…`)이 있는 **유일한 곳**이다. 상태는 없다(공유 상태는 `useAddressSearch`).
 * 계약 후보(화면 확정 뒤 `docs/features/address-search-contract.md`):
 *  - `GET /api/address/config` → `AddressSearchConfig`(제공처·다음 스크립트 주소). 비밀값이 없어 로그인 없이도 준다.
 *  - `GET /api/address/search?keyword=&page=&size=` → `AddressSearchPage`. 검색어 2자 이상(400 `VALIDATION` `fields.keyword`),
 *    주소 DB 를 한 번도 올린 적이 없으면 503 `ADDRESS_DB_NOT_LOADED`. 도로명·건물 이름·지번·영문 주소의 부분 일치(서버 `pg_trgm`).
 *
 * ⚠ MOCK: 서버에 API 가 없어 `./mock/address-mock.ts` 를 부른다. 서버가 붙으면 `// MOCK:` 줄과 목업 import 를 지우고 `// REAL:` 줄을 살린다.
 */
const PATH = {
    config: '/api/address/config',
    search: '/api/address/search',
};

export function useAddressApi() {
    // REAL: const client = useApiClient();
    void PATH;

    return {
        /** 우편번호 찾기 제공처와 다음 스크립트 주소 */
        config: (): Promise<AddressSearchConfig> =>
            // MOCK:
            mockAddressConfig(),
        // REAL: client.get<AddressSearchConfig>(PATH.config),

        /** 자체 주소 DB 검색 — 쪽 번호는 0부터 */
        search: (keyword: string, page: number, size: number, signal?: AbortSignal): Promise<AddressSearchPage> =>
            // MOCK:
            mockAddressSearch(keyword, page, size, signal),
        // REAL: client.get<AddressSearchPage>(PATH.search, { query: { keyword, page, size }, signal }),
    };
}
