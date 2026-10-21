import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { NotificationChannelType, NotificationChannelView } from '~/composables/notification/api/useNotificationChannelApi.types';
import UtilNotificationChannel, { type ChannelBlockedReason, type ChannelFieldKey, type ChannelProblem } from '~/utils/util-notification-channel';

/** 알림 도구 화면의 계산이 낀 문구 — 상수 문구는 컴포넌트가 `t('notification.…')` 로 부른다. 키 규칙은 `i18n.md`. */
export default class UtilNotificationChannelMessages {
    /**
     * 도구 종류의 이름(서랍·이메일·Slack …).
     * @param {Translate} t - 번역 함수.
     * @param {NotificationChannelType} type - 도구 종류.
     * @returns {string} 종류 이름.
     */
    static typeLabel(t: Translate, type: NotificationChannelType): string {
        return t(`notification.channelType.${type}`);
    }

    /**
     * 도구를 화면에 보이는 이름 — 기본 제공 도구(서랍·이메일·브라우저)는 서버가 준 이름이 아니라 **지금 언어의 종류 이름**을, 사용자가 만든 도구는 이름 그대로.
     * @param {Translate} t - 번역 함수.
     * @param {Pick<NotificationChannelView, 'builtIn' | 'type' | 'name'>} channel - 도구.
     * @returns {string} 보일 이름.
     */
    static displayName(t: Translate, channel: Pick<NotificationChannelView, 'builtIn' | 'type' | 'name'>): string {
        return channel.builtIn ? UtilNotificationChannelMessages.typeLabel(t, channel.type) : channel.name;
    }

    /**
     * 입력칸 라벨.
     * @param {Translate} t - 번역 함수.
     * @param {ChannelFieldKey} key - 칸 이름.
     * @returns {string} 라벨.
     */
    static fieldLabel(t: Translate, key: ChannelFieldKey): string {
        return t(`notification.channelField.${key}`);
    }

    /**
     * 입력 검사 문제를 사람이 읽을 문구로.
     * @param {Translate} t - 번역 함수.
     * @param {ChannelFieldKey} key - 칸 이름.
     * @param {ChannelProblem} problem - 문제.
     * @returns {string} 문구.
     */
    static problem(t: Translate, key: ChannelFieldKey, problem: ChannelProblem): string {
        if (problem === 'required') return t('notification.channelProblemRequired', { field: UtilNotificationChannelMessages.fieldLabel(t, key) });
        if (problem === 'invalidUrl') return t('notification.channelProblemUrl', { field: UtilNotificationChannelMessages.fieldLabel(t, key) });
        const max = key === 'name' ? UtilNotificationChannel.NAME_MAX : key === 'description' ? UtilNotificationChannel.DESC_MAX : UtilNotificationChannel.VALUE_MAX;
        return t('notification.channelProblemTooLong', { field: UtilNotificationChannelMessages.fieldLabel(t, key), max });
    }

    /**
     * 목록에서 도구 이름 아래에 보이는 한 줄 설명 — 어디로 보내는 도구인지.
     * @param {Translate} t - 번역 함수.
     * @param {NotificationChannelView} channel - 도구.
     * @returns {string} 한 줄. 말할 것이 없으면 빈 문자열.
     */
    static summary(t: Translate, channel: NotificationChannelView): string {
        if (channel.type === 'DRAWER') return t('notification.channelSummaryDrawer');
        if (channel.type === 'EMAIL') return t('notification.channelSummaryEmail');
        if (channel.type === 'BROWSER') return t('notification.channelSummaryBrowser');
        if (channel.type === 'SLACK') return t('notification.channelSummarySlack', { channel: channel.config.channel ?? '' });
        if (channel.type === 'MATTERMOST') {
            return channel.config.mattermostMode === 'webhook' ? t('notification.channelSummaryWebhook') : t('notification.channelSummaryMattermost', { server: channel.config.serverUrl ?? '', channel: channel.config.channel ?? '' });
        }
        return '';
    }

    /**
     * 이벤트에서 도구를 고를 수 없는 이유 문구.
     * @param {Translate} t - 번역 함수.
     * @param {ChannelBlockedReason} reason - 이유.
     * @returns {string} 문구.
     */
    static blocked(t: Translate, reason: ChannelBlockedReason): string {
        return t(`notification.channelBlocked.${reason}`);
    }

    /**
     * 도구를 지울 때의 확인 문구 — 이 도구를 전달 도구로 고른 이벤트가 있으면 함께 빠진다고 알린다.
     * @param {Translate} t - 번역 함수.
     * @param {string} name - 도구 이름.
     * @param {number} eventCount - 이 도구를 고른 이벤트 수.
     * @returns {string} 문구.
     */
    static deleteConfirm(t: Translate, name: string, eventCount: number): string {
        return eventCount > 0 ? t('notification.channelDeleteConfirmUsed', { name, n: eventCount }) : t('notification.channelDeleteConfirm', { name });
    }
}
