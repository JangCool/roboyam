<template>
    <!-- 한 가지 결과를 크게 알리는 화면 조각 — "인증 메일을 보냈어요", "링크가 만료됐어요" 같은 것 -->
    <section class="flex flex-col items-center gap-3 py-1 text-center" :role="tone === 'error' || tone === 'warn' ? 'alert' : 'status'">
        <div class="grid size-14 place-items-center rounded-full text-2xl" :class="TONE_CLASS[tone]" aria-hidden="true">
            <i :class="[icon, 'text-2xl']" />
        </div>
        <h1 ref="heading" tabindex="-1" class="text-lg font-semibold">{{ title }}</h1>
        <div class="text-sm leading-relaxed text-muted-color">
            <slot />
        </div>
        <div v-if="$slots.actions" class="mt-2 flex w-full flex-col gap-2">
            <slot name="actions" />
        </div>
    </section>
</template>

<script setup lang="ts">
export type StatusTone = 'info' | 'success' | 'warn' | 'error';

const TONE_CLASS: Record<StatusTone, string> = {
    info: 'bg-primary-50 text-primary-700 dark:bg-primary-950 dark:text-primary-300',
    success: 'bg-green-100 text-green-700 dark:bg-green-950 dark:text-green-300',
    warn: 'bg-amber-100 text-amber-700 dark:bg-amber-950 dark:text-amber-300',
    error: 'bg-red-100 text-red-700 dark:bg-red-950 dark:text-red-300',
};

interface Props {
    title: string;
    /** primeicons 클래스 (예: `pi pi-envelope`) */
    icon?: string;
    tone?: StatusTone;
}

const props = withDefaults(defineProps<Props>(), {
    icon: 'pi pi-info-circle',
    tone: 'info',
});

// 화면이 바뀌어 이 조각이 나타나면 제목으로 초점을 옮긴다 — 스크린리더가 결과를 읽는다
const heading = ref<HTMLElement | null>(null);
onMounted(() => heading.value?.focus({ preventScroll: true }));
</script>
