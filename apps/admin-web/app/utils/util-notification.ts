import type { NotificationKind } from '~/composables/shell/api/useNotificationApi.types';

/** PrimeVue `Tag` 의 severity */
export type NotificationTagSeverity = 'info' | 'success' | 'warn' | 'danger';

/**
 * 관리자 알림 화면(종 알림 서랍 · 이벤트 관리 · 내 정보의 이벤트별 알림)이 함께 쓰는 순수 규칙 — 심각도 아이콘·색, 영역 차례·아이콘, 이벤트 아이콘, 이동 경로 읽기.
 * 문구는 i18n 키(`notification.severity.*` · `notification.area.*`)이고 여기에는 키를 고르는 규칙만 둔다.
 * 서버 쪽 이름: 영역 id 는 `NotificationArea`(`security` · `system` · `member` · `content` · `safety` · `club` · `hotdeal` · `groupbuy` · `bizAd` — 권한 영역(`PermissionArea`)에서 「종합」을 빼고 `security`(보안)를 더한 것)이다. 이벤트 코드는 `AdminNotificationEvent` 의 `code`.
 */
export default class UtilNotification {
    /** 영역을 보이는 차례 — 모르는 영역은 이 뒤에 서버가 준 차례로 붙는다 */
    static readonly AREA_ORDER: readonly string[] = ['security', 'system', 'member', 'content', 'safety', 'club', 'hotdeal', 'groupbuy', 'bizAd'];

    private static readonly AREA_ICONS: Readonly<Record<string, string>> = {
        security: 'pi pi-shield',
        system: 'pi pi-cog',
        member: 'pi pi-users',
        content: 'pi pi-file',
        safety: 'pi pi-flag',
        club: 'pi pi-comments',
        hotdeal: 'pi pi-tag',
        groupbuy: 'pi pi-shopping-cart',
        bizAd: 'pi pi-megaphone',
    };

    /** 영역 아이콘보다 구체적인 이벤트 아이콘 */
    private static readonly EVENT_ICONS: Readonly<Record<string, string>> = {
        BATCH_RUN_FAILED: 'pi pi-clock',
        OPERATOR_INVITATION_ACCEPTED: 'pi pi-user-plus',
        OPERATOR_ACCOUNT_CREATED: 'pi pi-user-plus',
        OPERATOR_STATUS_CHANGED_ME: 'pi pi-user-edit',
        OPERATOR_LOCKED_BY_FAILURES: 'pi pi-lock',
        ROLES_CHANGED_ME: 'pi pi-id-card',
        PASSWORD_CHANGED_ME: 'pi pi-key',
        PASSWORD_RESET_DONE_ME: 'pi pi-key',
        SITE_SETTINGS_CHANGED: 'pi pi-sliders-h',
        GRADE_SETTINGS_SAVED: 'pi pi-trophy',
        GRADE_PROMOTION_RUN: 'pi pi-trophy',
        // 포인트 상점 — 정지 중 회원의 환불 신청(링크 `/point-shop/purchases?refundRequest=PENDING`)
        POINT_SHOP_REFUND_REQUESTED: 'pi pi-replay',
        // 결제 — 결제 이상(링크 `/payments/anomalies`) · 대사 불일치(링크 `/payments/reconciliation`)
        COMMERCE_PAYMENT_ANOMALY: 'pi pi-credit-card',
        COMMERCE_RECONCILE_MISMATCH: 'pi pi-check-square',
    };

    /**
     * 심각도의 아이콘 — 색만으로는 곁눈으로 안 갈리고 색을 못 가리는 사람에게는 같아 보이므로 모양을 다르게 한다.
     * @param {NotificationKind} kind - 심각도.
     * @returns {string} primeicons 클래스.
     */
    static kindIcon(kind: NotificationKind): string {
        if (kind === 'success') return 'pi pi-check-circle';
        if (kind === 'warning') return 'pi pi-exclamation-triangle';
        if (kind === 'critical') return 'pi pi-exclamation-circle';
        return 'pi pi-info-circle';
    }

    /**
     * 심각도 딱지(PrimeVue `Tag`)의 색.
     * @param {NotificationKind} kind - 심각도.
     * @returns {NotificationTagSeverity} Tag 의 severity.
     */
    static tagSeverity(kind: NotificationKind): NotificationTagSeverity {
        if (kind === 'success') return 'success';
        if (kind === 'warning') return 'warn';
        if (kind === 'critical') return 'danger';
        return 'info';
    }

    /**
     * 알림이 어느 영역·이벤트의 것인지 짚는 작은 아이콘. 이벤트 코드로 먼저 찾고, 없으면 영역으로, 그래도 없으면 null(심각도 아이콘만 보인다).
     * @param {string} eventCode - 이벤트 코드.
     * @param {string} area - 영역 id.
     * @returns {string | null} primeicons 클래스.
     */
    static iconOf(eventCode: string, area: string): string | null {
        return UtilNotification.EVENT_ICONS[eventCode] ?? UtilNotification.AREA_ICONS[area] ?? null;
    }

    /**
     * 영역의 아이콘.
     * @param {string} area - 영역 id.
     * @returns {string | null} primeicons 클래스. 모르는 영역은 null.
     */
    static areaIcon(area: string): string | null {
        return UtilNotification.AREA_ICONS[area] ?? null;
    }

    /**
     * 영역 id 가 i18n 에 있는 영역인가 — 없으면 화면은 id 를 그대로 보인다.
     * @param {string} area - 영역 id.
     * @returns {boolean} 아는 영역이면 true.
     */
    static isKnownArea(area: string): boolean {
        return UtilNotification.AREA_ORDER.includes(area);
    }

    /**
     * 영역 문구의 i18n 키. `security` 만 알림의 키이고 나머지는 권한 영역 이름을 같이 쓴다(같은 영역 id).
     * @param {string} area - 영역 id.
     * @returns {string} i18n 키.
     */
    static areaLabelKey(area: string): string {
        return area === 'security' ? 'notification.areaSecurity' : `permission.area.${area}`;
    }

    /**
     * 영역을 구획 주소(`AppGadget` 의 `anchor`: 소문자·숫자·하이픈)로 — `bizAd` → `area-biz-ad`.
     * @param {string} area - 영역 id.
     * @returns {string} 구획 주소.
     */
    static areaAnchor(area: string): string {
        return `area-${area.replace(/([a-z0-9])([A-Z])/g, '$1-$2').toLowerCase()}`;
    }

    /**
     * 항목을 영역별로 묶는다 — 영역은 `AREA_ORDER`(모르는 영역은 뒤에 처음 나온 차례), 영역 안은 받은 차례 그대로.
     * @param {T[]} list - 영역(`area`)을 가진 항목들.
     * @returns {{ area: string; items: T[] }[]} 영역별 묶음.
     */
    static groupByArea<T extends { area: string }>(list: readonly T[]): { area: string; items: T[] }[] {
        const groups = new Map<string, T[]>();
        for (const item of list) {
            const group = groups.get(item.area);
            if (group) group.push(item);
            else groups.set(item.area, [item]);
        }
        const rank = (area: string) => {
            const at = UtilNotification.AREA_ORDER.indexOf(area);
            return at < 0 ? UtilNotification.AREA_ORDER.length : at;
        };
        return [...groups.entries()].map(([area, items]) => ({ area, items })).sort((a, b) => rank(a.area) - rank(b.area));
    }

    /**
     * 이벤트의 받는 범위가 「권한자 전원」인가 — 칸이 없으면(이 칸이 생기기 전 서버) 권한자 전원으로 본다.
     * @param {{ defaultRecipients?: boolean }} event - 운영자 이벤트 한 줄(`NotificationEventView`).
     * @returns {boolean} 권한자 전원이면 true, 「팀에만」이면 false.
     */
    static defaultRecipientsOf(event: { defaultRecipients?: boolean }): boolean {
        return event.defaultRecipients !== false;
    }

    /**
     * 받는 범위가 저장할 수 없는 모양인가 — 「팀에만」(`defaultRecipients=false`)인데 고른 팀이 없다.
     * 서버 이벤트 `PUT` 의 같은 검사(400 `VALIDATION` `fields.teamIds`)를 미리 알려 주는 용도다(판정은 서버).
     * @param {boolean} defaultRecipients - 받는 범위(true = 권한자 전원).
     * @param {readonly string[]} teamIds - 고른 팀 id 들.
     * @returns {boolean} 팀을 골라야 저장할 수 있으면 true.
     */
    static needsTeam(defaultRecipients: boolean, teamIds: readonly string[]): boolean {
        return !defaultRecipients && teamIds.length === 0;
    }

    /**
     * 이동 경로에서 쿼리·조각을 뗀 주소 부분 — 화면 열기 권한 판정(`UtilAdminMenu.canOpen`)은 경로만 본다.
     * @param {string} link - 서버가 준 이동 경로(예: `/settings/batch-logs?jobName=x`).
     * @returns {string} 경로(예: `/settings/batch-logs`).
     */
    static pathOf(link: string): string {
        return link.split(/[?#]/)[0] || '/';
    }
}
