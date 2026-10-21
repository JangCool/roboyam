<template>
    <!-- 모바일은 아래에서 올라오는 시트, 데스크톱은 가운데 대화상자 (design 01-shell 7·8). 초점 가두기·Esc·바깥 누르기는 Dialog 가 한다 -->
    <Dialog
        v-model:visible="open"
        modal
        :closable="false"
        :draggable="false"
        dismissable-mask
        :position="desktop ? 'center' : 'bottom'"
        :aria-labelledby="titleId"
        :style="desktop ? { width: wide ? '44rem' : '26rem', maxWidth: 'calc(100vw - 2rem)' } : { width: '100%', maxWidth: '100%', margin: '0', maxHeight: '88dvh' }"
        :pt="{ root: { class: desktop ? '' : 'rounded-b-none! border-x-0! border-b-0!' } }"
    >
        <template #container>
            <div class="flex max-h-[88dvh] flex-col pb-[max(1rem,env(safe-area-inset-bottom))] pt-3">
                <div v-if="!desktop" class="mx-auto mb-3 h-1 w-9 rounded-full bg-surface-300 dark:bg-surface-600" aria-hidden="true" />
                <div class="px-5">
                    <h3 :id="titleId" class="text-base font-semibold">{{ title }}</h3>
                    <p v-if="description" class="mt-1 text-sm leading-relaxed text-muted-color">{{ description }}</p>
                </div>
                <div class="min-h-0 flex-1 overflow-y-auto">
                    <slot />
                </div>
            </div>
        </template>
    </Dialog>
</template>

<script setup lang="ts">
/** 아래에서 올라오는 시트(데스크톱은 가운데 대화상자) — ⋯ 메뉴·정렬 고르기·로그인 유도가 같이 쓴다. */
interface Props {
    title: string;
    description?: string;
    /** 데스크톱에서 넓게(44rem) — 수정 이력처럼 본문을 읽는 시트. 모바일은 늘 화면 폭 */
    wide?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
    description: '',
    wide: false,
});

const open = defineModel<boolean>('visible', { required: true });

const desktop = useIsDesktop();
const titleId = useId();
</script>
