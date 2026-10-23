/** 설정 묶음의 한 화면 — 이름·안내는 i18n 키(그릴 때 `t` 로 푼다) */
export interface SettingsEntry {
    to: string;
    /** 이름의 i18n 키 */
    labelKey: string;
    /** 목록(좁은 화면)의 둘째 줄 안내 i18n 키 */
    hintKey: string;
    icon: string;
    testid: string;
}

/**
 * 설정 묶음(`/settings/*`)의 화면 목록 — 넓은 화면의 왼쪽 메뉴(`pages/settings.vue`)와 좁은 화면의 목록(`pages/settings/index.vue`)이 같은 차례로 그린다.
 * **지금 만들어진 화면만** 둔다(community 의 차단·메모·쪽지·19금·닉네임 그림은 로보얌에 기능이 없어 넣지 않았다).
 */
export default class UtilSettings {
    /** 설정 묶음 첫 화면(넓은 화면에서 `/settings` 로 오면 여기로) */
    static readonly FIRST_PATH = '/settings/profile';

    static readonly ENTRIES: readonly SettingsEntry[] = [
        { to: '/settings/profile', labelKey: 'settings.profile', hintKey: 'settings.profileHint', icon: 'pi pi-user-edit', testid: 'settings-profile' },
        { to: '/settings/notifications', labelKey: 'settings.notifications', hintKey: 'settings.notificationsHint', icon: 'pi pi-bell', testid: 'settings-notifications' },
        { to: '/settings/display', labelKey: 'settings.display', hintKey: 'settings.displayHint', icon: 'pi pi-clock', testid: 'settings-display' },
        { to: '/settings/account', labelKey: 'account.title', hintKey: 'settings.accountHint', icon: 'pi pi-link', testid: 'settings-account' },
    ];

    /**
     * 지금 주소가 그 설정 화면(또는 그 아래)인가.
     * @param {string} path - 지금 경로.
     * @param {string} to - 설정 화면 경로.
     * @returns {boolean} 같거나 그 아래면 true.
     */
    static isActive(path: string, to: string): boolean {
        return path === to || path.startsWith(`${to}/`);
    }
}
