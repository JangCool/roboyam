<template>
    <!--
        팝업 — 사이트에 들어오면 화면 가운데 뜨는 이벤트·프로모션 창(운영자 관리자 「팝업 관리」). 계약 `docs/features/site-popup-contract.md`.
          기본 레이아웃에 한 번 놓는다. 메뉴 줄 아래 한 줄인 「공지 띠」(`SiteNoticeBar`)와 다른 기능이다.
        어디에 뜨나: 서버가 준 목록(노출 켬·기간 안) 가운데 보일 곳(홈 화면만 = `/` · 모든 화면)·기기(폭 768px 이상 PC, 미만 모바일)에 맞고 닫지 않은 것(`UtilSitePopup.visible`).
        여러 개면 **한 창에서 넘겨 본다**(목록 차례) — 위 오른쪽 「1 / 3」, 양옆 ‹ ›, 모바일은 밀어서도 넘긴다. 겹쳐 띄우지 않는다(모바일에서 여러 창이 겹치면 닫기 어렵다).
        아래 줄: 왼쪽 「보지 않기」(지금 장의 운영자 선택 — 오늘 하루 · 1주일 · 다시 보지 않기, 「닫기」만이면 없음) → 그 장만 빠지고 다음 장 · 오른쪽 「닫기」 → 이번 방문 동안 창 전체를 닫는다.
          Esc·바깥(어두운 막) 누르기도 「닫기」다. 그림·단추의 링크를 누르면 창을 닫고 이동한다(바깥 주소는 새 창).
        「보지 않기」 기록은 이 브라우저(`UtilSitePopup` — localStorage `roboyam:site-popups`)에만 — 운영자가 팝업을 고치면(내용 표식 `version`) 다시 보인다. 저장소를 못 쓰면 이번 방문 동안만 빠진다.
        모양은 관리자 미리보기(admin-web `components/popup/SitePopupCard.vue`)와 같다 — 두 곳을 같이 고친다.
          PC 폭 400px · 모바일 화면 폭 - 좌우 1.25rem, 가운데. 그림은 가로 맞춤(세로가 화면 70% 를 넘으면 줄여 담는다), 텍스트는 제목·내용(줄바꿈 유지, 길면 안에서 스크롤)·단추.
    -->
    <Dialog
        :visible="open"
        modal
        dismissable-mask
        :show-header="false"
        :draggable="false"
        :style="{ width: 'min(400px, calc(100vw - 2.5rem))' }"
        :pt="{ root: { class: 'overflow-hidden! rounded-xl! border-0! bg-transparent! shadow-2xl!' }, content: { class: 'p-0! overflow-hidden!' }, mask: { class: 'bg-black/55!' } }"
        :aria-label="t('popup.label')"
        data-testid="site-popup"
        @update:visible="(v: boolean) => !v && closeAll()"
    >
        <div v-if="current" class="flex flex-col overflow-hidden rounded-xl bg-white text-surface-900 dark:bg-surface-900 dark:text-surface-0">
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
                                @click="linkKind && go()"
                            >
                                <img :src="current.imageUrl ?? ''" :alt="current.imageAlt ?? ''" class="block max-h-[min(70dvh,600px)] w-full object-contain" />
                                <span v-if="linkKind === 'external'" class="sr-only">{{ t('popup.newWindow') }}</span>
                            </component>
                        </template>
                        <div v-else class="flex flex-col gap-3 px-6 pb-5 pt-6">
                            <h2 v-if="current.heading" class="m-0 text-lg font-bold leading-snug [overflow-wrap:anywhere]">{{ current.heading }}</h2>
                            <p class="m-0 max-h-[min(50dvh,420px)] overflow-y-auto whitespace-pre-line text-sm leading-relaxed text-surface-700 [overflow-wrap:anywhere] dark:text-surface-200">{{ current.body }}</p>
                            <a v-if="linkKind === 'external'" :href="current.link!" target="_blank" rel="noopener noreferrer" class="mt-1 inline-flex h-10 w-full items-center justify-center rounded-lg bg-primary text-sm font-semibold text-primary-contrast no-underline hover:opacity-90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary" @click="go()">
                                {{ current.buttonLabel || t('popup.buttonDefault') }}
                                <span class="sr-only">{{ t('popup.newWindow') }}</span>
                            </a>
                            <Button v-else-if="linkKind === 'internal'" type="button" :label="current.buttonLabel || t('popup.buttonDefault')" class="mt-1 h-10 w-full" @click="go()" />
                        </div>
                    </div>
                </Transition>

                <template v-if="list.length > 1">
                    <span class="pointer-events-none absolute right-3 top-3 rounded-full bg-black/55 px-2 py-0.5 text-[11px] font-medium tabular-nums text-white" aria-live="polite">{{ index + 1 }} / {{ list.length }}</span>
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
                    @click="hideCurrent"
                >
                    <i class="pi pi-check-circle text-[0.8rem] text-neutral-400" aria-hidden="true" />
                    <span class="truncate">{{ t(`popup.hide.${current.hideOption}`) }}</span>
                </button>
                <span v-else />
                <button type="button" class="inline-flex shrink-0 cursor-pointer items-center gap-1 rounded border-0 bg-transparent p-0 font-medium text-inherit hover:text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white" data-testid="site-popup-close" @click="closeAll">
                    {{ t('popup.close') }}
                    <i class="pi pi-times text-[0.7rem]" aria-hidden="true" />
                </button>
            </div>
        </div>
    </Dialog>
</template>

<script setup lang="ts">
import type { SitePopupHideOption } from '~/composables/popup/api/useSitePopupApi.types';
import UtilSitePopup, { type SitePopupHideRecords } from '~/utils/util-site-popup';

const { t } = useI18n();
const route = useRoute();
const isDesktop = useIsDesktop();
const { popups, closedIds, ensureLoaded, closeForVisit } = useSitePopups();
void ensureLoaded();

/** 보지 않기 기록 — 마운트 뒤 저장소에서 읽는다. 읽기 전에는 띄우지 않는다(닫은 팝업이 잠깐 비치지 않게) */
const records = ref<SitePopupHideRecords>({});
const storageRead = ref(false);
/** 판정 기준 시각 — 창을 연 동안 기간이 끝나도 다시 계산하지 않는다 */
const now = ref(0);

onMounted(() => {
    try {
        records.value = UtilSitePopup.parse(window.localStorage.getItem(UtilSitePopup.STORAGE_KEY));
    } catch {
        records.value = {};
    }
    now.value = Date.now();
    storageRead.value = true;
});

/** 지금 화면·기기에 띄울 팝업(서버 차례) */
const list = computed(() => {
    const value = popups.value;
    if (!storageRead.value || !value) return [];
    return UtilSitePopup.visible(value.items, { home: route.path === '/', device: isDesktop.value ? 'PC' : 'MOBILE', closed: closedIds.value, records: records.value, now: now.value });
});

const open = computed(() => list.value.length > 0);
const index = ref(0);
const current = computed(() => list.value[Math.min(index.value, list.value.length - 1)] ?? null);
const linkKind = computed(() => UtilSitePopup.linkKind(current.value?.link ?? null));

/* ── 넘기기 ── */
const direction = ref<1 | -1>(1);
const slideName = computed(() => (direction.value === 1 ? 'popup-next' : 'popup-prev'));

function step(delta: -1 | 1): void {
    const count = list.value.length;
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

/* ── 닫기 ── */

/** 「닫기」·Esc·바깥 — 지금 창에 있는 팝업 전부를 이번 방문 동안 닫는다(모든 화면 팝업도 다른 화면에서 다시 뜨지 않는다) */
function closeAll(): void {
    closeForVisit(list.value.map((item) => item.id));
    index.value = 0;
}

/** 「보지 않기」 — 지금 장만 이 브라우저에 기록하고 빠진다(남은 장이 있으면 그 자리의 다음 장) */
function hideCurrent(): void {
    const item = current.value;
    if (!item || item.hideOption === 'NONE') return;
    const at = Date.now();
    const next = UtilSitePopup.prune({ ...records.value, [item.id]: UtilSitePopup.record(item.version, item.hideOption as Exclude<SitePopupHideOption, 'NONE'>, at) }, popups.value?.items.map((row) => row.id) ?? [], at);
    try {
        window.localStorage.setItem(UtilSitePopup.STORAGE_KEY, JSON.stringify(next));
        records.value = next;
    } catch {
        // 저장소를 못 쓴다 — 이번 방문 동안만 뺀다
        closeForVisit([item.id]);
    }
    // 마지막 장이었으면 앞 장으로(남은 것이 없으면 창이 닫힌다)
    if (index.value >= list.value.length) index.value = Math.max(0, list.value.length - 1);
}

/** 링크로 간다 — 창은 이번 방문 동안 닫는다. 바깥 주소는 `<a target=_blank>` 가 연다 */
function go(): void {
    const link = current.value?.link ?? null;
    const kind = linkKind.value;
    closeAll();
    if (kind === 'internal' && link) void navigateTo(link);
}

// 보일 곳이 바뀌면(홈 ↔ 다른 화면) 첫 장부터
watch(
    () => list.value.map((item) => item.id).join(','),
    () => {
        if (index.value >= list.value.length) index.value = 0;
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
