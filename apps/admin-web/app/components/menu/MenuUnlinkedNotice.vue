<template>
    <!--
        「메뉴에 없는 ○○ N개」 경고 — 공개 메뉴(사용 중인데 메뉴에 없거나 숨김 안에만 있는 게시판)와 관리자 메뉴(어느 모드에도 없는 화면)가 함께 쓴다.
        줄마다 [넣기] — 어디에 넣는지는 부르는 쪽이 정한다(공개: 자동 추가 폴더 또는 최상위 끝 · 관리자: 원래 묶음 또는 그 모드 최상위 끝). 처음에는 접혀 있다.
    -->
    <div v-if="entries.length" class="rounded-xl border border-amber-300 bg-amber-50 px-4 py-3 dark:border-amber-800 dark:bg-amber-950/40" role="region" :aria-label="title">
        <div class="flex flex-wrap items-center gap-2">
            <i class="pi pi-exclamation-triangle text-sm text-amber-700 dark:text-amber-400" aria-hidden="true" />
            <span class="text-sm font-medium text-amber-800 dark:text-amber-300">{{ title }}</span>
            <Button type="button" :label="open ? t('menuConfig.unlinkedFold') : t('menuConfig.unlinkedShow')" size="small" severity="secondary" variant="text" class="ml-auto" :aria-expanded="open" @click="open = !open" />
        </div>
        <p v-if="note" class="m-0 mt-1 text-xs text-amber-800/80 dark:text-amber-300/80">{{ note }}</p>
        <ul v-if="open" class="m-0 mt-2 flex list-none flex-col gap-1 p-0">
            <li v-for="entry in entries" :key="entry.key" class="flex min-w-0 items-center gap-2 rounded-md bg-surface-0 px-2 py-1 dark:bg-surface-900">
                <span class="min-w-0 truncate text-[13px]">{{ entry.label }}</span>
                <code v-if="entry.sub" class="truncate text-[11px] text-muted-color">{{ entry.sub }}</code>
                <MenuTag v-if="entry.reason" :value="entry.reason" />
                <Button type="button" :label="t('menuConfig.put')" icon="pi pi-plus" size="small" severity="secondary" variant="outlined" class="ml-auto shrink-0" :disabled="disabled" @click="emit('put', entry.key)" />
            </li>
        </ul>
    </div>
</template>

<script setup lang="ts">
interface Props {
    /** 제목 — 「메뉴에 없는 게시판 3개」 */
    title: string;
    /** 줄 — `key` 는 [넣기]로 돌려받는 값 */
    entries: { key: string; label: string; sub?: string; reason?: string }[];
    /** 어디에 들어가는지 안내 */
    note?: string;
    disabled?: boolean;
}

withDefaults(defineProps<Props>(), { note: '', disabled: false });
const emit = defineEmits<{ (e: 'put', key: string): void }>();

const { t } = useI18n();
const open = ref(false);
</script>
