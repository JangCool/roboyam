<template>
    <!--
        핫딜 제목 안내(05 §2-5) — 「상품명만 적어요 · 쇼핑몰·가격은 아래 칸이 목록에 보여 줘요」.
        제목이 `[` 로 시작하거나 `(…원)` 로 끝나면 「자동으로 붙어요 — 지울까요? [지우기]」(막지 않는다 — 그대로 등록할 수 있다). 글쓰기·고치기가 제목 칸 아래(`PostEditorForm` 의 `titleHint`)에 둔다.
    -->
    <div class="-mt-2 flex flex-col gap-1 text-xs" data-testid="hotdeal-title-hint">
        <p class="m-0 text-muted-color">{{ t('hotdeal.titleHelp') }}</p>
        <p v-if="hasAuto" class="m-0 flex flex-wrap items-center gap-x-2 text-amber-700 dark:text-amber-300" data-testid="hotdeal-title-auto">
            <i class="pi pi-info-circle text-[0.6875rem]" aria-hidden="true" />{{ t('hotdeal.titleAutoParts') }}
            <button
                type="button"
                class="rounded px-0.5 font-semibold text-primary underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-primary"
                data-testid="hotdeal-title-strip"
                @click="$emit('strip', UtilHotdeal.stripTitleAutoParts(title))"
            >
                {{ t('hotdeal.titleStrip') }}
            </button>
        </p>
    </div>
</template>

<script setup lang="ts">
import UtilHotdeal from '~/utils/util-hotdeal';

interface Props {
    title: string;
}

const props = withDefaults(defineProps<Props>(), {});
defineEmits<{
    /** [지우기] — 앞 `[…]`·끝 `(…원)` 을 뗀 제목 */
    strip: [title: string];
}>();

const { t } = useI18n();
const hasAuto = computed(() => UtilHotdeal.titleHasAutoParts(props.title) && UtilHotdeal.stripTitleAutoParts(props.title) !== '');
</script>
