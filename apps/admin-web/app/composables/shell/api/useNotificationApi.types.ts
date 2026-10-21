/**
 * 관리자 알림(종 알림 서랍) API 가 주고받는 모양 — 서버 `NotificationItemView`·`NotificationPageView`·`NotificationSummaryView` (`docs/features/notification-contract.md` 「API」).
 * ⚠ ID 는 문자열이다(Snowflake 64비트). 숫자로 바꾸지 않는다.
 */

/**
 * 알림의 성격(심각도 4단계) — 아이콘·색을 고르는 데 쓴다. 이벤트 관리·내 이벤트 목록의 심각도도 같은 값이다.
 * `critical` 은 로그인 잠금 반복·결제 이상처럼 급한 것(빨강).
 */
export type NotificationKind = 'info' | 'success' | 'warning' | 'critical';

/** 항목을 눌렀을 때 갈 화면 — 서버가 이벤트의 링크 템플릿을 풀어 준 경로(쿼리 포함, 예: `/settings/batch-logs?jobName=…`). 화면이 `UtilAdminMenu.canOpen` 으로 열 권한을 확인한다 */
export interface NotificationLink {
    path: string;
}

export interface NotificationItem {
    id: string;
    /** 이벤트 코드(안정 식별자, 예: `BATCH_RUN_FAILED`) — 화면이 아이콘을 고르는 데 쓴다 */
    eventCode: string;
    /** 이벤트의 영역 id(`security` · `system` · `member` …) */
    area: string;
    kind: NotificationKind;
    /** 서버가 요청 언어(`Accept-Language`)로 만든 제목·본문 */
    title: string;
    body: string | null;
    /** 눌렀을 때 이동할 곳. 없으면 읽음 처리만 한다 */
    link: NotificationLink | null;
    /** 생긴 시각 — UTC ISO */
    createdAt: string;
    read: boolean;
}

/** `GET /api/admin/notifications?cursor&size` 의 응답 — 최근 것부터. 권한이 없는 이벤트·내가 끈 이벤트는 서버가 뺀다 */
export interface NotificationListResponse {
    items: NotificationItem[];
    /** 다음 쪽을 받을 때 `cursor` 로 보내는 값. 끝이면 null */
    nextCursor: string | null;
    /** 안 읽은 알림 수(이 쪽에 없는 것까지 센 서버 값) */
    unread: number;
}

/** `GET /api/admin/notifications/summary` 의 응답 — 폴링용으로 가볍다 */
export interface NotificationSummaryResponse {
    unread: number;
    /** 가장 최근 알림의 id. 알림이 없으면 null. 이 값이 바뀌면 새 알림이 있다 */
    latestId: string | null;
}

/**
 * 폴링(`useNotificationApi().subscribe`)이 상태 컴포저블(`useNotification`)에 묻고 알리는 통로 — API 컴포저블은 상태를 갖지 않으므로 필요한 것을 이 모양으로 받는다.
 */
export interface NotificationWatch {
    /** 지금 아는 가장 최근 알림 id — 목록을 아직 못 받았으면 `undefined`, 알림이 없으면 `null` */
    knownLatestId: () => string | null | undefined;
    /** true 면 이번 주기는 묻지 않는다(서랍이 열려 있어 목록을 직접 보고 있다) */
    isPaused: () => boolean;
    /** 요약을 받을 때마다 — 종의 안 읽은 수를 맞춘다 */
    onSummary: (summary: NotificationSummaryResponse) => void;
    /** 새 알림이 있어 목록 첫 쪽을 받았다 — `summary` 는 이 목록을 받게 한 요약 */
    onNew: (page: NotificationListResponse, summary: NotificationSummaryResponse) => void;
}

export interface NotificationListQuery {
    /** 이전 응답의 `nextCursor`. 없으면 첫 쪽 */
    cursor?: string;
    /** 쪽 크기(서버 기본 30, 최대 100) */
    size?: number;
}
