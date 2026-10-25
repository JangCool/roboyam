/**
 * 내 알림 설정 API 가 주고받는 모양 — **서버 연결됨**(server/api `MyNotificationSettingsView`·`MyNotificationSettingView`·`MyNotificationSettingsRequest`). 계약: `docs/features/member-mypage-contract.md` 「알림 설정」.
 * 이벤트 목록·이름·잠금은 서버 회원 이벤트 카탈로그(shared `MemberEvent` — 관리자 「이벤트 › 회원」)가 원천이고, **준비 중이 아닌 이벤트만** 온다.
 */
import type { MemberNotificationType } from '~/composables/notification/api/useNotificationApi.types';

/** 이벤트 영역(서버 `NotificationArea`) — 내 글·댓글 활동 · 내 계정 · 신고·운영 */
export type MemberEventArea = 'content' | 'member' | 'safety';

/** 회원이 끌 수 없는 까닭 — 계정 알림(포인트 회수·신고로 숨김) · 운영 처리 알림 */
export type MemberEventLockReason = 'ACCOUNT' | 'ACTION';

/** 이메일을 받을 수 없는 까닭 — 이메일이 없는 계정 · 인증 전 */
export type NotificationEmailBlockedReason = 'NO_EMAIL' | 'NOT_VERIFIED';

/** 이벤트 한 줄 */
export interface MyNotificationSetting {
    /** 회원 이벤트 코드 — 지금은 회원 알림 종류(`MemberNotificationType`)와 같다 */
    code: MemberNotificationType;
    area: MemberEventArea;
    /** 서버가 요청 언어로 준 이름·설명(번들 `member.event.<코드>.name|description`) */
    name: string;
    description: string;
    /** 운영자가 앱 알림으로 보내는 이벤트인가(`enabled && inApp`) — 아니면 앱 알림 스위치를 그리지 않는다 */
    inAppOffered: boolean;
    /** 운영자가 이메일로 보내는 이벤트인가(`enabled && email`) — 아니면 이메일 스위치를 그리지 않는다 */
    emailOffered: boolean;
    /** 회원이 끌 수 있는가 — false 면 잠금, 받기가 늘 켜져 있다. 코드 잠금(`lockReason`)이거나 운영자가 「회원이 끌 수 있음」을 꺼 두었다 */
    memberCanTurnOff: boolean;
    /** 코드 잠금의 까닭만 — 운영자가 끌 수 없게 한 이벤트는 `memberCanTurnOff=false` 인데 `lockReason=null` 이다 */
    lockReason: MemberEventLockReason | null;
    /** 내 선택 — 앱 알림 받기(잠긴 이벤트·운영자가 꺼 둔 채널은 서버가 늘 true 로 준다) */
    inApp: boolean;
    /** 내 선택 — 이메일 받기 */
    email: boolean;
}

/** `GET`·`PUT /api/me/notification-settings` 응답 */
export interface MyNotificationSettings {
    /** 카탈로그 차례 */
    events: MyNotificationSetting[];
    /** 이메일을 받을 수 있는 계정인가(이메일이 있고 인증을 마침) — 아니면 이메일 스위치를 잠그고 까닭을 보인다 */
    emailReceivable: boolean;
    emailBlockedReason: NotificationEmailBlockedReason | null;
}

/** `PUT` 요청의 한 줄 — 회원이 끌 수 있는 이벤트만 보낸다. 세 칸 모두 필요(빠지면 `400 VALIDATION fields.events[i].code|inApp|email`) */
export interface MyNotificationSettingInput {
    code: MemberNotificationType;
    inApp: boolean;
    email: boolean;
}

/** `PUT /api/me/notification-settings` 요청 */
export interface MyNotificationSettingsRequest {
    events: MyNotificationSettingInput[];
}
