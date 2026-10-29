/**
 * 관리자 미리보기 — 관리자 테마 화면(admin-web `/settings/theme`)이 이 앱을 iframe 으로 `/?adminPreview=1` 로 띄우면 켜진다(사용자 결정 2026-10-28: 미리보기는 실제 공개 화면과 똑같아야 한다).
 *
 * - iframe 안이고 주소에 `adminPreview=1` 이 있을 때만 켠다. 메시지는 `runtimeConfig.public.adminOrigins`(쉼표로 여러 개, 개발 서버)와 서버가 머리에 넣은
 *   `<meta name="roboyam-admin-origin">`(빌드한 화면 — api 의 `AdminPreviewFrame`, 환경 값 `ADMIN_URL`)에서 온 것만 받는다 — 다른 곳의 메시지는 버린다.
 * - 받는 것(`roboyam:admin-preview`): 테마 id · 메뉴 설정(운영자 설정 모양 — 저장 전 값) · 홈 구성(배치 + 가젯 id·종류·제목·보이는 폭·내용 설정) · 가젯 페이지 머리(`/p/{key}` 를 띄웠을 때). 이 탭의 화면 상태만 바꾼다(서버에 아무것도 보내지 않는다).
 *   테마·메뉴는 `useTheme` 의 상태(`site-theme`·`site-menu-*`)를 덮고, 홈 구성은 `useAdminPreview().home` 에 두어 홈 화면이 덮어 그린다(`pages/index.vue`).
 * - 보내는 것: 준비됨(`…-ready` — 관리자가 이때 값을 보낸다) · 메뉴 자리를 눌렀다(`…-edit-menu` — 관리자가 메뉴 설정 drawer 를 연다).
 *   iframe 은 정해진 창 크기(관리자가 줄여 보인다)이고 안에서 스크롤한다 — 메뉴 붙이기 같은 스크롤 동작도 공개 사이트 그대로 보인다.
 * - 편집 중(`editing`)이면 홈 격자가 가젯 위에 손잡이를 얹고, 옮기기·크기·빼기를 관리자에게 알린다(`…-layout`, 계산·저장은 관리자 — `HomeGadgetGrid`).
 * - 미리보기 안에서는 다른 화면으로 가지 않는다 — 링크 누르기는 막고, 윗줄·메뉴·사이드 메뉴(`header`·`nav`·`.side-menu`)를 누르면 메뉴 설정을 열라고 알린다.
 * - 회원 보기 설정의 메뉴 위치는 쓰지 않는다(`useMenuPosition`) — 운영자가 보는 값이 공개 기본 화면이어야 한다.
 */
import UtilAdminPreview, { ADMIN_PREVIEW_EDIT_MENU, ADMIN_PREVIEW_READY } from '~/utils/util-admin-preview';
import UtilMenuPosition, {
    type SiteMenuOverflowSetting,
    type SiteMenuSetting,
    type SiteMenuStyle,
    type SiteMenuWidthSetting,
    type SiteTopItemsSetting,
} from '~/utils/util-menu-position';
import UtilTheme, { type ThemeId } from '~/utils/util-theme';

export default defineNuxtPlugin(() => {
    if (window.parent === window || new URLSearchParams(window.location.search).get('adminPreview') !== '1') return;

    // 관리자 주소 — 개발 서버는 runtimeConfig, 빌드한 화면은 서버가 이 응답 머리에 넣은 메타(ADMIN_URL — 서버가 같은 주소만 frame-ancestors 로 허락한다)
    const served = document.querySelector<HTMLMetaElement>('meta[name="roboyam-admin-origin"]')?.content ?? '';
    const allowed = [...String(useRuntimeConfig().public.adminOrigins ?? '').split(','), served]
        .map((origin) => origin.trim())
        .filter(Boolean);
    if (!allowed.length) return;

    const { active, editing, home, page } = useAdminPreview();
    active.value = true;
    openAdminPreviewChannel(allowed);

    // `useTheme` 와 같은 상태 — 운영자 설정 모양 그대로 덮는다(해석은 `useMenuPosition`·`useMenuStyle` 이 테마 기본과 함께 한다)
    const theme = useState<ThemeId | undefined>('site-theme');
    const menu = useState<SiteMenuSetting | undefined>('site-menu-position');
    const top = useState<SiteTopItemsSetting | undefined>('site-top-items');
    const bar = useState<string[] | undefined>('site-bar-groups');
    const width = useState<SiteMenuWidthSetting | undefined>('site-menu-width');
    const overflow = useState<SiteMenuOverflowSetting | undefined>('site-menu-overflow');
    const style = useState<SiteMenuStyle | undefined>('site-menu-style');

    window.addEventListener('message', (event: MessageEvent) => {
        if (!allowed.includes(event.origin)) return;
        const msg = UtilAdminPreview.read(event.data);
        if (!msg) return;
        pinAdminPreviewOrigin(event.origin);
        theme.value = UtilTheme.normalize(msg.theme);
        if (msg.menu) {
            menu.value = UtilMenuPosition.readSite(msg.menu.menuPosition);
            top.value = UtilMenuPosition.readSiteTopItems(msg.menu.topItems);
            bar.value = UtilMenuPosition.readBarGroupIds(msg.menu.barGroupIds);
            width.value = UtilMenuPosition.readSiteWidth(msg.menu.menuWidth);
            overflow.value = UtilMenuPosition.readSiteOverflow(msg.menu.menuOverflow);
            style.value = UtilMenuPosition.readSiteStyle(msg.menu.menuStyle);
        }
        home.value = msg.home;
        page.value = msg.page;
        editing.value = msg.editing;
    });

    // 미리보기 안에서는 화면을 옮기지 않는다 — 메뉴 자리는 메뉴 설정 열기, 그 밖의 링크는 막기
    document.addEventListener(
        'click',
        (event) => {
            const target = event.target instanceof Element ? event.target : null;
            if (!target) return;
            // 편집 중의 홈 격자 손잡이는 격자가 맡는다
            if (target.closest('[data-home-edit]')) return;
            if (target.closest('header, nav, .side-menu')) {
                event.preventDefault();
                event.stopPropagation();
                postToAdminPreview({ type: ADMIN_PREVIEW_EDIT_MENU });
                return;
            }
            if (target.closest('a')) {
                event.preventDefault();
                event.stopPropagation();
            }
        },
        true,
    );

    postToAdminPreview({ type: ADMIN_PREVIEW_READY });
});
