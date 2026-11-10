<template>
    <!--
        반복 검토 탭의 서랍 — 표 줄을 누르면(또는 `?review=` 로 들어오면) 연다. 게시판 제재 서랍과 같은 구조(표를 가리지 않음 · 바깥 누르면 닫힘 · 이전/다음은 지금 쪽의 줄 순서).
        본문: 요약(상태 · 번호 · 회원 · 게시판 · 7일 건수 · 열린/닫은 시각) + 「여러 게시판」 배지 → 그 게시판에서 받은 최근 제재(처리자 포함, 이 검토가 센 것에 「검토 대상」)
          → 다른 게시판 요약 → 그 회원의 사이트 제재 현황(`SanctionPreviewSummary` — 부른 화면이 `useSanctionPreview` 로 받아 내려 준다) → 닫힌 건이면 닫은 결과.
          봉인 줄(`user.sealed` — 익명 글 작성자)은 회원 칸이 자물쇠 + 「익명 글 작성자(봉인)」이고, 다른 게시판·사이트 제재 현황·회원 링크 대신 봉인 안내 한 줄.
          「사이트 제재 부여」는 `sealedSource` 가 있을 때만 고를 수 있다(부른 화면이 「이 글(댓글) 작성자」 대상으로 대화창을 연다).
        바닥(열린 건 + `sanction.manage`): 닫기 셋 중 하나 — 「그대로 둠」 · 「사이트 제재 부여」(부른 화면이 기존 제재 대화창을 열고, 부여되면 그 번호로 닫는다) ·
          「게시판 제재 무효」(본문 목록에서 이 검토가 센 제재를 골라 무효로 하고 닫는다) + 메모 0~200자(선택). `409 BOARD_SANCTION_REVIEW_CLOSED` 는 「이미 닫혔어요 — 다시 불러오기」.
        고른 결과·메모·고른 제재는 다른 검토를 열 때마다 비운다. 서버 호출은 하지 않는다 — `submit` 으로 알린다.
    -->
    <Drawer
        :visible="open"
        class="board-sanction-review-drawer"
        :modal="false"
        :dismissable="false"
        :position="prefs.drawerPosition"
        :aria-labelledby="titleId"
        :style="{ width: '42rem', maxWidth: '100vw' }"
        :pt="{ content: { class: 'pt-0!' } }"
        @update:visible="(v: boolean) => (open = v)"
        @after-hide="emit('closed')"
    >
        <template #header>
            <div class="flex min-w-0 flex-1 items-start gap-2">
                <h2 :id="titleId" class="min-w-0 flex-1 text-base font-semibold break-words">{{ head ? t('boardSanction.review.drawerTitle', { no: head.no, name: UtilSanctionMessages.userName(t, head.user) }) : t('boardSanction.review.drawerTitleEmpty') }}</h2>
                <div class="flex shrink-0 items-center gap-0.5">
                    <Button v-tooltip.bottom="t('boardSanction.review.prev')" type="button" icon="pi pi-chevron-up" size="small" severity="secondary" variant="text" rounded :aria-label="t('boardSanction.review.prev')" :disabled="!hasPrev" @click="emit('prev')" />
                    <Button v-tooltip.bottom="t('boardSanction.review.next')" type="button" icon="pi pi-chevron-down" size="small" severity="secondary" variant="text" rounded :aria-label="t('boardSanction.review.next')" :disabled="!hasNext" @click="emit('next')" />
                </div>
            </div>
        </template>

        <ErrorState v-if="error" :title="t('boardSanction.review.detailFailed')" :description="error.message" :code="error.status === 404 ? '404' : '!'" :retry="error.status !== 404" :retrying="loading" @retry="emit('retry')" />

        <div v-else-if="!detail" class="flex flex-col gap-3 py-4" role="status" :aria-label="t('common.loading')" aria-busy="true">
            <Skeleton width="60%" height="1.25rem" />
            <Skeleton v-for="n in 6" :key="n" height="1rem" />
        </div>

        <div v-else class="flex flex-col gap-5 pt-1" :aria-busy="loading">
            <DrawerSummary class="mt-2" :label="t('drawerSummary.label')" :items="summaryItems">
                <template #item-state><BoardSanctionTag type="review" :status="detail.status" /></template>
                <template v-if="sealed" #item-member><AnonymousSealedAuthor :label="t('anonymousAuthor.sanctionTarget')" /></template>
            </DrawerSummary>

            <p class="m-0 flex flex-wrap items-center gap-2 text-[13px]">
                <i class="pi pi-info-circle text-[12px] text-muted-color" aria-hidden="true" />
                <span>{{ t('boardSanction.review.why', { board: boardName, n: detail.recentCount }) }}</span>
                <span v-if="UtilBoardSanction.isMultiBoard(detail.recentBoardCount)" v-tooltip.bottom="t('boardSanction.review.multiBoardHint', { n: detail.recentBoardCount })" class="inline-flex items-center gap-1 rounded-full bg-red-100 px-2 py-0.5 text-[11px] font-medium text-red-800 dark:bg-red-950 dark:text-red-300">
                    <i class="pi pi-clone text-[9px]" aria-hidden="true" />{{ t('boardSanction.review.multiBoard') }}
                </span>
            </p>

            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-board`">
                <h3 :id="`${titleId}-board`" class="text-sm font-semibold">{{ t('boardSanction.review.boardSanctionsTitle', { board: boardName }) }}</h3>
                <BoardSanctionHistoryList v-model:selected="voidIds" :lines="detail.sanctions" :show-board="false" link-sanction :marked-ids="detail.reviewSanctionIds" :selectable-ids="picking ? voidableIds : []" :busy="busy" />
            </section>

            <!-- 봉인 줄(익명 글 작성자 — anonymous-board-contract 정책 9) — 회원 이력(다른 게시판 · 사이트 제재 현황)과 회원 링크를 보이지 않는다(신원은 열람 창으로만) -->
            <p v-if="sealed" class="m-0 flex items-center gap-1.5 rounded-lg border border-dashed border-(--line) px-3 py-2 text-xs text-muted-color" data-testid="board-sanction-review-sealed-note">
                <i class="pi pi-lock text-[11px]" aria-hidden="true" />{{ t('anonymousAuthor.sanctionSealedNote') }}
            </p>

            <section v-if="!sealed" class="flex flex-col gap-2" :aria-labelledby="`${titleId}-others`">
                <h3 :id="`${titleId}-others`" class="text-sm font-semibold">{{ t('boardSanction.review.otherBoardsTitle') }}</h3>
                <p v-if="!detail.otherBoards.length" class="m-0 text-[13px] text-muted-color">{{ t('boardSanction.review.otherBoardsEmpty') }}</p>
                <ul v-else class="m-0 flex list-none flex-col divide-y divide-(--line) rounded-lg border border-(--line) p-0">
                    <li v-for="other in detail.otherBoards" :key="other.board.id" class="flex flex-wrap items-center gap-x-3 gap-y-0.5 px-3 py-2 text-[13px]">
                        <span class="min-w-0 flex-1 truncate font-medium">{{ localizedName(other.board.name, other.board.nameEn) }}</span>
                        <span class="text-xs tabular-nums text-muted-color">{{ t('boardSanction.review.otherBoardValue', { recent: other.recentCount, total: other.total }) }}</span>
                        <span v-if="other.activeUntil" class="text-xs tabular-nums text-red-600 dark:text-red-400">{{ t('boardSanction.review.activeUntil', { at: time.full(other.activeUntil) }) }}</span>
                    </li>
                </ul>
            </section>

            <section v-if="!sealed" class="flex flex-col gap-2" :aria-labelledby="`${titleId}-site`">
                <div class="flex flex-wrap items-center justify-between gap-2">
                    <h3 :id="`${titleId}-site`" class="text-sm font-semibold">{{ t('boardSanction.review.siteTitle') }}</h3>
                    <NuxtLink v-if="detail.user.id" :to="{ path: '/sanctions', query: { userId: detail.user.id } }" class="inline-flex items-center gap-1 rounded px-1 text-xs text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary">
                        <i class="pi pi-shield text-[10px]" aria-hidden="true" />{{ t('boardSanction.review.openSiteSanctions') }}
                    </NuxtLink>
                </div>
                <ErrorState v-if="sitePreviewError" :title="t('sanction.previewFailed')" :description="sitePreviewError.message" :code="sitePreviewError.status === 404 ? '404' : '!'" :retry="sitePreviewError.status !== 404" :retrying="sitePreviewLoading" @retry="emit('retry-site')" />
                <Skeleton v-else-if="!sitePreview" height="4.5rem" />
                <SanctionPreviewSummary v-else :preview="sitePreview" />
            </section>

            <section v-if="detail.status === 'CLOSED'" class="flex flex-col gap-2" :aria-labelledby="`${titleId}-result`">
                <h3 :id="`${titleId}-result`" class="text-sm font-semibold">{{ t('boardSanction.review.resultTitle') }}</h3>
                <div class="flex flex-col gap-1.5 rounded-lg border border-(--line) px-3 py-2.5">
                    <p class="m-0 flex flex-wrap items-center gap-1.5 text-[13px]">
                        <BoardSanctionTag v-if="detail.result" type="result" :status="detail.result" />
                        <span class="text-xs text-muted-color tabular-nums">{{ t('boardSanction.review.closedByLine', { name: UtilSanctionMessages.actor(t, detail.closedBy), at: time.full(detail.closedAt) }) }}</span>
                    </p>
                    <NuxtLink v-if="detail.siteSanction" :to="{ path: '/sanctions', query: { sanction: detail.siteSanction.id } }" class="inline-flex items-center gap-1 text-[13px] text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary">
                        <i class="pi pi-ban text-[11px]" aria-hidden="true" />{{ t('boardSanction.review.siteSanctionLink', { no: detail.siteSanction.no }) }}
                    </NuxtLink>
                    <p v-if="detail.voidedSanctionIds.length" class="m-0 text-[13px]">{{ t('boardSanction.review.voidedCount', { n: detail.voidedSanctionIds.length }) }}</p>
                    <p class="m-0 text-[13px] break-words whitespace-pre-line [overflow-wrap:anywhere]" :class="detail.memo ? '' : 'text-muted-color'">{{ detail.memo || t('boardSanction.review.memoNone') }}</p>
                </div>
            </section>
        </div>

        <template v-if="detail && !error && detail.status === 'OPEN'" #footer>
            <div class="flex w-full flex-col gap-2 border-t border-(--line) pt-3">
                <Message v-if="closed" severity="warn" size="small" :closable="false" icon="pi pi-info-circle">
                    <span class="flex flex-wrap items-center gap-2">
                        {{ t('boardSanction.review.closedNote') }}
                        <Button type="button" :label="t('boardSanction.review.reload')" icon="pi pi-refresh" size="small" severity="secondary" variant="outlined" :loading="loading" @click="emit('retry')" />
                    </span>
                </Message>
                <p v-else-if="!canManage" class="m-0 flex items-center gap-1.5 text-xs text-muted-color"><i class="pi pi-lock" aria-hidden="true" />{{ t('boardSanction.review.readOnly') }}</p>
                <form v-else :id="formId" class="flex flex-col gap-3" novalidate @submit.prevent="send">
                    <fieldset class="m-0 flex flex-col gap-1.5 border-0 p-0">
                        <legend class="mb-1 text-sm font-semibold">{{ t('boardSanction.review.closeTitle') }}</legend>
                        <p class="m-0 mb-1 text-xs text-muted-color">{{ t('boardSanction.review.closeHelp') }}</p>
                        <div v-for="choice in UtilBoardSanction.REVIEW_RESULTS" :key="choice" class="flex items-start gap-2 rounded-lg border px-3 py-2" :class="result === choice ? 'border-primary' : 'border-(--line)'">
                            <RadioButton v-model="result" :value="choice" :input-id="`${formId}-${choice}`" :name="`${formId}-result`" :disabled="busy || choiceBlocked(choice)" class="mt-0.5" />
                            <label :for="`${formId}-${choice}`" class="flex min-w-0 flex-1 flex-col gap-0.5" :class="choiceBlocked(choice) ? 'cursor-not-allowed opacity-60' : 'cursor-pointer'">
                                <span class="text-[13px] font-medium">{{ t(`boardSanction.review.choice.${choice}`) }}</span>
                                <span class="text-xs text-muted-color">{{ choiceHelp(choice) }}</span>
                            </label>
                        </div>
                    </fieldset>
                    <p v-if="result === 'VOIDED'" class="m-0 text-xs" :class="voidError ? 'text-red-600 dark:text-red-400' : 'text-muted-color'" :role="voidError ? 'alert' : undefined">
                        {{ voidError || (voidableIds.length ? t('boardSanction.review.voidPickCount', { n: voidIds.length, max: voidableIds.length }) : t('boardSanction.review.voidPickNone')) }}
                    </p>
                    <LabeledField :label="t('boardSanction.review.memoLabel')" :hint="t('sanction.textCounter', { n: UtilBoardSanction.memoLength(memo), max: UtilBoardSanction.MEMO_MAX })" :help="t('boardSanction.review.memoHelp')" :error="memoError">
                        <template #default="{ id, describedBy, invalid }">
                            <Textarea :id="id" v-model="memo" rows="2" auto-resize fluid :placeholder="t('boardSanction.review.memoPlaceholder')" :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" />
                        </template>
                    </LabeledField>
                    <Message v-if="formError" severity="error" size="small" variant="simple" :closable="false">{{ formError }}</Message>
                    <div class="flex justify-end">
                        <Button type="submit" :label="result ? t(`boardSanction.review.submit.${result}`) : t('boardSanction.review.closeTitle')" :icon="submitIcon" :severity="result === 'SITE_SANCTIONED' ? 'danger' : undefined" size="small" :loading="busy" :disabled="busy || !result || choiceBlocked(result)" />
                    </div>
                </form>
            </div>
        </template>
    </Drawer>
</template>

<script setup lang="ts">
import type { DrawerSummaryItem } from '~/components/common/DrawerSummary.types';
import type { ApiError } from '~/composables/common/useApiClient';
import type { BoardSanctionReviewDetail, BoardSanctionReviewItem, BoardSanctionReviewResult } from '~/composables/sanction/api/useBoardSanctionReviewApi.types';
import type { SanctionPreview } from '~/composables/sanction/api/useSanctionApi.types';
import UtilBoardSanction from '~/utils/util-board-sanction';
import UtilSanctionMessages from '~/utils/util-sanction-messages';

const open = defineModel<boolean>('visible', { required: true });

interface Props {
    /** 머리를 바로 그릴 줄 — 받은 상세, 아직이면 누른 목록 줄 */
    item?: BoardSanctionReviewItem | null;
    detail?: BoardSanctionReviewDetail | null;
    loading?: boolean;
    error?: ApiError | null;
    /** 그 회원의 사이트 제재 현황 */
    sitePreview?: SanctionPreview | null;
    sitePreviewLoading?: boolean;
    sitePreviewError?: ApiError | null;
    /** `sanction.manage` */
    canManage?: boolean;
    busy?: boolean;
    /** 닫다가 `409 BOARD_SANCTION_REVIEW_CLOSED` 를 받았다 */
    closed?: boolean;
    /** 칸에 붙지 않는 오류(서버 문구 그대로) */
    formError?: string;
    /** 서버 칸 오류 — `memo`·`voidSanctionIds`·`siteSanctionId` */
    fieldErrors?: Record<string, string>;
    hasPrev?: boolean;
    hasNext?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
    item: null,
    detail: null,
    loading: false,
    error: null,
    sitePreview: null,
    sitePreviewLoading: false,
    sitePreviewError: null,
    canManage: false,
    busy: false,
    closed: false,
    formError: '',
    fieldErrors: () => ({}),
    hasPrev: false,
    hasNext: false,
});
const emit = defineEmits<{
    retry: [];
    'retry-site': [];
    submit: [payload: { result: BoardSanctionReviewResult; memo: string | null; voidSanctionIds: string[] }];
    prev: [];
    next: [];
    closed: [];
}>();

const { t } = useI18n();
const { prefs } = useLayoutPrefs();
const time = useDisplayTime();
const { localizedName } = useLocalizedName();
const titleId = useId();
const formId = useId();

const head = computed(() => props.detail ?? props.item);
const boardName = computed(() => (head.value ? localizedName(head.value.board.name, head.value.board.nameEn) : ''));
/** 봉인 줄 — 익명 글 작성자(anonymous-board-contract 정책 9) */
const sealed = computed(() => head.value?.user.sealed === true);
/** 「사이트 제재 부여」를 줄 대상이 없다 — 봉인 줄인데 가리킬 글·댓글(`sealedSource`)이 없음 등 */
const siteBlocked = computed(() => Boolean(props.detail && !UtilBoardSanction.reviewIssueTarget(props.detail)));

function choiceBlocked(choice: BoardSanctionReviewResult): boolean {
    return choice === 'SITE_SANCTIONED' && siteBlocked.value;
}

function choiceHelp(choice: BoardSanctionReviewResult): string {
    if (choiceBlocked(choice)) return t('boardSanction.review.siteSealedNoSource');
    if (choice === 'SITE_SANCTIONED' && sealed.value) return t('boardSanction.review.siteSealedHelp');
    return t(`boardSanction.review.choiceHelp.${choice}`);
}

const result = ref<BoardSanctionReviewResult | null>(null);
const memo = ref('');
const voidIds = ref<string[]>([]);
const tried = ref(false);

// 다른 검토를 열면 고른 것을 비운다(앞 회원에게 고른 결과가 딸려 가면 안 된다)
watch(
    () => props.detail?.id ?? props.item?.id ?? null,
    (next, prev) => {
        if (next === prev) return;
        result.value = null;
        memo.value = '';
        voidIds.value = [];
        tried.value = false;
    },
);

const voidableIds = computed(() => (props.detail ? UtilBoardSanction.voidable(props.detail).map((l) => l.id) : []));
/** 본문 목록에 고르기 칸을 보일 때 — 열린 건 · 권한 · 「게시판 제재 무효」를 골랐다 */
const picking = computed(() => props.canManage && props.detail?.status === 'OPEN' && result.value === 'VOIDED' && !props.closed);
const voidError = computed(() => props.fieldErrors.voidSanctionIds || (tried.value && result.value === 'VOIDED' && voidIds.value.length === 0 ? t('boardSanction.review.voidPickRequired') : ''));
const memoError = computed(() => props.fieldErrors.memo || (UtilBoardSanction.memoTooLong(memo.value) ? t('boardSanction.review.memoTooLong', { max: UtilBoardSanction.MEMO_MAX }) : ''));
const submitIcon = computed(() => (result.value ? UtilBoardSanction.reviewResultIcon(result.value) : 'pi pi-check'));

const summaryItems = computed<DrawerSummaryItem[]>(() => {
    const h = head.value;
    if (!h) return [];
    return [
        { key: 'state', label: t('boardSanction.review.filterStatus') },
        { key: 'no', label: t('boardSanction.review.col.no'), value: UtilSanctionMessages.no(t, h) },
        { key: 'member', label: t('boardSanction.review.col.member'), value: UtilSanctionMessages.userName(t, h.user) },
        { key: 'board', label: t('boardSanction.review.col.board'), value: boardName.value },
        { key: 'recent', label: t('boardSanction.review.col.recent'), value: t('boardSanction.review.recentValue', { n: h.recentCount }) },
        { key: 'opened', label: t('boardSanction.review.col.openedAt'), value: time.value.full(h.openedAt) },
        ...(h.closedAt ? [{ key: 'closedAt', label: t('boardSanction.review.col.closedAt'), value: time.value.full(h.closedAt) }] : []),
    ];
});

function send(): void {
    if (props.busy || !result.value || choiceBlocked(result.value)) return;
    tried.value = true;
    if (memoError.value) return;
    if (result.value === 'VOIDED' && voidIds.value.length === 0) return;
    const text = memo.value.trim();
    emit('submit', { result: result.value, memo: text || null, voidSanctionIds: result.value === 'VOIDED' ? voidIds.value.filter((id) => voidableIds.value.includes(id)) : [] });
}

/** 바깥 누르기 — `SanctionDetailDrawer` 와 같은 방식 */
const INSIDE = '.p-drawer, .p-drawer-mask, tbody tr, .p-dialog, .p-dialog-mask, .p-overlay, .p-select-overlay, .p-menu, .p-popover, .p-tooltip, .p-toast, [role="alertdialog"], [role="listbox"], [role="tab"]';
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
