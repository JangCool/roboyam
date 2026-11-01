<template>
    <!--
        광고 자리 미리보기의 광고 상자 한 개 — 주 색상 테두리·옅은 바탕 + 「광고」 꼬리표(실제 광고 내용은 없다).
        모양은 공개 화면 규격 비율: 한 줄 = 얇은 글줄 · 띠 = 12:1 · 네모 = 6:5(폭 제한) · 날개 = 4:15(폭은 부모가 정한다).
    -->
    <div class="relative flex min-w-0 rounded-[3px] border border-primary bg-primary-50 dark:bg-primary-950/40" :class="shapeClass" data-testid="ad-placement-slot" :data-shape="shape">
        <span class="inline-flex shrink-0 items-center justify-center rounded-sm bg-primary px-1 py-0.5 text-[0.5625rem] font-semibold leading-none text-primary-contrast" :class="tagClass">
            <span class="relative top-[-0.09em]">{{ t('ad.previewAdTag') }}</span>
        </span>
        <span v-if="shape === 'line'" class="h-1 min-w-0 flex-1 rounded-full bg-primary-200 dark:bg-primary-800" aria-hidden="true" />
    </div>
</template>

<script setup lang="ts">
interface Props {
    shape: 'line' | 'strip' | 'square' | 'wing';
    /** 네모 배너를 가운데에 둔다(글 카드 안 `AFTER_BODY`). 기본은 왼쪽 */
    center?: boolean;
}

const props = withDefaults(defineProps<Props>(), { center: false });
const { t } = useI18n();

const shapeClass = computed(() => {
    switch (props.shape) {
        case 'line':
            return 'h-5 w-full items-center gap-1.5 px-1.5';
        case 'strip':
            return 'aspect-[12/1] min-h-5 w-full items-center px-1';
        case 'square':
            return ['aspect-[6/5] w-[42%] max-w-32 items-start p-1', props.center ? 'mx-auto' : ''];
        default:
            return 'aspect-[4/15] w-full items-start justify-center p-0.5';
    }
});

const tagClass = computed(() => (props.shape === 'wing' ? 'px-0.5 text-[0.5rem]' : ''));
</script>
