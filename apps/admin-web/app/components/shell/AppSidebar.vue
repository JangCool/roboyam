<template>
    <!--
        데스크톱 사이드바 — PrimeVue 사이드바(아이콘 접힘 방식)를 쓴다. 자리(`SidebarSpacer`)가 폭을 고정하고 실제 패널(`SidebarAside`)이 그 위에 얹힌다.
          · 펼침/접힘은 상단 바 토글(`useLayoutPrefs().toggleSidebar`)이 정하고 기억한다. 접힌 모습이 레일(아이콘만)이다.
          · 설정의 "마우스 올리면 펼치기"가 켜져 있으면 레일에 마우스를 올릴 때 잠시 펼친다. 이때는 `overlay` 로 자리를 넓히지 않고 본문 위에 덮는다(본문 폭이 흔들리지 않는다).
        메뉴 모드(`useAppMenu`): 설정 메뉴일 때 위쪽 머리에 "설정"(아이콘 + 이름 — 접힌 레일에서는 아이콘만, 이름은 스크린리더에 남는다), 아래 고정 영역에 「메뉴로 돌아가기」를 둔다.
          일반 메뉴에는 머리도 하단 단추도 없다(설정으로 들어가는 길은 프로필 메뉴의 「설정」이다). 머리는 짧게 페이드하며 나타난다(`prefers-reduced-motion` 존중).
        설정의 "사이드바 위치"(왼쪽/오른쪽)를 따른다 — PrimeVue 의 `side` 로 자리·패널·경계선이 함께 반대편에 서고, 본문보다 뒤(`order-last`)에 놓는다. 모바일(md 미만)에서는 만들지 않고 햄버거 드로어(`AppDrawer`)가 같은 메뉴를 맡는다.
    -->
    <Sidebar
        id="app-sidebar"
        collapsible="icon"
        :side="sidebarSide"
        :class="{ 'order-last': sidebarSide === 'right' }"
        :open="sidebarOpen"
        :overlay="hoverOverlay"
        width="var(--w-sidebar)"
        icon-width="var(--w-rail)"
        :hide-on-outside-click="false"
        :dismissable="false"
        @pointerenter="hovered = true"
        @pointerleave="hovered = false"
    >
        <SidebarSpacer />
        <SidebarAside>
            <SidebarPanel>
                <Transition name="menu-fade">
                    <SidebarHeader v-if="isSettings" class="border-b border-(--line)">
                        <div class="flex h-8 items-center gap-2 px-2.5" :class="{ 'justify-center px-0!': !sidebarOpen }" :title="!sidebarOpen ? t('shell.settings') : undefined">
                            <i class="pi pi-cog w-4 shrink-0 text-center text-muted-color" aria-hidden="true" />
                            <span :class="sidebarOpen ? 'truncate text-sm font-medium' : 'sr-only'">{{ t('shell.settings') }}</span>
                        </div>
                    </SidebarHeader>
                </Transition>
                <SidebarContent>
                    <!-- `AppMenuList` 가 묶음마다 `SidebarGroup` 을 이미 그린다 — 여기서 또 감싸면 가로 여백이 두 겹이 되어 레일(48px)에서 아이콘이 오른쪽 선에 붙는다 -->
                    <AppMenuList :rail="!sidebarOpen" />
                </SidebarContent>
                <!-- 설정 메뉴의 하단 고정 영역 — 일반 메뉴로 나오는 문. 메뉴 목록에 섞지 않아 스크롤해도 늘 같은 자리에 있다 -->
                <SidebarFooter v-if="isSettings" class="border-t border-(--line)">
                    <SidebarMenu>
                        <SidebarMenuItem>
                            <SidebarMenuButton :title="!sidebarOpen ? t('shell.backToMenu') : undefined" @click="backToMenu">
                                <i class="pi pi-arrow-left w-4 shrink-0 text-center" aria-hidden="true" />
                                <span>{{ t('shell.backToMenu') }}</span>
                            </SidebarMenuButton>
                        </SidebarMenuItem>
                    </SidebarMenu>
                </SidebarFooter>
            </SidebarPanel>
        </SidebarAside>
    </Sidebar>
</template>

<script setup lang="ts">
const { t } = useI18n();
const { hovered, sidebarOpen, hoverOverlay, sidebarSide } = useLayoutPrefs();
const { isSettings, backToMenu } = useAppMenu();
</script>

<style scoped>
/* 설정 머리 — 목록과 같은 짧은 페이드 */
.menu-fade-enter-active,
.menu-fade-leave-active {
    transition: opacity 0.11s ease;
}
.menu-fade-enter-from,
.menu-fade-leave-to {
    opacity: 0;
}
@media (prefers-reduced-motion: reduce) {
    .menu-fade-enter-active,
    .menu-fade-leave-active {
        transition-duration: 0.01s;
    }
}
</style>
