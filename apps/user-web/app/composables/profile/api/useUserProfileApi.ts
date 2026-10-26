import type { AuthorCommentPage, ProfileGroupsResponse, PublicProfile, UserFeedQuery, UserPostPage } from '~/composables/profile/api/useUserProfileApi.types';

/**
 * 공개 회원 프로필 API — 서버 주소 문자열(`/api/users/{id}/…`)이 있는 **유일한 곳**이다. 상태는 없다. **비회원도** 읽는다. **서버 연결됨**(server/api `UserProfileController`).
 * 계약: `docs/features/member-activity-contract.md` 「공개 프로필」(모양은 community `docs/api-profile.md`「공개 프로필」). 내 프로필 고치기는 `useProfileApi`(`/api/me/profile`).
 *
 *  - `GET /api/users/{id}/profile` → `PublicProfile` · 없는·탈퇴한 회원·형식이 틀린 ID `404 PROFILE_NOT_FOUND`
 *  - `GET /api/users/{id}/posts?size&cursor` → `UserPostPage`(게시판이 섞인 글 줄, 커서 더보기)
 *  - `GET /api/users/{id}/comments?size&cursor` → `AuthorCommentPage`
 *  - `GET /api/users/{id}/groups` → `ProfileGroupsResponse`(소모임 탭 — 6단계, community `docs/api-profile.md` 와 같은 경로·칸이라고 **가정**. 서버 6단계 코드가 생기면 대조한다)
 * 목록 오류: `size` 가 1~50 밖이면 `400 VALIDATION fields.size`(서버가 잘라 맞추지 않는다) · 틀린 커서 `400 VALIDATION fields.cursor`.
 */
const PATH = {
    profile: (id: string) => `/api/users/${encodeURIComponent(id)}/profile`,
    posts: (id: string) => `/api/users/${encodeURIComponent(id)}/posts`,
    comments: (id: string) => `/api/users/${encodeURIComponent(id)}/comments`,
    groups: (id: string) => `/api/users/${encodeURIComponent(id)}/groups`,
};

export function useUserProfileApi() {
    // CSRF 쿠키를 다시 받는 호출 — setup 문맥에서 미리 잡아 둔다(콜백 안에서 컴포저블을 부르지 않는다)
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 프로필 머리 — 닉네임·등급·소개·가입일·수치 */
        profile: (id: string, signal?: AbortSignal): Promise<PublicProfile> => client.get<PublicProfile>(PATH.profile(id), { signal }),

        /** 작성글 한 쪽 */
        posts: (id: string, query: UserFeedQuery, signal?: AbortSignal): Promise<UserPostPage> =>
            client.get<UserPostPage>(PATH.posts(id), { query: { size: query.size, cursor: query.cursor }, signal }),

        /** 작성 댓글 한 쪽 */
        comments: (id: string, query: UserFeedQuery, signal?: AbortSignal): Promise<AuthorCommentPage> =>
            client.get<AuthorCommentPage>(PATH.comments(id), { query: { size: query.size, cursor: query.cursor }, signal }),

        /** 소모임 탭 — 그 회원이 지금 멤버인 열린 소모임(최대 50개, 더보기 없음) */
        groups: (id: string, signal?: AbortSignal): Promise<ProfileGroupsResponse> => client.get<ProfileGroupsResponse>(PATH.groups(id), { signal }),
    };
}
