// MOCK: 알림 이벤트의 전달 도구·추가 수신 팀(후보 계약 `NotificationEventDelivery`)의 메모리 목업 — `useNotificationEventApi.ts` 가 이 파일의 함수를 부른다.
// 이벤트 목록 자체(`GET·PUT /api/admin/notification-events`)는 이미 실제 서버에 연결돼 있어 이 목업은 이벤트 **코드**만 키로 쓴다(이벤트 정의는 모른다).
// 서버가 붙으면 `NotificationEventView` 에 `channels`·`teamIds`·`broadcastAllowed` 가 합쳐지므로 이 파일과 `deliveries`·`setDelivery` 를 함께 지운다.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` (쉼표로 여러 개) ─
//   notification-delivery-error       전달 설정을 받는 요청이 `500 INTERNAL` 로 실패(이벤트 표 전체가 오류 상태)
//   notification-delivery-save-error  전달 도구·추가 수신 팀 저장이 `500 INTERNAL` 로 실패 — 낙관적 갱신이 되돌아오는지 확인
//
// ── 흉내 ─
//   · 본인에게만 가는 이벤트(코드가 `_ME` 로 끝난다: 내 비밀번호 변경·내 역할 변경 …)는 `broadcastAllowed=false` — 팀·Slack·Mattermost 로 보낼 수 없다(400 `NOTIFICATION_BROADCAST_NOT_ALLOWED`). 서버에서는 이벤트 정의의 수신 규칙(`SELF`)이 정한다.
//   · 없는 도구·없는 팀을 보내면 400(`NOTIFICATION_CHANNEL_NOT_FOUND` · `TEAM_NOT_FOUND`), 꺼진 도구·멈춘 팀을 **새로** 고르면 409(`NOTIFICATION_CHANNEL_DISABLED` · `TEAM_DISABLED`) — 이미 골라 둔 것을 두는 것은 허용.
//   · 도구·팀 목업은 서로 이 저장소를 부른다 — 도구를 지우면 그 도구가, 팀을 지우면 그 팀이 이벤트 설정에서 함께 빠진다(서버의 연쇄 삭제). 바꾼 값은 새로고침하면 처음으로 돌아간다.

import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { NotificationEventDelivery, NotificationEventDeliveryRequest } from '~/composables/notification/api/useNotificationEventApi.types';

/** 이벤트 코드 — 계약서의 초기 이벤트 11개 + 예약 이벤트 11개(실제 서버가 주는 22개와 같다) */
const EVENT_CODES = [
    'BATCH_RUN_FAILED',
    'OPERATOR_INVITATION_ACCEPTED',
    'OPERATOR_ACCOUNT_CREATED',
    'OPERATOR_STATUS_CHANGED_ME',
    'OPERATOR_LOCKED_BY_FAILURES',
    'ROLES_CHANGED_ME',
    'PASSWORD_CHANGED_ME',
    'PASSWORD_RESET_DONE_ME',
    'SITE_SETTINGS_CHANGED',
    'GRADE_SETTINGS_SAVED',
    'GRADE_PROMOTION_RUN',
    'REPORT_RECEIVED',
    'SANCTION_APPEAL_FILED',
    'BOARD_SANCTION_REPEAT_REVIEW',
    'NICKNAME_IMAGE_PENDING',
    'CLUB_OPEN_REQUEST_PENDING',
    'HOTDEAL_SUSPECTED_PROMO',
    'COMMERCE_ORDER_ANOMALY',
    'COMMERCE_REFUND_REQUESTED',
    'BUSINESS_APPLICATION_PENDING',
    'AD_REVIEW_PENDING',
    'OPERATOR_APPLICATION_PENDING',
];

const broadcastAllowedOf = (code: string): boolean => !code.endsWith('_ME');

/** 서버 메모리 — 처음 부를 때 만든다. 시작 값: 배치 실패는 Slack · 브라우저 + 운영팀, 로그인 잠금은 Mattermost + 보안팀 */
let store: Map<string, NotificationEventDelivery> | null = null;

function seeds(): Map<string, NotificationEventDelivery> {
    if (store) return store;
    store = new Map(EVENT_CODES.map((code) => [code, { code, channelIds: [], teamIds: [], broadcastAllowed: broadcastAllowedOf(code) }]));
    store.set('BATCH_RUN_FAILED', { code: 'BATCH_RUN_FAILED', channelIds: ['ch-slack-ops', 'browser'], teamIds: ['t-ops'], broadcastAllowed: true });
    store.set('OPERATOR_LOCKED_BY_FAILURES', { code: 'OPERATOR_LOCKED_BY_FAILURES', channelIds: ['ch-mm-dev', 'browser'], teamIds: ['t-sec'], broadcastAllowed: true });
    return store;
}

/** 이벤트 목록에는 있는데 이 목업이 모르는 코드는 빈 설정으로 본다(서버에 이벤트가 늘었을 때) */
const deliveryOf = (code: string): NotificationEventDelivery => seeds().get(code) ?? { code, channelIds: [], teamIds: [], broadcastAllowed: broadcastAllowedOf(code) };

// ── 다른 목업(도구·팀)이 부르는 연쇄·집계 ─

/** 이 도구를 고른 이벤트 수 */
export function mockEventCountOfChannel(channelId: string): number {
    return [...seeds().values()].filter((delivery) => delivery.channelIds.includes(channelId)).length;
}

/** 이 팀을 고른 이벤트 수 */
export function mockEventCountOfTeam(teamId: string): number {
    return [...seeds().values()].filter((delivery) => delivery.teamIds.includes(teamId)).length;
}

/** 도구를 지웠다 — 모든 이벤트에서 뺀다 */
export function mockRemoveChannelFromDeliveries(channelId: string): void {
    for (const delivery of seeds().values()) delivery.channelIds = delivery.channelIds.filter((id) => id !== channelId);
}

/** 팀을 지웠다 — 모든 이벤트에서 뺀다 */
export function mockRemoveTeamFromDeliveries(teamId: string): void {
    for (const delivery of seeds().values()) delivery.teamIds = delivery.teamIds.filter((id) => id !== teamId);
}

// ── API ─

export async function mockNotificationEventDeliveries(): Promise<NotificationEventDelivery[]> {
    await mockDelay();
    if (mockFlags().has('notification-delivery-error')) throw MOCK_ERRORS.internal();
    return [...seeds().values()].map((delivery) => ({ ...delivery, channelIds: [...delivery.channelIds], teamIds: [...delivery.teamIds] }));
}

/**
 * 한 이벤트의 전달 도구·추가 수신 팀을 바꾼다.
 * @param code 이벤트 코드.
 * @param body 바꿀 값.
 * @param known 지금 있는 도구·팀의 상태(`{ channels: id → 사용 여부, teams: id → 사용 여부 }`) — 도구·팀 목업이 알려 준다(서버는 DB 에서 본다).
 */
export async function mockNotificationEventSetDelivery(
    code: string,
    body: NotificationEventDeliveryRequest,
    known: { channels: Map<string, boolean>; teams: Map<string, boolean> },
): Promise<NotificationEventDelivery> {
    await mockDelay();
    if (mockFlags().has('notification-delivery-save-error')) throw MOCK_ERRORS.internal();
    const current = deliveryOf(code);
    if (!current.broadcastAllowed && (body.channelIds.length > 0 || body.teamIds.length > 0)) {
        throw new ApiError({ status: 400, code: 'NOTIFICATION_BROADCAST_NOT_ALLOWED', message: '본인에게만 가는 알림이라 팀이나 공유 채널로는 보낼 수 없어요.' });
    }
    for (const id of body.channelIds) {
        const enabled = known.channels.get(id);
        if (enabled === undefined) throw new ApiError({ status: 400, code: 'NOTIFICATION_CHANNEL_NOT_FOUND', message: '알림 도구를 찾을 수 없어요.' });
        if (!enabled && !current.channelIds.includes(id)) throw new ApiError({ status: 409, code: 'NOTIFICATION_CHANNEL_DISABLED', message: '꺼져 있는 알림 도구는 새로 고를 수 없어요. 도구를 먼저 켜 주세요.' });
    }
    for (const id of body.teamIds) {
        const enabled = known.teams.get(id);
        if (enabled === undefined) throw new ApiError({ status: 400, code: 'TEAM_NOT_FOUND', message: '팀을 찾을 수 없어요.' });
        if (!enabled && !current.teamIds.includes(id)) throw new ApiError({ status: 409, code: 'TEAM_DISABLED', message: '사용 안 함인 팀은 새로 고를 수 없어요. 팀을 먼저 켜 주세요.' });
    }
    const next: NotificationEventDelivery = { ...current, channelIds: [...body.channelIds], teamIds: [...body.teamIds] };
    seeds().set(code, next);
    return { ...next, channelIds: [...next.channelIds], teamIds: [...next.teamIds] };
}
