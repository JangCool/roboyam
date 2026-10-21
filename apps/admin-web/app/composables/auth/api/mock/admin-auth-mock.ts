// MOCK: 아직 서버가 없는 영역(내 정보·알림·감사)의 목업이 "지금 로그인한 운영자"를 읽는 작은 연결 — 로그인·로그아웃·`me` 는 이제 실제 서버(`useAdminAuthApi`)가 한다.
// 목업 계정·로그인 흉내·`?mock=login-*` 스위치는 없어졌다. 여기는 **실제 로그인 상태**(`useAdminAuth` 가 든 `useState`)를 그대로 읽어 줄 뿐이라 권한(`isSuper`·`permissions`)도 서버가 준 값이다.
// 그 영역의 서버가 붙으면 이 파일은 `profile-mock.ts` 등과 함께 지운다.

import { ADMIN_AUTH_USER_STATE_KEY } from '~/composables/common/useApiClient';
import type { AdminUser } from '~/composables/auth/api/useAdminAuthApi.types';

/** 지금 로그인한 운영자(실제 로그인 상태) — 없으면 null. 목업은 요청 때 이 값을 읽어 권한을 흉내 낸다 */
export function mockCurrentAdmin(): AdminUser | null {
    return useState<AdminUser | null>(ADMIN_AUTH_USER_STATE_KEY, () => null).value;
}

/** 내 정보 목업이 닉네임을 바꾸면 화면의 로그인 상태에도 반영한다(상단 이름이 바로 따르도록). 서버의 이름은 그대로라 새로고침하면 돌아온다 */
export function mockRenameAdmin(nickname: string): void {
    const state = useState<AdminUser | null>(ADMIN_AUTH_USER_STATE_KEY, () => null);
    if (state.value) state.value = { ...state.value, nickname };
}

/** 이미 쓰는 닉네임인가(중복 검사 흉내) — 다른 운영자의 이름이라고 가정한 고정 목록 */
export function mockNicknameTaken(nickname: string): boolean {
    const mine = mockCurrentAdmin()?.nickname;
    return nickname !== mine && ['김관리', '박운영'].includes(nickname);
}
