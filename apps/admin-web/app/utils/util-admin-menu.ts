/**
 * 관리자 메뉴 항목 정의 — 관리자 셸의 메뉴 정의다. 사이드 메뉴(데스크톱)·상단 메뉴 줄·모바일 드로어·메뉴 검색이 모두 여기서 읽는다.
 *
 * **새 화면 = 여기 `ITEMS` 한 줄 + 두 언어의 `i18n/locales/{ko,en}/menu.ts` 항목 이름(`labelKey` 의 키) + 서버 `AdminScreenRegistry`(server/admin `menu/service`) 한 줄.**
 * 서버 레지스트리는 관리자 메뉴 구성(메뉴 관리 › 관리자 메뉴)이 쓰는 화면 키 → 주소·권한 표이자 저장한 적 없을 때의 기본 구성이라, 두 곳의 키·주소·권한·아이콘·차례를 같게 유지한다.
 * 셸은 아직 저장한 구성이 아니라 이 `ITEMS` 를 읽는다(menu-contract 미확정 1).
 * **아직 화면이 없는 기능의 항목은 넣지 않는다**(규약: 없는 기능의 메뉴·링크는 보여 주지 않는다 — `_frontend-core.md` 2-2).
 * 메뉴별 권한: 화면 항목에 `permission`(권한 코드, `util-permission-catalog.ts`)을 달면 그 권한이 없는 운영자에게는 메뉴·검색에서 감춘다(`visible`). 칸이 없는 항목(대시보드)은 모두에게 보인다.
 * 주소로 직접 들어오면 셸(`layouts/default.vue`)이 `canOpen` 으로 걸러 "권한 없음" 상태를 보인다. 감추는 것은 편의이고 막는 것은 서버다.
 * 최고 관리자 전용(권한 코드로 위임하지 않는 화면 — 로그인 보안): 항목에 `superOnly: true` 를 단다. 판정은 같은 `can` 에 예약 코드 `SUPER_ONLY` 를 물어서 한다 —
 * 로그인한 운영자의 `can`(`useAdminPermission`)은 최고 관리자에게만 모든 코드에 true 이고, 역할 미리보기의 `can`(그 역할이 가진 코드)에는 이 코드가 없어 늘 감춰진다.
 * 권한 카탈로그의 `featureReady`(기능 준비 중 표시)는 메뉴 감춤에 쓰지 않는다 — 감춤은 `permission` 보유만 본다.
 *
 * 구조는 hamonica3 의 메뉴(`menu` 표의 최상위 + 하위)를 따른다 — 두 층까지다.
 *  · 화면 항목(`AdminMenuItem`): 주소(`to`)가 있는 갈 수 있는 항목.
 *  · 하위 항목(`children`): 화면 항목이 하위 화면 항목을 거느릴 수 있다(예: 역할 관리 › 회원 역할). 부모 항목은 눌러도 이동하지 않고 펼치고 접는다 — `to` 는 하위 주소들의 공통 앞부분(`/settings/roles`)이라
 *    하위 주소에 있으면 부모도 활성이 된다(부모 자체의 화면은 없다). 권한·모드·활성·열기 판정은 하위 항목까지 재귀로 본다(하위가 모두 숨겨지면 부모도 숨는다).
 *    어느 하위에도 속하지 않는 부모 아래 주소(하위 둘이 같이 쓰는 편집 화면 — 문구 편집 `/settings/notification-messages/{id}`)는 부모만 활성이고, 권한·모드도 부모의 것으로 본다.
 *  · 묶음(`AdminMenuGroup`): 주소가 없고 아래에 화면 항목을 거느린다. 사이드바에서는 눌리지 않는 구획 제목이 되고(레일에서는 제목 없이 항목만),
 *    상단 메뉴 줄에서는 팝업을 여는 최상위 항목이 되며, 모바일 드로어는 사이드바와 같다.
 */
/**
 * 메뉴 모드 — 일반 메뉴(`normal`)와 설정 메뉴(`settings`). 사이드바·상단 메뉴·모바일 드로어는 지금 모드의 메뉴만 보이고, 검색은 모든 모드를 찾는다.
 * 모드는 **최상위 노드**(화면 항목 또는 묶음)에 달고, 묶음 아래 항목은 묶음의 모드를 따른다. 칸이 없으면 `normal` 이다.
 * 어느 모드를 보일지는 `composables/shell/useAppMenu.ts` 가 정한다(주소가 속한 노드의 모드 → 없으면 마지막으로 고른 모드).
 */
export type AdminMenuMode = 'normal' | 'settings';

export interface AdminMenuItem {
    /** 항목 식별자 — `v-for` 의 key */
    key: string;
    /** 최상위 노드일 때 속한 메뉴 모드. 없으면 `normal` (묶음 아래 항목에서는 읽지 않는다) */
    mode?: AdminMenuMode;
    /** 이동할 주소(화면 경로) */
    to: string;
    /**
     * primeicons 클래스 — **선택**이다. 있으면 그리고, 없으면 아이콘 자리(폭)도 만들지 않고 이름 글자만 그린다(2차 항목은 보통 있고, 하위(3차) 항목은 두지 않는다).
     * 아이콘이 없는 항목은 접힌 레일(아이콘만)에 그릴 수 없어 레일에서는 숨는다 — 그래서 하위 항목은 레일에서 그리지 않고 아이콘 있는 부모만 보인다. 이름 첫 글자 칩 같은 대체 표시는 두지 않는다.
     */
    icon?: string;
    /** 항목 이름의 i18n 키 (`menu.<key>`) */
    labelKey: string;
    /** 이 화면을 열 수 있는 권한 코드. 없으면 모두에게 보인다 */
    permission?: string;
    /** 최고 관리자(루트)만 — 권한 코드로 위임하지 않는 화면. `permission` 과 함께 쓰지 않는다 */
    superOnly?: true;
    /** 하위 화면 항목 — 있으면 이 항목은 이동하지 않고 하위를 펼치는 부모다(`to` 는 하위 주소들의 공통 앞부분) */
    children?: readonly AdminMenuItem[];
}

export interface AdminMenuGroup {
    /** 묶음 식별자 — `v-for` 의 key */
    key: string;
    /** 속한 메뉴 모드. 없으면 `normal` */
    mode?: AdminMenuMode;
    /** primeicons 클래스 — 상단 메뉴 줄의 최상위 항목에 보인다 */
    icon: string;
    /** 묶음 이름의 i18n 키 (`menu.<key>`) */
    labelKey: string;
    /** 묶음 아래의 화면 항목 */
    items: readonly AdminMenuItem[];
}

export type AdminMenuNode = AdminMenuItem | AdminMenuGroup;

/** 사이드바·드로어가 그리는 한 덩어리 — 제목(`group`)이 없으면 제목 없는 항목들이다 */
export interface AdminMenuSection {
    key: string;
    /** 구획 제목이 되는 묶음. 낱개 화면 항목들의 덩어리면 null */
    group: AdminMenuGroup | null;
    items: readonly AdminMenuItem[];
}

export default class UtilAdminMenu {
    /** 최고 관리자 전용 항목(`superOnly`)을 물을 때 쓰는 예약 코드 — 권한 카탈로그에 없는 값이라 최고 관리자의 `can` 만 true 를 준다 */
    static readonly SUPER_ONLY = '$super';

    /**
     * 이 항목을 열 수 있는가 — 최고 관리자 전용이면 `SUPER_ONLY`, 권한이 걸렸으면 그 권한을 본다.
     * @param {AdminMenuItem} item - 메뉴 항목.
     * @param {(code: string) => boolean} can - 그 권한 코드를 가졌는가.
     * @returns {boolean} 열 수 있으면 true.
     */
    static allowed(item: AdminMenuItem, can: (code: string) => boolean): boolean {
        if (item.superOnly && !can(UtilAdminMenu.SUPER_ONLY)) return false;
        return !item.permission || can(item.permission);
    }

    /**
     * 지금 있는 화면의 메뉴. 새 화면이 생길 때 한 줄씩 더한다(서버 `AdminScreenRegistry` 에도 같은 한 줄 — 파일 머리말).
     * 운영자와 권한 묶음 — hamonica3 의 `사용자와 권한`(회원 관리 · 역할 관리)에 대응한다. 이 제품에서 회원은 공개 사이트 이용자이고 관리자 앱 계정은 운영자다.
     * 배치와 로그 묶음 — hamonica3 의 시스템 설정 메뉴(`배치와 로그`: 배치 관리 · 배치 로그 · 감사 로그)와 같은 이름·차례·아이콘·주소다.
     * 모드: 대시보드·회원(회원 관리·등급 설정)·메뉴 관리·게시판·포인트는 일반 메뉴, 「일반」·알림·운영자와 권한·배치와 로그는 설정 메뉴(`mode: 'settings'`)다. 등급 설정은 회원 운영 업무라 일반 메뉴에 남긴다(주소가 `/settings/…` 여도 모드는 메뉴 정의가 정한다).
     * 포인트 묶음(포인트 내역·포인트 정책)은 실제 서버(`usePointApi`)를 부른다 — 권한(`point.read`)이 있는 운영자에게 보인다.
     */
    static readonly ITEMS: readonly AdminMenuNode[] = [
        { key: 'dashboard', to: '/', icon: 'pi pi-home', labelKey: 'menu.dashboard' },
        // 회원 묶음 — 공개 사이트 이용자(회원)를 다룬다. 관리자 앱 계정(운영자)은 아래 `operatorsAndPermissions` 다.
        {
            key: 'member',
            icon: 'pi pi-users',
            labelKey: 'menu.member',
            items: [
                { key: 'members', to: '/members', icon: 'pi pi-user', labelKey: 'menu.members', permission: 'member.read' },
                { key: 'grades', to: '/grades', icon: 'pi pi-trophy', labelKey: 'menu.grades', permission: 'grade.read' },
                // 닉네임 이미지 심사 — 주소가 `/members` 아래지만 회원 관리와 다른 화면·권한이다(`isActive` 가 더 구체적인 항목에 주소를 준다)
                { key: 'nicknameImages', to: '/members/nickname-images', icon: 'pi pi-image', labelKey: 'menu.nicknameImages', permission: 'nickname-image.read' },
            ],
        },
        // 메뉴 관리 묶음 — 공개 사이트 메뉴(메뉴 그룹별 트리)와 관리자 사이트 메뉴 구성. community 의 「메뉴 구성」에 대응한다. 부모 주소(`/menus`)로 들어오면 공개 사이트 메뉴로 보낸다
        {
            key: 'siteMenu',
            icon: 'pi pi-bars',
            labelKey: 'menu.siteMenu',
            items: [
                { key: 'menusPublic', to: '/menus/public', icon: 'pi pi-globe', labelKey: 'menu.menusPublic', permission: 'layout.read' },
                { key: 'menusAdmin', to: '/menus/admin', icon: 'pi pi-desktop', labelKey: 'menu.menusAdmin', permission: 'layout.read' },
            ],
        },
        // 게시판 묶음 — 게시판 관리(게시판 목록·게시판별 설정)와 모든 게시판에 일괄 적용되는 기본 설정. community 의 「게시판 구성」·「게시판 기본 설정」, 그누보드7 의 게시판 관리·기본 설정에 대응한다
        {
            key: 'board',
            icon: 'pi pi-list',
            labelKey: 'menu.board',
            items: [
                { key: 'boards', to: '/boards', icon: 'pi pi-th-large', labelKey: 'menu.boards', permission: 'board.read' },
                // 기본 설정은 하위 메뉴 다섯을 가진 부모다(그누보드7 게시판 기본 설정의 탭 다섯) — 역할 관리와 같은 방식. 부모 주소(`/boards/defaults`)로 들어오면 「일반」으로 보낸다
                {
                    key: 'boardDefaults',
                    to: '/boards/defaults',
                    icon: 'pi pi-sliders-v',
                    labelKey: 'menu.boardDefaults',
                    permission: 'board.read',
                    children: [
                        { key: 'boardDefaultsGeneral', to: '/boards/defaults/general', labelKey: 'menu.boardDefaultsGeneral', permission: 'board.read' },
                        { key: 'boardDefaultsReport', to: '/boards/defaults/report', labelKey: 'menu.boardDefaultsReport', permission: 'board.read' },
                        { key: 'boardDefaultsSecurity', to: '/boards/defaults/security', labelKey: 'menu.boardDefaultsSecurity', permission: 'board.read' },
                        { key: 'boardDefaultsNotifications', to: '/boards/defaults/notifications', labelKey: 'menu.boardDefaultsNotifications', permission: 'board.read' },
                        { key: 'boardDefaultsPrivacy', to: '/boards/defaults/privacy', labelKey: 'menu.boardDefaultsPrivacy', permission: 'board.read' },
                    ],
                },
            ],
        },
        // 콘텐츠 묶음 — 하이라이트(토론 의견 관리 포함)·인기글 관리(빼기·다시 넣기)·인기글 설정(점수식). 서버 `AdminScreenRegistry` 의 `menu.content` 와 같은 키·주소·권한·차례(docs/features/popular-highlight-contract.md).
        // `/content/popular/settings` 는 `/content/popular` 아래지만 다른 화면이다(`isActive` 가 더 구체적인 항목에 주소를 준다)
        {
            key: 'content',
            icon: 'pi pi-star',
            labelKey: 'menu.content',
            items: [
                { key: 'highlights', to: '/content/highlights', icon: 'pi pi-bookmark', labelKey: 'menu.highlights', permission: 'highlight.read' },
                { key: 'popularPosts', to: '/content/popular', icon: 'pi pi-chart-line', labelKey: 'menu.popularPosts', permission: 'popular.read' },
                { key: 'popularSettings', to: '/content/popular/settings', icon: 'pi pi-sliders-h', labelKey: 'menu.popularSettings', permission: 'popular.read' },
            ],
        },
        // 포인트 묶음 — 전체 포인트 내역(수동 지급·차감 포함)과 지급 규칙(포인트 정책). community 의 「포인트 설정」, 그누보드7 의 포인트 관리에 대응한다
        {
            key: 'point',
            icon: 'pi pi-wallet',
            labelKey: 'menu.point',
            items: [
                { key: 'points', to: '/points', icon: 'pi pi-receipt', labelKey: 'menu.points', permission: 'point.read' },
                { key: 'pointPolicy', to: '/points/policy', icon: 'pi pi-percentage', labelKey: 'menu.pointPolicy', permission: 'point.read' },
            ],
        },
        // 설정 메뉴의 첫 묶음 — 시스템(hamonica3 의 설정 > 일반). 「일반」·「소셜 로그인」·「조직 구성」이 있고(메일 서버는 다음 「메일」 묶음, 알림 도구·템플릿·문구·이벤트는 그다음 「알림·이벤트」 묶음) 후속 설정(사이트·연동 등)이 들어올 자리다. 프로필 메뉴의 「설정」이 여기로 들어온다
        {
            key: 'settingsSystem',
            mode: 'settings',
            icon: 'pi pi-cog',
            labelKey: 'menu.system',
            items: [
                { key: 'siteGeneral', to: '/settings/general', icon: 'pi pi-sliders-h', labelKey: 'menu.general', permission: 'site.read' },
                // 소셜 로그인 설정(카카오·네이버·구글 키) — 조회는 `site.read`, 저장·삭제는 화면 안에서 `site.secret`
                { key: 'socialLogin', to: '/settings/social-login', icon: 'pi pi-key', labelKey: 'menu.socialLogin', permission: 'site.read' },
                // 로그인 보안(관리자 2차 인증 켜기·끄기와 값) — 최고 관리자만(commerce.md 13-3, 권한 코드로 위임하지 않는다). 서버 `AdminScreenRegistry` 에도 같은 한 줄이 필요하다(서버 단계)
                { key: 'loginSecurity', to: '/settings/login-security', icon: 'pi pi-shield', labelKey: 'menu.loginSecurity', superOnly: true },
                // 조직 구성 — 운영자를 팀으로 묶는다(알림 이벤트의 「추가 수신 팀」으로 쓴다)
                { key: 'teams', to: '/settings/teams', icon: 'pi pi-sitemap', labelKey: 'menu.teams', permission: 'team.read' },
                { key: 'terms', to: '/settings/terms', icon: 'pi pi-file', labelKey: 'menu.terms', permission: 'terms.read' },
            ],
        },
        // 회원 설정 묶음 — 회원 기능의 사이트 규칙. 닉네임 이미지 신청 조건(조회 `nickname-image.read`, 저장은 화면 안에서 `nickname-image.manage`)
        {
            key: 'settingsMember',
            mode: 'settings',
            icon: 'pi pi-users',
            labelKey: 'menu.memberSettings',
            items: [{ key: 'nicknameImageSettings', to: '/settings/nickname-image', icon: 'pi pi-image', labelKey: 'menu.nicknameImageSettings', permission: 'nickname-image.read' }],
        },
        // 메일 묶음 — 메일 서버만(message-template-contract 「개정 3」 — 메일 종류·템플릿·레이아웃은 알림·이벤트 묶음으로 옮겼다 — 개정 4 의 「템플릿 › 메일」·「문구」)
        {
            key: 'settingsMail',
            mode: 'settings',
            icon: 'pi pi-envelope',
            labelKey: 'menu.mail',
            items: [
                // 메일 서버 설정 — 조회는 `site.read`, 저장·시험 발송은 화면 안에서 `site.secret`
                { key: 'mailSettings', to: '/settings/mail', icon: 'pi pi-server', labelKey: 'menu.mailSettings', permission: 'site.read' },
            ],
        },
        // 알림·이벤트 묶음(개정 4 — message-template-contract) — 알림 도구 · 템플릿(부모: 메일 · Slack · Mattermost · 카카오톡 — 도구별 겉모양) · 문구(부모: 운영자 · 회원 · 필수 안내 메일 — 이벤트·채널별 제목과 본문) · 이벤트(부모: 운영자 · 회원).
        // 옛 주소(개정 3 의 `/settings/notification-templates/templates|layouts|required-mails/**` · 그 전 `/settings/mail-*`)는 각 화면이 새 주소로 보낸다.
        // 모두 실제 서버에 연결돼 있다. 키·주소·권한(`template.read` · `notification.read`)은 서버 `AdminScreenRegistry` 와 같다.
        {
            key: 'settingsNotification',
            mode: 'settings',
            icon: 'pi pi-bell',
            labelKey: 'menu.notifications',
            items: [
                { key: 'notificationChannels', to: '/settings/notification-channels', icon: 'pi pi-send', labelKey: 'menu.notificationChannels', permission: 'notification.read' },
                // 템플릿은 하위 메뉴 넷을 가진 부모다(역할 관리와 같은 방식). 부모 주소(`/settings/notification-templates`)로 들어오면 메일 템플릿으로 보낸다.
                // 편집 화면(`/{유형}/{id}`)은 그 유형의 하위 주소라 그 하위가 활성이다. 카카오톡은 준비 중(보기만) — 계약이 메뉴에 두기로 정했다
                {
                    key: 'notificationTemplates',
                    to: '/settings/notification-templates',
                    icon: 'pi pi-palette',
                    labelKey: 'menu.notificationTemplates',
                    permission: 'template.read',
                    children: [
                        { key: 'mailTemplates', to: '/settings/notification-templates/mail', labelKey: 'menu.mailTemplates', permission: 'template.read' },
                        { key: 'slackTemplates', to: '/settings/notification-templates/slack', labelKey: 'menu.slackTemplates', permission: 'template.read' },
                        { key: 'mattermostTemplates', to: '/settings/notification-templates/mattermost', labelKey: 'menu.mattermostTemplates', permission: 'template.read' },
                        { key: 'kakaoTemplates', to: '/settings/notification-templates/kakao', labelKey: 'menu.kakaoTemplates', permission: 'template.read' },
                    ],
                },
                // 문구는 하위 메뉴 셋을 가진 부모다. 부모 주소(`/settings/notification-messages`)로 들어오면 운영자 문구로 보낸다.
                // 문구 목록은 받는 사람(운영자 · 회원 — 이벤트 메뉴와 같은 차례)마다 메뉴 하나다. 편집 화면(`/settings/notification-messages/{id}`)은 어느 하위에도 속하지 않아 부모만 활성이 되고, 권한·모드도 부모의 것으로 본다(`parents`)
                {
                    key: 'notificationMessages',
                    to: '/settings/notification-messages',
                    icon: 'pi pi-file-edit',
                    labelKey: 'menu.notificationMessages',
                    permission: 'template.read',
                    children: [
                        { key: 'operatorMessages', to: '/settings/notification-messages/operator', labelKey: 'menu.operatorMessages', permission: 'template.read' },
                        { key: 'memberMessages', to: '/settings/notification-messages/member', labelKey: 'menu.memberMessages', permission: 'template.read' },
                        { key: 'requiredMails', to: '/settings/notification-messages/required-mails', labelKey: 'menu.requiredMails', permission: 'template.read' },
                    ],
                },
                // 이벤트는 하위 메뉴 둘(운영자·회원)을 가진 부모다. 부모 주소(`/settings/notifications`)로 들어오면 운영자 이벤트로 보낸다
                {
                    key: 'events',
                    to: '/settings/notifications',
                    icon: 'pi pi-bolt',
                    labelKey: 'menu.events',
                    permission: 'notification.read',
                    children: [
                        { key: 'operatorEvents', to: '/settings/notifications/operator', labelKey: 'menu.eventsOperator', permission: 'notification.read' },
                        { key: 'memberEvents', to: '/settings/notifications/member', labelKey: 'menu.eventsMember', permission: 'notification.read' },
                    ],
                },
            ],
        },
        {
            key: 'operatorsAndPermissions',
            mode: 'settings',
            icon: 'pi pi-users',
            labelKey: 'menu.operatorsAndPermissions',
            items: [
                { key: 'operators', to: '/settings/operators', icon: 'pi pi-user-edit', labelKey: 'menu.operators', permission: 'operator.read' },
                // 역할 관리는 하위 메뉴 셋을 가진 부모다 — 하위 주소의 마지막 조각이 화면 종류다(`pages/settings/roles/[[kind]].vue`). 부모 주소(`/settings/roles`)로 직접 들어오면 운영자 역할로 보낸다
                {
                    key: 'roles',
                    to: '/settings/roles',
                    icon: 'pi pi-id-card',
                    labelKey: 'menu.roles',
                    permission: 'role.read',
                    children: [
                        { key: 'rolesMember', to: '/settings/roles/member', labelKey: 'menu.rolesMember', permission: 'role.read' },
                        { key: 'rolesOperator', to: '/settings/roles/operator', labelKey: 'menu.rolesOperator', permission: 'role.read' },
                        { key: 'rolesGrade', to: '/settings/roles/grade', labelKey: 'menu.rolesGrade', permission: 'role.read' },
                    ],
                },
            ],
        },
        {
            key: 'batchAndLogs',
            mode: 'settings',
            icon: 'pi pi-history',
            labelKey: 'menu.batchAndLogs',
            items: [
                { key: 'batches', to: '/settings/batches', icon: 'pi pi-clock', labelKey: 'menu.batches', permission: 'batch.read' },
                { key: 'batchLogs', to: '/settings/batch-logs', icon: 'pi pi-book', labelKey: 'menu.batchLogs', permission: 'batch.read' },
                { key: 'audit', to: '/settings/audit', icon: 'pi pi-history', labelKey: 'menu.audit', permission: 'audit.read' },
            ],
        },
    ];

    /**
     * 권한으로 걸러 낸 메뉴 — 권한이 없는 화면 항목은 빼고, 항목이 하나도 안 남은 묶음도 뺀다. 사이드바·상단 메뉴·드로어·검색이 이것을 읽는다.
     * 역할 관리의 "이 역할이 보는 메뉴" 미리보기도 같은 함수에 그 역할의 권한을 넣어 계산한다.
     * @param {(code: string) => boolean} can - 그 권한 코드를 가졌는가.
     * @param {AdminMenuMode} [mode] - 이 모드의 노드만. 안 주면 모든 모드(검색·역할 미리보기).
     * @returns {AdminMenuNode[]} 보이는 메뉴 노드(원본 순서).
     */
    static visible(can: (code: string) => boolean, mode?: AdminMenuMode): AdminMenuNode[] {
        const out: AdminMenuNode[] = [];
        for (const node of UtilAdminMenu.ITEMS) {
            if (mode && UtilAdminMenu.modeOf(node) !== mode) continue;
            if (UtilAdminMenu.isGroup(node)) {
                const items = UtilAdminMenu.visibleItems(node.items, can);
                if (items.length) out.push({ ...node, items });
            } else {
                const [item] = UtilAdminMenu.visibleItems([node], can);
                if (item) out.push(item);
            }
        }
        return out;
    }

    /**
     * 화면 항목들을 권한으로 거른다 — 하위 항목은 재귀로 거르고, 하위가 있던 부모는 하위가 모두 숨겨지면 함께 숨긴다.
     * @param {readonly AdminMenuItem[]} items - 거를 항목들.
     * @param {(code: string) => boolean} can - 그 권한 코드를 가졌는가.
     * @returns {AdminMenuItem[]} 보이는 항목(원본 순서, 하위도 걸러진 새 객체).
     */
    static visibleItems(items: readonly AdminMenuItem[], can: (code: string) => boolean): AdminMenuItem[] {
        const out: AdminMenuItem[] = [];
        for (const item of items) {
            if (!UtilAdminMenu.allowed(item, can)) continue;
            if (item.children) {
                const children = UtilAdminMenu.visibleItems(item.children, can);
                if (children.length) out.push({ ...item, children });
            } else {
                out.push(item);
            }
        }
        return out;
    }

    /**
     * 하위까지 펼친 **화면 항목(잎)** 들 — 이동할 수 있는 항목만. 부모(`children`)는 빠지고 그 하위가 들어간다.
     * @param {readonly AdminMenuItem[]} items - 항목들.
     * @returns {AdminMenuItem[]} 잎 항목.
     */
    static leaves(items: readonly AdminMenuItem[]): AdminMenuItem[] {
        return items.flatMap((item) => (item.children ? UtilAdminMenu.leaves(item.children) : [item]));
    }

    /**
     * 노드의 메뉴 모드 — 칸이 없으면 일반 메뉴.
     * @param {AdminMenuNode} node - 최상위 메뉴 노드.
     * @returns {AdminMenuMode} 속한 모드.
     */
    static modeOf(node: AdminMenuNode): AdminMenuMode {
        return node.mode ?? 'normal';
    }

    /**
     * 이 주소가 속한 메뉴 노드의 모드 — 메뉴 항목(하위 주소 포함)이 아닌 주소(내 정보 등)는 null. 권한과 무관하다(주소가 어느 메뉴의 것인지만 본다).
     * @param {string} path - 주소의 경로(`route.path`).
     * @returns {AdminMenuMode | null} 속한 모드. 어느 메뉴에도 없으면 null.
     */
    static modeOfPath(path: string): AdminMenuMode | null {
        for (const node of UtilAdminMenu.ITEMS) {
            const items = UtilAdminMenu.leaves(UtilAdminMenu.isGroup(node) ? node.items : [node]);
            if (items.some((item) => UtilAdminMenu.isActive(item, path))) return UtilAdminMenu.modeOf(node);
        }
        for (const node of UtilAdminMenu.ITEMS) {
            const parents = UtilAdminMenu.parents(UtilAdminMenu.isGroup(node) ? node.items : [node]);
            if (parents.some((item) => UtilAdminMenu.isActive(item, path))) return UtilAdminMenu.modeOf(node);
        }
        return null;
    }

    /**
     * 하위를 가진 **부모** 항목들(하위까지 재귀) — 어느 하위에도 속하지 않는 부모 아래 주소(문구 편집 `/settings/notification-messages/{id}` — 받는 사람 메뉴 둘이 같이 쓴다)를 부모의 것으로 보는 데 쓴다.
     * @param {readonly AdminMenuItem[]} items - 항목들.
     * @returns {AdminMenuItem[]} 부모 항목.
     */
    static parents(items: readonly AdminMenuItem[]): AdminMenuItem[] {
        return items.flatMap((item) => (item.children ? [item, ...UtilAdminMenu.parents(item.children)] : []));
    }

    /**
     * 메뉴에서 맨 처음 갈 수 있는 화면 항목의 주소 — 설정 메뉴로 들어갈 때 첫 화면을 고른다.
     * @param {readonly AdminMenuNode[]} nodes - 권한·모드로 거른 메뉴(`visible` 의 결과).
     * @returns {string | null} 첫 화면 항목의 주소. 항목이 없으면 null.
     */
    static firstTo(nodes: readonly AdminMenuNode[]): string | null {
        const first = nodes[0];
        if (!first) return null;
        return UtilAdminMenu.leaves(UtilAdminMenu.isGroup(first) ? first.items : [first])[0]?.to ?? null;
    }

    /**
     * 이 주소의 화면을 열 권한이 있는가 — 메뉴 항목에 걸린 권한을 본다. 메뉴에 없는 주소(내 정보 등)·권한이 안 걸린 항목은 열 수 있다.
     * @param {string} path - 주소의 경로(`route.path`).
     * @param {(code: string) => boolean} can - 그 권한 코드를 가졌는가.
     * @returns {boolean} 열 수 있으면 true.
     */
    static canOpen(path: string, can: (code: string) => boolean): boolean {
        for (const node of UtilAdminMenu.ITEMS) {
            const items = UtilAdminMenu.leaves(UtilAdminMenu.isGroup(node) ? node.items : [node]);
            const hit = items.find((item) => UtilAdminMenu.isActive(item, path));
            if (hit) return UtilAdminMenu.allowed(hit, can);
        }
        // 어느 화면 항목에도 없지만 부모 아래인 주소(문구 편집 등)는 부모의 권한으로 본다
        for (const node of UtilAdminMenu.ITEMS) {
            const parents = UtilAdminMenu.parents(UtilAdminMenu.isGroup(node) ? node.items : [node]);
            const hit = parents.find((item) => UtilAdminMenu.isActive(item, path));
            if (hit) return UtilAdminMenu.allowed(hit, can);
        }
        return true;
    }

    /**
     * 노드가 묶음인가.
     * @param {AdminMenuNode} node - 메뉴 노드.
     * @returns {boolean} 묶음이면 true.
     */
    static isGroup(node: AdminMenuNode): node is AdminMenuGroup {
        return 'items' in node;
    }

    /**
     * 사이드바·드로어가 그릴 덩어리로 나눈다 — 이어진 낱개 화면 항목은 한 덩어리(제목 없음), 묶음은 제목 있는 한 덩어리다.
     * @param {readonly AdminMenuNode[]} nodes - 나눌 메뉴(기본은 전체, 권한으로 거른 것은 `visible` 의 결과를 넣는다).
     * @returns {AdminMenuSection[]} 위에서 아래 차례의 덩어리.
     */
    static sections(nodes: readonly AdminMenuNode[] = UtilAdminMenu.ITEMS): AdminMenuSection[] {
        const out: AdminMenuSection[] = [];
        for (const node of nodes) {
            if (UtilAdminMenu.isGroup(node)) {
                out.push({ key: node.key, group: node, items: node.items });
                continue;
            }
            const last = out[out.length - 1];
            if (last && !last.group) (last.items as AdminMenuItem[]).push(node);
            else out.push({ key: node.key, group: null, items: [node] });
        }
        return out;
    }

    /**
     * 지금 주소가 이 항목의 화면인가 — 홈(`/`)은 정확히 같을 때만, 나머지는 하위 주소(`/members/12`)까지 같은 항목으로 본다.
     * 단 그 하위 주소를 **더 구체적인 다른 메뉴 항목**이 가지면(`/members` 아래의 `/members/nickname-images`, `/points` 아래의 `/points/policy`) 그 항목의 화면이다 —
     * 활성 표시가 둘로 갈라지지 않고, 열기 권한(`canOpen`)·모드(`modeOfPath`)도 그 항목의 것으로 본다.
     * @param {AdminMenuItem} item - 메뉴 항목.
     * @param {string} path - 지금 주소의 경로(`route.path`).
     * @returns {boolean} 그 항목이 활성이면 true.
     */
    static isActive(item: AdminMenuItem, path: string): boolean {
        if (item.to === '/') return path === '/';
        if (!UtilAdminMenu.under(path, item.to)) return false;
        return !UtilAdminMenu.allItems().some((other) => other.to.length > item.to.length && UtilAdminMenu.under(other.to, item.to) && UtilAdminMenu.under(path, other.to));
    }

    /**
     * 주소가 그 경로이거나 그 아래인가.
     * @param {string} path - 볼 주소.
     * @param {string} to - 메뉴 항목의 경로.
     * @returns {boolean} 같거나 `to/` 로 시작하면 true.
     */
    static under(path: string, to: string): boolean {
        return path === to || path.startsWith(`${to}/`);
    }

    /**
     * 메뉴의 모든 화면 항목(부모·하위 포함) — 더 구체적인 항목을 찾는 데 쓴다.
     * @returns {AdminMenuItem[]} 모든 항목.
     */
    static allItems(): AdminMenuItem[] {
        const walk = (items: readonly AdminMenuItem[]): AdminMenuItem[] => items.flatMap((item) => [item, ...(item.children ? walk(item.children) : [])]);
        return walk(UtilAdminMenu.ITEMS.flatMap((node) => (UtilAdminMenu.isGroup(node) ? node.items : [node])));
    }

    /**
     * 묶음 아래 항목 중 하나라도 활성인가 — 상단 메뉴 줄의 최상위 항목 표시에 쓴다.
     * @param {AdminMenuGroup} group - 메뉴 묶음.
     * @param {string} path - 지금 주소의 경로(`route.path`).
     * @returns {boolean} 하나라도 활성이면 true.
     */
    static isGroupActive(group: AdminMenuGroup, path: string): boolean {
        return group.items.some((item) => UtilAdminMenu.isItemActive(item, path));
    }

    /**
     * 이 항목이나 그 하위 중 하나라도 활성인가 — 하위가 있는 부모의 활성·펼침 판정에 쓴다.
     * @param {AdminMenuItem} item - 메뉴 항목.
     * @param {string} path - 지금 주소의 경로(`route.path`).
     * @returns {boolean} 하나라도 활성이면 true.
     */
    static isItemActive(item: AdminMenuItem, path: string): boolean {
        // 부모는 하위 주소에 있을 때, 또는 어느 하위에도 속하지 않는 부모 아래 주소(문구 편집 `/settings/notification-messages/{id}`)에 있을 때 활성이다
        return UtilAdminMenu.leaves([item]).some((leaf) => UtilAdminMenu.isActive(leaf, path)) || (Boolean(item.children) && UtilAdminMenu.isActive(item, path));
    }
}
