<template>
    <!--
        팝업 창 하나 — 같은 「띄울 창」 번호의 팝업들을 한 창에서 슬라이드로 넘긴다(목록 차례). `SitePopupLayer` 가 창마다 하나씩 놓는다(PC 는 나란히, 모바일은 하나씩 차례로).
          여러 장이면 위 오른쪽 「1 / 3」, 양옆 ‹ ›, 밀어서도 넘긴다.
          아래 줄: 왼쪽 「보지 않기」(지금 장의 운영자 선택 — 「닫기」만이면 없음) → 그 장만 빠진다 · 오른쪽 「닫기」 → 이번 방문 동안 이 창을 닫는다.
          그림·단추의 링크를 누르면 `go` — 레이어가 창을 닫고 이동한다(바깥 주소는 `<a target=_blank>` 가 연다).
        모양은 관리자 미리보기(admin-web `components/popup/SitePopupCard.vue`)와 같다 — 두 곳을 같이 고친다.
          폭은 부모가 정한다(PC 400px · 모바일 화면 폭 - 2.5rem). 그림은 가로 맞춤(세로 최대 min(70dvh,600px)), 텍스트는 제목·내용(줄바꿈 유지, 길면 안에서 스크롤)·단추.
    -->
    <div v-if="current" class="flex w-full flex-col overflow-hidden rounded-xl bg-white text-surface-900 shadow-2xl dark:bg-surface-900 dark:text-surface-0" data-testid="site-popup-window">
        <div class="relative" @touchstart.passive="onTouchStart" @touchend.passive="onTouchEnd">
            <Transition :name="slideName" mode="out-in">
                <div :key="current.id">
                    <template v-if="current.kind === 'IMAGE'">
                        <component
                            :is="linkKind === 'internal' ? 'button' : linkKind === 'external' ? 'a' : 'div'"
                            v-bind="linkKind === 'external' ? { href: current.link, target: '_blank', rel: 'noopener noreferrer' } : linkKind === 'internal' ? { type: 'button' } : {}"
                            class="block w-full border-0 bg-transparent p-0 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary"
                            :class="linkKind ? 'cursor-pointer' : ''"
                            data-testid="site-popup-image"
                            @click="linkKind && emit('go', current)"
                        >
                            <img :src="current.imageUrl ?? ''" :alt="current.imageAlt ?? ''" class="block max-h-[min(70dvh,600px)] w-full object-contain" />
                            <span v-if="linkKind === 'external'" class="sr-only">{{ t('popup.newWindow') }}</span>
                        </component>
                    </template>
                    <div v-else class="flex flex-col gap-3 px-6 pb-5 pt-6">
                        <h2 v-if="current.heading" class="m-0 text-lg font-bold leading-snug [overflow-wrap:anywhere]">{{ current.heading }}</h2>
                        <p class="m-0 max-h-[min(50dvh,420px)] overflow-y-auto whitespace-pre-line text-sm leading-relaxed text-surface-700 [overflow-wrap:anywhere] dark:text-surface-200">{{ current.body }}</p>
                        <a
                            v-if="linkKind === 'external'"
                            :href="current.link!"
                            target="_blank"
                            rel="noopener noreferrer"
                            class="mt-1 inline-flex h-10 w-full items-center justify-center rounded-lg bg-primary text-sm font-semibold text-primary-contrast no-underline hover:opacity-90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                            @click="emit('go', current)"
                        >
                            {{ current.buttonLabel || t('popup.buttonDefault') }}
                            <span class="sr-only">{{ t('popup.newWindow') }}</span>
                        </a>
                        <Button v-else-if="linkKind === 'internal'" type="button" :label="current.buttonLabel || t('popup.buttonDefault')" class="mt-1 h-10 w-full" @click="emit('go', current)" />
                    </div>
                </div>
            </Transition>

            <template v-if="items.length > 1">
                <span class="pointer-events-none absolute right-3 top-3 rounded-full bg-black/55 px-2 py-0.5 text-[11px] font-medium tabular-nums text-white" aria-live="polite">{{ index + 1 }} / {{ items.length }}</span>
                <button type="button" class="absolute left-2 top-1/2 inline-flex size-8 -translate-y-1/2 cursor-pointer items-center justify-center rounded-full border-0 bg-black/40 text-white hover:bg-black/60 focus-visible:outline-2 focus-visible:outline-white" :aria-label="t('popup.prev')" data-testid="site-popup-prev" @click="step(-1)">
                    <i class="pi pi-chevron-left text-xs" aria-hidden="true" />
                </button>
                <button type="button" class="absolute right-2 top-1/2 inline-flex size-8 -translate-y-1/2 cursor-pointer items-center justify-center rounded-full border-0 bg-black/40 text-white hover:bg-black/60 focus-visible:outline-2 focus-visible:outline-white" :aria-label="t('popup.next')" data-testid="site-popup-next" @click="step(1)">
                    <i class="pi pi-chevron-right text-xs" aria-hidden="true" />
                </button>
            </template>
        </div>

        <div class="flex h-11 shrink-0 items-center justify-between gap-3 bg-neutral-900 px-4 text-[13px] text-neutral-100">
            <button
                v-if="current.hideOption !== 'NONE'"
                type="button"
                class="inline-flex min-w-0 cursor-pointer items-center gap-1.5 rounded border-0 bg-transparent p-0 text-inherit hover:text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white"
                data-testid="site-popup-hide"
                @click="emit('hide', current)"
            >
                <i class="pi pi-check-circle text-[0.8rem] text-neutral-400" aria-hidden="true" />
                <span class="truncate">{{ t(`popup.hide.${current.hideOption}`) }}</span>
            </button>
            <span v-else />
            <button
                type="button"
                class="inline-flex shrink-0 cursor-pointer items-center gap-1 rounded border-0 bg-transparent p-0 font-medium text-inherit hover:text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white"
                data-testid="site-popup-close"
                @click="emit('close', items)"
            >
                {{ t('popup.close') }}
                <i class="pi pi-times text-[0.7rem]" aria-hidden="true" />
            </button>
        </div>
    </div>
</template>

<script setup lang="ts">
import type { PublicSitePopup } from '~/composables/popup/api/useSitePopupApi.types';
import UtilSitePopup from '~/utils/util-site-popup';

interface Props {
    /** 이 창의 팝업(목록 차례) */
    items: PublicSitePopup[];
}

const props = withDefaults(defineProps<Props>(), {});
const emit = defineEmits<{ close: [items: PublicSitePopup[]]; hide: [item: PublicSitePopup]; go: [item: PublicSitePopup] }>();
const { t } = useI18n();

const index = ref(0);
const current = computed(() => props.items[Math.min(index.value, props.items.length - 1)] ?? null);
const linkKind = computed(() => UtilSitePopup.linkKind(current.value?.link ?? null));

const direction = ref<1 | -1>(1);
const slideName = computed(() => (direction.value === 1 ? 'popup-next' : 'popup-prev'));

function step(delta: -1 | 1): void {
    const count = props.items.length;
    if (count < 2) return;
    direction.value = delta;
    index.value = (index.value + delta + count) % count;
}

let touchX: number | null = null;
function onTouchStart(event: TouchEvent): void {
    touchX = event.touches[0]?.clientX ?? null;
}
function onTouchEnd(event: TouchEvent): void {
    const end = event.changedTouches[0]?.clientX;
    if (touchX === null || end === undefined) return;
    const dx = end - touchX;
    touchX = null;
    if (Math.abs(dx) > 40) step(dx < 0 ? 1 : -1);
}

// 한 장이 빠지면(보지 않기) 그 자리의 다음 장 — 마지막이었으면 앞 장
watch(
    () => props.items.length,
    (count) => {
        if (index.value >= count) index.value = Math.max(0, count - 1);
    },
);
</script>

<style scoped>
.popup-next-enter-active,
.popup-next-leave-active,
.popup-prev-enter-active,
.popup-prev-leave-active {
    transition:
        transform 0.2s ease,
        opacity 0.2s ease;
}

.popup-next-enter-from,
.popup-prev-leave-to {
    transform: translateX(24px);
    opacity: 0;
}

.popup-next-leave-to,
.popup-prev-enter-from {
    transform: translateX(-24px);
    opacity: 0;
}

@media (prefers-reduced-motion: reduce) {
    .popup-next-enter-active,
    .popup-next-leave-active,
    .popup-prev-enter-active,
    .popup-prev-leave-active {
        transition: none;
    }
}
</style>
