import type { ShippingAddress, ShippingAddressList, ShippingAddressRequest } from '~/composables/address/api/useAddressBookApi.types';

/**
 * 배송지 목록(주소록) API — 서버 주소 문자열(`/api/me/addresses`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 로그인 필요(401). 회원 본인 것만(남의 것은 404) — 운영자 화면에는 이 자원이 없다. 계약: `docs/features/address-book-contract.md`(서버 `AddressBookController`).
 *  - `GET /api/me/addresses` → `{ items, max }`(기본이 맨 위, 나머지는 최근 만든/고친 순)
 *  - `POST /api/me/addresses` → 201 `ShippingAddress` · `409 ADDRESS_BOOK_FULL`(10개) · `400 VALIDATION fields`(`label`·`recipient`·`phone`·`postcode`·`address1`·`address2`·`memo`)
 *  - `PUT /api/me/addresses/{id}` (같은 본문) → 200 `ShippingAddress` · `404 ADDRESS_NOT_FOUND`
 *  - `DELETE /api/me/addresses/{id}` → 204(기본을 지우면 남은 것 중 가장 최근 것이 기본 — 서버가 정한다) · 404
 *  - `PUT /api/me/addresses/{id}/default` → 204 · 404
 *  - 데이터 키가 없으면 모두 `503 DATA_KEY_MISSING`.
 * 첫 배송지는 `makeDefault` 와 상관없이 기본이 된다(서버 규칙).
 */
const PATH = {
    list: '/api/me/addresses',
    one: (id: string) => `/api/me/addresses/${encodeURIComponent(id)}`,
    makeDefault: (id: string) => `/api/me/addresses/${encodeURIComponent(id)}/default`,
};

export function useAddressBookApi() {
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 내 배송지 목록 */
        list: (signal?: AbortSignal): Promise<ShippingAddressList> => client.get<ShippingAddressList>(PATH.list, { signal }),

        /** 배송지를 더한다 */
        create: (body: ShippingAddressRequest): Promise<ShippingAddress> => client.post<ShippingAddress>(PATH.list, body),

        /** 배송지를 고친다 */
        update: (id: string, body: ShippingAddressRequest): Promise<ShippingAddress> => client.put<ShippingAddress>(PATH.one(id), body),

        /** 배송지를 지운다(바로 삭제) */
        remove: (id: string): Promise<void> => client.del<void>(PATH.one(id)),

        /** 기본 배송지로 정한다 */
        makeDefault: (id: string): Promise<void> => client.put<void>(PATH.makeDefault(id)),
    };
}
