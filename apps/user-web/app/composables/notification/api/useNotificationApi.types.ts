/**
 * 회원 알림 API 가 주고받는 모양 — 서버 `membernotification/dto/response`(`MemberNotificationPageView`·`MemberNotificationItemView` …)와 같다.
 * 계약: `docs/features/member-notification-contract.md`. ⚠ ID 는 문자열이다(Snowflake). 시각은 UTC ISO.
 * `title`·`body` 는 서버가 요청 언어로 푼 문장이다(묶인 댓글 「mem3님 외 1개의 댓글…」·탈퇴한 회원·임시 숨김 문구 포함) — 화면은 그대로 보인다.
 */

/**
 * 알림 종류(서버 `MemberNotificationType`). `FEATURE_RESTRICTED` = 내 계정에 기능 제한(포인트 동결·공구 참여 제한·충전 제한)이 걸림(커머스 0-4 — 영역 SAFETY, 회원이 끌 수 없음,
 * 종류·끝·공개 사유를 서버가 `title`·`body` 로 풀어 준다. 운영 메모는 없다).
 */
export type MemberNotificationType = 'COMMENT' | 'REPLY' | 'POINTS_REVOKED' | 'POST_HIDDEN' | 'COMMENT_HIDDEN' | 'FEATURE_RESTRICTED';

/** 알림 묶음 — 댓글·답글 · 계정(포인트 회수·신고 숨김·기능 제한) */
export type MemberNotificationCategory = 'comment' | 'system';

/** 목록 거르기(`filter`) */
export type MemberNotificationFilter = 'all' | MemberNotificationCategory;

/** 알림을 일으킨 사람 — 댓글·답글만. 읽을 때의 현재 값(탈퇴면 `withdrawn` + 「탈퇴한 회원」) */
export interface NotificationActor {
    kind: 'MEMBER' | 'GUEST';
    memberId: string | null;
    name: string;
    withdrawn: boolean;
}

/** 알림이 가리키는 글 — 지웠거나 숨겼거나 게시판이 꺼졌으면 `available=false`, `title=null` */
export interface NotificationPostRef {
    id: string;
    title: string | null;
    available: boolean;
}

/** 포인트 회수 알림의 칸 */
export interface NotificationPoints {
    kind: 'ADMIN_ADJUST';
    /** 실제로 회수된 포인트(양수) */
    amount: number;
    note: string | null;
}

/** 알림 한 건 */
export interface MemberNotificationItem {
    id: string;
    type: MemberNotificationType;
    category: MemberNotificationCategory;
    read: boolean;
    createdAt: string;
    /** 한 줄 문장(서버가 요청 언어로 푼다) */
    title: string;
    /** 둘째 줄 — 댓글 앞부분 · 숨겨진 글 제목/댓글 발췌 · 회수 사유. 없으면 null */
    body: string | null;
    /** 누르면 갈 공개 사이트 경로(`/posts/{id}`). 갈 곳이 없으면 null(읽음 처리만) */
    link: string | null;
    actor: NotificationActor | null;
    post: NotificationPostRef | null;
    commentId: string | null;
    commentPreview: string | null;
    /** 묶인 댓글 수(1 = 묶이지 않음) */
    groupCount: number;
    points: NotificationPoints | null;
    /** 권리침해 임시조치로 숨겨졌으면 다시 보이는 시각, 그 밖은 null */
    hiddenUntil: string | null;
}

/** `GET /api/notifications` — 최신이 위, 커서 쪽 */
export interface MemberNotificationPage {
    items: MemberNotificationItem[];
    hasNext: boolean;
    nextCursor: string | null;
    /** 거르기와 상관없는 전체 안 읽은 수(상한 100) */
    unreadCount: number;
    /** 회원 알림 보관 일수 */
    retentionDays: number;
}

/** `GET /api/notifications` 의 조건 — 빈 값은 보내지 않는다 */
export interface MemberNotificationQuery {
    filter?: MemberNotificationFilter;
    /** 1~50(기본 20) */
    size?: number;
    cursor?: string;
}

/** `GET /api/notifications/unread-count` */
export interface UnreadCountResponse {
    /** 상한 100 */
    count: number;
}

/** `POST /api/notifications/read-all` */
export interface ReadAllResponse {
    /** 읽음으로 바꾼 개수 */
    updated: number;
}
