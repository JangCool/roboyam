import type { MattermostMode, NotificationChannelConfig, NotificationChannelSecrets, NotificationChannelType, NotificationChannelView, NotificationSecretKey } from '~/composables/notification/api/useNotificationChannelApi.types';
import type { NotificationEventDelivery, NotificationEventView } from '~/composables/notification/api/useNotificationEventApi.types';

/** 도구 입력칸 이름 — 문구는 `notification.channelField.<이름>` */
export type ChannelFieldKey = 'name' | 'description' | 'channel' | 'serverUrl' | 'botToken' | 'webhookUrl';

/** 입력 검사 문제 — `required` 비었다 · `tooLong` 너무 길다 · `invalidUrl` 주소 모양이 아니다 */
export type ChannelProblem = 'required' | 'tooLong' | 'invalidUrl';

/** 이벤트에서 이 도구를 고를 수 없는 이유 — 문구는 `notification.channelBlocked.<이유>` */
export type ChannelBlockedReason = 'notReady' | 'emailNotAvailable' | 'selfOnly' | 'disabled';

/** 도구 입력 한 벌 — 다이얼로그가 검사에 넘기는 값 */
export interface ChannelInput {
    type: NotificationChannelType;
    name: string;
    description: string;
    config: NotificationChannelConfig;
    secrets: NotificationChannelSecrets;
    /** 서버에 이미 저장된 비밀 칸 — 고칠 때 비워 두면 그대로 쓴다 */
    savedSecrets: readonly NotificationSecretKey[];
}

/**
 * 알림 도구(채널) 화면이 함께 쓰는 순수 규칙 — 종류별 아이콘·색·만들기 가능 여부, 종류별 입력칸과 화면 선검사, 이벤트에서 도구를 고를 수 있는지.
 * 서버 규칙 이름: 종류별 필수 칸은 hamonica3 서버 `ChannelSpec.requiredKeys`(Slack=botToken+channel, Mattermost=웹훅 주소 또는 서버 주소+봇 토큰+채널, 이메일·브라우저=없음)를 따른 후보 규칙이다.
 * 서버가 같은 규칙으로 다시 본다(감추는 것은 편의이고 막는 것은 서버다). 비밀 칸은 `ChannelSecrets` 가 정한다(봇 토큰·웹훅 주소).
 */
export default class UtilNotificationChannel {
    /** 도구 이름 최대 글자 수 */
    static readonly NAME_MAX = 100;
    /** 설명 최대 글자 수 */
    static readonly DESC_MAX = 300;
    /** 채널·주소 칸 최대 글자 수 */
    static readonly VALUE_MAX = 300;

    /** 기본 제공 이메일 도구의 id — 서버가 이벤트의 `emailEnabled` 로 갖고 있어서(후보 계약에서는 `channels` 에 합쳐진다) 이벤트 전달 선택에서 따로 다룬다 */
    static readonly EMAIL_ID = 'email';

    /** 종류 선택지의 차례 — 서랍은 선택지가 아니다(기본 제공) */
    static readonly TYPE_ORDER: readonly NotificationChannelType[] = ['SLACK', 'MATTERMOST', 'EMAIL', 'BROWSER', 'KAKAO', 'SMS'];

    /** 종류별 아이콘(primeicons) — 이름이 같은 로고는 없어서 뜻이 가까운 아이콘으로 고른다 */
    private static readonly ICONS: Readonly<Record<NotificationChannelType, string>> = {
        DRAWER: 'pi pi-bell',
        EMAIL: 'pi pi-envelope',
        BROWSER: 'pi pi-desktop',
        SLACK: 'pi pi-slack',
        MATTERMOST: 'pi pi-comments',
        KAKAO: 'pi pi-comment',
        SMS: 'pi pi-mobile',
    };

    /**
     * 종류의 아이콘.
     * @param {NotificationChannelType} type - 도구 종류.
     * @returns {string} primeicons 클래스.
     */
    static icon(type: NotificationChannelType): string {
        return UtilNotificationChannel.ICONS[type];
    }

    /**
     * 종류 딱지(PrimeVue `Tag`)의 색 — 서랍은 안내색, 기본 제공은 회색, 사용자가 만드는 도구는 강조색으로 갈라 한눈에 보인다.
     * @param {NotificationChannelType} type - 도구 종류.
     * @returns {'info' | 'secondary' | 'contrast'} Tag 의 severity.
     */
    static tagSeverity(type: NotificationChannelType): 'info' | 'secondary' | 'contrast' {
        if (type === 'DRAWER') return 'info';
        return UtilNotificationChannel.isCreatable(type) ? 'contrast' : 'secondary';
    }

    /**
     * 사용자가 새로 만들 수 있는 종류인가 — Slack · Mattermost 뿐이다(서랍·이메일·브라우저는 기본 제공, 카카오·SMS 는 업체 계약 전).
     * @param {NotificationChannelType} type - 도구 종류.
     * @returns {boolean} 만들 수 있으면 true.
     */
    static isCreatable(type: NotificationChannelType): boolean {
        return type === 'SLACK' || type === 'MATTERMOST';
    }

    /**
     * 종류 선택지에서 「준비 중」으로 흐리게 보이는 종류인가 — 카카오 · SMS.
     * @param {NotificationChannelType} type - 도구 종류.
     * @returns {boolean} 준비 중이면 true.
     */
    static isComingSoon(type: NotificationChannelType): boolean {
        return type === 'KAKAO' || type === 'SMS';
    }

    /**
     * 연결을 시험할 수 있는 종류인가 — 실제로 어딘가에 연결하는 것(이메일 서버 · Slack · Mattermost)만. 서랍·브라우저 알림은 연결할 곳이 없다.
     * @param {NotificationChannelType} type - 도구 종류.
     * @returns {boolean} 시험할 수 있으면 true.
     */
    static isTestable(type: NotificationChannelType): boolean {
        return type === 'EMAIL' || type === 'SLACK' || type === 'MATTERMOST';
    }

    /**
     * 종류가 도구에서 쓰는 입력칸(이름·설명 제외) — 화면이 칸을 그리는 차례다.
     * @param {NotificationChannelType} type - 도구 종류.
     * @param {MattermostMode | undefined} mode - Mattermost 연결 방식.
     * @returns {ChannelFieldKey[]} 칸 이름들. 이메일·브라우저·서랍은 칸이 없다.
     */
    static fieldsOf(type: NotificationChannelType, mode: MattermostMode | undefined): ChannelFieldKey[] {
        if (type === 'SLACK') return ['botToken', 'channel'];
        if (type === 'MATTERMOST') return mode === 'webhook' ? ['webhookUrl'] : ['serverUrl', 'botToken', 'channel'];
        return [];
    }

    /**
     * 비밀 칸인가 — 화면에 값을 되돌려 보이지 않고 「입력됨」만 보인다.
     * @param {ChannelFieldKey} key - 칸 이름.
     * @returns {boolean} 비밀이면 true.
     */
    static isSecret(key: ChannelFieldKey): key is NotificationSecretKey {
        return key === 'botToken' || key === 'webhookUrl';
    }

    /**
     * 주소 칸인가 — `http(s)://` 로 시작하는 주소여야 한다.
     * @param {ChannelFieldKey} key - 칸 이름.
     * @returns {boolean} 주소 칸이면 true.
     */
    static isUrl(key: ChannelFieldKey): boolean {
        return key === 'serverUrl' || key === 'webhookUrl';
    }

    /**
     * 주소 모양을 검사한다(`http://` 또는 `https://` + 호스트).
     * @param {string} value - 입력한 주소.
     * @returns {boolean} 주소면 true.
     */
    static isHttpUrl(value: string): boolean {
        try {
            const url = new URL(value.trim());
            return (url.protocol === 'http:' || url.protocol === 'https:') && url.hostname.length > 0;
        } catch {
            return false;
        }
    }

    /**
     * 입력 한 벌을 화면에서 먼저 검사한다 — 서버가 같은 규칙으로 다시 본다.
     * 저장돼 있는 비밀 칸은 비워 둬도 된다(그대로 쓴다는 뜻).
     * @param {ChannelInput} input - 입력 한 벌.
     * @returns {Partial<Record<ChannelFieldKey, ChannelProblem>>} 칸마다의 문제. 비었으면 통과.
     */
    static validate(input: ChannelInput): Partial<Record<ChannelFieldKey, ChannelProblem>> {
        const out: Partial<Record<ChannelFieldKey, ChannelProblem>> = {};
        const name = input.name.trim();
        if (!name) out.name = 'required';
        else if (name.length > UtilNotificationChannel.NAME_MAX) out.name = 'tooLong';
        if (input.description.trim().length > UtilNotificationChannel.DESC_MAX) out.description = 'tooLong';

        for (const key of UtilNotificationChannel.fieldsOf(input.type, input.config.mattermostMode)) {
            const value = (UtilNotificationChannel.isSecret(key) ? input.secrets[key] : input.config[key as 'channel' | 'serverUrl']) ?? '';
            const text = value.trim();
            if (!text) {
                // 저장된 비밀은 비워 두면 그대로 쓴다
                if (UtilNotificationChannel.isSecret(key) && input.savedSecrets.includes(key)) continue;
                out[key] = 'required';
            } else if (text.length > UtilNotificationChannel.VALUE_MAX) out[key] = 'tooLong';
            else if (UtilNotificationChannel.isUrl(key) && !UtilNotificationChannel.isHttpUrl(text)) out[key] = 'invalidUrl';
        }
        return out;
    }

    /**
     * 이 이벤트에서 이 도구를 **새로 고를 수 없는 이유** — 이미 골라 둔 것은 해제할 수 있어야 하므로 부르는 쪽이 「고른 것」을 따로 본다.
     * 이유가 여럿이면 이벤트 쪽 이유(준비 중 · 이메일 불가 · 본인 전용)가 도구 쪽 이유(꺼짐)보다 먼저다.
     * @param {NotificationChannelView} channel - 도구.
     * @param {NotificationEventView} event - 이벤트.
     * @param {NotificationEventDelivery | undefined} delivery - 이벤트의 전달 설정(없으면 팀·공유 채널 허용으로 본다).
     * @returns {ChannelBlockedReason | null} 고를 수 있으면 null.
     */
    static blockedReason(channel: NotificationChannelView, event: NotificationEventView, delivery: NotificationEventDelivery | undefined): ChannelBlockedReason | null {
        if (!event.featureReady) return 'notReady';
        if (channel.type === 'EMAIL' && !event.emailable) return 'emailNotAvailable';
        if ((channel.type === 'SLACK' || channel.type === 'MATTERMOST') && delivery?.broadcastAllowed === false) return 'selfOnly';
        if (!channel.enabled) return 'disabled';
        return null;
    }

    /**
     * 이벤트에서 고를 수 있는 도구들 — 서랍(항상 켜짐, 고르는 것이 아니다)과 종류가 `KAKAO`·`SMS` 인 것은 뺀다.
     * @param {readonly NotificationChannelView[]} channels - 모든 도구.
     * @returns {NotificationChannelView[]} 선택지(받은 차례, 기본 제공 → 사용자 도구).
     */
    static selectable(channels: readonly NotificationChannelView[]): NotificationChannelView[] {
        return channels.filter((channel) => channel.type !== 'DRAWER' && !UtilNotificationChannel.isComingSoon(channel.type));
    }
}
