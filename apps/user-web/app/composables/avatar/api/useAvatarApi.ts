import type { AvatarCatalog, AvatarSelectRequest } from '~/composables/avatar/api/useAvatarApi.types';
import type { MyProfile } from '~/composables/profile/api/useProfileApi.types';

/**
 * 프로필 아바타 API — 서버 주소 문자열(`/api/avatars`·`/api/me/profile/avatar`)이 있는 **유일한 곳**이다. 상태는 없다(내 아바타의 겹치기는 `useMyDecoration`). **서버 연결됨**.
 * 계약: `docs/features/commerce-stage1-contract.md` 「1-2 화면 목업」 API 후보 「아바타」.
 *  - `GET /api/avatars` → `AvatarCatalog`(328줄, 카탈로그 순서 — 비로그인도 읽는다: 그때 `balance` 0, `owned`·`selected`·`refundable` false)
 *  - `PUT /api/me/profile/avatar` `{ avatarId }` → `MyProfile`(`avatarUrl` 포함). 오류는 `AvatarSelectRequest` 주석
 *  - 유료 아바타 사기는 따로 없다 — `usePointShopApi().purchase({ itemId })` 그대로(사도 프로필은 바뀌지 않는다, 화면이 「바로 프로필로 쓰기」로 이어 부른다)
 */
const PATH = {
    avatars: '/api/avatars',
    myAvatar: '/api/me/profile/avatar',
};

export function useAvatarApi() {
    // CSRF 쿠키를 다시 받는 호출 — setup 문맥에서 미리 잡아 둔다(콜백 안에서 컴포저블을 부르지 않는다)
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 카탈로그 전부 */
        catalog: (): Promise<AvatarCatalog> => client.get<AvatarCatalog>(PATH.avatars),

        /** 내 프로필 아바타를 고른다 — 응답은 저장 뒤의 내 프로필 */
        select: (body: AvatarSelectRequest): Promise<MyProfile> => client.put<MyProfile>(PATH.myAvatar, body),
    };
}
