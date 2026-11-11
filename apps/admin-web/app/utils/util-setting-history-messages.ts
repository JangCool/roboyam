import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { SettingHistoryAction, SettingHistoryChange, SettingHistorySource } from '~/composables/common/useSettingHistory.types';

/**
 * 설정 변경 이력(소셜 로그인 · 메일 서버)의 문구 중 계산이 끼는 것 — 문구 본문은 `i18n/locales/{ko,en}/settingHistory.ts`. `t` 를 첫 인자로 받는다.
 * 값 모양은 서버가 정한다(`login-contract.md` 「변경 이력」·`mail-settings-contract.md` 「설정 이력」):
 *  · 비밀 값(`clientSecret`·`password`)은 값 없이 표시 코드만 — `SET`(입력함) · `CHANGED`(바꿈) · `CLEARED`(지움)
 *  · `clientId` 는 끝 4자리만(`****abcd`) — 그대로 보인다 · `enabled`·`showLogo`·`showSiteName`(메일 레이아웃) 은 `"true"`/`"false"` · `security` 는 `NONE`·`STARTTLS`·`SSL` · 나머지는 글자 그대로
 *  · 통계 수집 설정의 `visits`·`rawEvents`·`referrers`·`flows`·`notificationSends`·`boardActivity` 도 `"true"`/`"false"` · `rawRetentionMonths` 는 개월 수 글자(「2개월」, 3단계) · 옛 기록의 `rawRetentionDays` 는 일 수 글자(「90일」)
 *  · 값이 없으면(null — 만들기의 전 값, 지우기의 후 값) 「(없음)」
 */

/** 값 대신 표시 코드만 남는 비밀 칸 */
const SECRET_FIELDS: ReadonlySet<string> = new Set(['clientSecret', 'password']);
/** `"true"`/`"false"` 로 오는 켜고 끄는 칸 — 메일 서버의 `enabled`, 메일 레이아웃의 로고·사이트 이름 보이기, 통계 수집 설정의 항목들 */
const SWITCH_FIELDS: ReadonlySet<string> = new Set(['enabled', 'showLogo', 'showSiteName', 'visits', 'rawEvents', 'referrers', 'flows', 'notificationSends', 'boardActivity']);
const SECRET_CODE_KEYS: Record<string, string> = {
    SET: 'settingHistory.secretSet',
    CHANGED: 'settingHistory.secretChanged',
    CLEARED: 'settingHistory.secretCleared',
};

export default class UtilSettingHistoryMessages {
    /**
     * 칸 이름 — 서버가 준 i18n 키(`settingHistory.field.<field>`)로 찾고, 화면이 아직 모르는 칸이면 칸 코드 그대로.
     * @param t - 번역 함수.
     * @param te - 키가 있는지 보는 함수(`useI18n().te`).
     * @param change - 바뀐 칸.
     * @returns 칸 이름.
     */
    static fieldLabel(t: Translate, te: (key: string) => boolean, change: SettingHistoryChange): string {
        return te(change.labelKey) ? t(change.labelKey) : change.field;
    }

    /**
     * 바뀐 값 하나를 사람이 읽는 글자로.
     * @param t - 번역 함수.
     * @param field - 칸 코드.
     * @param value - 서버가 준 값.
     * @returns 보일 글자.
     */
    static value(t: Translate, field: string, value: string | null): string {
        if (value == null || value === '') return t('settingHistory.none');
        if (SECRET_FIELDS.has(field)) {
            const key = SECRET_CODE_KEYS[value];
            return key ? t(key) : value;
        }
        if (SWITCH_FIELDS.has(field)) {
            if (value === 'true') return t('common.on');
            if (value === 'false') return t('common.off');
        }
        // 원본 보관 — 3단계부터 개월(이번 달 포함). 옛 기록에 남은 일 단위 칸도 그대로 읽어 보인다
        if (field === 'rawRetentionMonths') return t('analytics.retentionMonths', { n: value });
        if (field === 'rawRetentionDays') return t('analytics.retentionDays', { n: value });
        if (field === 'security' && value === 'NONE') return t('mail.securityNone');
        if (field === 'security' && value === 'SSL') return 'SSL/TLS';
        return value;
    }

    /**
     * 출처 딱지 글자.
     * @param t - 번역 함수.
     * @param source - 출처 코드.
     * @returns 「설치」 · 「관리자」(모르는 코드는 그대로).
     */
    static source(t: Translate, source: SettingHistorySource | string): string {
        if (source === 'INSTALL') return t('settingHistory.sourceInstall');
        if (source === 'ADMIN') return t('settingHistory.sourceAdmin');
        return source;
    }

    /**
     * 작업 이름.
     * @param t - 번역 함수.
     * @param action - 작업 코드.
     * @returns 「처음 설정」 · 「변경」 · 「삭제」(모르는 코드는 그대로).
     */
    static action(t: Translate, action: SettingHistoryAction | string): string {
        if (action === 'CREATE') return t('settingHistory.actionCreate');
        if (action === 'UPDATE') return t('settingHistory.actionUpdate');
        if (action === 'DELETE') return t('settingHistory.actionDelete');
        return action;
    }
}
