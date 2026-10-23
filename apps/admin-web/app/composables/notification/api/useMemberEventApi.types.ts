/**
 * 회원 이벤트 API 의 모양 — `docs/features/message-template-contract.md` 「개정 3」 이벤트 화면(회원 이벤트). **화면 목업 단계 — 서버에 아직 없다.**
 * 회원 이벤트 = 공개 사이트 회원이 받는 알림의 일. 지금 코드의 회원 알림 5종(`MemberNotificationType`)과 게시판 기본 설정 「알림」의 7종을 하나로 합친 목록이다 —
 * 실제로 일어나는 것만 바꿀 수 있고(`featureReady`), 나머지는 「준비 중」. 이벤트의 정의(이름·영역·받는 사람·끌 수 있는지의 잠금)는 서버 코드가 원천이다.
 * 게시판 기본 설정 「알림」 탭의 이벤트별 칸(`board_notification_settings.member_defaults`)은 서버 단계에서 이 자원으로 옮긴다(목업 가정).
 */

/** 회원이 이 이벤트를 끌 수 있는지가 코드로 잠긴 까닭 — 계정 알림(포인트 회수·신고로 숨김) · 운영 처리 알림(사유와 함께 보낸다) */
export type MemberEventLockReason = 'ACCOUNT' | 'ACTION';

/** `GET /api/admin/member-events` 의 한 줄(권한 `notification.read`) */
export interface MemberEventView {
    /** 이벤트 코드 — 실제 이벤트는 `MemberNotificationType` 이름(`COMMENT` …), 준비 중은 게시판 기본 설정의 종류 이름(`MENTION` …) */
    code: string;
    /** 영역 id(`content` · `member` · `safety` — 알림 화면과 같은 값) */
    area: string;
    /** 서버가 요청 언어로 푼 이름·설명·받는 사람 문구 */
    name: string;
    description: string;
    recipientText: string;
    /** false 면 아직 일어나지 않는 이벤트 — 「준비 중」으로만 보이고 바꿀 수 없다 */
    featureReady: boolean;
    enabled: boolean;
    /** 앱 알림(회원 알림함)으로 보낸다 */
    inApp: boolean;
    /** 이메일로도 보낸다 */
    email: boolean;
    /** 회원이 개인 설정에서 끌 수 있다 — `lockReason` 이 있으면 늘 false */
    memberCanTurnOff: boolean;
    /** 끌 수 있는지를 코드가 잠갔다(바꿀 수 없다) — null 이면 운영자가 정한다 */
    lockReason: MemberEventLockReason | null;
    /** 게시판 기본 설정 「알림」의 같은 종류(옮겨 올 칸) — 없으면 null */
    boardSettingKind: string | null;
    /** 최근 30일 발송 수 */
    sentLast30Days: number;
}

/** `PUT /api/admin/member-events/{code}` 의 요청(권한 `notification.manage`) — 응답은 바뀐 한 줄 */
export interface MemberEventUpdateRequest {
    enabled: boolean;
    inApp: boolean;
    email: boolean;
    memberCanTurnOff: boolean;
}
