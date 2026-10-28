<template>
    <!--
        자체 주소 DB 검색 — 공용 주소 입력(`AddressInput`)의 시트 안에서 다음 우편번호 대신 쓴다(제공처 `INTERNAL`, 또는 `DAUM_FALLBACK_INTERNAL` 에서 다음을 못 불러왔을 때).
        도로명·건물 이름·지번·영문 주소로 부분 일치 검색(2자 이상, 치는 동안 0.25초 쉬면 찾는다 — 앞 요청은 끊는다). 「더 보기」가 다음 쪽을 아래에 잇는다.
        줄을 누르면 `select`. 주소 DB 를 쓸 수 없으면(503 `ADDRESS_DB_NOT_LOADED`) 서버 문구 + 「직접 입력」(`manual`), 그 밖의 실패는 서버 문구 + 다시 시도.
        다시 열 때 처음 상태로 돌리려면 부르는 쪽이 `:key` 를 바꾼다.
    -->
    <div class="flex h-full min-h-0 flex-col gap-3">
        <IconField>
            <InputIcon class="pi pi-search" />
            <InputText
                ref="keywordInput"
                v-model="keyword"
                type="search"
                enterkeyhint="search"
                autocomplete="off"
                maxlength="80"
                :placeholder="t('address.keywordPlaceholder')"
                :aria-label="t('address.keywordLabel')"
                :aria-describedby="statusId"
                fluid
                data-testid="address-keyword"
                @keydown.enter.prevent="searchNow"
            />
        </IconField>

        <div class="flex min-h-0 flex-1 flex-col overflow-y-auto" data-testid="address-results">
            <!-- 상태 한 줄 — 스크린리더가 바뀔 때 읽는다 -->
            <div :id="statusId" aria-live="polite" class="text-center text-sm text-muted-color">
                <div v-if="error" class="flex flex-col items-center gap-2 py-6" role="alert">
                    <p class="m-0 text-red-600 dark:text-red-400">{{ error.message }}</p>
                    <Button v-if="unavailable" type="button" :label="t('address.manualEntry')" icon="pi pi-pencil" size="small" severity="secondary" variant="outlined" @click="emit('manual')" />
                    <Button v-else type="button" :label="t('common.retry')" icon="pi pi-refresh" size="small" severity="secondary" variant="outlined" @click="run(items.length > 0 ? page + 1 : 0)" />
                </div>
                <p v-else-if="trimmed.length === 0" class="m-0 py-6">{{ t('address.searchIntro') }}</p>
                <p v-else-if="trimmed.length < MIN_KEYWORD" class="m-0 py-6">{{ t('address.keywordTooShort', { min: MIN_KEYWORD }) }}</p>
                <p v-else-if="!loading && searched && items.length === 0" class="m-0 py-6">{{ t('address.noResults') }}</p>
                <p v-else-if="items.length > 0" class="sr-only">{{ t('address.resultCount', { count: total }) }}</p>
            </div>

            <ul v-if="items.length > 0" class="m-0 flex list-none flex-col p-0">
                <li v-for="(item, i) in items" :key="`${item.postcode}-${item.roadAddress}-${i}`">
                    <button
                        type="button"
                        class="flex w-full flex-col items-start gap-0.5 rounded-lg px-2 py-2.5 text-left transition hover:bg-surface-100 focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-surface-800"
                        data-testid="address-result"
                        @click="emit('select', item)"
                    >
                        <span class="flex items-center gap-2 text-sm font-medium">
                            <span class="inline-flex shrink-0 items-center justify-center rounded bg-surface-100 px-1.5 py-0.5 text-xs leading-none tabular-nums text-muted-color dark:bg-surface-800">{{ item.postcode }}</span>
                            <span>{{ item.roadAddress }}</span>
                        </span>
                        <span v-if="item.buildingName || item.jibunAddress" class="text-xs text-muted-color">
                            {{ [item.buildingName, item.jibunAddress ? t('address.jibunPrefix', { address: item.jibunAddress }) : ''].filter(Boolean).join(' · ') }}
                        </span>
                        <span v-if="item.roadAddressEn" class="text-xs italic text-muted-color" lang="en">{{ item.roadAddressEn }}</span>
                    </button>
                </li>
            </ul>

            <div v-if="loading" class="flex justify-center py-4" role="status" :aria-label="t('common.loading')">
                <ProgressSpinner style="width: 1.5rem; height: 1.5rem" />
            </div>
            <Button v-else-if="hasMore && !error" type="button" :label="t('common.more')" size="small" severity="secondary" variant="text" class="mt-1 self-center" data-testid="address-more" @click="run(page + 1)" />
        </div>
    </div>
</template>

<script setup lang="ts">
import type { AddressSearchResult } from '~/composables/address/api/useAddressApi.types';
import { ApiError } from '~/composables/common/useApiClient';

/** 서버와 같은 최소 검색어 길이(앞뒤 빈칸 뺀 글자 수) */
const MIN_KEYWORD = 2;
const PAGE_SIZE = 10;
const DEBOUNCE_MS = 250;

const emit = defineEmits<{ select: [result: AddressSearchResult]; manual: [] }>();
const { t } = useI18n();
const addressSearch = useAddressSearch();
const statusId = useId();

const keyword = ref('');
const items = ref<AddressSearchResult[]>([]);
const total = ref(0);
const page = ref(0);
const loading = ref(false);
/** 지금 검색어로 한 번이라도 답을 받았다 — 받기 전에 「결과 없음」이 비치지 않게 */
const searched = ref(false);
const error = ref<ApiError | null>(null);
const keywordInput = ref<{ $el: HTMLElement } | null>(null);

const trimmed = computed(() => keyword.value.trim());
const hasMore = computed(() => items.value.length < total.value);
/** 주소 DB 를 쓸 수 없다 — 다시 시도해도 같으니 직접 입력을 권한다 */
const unavailable = computed(() => error.value?.code === 'ADDRESS_DB_NOT_LOADED');

let controller: AbortController | null = null;
let timer: ReturnType<typeof setTimeout> | undefined;

/**
 * 한 쪽을 받는다 — 0쪽이면 목록을 새로 채우고, 그 밖이면 아래에 잇는다. 앞 요청은 끊는다.
 * @param {number} target - 받을 쪽 번호.
 */
async function run(target: number): Promise<void> {
    const q = trimmed.value;
    controller?.abort();
    if (q.length < MIN_KEYWORD) {
        items.value = [];
        total.value = 0;
        error.value = null;
        searched.value = false;
        loading.value = false;
        return;
    }
    const mine = new AbortController();
    controller = mine;
    loading.value = true;
    error.value = null;
    if (target === 0) searched.value = false;
    try {
        const result = await addressSearch.search(q, target, PAGE_SIZE, mine.signal);
        if (controller !== mine) return;
        items.value = target === 0 ? result.items : [...items.value, ...result.items];
        total.value = result.total;
        page.value = target;
        searched.value = true;
    } catch (e) {
        if (controller !== mine || (e instanceof ApiError && e.code === 'ABORTED')) return;
        if (target === 0) items.value = [];
        error.value = e instanceof ApiError ? e : new ApiError({ status: 0, code: 'NETWORK', message: t('address.searchFailed') });
    } finally {
        if (controller === mine) loading.value = false;
    }
}

function searchNow(): void {
    clearTimeout(timer);
    void run(0);
}

watch(keyword, () => {
    clearTimeout(timer);
    timer = setTimeout(() => void run(0), DEBOUNCE_MS);
});

onMounted(() => {
    // 시트가 열리며 초점이 시트 머리로 간 뒤에 검색칸으로 옮긴다
    requestAnimationFrame(() => keywordInput.value?.$el?.focus());
});

onBeforeUnmount(() => {
    clearTimeout(timer);
    controller?.abort();
});
</script>
