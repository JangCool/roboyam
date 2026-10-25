// MOCK: 내 알림 설정 API(`GET`·`PUT /api/me/notification-settings`)의 메모리 목업 — `useNotificationSettingsApi.ts` 가 부른다. 서버가 붙으면 이 파일을 지운다.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` (쉼표로 여러 개) ─
//   notify-settings-fail       읽기 실패(500) → 화면 오류 상태
//   notify-settings-save-fail  저장 실패(500) → 저장 줄 안 오류 한 줄
//   notify-settings-no-email   이메일 인증 전 계정 → 이메일 스위치 잠금 + 까닭
//
// ── 목업 규칙(가정 — 계약 「목업 가정」) ─
//   · 이벤트 6종(준비 중 제외)은 서버 카탈로그 차례. 이름·설명은 서버가 요청 언어로 준다 — 목업은 한국어로만.
//   · 운영자 설정 가정: 6종 모두 앱 알림 켬, 이메일은 「내 글에 댓글」·「포인트 회수」만 켬.
//   · 잠긴 이벤트(포인트 회수·글/댓글 숨김·기능 제한)의 `inApp`·`email` 은 늘 true. 잠긴 이벤트를 끄려 하면 `409 NOTIFICATION_SETTING_LOCKED`.
//   · 요청에 모르는 코드가 있으면 `400 VALIDATION fields.events[i].code`.
//   · 운영자가 보내지 않는 채널의 값은 저장하되 응답에서는 true(무의미)로 둔다.

import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { MyNotificationSetting, MyNotificationSettings, MyNotificationSettingsRequest } from '~/composables/notification/api/useNotificationSettingsApi.types';

const CATALOG: MyNotificationSetting[] = [
    {
        code: 'COMMENT',
        area: 'content',
        name: '내 글에 댓글',
        description: '내가 쓴 글에 새 댓글이 달리면 알려요. 짧은 시간에 여러 개가 달리면 한 번에 묶어 알려요.',
        inAppOffered: true,
        emailOffered: true,
        memberCanTurnOff: true,
        lockReason: null,
        inApp: true,
        email: false,
    },
    {
        code: 'REPLY',
        area: 'content',
        name: '내 댓글에 답글',
        description: '내가 쓴 댓글에 답글이 달리면 알려요.',
        inAppOffered: true,
        emailOffered: false,
        memberCanTurnOff: true,
        lockReason: null,
        inApp: true,
        email: true,
    },
    {
        code: 'POINTS_REVOKED',
        area: 'member',
        name: '포인트 회수',
        description: '운영 조정으로 포인트가 차감되면 알려요.',
        inAppOffered: true,
        emailOffered: true,
        memberCanTurnOff: false,
        lockReason: 'ACCOUNT',
        inApp: true,
        email: true,
    },
    {
        code: 'POST_HIDDEN',
        area: 'safety',
        name: '내 글 숨김',
        description: '신고가 쌓여 내 글이 숨겨지면 알려요.',
        inAppOffered: true,
        emailOffered: false,
        memberCanTurnOff: false,
        lockReason: 'ACCOUNT',
        inApp: true,
        email: true,
    },
    {
        code: 'COMMENT_HIDDEN',
        area: 'safety',
        name: '내 댓글 숨김',
        description: '신고가 쌓여 내 댓글이 숨겨지면 알려요.',
        inAppOffered: true,
        emailOffered: false,
        memberCanTurnOff: false,
        lockReason: 'ACCOUNT',
        inApp: true,
        email: true,
    },
    {
        // 커머스 0-4 — 서버 `MemberEvent.FEATURE_RESTRICTED`(SAFETY, 계정 알림이라 잠김)
        code: 'FEATURE_RESTRICTED',
        area: 'safety',
        name: '기능 제한',
        description: '포인트 동결·공동구매 참여 제한·충전 제한이 걸리면 어떤 기능이 언제까지 막히는지와 사유를 알려요. 계정 알림이라 회원이 끌 수 없어요.',
        inAppOffered: true,
        emailOffered: false,
        memberCanTurnOff: false,
        lockReason: 'ACCOUNT',
        inApp: true,
        email: true,
    },
];

let store: MyNotificationSetting[] | null = null;

function view(): MyNotificationSettings {
    store ??= mockClone(CATALOG);
    const noEmail = mockFlags().has('notify-settings-no-email');
    return {
        events: mockClone(store).map((row) => ({
            ...row,
            inApp: row.memberCanTurnOff && row.inAppOffered ? row.inApp : true,
            email: row.memberCanTurnOff && row.emailOffered ? row.email : true,
        })),
        emailReceivable: !noEmail,
        emailBlockedReason: noEmail ? 'NOT_VERIFIED' : null,
    };
}

export async function mockMyNotificationSettings(): Promise<MyNotificationSettings> {
    await mockDelay();
    if (mockFlags().has('notify-settings-fail')) throw MOCK_ERRORS.internal();
    return view();
}

export async function mockSaveMyNotificationSettings(body: MyNotificationSettingsRequest): Promise<MyNotificationSettings> {
    await mockDelay();
    if (mockFlags().has('notify-settings-save-fail')) throw MOCK_ERRORS.internal();
    store ??= mockClone(CATALOG);

    const fields: Record<string, string> = {};
    body.events.forEach((input, index) => {
        if (!store!.some((row) => row.code === input.code)) fields[`events[${index}].code`] = '알 수 없는 알림이에요.';
    });
    if (Object.keys(fields).length > 0) throw MOCK_ERRORS.validation(fields);

    for (const input of body.events) {
        const row = store.find((r) => r.code === input.code)!;
        if (!row.memberCanTurnOff && (!input.inApp || !input.email)) {
            throw new ApiError({ status: 409, code: 'NOTIFICATION_SETTING_LOCKED', message: `「${row.name}」 알림은 끌 수 없어요.` });
        }
    }
    for (const input of body.events) {
        const row = store.find((r) => r.code === input.code)!;
        row.inApp = input.inApp;
        row.email = input.email;
    }
    return view();
}
