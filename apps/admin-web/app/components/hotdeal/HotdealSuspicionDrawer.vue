<template>
    <!--
        의심 후보 서랍 — 의심 후보 줄을 누르면(또는 `?suspicion=` 로 들어오면) 연다. 「보고 판단」하는 자리라 제재 기능은 없고 기존 화면으로 가는 링크만 둔다(03-hotdeal 7-1).
        본문: 회원(탈퇴 Tag) · 판매처 · 기간 안 건수 · 다른 회원 수 · 처음 감지 · (무시했으면 누가·언제·메모) → 그 회원의 그 판매처 글 최근 10개(상태 · 딜가 · 신고 대기 — 글은 핫딜 글 서랍 `/hotdeals?deal=` 로).
        바닥: [무시](열린 후보 · `hotdeal.manage`) · 회원 관리 · 신고 관리 링크. 서버 호출은 하지 않는다(부른 화면이 한다).
    -->
    <Drawer
        :visible="open"
        :modal="false"
        :dismissable="false"
        :position="prefs.drawerPosition"
        :aria-labelledby="titleId"
        :style="{ width: '36rem', maxWidth: '100vw' }"
        :pt="{ content: { class: 'pt-0!' } }"
        @update:visible="(v: boolean) => (open = v)"
        @after-hide="emit('closed')"
    >
        <template #header>
            <div class="flex min-w-0 flex-1 flex-col gap-1.5">
                <h2 :id="titleId" class="min-w-0 text-base font-semibold break-words">{{ head ? t('hotdeal.suspicion.drawerTitle', { name: head.member.nickname, store: head.storeName }) : t('hotdeal.suspicion.title') }}</h2>
                <div v-if="head" class="flex flex-wrap items-center gap-2">
                    <Tag :value="t(`hotdeal.suspicion.status.${head.status}`)" :severity="head.status === 'OPEN' ? 'warn' : 'secondary'" class="text-[11px]!" />
                    <Tag v-if="head.member.withdrawn" :value="t('hotdeal.withdrawn')" severity="secondary" class="text-[11px]!" />
                </div>
            </div>
        </template>

        <ErrorState v-if="error" :title="t('hotdeal.suspicion.detailLoadFailed')" :description="error.message" :code="error.status === 404 ? '404' : '!'" :retry="error.status !== 404" :retrying="loading" @retry="emit('retry')" />

        <div v-else-if="!detail" class="flex flex-col gap-3 py-4" role="status" :aria-label="t('common.loading')" aria-busy="true">
            <Skeleton v-for="n in 5" :key="n" height="1.25rem" />
        </div>

        <div v-else class="flex flex-col gap-5 pt-1" :aria-busy="loading">
            <dl class="m-0 grid grid-cols-[max-content_minmax(0,1fr)] gap-x-4 gap-y-1.5 text-[13px]">
                <dt class="text-muted-color">{{ t('hotdeal.suspicion.col.member') }}</dt>
                <dd class="m-0 flex flex-wrap items-center gap-2">
                    <span :class="s.member.withdrawn ? 'text-muted-color' : ''">{{ s.member.nickname }}</span>
                    <span class="text-xs text-muted-color">#{{ s.member.id }}</span>
                </dd>
                <dt class="text-muted-color">{{ t('hotdeal.suspicion.col.store') }}</dt>
                <dd class="m-0">{{ s.storeName }} <span class="font-mono text-xs text-muted-color">{{ s.domain }}</span></dd>
                <dt class="text-muted-color">{{ t('hotdeal.suspicion.col.count') }}</dt>
                <dd class="m-0 tabular-nums">{{ t('hotdeal.suspicion.countValue', { n: s.postCount, days: s.windowDays }) }}</dd>
                <dt class="text-muted-color">{{ t('hotdeal.suspicion.col.others') }}</dt>
                <dd class="m-0 tabular-nums">{{ t('hotdeal.suspicion.othersValue', { n: s.othersCount }) }}</dd>
                <dt class="text-muted-color">{{ t('hotdeal.suspicion.col.firstDetected') }}</dt>
                <dd class="m-0 tabular-nums">{{ time.full(s.firstDetectedAt) }}</dd>
                <template v-if="s.status === 'DISMISSED'">
                    <dt class="text-muted-color">{{ t('hotdeal.suspicion.dismissedLabel') }}</dt>
                    <dd class="m-0">{{ t('hotdeal.suspicion.dismissedValue', { name: s.dismissedBy ?? '—', at: time.full(s.dismissedAt) }) }}</dd>
                    <template v-if="s.note">
                        <dt class="text-muted-color">{{ t('hotdeal.suspicion.noteLabel') }}</dt>
                        <dd class="m-0 break-words">{{ s.note }}</dd>
                    </template>
                </template>
            </dl>
            <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('hotdeal.suspicion.othersHint') }}</p>

            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-posts`">
                <h3 :id="`${titleId}-posts`" class="text-sm font-semibold">{{ t('hotdeal.suspicion.postsTitle', { n: detail.posts.length }) }}</h3>
                <p v-if="detail.posts.length === 0" class="m-0 text-[13px] text-muted-color">{{ t('hotdeal.suspicion.postsEmpty') }}</p>
                <ol v-else class="m-0 flex list-none flex-col divide-y divide-(--line) rounded-lg border border-(--line) p-0">
                    <li v-for="p in detail.posts" :key="p.postId" class="flex flex-col gap-1 px-3 py-2">
                        <div class="flex min-w-0 flex-wrap items-center gap-1.5">
                            <HotdealViewTag :view="p.view" />
                            <NuxtLink :to="{ path: '/hotdeals', query: { view: 'ALL', deal: p.postId } }" class="min-w-0 text-[13px] font-medium break-words text-primary hover:underline">{{ p.title }}</NuxtLink>
                        </div>
                        <p class="m-0 flex flex-wrap items-center gap-x-2 text-xs text-muted-color tabular-nums">
                            <span>{{ localizedName(p.board.name, p.board.nameEn) }}</span>
                            <span>{{ UtilHotdealMessages.won(t, locale, p.dealPrice) }}</span>
                            <time :datetime="p.createdAt" :title="time.full(p.createdAt)">{{ time.listLabel(p.createdAt) }}</time>
                            <NuxtLink v-if="p.openReports > 0 && canReadReports" :to="UtilPost.reportsLink(p.postId)" class="text-red-600 hover:underline dark:text-red-400">{{ t('hotdeal.reportsPending', { n: p.openReports }) }}</NuxtLink>
                            <span v-else-if="p.openReports > 0" class="text-red-600 dark:text-red-400">{{ t('hotdeal.reportsPending', { n: p.openReports }) }}</span>
                        </p>
                    </li>
                </ol>
            </section>
        </div>

        <template v-if="detail && !error" #footer>
            <div class="flex w-full flex-col gap-2 border-t border-(--line) pt-3">
                <p class="m-0 text-xs text-muted-color">{{ t('hotdeal.suspicion.sanctionHint') }}</p>
                <div class="flex flex-wrap items-center justify-end gap-2">
                    <Button v-if="canReadMembers && !s.member.withdrawn" type="button" :label="t('hotdeal.findMember')" icon="pi pi-user" size="small" severity="secondary" variant="outlined" @click="go('/members')" />
                    <Button v-if="canReadReports" type="button" :label="t('hotdeal.suspicion.openReports')" icon="pi pi-flag" size="small" severity="secondary" variant="outlined" @click="go('/reports')" />
                    <Button v-if="s.status === 'OPEN' && canManage" type="button" :label="t('hotdeal.suspicion.dismiss')" icon="pi pi-eye-slash" size="small" :disabled="busy || loading" @click="emit('dismiss')" />
                </div>
                <p v-if="s.status === 'OPEN' && !canManage" class="m-0 flex items-center gap-1.5 text-xs text-muted-color"><i class="pi pi-lock" aria-hidden="true" />{{ t('hotdeal.readOnly') }}</p>
            </div>
        </template>
    </Drawer>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { DealSuspicion, DealSuspicionDetail } from '~/composables/hotdeal/api/useHotdealSuspicionApi.types';
import UtilHotdealMessages from '~/utils/util-hotdeal-messages';
import UtilPost from '~/utils/util-post';

const open = defineModel<boolean>('visible', { required: true });

interface Props {
    item?: DealSuspicion | null;
    detail?: DealSuspicionDetail | null;
    loading?: boolean;
    error?: ApiError | null;
    busy?: boolean;
    canManage?: boolean;
    canReadReports?: boolean;
    canReadMembers?: boolean;
}

const props = withDefaults(defineProps<Props>(), { item: null, detail: null, loading: false, error: null, busy: false, canManage: false, canReadReports: false, canReadMembers: false });
const emit = defineEmits<{ retry: []; dismiss: []; closed: [] }>();

const { t, locale } = useI18n();
const { prefs } = useLayoutPrefs();
const { localizedName } = useLocalizedName();
const time = useDisplayTime();
const titleId = useId();

const head = computed(() => props.detail?.suspicion ?? props.item);
/** 본문이 그리는 후보 — 템플릿은 `detail` 이 있을 때만 읽는다 */
const s = computed(() => props.detail?.suspicion as DealSuspicion);

function go(path: string): void {
    void navigateTo(path);
}

const INSIDE = '.p-drawer, .p-drawer-mask, tbody tr, .p-dialog, .p-dialog-mask, .p-overlay, .p-menu, .p-popover, .p-tooltip, .p-toast, [role="alertdialog"], [role="listbox"]';
function onPointerDown(e: PointerEvent): void {
    const target = e.target as Element | null;
    if (!target || target.closest(INSIDE)) return;
    open.value = false;
}
watch(
    open,
    (isOpen) => {
        if (isOpen) document.addEventListener('pointerdown', onPointerDown, true);
        else document.removeEventListener('pointerdown', onPointerDown, true);
    },
    { immediate: true },
);
onBeforeUnmount(() => document.removeEventListener('pointerdown', onPointerDown, true));
</script>
