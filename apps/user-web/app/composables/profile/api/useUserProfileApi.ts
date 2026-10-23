import { mockUserComments, mockUserPosts, mockUserProfile } from '~/composables/profile/api/mock/user-profile-mock';
import type { AuthorCommentPage, PublicProfile, UserFeedQuery, UserPostPage } from '~/composables/profile/api/useUserProfileApi.types';

/**
 * 공개 회원 프로필 API — 서버 주소 문자열(`/api/users/{id}/…`)이 있는 **유일한 곳**이다. 상태는 없다. **비회원도** 읽는다.
 * 계약 후보: `docs/features/member-activity-contract.md`(모양은 community `docs/api-profile.md`「공개 프로필」). 내 프로필 고치기는 `useProfileApi`(`/api/me/profile`).
 *
 *  - `GET /api/users/{id}/profile` → `PublicProfile` · 없는·탈퇴한 회원 `404 PROFILE_NOT_FOUND`
 *  - `GET /api/users/{id}/posts?size&cursor` → `UserPostPage`(게시판이 섞인 글 줄, 커서 더보기)
 *  - `GET /api/users/{id}/comments?size&cursor` → `AuthorCommentPage` · 틀린 커서 `400 VALIDATION fields.cursor`
 *
 * ⚠ MOCK: 서버에 API 가 없어 `./mock/user-profile-mock.ts` 를 부른다(작성글은 실제 공개 게시판 글에서 고른다). 서버가 붙으면 `// MOCK:` 줄과 목업 import 를 지우고 `// REAL:` 줄을 살린다.
 */
const PATH = {
    profile: (id: string) => `/api/users/${encodeURIComponent(id)}/profile`,
    posts: (id: string) => `/api/users/${encodeURIComponent(id)}/posts`,
    comments: (id: string) => `/api/users/${encodeURIComponent(id)}/comments`,
};

export function useUserProfileApi() {
    // REAL: const auth = useAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    // MOCK: 목업은 실제 게시판 글을 모으고, 본인인지는 로그인 정보로 안다
    const boardApi = useBoardApi();
    const source = { boards: () => boardApi.boards(), posts: boardApi.posts };
    const { user } = useAuth();
    void PATH;

    return {
        /** 프로필 머리 — 닉네임·등급·소개·가입일·수치 */
        profile: (id: string, signal?: AbortSignal): Promise<PublicProfile> =>
            // MOCK:
            (void signal, mockUserProfile(source, id, user.value)),
        // REAL: client.get<PublicProfile>(PATH.profile(id), { signal }),

        /** 작성글 한 쪽 */
        posts: (id: string, query: UserFeedQuery, signal?: AbortSignal): Promise<UserPostPage> =>
            // MOCK:
            (void signal, mockUserPosts(source, id, query)),
        // REAL: client.get<UserPostPage>(PATH.posts(id), { query: { size: query.size, cursor: query.cursor }, signal }),

        /** 작성 댓글 한 쪽 */
        comments: (id: string, query: UserFeedQuery, signal?: AbortSignal): Promise<AuthorCommentPage> =>
            // MOCK:
            (void signal, mockUserComments(source, id, query)),
        // REAL: client.get<AuthorCommentPage>(PATH.comments(id), { query: { size: query.size, cursor: query.cursor }, signal }),
    };
}
