<template>
    <!--
        새소식(글 목록 가젯) 태그 탭(post-tags-contract 「시안 2」) — 「전체 · #태그 …」 글자 탭. 인기글 기간 탭과 같은 모양(고른 탭 굵게 + 주 색상 밑줄), `#` 은 흐린 색.
        PC 는 가젯 머리 제목 옆(`GadgetFrame` `head` 슬롯), 한 열(모바일)은 제목 줄 아래 한 줄 — 넘치면 가로로 밀고(스크롤바 숨김), 누르는 높이 32px.
        값은 고른 태그(`''` = 전체). 탭 id 는 `{idBase}-tag-{차례}`(전체 = `all`) — 목록이 `aria-labelledby` 로 잇는다.
    -->
    <div
        role="tablist"
        :aria-label="t('postTag.tabs')"
        class="flex min-w-0 gap-3.5 overflow-x-auto overscroll-x-contain [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
        :class="stacked ? '' : 'shrink'"
        data-testid="home-tag-tabs"
    >
        <button
            v-for="(tab, i) in all"
            :id="tabId(i)"
            :key="tab || '*'"
            type="button"
            role="tab"
            :aria-selected="tab === modelValue"
            :aria-controls="panelId"
            class="inline-flex shrink-0 items-center whitespace-nowrap border-b-2 text-xs focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary"
            :class="[tab === modelValue ? 'border-primary font-bold text-color' : 'border-transparent font-normal text-muted-color hover:text-color', stacked ? 'min-h-8' : 'min-h-6']"
            data-testid="home-tag-tab"
            @click="emit('update:modelValue', tab)"
        >
            <template v-if="tab"><span class="font-normal text-muted-color" aria-hidden="true">#</span>{{ tab }}</template>
            <template v-else>{{ t('common.all') }}</template>
        </button>
    </div>
</template>

<script setup lang="ts">
interface Props {
    /** 운영자가 고른 태그(차례대로) — 「전체」는 이 부품이 앞에 붙인다 */
    tags: string[];
    /** 고른 태그 — `''` 이면 전체 */
    modelValue: string;
    /** 탭 id 앞마디 */
    idBase: string;
    /** 탭이 고르는 목록의 id */
    panelId: string;
    /** 한 열(모바일) — 누르는 높이 32px */
    stacked?: boolean;
}

const props = withDefaults(defineProps<Props>(), { stacked: false });
const emit = defineEmits<{ 'update:modelValue': [value: string] }>();

const { t } = useI18n();

const all = computed(() => ['', ...props.tags]);

function tabId(i: number): string {
    return `${props.idBase}-tag-${i === 0 ? 'all' : i - 1}`;
}
</script>
