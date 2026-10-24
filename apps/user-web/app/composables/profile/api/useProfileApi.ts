import { mockMyProfile, mockSaveMyProfile } from '~/composables/profile/api/mock/profile-mock';
import type { MyProfile, MyProfileRequest } from '~/composables/profile/api/useProfileApi.types';
import UtilSanction from '~/utils/util-sanction'; // MOCK:

/**
 * 내 프로필 API — 서버 주소 문자열(`/api/me/profile`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 계약 후보: `docs/features/member-mypage-contract.md` 「프로필」. 로그인 필요(401), 본인 것만, `PUT` 은 CSRF.
 *
 *  - `GET /api/me/profile` → `MyProfile`(닉네임·한 줄 소개·가입 시각·닉네임 변경 상태 `nicknameChange`). 정지 중에도 읽는다.
 *  - `PUT /api/me/profile` `{ nickname, bio }` → `MyProfile`. 닉네임이 그대로면(소개만) 변경 횟수를 쓰지 않는다.
 *    오류: `400 VALIDATION` `fields.nickname|bio` · `409 NICKNAME_TAKEN` ·
 *    `409 NICKNAME_CHANGE_LIMIT`(가입 뒤 1번의 변경을 이미 썼다 — 더 못 바꾼다) · `403 ACCOUNT_RESTRICTED`(정지·영구 정지 중 — 글쓰기와 같다).
 *
 * ⚠ MOCK: 서버에 API 가 없어 `./mock/profile-mock.ts` 를 부른다. 서버가 붙으면 `// MOCK:` 줄과 목업 import 를 지우고 `// REAL:` 줄을 살린다.
 */
const PATH = {
    me: '/api/me/profile',
};

export function useProfileApi() {
    // REAL: const auth = useAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    // MOCK: 목업은 로그인 정보의 닉네임을 처음 값으로 쓰고, 정지 여부(`restriction`)로 저장을 막는다
    const { user, restriction } = useAuth();
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
            mockSaveMyProfile(user.value?.nickname ?? '', UtilSanction.isRestricted(restriction.value), body),
        // REAL: client.put<MyProfile>(PATH.me, body),
    };
}
