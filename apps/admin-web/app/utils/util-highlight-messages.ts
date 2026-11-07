import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { DisplayTime } from '#layers/display-time/utils/util-time';
import type { HighlightItem, HighlightPlace } from '~/composables/content/api/useHighlightApi.types';
import UtilHighlight, { type HighlightCandidateProblem, type HighlightFormProblem, type HighlightImageProblem } from '~/utils/util-highlight';

/**
 * 하이라이트 화면의 계산이 낀 문구 — 칸 문제·이미지 문제·노출 기간 한 줄. 상수 문구는 화면이 `t('highlight.*')` 로 부른다.
 */
export default class UtilHighlightMessages {
    static formProblem(t: Translate, problem: HighlightFormProblem): string {
        if (problem === 'POST_REQUIRED') return t('highlight.postRequired');
        if (problem === 'TITLE_TOO_LONG') return t('highlight.tooLong', { max: UtilHighlight.TITLE_MAX });
        if (problem === 'SUMMARY_TOO_LONG') return t('highlight.tooLong', { max: UtilHighlight.SUMMARY_MAX });
        return t('highlight.periodOrder');
    }

    static imageProblem(t: Translate, problem: HighlightImageProblem): string {
        if (problem === 'EMPTY') return t('highlight.imageEmpty');
        if (problem === 'SIZE') return t('highlight.imageSize', { max: UtilHighlight.IMAGE_MAX_BYTES / (1024 * 1024) });
        return t('highlight.imageType');
    }

    static candidateProblem(t: Translate, problem: HighlightCandidateProblem | null): string {
        if (!problem) return '';
        return t('highlight.minLikesRange', { min: UtilHighlight.MIN_LIKES_MIN, max: UtilHighlight.MIN_LIKES_MAX });
    }

    /** 노출 기간 한 줄 — 「상시」 · 「10.24 09:00 부터」 · 「~ 10.31 18:00」 · 「10.24 09:00 ~ 10.31 18:00」 */
    static period(t: Translate, time: DisplayTime, item: Pick<HighlightItem, 'showFrom' | 'showUntil'>): string {
        if (!item.showFrom && !item.showUntil) return t('highlight.periodAlways');
        if (item.showFrom && !item.showUntil) return t('highlight.periodFromOnly', { from: time.shortDateTime(item.showFrom) });
        if (!item.showFrom && item.showUntil) return t('highlight.periodUntilOnly', { until: time.shortDateTime(item.showUntil) });
        return `${time.shortDateTime(item.showFrom)} ~ ${time.shortDateTime(item.showUntil)}`;
    }

    /** 후보 기간 고르개 이름 — 0 은 전체 */
    static days(t: Translate, days: number): string {
        return days === 0 ? t('highlight.daysAll') : t('highlight.daysRecent', { n: days });
    }

    /** 곳 고르개 이름 — 가젯 페이지는 「가젯 페이지 · 이름」(홈·팁 묶음은 서버 이름 그대로) */
    static placeLabel(t: Translate, place: HighlightPlace): string {
        return place.kind === 'PAGE' ? t('highlight.placePage', { name: place.name }) : place.name;
    }

    /** 곳 고르개 선택지 — 목록을 아직 못 받았으면 지금 곳 하나(키 그대로) */
    static placeOptions(t: Translate, places: readonly HighlightPlace[], current: string): { value: string; label: string }[] {
        if (!places.length) return [{ value: current, label: current }];
        return places.map((p) => ({ value: p.key, label: UtilHighlightMessages.placeLabel(t, p) }));
    }
}
