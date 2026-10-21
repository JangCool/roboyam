import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { OperatorStatusKey } from '~/utils/util-operator';

/** 운영자 화면이 함께 쓰는 계산이 낀 문구. 문구 본문은 `i18n/locales/{ko,en}/operator.ts`. */
export default class UtilOperatorMessages {
    /**
     * 상태 딱지의 글자.
     * @param {Translate} t - 번역 함수.
     * @param {OperatorStatusKey} key - 상태 키.
     * @returns {string} 딱지에 보일 글자.
     */
    static statusLabel(t: Translate, key: OperatorStatusKey): string {
        return t(`operator.tag.${key}`);
    }

    /**
     * 계정 기간 한 줄 — 둘 다 비면 "무기한", 한쪽만 있으면 빈 쪽을 「—」로 둔다.
     * @param {Translate} t - 번역 함수.
     * @param {(iso: string) => string} full - 시각을 표시 형식으로 그리는 함수(`time.value.full`).
     * @param {string | null} start - 시작 (UTC ISO).
     * @param {string | null} end - 종료 (UTC ISO).
     * @returns {string} 기간 글자.
     */
    static period(t: Translate, full: (iso: string) => string, start: string | null, end: string | null): string {
        if (!start && !end) return t('operator.unlimited');
        return `${start ? full(start) : '—'} ~ ${end ? full(end) : '—'}`;
    }

    /**
     * 역할 딱지 "+N" 의 툴팁·읽기 글자 — 줄인 역할 이름들.
     * @param {Translate} t - 번역 함수.
     * @param {string[]} names - 줄인 역할 이름들.
     * @returns {string} 예: `외 2개 역할: 배치 운영, 운영자 조회`.
     */
    static moreRoles(t: Translate, names: string[]): string {
        return t('operator.moreRoles', { count: names.length, names: names.join(', ') });
    }
}
