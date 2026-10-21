import type { Translate } from '#layers/i18n/utils/util-i18n';

/**
 * 우측 구역 내비 항목 옆 표시의 글자 — 점·숫자만 보이고 글자는 스크린리더 이름·툴팁·선택 상자 항목에만 쓴다(색만으로 말하지 않는다).
 * 번역 함수 `t` 를 첫 인자로 받는다(`.claude/guides/frontend/i18n.md`). 문구 본문은 `i18n/locales/{ko,en}/section.ts`.
 */
export default class UtilSectionNavMessages {
    /**
     * 바뀐 구획의 이름 — 칸 수가 둘 이상이면 수를 붙인다.
     * @param {Translate} t - 번역 함수.
     * @param {number} count - 바뀐 칸 수.
     * @returns {string} "바뀜" 또는 "바뀜 N칸".
     */
    static changed(t: Translate, count: number): string {
        return count >= 2 ? t('section.changedCount', { count }) : t('section.changed');
    }

    /**
     * 입력 오류가 있는 구획의 이름.
     * @param {Translate} t - 번역 함수.
     * @param {number} count - 오류 칸 수.
     * @returns {string} "오류 N건".
     */
    static invalid(t: Translate, count: number): string {
        return t('section.invalidCount', { count });
    }

    /**
     * 항목에 붙는 상태 글 — 바뀜·오류가 없으면 빈 글자.
     * @param {Translate} t - 번역 함수.
     * @param {number} changes - 바뀐 칸 수.
     * @param {number} invalid - 오류 칸 수.
     * @returns {string} "오류 2건 · 바뀜 3칸" 꼴(오류가 먼저).
     */
    static statusText(t: Translate, changes: number, invalid: number): string {
        const parts: string[] = [];
        if (invalid > 0) parts.push(UtilSectionNavMessages.invalid(t, invalid));
        if (changes > 0) parts.push(UtilSectionNavMessages.changed(t, changes));
        return parts.join(' · ');
    }

    /**
     * 선택 상자 항목 글 — 구획 이름 뒤에 상태를 덧붙인다(선택 상자는 점·숫자 배지를 그릴 수 없다).
     * @param {Translate} t - 번역 함수.
     * @param {string} label - 구획 이름.
     * @param {number} changes - 바뀐 칸 수.
     * @param {number} invalid - 오류 칸 수.
     * @returns {string} 상태가 없으면 이름 그대로, 있으면 "이름 · 오류 2건 · 바뀜 3칸".
     */
    static optionText(t: Translate, label: string, changes: number, invalid: number): string {
        const status = UtilSectionNavMessages.statusText(t, changes, invalid);
        return status === '' ? label : `${label} · ${status}`;
    }
}
