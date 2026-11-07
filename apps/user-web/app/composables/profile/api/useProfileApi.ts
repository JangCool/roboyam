import type { MyProfile, MyProfileRequest } from '~/composables/profile/api/useProfileApi.types';
import { mockSaveVisibility, mockVisibilityOf } from '~/composables/profile/api/mock/profile-visibility-mock';

/**
 * 내 프로필 API — 서버 주소 문자열(`/api/me/profile`)이 있는 **유일한 곳**이다. 상태는 없다. **서버 연결됨**(server/api `MyProfileController`).
 * 계약: `docs/features/member-mypage-contract.md` 「프로필」·「후속 작업: 닉네임 변경권」, `docs/features/commerce-stage1-contract.md`(변경권·아바타). 로그인 필요(401), 본인 것만, `PUT` 은 CSRF.
 *
 *  - `GET /api/me/profile` → `MyProfile`(닉네임·한 줄 소개·가입 시각·닉네임 변경 상태 `nicknameChange`(변경권 수·값 포함)·`avatarUrl`). 정지 중에도 읽는다.
 *  - `PUT /api/me/profile` `{ nickname, bio, useTicket? }` → `MyProfile`. 닉네임이 그대로면(소개만) 변경 횟수·변경권을 쓰지 않고 검사도 하지 않는다. 대소문자만 바꿔도 변경으로 센다.
 *    오류(서버 검사 순서): `401` → `403 ACCOUNT_RESTRICTED`(정지·영구 정지 중 — 글쓰기와 같다) → `400 VALIDATION` `fields.nickname|bio` →
 *    `409 NICKNAME_TICKET_REQUIRED`(무료를 썼고 변경권을 쓰지 않았거나 없다 — 최상위 `ticketCount`·`ticketPrice`) → `409 NICKNAME_TAKEN`.
 *  - 프로필 아바타 고르기는 `useAvatarApi().select`(`PUT /api/me/profile/avatar`).
 */
const PATH = {
    me: '/api/me/profile',
};

export function useProfileApi() {
    // CSRF 쿠키를 다시 받는 호출 — setup 문맥에서 미리 잡아 둔다(콜백 안에서 컴포저블을 부르지 않는다)
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });
    // MOCK: 공개 범위(`visibility`)는 서버에 아직 없어 이 기기에 둔다 — 로그인한 회원 ID 로 고른다
    const { user } = useAuth();

    return {
        /** 내 프로필 */
        // REAL: me: (): Promise<MyProfile> => client.get<MyProfile>(PATH.me),
        // MOCK: 실제 응답에 이 기기의 공개 범위를 겹친다
        me: async (): Promise<MyProfile> => ({ ...(await client.get<MyProfile>(PATH.me)), visibility: mockVisibilityOf(user.value?.id) }),

        /** 닉네임·한 줄 소개·공개 범위 저장 — 무료 변경을 썼으면 `useTicket: true` 로 변경권 1장을 써서 바꾼다 */
        // REAL: save: (body: MyProfileRequest): Promise<MyProfile> => client.put<MyProfile>(PATH.me, body),
        // MOCK: 닉네임·소개는 실제 서버로(공개 범위 칸은 빼고), 실제 저장이 끝난 뒤 공개 범위를 이 기기에 둔다
        save: async (body: MyProfileRequest): Promise<MyProfile> => {
            const { visibility, ...real } = body;
            const saved = await client.put<MyProfile>(PATH.me, real);
            if (visibility && user.value) mockSaveVisibility(user.value.id, visibility);
            return { ...saved, visibility: mockVisibilityOf(user.value?.id) };
        },
    };
}
