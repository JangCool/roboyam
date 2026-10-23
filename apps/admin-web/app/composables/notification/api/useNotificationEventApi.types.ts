/**
 * 알림 이벤트 API 가 주고받는 모양 — 서버 `NotificationEventView`·`MyNotificationEventView` (`docs/features/notification-contract.md` 「API」·「서버 구현 결과 (알림 도구·조직 구성)」).
 * 이벤트의 정의(코드·영역·심각도·수신 규칙·필수 여부·`broadcastAllowed`)는 서버 코드가 원천이고, 운영자가 바꾸는 것은 사용 여부·전달 도구(`channels`)·받는 범위(`defaultRecipients`)·팀(`teamIds`)뿐이다.
 * ⚠ 도구·팀 id 는 문자열이다(Snowflake). 기본 제공 도구의 id 는 `drawer`·`email`·`browser` 다.
 */
import type { NotificationKind } from '~/composables/shell/api/useNotificationApi.types';

/** 이벤트 관리 화면의 한 줄 — `GET /api/admin/notification-events` (권한 `notification.read`) */
export interface NotificationEventView {
    /** 이벤트 코드(안정 식별자, 예: `BATCH_RUN_FAILED`) */
    code: string;
    /** 영역 id(`security` · `system` · `member` …) */
    area: string;
    severity: NotificationKind;
    /** 서버가 요청 언어로 푼 이름·설명·수신 대상 문구(「배치 조회 권한을 가진 운영자」처럼 코드에 고정된 수신 규칙을 말로 푼 것) */
    name: string;
    description: string;
    recipientText: string;
    /** 필수(보안) 이벤트 — 끌 수 없다 */
    mandatory: boolean;
    /** 이메일로도 보낼 수 있는 이벤트인가 */
    emailable: boolean;
    /** 사용 여부 */
    enabled: boolean;
    /** 이메일 전달 여부 — `channels` 에 `email` 이 있는지와 같은 값(`emailable=false` 면 늘 false). 화면은 `channels` 를 본다 */
    emailEnabled: boolean;
    /** false 면 발행처가 아직 없는 예약 이벤트 — 화면에 「준비 중」으로만 보이고 바꿀 수 없다 */
    featureReady: boolean;
    /** 최근 30일 발송 수(알림 행 수) */
    sentLast30Days: number;
    /** 고른 전달 도구 id(서랍 제외 — 서랍은 늘 켜져 있다). `email`·`browser` 가 있으면 앞에, 그다음 Slack·Mattermost 도구 id */
    channels: string[];
    /** 추가 수신 팀 id — 이름은 `team.read` 가 있어야 팀 목록에서 읽는다 */
    teamIds: string[];
    /** 팀·공유 채널(Slack·Mattermost)로 보낼 수 있는 이벤트인가. 본인에게만 가는 이벤트(`*_ME`)·필수(보안) 이벤트는 false — 이메일·브라우저는 가능 */
    broadcastAllowed: boolean;
    /**
     * 받는 범위 — true(「권한자 전원」) = 이 이벤트를 볼 권한이 있는 운영자 전원 + 고른 팀원(중복 없이), false(「팀에만」) = 고른 팀의 팀원 중 볼 권한이 있는 사람만.
     * **없으면 true 로 본다**(이 칸이 생기기 전 서버) — 읽을 때는 `UtilNotification.defaultRecipientsOf` 를 쓴다. 본인 전용·필수 이벤트는 늘 true.
     * 「팀에만」인데 고른 팀이 모두 없어지거나 받는 사람이 0명이면 서버가 보낼 때 권한자 전원으로 되돌린다(알림이 사라지지 않게).
     */
    defaultRecipients?: boolean;
    /**
     * 「팀에만」을 고를 수 없는 이유(서버가 요청 언어로) — 모든 운영자에게 닿아야 하는 보안 경보(2차 인증 꺼짐)만 있다. 없으면 null(옛 서버는 칸이 없다).
     * 있으면 받는 범위는 늘 「권한자 전원」이고 false 저장은 400 `fields.defaultRecipients`. 팀 더하기는 된다.
     */
    teamsOnlyBlockedReason?: string | null;
}

/**
 * `PUT /api/admin/notification-events/{code}` 의 요청 (권한 `notification.manage`) — 응답은 바뀐 한 줄(`NotificationEventView`).
 * `channels` 를 보내면 그 목록이 전부이고 `email` 포함 여부가 곧 이메일 전달이다(서버는 `emailEnabled` 를 따로 받지 않는다). `teamIds` 는 보내야 바뀐다 —
 * 팀 선택을 **바꾸려면** `team.read` 도 있어야 한다(없으면 403 `PERMISSION_DENIED`). 현재 선택을 그대로 보내는 것은 `notification.manage` 만으로 된다.
 */
export interface NotificationEventUpdateRequest {
    enabled: boolean;
    channels: string[];
    teamIds: string[];
    /**
     * 받는 범위(보내지 않으면 서버가 지금 값을 둔다) — 화면은 늘 함께 보낸다. false 인데 `teamIds` 가 비면 400 `fields.teamIds`,
     * 본인 전용(`broadcastAllowed=false`)·필수(`mandatory`) 이벤트에서 false 면 400 `fields.defaultRecipients`.
     */
    defaultRecipients?: boolean;
}

/** 내 정보 > 개인 설정의 이벤트 한 줄 — `GET /api/admin/profile/notification/events` (내가 볼 수 있는 이벤트만, 준비 중 제외) */
export interface MyNotificationEventView {
    code: string;
    area: string;
    name: string;
    description: string;
    severity: NotificationKind;
    mandatory: boolean;
    /** 내가 끈 이벤트인가 */
    muted: boolean;
    emailable: boolean;
}

/** `PUT /api/admin/profile/notification/events/{code}` 의 요청 — 응답은 바뀐 한 줄(`MyNotificationEventView`) */
export interface MyNotificationEventMuteRequest {
    muted: boolean;
}
