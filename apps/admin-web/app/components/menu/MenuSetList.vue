<template>
    <!-- 메뉴 그룹 목록 — 줄마다 이름 · 코드 · 위치 딱지 · 노드 수 · 사용 여부 · 기본 그룹 표시. 누르면 그 그룹을 연다 -->
    <ul class="m-0 flex list-none flex-col gap-1 p-0" :aria-label="t('menuConfig.setsTitle')">
        <li v-for="set in sets" :key="set.key">
            <button
                type="button"
                class="flex w-full flex-col gap-1 rounded-lg border px-3 py-2 text-left focus-visible:outline-2 focus-visible:outline-primary"
                :class="set.key === selectedKey ? 'border-primary bg-primary-50 dark:bg-primary-950/40' : 'border-(--line) hover:bg-surface-50 dark:hover:bg-surface-800'"
                :aria-current="set.key === selectedKey ? 'true' : undefined"
                @click="emit('select', set.key)"
            >
                <span class="flex min-w-0 items-center gap-1.5">
                    <span class="min-w-0 truncate text-[13px] font-medium" :class="set.enabled ? '' : 'text-muted-color line-through'">{{ localizedName(set.name, set.nameEn) }}</span>
                    <i v-if="set.system" v-tooltip.top="t('menuConfig.systemSet')" class="pi pi-lock text-[10px] text-muted-color" :aria-label="t('menuConfig.systemSet')" />
                </span>
                <span class="flex flex-wrap items-center gap-1.5">
                    <code class="text-[11px] text-muted-color">{{ set.key }}</code>
                    <MenuTag :value="t(`menuConfig.placement.${set.placement}`)" :severity="set.placement === 'SIDE' ? 'info' : 'secondary'" />
                    <MenuTag v-if="!set.enabled" :value="t('menuConfig.setDisabled')" severity="warn" />
                    <span class="ml-auto text-[11px] tabular-nums text-muted-color">{{ t('menuConfig.nodeCount', { n: set.nodeCount }) }}</span>
                </span>
            </button>
        </li>
    </ul>
</template>

<script setup lang="ts">
import type { MenuSet } from '~/composables/menu/api/useMenuApi.types';

interface Props {
    sets: MenuSet[];
    selectedKey?: string | null;
}

withDefaults(defineProps<Props>(), { selectedKey: null });
const emit = defineEmits<{ (e: 'select', key: string): void }>();

const { t } = useI18n();
const { localizedName } = useLocalizedName();
</script>
