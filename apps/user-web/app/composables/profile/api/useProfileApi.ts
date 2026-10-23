import { mockMyProfile, mockSaveMyProfile } from '~/composables/profile/api/mock/profile-mock';
import type { MyProfile, MyProfileRequest } from '~/composables/profile/api/useProfileApi.types';

/**
 * 내 프로필 API — 서버 주소 문자열(`/api/me/profile`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 계약 후보: `docs/features/member-mypage-contract.md` 「프로필」. 로그인 필요(401), 본인 것만, `PUT` 은 CSRF.
 *
 *  - `GET /api/me/profile` → `MyProfile`(닉네임·한 줄 소개·가입 시각·닉네임 변경 주기)
 *  - `PUT /api/me/profile` `{ nickname, bio }` → `MyProfile`.
 *    오류: `400 VALIDATION` `fields.nickname|bio` · `409 NICKNAME_TAKEN` · `409 NICKNAME_CHANGE_TOO_SOON`(`extras.availableAt`).
 *
 * ⚠ MOCK: 서버에 API 가 없어 `./mock/profile-mock.ts` 를 부른다. 서버가 붙으면 `// MOCK:` 줄과 목업 import 를 지우고 `// REAL:` 줄을 살린다.
 */
const PATH = {
    me: '/api/me/profile',
};

export function useProfileApi() {
    // REAL: const auth = useAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    // MOCK: 목업은 로그인 정보의 닉네임을 처음 값으로 쓴다
    const { user } = useAuth();
    void PATH;

    return {
        /** 내 프로필 */
        me: (): Promise<MyProfile> =>
            // MOCK:
            mockMyProfile(user.value?.nickname ?? ''),
        // REAL: client.get<MyProfile>(PATH.me),

        /** 닉네임·한 줄 소개 저장 */
        save: (body: MyProfileRequest): Promise<MyProfile> =>
            // MOCK:
            mockSaveMyProfile(user.value?.nickname ?? '', body),
        // REAL: client.put<MyProfile>(PATH.me, body),
    };
}
