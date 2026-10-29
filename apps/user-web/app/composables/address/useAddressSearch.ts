import type { AddressSearchConfig, AddressSearchPage } from '~/composables/address/api/useAddressApi.types';

/**
 * 우편번호 찾기 공유 상태 — 제공처 설정(`AddressSearchConfig`)을 세션 동안 한 번만 받고, 자체 주소 DB 검색을 대신 부른다(공용 주소 입력 `AddressInput`·`AddressSearchPanel` 이 쓴다).
 * 설정을 못 받으면 **다음 우편번호(기본값)로 계속한다**(pionworld2 `AddressInput` 과 같다) — 그 결과는 담아 두지 않아 다음에 열 때 다시 묻는다.
 */

/** 서버 기본값과 같은 다음 우편번호 스크립트 주소(서버가 설정 응답에 내려 주는 기본값) — 설정을 못 받았을 때만 쓴다 */
const FALLBACK_CONFIG: AddressSearchConfig = {
    provider: 'DAUM',
    scriptUrl: 'https://t1.daumcdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js',
};

/** 받는 중인 약속 — 직렬화하지 않는 값이라 `useState` 밖(모듈)에 둔다. 여러 칸이 동시에 열어도 한 번만 묻는다 */
let pending: Promise<AddressSearchConfig> | null = null;

export function useAddressSearch() {
    const api = useAddressApi();
    /** `undefined` = 아직 안 물어봄 */
    const config = useState<AddressSearchConfig | undefined>('address-search-config', () => undefined);

    /**
     * 제공처 설정 — 받은 적이 있으면 그것을, 없으면 묻는다. 실패해도 던지지 않고 다음 우편번호 기본값을 준다.
     * @returns {Promise<AddressSearchConfig>} 쓸 설정.
     */
    async function ensureConfig(): Promise<AddressSearchConfig> {
        if (config.value) return config.value;
        pending ??= api
            .config()
            .then((value) => {
                config.value = value;
                return value;
            })
            .catch(() => FALLBACK_CONFIG)
            .finally(() => {
                pending = null;
            });
        return pending;
    }

    /** 받아 둔 설정을 버린다 — 다음 `ensureConfig` 가 다시 묻는다(개발 확인 화면이 목업 스위치를 바꿀 때) */
    function forget(): void {
        config.value = undefined;
    }

    /**
     * 자체 주소 DB 검색 — 쪽 번호는 0부터. 오류는 `ApiError` 그대로 던진다.
     * @returns {Promise<AddressSearchPage>} 한 쪽.
     */
    function search(keyword: string, page: number, size: number, signal?: AbortSignal): Promise<AddressSearchPage> {
        return api.search(keyword, page, size, signal);
    }

    return { config: readonly(config), ensureConfig, forget, search };
}
