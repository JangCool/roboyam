/**
 * 지금 로그인한 운영자의 권한 — `useAdminAuth().user` 의 `isSuper`·`permissions` 를 읽는 판정 도우미다(상태를 새로 두지 않는다).
 *
 * **감추는 것은 편의이고 막는 것은 서버다.** 여기서 `can` 이 true 여도 서버가 요청마다 다시 판정한다.
 * 최고 관리자(`isSuper`)는 모든 권한을 갖는다. 권한 코드는 `utils/util-permission-catalog.ts` 의 `영역.행위`.
 *
 * 반환: `isSuper` · `can(code)` · `canAll(codes)` · `permissions`(가진 코드 집합, 최고 관리자는 서버가 준 전체 목록).
 */
export function useAdminPermission() {
    const { user } = useAdminAuth();

    const isSuper = computed(() => user.value?.isSuper === true);
    const permissions = computed(() => new Set(user.value?.permissions ?? []));

    /** 이 권한을 가졌는가. 계산값을 읽으므로 로그인 상태가 바뀌면 이것을 쓰는 `computed` 도 다시 계산된다 */
    function can(code: string): boolean {
        return isSuper.value || permissions.value.has(code);
    }

    function canAll(codes: readonly string[]): boolean {
        return codes.every(can);
    }

    return { isSuper, permissions, can, canAll };
}
