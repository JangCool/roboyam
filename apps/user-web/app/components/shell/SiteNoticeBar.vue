<template>
    <!--
        공지 띠 — 모든 화면의 메뉴 줄(헤더·상단 메뉴) 아래, 본문 위(기본 레이아웃의 `after-header` 맨 끝에 한 번 놓는다). 홈 맨 아래 투자 고지 띠 자리를 대신한다(사용자 결정 2026-11-06).
        크기는 투자 고지 띠(`BoardInvestmentNotice`)와 같다 — 본문 폭 안 한 줄 상자(반경 lg · px-3 py-2 · 글자 xs).
        띠 위아래 여백은 같다(0.5rem) — 아래쪽은 본문 줄의 위 여백을 `main.css` 의 `.site-notice-bar` 규칙이 줄여 맞춘다.
        내용(`useSiteNotice`)은 운영자 설정 > 일반 「공지 띠」가 정한다: 공지 게시판 최근 N개(5·10)를 한 줄씩 차례로 보이거나 · 직접 입력 문구 한 줄 · 꺼짐(그리지 않는다).
          여러 줄이면 5초마다 다음 줄로 넘어간다. 마우스를 올리거나 띠 안에 초점이 있으면 멈추고, ‹ › 단추로 직접 넘길 수 있다(움직임 줄이기 설정이면 미끄러지지 않고 바로 바뀐다).
        오른쪽 끝 X → 닫을 기간 고르기(하루 · 1주일 · 1달 · 영원히). 「영원히」는 확인창에서 「신속히 확인하지 못한 피해는 본인 책임」을 알리고 한 번 더 묻는다.
          닫음 기록은 이 브라우저에 공지 묶음 표식(`version`)별로 남는다(`UtilSiteNotice`) — 공지가 바뀌면 기간이 남았어도 다시 보인다. 저장소를 못 쓰면 이번 화면에서만 닫힌다.
        모바일 안쪽 화면(글 상세·글쓰기 — 셸 막대를 숨기는 곳)에서는 레이아웃이 숨긴다.
    -->
    <div v-if="items.length" class="site-notice-bar mx-auto w-full max-w-[var(--content-max)] px-4 pt-2" data-testid="site-notice-bar" data-cache-area="site.noticeBar">
        <aside
            class="flex items-center gap-2 rounded-lg border border-hairline-strong bg-card py-1 pl-3 pr-1 text-xs"
            :aria-label="t('siteNotice.label')"
            @mouseenter="hovered = true"
            @mouseleave="hovered = false"
            @focusin="focused = true"
            @focusout="onFocusOut"
        >
            <span class="inline-flex shrink-0 items-center gap-1 font-semibold text-primary">
                <i class="pi pi-megaphone text-[0.8rem]" aria-hidden="true" />
                {{ t('siteNotice.badge') }}
            </span>

            <!-- 한 줄씩 — 넘어갈 때 위로 미끄러진다. 화면 읽기 프로그램에 자동으로 넘어가는 줄을 읽히지 않는다(시끄럽다) -->
            <div class="relative h-7 min-w-0 flex-1 overflow-hidden">
                <Transition name="notice-roll">
                    <div :key="current.key" class="absolute inset-0 flex items-center gap-2">
                        <NuxtLink v-if="current.kind === 'internal'" :to="current.link!" class="min-w-0 truncate rounded hover:underline focus-visible:outline-2 focus-visible:outline-primary" data-testid="site-notice-link">{{ current.title }}</NuxtLink>
                        <a v-else-if="current.kind === 'external'" :href="current.link!" target="_blank" rel="noopener noreferrer" class="min-w-0 truncate rounded hover:underline focus-visible:outline-2 focus-visible:outline-primary" data-testid="site-notice-link">
                            {{ current.title }}
                            <span class="sr-only">{{ t('siteNotice.newWindow') }}</span>
                        </a>
                        <span v-else class="min-w-0 truncate">{{ current.title }}</span>
                        <span v-if="current.date" class="shrink-0 text-muted-color max-sm:hidden">{{ current.date }}</span>
                    </div>
                </Transition>
            </div>

            <!-- 여러 줄이면 ‹ › (몇 번째인지는 보이지 않는다 — 사용자 결정 2026-11-06) -->
            <div v-if="items.length > 1" class="flex shrink-0 items-center text-muted-color">
                <button type="button" class="inline-flex size-7 shrink-0 items-center justify-center rounded-full text-muted-color hover:bg-surface-100 hover:text-color focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-surface-800" :aria-label="t('siteNotice.prev')" data-testid="site-notice-prev" @click="step(-1)">
                    <i class="pi pi-chevron-left text-[0.65rem]" aria-hidden="true" />
                </button>
                <button type="button" class="inline-flex size-7 shrink-0 items-center justify-center rounded-full text-muted-color hover:bg-surface-100 hover:text-color focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-surface-800" :aria-label="t('siteNotice.next')" data-testid="site-notice-next" @click="step(1)">
                    <i class="pi pi-chevron-right text-[0.65rem]" aria-hidden="true" />
                </button>
            </div>

            <button
                type="button"
                class="inline-flex size-7 shrink-0 items-center justify-center rounded-full text-muted-color hover:bg-surface-100 hover:text-color focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-surface-800"
                :aria-label="t('siteNotice.close')"
                aria-haspopup="menu"
                data-testid="site-notice-close"
                @click="hideMenu?.toggle($event)"
            >
                <i class="pi pi-times text-[0.7rem]" aria-hidden="true" />
            </button>
            <Menu ref="hideMenu" :model="hideItems" popup :aria-label="t('siteNotice.hideMenu')">
                <template #start>
                    <p class="m-0 px-3 pb-1 pt-2 text-xs text-muted-color">{{ t('siteNotice.hideMenu') }}</p>
                </template>
            </Menu>
        </aside>
    </div>
</template>

<script setup lang="ts">
import UtilSiteNotice, { type SiteNoticeDismissal, type SiteNoticeHidePeriod } from '~/utils/util-site-notice';

const { t } = useI18n();
const time = useDisplayTime();
const { confirm } = useConfirmDialog();
const { notice, ensureLoaded } = useSiteNotice();
void ensureLoaded();

/** 닫음 기록 — 마운트 뒤 저장소에서 읽는다. 읽기 전에는 띠를 그리지 않는다(닫은 띠가 잠깐 비치지 않게) */
const dismissal = ref<SiteNoticeDismissal | null>(null);
const storageRead = ref(false);
/** 판정 기준 시각 — 띠를 연 동안 기간이 끝나도 다시 그리지 않는다(다음 화면 열기 때 다시 본다) */
const now = ref(0);

onMounted(() => {
    try {
        dismissal.value = UtilSiteNotice.parse(window.localStorage.getItem(UtilSiteNotice.STORAGE_KEY));
    } catch {
        dismissal.value = null;
    }
    now.value = Date.now();
    storageRead.value = true;
});

const items = computed(() => {
    const value = notice.value;
    if (!storageRead.value || !value || UtilSiteNotice.hidden(dismissal.value, value.version, now.value)) return [];
    return value.items;
});

/* ── 차례로 넘기기 ─────────────────────────── */

const ROLL_MS = 5000;
const index = ref(0);
const hovered = ref(false);
const focused = ref(false);

const current = computed(() => {
    const item = items.value[index.value] ?? items.value[0]!;
    return {
        key: `${index.value}:${item.postId ?? item.title}`,
        title: item.title,
        link: item.link,
        kind: UtilSiteNotice.linkKind(item.link),
        date: item.publishedAt ? time.value.date(item.publishedAt) : null,
    };
});

function step(delta: number): void {
    const count = items.value.length;
    if (count > 1) index.value = (index.value + delta + count) % count;
}

function onFocusOut(event: FocusEvent): void {
    const next = event.relatedTarget as Node | null;
    focused.value = Boolean(next && (event.currentTarget as HTMLElement).contains(next));
}

let timer: ReturnType<typeof setInterval> | null = null;
onMounted(() => {
    timer = setInterval(() => {
        if (hovered.value || focused.value || document.hidden) return;
        step(1);
    }, ROLL_MS);
});
onBeforeUnmount(() => {
    if (timer) clearInterval(timer);
});

/* ── 닫기 ─────────────────────────── */

const hideMenu = ref<{ toggle: (event: Event) => void } | null>(null);
const hideItems = computed(() =>
    UtilSiteNotice.PERIODS.map((period) => ({
        label: t(`siteNotice.hide.${period}`),
        command: () => void hide(period),
    })),
);

async function hide(period: SiteNoticeHidePeriod): Promise<void> {
    const version = notice.value?.version;
    if (!version) return;
    if (period === 'FOREVER') {
        const ok = await confirm({
            title: t('siteNotice.foreverTitle'),
            message: t('siteNotice.foreverMessage'),
            confirmLabel: t('siteNotice.foreverConfirm'),
        });
        if (!ok) return;
    }
    const record = UtilSiteNotice.dismissal(version, period, Date.now());
    dismissal.value = record;
    now.value = Date.now();
    try {
        window.localStorage.setItem(UtilSiteNotice.STORAGE_KEY, JSON.stringify(record));
    } catch {
        // 저장소를 못 쓴다 — 이번 화면에서만 닫는다
    }
}
</script>

<style scoped>
.notice-roll-enter-active,
.notice-roll-leave-active {
    transition:
        transform 0.35s ease,
        opacity 0.35s ease;
}

.notice-roll-enter-from {
    transform: translateY(100%);
    opacity: 0;
}

.notice-roll-leave-to {
    transform: translateY(-100%);
    opacity: 0;
}

@media (prefers-reduced-motion: reduce) {
    .notice-roll-enter-active,
    .notice-roll-leave-active {
        transition: none;
    }
}
</style>
