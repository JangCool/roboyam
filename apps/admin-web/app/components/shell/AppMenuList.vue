<template>
    <!--
        관리 메뉴(세로) — 데스크톱 사이드바(`AppSidebar`)와 모바일 드로어(`AppDrawer`)가 같은 데이터(`UtilAdminMenu.ITEMS` 에서 지금 모드(`useAppMenu`)의 노드)·같은 모양을 쓴다.
        모드가 바뀌면(일반 메뉴 ↔ 설정 메뉴) 목록이 짧게 페이드한다 — `key` 가 모드라 목록 전체가 갈린다. 움직임을 줄여 달라는 설정(`prefers-reduced-motion`)에서는 전환을 거의 없앤다.
        PrimeVue 사이드바 메뉴(SidebarMenu · SidebarMenuItem · SidebarMenuButton)를 쓰고, 버튼의 `as` 를 NuxtLink 로 바꿔 이동을 맡긴다.
        레일(접힌 사이드바)에서는 PrimeVue 가 버튼을 아이콘 폭으로 줄이고 이름은 잘린다 — 무엇인지 알 수 있게 `title` 로 이름을 단다.
        아이콘(`icon`)은 선택이다 — 있으면 그리고 없으면 자리(폭)도 만들지 않고 이름 글자만 그린다(하위 항목은 아이콘이 없다. 들여쓰기는 PrimeVue 하위 목록이 주는 것을 그대로 쓰고 아이콘 글자 시작에 맞추지 않는다 — hamonica3 `AppSidebarMenuNode` 도 단계마다 들여쓰기만 늘리고 아이콘 유무로 정렬을 맞추지 않는다).
        아이콘이 없는 최상위 항목은 레일(아이콘만)에 그릴 수 없어 레일에서 숨고(대체 표시는 두지 않는다), 하위 항목은 레일에서 그리지 않는다 — 아이콘 있는 부모만 보이고 누르면 사이드바가 펼쳐진다.
        하위 항목(`children`, 예: 역할 관리 › 회원 역할)이 있는 항목은 PrimeVue 의 접히는 메뉴 항목(`SidebarMenuItem collapsible` + `SidebarMenuSub`)이다 — 눌러도 이동하지 않고 펼치고 접는다. 지금 주소가 하위 항목이면 부모가 펼쳐진 채 활성으로 보이고,
        레일(접힌 사이드바)에서 부모 아이콘을 누르면 사이드바가 펼쳐지며 그 하위가 열린다(hamonica3 `AppSidebarMenuNode` 의 `@expand` 방식).
        묶음(hamonica3 와 같은 방식)은 눌리지 않는 구획 제목(SidebarGroupLabel) 아래에 화면 항목을 같은 줄 높이로 늘어놓는다. 레일에서는 제목을 그리지 않고 항목 아이콘만 남는다.
    -->
    <Transition name="menu-fade" mode="out-in">
        <nav :key="mode" :aria-label="isSettings ? t('shell.settingsNav') : t('shell.mainNav')">
            <SidebarGroup v-for="section in sections" :key="section.key">
                <SidebarGroupLabel v-if="section.group && !rail" :id="labelIds[section.key]">{{ t(section.group.labelKey) }}</SidebarGroupLabel>
                <SidebarGroupContent>
                    <SidebarMenu :aria-labelledby="section.group && !rail ? labelIds[section.key] : undefined">
                        <template v-for="item in section.items" :key="item.key">
                            <!-- 하위가 있는 항목 — 펼치고 접는다 -->
                            <SidebarMenuItem v-if="item.children && (!rail || item.icon)" collapsible :open="isOpen(item.key)" @update:open="(value: boolean) => (opened[item.key] = value)">
                                <SidebarMenuButton :is-active="UtilAdminMenu.isItemActive(item, route.path)" :title="rail ? t(item.labelKey) : undefined" :aria-controls="`${idBase}-${item.key}-sub`" @click="onParentClick(item)">
                                    <i v-if="item.icon" :class="[item.icon, 'w-4 shrink-0 text-center']" aria-hidden="true" />
                                    <span>{{ t(item.labelKey) }}</span>
                                    <i v-if="!rail" class="pi ml-auto text-[11px] opacity-60" :class="isOpen(item.key) ? 'pi-chevron-down' : 'pi-chevron-right'" aria-hidden="true" />
                                </SidebarMenuButton>
                                <SidebarMenuSub v-if="!rail" :id="`${idBase}-${item.key}-sub`">
                                    <SidebarMenuSubItem v-for="child in item.children" :key="child.key">
                                        <SidebarMenuSubButton :as="NuxtLink" :to="child.to" :is-active="isActive(child)" :aria-current="isActive(child) ? 'page' : undefined" @click="emit('navigate')">
                                            <i v-if="child.icon" :class="[child.icon, 'w-4 shrink-0 text-center']" aria-hidden="true" />
                                            <span>{{ t(child.labelKey) }}</span>
                                        </SidebarMenuSubButton>
                                    </SidebarMenuSubItem>
                                </SidebarMenuSub>
                            </SidebarMenuItem>
                            <SidebarMenuItem v-else-if="!rail || item.icon">
                                <SidebarMenuButton :as="NuxtLink" :to="item.to" :is-active="isActive(item)" :aria-current="isActive(item) ? 'page' : undefined" :title="rail ? t(item.labelKey) : undefined" @click="emit('navigate')">
                                    <i v-if="item.icon" :class="[item.icon, 'w-4 shrink-0 text-center']" aria-hidden="true" />
                                    <span>{{ t(item.labelKey) }}</span>
                                </SidebarMenuButton>
                            </SidebarMenuItem>
                        </template>
                    </SidebarMenu>
                </SidebarGroupContent>
            </SidebarGroup>
        </nav>
    </Transition>
</template>

<script setup lang="ts">
import UtilAdminMenu, { type AdminMenuItem } from '~/utils/util-admin-menu';

interface Props {
    /** 접힌 사이드바(아이콘만)인가 */
    rail?: boolean;
}

const props = withDefaults(defineProps<Props>(), { rail: false });
const emit = defineEmits<{ navigate: [] }>();

const { t } = useI18n();
const NuxtLink = resolveComponent('NuxtLink');
const route = useRoute();
const idBase = useId();

const { mode, isSettings, nodes } = useAppMenu();
/** 지금 모드에서 권한이 없는 항목은 빼고 나눈다 — 로그인한 운영자나 모드가 바뀌면 다시 계산된다 */
const sections = computed(() => UtilAdminMenu.sections(nodes.value));
/** 구획 제목의 id — 항목 목록이 제목으로 이름을 얻게 한다(`aria-labelledby`). 사이드바와 드로어가 함께 떠도 겹치지 않게 `useId` 로 만든다 */
const labelIds = Object.fromEntries(UtilAdminMenu.sections().map((section) => [section.key, `${idBase}-${section.key}`]));

const isActive = (item: AdminMenuItem) => UtilAdminMenu.isActive(item, route.path);

const { toggleSidebar, expanded } = useLayoutPrefs();

/** 하위가 있는 항목의 펼침 — 키(`item.key`)별. 지금 주소가 하위 항목이면 그 부모를 펼친다(주소가 바뀔 때마다 다시 본다) */
const opened = reactive<Record<string, boolean>>({});
const isOpen = (key: string) => opened[key] === true;
watch(
    () => [route.path, nodes.value] as const,
    () => {
        for (const section of sections.value) {
            for (const item of section.items) if (item.children && UtilAdminMenu.isItemActive(item, route.path)) opened[item.key] = true;
        }
    },
    { immediate: true },
);

/** 레일에서 부모를 누르면 사이드바를 펼치고 그 하위를 연다 — 접힌 채로는 하위를 보여 줄 자리가 없다. 부모의 펼침 토글(PrimeVue)이 먼저 돌므로 여기서 마지막에 열림으로 못 박는다 */
function onParentClick(item: AdminMenuItem): void {
    if (!props.rail) return;
    if (!expanded.value) toggleSidebar();
    opened[item.key] = true;
}
</script>

<style scoped>
/* 모드 전환 — 나가고(110ms) 들어오는(110ms) 짧은 페이드. 겹치지 않고 차례로(`out-in`) 갈아 끼운다 */
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
