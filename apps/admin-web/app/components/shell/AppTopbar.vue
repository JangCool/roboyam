<template>
    <!--
        상단 네비 — 높이 고정. 왼쪽 브랜드 칸(왼쪽 사이드바일 때 폭이 사이드바에 맞춰 움직이고 세로 구분선이 본문까지 이어진다) · 가운데 메뉴 검색 · 오른쪽 끝 단추들.
        사이드바가 오른쪽이면 브랜드 칸은 제 폭만 쓰고(묶일 사이드바가 이쪽에 없다) 사이드바 토글이 오른쪽 끝으로 간다. 메뉴 위치가 상단이면 사이드바가 없어 토글도 없다.
        배경은 선택한 강조색을 따른다(`--bar-bg` · `--bar-fg`, `main.css` 의 `.app-topbar`). 사이트 이름(`SiteBrand`)은 서버에서 받은 값이다.
        모바일(md 미만)은 ☰ · 브랜드 · 검색/알림/프로필로 줄이고, 검색은 눌러야 상단 바 전체를 덮어 열린다.
    -->
    <header class="app-topbar">
        <!-- 모바일에서 검색을 연 상태 — 상단 바 전체가 검색 칸이 된다 -->
        <template v-if="isMobile && searchOpen">
            <div class="flex min-w-0 flex-1 items-center gap-1 px-2">
                <AppSearch autofocus class="flex-1" @done="searchOpen = false" />
                <Button type="button" icon="pi pi-times" variant="text" rounded severity="secondary" :aria-label="t('shell.searchClose')" @click="searchOpen = false" />
            </div>
        </template>

        <template v-else>
            <div class="app-brand" :class="{ 'app-brand--linked': brandLinked, 'app-brand--rail': brandLinked && isRail }" :style="{ width: brandWidth }">
                <Button v-if="isMobile" type="button" icon="pi pi-sidebar" variant="text" rounded severity="secondary" class="shrink-0" :aria-label="t('shell.openMenu')" aria-haspopup="dialog" @click="emit('open-menu')" />

                <NuxtLink to="/" class="app-brand-link" :aria-label="t('shell.homeLink', { site: siteLabel })">
                    <!-- 레일(사이드바 폭)에는 이름이 들어가지 않는다 — 올린 로고가 있으면 로고, 없으면 이름의 첫 글자만 -->
                    <Avatar v-if="brandLinked && isRail" :label="siteInitial" :image="siteLogoUrl ?? undefined" shape="square" :class="{ 'avatar-primary': !siteLogoUrl }" aria-hidden="true" />
                    <SiteBrand v-else />
                </NuxtLink>

                <!-- 왼쪽 사이드바가 펼쳐져 있으면 토글이 브랜드 칸 안 오른쪽 끝에 있다 -->
                <Button v-if="brandLinked && !isRail" type="button" icon="pi pi-sidebar" variant="text" rounded severity="secondary" class="ml-auto shrink-0" :aria-label="t('shell.collapseSidebar')" :aria-expanded="true" @click="toggleSidebar" />
            </div>

            <div class="flex min-w-0 flex-1 items-center gap-1 px-2 md:px-4">
                <!-- 왼쪽 사이드바가 접혀 있으면 토글이 구분선 밖으로 나와 검색 왼쪽에 붙는다 -->
                <Button v-if="brandLinked && isRail" type="button" icon="pi pi-sidebar" variant="text" rounded severity="secondary" class="shrink-0" :aria-label="t('shell.expandSidebar')" :aria-expanded="false" @click="toggleSidebar" />

                <AppSearch v-if="!isMobile" class="mx-auto" />

                <!-- 항상 오른쪽 끝 -->
                <div class="ml-auto flex shrink-0 items-center gap-1">
                    <Button v-if="isMobile" type="button" icon="pi pi-search" variant="text" rounded severity="secondary" :aria-label="t('shell.searchOpen')" @click="searchOpen = true" />

                    <Button
                        v-if="!isMobile"
                        type="button"
                        :icon="prefs.theme === 'dark' ? 'pi pi-sun' : 'pi pi-moon'"
                        variant="text"
                        rounded
                        severity="secondary"
                        :aria-label="prefs.theme === 'dark' ? t('shell.toLight') : t('shell.toDark')"
                        @click="toggleTheme"
                    />

                    <!--
                        알림 종 — 누르면 서랍이 열린다. **뱃지(숫자)를 눌러도 열려야 한다**: 뱃지가 종 모서리를 물고 있어 숫자만 조준해 눌러도 아무 일이 없으면 안 된다.
                        그래서 단추가 아니라 감싼 쪽(OverlayBadge)이 누름을 받는다 — 종·숫자 어디를 눌러도, 키보드 Enter 도(단추의 click 이 올라온다) 같은 자리에서 열린다.
                        단추는 이름표(aria-label)와 키보드 초점을 맡는다.
                    -->
                    <OverlayBadge v-if="unread > 0" :value="unread > 99 ? '99+' : String(unread)" severity="danger" size="small" class="badge-on-icon" @click="notificationOpen = true">
                        <Button type="button" icon="pi pi-bell" variant="text" rounded severity="secondary" :aria-label="bellLabel" />
                    </OverlayBadge>
                    <Button v-else type="button" icon="pi pi-bell" variant="text" rounded severity="secondary" :aria-label="bellLabel" @click="notificationOpen = true" />

                    <Button v-if="!isMobile" type="button" icon="pi pi-palette" variant="text" rounded severity="secondary" :aria-label="t('shell.displaySettings')" @click="emit('open-settings')" />

                    <button v-if="user" type="button" class="app-profile" :aria-label="t('shell.profileMenu')" aria-haspopup="dialog" @click="profile?.toggle($event)">
                        <UserAvatar :name="user.nickname" />
                        <span class="hidden max-w-40 truncate text-sm font-medium md:block">{{ user.nickname }}</span>
                        <RoleBadge class="hidden md:inline-block" :role="user.role" />
                        <i class="pi pi-angle-down hidden text-xs text-muted-color md:block" aria-hidden="true" />
                    </button>

                    <!-- 오른쪽 사이드바의 토글은 사이드바가 있는 쪽(오른쪽 끝)에 둔다 -->
                    <Button
                        v-if="hasSidebar && sidebarSide === 'right'"
                        type="button"
                        icon="pi pi-sidebar"
                        variant="text"
                        rounded
                        severity="secondary"
                        :aria-label="isRail ? t('shell.expandSidebar') : t('shell.collapseSidebar')"
                        :aria-expanded="!isRail"
                        @click="toggleSidebar"
                    />
                </div>
            </div>
        </template>
    </header>
    <ProfilePopover ref="profile" />
</template>

<script setup lang="ts">
const emit = defineEmits<{ 'open-menu': []; 'open-settings': [] }>();

const { t } = useI18n();
const { user, siteLabel, siteLogoUrl } = useAdminAuth();
const { prefs, isMobile, isRail, hasSidebar, sidebarSide, brandLinked, brandWidth, toggleSidebar, toggleTheme } = useLayoutPrefs();
const { unread, open: notificationOpen } = useNotification();

const profile = useTemplateRef('profile');
/** 모바일에서 검색을 열었는가 */
const searchOpen = ref(false);

/** 사이트 이름의 첫 글자(레일에 보이는 로고 대신) */
const siteInitial = computed(() => (Array.from(siteLabel.value)[0] ?? '?').toUpperCase());

const bellLabel = computed(() => (unread.value > 0 ? t('shell.notificationsUnread', { count: unread.value > 99 ? '99+' : unread.value }) : t('shell.notifications')));

// 모바일 → 데스크톱으로 넘어오면 열려 있던 모바일 검색을 닫는다
watch(isMobile, () => (searchOpen.value = false));
</script>
