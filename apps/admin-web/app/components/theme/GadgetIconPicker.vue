<template>
    <!--
        제목 아이콘 고르기(`GadgetTitleIconFields` 가 쓴다) — 검색 입력 + 격자(고른 것은 주 색상 테두리). PrimeIcons 8 전부 · Tabler Icons outline 전부(MIT — 창을 열 때만 목록을 읽는다).
        검색은 영어 이름·Tabler 태그, 한국어는 몇 가지 별칭(`UtilIconCatalog`). 목록이 커도 느려지지 않게 한 번에 160개까지만 그리고, 넘으면 검색어를 더 적으라고 알린다.
        격자 칸은 단추(`aria-pressed`, 이름은 아이콘 이름 — 영어) — Tab 으로 들어가 누른다.
    -->
    <div class="flex flex-col gap-2" :data-testid="`gadget-icon-picker-${source.toLowerCase()}`">
        <IconField class="w-full sm:w-80">
            <InputIcon class="pi pi-search" />
            <InputText v-model="query" :placeholder="t('homeLayout.iconSearchPlaceholder')" :aria-label="t('homeLayout.iconSearch')" :aria-controls="gridId" fluid />
        </IconField>
        <p v-if="loading" role="status" class="m-0 text-xs text-muted-color"><i class="pi pi-spin pi-spinner mr-1 text-xs" aria-hidden="true" />{{ t('homeLayout.iconLoading') }}</p>
        <p v-else-if="loadError" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ t('homeLayout.iconLoadFailed') }}</p>
        <template v-else>
            <div :id="gridId" role="group" :aria-label="t('homeLayout.iconGrid')" class="grid max-h-60 grid-cols-[repeat(auto-fill,minmax(2.5rem,1fr))] gap-1 overflow-y-auto rounded-md border border-(--line) p-1.5">
                <button
                    v-for="it in result.items"
                    :key="it.name"
                    type="button"
                    :title="label(it.name)"
                    :aria-label="label(it.name)"
                    :aria-pressed="it.name === selected"
                    class="grid size-10 place-items-center rounded-md border text-lg focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-primary"
                    :class="it.name === selected ? 'border-primary bg-primary/10 text-primary' : 'border-transparent hover:bg-emphasis'"
                    @click="pick(it.name)"
                >
                    <GadgetIconGlyph :prime="source === 'PRIME' ? it.name : null" :paths="source === 'TABLER' ? (tabler?.paths[it.name] ?? null) : null" />
                </button>
                <p v-if="result.items.length === 0" class="col-span-full m-0 py-4 text-center text-xs text-muted-color">{{ t('homeLayout.iconNoMatch') }}</p>
            </div>
            <small class="text-xs text-muted-color" aria-live="polite">
                {{ result.total > result.items.length ? t('homeLayout.iconMore', { shown: result.items.length, total: result.total }) : t('homeLayout.iconCount', { count: result.total }) }}
            </small>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { GadgetTablerIcon } from '~/composables/home/api/useHomeLayoutApi.types';
import type { TablerCatalog } from '~/utils/util-icon-catalog';
import UtilIconCatalog from '~/utils/util-icon-catalog';

interface Props {
    source: 'PRIME' | 'TABLER';
    /** 지금 고른 이름(PrimeIcons `pi-…` · Tabler 이름) */
    selected: string | null;
}

const props = withDefaults(defineProps<Props>(), {});
const emit = defineEmits<{ (e: 'pickPrime', name: string): void; (e: 'pickTabler', icon: GadgetTablerIcon): void }>();

const { t } = useI18n();
const gridId = `${useId()}-grid`;
const query = ref('');

const prime = UtilIconCatalog.prime();
const tabler = shallowRef<TablerCatalog | null>(null);
const loading = ref(false);
const loadError = ref(false);

watch(
    () => props.source,
    async (source) => {
        query.value = '';
        if (source !== 'TABLER' || tabler.value) return;
        loading.value = true;
        loadError.value = false;
        try {
            tabler.value = await UtilIconCatalog.tabler();
        } catch {
            loadError.value = true;
        } finally {
            loading.value = false;
        }
    },
    { immediate: true },
);

const result = computed(() => UtilIconCatalog.search(props.source === 'PRIME' ? prime : (tabler.value?.items ?? []), query.value));

function label(name: string): string {
    return props.source === 'PRIME' ? name.slice(3) : name;
}

function pick(name: string): void {
    if (props.source === 'PRIME') {
        emit('pickPrime', name);
        return;
    }
    const icon = tabler.value ? UtilIconCatalog.tablerIcon(tabler.value, name) : null;
    if (icon) emit('pickTabler', icon);
}
</script>
