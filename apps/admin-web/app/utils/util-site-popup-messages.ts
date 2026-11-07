import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { DisplayTime } from '#layers/display-time/utils/util-time';
import type { SitePopupItem } from '~/composables/popup/api/useSitePopupApi.types';
import UtilSitePopup, { type SitePopupFormField, type SitePopupFormProblem, type SitePopupImageProblem } from '~/utils/util-site-popup';

/** 팝업 관리 화면의 계산이 낀 문구 — 상수 문구는 컴포넌트가 `t('popup.…')` 를 부른다 */
export default class UtilSitePopupMessages {
    /** 칸 문제 한 줄 — 글자 수 초과는 그 칸의 최대 글자 수를 넣는다 */
    static formProblem(t: Translate, field: SitePopupFormField, problem: SitePopupFormProblem): string {
        if (problem === 'TOO_LONG') {
            const max = { name: UtilSitePopup.NAME_MAX, imageAlt: UtilSitePopup.IMAGE_ALT_MAX, heading: UtilSitePopup.HEADING_MAX, body: UtilSitePopup.BODY_MAX, buttonLabel: UtilSitePopup.BUTTON_MAX }[field as 'name'] ?? 0;
            return t('popup.problem.TOO_LONG', { max });
        }
        if (problem === 'REQUIRED') return field === 'name' ? t('popup.problem.NAME_REQUIRED') : t('popup.problem.BODY_REQUIRED');
        return t(`popup.problem.${problem}`);
    }

    static imageProblem(t: Translate, problem: SitePopupImageProblem): string {
        return problem === 'SIZE' ? t('popup.imageTooLarge', { max: UtilSitePopup.IMAGE_MAX_BYTES / (1024 * 1024) }) : t(`popup.image${problem === 'EMPTY' ? 'Empty' : 'Type'}`);
    }

    /** 노출 기간 한 줄 — 「상시」 · 「11.07 09:00 부터」 · 「~ 11.30 18:00」 · 「11.07 09:00 ~ 11.30 18:00」 */
    static period(t: Translate, time: DisplayTime, item: Pick<SitePopupItem, 'showFrom' | 'showUntil'>): string {
        if (!item.showFrom && !item.showUntil) return t('popup.periodAlways');
        if (item.showFrom && !item.showUntil) return t('popup.periodFromOnly', { from: time.shortDateTime(item.showFrom) });
        if (!item.showFrom && item.showUntil) return t('popup.periodUntilOnly', { until: time.shortDateTime(item.showUntil) });
        return `${time.shortDateTime(item.showFrom)} ~ ${time.shortDateTime(item.showUntil)}`;
    }

    /** 기기 한 줄 — 「PC · 모바일」 */
    static devices(t: Translate, item: Pick<SitePopupItem, 'devices'>): string {
        return item.devices.map((device) => t(`popup.device.${device}`)).join(' · ');
    }
}
