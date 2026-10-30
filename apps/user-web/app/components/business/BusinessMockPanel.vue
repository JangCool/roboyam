<template>
    <!--
        목업 단계 개발용 상자 — 서버가 없어 목업 상태를 바꾸거나 운영자 처리를 흉내 낸다. 「목업」 딱지와 점선 테두리로 실제 화면과 구별한다.
        `floating`: 화면 오른쪽 아래 구석(모바일은 하단 탭 위)에 접히는 상자. 아니면 본문 안 상자.
        ⚠ 서버가 붙으면 이 부품과 쓰는 곳을 함께 지운다(`useBusinessApi` 의 `mockSetStatus`·`mockOperate`).
    -->
    <details
        :open="open"
        class="rounded-xl border-2 border-dashed border-amber-400 bg-amber-50 text-sm text-amber-950 dark:border-amber-600 dark:bg-amber-950/80 dark:text-amber-100"
        :class="floating ? 'fixed bottom-20 right-3 z-40 w-[min(18rem,calc(100vw-1.5rem))] shadow-lg md:bottom-4' : ''"
        data-testid="business-mock-panel"
        @toggle="open = ($event.target as HTMLDetailsElement).open"
    >
        <summary class="flex min-h-10 cursor-pointer select-none items-center gap-2 px-3 py-2 font-semibold">
            <span class="inline-flex items-center justify-center rounded bg-amber-500 px-1.5 py-1 text-[0.6875rem] font-bold leading-none text-white">
                <span class="relative top-[-0.09em]">{{ t('business.mock.badge') }}</span>
            </span>
            {{ title }}
        </summary>
        <div class="flex flex-col gap-2 border-t border-dashed border-amber-400 px-3 pb-3 pt-2 dark:border-amber-600">
            <p v-if="help" class="m-0 text-xs leading-relaxed opacity-80">{{ help }}</p>
            <slot />
        </div>
    </details>
</template>

<script setup lang="ts">
interface Props {
    title: string;
    help?: string;
    floating?: boolean;
    /** 처음에 펼쳐 둘까 */
    initiallyOpen?: boolean;
}

const props = withDefaults(defineProps<Props>(), { help: '', floating: false, initiallyOpen: false });

const { t } = useI18n();
const open = ref(props.initiallyOpen);
</script>
