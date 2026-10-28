<template>
    <!-- 상단 가로 메뉴 안의 하위 그룹 — 제목 단추를 누르면 항목이 드롭다운으로 펼쳐진다. 안에 지금 있는 곳이 있으면 이 단추도 활성(아래 줄)이다.
         가로 줄(`overflow-x-auto`) 안에 두면 드롭다운이 잘리므로 PrimeVue `Popover` 가 본문으로 띄운다 — 바깥 누르기·Esc 로 닫히고 키보드로 열고 닫는다 -->
    <button
        type="button"
        :class="buttonClass || UtilMenu.barItemClass(entry.active)"
        :data-active="entry.active ? 'true' : undefined"
        aria-haspopup="true"
        :aria-expanded="open"
        :data-testid="`top-menu-dropdown-${entry.key}`"
        @click="toggle"
    >
        <span>{{ localizedName(entry.group.label, entry.group.nameEn) }}</span>
        <i class="pi pi-angle-down text-xs" :class="open ? 'rotate-180' : ''" aria-hidden="true" />
    </button>

    <Popover ref="popover" :aria-label="localizedName(entry.group.label, entry.group.nameEn)" class="min-w-44 max-w-[calc(100vw-1rem)]" :pt="{ content: { class: 'p-0!' } }" @show="open = true" @hide="open = false">
        <div class="max-h-[70dvh] overflow-y-auto py-1 text-sm">
            <template v-for="block in entry.blocks" :key="block.key">
                <!-- 더 아래 그룹의 소제목 — 들여쓴다 -->
                <p v-if="block.group" class="m-0 px-4 pb-0.5 pt-2 text-xs text-muted-color" :style="indent(block.level)">{{ localizedName(block.group.label, block.group.nameEn) }}</p>
                <ul class="m-0 list-none p-0">
                    <li v-for="row in block.items" :key="row.key">
                        <AppMenuLink
                            :node="row.node"
                            :class="UtilMenu.listItemClass(row.active)"
                            :style="indent(row.level)"
                            :aria-current="row.active ? 'page' : undefined"
                            @click="close"
                        >
                            <span class="flex min-w-0 items-center">
                                <i :class="UtilMenu.dotClass(row.color)" :style="UtilBoard.dotStyle(row.color)" aria-hidden="true" :data-testid="row.color ? 'menu-dot-color' : 'menu-dot-slot'" />
                                <span class="truncate">{{ localizedName(row.node.label, row.node.nameEn) }}</span>
                            </span>
                            <small v-if="row.notice" class="shrink-0 text-xs text-muted-color">{{ t('menu.investmentNotice') }}</small>
                        </AppMenuLink>
                    </li>
                </ul>
            </template>
        </div>
    </Popover>
</template>

<script setup lang="ts">
import type { MenuBarEntry } from '~/utils/util-menu';
import UtilBoard from '~/utils/util-board';
import UtilMenu from '~/utils/util-menu';

/** 팝오버의 열고 닫기 — PrimeVue `Popover` 가 바깥 클릭·Esc·초점 가두기를 맡는다 */
interface PopoverHandle {
    toggle: (event: Event) => void;
    hide: () => void;
}

interface Props {
    /** 드롭다운 칸 하나 — 하위 그룹과 그 안의 항목 덩어리들 */
    entry: Extract<MenuBarEntry, { kind: 'dropdown' }>;
    /** 제목 단추의 클래스 — 테마 셸이 자기 탭 모양을 줄 때만(활성 여부는 `entry.active` 로 셸이 고른다). 비우면 상단 가로 막대 칸 모양(`UtilMenu.barItemClass`) */
    buttonClass?: string;
}

const props = withDefaults(defineProps<Props>(), { buttonClass: '' });

const route = useRoute();
const { t } = useI18n();
// 메뉴 이름은 운영자가 만든 동적 이름이다 — `label`(한국어)과 선택 영문명 `nameEn` 을 지금 언어로 고른다
const { localizedName } = useLocalizedName();
const popover = ref<PopoverHandle | null>(null);
const open = ref(false);

function toggle(event: Event) {
    popover.value?.toggle(event);
}

function close() {
    popover.value?.hide();
}

/** 더 아래 그룹 안(소제목·항목)을 들여쓴다 — 단계마다 0.75rem. 드롭다운 직속 항목(0단계)은 그대로다 */
function indent(level: number): Record<string, string> | undefined {
    return level > 0 ? { paddingLeft: `${1 + level * 0.75}rem` } : undefined;
}

// 다른 화면으로 가면 닫는다(항목을 눌러 이동할 때 포함)
watch(
    () => route.fullPath,
    () => close(),
);
</script>
