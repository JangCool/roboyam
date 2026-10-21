// MOCK: 알림 도구(채널) API(`GET·POST /api/admin/notification-channels`, `PUT·DELETE …/{channelId}`, `PUT …/{channelId}/enabled`, `POST …/{channelId}/test`)의 메모리 목업 — `useNotificationChannelApi.ts` 가 이 파일의 함수를 부른다.
// 서버가 붙으면 이 파일을 지운다. 응답 모양·오류 코드는 `useNotificationChannelApi.ts` 머리말(후보 계약)을 따른다. 비밀 값(토큰·웹훅 주소)은 이 목업도 저장하지 않고 **저장돼 있다는 표시(`secretKeys`)만** 둔다.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` (쉼표로 여러 개) ─
//   notification-channel-error       도구 목록 요청이 `500 INTERNAL` 로 실패(구획 안 오류 상태와 다시 시도)
//   notification-channel-save-error  쓰기(만들기·고치기·지우기·사용 켜기/끄기)가 `500 INTERNAL` 로 실패(폼의 오류 문구·낙관적 갱신 되돌리기)
//   notification-channel-test-error  연결 시험 요청 자체가 `500 INTERNAL` 로 실패(시험 결과가 아니라 요청 실패 — 토스트)
//   notification-channel-no-read     목록 요청이 `403 PERMISSION_DENIED`
//
// ── 흉내 ─
//   시작 도구 5개: 서랍(기본·항상 켜짐) · 이메일(기본) · 브라우저 알림(기본) · 「운영 알림」 Slack · 「개발팀 Mattermost」(봇 방식, 지난 시험 실패).
//   연결 시험은 누를 때마다 **성공 ↔ 실패를 번갈아** 낸다(처음 누르면 지난 결과의 반대). 결과 문구는 서버 몫이라 한국어 그대로다. 서랍·브라우저는 시험할 수 없다(400 `NOTIFICATION_CHANNEL_NOT_TESTABLE`).
//   서버 규칙 흉내: 이름 필수·100자·중복(409 `NOTIFICATION_CHANNEL_NAME_DUPLICATED` + `fields.name`), 종류별 필수 칸·주소 모양(400 VALIDATION + `fields.<칸>`, 저장된 비밀은 비워도 통과),
//   카카오·SMS 만들기 400 `NOTIFICATION_CHANNEL_TYPE_UNSUPPORTED`(준비 중), 기본 제공 종류 만들기·지우기 409 `NOTIFICATION_CHANNEL_BUILTIN`, 종류 바꾸기 400 `fields.type`, 서랍 끄기 409 `NOTIFICATION_CHANNEL_ALWAYS_ON`,
//   없는 도구 404 `NOTIFICATION_CHANNEL_NOT_FOUND`, 권한 없으면 403 `PERMISSION_DENIED`(읽기 `notification.read`, 쓰기·시험 `notification.manage`). 바꾼 것은 메모리에만 남아 새로고침하면 처음으로 돌아간다.

import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import { mockEventCountOfChannel, mockRemoveChannelFromDeliveries } from '~/composables/notification/api/mock/notification-event-delivery-mock';
import type {
    NotificationChannelEnabledRequest,
    NotificationChannelSaveRequest,
    NotificationChannelTestResult,
    NotificationChannelView,
    NotificationSecretKey,
} from '~/composables/notification/api/useNotificationChannelApi.types';
import UtilNotificationChannel from '~/utils/util-notification-channel';

/** 비밀 값을 뺀 도구 원본 */
type StoredChannel = Omit<NotificationChannelView, 'usedByEventCount'>;

const DAY = 86_400_000;
const ago = (days: number): string => new Date(Date.now() - days * DAY).toISOString();

let channels: StoredChannel[] | null = null;
let nextId = 100;

function store(): StoredChannel[] {
    if (channels) return channels;
    channels = [
        { channelId: 'drawer', type: 'DRAWER', name: '서랍', description: null, enabled: true, builtIn: true, config: {}, secretKeys: [], lastTest: null },
        {
            channelId: 'email',
            type: 'EMAIL',
            name: '이메일',
            description: null,
            enabled: true,
            builtIn: true,
            config: {},
            secretKeys: [],
            lastTest: { ok: true, message: '메일 서버로 시험 메일을 보냈어요.', testedAt: ago(3) },
        },
        { channelId: 'browser', type: 'BROWSER', name: '브라우저 알림', description: null, enabled: true, builtIn: true, config: {}, secretKeys: [], lastTest: null },
        {
            channelId: 'ch-slack-ops',
            type: 'SLACK',
            name: '운영 알림',
            description: '배치 실패·보안 알림을 운영 채널에 올려요.',
            enabled: true,
            builtIn: false,
            config: { channel: '#ops-alerts' },
            secretKeys: ['botToken'],
            lastTest: { ok: true, message: '#ops-alerts 에 시험 메시지를 올렸어요.', testedAt: ago(1) },
        },
        {
            channelId: 'ch-mm-dev',
            type: 'MATTERMOST',
            name: '개발팀 Mattermost',
            description: null,
            enabled: true,
            builtIn: false,
            config: { mattermostMode: 'bot', serverUrl: 'https://chat.example.com', channel: 'dev-alerts' },
            secretKeys: ['botToken'],
            lastTest: { ok: false, message: '서버에 연결하지 못했어요. 서버 주소와 봇 토큰을 확인해 주세요. (401 Unauthorized)', testedAt: ago(0.2) },
        },
    ];
    return channels;
}

const viewOf = (channel: StoredChannel): NotificationChannelView => ({ ...channel, config: { ...channel.config }, secretKeys: [...channel.secretKeys], usedByEventCount: mockEventCountOfChannel(channel.channelId) });

const notFound = (): ApiError => new ApiError({ status: 404, code: 'NOTIFICATION_CHANNEL_NOT_FOUND', message: '알림 도구를 찾을 수 없어요.' });
const denied = (): ApiError => new ApiError({ status: 403, code: 'PERMISSION_DENIED', message: '이 작업을 할 권한이 없어요.' });
const builtinError = (): ApiError => new ApiError({ status: 409, code: 'NOTIFICATION_CHANNEL_BUILTIN', message: '기본 제공 도구는 새로 만들거나 지울 수 없어요.' });

function find(channelId: string): StoredChannel {
    const channel = store().find((item) => item.channelId === channelId);
    if (!channel) throw notFound();
    return channel;
}

async function write(can: (code: string) => boolean): Promise<void> {
    await mockDelay();
    if (!can('notification.manage')) throw denied();
    if (mockFlags().has('notification-channel-save-error')) throw MOCK_ERRORS.internal();
}

/** 서버 검사 흉내 — 화면의 선검사(`UtilNotificationChannel.validate`)와 같은 규칙이되 거절 문구는 서버 몫이라 한국어 그대로다 */
function check(body: NotificationChannelSaveRequest, self?: StoredChannel): void {
    const fields: Record<string, string> = {};
    const problems = UtilNotificationChannel.validate({
        type: body.type,
        name: body.name,
        description: body.description ?? '',
        config: body.config,
        secrets: body.secrets,
        savedSecrets: self?.secretKeys ?? [],
    });
    const labels: Record<string, string> = { name: '이름', description: '설명', channel: '채널', serverUrl: '서버 주소', botToken: '봇 토큰', webhookUrl: '웹훅 주소' };
    for (const [key, problem] of Object.entries(problems)) {
        const label = labels[key] ?? key;
        fields[key] = problem === 'required' ? `${label}을(를) 입력해 주세요.` : problem === 'invalidUrl' ? `${label}은(는) http:// 또는 https:// 로 시작하는 주소여야 해요.` : `${label}이(가) 너무 길어요.`;
    }
    if (Object.keys(fields).length) throw new ApiError({ status: 400, code: 'VALIDATION', message: '입력한 내용을 확인해 주세요.', fields });
    const name = body.name.trim().toLowerCase();
    if (store().some((item) => item !== self && item.name.trim().toLowerCase() === name)) {
        throw new ApiError({ status: 409, code: 'NOTIFICATION_CHANNEL_NAME_DUPLICATED', message: '같은 이름의 알림 도구가 이미 있어요.', fields: { name: '같은 이름의 알림 도구가 이미 있어요.' } });
    }
}

/** 입력한 비밀 칸 + 이미 저장된 칸 — 종류가 쓰는 칸만 남긴다(Mattermost 방식을 바꾸면 반대 방식의 비밀은 버린다) */
function secretKeysAfter(body: NotificationChannelSaveRequest, before: readonly NotificationSecretKey[]): NotificationSecretKey[] {
    const used = UtilNotificationChannel.fieldsOf(body.type, body.config.mattermostMode).filter(UtilNotificationChannel.isSecret);
    return used.filter((key) => (body.secrets[key] ?? '').trim() !== '' || before.includes(key));
}

// ── 다른 목업(이벤트 전달)이 부르는 집계 ─

/** 도구 id → 사용 여부 — 이벤트 전달 목업이 고를 수 있는 도구인지 가를 때 쓴다 */
export function mockChannelStates(): Map<string, boolean> {
    return new Map(store().map((channel) => [channel.channelId, channel.enabled]));
}

// ── API ─

export async function mockChannelList(can: (code: string) => boolean): Promise<NotificationChannelView[]> {
    await mockDelay();
    const flags = mockFlags();
    if (flags.has('notification-channel-no-read') || !can('notification.read')) throw denied();
    if (flags.has('notification-channel-error')) throw MOCK_ERRORS.internal();
    return store().map(viewOf);
}

export async function mockChannelCreate(can: (code: string) => boolean, body: NotificationChannelSaveRequest): Promise<NotificationChannelView> {
    await write(can);
    if (UtilNotificationChannel.isComingSoon(body.type)) {
        throw new ApiError({ status: 400, code: 'NOTIFICATION_CHANNEL_TYPE_UNSUPPORTED', message: '카카오톡·SMS 도구는 아직 준비 중이라 만들 수 없어요.', fields: { type: '준비 중인 종류예요.' } });
    }
    if (!UtilNotificationChannel.isCreatable(body.type)) throw builtinError();
    check(body);
    const channel: StoredChannel = {
        channelId: `ch-${nextId++}`,
        type: body.type,
        name: body.name.trim(),
        description: body.description?.trim() || null,
        enabled: body.enabled,
        builtIn: false,
        config: { ...body.config },
        secretKeys: secretKeysAfter(body, []),
        lastTest: null,
    };
    store().push(channel);
    return viewOf(channel);
}

export async function mockChannelUpdate(can: (code: string) => boolean, channelId: string, body: NotificationChannelSaveRequest): Promise<NotificationChannelView> {
    await write(can);
    const channel = find(channelId);
    if (body.type !== channel.type) {
        throw new ApiError({ status: 400, code: 'VALIDATION', message: '입력한 내용을 확인해 주세요.', fields: { type: '도구 종류는 만든 뒤에 바꿀 수 없어요.' } });
    }
    if (channel.builtIn) {
        // 기본 제공 도구는 사용 여부만 바뀐다(이름·설정 칸이 없다)
        if (channel.type === 'DRAWER' && !body.enabled) throw new ApiError({ status: 409, code: 'NOTIFICATION_CHANNEL_ALWAYS_ON', message: '서랍은 항상 켜져 있어요.' });
        channel.enabled = body.enabled;
        return viewOf(channel);
    }
    check(body, channel);
    channel.name = body.name.trim();
    channel.description = body.description?.trim() || null;
    channel.enabled = body.enabled;
    channel.config = { ...body.config };
    channel.secretKeys = secretKeysAfter(body, channel.secretKeys);
    return viewOf(channel);
}

export async function mockChannelSetEnabled(can: (code: string) => boolean, channelId: string, body: NotificationChannelEnabledRequest): Promise<NotificationChannelView> {
    await write(can);
    const channel = find(channelId);
    if (channel.type === 'DRAWER' && !body.enabled) throw new ApiError({ status: 409, code: 'NOTIFICATION_CHANNEL_ALWAYS_ON', message: '서랍은 항상 켜져 있어요.' });
    channel.enabled = body.enabled;
    return viewOf(channel);
}

export async function mockChannelRemove(can: (code: string) => boolean, channelId: string): Promise<void> {
    await write(can);
    const channel = find(channelId);
    if (channel.builtIn) throw builtinError();
    channels = store().filter((item) => item !== channel);
    mockRemoveChannelFromDeliveries(channelId);
}

/** 연결 시험 — 누를 때마다 성공 ↔ 실패를 번갈아 낸다(처음이면 성공). 결과는 도구에 남는다 */
export async function mockChannelTest(can: (code: string) => boolean, channelId: string): Promise<NotificationChannelTestResult> {
    await mockDelay();
    if (!can('notification.manage')) throw denied();
    if (mockFlags().has('notification-channel-test-error')) throw MOCK_ERRORS.internal();
    const channel = find(channelId);
    if (channel.type === 'DRAWER' || channel.type === 'BROWSER') {
        throw new ApiError({ status: 400, code: 'NOTIFICATION_CHANNEL_NOT_TESTABLE', message: '이 도구는 연결을 시험할 수 없어요.' });
    }
    const ok = !(channel.lastTest?.ok ?? false);
    const result: NotificationChannelTestResult = {
        ok,
        message: ok ? successMessage(channel) : failureMessage(channel),
        testedAt: new Date().toISOString(),
    };
    channel.lastTest = result;
    return { ...result };
}

function successMessage(channel: StoredChannel): string {
    if (channel.type === 'EMAIL') return '메일 서버로 시험 메일을 보냈어요.';
    if (channel.type === 'SLACK') return `${channel.config.channel ?? '채널'} 에 시험 메시지를 올렸어요.`;
    return channel.config.mattermostMode === 'webhook' ? '웹훅으로 시험 메시지를 보냈어요.' : `${channel.config.channel ?? '채널'} 에 시험 메시지를 올렸어요.`;
}

function failureMessage(channel: StoredChannel): string {
    if (channel.type === 'EMAIL') return '메일 서버에 연결하지 못했어요. 설정 > 메일 서버 설정을 확인해 주세요.';
    if (channel.type === 'SLACK') return '봇 토큰이 올바르지 않거나 채널을 찾을 수 없어요. (invalid_auth)';
    return '서버에 연결하지 못했어요. 서버 주소와 봇 토큰을 확인해 주세요. (401 Unauthorized)';
}
