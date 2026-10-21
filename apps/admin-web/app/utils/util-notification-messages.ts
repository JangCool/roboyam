import type { Translate } from '#layers/i18n/utils/util-i18n';
import UtilNotification from '~/utils/util-notification';

/** 관리자 알림 화면의 계산이 낀 문구 — 상수 문구는 컴포넌트가 `t('notification.…')` 로 부른다. 키 규칙은 `i18n.md`. */
export default class UtilNotificationMessages {
    /**
     * 영역 이름 — 아는 영역은 번역 문구, 모르는 영역은 서버가 준 id 를 그대로.
     * @param {Translate} t - 번역 함수.
     * @param {string} area - 영역 id.
     * @returns {string} 보일 이름.
     */
    static areaLabel(t: Translate, area: string): string {
        return UtilNotification.isKnownArea(area) ? t(UtilNotification.areaLabelKey(area)) : area;
    }
}
