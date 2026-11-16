<template>
    <!--
        글 상세 맨 아래 탭 묶음 [인기글] [최신글](사용자 2026-11-16 — 디시처럼, 기본 인기글). 게시판 `detailList` 와 상관없이·소모임 글에도 보인다.
        탭은 `/popular` 기간 탭과 같은 방식(role=tablist/tab/tabpanel — 고른 탭만 Tab 키로 닿고 화살표·Home·End 로 초점만 옮긴 뒤 Enter·Space 로 고른다).
        줄은 인기글·최신글 화면의 `FeedPostItem`(PC A 방식 두 줄·모바일 줄 그대로, 인기글은 순위 번호), 지금 글이면 강조. 탭 아래 [더 보기] → `/popular` · `/latest`.
        화면이 근처(위아래 600px)에 오면 `near` 를 한 번 올린다 — 받기·기억은 페이지가 컴포저블(`usePostDetailFeedTabs`)로 한다. 그리기만 한다.
        PC·휴대폰 표시(게시판 `detailFeedTabsPc`·`detailFeedTabsMobile` — 2차)는 페이지가 정한다: 끈 기기에서는 페이지가 `md:hidden`·`max-md:hidden` 으로 숨기고(숨긴 요소는 화면에 들어오지 않아 `near` 가 안 올라가 부르지도 않는다), 둘 다 끄면 그리지 않는다.
    -->
    <section ref="root" class="content-card" :aria-label="t('feed.detailTabs')" data-testid="post-detail-feed-tabs">
        <!-- 탭 줄 위 여백은 다른 카드 머리(댓글 카드 머리 위 14)와 맞춘다. 휴대폰은 두 탭이 가로를 반씩(5:5) 채운다(2차 — 사용자 2026-11-16) -->
        <div role="tablist" :aria-label="t('feed.detailTabs')" class="flex border-b border-surface pt-2 md:gap-3.5" @keydown="onTabKeydown">
            <button
                v-for="tab in TABS"
                :id="tabId(tab)"
                :key="tab"
                type="button"
                role="tab"
                :data-tab="tab"
                :aria-selected="tab === active"
                :aria-controls="panelId"
                :tabindex="tab === active ? 0 : -1"
                :class="[TAB, tab === active ? TAB_ON : TAB_OFF]"
                @click="emit('select', tab)"
            >
                {{ nameOf(tab) }}
            </button>
        </div>

        <div :id="panelId" class="mt-2" role="tabpanel" :aria-labelledby="tabId(active)" :aria-busy="current.state === 'loading'" :data-cache-area="active === 'POPULAR' ? 'home.popular' : 'home.latest'">
            <div v-if="current.state === 'idle' || current.state === 'loading'" role="status" :aria-label="t('feed.loading', { name: nameOf(active) })"><FeedSkeleton /></div>

            <ErrorState v-else-if="current.state === 'error'" :title="t('feed.loadFailed', { name: nameOf(active) })" :description="current.errorMessage" retry @retry="emit('retry', active)" />

            <EmptyState v-else-if="current.items.length === 0" :title="active === 'POPULAR' ? UtilFeedMessages.popularEmpty(t, UtilPopular.DEFAULT_PERIOD).title : t('feed.latestEmptyTitle')" icon="pi pi-file" />

            <ul v-else class="-mx-4 m-0 list-none p-0 md:mx-0">
                <FeedPostItem
                    v-for="(row, i) in current.items"
                    :key="row.id"
                    :row="row"
                    :rank="active === 'POPULAR' ? i + 1 : 0"
                    :fill="active === 'POPULAR' ? UtilPopular.fillOf(current.fills, row.id) : null"
                    strong-title
                    desktop-row
                    :active="row.id === activeId"
                    :date-display="dateDisplay"
                    :new-post-hours="newPostHours"
                    :chip-position="chipPosition"
                />
            </ul>

            <div class="mt-2 flex justify-end">
                <NuxtLink
                    :to="active === 'POPULAR' ? '/popular' : '/latest'"
                    class="inline-flex min-h-9 items-center gap-1 text-sm text-muted-color hover:text-color focus-visible:outline-2 focus-visible:outline-primary"
                    :data-testid="`post-detail-feed-more-${active.toLowerCase()}`"
                >
                    {{ t('feed.detailMore', { name: nameOf(active) }) }}<i class="pi pi-angle-right text-xs" aria-hidden="true" />
                </NuxtLink>
            </div>
        </div>
    </section>
</template>

<script setup lang="ts">
import type { BoardDateDisplay, PostChipPosition } from '~/composables/board/api/useBoardApi.types';
import type { PostDetailFeedList, PostDetailFeedTab } from '~/composables/home/usePostDetailFeedTabs';
import UtilFeedMessages from '~/utils/util-feed-messages';
import UtilPopular from '~/utils/util-popular';

interface Props {
    active: PostDetailFeedTab;
    lists: Record<PostDetailFeedTab, PostDetailFeedList>;
    /** 지금 보는 글 — 목록에 있으면 강조 */
    activeId: string;
    dateDisplay: BoardDateDisplay;
    newPostHours: number;
    /** 말머리·상태 칩 위치 — 공개 표시 기본값의 `listChipPosition`(인기글·최신글 화면과 같다) */
    chipPosition?: PostChipPosition;
}

const props = withDefaults(defineProps<Props>(), { chipPosition: 'ABOVE' });
const emit = defineEmits<{ (e: 'near'): void; (e: 'select', tab: PostDetailFeedTab): void; (e: 'retry', tab: PostDetailFeedTab): void }>();

const TABS: readonly PostDetailFeedTab[] = ['POPULAR', 'LATEST'];
const TAB = 'relative cursor-pointer whitespace-nowrap inline-flex min-h-11 items-center max-md:flex-1 max-md:justify-center border-b-2 -mb-px text-[0.9375rem] leading-none after:absolute after:inset-x-0 after:-inset-y-1 after:content-[""] focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-primary';
const TAB_ON = 'border-primary font-bold text-color';
const TAB_OFF = 'border-transparent text-muted-color hover:text-color';

const { t } = useI18n();
const { term } = useSiteTerms();
const uid = useId();
const panelId = `${uid}-panel`;

const current = computed(() => props.lists[props.active]);

/** 탭 이름 — 서비스 이름(운영자가 바꿀 수 있다 — 인기글·최신글 화면 제목과 같다) */
function nameOf(tab: PostDetailFeedTab): string {
    return term(tab === 'POPULAR' ? 'POPULAR' : 'LATEST');
}
function tabId(tab: PostDetailFeedTab): string {
    return `${uid}-tab-${tab.toLowerCase()}`;
}

/** 화살표·Home·End 로 초점만 옮긴다(눌러야 고른다 — `/popular` 기간 탭과 같다) */
function onTabKeydown(event: KeyboardEvent): void {
    const raw = (event.target as HTMLElement | null)?.closest<HTMLElement>('[data-tab]')?.dataset.tab;
    const at = TABS.findIndex((tab) => tab === raw);
    if (at < 0) return;
    const last = TABS.length - 1;
    const next = event.key === 'ArrowRight' ? (at >= last ? 0 : at + 1) : event.key === 'ArrowLeft' ? (at <= 0 ? last : at - 1) : event.key === 'Home' ? 0 : event.key === 'End' ? last : -1;
    if (next < 0) return;
    event.preventDefault();
    (event.currentTarget as HTMLElement).querySelector<HTMLElement>(`[data-tab="${TABS[next]}"]`)?.focus();
}

/* ── 화면이 근처에 오면 한 번 알린다 ── */
const root = ref<HTMLElement | null>(null);
let observer: IntersectionObserver | null = null;
onMounted(() => {
    const el = root.value;
    if (!el) return;
    if (typeof IntersectionObserver === 'undefined') {
        emit('near');
        return;
    }
    observer = new IntersectionObserver(
        (entries) => {
            if (!entries.some((e) => e.isIntersecting)) return;
            observer?.disconnect();
            observer = null;
            emit('near');
        },
        { rootMargin: '600px 0px' },
    );
    observer.observe(el);
});
onBeforeUnmount(() => observer?.disconnect());
</script>
