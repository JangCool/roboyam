<template>
    <!--
        상단 메뉴 줄 — 설정의 메뉴 위치가 "상단"일 때 상단 네비 바로 아래(2행)에 선다(사이드바는 없다). PrimeVue Menubar 를 쓴다.
        메뉴 데이터는 사이드바와 같다 — `UtilAdminMenu.ITEMS` 에서 지금 모드(`useAppMenu`)의 노드. 설정 메뉴일 때는 맨 앞(왼쪽)에 「메뉴로 돌아가기」 항목이 서고, 누르면 일반 메뉴의 첫 화면(대시보드)으로 간다. 묶음(예: 배치와 로그)은 최상위 항목이 되고 아래 화면 항목은 Menubar 가 팝업으로 잇는다(묶음 안 항목이 활성이면 최상위도 활성으로 표시).
        모바일(md 미만)에서는 만들지 않는다(햄버거 드로어가 맡는다).
        ⚠ `breakpoint` 는 셸의 모바일 경계(767px — `useLayoutPrefs` 의 `MOBILE_QUERY`)와 같아야 한다. 768px 로 두면 폭이 정확히 768px 일 때 셸은 데스크톱인데 Menubar 만 햄버거 모드가 되어 메뉴 목록이 숨는다.
    -->
    <div class="app-menu-top">
        <Menubar :model="model" breakpoint="767px" :aria-label="isSettings ? t('shell.settingsNav') : t('shell.mainNav')" class="app-menubar min-w-0 flex-1">
            <template #item="{ item, props, hasSubmenu, root }">
                <NuxtLink v-if="item.route" :to="item.route" class="app-menubar-item" :class="{ 'app-menubar-item--active': item.active, 'app-menubar-item--back': item.back }" :aria-current="item.active ? 'page' : undefined" @click="item.back ? setMode('normal') : undefined">
                    <i v-if="item.icon" :class="[item.icon, 'text-sm']" aria-hidden="true" />
                    <span class="truncate">{{ item.label }}</span>
                </NuxtLink>
                <!-- 묶음 — 주소가 없고 팝업을 연다. 최상위는 아래로, 팝업 안은 옆으로 이어진다 -->
                <a v-else class="app-menubar-item cursor-pointer" :class="{ 'app-menubar-item--active': item.active }" v-bind="props.action">
                    <i v-if="item.icon" :class="[item.icon, 'text-sm']" aria-hidden="true" />
                    <span class="truncate">{{ item.label }}</span>
                    <i v-if="hasSubmenu" class="pi ml-auto pl-2 text-[11px] opacity-60" :class="root ? 'pi-angle-down' : 'pi-angle-right'" aria-hidden="true" />
                </a>
            </template>
        </Menubar>
    </div>
</template>

<script setup lang="ts">
import UtilAdminMenu, { type AdminMenuItem } from '~/utils/util-admin-menu';

const { t } = useI18n();
const route = useRoute();
const { isSettings, nodes, setMode } = useAppMenu();

/** 화면 항목 하나를 Menubar 항목으로 — 하위가 있으면 이동하지 않는 부모이고 하위가 옆으로 열리는 팝업이다 */
function toModel(item: AdminMenuItem): Record<string, unknown> {
    return item.children
        ? { key: item.key, label: t(item.labelKey), icon: item.icon, active: UtilAdminMenu.isItemActive(item, route.path), items: item.children.map(toModel) }
        : { key: item.key, label: t(item.labelKey), icon: item.icon, route: item.to, active: UtilAdminMenu.isActive(item, route.path) };
}

/** 설정 메뉴 맨 앞의 「메뉴로 돌아가기」 — 주소는 일반 메뉴의 첫 화면(대시보드)이고, 누르면 모드도 일반으로 기억한다 */
const backItem = computed(() => (isSettings.value ? [{ key: 'back', label: t('shell.backToMenu'), icon: 'pi pi-arrow-left', route: '/', active: false, back: true }] : []));

/** Menubar 모델 — 이름은 번역이라 `computed`, 활성 표시는 주소를 따라 다시 계산된다. 지금 모드에서 권한이 없는 항목은 뺀다 */
const model = computed(() => [
    ...backItem.value,
    ...nodes.value.map((node) =>
        UtilAdminMenu.isGroup(node)
            ? {
                  key: node.key,
                  label: t(node.labelKey),
                  icon: node.icon,
                  active: UtilAdminMenu.isGroupActive(node, route.path),
                  items: node.items.map(toModel),
              }
            : { key: node.key, label: t(node.labelKey), icon: node.icon, route: node.to, active: UtilAdminMenu.isActive(node, route.path) },
    ),
]);
</script>
