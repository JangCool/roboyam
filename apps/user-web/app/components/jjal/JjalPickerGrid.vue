<template>
    <!--
        고르기 창 「짤」 탭의 내용(계약 my-jjal-contract 「고르기 창」) — 위에 `12 / 50`, 첫 칸 「올리기」, 나머지는 내 짤(내가 정한 순서 — 내 로보티콘 「내 짤」 칸의 「순서 바꾸기」, 내려간 짤은 뺀다),
        아래 「나만 쓰는 보관함이에요. 상점에 나오지 않아요.」. 고르면 짤 ID 를 낸다(넣는 일은 부르는 쪽 — 토큰 `:jjal-ID:`).
    -->
    <div class="flex flex-col gap-1.5" data-testid="jjal-picker">
        <p class="m-0 flex items-center justify-between gap-2 px-1">
            <span class="text-xs font-semibold">{{ t('jjal.tabTitle') }}</span>
            <span v-if="list" class="text-xs tabular-nums text-muted-color" data-testid="jjal-picker-count">{{ t('jjal.count', { count: list.count, limit: list.limit }) }}</span>
        </p>

        <div v-if="list === undefined && !error" class="grid grid-cols-[repeat(auto-fill,minmax(4rem,1fr))] gap-1" role="status" :aria-label="t('jjal.loading')">
            <div v-for="n in 8" :key="n" class="aspect-square animate-pulse rounded-lg bg-surface-200 dark:bg-surface-800" />
        </div>
        <p v-else-if="error && !list" class="m-0 flex flex-wrap items-center gap-2 px-1 py-2 text-xs text-muted-color" role="alert">
            {{ t('jjal.loadFailed') }}
            <Button type="button" :label="t('common.retry')" size="small" severity="secondary" variant="text" @click="mine.reload()" />
        </p>
        <div v-else class="grid grid-cols-[repeat(auto-fill,minmax(4rem,1fr))] gap-1">
            <!-- 첫 칸 — 올리기(처음이면 안내 창 먼저) -->
            <button
                type="button"
                class="flex aspect-square flex-col items-center justify-center gap-1 rounded-lg border border-dashed border-surface text-muted-color hover:bg-surface-100 hover:text-color focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-surface-800"
                data-testid="jjal-picker-upload"
                @click="$emit('upload')"
            >
                <i class="pi pi-upload text-base" aria-hidden="true" />
                <span class="text-[0.6875rem] font-medium leading-none">{{ t('jjal.upload') }}</span>
            </button>
            <button
                v-for="item in items"
                :key="item.id"
                type="button"
                class="grid aspect-square place-items-center overflow-hidden rounded-lg p-0.5 hover:bg-surface-100 focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-surface-800"
                :aria-label="t('jjal.pickAria')"
                :title="t('jjal.pickAria')"
                data-testid="jjal-picker-item"
                @click="pick(item)"
            >
                <img :src="item.url ?? undefined" alt="" class="size-16 object-contain" loading="lazy" decoding="async" />
            </button>
        </div>
        <p v-if="list && items.length === 0" class="m-0 px-1 text-xs text-muted-color" data-testid="jjal-picker-empty">{{ t('jjal.pickerEmpty') }}</p>
        <p class="m-0 px-1 pt-0.5 text-[0.6875rem] text-muted-color">{{ t('jjal.pickerNote') }}</p>
    </div>
</template>

<script setup lang="ts">
import type { MyJjalItem } from '~/composables/jjal/api/useJjalApi.types';
import UtilJjal from '~/utils/util-jjal';

const emit = defineEmits<{
    /** 고른 짤 ID(토큰으로 넣는다) */
    pick: [id: string];
    /** 올리기 칸을 눌렀다 */
    upload: [];
}>();

const { t } = useI18n();
const mine = useMyJjals();
const catalog = useJjalCatalog();
const { list, error } = mine;

const items = computed(() => (list.value ? UtilJjal.forPicker(list.value.items) : []));

onMounted(() => {
    void mine.ensureLoaded();
});

/** 고른 짤 — 그림을 기억시킨다(넣자마자 그림으로). 순서는 바꾸지 않는다(회원이 정한 순서) */
function pick(item: MyJjalItem): void {
    catalog.remember({ id: item.id, url: item.url, width: item.width, height: item.height, animated: item.animated, blocked: item.blocked, collectable: !item.blocked });
    emit('pick', item.id);
}
</script>
