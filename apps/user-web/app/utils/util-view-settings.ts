import type { CommentOrderSetting, TextScaleSetting, ThemeSetting, ViewSettings } from '~/composables/view/api/useViewSettingsApi.types';

/**
 * 보기 설정의 순수 규칙 — 기본값·고를 수 있는 값·서버 응답 읽기·합치기·되돌리기·야간 모드 풀기·글자 크기 단계(community `UtilSettings`·`UtilDisplay` 중 쓰는 것).
 * 값 목록은 서버 후보 `ViewSettingsPolicy` 와 같다. 상태도 요청도 없다.
 */
export default class UtilViewSettings {
    /** 이 브라우저에 저장해 두는 열쇠 — 서버 값이 늦게 와도 첫 화면(야간 모드 깜빡임)을 막는다 */
    static readonly STORAGE_KEY = 'roboyam:view-settings';

    static readonly THEMES: readonly ThemeSetting[] = ['AUTO', 'LIGHT', 'DARK'];
    static readonly TEXT_SCALES: readonly TextScaleSetting[] = [90, 100, 110, 125];
    static readonly COMMENT_ORDERS: readonly CommentOrderSetting[] = ['OLDEST', 'LATEST'];

    static readonly DEFAULTS: Readonly<ViewSettings> = Object.freeze({ theme: 'AUTO', textScale: 100, commentOrder: 'OLDEST' });

    /** 글자 배율을 담는 CSS 변수 */
    static readonly TEXT_SCALE_VAR = '--text-scale';

    /** 기본값 사본 */
    static defaults(): ViewSettings {
        return { ...UtilViewSettings.DEFAULTS };
    }

    /**
     * 서버 응답·저장해 둔 값을 읽는다 — 모르는 값은 기본값.
     * @param {unknown} raw - 값.
     * @returns {ViewSettings} 설정.
     */
    static sanitize(raw: unknown): ViewSettings {
        const root = typeof raw === 'object' && raw !== null && !Array.isArray(raw) ? (raw as Record<string, unknown>) : {};
        const d = UtilViewSettings.DEFAULTS;
        return {
            theme: UtilViewSettings.THEMES.find((v) => v === root.theme) ?? d.theme,
            textScale: UtilViewSettings.TEXT_SCALES.find((v) => v === root.textScale) ?? d.textScale,
            commentOrder: UtilViewSettings.COMMENT_ORDERS.find((v) => v === root.commentOrder) ?? d.commentOrder,
        };
    }

    /**
     * 저장해 둔 글(JSON)을 읽는다 — 없거나 깨졌으면 null.
     * @param {string | null} text - 저장된 글.
     * @returns {ViewSettings | null} 설정.
     */
    static parseStored(text: string | null): ViewSettings | null {
        if (!text) return null;
        try {
            return UtilViewSettings.sanitize(JSON.parse(text));
        } catch {
            return null;
        }
    }

    /**
     * 바꿀 칸만 덮는다.
     * @param {ViewSettings} base - 지금 값.
     * @param {Partial<ViewSettings>} partial - 바꿀 칸.
     * @returns {ViewSettings} 새 값.
     */
    static merge(base: ViewSettings, partial: Partial<ViewSettings>): ViewSettings {
        return UtilViewSettings.sanitize({ ...base, ...partial });
    }

    /** 세 칸이 모두 같은가 */
    static isSame(a: ViewSettings, b: ViewSettings): boolean {
        return a.theme === b.theme && a.textScale === b.textScale && a.commentOrder === b.commentOrder;
    }

    /**
     * 저장 실패 뒤 — 이번에 바꾼 칸(`target` 이 `confirmed` 와 다른 칸)만 서버 확정 값으로 되돌린다. 그 사이 사용자가 또 바꾼 칸은 둔다.
     * @param {ViewSettings} current - 지금 화면 값.
     * @param {ViewSettings} target - 보내려던 값.
     * @param {ViewSettings} confirmed - 서버가 마지막으로 확정한 값.
     * @returns {ViewSettings} 되돌린 값.
     */
    static revert(current: ViewSettings, target: ViewSettings, confirmed: ViewSettings): ViewSettings {
        const out = { ...current };
        for (const key of ['theme', 'textScale', 'commentOrder'] as const) {
            if (current[key] === target[key] && target[key] !== confirmed[key]) (out as Record<string, unknown>)[key] = confirmed[key];
        }
        return UtilViewSettings.sanitize(out);
    }

    /**
     * 야간 모드를 라이트·다크로 푼다 — `AUTO` 면 기기 설정.
     * @param {ThemeSetting} theme - 설정.
     * @param {boolean} systemDark - 기기가 다크인가.
     * @returns {'light' | 'dark'} `<html data-color-scheme>` 값.
     */
    static resolveScheme(theme: ThemeSetting, systemDark: boolean): 'light' | 'dark' {
        if (theme === 'DARK') return 'dark';
        if (theme === 'LIGHT') return 'light';
        return systemDark ? 'dark' : 'light';
    }

    /**
     * 글자 크기를 한 단 옮긴다 — 끝에서는 그대로.
     * @param {number} current - 지금 백분율.
     * @param {number} by - −1·+1.
     * @returns {TextScaleSetting} 새 값.
     */
    static stepTextScale(current: number, by: number): TextScaleSetting {
        const steps = UtilViewSettings.TEXT_SCALES as readonly number[];
        const at = steps.indexOf(current);
        const from = at < 0 ? steps.indexOf(UtilViewSettings.DEFAULTS.textScale) : at;
        return UtilViewSettings.TEXT_SCALES[Math.min(steps.length - 1, Math.max(0, from + by))]!;
    }

    /** 이 방향으로 더 옮길 수 있는가 */
    static canStepTextScale(current: number, by: number): boolean {
        return UtilViewSettings.stepTextScale(current, by) !== UtilViewSettings.stepTextScale(current, 0);
    }
}
