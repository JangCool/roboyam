import type { Translate } from '#layers/i18n/utils/util-i18n';

/**
 * 알림 표시 중 셸(헤더의 종·하단 탭의 알림·쪽지 아이콘)의 안 읽은 수 배지가 쓰는 것만 남긴 단순 버전.
 * 원본(community)의 `UtilNotification` 은 알림 종류별 문구·이동 주소를 담은 1,100줄짜리 파일이다 — 알림 화면을 만들 때 원본으로 바꾼다.
 */
export default class UtilNotification {
    /** 알림 점·숫자에 그대로 보이는 가장 큰 수 — 넘으면 "99+" */
    static readonly BADGE_MAX = 99;

    /** 안 읽은 알림 수를 다시 묻는 간격(탭이 보이는 동안) */
    static readonly POLL_INTERVAL_MS = 60_000;

    /**
     * 서버가 준 안 읽은 수를 0 이상의 정수로 다듬습니다. 숫자가 아니면 0 입니다.
     * @param {unknown} value - 응답의 `count`·`unreadCount`.
     * @returns {number} 0 이상의 정수.
     */
    static clampCount(value: unknown): number {
        return typeof value === 'number' && Number.isFinite(value) ? Math.max(0, Math.floor(value)) : 0;
    }

    /**
     * 안 읽은 알림 수를 점·숫자에 보일 글자로 바꿉니다.
     * @param {number} count - 안 읽은 알림 수(서버 상한 100).
     * @returns {string} 0 이면 빈 문자열, 99 를 넘으면 "99+", 아니면 숫자.
     */
    static badgeText(count: number): string {
        const n = UtilNotification.clampCount(count);
        if (n === 0) return '';
        return n > UtilNotification.BADGE_MAX ? `${UtilNotification.BADGE_MAX}+` : String(n);
    }

    /**
     * 알림 링크·버튼의 이름(스크린리더용). 점·숫자는 시각 요소(`aria-hidden`)라서 이름에 숫자를 넣습니다.
     * @param {Translate} t - 번역 함수(`useTranslate()` / `useI18n().t`).
     * @param {number} count - 안 읽은 알림 수.
     * @returns {string} "알림" · "알림, 안 읽은 알림 3개" · "알림, 안 읽은 알림 99개 이상".
     */
    static badgeLabel(t: Translate, count: number): string {
        const n = UtilNotification.clampCount(count);
        if (n === 0) return t('notification.title');
        return n > UtilNotification.BADGE_MAX ? t('notification.badgeUnreadOver', { max: UtilNotification.BADGE_MAX }) : t('notification.badgeUnread', { count: n });
    }

    /**
     * 쪽지함 링크의 이름(스크린리더용). 점·숫자는 시각 요소라서 이름에 안 읽은 대화 수를 넣습니다.
     * @param {Translate} t - 번역 함수(`useTranslate()` / `useI18n().t`).
     * @param {number} count - 안 읽은 대화 수.
     * @returns {string} "쪽지함" · "쪽지함, 안 읽은 대화 3개" · "쪽지함, 안 읽은 대화 99개 이상".
     */
    static dmBadgeLabel(t: Translate, count: number): string {
        const n = UtilNotification.clampCount(count);
        if (n === 0) return t('notification.dmTitle');
        return n > UtilNotification.BADGE_MAX ? t('notification.dmBadgeUnreadOver', { max: UtilNotification.BADGE_MAX }) : t('notification.dmBadgeUnread', { count: n });
    }

    /**
     * 알림 하나를 읽었을 때의 안 읽은 수(낙관적). 이미 읽은 알림이었으면 그대로입니다.
     * @param {number} count - 지금 보이는 안 읽은 수.
     * @param {boolean} wasUnread - 누른 알림이 안 읽은 상태였는가.
     * @returns {number} 줄어든 수(0 아래로 내려가지 않는다).
     */
    static afterRead(count: number, wasUnread: boolean): number {
        const n = UtilNotification.clampCount(count);
        return wasUnread ? Math.max(0, n - 1) : n;
    }

    /**
     * "모두 읽음"을 눌렀을 때의 안 읽은 수(낙관적). 화면에 불러온 안 읽은 만큼만 줄이고 나머지는 서버 값으로 맞춥니다.
     * @param {number} count - 지금 보이는 안 읽은 수.
     * @param {number} unreadShown - 화면에 불러온 알림 중 안 읽은 개수.
     * @returns {number} 줄어든 수(0 아래로 내려가지 않는다).
     */
    static afterReadAll(count: number, unreadShown: number): number {
        return Math.max(0, UtilNotification.clampCount(count) - UtilNotification.clampCount(unreadShown));
    }
}
