/**
 * 글 API 가 주고받는 모양 — 목록 한 줄(`PostRow`)·글 하나(`PostDetail`)·쓰기/수정/삭제 요청. 서버 `server/api/.../post/dto`·`board/dto/request` 와 같다(2026-10-22 연결).
 * community `docs/api-board.md` 의 글 모양에서 roboyam 개념(비회원 글·비밀번호·IP 마스킹, 문자열 Snowflake ID)으로 옮겼다.
 * ⚠ ID 는 모두 문자열이다(Snowflake 64비트). 숫자로 바꾸지 않는다. 시각은 UTC ISO 글자.
 */
import type { Role } from '~/composables/auth/api/useAuthApi.types';
import type { BoardProtectDelete, PrefixView } from '~/composables/board/api/useBoardApi.types';
import type { BlockedBy } from '~/composables/block/api/useBlockApi.types';
import type { DebateSummary, PostDebateOpen } from '~/composables/board/api/useDebateApi.types';
import type { PollInput, PollView } from '~/composables/board/api/usePollApi.types';
import type { AuthorDecoration } from '~/composables/pointshop/api/usePointShopApi.types';

/**
 * 본문 형식 — `TEXT` 평문(Textarea, 글자로 그린다) · `HTML` 에디터 HTML(서버가 정화한 것만 돌려준다 — 글 보기·댓글 보기만 `v-html` 로 그린다).
 * 쓰기 요청의 형식은 게시판 에디터 설정을 따른다(`UtilBoard.postFormat`·`commentFormat`). 응답에 없으면(옛 응답) `TEXT` 로 본다.
 */
export type ContentFormat = 'TEXT' | 'HTML';

/** 작성자 종류 — 회원 · 비회원 */
export type PostAuthorKind = 'MEMBER' | 'GUEST';

/** 글·댓글의 작성자 */
export interface PostAuthor {
    kind: PostAuthorKind;
    /** 회원 ID — 비회원은 null */
    memberId: string | null;
    /** 회원은 닉네임, 비회원은 입력한 이름 */
    name: string;
    /** 회원 등급 키(등급 목록의 `gradeKey` — 고정 enum 이 아니다) — 비회원은 null */
    grade: string | null;
    /** 서버가 준 등급 이름 — 없으면 기본 이름 */
    gradeLabel: string | null;
    /**
     * 회원이면 늘 `USER`, 비회원은 null(서버 2026-10-22) — 운영자는 공개 사이트에 로그인하지 않고, 게시판 매니저·스텝 표시는 아직 응답에 없다.
     * 그래서 화면은 이 값으로 공식 뱃지를 고르지 않는다.
     */
    role: Role | null;
    /**
     * 비회원 글·댓글의 작성 IP 앞 두 묶음 — IPv4 `123.45.*.*` · IPv6 `2001:db8:*:*`. 서버가 가려서 준다(회원 글은 null).
     */
    ipMasked: string | null;
    /**
     * 내가 이 회원에게 남긴 이용자 메모(나에게만 오는 값) — **서버 후보**(`member-social-contract.md`). 지금 서버 응답에는 없다(칸이 없으면 메모 없음).
     * 화면은 이 기기에서 방금 바꾼 것을 겹쳐 그린다(`useBlockOverrides().memoOf`).
     */
    memo?: string | null;
    /**
     * 작성자 장식(포인트 상점에서 산 닉네임 색·프로필 테두리를 장착한 것) — 서버가 준다(칸이 없는 옛 응답이면 장식 없음).
     * 만료된 장착은 서버가 null 로 준다. 내 것은 이 기기에서 방금 바꾼 것을 겹쳐 그린다(`useMyDecoration().decorationOf`).
     */
    decoration?: AuthorDecoration | null;
    /**
     * 프로필 아바타 그림 주소 — 서버가 준다(회원은 늘 채운다 — 고르지 않았으면 `basic-default` · 비회원·숨긴 글·지운 댓글은 null → 이니셜).
     * 화면은 `useMyDecoration().avatarOf` 로 채운다(내 것은 방금 고른 것).
     */
    avatarUrl?: string | null;
}

/** 말머리를 가리키는 짧은 모양 */
export type PostPrefixRef = Pick<PrefixView, 'id' | 'name' | 'staffOnly' | 'color'>;

/**
 * 목록에서 가려진 까닭 — 신고 숨김(신고 누적·불법촬영물·권리침해)은 늘 `REPORTED` 로 온다.
 * 운영자 숨김은 게시판 옵션 `staffHiddenNotice` 가 켜졌으면 `STAFF`(「운영자가 숨긴 글이에요」), 꺼졌으면 `REPORTED` 로 온다(post-admin-contract 「공개 쪽 영향」).
 */
export type PostHiddenReason = 'REPORTED' | 'STAFF';

/**
 * 소모임 글의 공개 상태(비멤버에게 보이는가) — 비공개(그 소모임 멤버만 읽는다) · 공개(누구나).
 * community `docs/api-groups.md` 8절·11절 — 공개 소모임의 글은 쓰자마자 `PUBLIC`, 비공개 소모임의 글은 승인·직접 공개해야 `PUBLIC` 이다.
 */
export type PostGroupVisibility = 'PRIVATE' | 'PUBLIC';

/** 글이 모두에게 보이는 까닭(11-6·11-7) — 공개 소모임이라서 · 공개 요청 승인 · 모임장·매니저가 직접 공개. 비멤버에게 안 보이면 null */
export type PostGroupPublicBy = 'GROUP' | 'APPROVED' | 'DIRECT';

/**
 * 글 상세의 공개 요청 상태(11-7 `publishRequest`) — 작성자와 처리자(모임장·`UNPUBLISH` 매니저)에게만 온다.
 * 거절이면 `rejectReason`(있을 때)·`decidedAt`·`canRetryAt`(다시 요청할 수 있는 시각, 거절 한도에 닿았으면 null) 이 있다.
 */
export interface PostGroupPublishRequest {
    status: 'PENDING' | 'REJECTED';
    requestedAt: string;
    note?: string | null;
    rejectReason?: string | null;
    decidedAt?: string | null;
    canRetryAt?: string | null;
    rejectCount: number;
}

/**
 * 글 줄의 소모임 표시(community `GroupRef`) — 소모임 글이면 `{ id, name, visibility }`, 일반 글이면 null(옛 응답은 칸이 없다).
 * 홈·검색·프로필에 나오는 소모임 글은 모두 `PUBLIC`, 소모임 글 탭에서는 `PRIVATE` 도 나온다.
 * `dissolved`: 해산한 소모임의 공개 글(소모임 연결은 남는다) — 화면은 링크 없이 「해산한 소모임」으로 보인다.
 */
export interface PostGroupRef {
    id: string;
    name: string;
    visibility: PostGroupVisibility;
    dissolved?: boolean;
}

/**
 * 글 상세의 소모임 칸(community `GroupPostInfo` 8절 + 11-7 의 5단계 칸).
 * **공개 범위 기능이 꺼져 있고 비공개 소모임이면 `groupVisibility` 가 null** 이다 — 그때는 옛 의미 그대로다:
 *   `canPublish` 내가 작성자·지금도 멤버·쓸 수 있고 아직 비공개 / `canUnpublish` 공개 글이고 내가 작성자·모임장·`UNPUBLISH` 매니저.
 * **기능이 켜져 있으면**(`groupVisibility` 가 있다) `canPublish` = 모임장·권한 매니저의 직접 공개(비공개 소모임·비공개 글), `canUnpublish` = 비공개 소모임의 개별 공개 글 되돌리기
 *   (공개 소모임의 글은 false — 그쪽은 `canMakeMembersOnly`). 패널 모습은 `UtilPostGroupPanel.stateOf` 가 이 칸들로만 정한다.
 * 소모임 글에는 이전·다음 글이 없다(서버가 `prev`·`next` 를 null 로 준다).
 */
export interface PostGroupInfo extends PostGroupRef {
    canPublish: boolean;
    canUnpublish: boolean;
    /** 소모임의 공개 범위 — 기능이 꺼져 있고 비공개 소모임이면 null(옛 패널) */
    groupVisibility?: 'PUBLIC' | 'PRIVATE' | null;
    publicBy?: PostGroupPublicBy | null;
    /** 공개 소모임에서 모임장·매니저가 멤버 전용으로 돌린 글 */
    membersOnly?: boolean;
    publishRequest?: PostGroupPublishRequest | null;
    /** 작성자·비공개 소모임·아직 비공개·쓸 수 있음·요청 조건 통과 */
    canRequestPublish?: boolean;
    /** 처리자이고 요청이 `PENDING` */
    canDecidePublishRequest?: boolean;
    /** 처리자·공개 소모임·지금 비멤버에게 보이는 글 */
    canMakeMembersOnly?: boolean;
    /** 처리자·멤버 전용으로 돌려 둔 글 */
    canReleaseMembersOnly?: boolean;
    /** 비멤버·비회원에게만 숫자(공개 전에 달려 가려진 댓글 수), 멤버는 null */
    membersOnlyCommentCount?: number | null;
    /** 공개 요청 한마디·거절 사유의 글자 수 한도(관리자 설정 10~100) — 칸이 없으면 100 */
    publishTextMax?: number;
}

/** 목록 한 줄 */
export interface PostRow {
    id: string;
    prefix: PostPrefixRef | null;
    /** 가려진 글은 빈 글자 */
    title: string;
    commentCount: number;
    /** 본문에 이미지가 있다 */
    hasImage: boolean;
    /** 카드·갤러리용 썸네일 — 지금 서버는 늘 null(첨부·에디터가 생기면 채운다). 화면은 자리 그림을 그린다 */
    thumbnailUrl: string | null;
    author: PostAuthor;
    createdAt: string;
    viewCount: number;
    likeCount: number;
    /** 고정 공지 */
    pinned: boolean;
    /** 19금 */
    adult: boolean;
    /** 가려진 글이면 그 까닭 — 제목·작성자 자리에 「신고로 숨겨진 글이에요」 */
    hiddenReason: PostHiddenReason | null;
    /** 투표가 붙은 글 — 제목 옆 투표 칩(post-poll-contract). 옛 응답에는 없을 수 있다(없으면 없음) */
    hasPoll?: boolean;
    /**
     * 내가 차단한 회원의 글(`USER`)·차단한 단어가 제목에 든 글(`WORD`) — **서버 후보**(`member-social-contract.md`). 지금 서버 응답에는 없다.
     * 접는 것은 화면의 일이다(`FoldedRow`, `useBlockOverrides().blockedOf`).
     */
    blocked?: BlockedBy | null;
    /** 소모임 글이면 그 소모임(2단계) — 일반 글·옛 응답은 null·없음 */
    group?: PostGroupRef | null;
}

/** 목록 정렬 — 최신순 · 오래된순 */
export type PostSort = 'LATEST' | 'OLDEST';

/** `GET /api/boards/{slug}/posts?prefix&sort&q&page` */
export interface PostListQuery {
    /** 말머리 ID — 없으면 전체 */
    prefix: string;
    sort: PostSort;
    /** 게시판 안 검색어(제목·내용·작성자) — 비면 검색하지 않는다 */
    q: string;
    /** 1부터 */
    page: number;
}

export interface PostListResponse {
    /** 고정 공지 — 첫 쪽·말머리/검색 없음일 때만, 최대 `pinnedMax` */
    pinned: PostRow[];
    items: PostRow[];
    page: number;
    totalPages: number;
    totalCount: number;
}

/**
 * 게시판이 섞인 목록(최신글·인기글·검색·회원 프로필 작성글)의 게시판 표시 — 글 줄 앞에 게시판 이름 태그로 그린다.
 * community `PostRow.board` 와 같은 자리다(`docs/features/member-activity-contract.md`). `nameEn` 은 영문 화면에서 이름 대신 쓴다.
 */
export interface PostBoardRef {
    slug: string;
    name: string;
    nameEn: string | null;
    /** 운영자가 고른 게시판 표시색의 프리셋 이름 또는 null(자동) */
    color: string | null;
}

/** 게시판이 섞인 목록의 한 줄 — 목록 한 줄 + 그 글의 게시판 */
export interface PostFeedRow extends PostRow {
    board: PostBoardRef;
}

/** 커서로 이어 받는 목록 한 쪽(최신글 전체·회원 작성글) — `nextCursor` 를 다음 요청의 `cursor` 로 보낸다 */
export interface PostFeedPage {
    items: PostFeedRow[];
    hasNext: boolean;
    nextCursor: string | null;
}

/** 이전·다음 글 */
export interface PostNeighbor {
    id: string;
    title: string;
}

/** 댓글 많은 글 보호 — 서버가 지금 상태로 정해 준다 */
export interface PostProtection {
    /** 보이는 댓글 수가 기준 이상이라 보호 중 */
    active: boolean;
    /** 보호 중이라 수정을 막는다 */
    editBlocked: boolean;
    /** 보호 중일 때 삭제 방식 */
    deleteMode: BoardProtectDelete;
    /** 예약 삭제까지의 시간(분) */
    delayMinutes: number;
    /** 보호 기준(댓글 수) */
    threshold: number;
}

/** `GET /api/posts/{id}` — 볼 때 조회수가 오른다(중복 방지는 서버). 가려진 글은 `403 POST_HIDDEN`, 없거나 지운 글은 `404 POST_NOT_FOUND`(가정) */
export interface PostDetail {
    id: string;
    board: { id: string; slug: string; name: string; nameEn: string | null };
    prefix: PostPrefixRef | null;
    title: string;
    /** 본문 — `contentFormat` 이 `TEXT` 면 평문(빈 줄로 문단을 나눈다), `HTML` 이면 서버가 정화한 HTML */
    content: string;
    /** 본문 형식 — 없으면(옛 응답) `TEXT` */
    contentFormat?: ContentFormat;
    author: PostAuthor;
    createdAt: string;
    /** 수정했으면 그 시각 */
    updatedAt: string | null;
    viewCount: number;
    likeCount: number;
    /** 내가 이미 추천했다(추천은 취소할 수 없다) */
    liked: boolean;
    commentCount: number;
    adult: boolean;
    /** 로그인한 내 글 — 수정·삭제를 바로 한다 */
    mine: boolean;
    protection: PostProtection;
    /** 예약 삭제된 글이면 지워질 시각 */
    deleteScheduledAt: string | null;
    /** 이전 글(더 오래된 글) · 다음 글(더 새 글) — 같은 게시판, 없으면 null */
    prev: PostNeighbor | null;
    next: PostNeighbor | null;
    /** 본문 외부 링크에 `rel="nofollow ugc noopener"` 를 단다(관리자 보안 `externalLinkNofollow`) */
    externalLinkNofollow: boolean;
    /**
     * 내가 차단한 회원의 글(`USER`)·차단한 단어가 제목에 든 글(`WORD`) — 게시판 글 목록 줄과 같은 판정(2026-10-24). 비로그인·내 글·수정 응답은 null.
     * 서버는 본문을 그대로 싣는다 — 접는 것은 화면의 일이다(`FoldedRow`, `useBlockOverrides().blockedOf`).
     */
    blocked?: BlockedBy | null;
    /** 지금 하이라이트로 노출 중인 글(노출 켬 + 기간 안) — 제목 위에 「하이라이트」 표시. 찬반 토론과 무관(토론은 글의 기능) */
    highlighted?: boolean;
    /** 찬반 토론 요약 — 늘 있다(토론이 없으면 `enabled false`). 옛 응답에는 없을 수 있다 */
    debate?: DebateSummary;
    /** 글에 붙은 투표(post-poll-contract) — 없으면 null. 옛 응답에는 없을 수 있다 */
    poll?: PollView | null;
    /**
     * 소모임 글이면 그 소모임과 공개 전환 권한(2단계) — 일반 글·옛 응답은 null·없음.
     * 소모임 글의 게시판은 공개 게시판 API(`GET /api/boards/{slug}`)에 없으므로 화면은 `GET /api/groups/{id}/board` 로 게시판 정보를 받는다(`useGroupApi.board`).
     */
    group?: PostGroupInfo | null;
}

/** 비회원 작성 정보 — 이름·비밀번호(수정·삭제용, 서버는 해시로만 저장)·자동입력 방지 토큰 */
export interface GuestCredentials {
    name: string;
    password: string;
    /**
     * 자동입력 방지 결과 — 지금 화면은 "로봇이 아닙니다" 체크의 자리 표시 값을 보낸다. 서버에 Turnstile 비밀키가 없으면 비어 있지만 않으면 통과하고,
     * 있으면 `400 VALIDATION fields['guest.captchaToken']` 으로 거절된다(Turnstile 위젯은 후속)
     */
    captchaToken: string;
}

/** `POST /api/posts` — 응답 `PostWriteResult`(201) */
export interface PostWriteRequest {
    boardSlug: string;
    prefixId: string | null;
    title: string;
    content: string;
    /** 게시판 글 에디터가 `NONE` 이면 `TEXT`, 에디터면 `HTML` */
    contentFormat: ContentFormat;
    adult: boolean;
    /** 비회원이면 필수, 회원이면 null */
    guest: GuestCredentials | null;
    /** 글과 함께 찬반 토론을 연다(회원이고 게시판 `debateEnabled` 일 때만 — 아니면 `400 fields.debate`). 열지 않으면 null */
    debate?: PostDebateOpen | null;
    /** 글과 함께 투표를 붙인다(회원이고 게시판 `pollEnabled` 일 때만 — 아니면 `400 fields.poll`). 붙이지 않으면 null */
    poll?: PollInput | null;
}

export interface PostWriteResult {
    id: string;
}

/** `PUT /api/posts/{id}` — 비회원 글은 `password` 필수 */
export interface PostUpdateRequest {
    prefixId: string | null;
    title: string;
    content: string;
    contentFormat: ContentFormat;
    adult: boolean;
    password: string | null;
    /**
     * 투표 — 보내면 만들기·고치기, `null` 이면 빼기(첫 표 뒤면 `409 POLL_LOCKED`, 지금과 같은 내용은 그대로). **칸을 아예 빼면 그대로 둔다**
     * (화면은 투표 칸을 바꾸지 않았거나 표가 있어 고칠 수 없을 때 칸을 뺀다)
     */
    poll?: PollInput | null;
}

/** `DELETE /api/posts/{id}` 본문 — 비회원 글은 `password` 필수 */
export interface PostDeleteRequest {
    password: string | null;
}

/** 삭제 결과 — 보호 중이고 예약 삭제면 `scheduledAt` 이 있다 */
export interface PostDeleteResult {
    scheduledAt: string | null;
}

/** `POST /api/posts/{id}/verify-password` */
export interface PostPasswordRequest {
    password: string;
}

/** `POST /api/posts/{id}/like` — 추천은 한 번만, 취소 없음 */
export interface PostLikeResult {
    likeCount: number;
    liked: boolean;
}
