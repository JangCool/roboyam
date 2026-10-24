/**
 * 공개 회원 프로필 API 가 주고받는 모양 — **서버 연결됨**(server/api `PublicProfileView`·`AuthorCommentPageView`, 작성글은 `PostFeedPageView`). 모양은 community `docs/api-profile.md`「공개 프로필」에서 로보얌에 있는 것만 남겼다
 * (프로필 이미지·닉네임 이미지·꾸미기·소모임·스크랩·인기글 수 없음). 계약: `docs/features/member-activity-contract.md` 「공개 프로필」.
 * ⚠ ID 는 문자열(Snowflake), 시각은 UTC ISO.
 */
import type { Role } from '~/composables/auth/api/useAuthApi.types';
import type { PostFeedPage } from '~/composables/board/api/usePostApi.types';

/** 활동 수치 — 보이는 글 수 · 지우지 않은 댓글 수 · 글·댓글이 받은 추천 합(서버가 최대 60초 늦을 수 있다) */
export interface ProfileStats {
    posts: number;
    comments: number;
    likesReceived: number;
}

/**
 * `GET /api/users/{id}/profile` — **비회원도** 읽는다. 없는 회원·탈퇴한 회원·형식이 틀린 ID 는 `404 PROFILE_NOT_FOUND`(서버는 둘을 가르지 않는다). 정지된 회원의 프로필은 보인다.
 */
export interface PublicProfile {
    id: string;
    nickname: string;
    /** 등급 키(등급 목록의 `gradeKey`) */
    grade: string;
    gradeLabel: string;
    /** 회원은 늘 `USER`(운영자는 공개 사이트 회원이 아니다) */
    role: Role;
    /** 한 줄 소개 — 없으면 null */
    bio: string | null;
    joinedAt: string;
    /** 보는 사람이 이 회원 본인인가(비회원은 false) */
    isMe: boolean;
    stats: ProfileStats;
    /** 내가 이 회원을 차단했는가(비회원·본인은 false) — 소통 계약(`member-social-contract.md`)이 더하는 칸. 서버는 아직 내려 주지 않는다(없으면 false 로 본다) */
    blockedByMe?: boolean;
    /** 내가 이 회원에게 남긴 이용자 메모(없으면 null) — 나에게만 온다. 소통 계약이 더하는 칸이라 서버는 아직 내려 주지 않는다 */
    memo?: string | null;
}

/** 작성글 — `GET /api/users/{id}/posts?size&cursor`. 최신이 위, 삭제·숨김 글과 꺼진 게시판의 글은 빠진다 */
export type UserPostPage = PostFeedPage;

/** 작성 댓글 한 줄 — 누르면 그 글의 그 댓글로 간다 */
export interface AuthorCommentRow {
    id: string;
    postId: string;
    postTitle: string;
    /** 서버는 게시판 `id` 도 함께 준다(화면은 쓰지 않는다) */
    board: { slug: string; name: string; nameEn: string | null };
    /** 댓글 앞 100자(줄바꿈은 공백 한 칸, 넘으면 `…`) */
    bodyPreview: string;
    likeCount: number;
    /** 답글인가 */
    reply: boolean;
    createdAt: string;
}

/** `GET /api/users/{id}/comments?size&cursor` — 최신이 위. 삭제한 댓글과 볼 수 없는 글의 댓글은 빠진다. `size` 범위 밖 `400 VALIDATION fields.size` · 틀린 커서 `fields.cursor` */
export interface AuthorCommentPage {
    items: AuthorCommentRow[];
    hasNext: boolean;
    nextCursor: string | null;
}

/** 작성글·댓글 요청 */
export interface UserFeedQuery {
    cursor?: string;
    /** 1~50, 기본 20 — 범위 밖은 서버가 잘라 맞추지 않고 `400 VALIDATION fields.size` */
    size?: number;
}
