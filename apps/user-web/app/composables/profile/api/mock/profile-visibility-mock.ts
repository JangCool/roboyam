// MOCK: 프로필 공개 범위(`profile-privacy-contract.md`) — 서버에 칸이 생기기 전까지 이 기기(localStorage)에 회원 ID 별로 둔다.
// 서버가 붙으면(`MyProfile.visibility`·`PublicProfile.visibility`·`PostAuthor.bio`) 이 파일과 `useProfileApi`·`useUserProfileApi`·`usePostAuthorBio` 의 `// MOCK:` 줄을 지운다.
//
// 같은 브라우저에서 A 로 로그인해 비공개로 바꾸고 B 로 로그인(또는 로그아웃)해 A 의 프로필을 열면 비공개로 보인다.
// 개발 스위치 `?mock=profile-private` — 남의 프로필을 모두 비공개로 본다(글 보기의 작성자 한 줄 소개도 빠진다).
// `?mock=author-bio` — 글 보기에서 소개가 빈 공개 회원에 예시 소개를 채운다(`usePostAuthorBio`).

import type { ProfileVisibility } from '~/composables/profile/api/useProfileApi.types';
import type { PublicProfile } from '~/composables/profile/api/useUserProfileApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import { mockFlags } from '~/composables/common/mock/mock-util';

const STORAGE_KEY = 'roboyam.mock.profileVisibility';

function readAll(): Record<string, ProfileVisibility> {
    if (typeof window === 'undefined') return {};
    try {
        const raw = window.localStorage.getItem(STORAGE_KEY);
        const parsed: unknown = raw ? JSON.parse(raw) : {};
        return parsed && typeof parsed === 'object' ? (parsed as Record<string, ProfileVisibility>) : {};
    } catch {
        return {};
    }
}

/** 그 회원의 공개 범위 — 정한 적이 없으면 `PUBLIC` */
export function mockVisibilityOf(userId: string | null | undefined): ProfileVisibility {
    if (!userId) return 'PUBLIC';
    return readAll()[userId] === 'PRIVATE' ? 'PRIVATE' : 'PUBLIC';
}

/** 그 회원의 공개 범위를 이 기기에 둔다 */
export function mockSaveVisibility(userId: string, value: ProfileVisibility): void {
    if (typeof window === 'undefined') return;
    try {
        const all = readAll();
        if (value === 'PRIVATE') all[userId] = 'PRIVATE';
        else delete all[userId];
        window.localStorage.setItem(STORAGE_KEY, JSON.stringify(all));
    } catch {
        // 저장소를 못 쓰면(사생활 보호 창 등) 이번 화면에서만 바뀐 것으로 본다
    }
}

/** 남이 보는 그 회원의 공개 범위 — 스위치 `profile-private` 이면 늘 비공개 */
export function mockVisibilityForViewer(userId: string, isMe: boolean): ProfileVisibility {
    if (!isMe && mockFlags().has('profile-private')) return 'PRIVATE';
    return mockVisibilityOf(userId);
}

/** 실제 공개 프로필 응답에 공개 범위를 겹친다 — 비공개이고 본인이 아니면 서버가 하듯 소개·가입일·수치를 비운다 */
export function mockApplyVisibility(profile: PublicProfile): PublicProfile {
    const visibility = mockVisibilityForViewer(profile.id, profile.isMe);
    if (visibility === 'PUBLIC' || profile.isMe) return { ...profile, visibility };
    return { ...profile, visibility, bio: null, joinedAt: null, stats: null };
}

/** 비공개 프로필의 목록(작성글·댓글·소모임) — 서버 후보 `403 PROFILE_PRIVATE` */
export function mockPrivateError(): ApiError {
    return new ApiError({ status: 403, code: 'PROFILE_PRIVATE', message: '비공개 프로필이에요.' });
}
