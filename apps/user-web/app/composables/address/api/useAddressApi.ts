import type { AddressSearchConfig, AddressSearchPage } from '~/composables/address/api/useAddressApi.types';

/**
 * 주소 검색 API — 서버 주소 문자열(`/api/address/…`)이 있는 **유일한 곳**이다. 상태는 없다(공유 상태는 `useAddressSearch`).
 * 계약: `docs/features/address-search-contract.md`. 둘 다 로그인 없이 부른다.
 *  - `GET /api/address/config` → `AddressSearchConfig`(제공처·다음 스크립트 주소). 비밀값이 없다.
 *  - `GET /api/address/search?keyword=&page=&size=` → `AddressSearchPage`. 400 `VALIDATION` `fields.keyword`(2자 미만·80자 초과·너무 넓은 검색어),
 *    429 `ADDRESS_SEARCH_RATE_LIMITED`(IP 하나 1분 60번), 503 `ADDRESS_DB_NOT_LOADED`(주소 DB 를 한 번도 올리지 않음).
 *    낱말마다 도로명·건물 이름·지번·영문·우편번호의 부분 일치(모든 낱말이 들어 있는 줄), `total` 은 1000 까지만 센다.
 */
const PATH = {
    config: '/api/address/config',
    search: '/api/address/search',
};

export function useAddressApi() {
    const client = useApiClient();

    return {
        /** 우편번호 찾기 제공처와 다음 스크립트 주소 */
        config: (): Promise<AddressSearchConfig> => client.get<AddressSearchConfig>(PATH.config),

        /** 자체 주소 DB 검색 — 쪽 번호는 0부터 */
        search: (keyword: string, page: number, size: number, signal?: AbortSignal): Promise<AddressSearchPage> =>
            client.get<AddressSearchPage>(PATH.search, { query: { keyword, page, size }, signal }),
    };
}
