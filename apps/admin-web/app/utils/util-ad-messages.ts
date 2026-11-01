import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { AdPlacementHistoryField, AdPlacementHistoryItem } from '~/composables/ad/api/useAdApi.types';
import type { SettingHistoryChange } from '~/composables/common/useSettingHistory.types';

/** 칸 코드 → 칸 이름 i18n 키 — 서버 `labelKey` 형식이 정해지기 전이라 화면이 칸 코드로 고른다(모르는 칸은 서버 `labelKey` 그대로) */
const FIELD_KEYS: Record<AdPlacementHistoryField, string> = {
    'postInline.enabled': 'ad.placementHistoryField.postInlineEnabled',
    'postInline.adType': 'ad.placementHistoryField.postInlineAdType',
    'postInline.position': 'ad.placementHistoryField.postInlinePosition',
    'wings.left': 'ad.placementHistoryField.wingsLeft',
    'wings.right': 'ad.placementHistoryField.wingsRight',
    'wings.screens': 'ad.placementHistoryField.wingsScreens',
    'boardList.enabled': 'ad.placementHistoryField.boardListEnabled',
    'boardList.after': 'ad.placementHistoryField.boardListAfter',
    'adult.enabled': 'ad.placementHistoryField.adultEnabled',
};

/** 켜고 끄는 칸 — 값 `"true"`/`"false"` */
const TOGGLE_FIELDS: ReadonlySet<string> = new Set(['postInline.enabled', 'wings.left', 'wings.right', 'boardList.enabled', 'adult.enabled']);

/**
 * 광고 화면의 계산이 낀 문구 — 상수 문구는 컴포넌트가 `t('ad.…')` 로 부른다. `t` 를 첫 인자로 받는다.
 */
export default class UtilAdMessages {
    /**
     * 광고 자리 설정 변경 이력을 표(`SettingHistoryTable`)가 그대로 보일 모양으로 — 칸 이름 키와 전·후 값을 사람이 읽는 글자로 바꾼 사본.
     * 값 모양은 서버가 정한다(켜기 `"true"`/`"false"` · 유형·위치 enum 이름 · 화면 목록 쉼표 이음, 빈 목록 `""` · 몇 번째 뒤 숫자 글자).
     * 모르는 값은 글자 그대로, 값이 없으면(null) 그대로 두어 표가 「(없음)」으로 보인다.
     * @param t - 번역 함수.
     * @param te - 키가 있는지 보는 함수(`useI18n().te`).
     * @param items - 서버가 준 이력 줄.
     * @returns 표에 넘길 이력 줄(원본은 고치지 않는다).
     */
    static placementHistoryItems(t: Translate, te: (key: string) => boolean, items: readonly AdPlacementHistoryItem[]): AdPlacementHistoryItem[] {
        return items.map((item) => ({ ...item, changes: item.changes.map((change) => UtilAdMessages.placementHistoryChange(t, te, change)) }));
    }

    /**
     * 바뀐 칸 하나.
     * @param t - 번역 함수.
     * @param te - 키가 있는지 보는 함수.
     * @param change - 서버가 준 바뀐 칸.
     * @returns 칸 이름 키·값 글자를 바꾼 사본.
     */
    static placementHistoryChange(t: Translate, te: (key: string) => boolean, change: SettingHistoryChange): SettingHistoryChange {
        const labelKey = FIELD_KEYS[change.field as AdPlacementHistoryField] ?? change.labelKey;
        return {
            ...change,
            labelKey,
            before: UtilAdMessages.placementHistoryValue(t, te, change.field, change.before),
            after: UtilAdMessages.placementHistoryValue(t, te, change.field, change.after),
        };
    }

    /**
     * 바뀐 값 하나를 사람이 읽는 글자로.
     * @param t - 번역 함수.
     * @param te - 키가 있는지 보는 함수.
     * @param field - 칸 코드.
     * @param value - 서버가 준 값.
     * @returns 「켜짐」·「띠 배너」·「홈, 게시판 목록」·「없음」·「5번째 글 뒤」(모르는 값은 그대로, null 은 null).
     */
    static placementHistoryValue(t: Translate, te: (key: string) => boolean, field: string, value: string | null): string | null {
        if (value == null) return null;
        if (TOGGLE_FIELDS.has(field)) {
            if (value === 'true') return t('ad.placementHistoryOn');
            if (value === 'false') return t('ad.placementHistoryOff');
            return value;
        }
        if (field === 'postInline.adType') return UtilAdMessages.option(t, te, 'ad.postInlineTypeOption', value);
        if (field === 'postInline.position') return UtilAdMessages.option(t, te, 'ad.postInlinePositionOption', value);
        if (field === 'wings.screens') {
            const screens = value
                .split(',')
                .map((screen) => screen.trim())
                .filter((screen) => screen !== '');
            if (!screens.length) return t('ad.placementHistoryNoScreens');
            return screens.map((screen) => UtilAdMessages.option(t, te, 'ad.wingScreen', screen)).join(', ');
        }
        if (field === 'boardList.after' && /^\d+$/.test(value)) return t('ad.placementHistoryAfter', { n: Number(value) });
        return value;
    }

    /** 고르기 칸의 이름(`<묶음 키>.<값>`) — 모르는 값은 그대로 */
    private static option(t: Translate, te: (key: string) => boolean, base: string, value: string): string {
        const key = `${base}.${value}`;
        return te(key) ? t(key) : value;
    }
}
