import type { MyProfile, MyProfileRequest } from '~/composables/profile/api/useProfileApi.types';

/**
 * 내 프로필 API — 서버 주소 문자열(`/api/me/profile`)이 있는 **유일한 곳**이다. 상태는 없다. **서버 연결됨**(server/api `MyProfileController`).
 * 계약: `docs/features/member-mypage-contract.md` 「프로필」. 로그인 필요(401), 본인 것만, `PUT` 은 CSRF.
 *
 *  - `GET /api/me/profile` → `MyProfile`(닉네임·한 줄 소개·가입 시각·닉네임 변경 상태 `nicknameChange`). 정지 중에도 읽는다.
 *  - `PUT /api/me/profile` `{ nickname, bio }` → `MyProfile`. 닉네임이 그대로면(소개만) 변경 횟수를 쓰지 않고 검사도 하지 않는다. 대소문자만 바꿔도 변경으로 센다.
 *    오류(서버 검사 순서): `401` → `403 ACCOUNT_RESTRICTED`(정지·영구 정지 중 — 글쓰기와 같다) → `400 VALIDATION` `fields.nickname|bio` →
 *    `409 NICKNAME_CHANGE_LIMIT`(가입 뒤 1번의 변경을 이미 썼다 — 더 못 바꾼다) → `409 NICKNAME_TAKEN`.
 */
const PATH = {
    me: '/api/me/profile',
};

export function useProfileApi() {
    // CSRF 쿠키를 다시 받는 호출 — setup 문맥에서 미리 잡아 둔다(콜백 안에서 컴포저블을 부르지 않는다)
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 내 프로필 */
        me: (): Promise<MyProfile> => client.get<MyProfile>(PATH.me),

        /** 닉네임·한 줄 소개 저장 */
        save: (body: MyProfileRequest): Promise<MyProfile> => client.put<MyProfile>(PATH.me, body),
    };
}
