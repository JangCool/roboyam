// MOCK: 알림 이벤트 API(`GET·PUT /api/admin/notification-events[/{code}]`, `GET·PUT /api/admin/profile/notification/events[/{code}]`)의 메모리 목업 — `useNotificationEventApi.ts` 가 이 파일의 함수를 부른다.
// 서버가 붙으면 `notification/api/mock/` 와 함께 지우고, 화면 문구 `i18n/locales/{ko,en}/notificationEvent.ts`(아래 이름·설명·수신 대상 문구)도 지운다 — 서버가 `messages_ko/en.properties` 로 요청 언어에 맞춰 풀어 준다.
// 응답 모양·오류 코드는 `docs/features/notification-contract.md` 「API」(설계 확정)를 따른다. 이벤트 정의(`SEEDS`)는 계약서 「초기 이벤트」 11개 + 「예약 이벤트」 11개다.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` (쉼표로 여러 개) ─
//   notification-event-error       이벤트 관리 목록 요청이 `500 INTERNAL` 로 실패
//   notification-event-empty       등록된 이벤트가 없는 상태
//   notification-event-save-error  스위치 저장(이벤트 관리 `PUT`, 내 이벤트 끄기 `PUT`)이 `500 INTERNAL` 로 실패 — 낙관적 갱신이 되돌아오는지 확인
//   notification-myevent-error     내 이벤트 목록 요청이 `500 INTERNAL` 로 실패
//
// 이름·설명·수신 대상 문구는 서버가 요청 언어로 풀어 주는 값을 흉내 내려고 화면 번역표(`notificationEvent.<코드>.*`)에서 지금 언어로 푼다(`t` 를 받는다). 오류 문구는 서버 몫이라 한국어 그대로다.
// 내가 볼 수 있는 이벤트는 로그인한 운영자의 권한(`can`)으로 거른다(서버는 `requiredPermission` 으로 거른다). 바꾼 값은 새로고침하면 처음으로 돌아간다.

import type { Translate } from '#layers/i18n/utils/util-i18n';
import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { MyNotificationEventView, NotificationEventUpdateRequest, NotificationEventView } from '~/composables/notification/api/useNotificationEventApi.types';
import type { NotificationKind } from '~/composables/shell/api/useNotificationApi.types';

/** 서버 코드가 정의하는 이벤트 한 개 + 운영자가 바꾼 값 */
interface EventSeed {
    code: string;
    area: string;
    severity: NotificationKind;
    mandatory: boolean;
    emailable: boolean;
    featureReady: boolean;
    /** 이 권한이 있어야 알림을 본다(없으면 누구나) */
    requiredPermission?: string;
    enabled: boolean;
    emailEnabled: boolean;
    sentLast30Days: number;
}

const ready = (
    code: string,
    area: string,
    severity: NotificationKind,
    o: { mandatory?: boolean; emailable?: boolean; emailEnabled?: boolean; requiredPermission?: string; sent: number },
): EventSeed => ({
    code,
    area,
    severity,
    mandatory: o.mandatory ?? false,
    emailable: o.emailable ?? false,
    featureReady: true,
    requiredPermission: o.requiredPermission,
    enabled: true,
    emailEnabled: o.emailEnabled ?? false,
    sentLast30Days: o.sent,
});

const reserved = (code: string, area: string, severity: NotificationKind, requiredPermission: string): EventSeed => ({
    code,
    area,
    severity,
    mandatory: false,
    emailable: false,
    featureReady: false,
    requiredPermission,
    enabled: false,
    emailEnabled: false,
    sentLast30Days: 0,
});

function buildSeeds(): EventSeed[] {
    return [
        // 계약서 「초기 이벤트」 — 발행처가 이미 있는 것
        ready('BATCH_RUN_FAILED', 'system', 'warning', { emailable: true, emailEnabled: true, requiredPermission: 'batch.read', sent: 14 }),
        ready('OPERATOR_INVITATION_ACCEPTED', 'system', 'info', { requiredPermission: 'operator.manage', sent: 5 }),
        ready('OPERATOR_ACCOUNT_CREATED', 'system', 'info', { requiredPermission: 'operator.manage', sent: 3 }),
        ready('OPERATOR_STATUS_CHANGED_ME', 'security', 'warning', { mandatory: true, emailable: true, emailEnabled: true, sent: 7 }),
        ready('OPERATOR_LOCKED_BY_FAILURES', 'security', 'critical', { emailable: true, requiredPermission: 'operator.lock', sent: 2 }),
        ready('ROLES_CHANGED_ME', 'security', 'info', { mandatory: true, sent: 11 }),
        ready('PASSWORD_CHANGED_ME', 'security', 'warning', { mandatory: true, emailable: true, emailEnabled: true, sent: 9 }),
        ready('PASSWORD_RESET_DONE_ME', 'security', 'warning', { mandatory: true, emailable: true, emailEnabled: true, sent: 4 }),
        ready('SITE_SETTINGS_CHANGED', 'system', 'info', { requiredPermission: 'site.read', sent: 21 }),
        ready('GRADE_SETTINGS_SAVED', 'member', 'info', { requiredPermission: 'grade.read', sent: 6 }),
        ready('GRADE_PROMOTION_RUN', 'member', 'info', { requiredPermission: 'grade.read', sent: 30 }),
        // 계약서 「예약 이벤트」 — 기능이 만들어질 때 켠다(관리 화면에는 「준비 중」으로만)
        reserved('REPORT_RECEIVED', 'safety', 'info', 'report.manage'),
        reserved('SANCTION_APPEAL_FILED', 'safety', 'info', 'appeal.manage'),
        reserved('BOARD_SANCTION_REPEAT_REVIEW', 'safety', 'warning', 'sanction.manage'),
        reserved('NICKNAME_IMAGE_PENDING', 'member', 'info', 'nickname-image.manage'),
        reserved('CLUB_OPEN_REQUEST_PENDING', 'club', 'info', 'group.manage'),
        reserved('HOTDEAL_SUSPECTED_PROMO', 'hotdeal', 'warning', 'hotdeal.manage'),
        reserved('COMMERCE_ORDER_ANOMALY', 'groupbuy', 'critical', 'commerce.order.manage'),
        reserved('COMMERCE_REFUND_REQUESTED', 'groupbuy', 'warning', 'commerce.refund.approve'),
        reserved('BUSINESS_APPLICATION_PENDING', 'bizAd', 'info', 'business.manage'),
        reserved('AD_REVIEW_PENDING', 'bizAd', 'info', 'ad.manage'),
        reserved('OPERATOR_APPLICATION_PENDING', 'system', 'info', 'operator.manage'),
    ];
}

/** 서버 메모리 — 처음 부를 때 만든다 */
let seeds: EventSeed[] | null = null;
/** 내가 끈 이벤트 코드들 */
const muted = new Set<string>();

const store = (): EventSeed[] => (seeds ??= buildSeeds());

const notFound = (): ApiError => new ApiError({ status: 404, code: 'NOTIFICATION_EVENT_NOT_FOUND', message: '알림 이벤트를 찾을 수 없어요.' });
const mandatoryError = (): ApiError => new ApiError({ status: 409, code: 'NOTIFICATION_MANDATORY', message: '필수 알림은 끌 수 없어요.' });

function viewOf(t: Translate, seed: EventSeed): NotificationEventView {
    return {
        code: seed.code,
        area: seed.area,
        severity: seed.severity,
        name: t(`notificationEvent.${seed.code}.name`),
        description: t(`notificationEvent.${seed.code}.description`),
        recipientText: t(`notificationEvent.${seed.code}.recipient`),
        mandatory: seed.mandatory,
        emailable: seed.emailable,
        enabled: seed.enabled,
        emailEnabled: seed.emailEnabled,
        featureReady: seed.featureReady,
        sentLast30Days: seed.sentLast30Days,
    };
}

function myViewOf(t: Translate, seed: EventSeed): MyNotificationEventView {
    return {
        code: seed.code,
        area: seed.area,
        name: t(`notificationEvent.${seed.code}.name`),
        description: t(`notificationEvent.${seed.code}.description`),
        severity: seed.severity,
        mandatory: seed.mandatory,
        muted: muted.has(seed.code),
        emailable: seed.emailable,
    };
}

// ── 이벤트 관리 ─

export async function mockNotificationEventList(t: Translate): Promise<NotificationEventView[]> {
    await mockDelay();
    const flags = mockFlags();
    if (flags.has('notification-event-error')) throw MOCK_ERRORS.internal();
    if (flags.has('notification-event-empty')) return [];
    // 영역·코드순
    return [...store()].sort((a, b) => a.area.localeCompare(b.area) || a.code.localeCompare(b.code)).map((seed) => viewOf(t, seed));
}

export async function mockNotificationEventUpdate(t: Translate, code: string, body: NotificationEventUpdateRequest): Promise<NotificationEventView> {
    await mockDelay();
    if (mockFlags().has('notification-event-save-error')) throw MOCK_ERRORS.internal();
    const seed = store().find((item) => item.code === code);
    if (!seed) throw notFound();
    if (!seed.featureReady) throw new ApiError({ status: 409, code: 'NOTIFICATION_EVENT_NOT_READY', message: '아직 알림을 보내는 기능이 없는 이벤트라 바꿀 수 없어요.' });
    if (seed.mandatory && !body.enabled) throw mandatoryError();
    if (!seed.emailable && body.emailEnabled) {
        throw new ApiError({ status: 400, code: 'VALIDATION', message: '입력한 내용을 확인해 주세요.', fields: { emailEnabled: '이 이벤트는 이메일로 보내지 않아요.' } });
    }
    seed.enabled = body.enabled;
    seed.emailEnabled = body.emailEnabled;
    return viewOf(t, seed);
}

// ── 내 이벤트 끄기 ─

export async function mockMyNotificationEventList(t: Translate, can: (code: string) => boolean): Promise<MyNotificationEventView[]> {
    await mockDelay();
    if (mockFlags().has('notification-myevent-error')) throw MOCK_ERRORS.internal();
    // 내가 볼 수 있는 이벤트만(준비 중 제외)
    return store()
        .filter((seed) => seed.featureReady && (!seed.requiredPermission || can(seed.requiredPermission)))
        .sort((a, b) => a.area.localeCompare(b.area) || a.code.localeCompare(b.code))
        .map((seed) => myViewOf(t, seed));
}

export async function mockMyNotificationEventMute(t: Translate, code: string, isMuted: boolean): Promise<MyNotificationEventView> {
    await mockDelay();
    if (mockFlags().has('notification-event-save-error')) throw MOCK_ERRORS.internal();
    const seed = store().find((item) => item.code === code && item.featureReady);
    if (!seed) throw notFound();
    if (seed.mandatory && isMuted) throw mandatoryError();
    if (isMuted) muted.add(code);
    else muted.delete(code);
    return myViewOf(t, seed);
}
