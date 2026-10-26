/** 설정 묶음의 한 화면 — 이름·안내는 i18n 키(그릴 때 `t` 로 푼다) */
export interface SettingsEntry {
    to: string;
    /** 이름의 i18n 키 */
    labelKey: string;
    /** 목록(좁은 화면)의 둘째 줄 안내 i18n 키 */
    hintKey: string;
    icon: string;
    testid: string;
    /** 이 화면 아래의 하위 화면 — 넓은 화면의 왼쪽 메뉴에만 들여 써서 보인다(좁은 화면 목록·`/me` 바로가기에는 없다 — 그 화면 안의 줄로 간다) */
    children?: readonly SettingsSubEntry[];
}

/** 설정 화면 아래의 하위 화면 한 줄 */
export interface SettingsSubEntry {
    to: string;
    labelKey: string;
    testid: string;
}

/**
 * 설정 묶음(`/settings/*`)의 화면 목록 — 넓은 화면의 왼쪽 메뉴(`pages/settings.vue`)와 좁은 화면의 목록(`pages/settings/index.vue`)이 같은 차례로 그린다.
 * **지금 만들어진 화면만** 둔다. 차례는 community 설정 메뉴와 같다(프로필 · 닉네임 이미지 · 성인 확인 · 보기 · 차단 관리 · 이용자 메모 · 알림 · 쪽지 수신 · 계정) —
 * community 에서 「보기」 안에 있던 시간 표시는 로보얌에서 따로 있는 화면이라 「보기」 바로 뒤에 둔다.
 */
export default class UtilSettings {
    /** 설정 묶음 첫 화면(넓은 화면에서 `/settings` 로 오면 여기로) */
    static readonly FIRST_PATH = '/settings/profile';

    static readonly ENTRIES: readonly SettingsEntry[] = [
        { to: '/settings/profile', labelKey: 'settings.profile', hintKey: 'settings.profileHint', icon: 'pi pi-user-edit', testid: 'settings-profile' },
        { to: '/settings/nickname-image', labelKey: 'settings.nicknameImage', hintKey: 'settings.nicknameImageHint', icon: 'pi pi-image', testid: 'settings-nickname-image' },
        { to: '/settings/adult', labelKey: 'settings.adult', hintKey: 'settings.adultHint', icon: 'pi pi-verified', testid: 'settings-adult' },
        { to: '/settings/view', labelKey: 'settings.view', hintKey: 'settings.viewHint', icon: 'pi pi-eye', testid: 'settings-view' },
        { to: '/settings/display', labelKey: 'settings.display', hintKey: 'settings.displayHint', icon: 'pi pi-clock', testid: 'settings-display' },
        { to: '/settings/blocks', labelKey: 'settings.blocks', hintKey: 'settings.blocksHint', icon: 'pi pi-ban', testid: 'settings-blocks' },
        { to: '/settings/memos', labelKey: 'settings.memos', hintKey: 'settings.memosHint', icon: 'pi pi-bookmark', testid: 'settings-memos' },
        { to: '/settings/notifications', labelKey: 'settings.notifications', hintKey: 'settings.notificationsHint', icon: 'pi pi-bell', testid: 'settings-notifications' },
        { to: '/settings/messages', labelKey: 'settings.messages', hintKey: 'settings.messagesHint', icon: 'pi pi-envelope', testid: 'settings-messages' },
        {
            to: '/settings/account',
            labelKey: 'account.title',
            hintKey: 'settings.accountHint',
            icon: 'pi pi-link',
            testid: 'settings-account',
            children: [
                { to: '/settings/account/email', labelKey: 'account.emailChange', testid: 'settings-account-email' },
                { to: '/settings/account/password', labelKey: 'account.passwordChange', testid: 'settings-account-password' },
            ],
        },
        { to: '/settings/identity', labelKey: 'identity.title', hintKey: 'settings.identityHint', icon: 'pi pi-id-card', testid: 'settings-identity' },
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

    /**
     * 지금 주소가 하위 화면이면 그 부모 설정 화면 — 좁은 화면의 「‹ 돌아가기」가 설정 목록 대신 부모로 간다.
     * @param {string} path - 지금 경로.
     * @returns {SettingsEntry | null} 하위 화면의 부모. 하위 화면이 아니면 null.
     */
    static parentOf(path: string): SettingsEntry | null {
        return UtilSettings.ENTRIES.find((entry) => entry.children?.some((child) => UtilSettings.isActive(path, child.to))) ?? null;
    }
}
