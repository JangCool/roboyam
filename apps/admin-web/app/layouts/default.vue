<template>
    <!--
        관리자 셸 —
          상단 네비(`AppTopbar`, 높이 고정)
          (메뉴 위치가 상단이면) 메뉴 줄(`AppMenuTop`)
          그 아래 `.app-body`: 사이드바(`AppSidebar`, 데스크톱 · 왼쪽/오른쪽) | 본문(`.app-main` = 페이지 툴바 슬롯 + `.app-content`)
          모바일(md 미만)은 설정과 무관하게 사이드바·메뉴 줄 대신 ☰ 로 여는 드로어(`AppDrawer`). 우측(설정에 따라 좌측) 서랍 둘(알림 · 화면 설정)은 셸에 붙는다.
        메뉴 항목은 `utils/util-admin-menu.ts` 한 곳에서 정의한다.
        `toolbar` 슬롯: 페이지 위쪽 툴바 자리. 비어 있으면 자리를 차지하지 않는다.
        `data-menu` 는 CSS 가 읽으라고 붙인다 — 메뉴 줄이 한 줄을 차지하면 `--h-appmenu` 가 그 높이가 되어, 세로를 꽉 채우는 자리가 `calc(100% - var(--h-appmenu))` 처럼 빼 쓴다.
        글자 크기 배율은 `.app-zoom`(본문 안쪽 래퍼)에만 걸린다 — 상단 네비·사이드바·서랍은 배율을 받지 않는다.
    -->
    <div class="app-shell" :data-menu="hasMenuTop ? 'top' : 'sidebar'">
        <a
            href="#main-content"
            class="sr-only focus:not-sr-only focus:fixed focus:left-2 focus:top-2 focus:z-[3000] focus:rounded focus:bg-surface-0 focus:px-3 focus:py-2 focus:text-sm focus:shadow-lg dark:focus:bg-surface-900"
        >
            {{ t('shell.skipToContent') }}
        </a>

        <!-- 메뉴 모드가 바뀌면(일반 메뉴 ↔ 설정 메뉴) 스크린리더에 알린다 -->
        <p class="sr-only" role="status" aria-live="polite">{{ modeNotice }}</p>

        <AppTopbar @open-menu="menuOpen = true" @open-settings="settingsOpen = true" />
        <AppMenuTop v-if="hasMenuTop" />

        <SidebarLayout class="app-body">
            <AppSidebar v-if="hasSidebar" />
            <SidebarMain as="main" id="main-content" class="app-main">
                <slot name="toolbar" />
                <div :id="SECTION_NAV_SCROLL_ID" class="app-content" :class="{ 'app-content--boxed': prefs.contentWidth === 'boxed' }">
                    <div class="app-content-grid" :class="{ 'app-content-grid--nav': navVisible }">
                        <div class="min-w-0">
                            <!-- 좁을 때의 "구역 이동" 선택 상자(넓으면 CSS 가 숨기고 오른쪽 열의 목록이 대신한다) -->
                            <SectionNav variant="select" />
                            <div class="app-zoom">
                                <!-- 메뉴 권한이 없는 화면에 주소로 들어온 경우 — 화면을 그리지 않고 권한 없음을 알린다(막는 것은 서버다) -->
                                <ErrorState v-if="forbidden" code="403" :title="t('shell.forbiddenTitle')" :description="t('shell.forbiddenBody')">
                                    <Button type="button" :label="t('shell.goHome')" icon="pi pi-home" severity="secondary" variant="outlined" @click="navigateTo('/')" />
                                </ErrorState>
                                <slot v-else />
                            </div>
                        </div>
                        <!-- 우측 구역 내비 — 페이지가 그리지 않는다. 구획(`AppGadget` 의 `anchor`)이 스스로 등록하면 여기에 나타난다(2개 미만이면 숨음) -->
                        <aside v-if="navVisible" class="section-nav-col">
                            <SectionNav />
                        </aside>
                    </div>
                </div>
            </SidebarMain>
        </SidebarLayout>

        <AppDrawer v-if="isMobile" v-model:visible="menuOpen" @open-settings="settingsOpen = true" />
        <NotificationDrawer />
        <ThemeSettingsDrawer v-model:visible="settingsOpen" />
        <AppConfirm />
        <AppToaster />

        <!--
            새 알림 토스트(`useNotification().receive`) — 종 바로 아래(오른쪽 위)에 뜬다. 일반 토스트(`AppToaster`, 아래 가운데)와 자리가 달라 겹치지 않는다.
            본문은 알림 제목 줄들과 「알림 이력 보기」 단추. 마우스를 올려 두는 동안은 꺼지지 않는다.
        -->
        <Toast :group="NOTIFICATION_TOAST_GROUP" position="top-right" :on-mouse-enter="holdToastWhileReading" :on-mouse-leave="holdToastWhileReading" :pt="{ root: { style: 'top: calc(var(--h-topbar) + 0.5rem)' } }">
            <template #message="{ message }">
                <span class="p-toast-message-icon pi pi-bell" aria-hidden="true" />
                <div class="p-toast-message-text">
                    <span class="p-toast-summary">{{ message.summary }}</span>
                    <div class="p-toast-detail">
                        <div v-for="(line, i) in toastDetail(message).lines" :key="i" class="truncate">{{ line }}</div>
                        <div v-if="toastDetail(message).summary" class="mt-0.5 line-clamp-2">{{ toastDetail(message).summary }}</div>
                        <div v-if="toastDetail(message).more" class="text-xs opacity-80">{{ toastDetail(message).more }}</div>
                    </div>
                    <button type="button" class="mt-1 self-start text-xs font-medium underline-offset-2 hover:underline focus-visible:underline" @click="openFromToast">
                        {{ t('notification.toastOpen') }}
                    </button>
                </div>
            </template>
        </Toast>
    </div>
</template>

<script setup lang="ts">
import type { NotificationToastDetail } from '~/composables/shell/useNotification.types';
import UtilAdminMenu from '~/utils/util-admin-menu';

const { t } = useI18n();
const { prefs, isMobile, hasMenuTop, hasSidebar } = useLayoutPrefs();
const { ensureLoaded, startWatching, open: notificationOpen } = useNotification();
const sectionNav = useSectionNav();
const navVisible = sectionNav.visible;
const route = useRoute();
const toast = useToast();
const { mode, follow } = useAppMenu();
const { can } = useAdminPermission();
/** 지금 주소의 화면을 열 권한이 없는가(메뉴 항목에 걸린 권한 — `UtilAdminMenu.canOpen`) */
const forbidden = computed(() => !UtilAdminMenu.canOpen(route.path, can));

// 메뉴 모드 — 메뉴에 있는 주소로 가면 그 모드를 "마지막 모드"로 적는다(메뉴에 없는 /me 등에서 직전 모드를 유지하려고). 셸에서 한 번만 건다 → useAppMenu
watch(
    () => route.path,
    (path) => follow(path),
    { immediate: true },
);

// 모드가 바뀌었을 때만 알린다(처음 뜰 때는 비워 둔다)
const modeNotice = ref('');
watch(mode, (next) => {
    modeNotice.value = next === 'settings' ? t('shell.settingsNav') : t('shell.mainNav');
});

const menuOpen = ref(false);
const settingsOpen = ref(false);

const toastDetail = (message: { detail?: unknown }) => message.detail as NotificationToastDetail;

/**
 * 마우스를 얹고 있는 동안에는 토스트가 안 꺼진다. 줄이 여럿이고 아래에 단추가 있어, 읽으려고 가져가는 사이 사라지면 단추를 누를 수가 없다.
 * ⚠ PrimeVue 는 이 두 값이 **있을 때만** 시계를 멈춘다(`ToastMessage` 가 `if (this.onMouseEnter)` 로 본다). 할 일이 없어도 자리는 채워야 한다.
 */
function holdToastWhileReading() {
    /* 자리를 채우는 것이 일이다 — 위 설명 참고 */
}

/** 토스트의 「알림 이력 보기」 — 서랍에 다 있으므로 떠 있는 알림 토스트는 걷고 서랍을 연다 */
function openFromToast() {
    toast.removeGroup(NOTIFICATION_TOAST_GROUP);
    notificationOpen.value = true;
}

// 종의 안 읽은 수를 위해 셸이 뜨면 알림을 한 번 받고, 새 알림 도착을 받기 시작한다(셸이 내려가면 끝낸다)
let stopWatching: (() => void) | undefined;
onMounted(() => {
    void ensureLoaded();
    stopWatching = startWatching();
});
onBeforeUnmount(() => stopWatching?.());

// 우측 구역 내비 — 구획은 `AppGadget` 이 스스로 등록한다. 셸은 현재 구획 추적(본문 스크롤 영역 `#app-scroll`)을 켜고, 화면이 바뀌면 비운다
onMounted(() => sectionNav.start());
onBeforeUnmount(() => sectionNav.stop());
watch(
    () => route.path,
    () => sectionNav.reset(),
);

// 넓은 화면으로 넘어오면 열려 있던 모바일 드로어를 닫는다
watch(isMobile, () => (menuOpen.value = false));
</script>
