/**
 * 알림 이벤트 API 가 주고받는 모양 — 서버 `NotificationEventView`·`MyNotificationEventView` (`docs/features/notification-contract.md` 「API」).
 * 이벤트의 정의(코드·영역·심각도·수신 규칙·필수 여부)는 서버 코드가 원천이고, 운영자가 바꾸는 것은 `enabled`·`emailEnabled` 뿐이다.
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
    /** 이메일 전달 여부(`emailable=false` 면 늘 false) */
    emailEnabled: boolean;
    /** false 면 발행처가 아직 없는 예약 이벤트 — 화면에 「준비 중」으로만 보이고 바꿀 수 없다 */
    featureReady: boolean;
    /** 최근 30일 발송 수(알림 행 수) */
    sentLast30Days: number;
}

/** `PUT /api/admin/notification-events/{code}` 의 요청 (권한 `notification.manage`) — 응답은 바뀐 한 줄(`NotificationEventView`) */
export interface NotificationEventUpdateRequest {
    enabled: boolean;
    emailEnabled: boolean;
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
