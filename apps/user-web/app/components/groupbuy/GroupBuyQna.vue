<template>
    <!--
        공구 상세의 상품 문의(Q&A) 구역 — 계약 commerce-groupbuy 「Q&A 규칙」(2b-6).
        · 쓰기: 로그인한 정상 회원만(서버 `canAsk`·`askBlock` — 로그인 안 함 · 정지 · 중단된 공구). 「연락처·주소는 쓰지 마세요」 상시 안내, 개인정보가 보여 서버가 비밀글로 바꾸면 안내.
        · 목록: 새 글이 위, 10개씩 「더 보기」. 전체 / 내 문의. 비밀글·숨긴 문의는 서버가 본문을 비워 보낸다(화면은 자리 글만).
        · 내 문의: 답변 전 고치기·지우기, 답변 뒤 비밀글로 바꾸기·지우기. 남의 문의: 신고(공용 신고 대상 `GROUP_BUY_QUESTION`).
        · 하자·오배송은 반품 요청으로 안내 · 「이전 차수 문의 보기」(서버 `previousRound`).
        부르는 화면은 로그인 회원이 바뀌면 `:key` 로 이 구역을 새로 그린다.
    -->
    <section id="qna" :aria-labelledby="titleId" class="flex flex-col gap-3 panel-card p-4" data-testid="group-buy-qna">
        <div class="flex flex-wrap items-center justify-between gap-2">
            <h2 :id="titleId" class="m-0 text-sm font-semibold">
                {{ t('groupBuyQna.title') }}<span v-if="page" class="ml-1.5 font-normal tabular-nums text-muted-color">{{ t('groupBuyQna.count', { count: allTotal }) }}</span>
            </h2>
            <Button v-if="page?.canAsk && composer !== 'new'" type="button" :label="t('groupBuyQna.ask')" icon="pi pi-pencil" size="small" data-testid="group-buy-qna-ask" @click="openNew" />
            <Button v-else-if="page?.askBlock === 'LOGIN_REQUIRED'" type="button" :label="t('groupBuyQna.loginToAsk')" icon="pi pi-sign-in" size="small" severity="secondary" variant="outlined" @click="goLogin" />
        </div>

        <p class="m-0 text-xs text-muted-color">
            <i class="pi pi-info-circle mr-1 text-xs" aria-hidden="true" />{{ t('groupBuyQna.defectGuide') }}
            <NuxtLink :to="UtilCommerce.ORDERS_PATH" class="ml-1 font-medium text-primary hover:underline">{{ t('groupBuyQna.defectLink') }}</NuxtLink>
        </p>
        <Message v-if="page?.askBlock === 'RESTRICTED' || page?.askBlock === 'CLOSED'" severity="secondary" size="small" :closable="false" data-testid="group-buy-qna-block">{{ t(`groupBuyQna.askBlock.${page.askBlock}`) }}</Message>

        <div v-if="composer === 'new'" class="rounded-lg border border-surface bg-surface-50 p-3 dark:bg-surface-800/50">
            <GroupBuyQnaForm :submit-label="t('groupBuyQna.submit')" :busy="saving" :error="formError" :field-error="formFieldError" @submit="submitNew" @cancel="closeComposer" />
        </div>
        <Message v-if="forcedNotice" severity="info" size="small" closable role="status" data-testid="group-buy-qna-forced" @close="forcedNotice = false">{{ t('groupBuyQna.forcedSecret') }}</Message>

        <div v-if="loggedIn" class="flex gap-1" role="group" :aria-label="t('groupBuyQna.filterLabel')">
            <Button
                v-for="f in filters"
                :key="f.value"
                type="button"
                size="small"
                :label="f.label"
                :severity="mine === f.mine ? undefined : 'secondary'"
                :variant="mine === f.mine ? undefined : 'text'"
                :aria-pressed="mine === f.mine"
                @click="selectFilter(f.mine)"
            />
        </div>

        <div v-if="status === 'loading'" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
            <div v-for="n in 3" :key="n" class="h-16 animate-pulse rounded-lg bg-surface-100 dark:bg-surface-800" />
        </div>
        <ErrorState v-else-if="status === 'error'" :title="t('groupBuyQna.loadFailed')" :description="loadError" retry @retry="load(false)" />
        <EmptyState v-else-if="items.length === 0" icon="pi pi-comments" :title="mine ? t('groupBuyQna.emptyMine') : t('groupBuyQna.empty')" :description="mine ? '' : t('groupBuyQna.emptyText')" />
        <ul v-else class="m-0 flex list-none flex-col divide-y divide-surface p-0" data-testid="group-buy-qna-list">
            <li v-for="q in items" :key="q.id" class="flex flex-col gap-2 py-3 first:pt-0 last:pb-0" :data-testid="`group-buy-qna-${q.id}`">
                <div class="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs">
                    <Tag :value="t(`groupBuyQna.status.${q.status}`)" :severity="q.status === 'ANSWERED' ? 'success' : 'secondary'" />
                    <span v-if="q.secret" class="inline-flex items-center gap-1 text-muted-color"><i class="pi pi-lock text-[11px]" aria-hidden="true" />{{ t('groupBuyQna.secret') }}</span>
                    <Tag v-if="q.mine" :value="t('groupBuyQna.mine')" severity="info" />
                    <span class="text-muted-color">{{ q.nickname ?? t('groupBuyQna.withdrawn') }} · {{ time.shortDateTime(q.createdAt) }}<template v-if="q.updatedAt"> · {{ t('groupBuyQna.editedMark') }}</template></span>
                    <div v-if="hasActions(q)" class="ml-auto flex gap-0.5" role="group" :aria-label="t('groupBuyQna.actionsLabel')">
                        <Button v-if="q.actions.edit" type="button" size="small" variant="text" severity="secondary" :label="t('groupBuyQna.actEdit')" :disabled="busyId === q.id" @click="openEdit(q)" />
                        <Button v-if="q.actions.makeSecret" type="button" size="small" variant="text" severity="secondary" :label="t('groupBuyQna.actMakeSecret')" :loading="busyId === q.id && busyKind === 'secret'" @click="makeSecret(q)" />
                        <Button v-if="q.actions.delete" type="button" size="small" variant="text" severity="danger" :label="t('groupBuyQna.actDelete')" :loading="busyId === q.id && busyKind === 'delete'" @click="remove(q)" />
                        <Button v-if="q.actions.report" type="button" size="small" variant="text" severity="secondary" icon="pi pi-flag" :label="t('groupBuyQna.actReport')" @click="openReport(q)" />
                    </div>
                </div>

                <GroupBuyQnaForm
                    v-if="composer === q.id"
                    :initial-body="q.body ?? ''"
                    :initial-secret="q.secret"
                    :submit-label="t('groupBuyQna.saveEdit')"
                    :busy="saving"
                    :error="formError"
                    :field-error="formFieldError"
                    @submit="(v) => submitEdit(q, v)"
                    @cancel="closeComposer"
                />
                <template v-else>
                    <p v-if="q.body === null" class="m-0 text-sm text-muted-color">{{ q.hidden ? t('groupBuyQna.hiddenBody') : t('groupBuyQna.secretBody') }}</p>
                    <p v-else class="m-0 whitespace-pre-line text-sm leading-relaxed [overflow-wrap:anywhere]">{{ q.body }}</p>
                    <p v-if="q.hidden && q.mine" class="m-0 text-xs text-orange-700 dark:text-orange-400">{{ t('groupBuyQna.hiddenMine') }}</p>
                </template>

                <div v-if="q.answer" class="flex flex-col gap-1 rounded-lg bg-surface-50 px-3 py-2 dark:bg-surface-800/60">
                    <span class="text-xs font-semibold text-primary">
                        <i class="pi pi-reply mr-1 text-[11px]" aria-hidden="true" />{{ t('groupBuyQna.answerTitle') }}
                        <span class="ml-1 font-normal text-muted-color">{{ time.shortDateTime(q.answer.answeredAt) }}<template v-if="q.answer.edited"> · {{ t('groupBuyQna.answerEdited') }}</template></span>
                    </span>
                    <p class="m-0 whitespace-pre-line text-sm leading-relaxed [overflow-wrap:anywhere]">{{ q.answer.body }}</p>
                </div>
            </li>
        </ul>

        <Button v-if="status === 'ready' && page?.hasMore" type="button" :label="t('groupBuyQna.more')" severity="secondary" variant="outlined" size="small" class="self-center" :loading="moreBusy" @click="loadMore" />
        <NuxtLink v-if="page?.previousRound" :to="`${UtilGroupBuy.detailPath(page.previousRound.id)}#qna`" class="w-fit text-sm font-medium text-primary hover:underline" data-testid="group-buy-qna-prev">
            {{ t('groupBuyQna.previousRound', { n: page.previousRound.roundNo }) }} <i class="pi pi-angle-right text-xs" aria-hidden="true" />
        </NuxtLink>

        <BoardReportDialog v-model:visible="reportOpen" target="GROUP_BUY_QUESTION" :loading="reporting" :error="reportError" :field-error="reportFieldError" :summary="reportSummary" @submit="submitReport" />
    </section>
</template>

<script setup lang="ts">
import type { GroupBuyQnaPage, GroupBuyQuestion, GroupBuyQuestionRequest } from '~/composables/groupbuy/api/useGroupBuyQnaApi.types';
import type { ReportReason } from '~/composables/report/api/useReportApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilCommerce from '~/utils/util-commerce';
import UtilGroupBuy from '~/utils/util-group-buy';
import UtilGroupBuyMessages from '~/utils/util-group-buy-messages';
import UtilRedirect from '~/utils/util-redirect';

const props = defineProps<{ groupBuyId: string }>();
/** 전체 문의 수가 바뀌면(읽기·쓰기·지우기) — 부르는 화면의 탭 (수) */
const emit = defineEmits<{ count: [total: number] }>();

const { t } = useI18n();
const { user } = useAuth();
const route = useRoute();
const time = useDisplayTime();
const api = useGroupBuyQnaApi();
const toast = useAppToast();
const { confirm } = useConfirmDialog();
const titleId = useId();

const loggedIn = computed(() => !!user.value);
const filters = computed(() => [
    { value: 'all', mine: false, label: t('groupBuyQna.filterAll') },
    { value: 'mine', mine: true, label: t('groupBuyQna.filterMine') },
]);

/* ── 목록 ── */

const page = ref<GroupBuyQnaPage | null>(null);
const items = ref<GroupBuyQuestion[]>([]);
const mine = ref(false);
/** 「전체」의 건수 — 「내 문의」로 바꿔도 제목 옆 수는 전체를 보인다 */
const allTotal = ref(0);
const status = ref<'loading' | 'ready' | 'error'>('loading');
const loadError = ref('');
const moreBusy = ref(false);
let seq = 0;

async function load(more: boolean): Promise<void> {
    const mineNow = ++seq;
    const next = more && page.value ? page.value.page + 1 : 0;
    if (more) moreBusy.value = true;
    else status.value = 'loading';
    loadError.value = '';
    try {
        const res = await api.list(props.groupBuyId, mine.value, next);
        if (mineNow !== seq) return;
        page.value = res;
        items.value = more ? [...items.value, ...res.items.filter((q) => !items.value.some((o) => o.id === q.id))] : res.items;
        if (!mine.value) {
            allTotal.value = res.total;
            emit('count', res.total);
        }
        status.value = 'ready';
    } catch (e) {
        if (mineNow !== seq) return;
        if (more) toast.error(UtilGroupBuyMessages.error(t, e));
        else {
            loadError.value = UtilGroupBuyMessages.error(t, e);
            status.value = 'error';
        }
    } finally {
        if (mineNow === seq) moreBusy.value = false;
    }
}

function loadMore(): void {
    void load(true);
}

function selectFilter(value: boolean): void {
    if (mine.value === value) return;
    mine.value = value;
    closeComposer();
    void load(false);
}

onMounted(() => void load(false));
onBeforeUnmount(() => seq++);

function hasActions(q: GroupBuyQuestion): boolean {
    return q.actions.edit || q.actions.delete || q.actions.makeSecret || q.actions.report;
}

function replace(updated: GroupBuyQuestion): void {
    items.value = items.value.map((q) => (q.id === updated.id ? updated : q));
}

function goLogin(): void {
    void navigateTo(UtilRedirect.toLogin(`${route.fullPath.split('#')[0]}#qna`));
}

/* ── 쓰기·고치기 — 한 번에 한 칸(`new` 또는 고치는 질문 ID) ── */

const composer = ref<string | null>(null);
const saving = ref(false);
const formError = ref('');
const formFieldError = ref('');
const forcedNotice = ref(false);

function resetForm(): void {
    formError.value = '';
    formFieldError.value = '';
}

function openNew(): void {
    resetForm();
    forcedNotice.value = false;
    composer.value = 'new';
}

function openEdit(q: GroupBuyQuestion): void {
    resetForm();
    forcedNotice.value = false;
    composer.value = q.id;
}

function closeComposer(): void {
    composer.value = null;
    resetForm();
}

/** 쓰기·고치기 실패 — 칸 오류 · 로그인 풀림 · 그 밖(칸 아래) */
function formFailed(e: unknown): void {
    if (e instanceof ApiError && e.code === 'UNAUTHENTICATED') return goLogin();
    const field = e instanceof ApiError ? e.field('body') : null;
    if (field) formFieldError.value = field;
    else formError.value = UtilGroupBuyMessages.error(t, e);
}

async function submitNew(body: GroupBuyQuestionRequest): Promise<void> {
    saving.value = true;
    resetForm();
    try {
        const created = await api.ask(props.groupBuyId, body);
        items.value = [created, ...items.value];
        allTotal.value++;
        emit('count', allTotal.value);
        if (page.value) page.value.total++;
        composer.value = null;
        forcedNotice.value = created.forcedSecret;
        toast.success(t('groupBuyQna.asked'));
    } catch (e) {
        formFailed(e);
    } finally {
        saving.value = false;
    }
}

async function submitEdit(q: GroupBuyQuestion, body: GroupBuyQuestionRequest): Promise<void> {
    saving.value = true;
    resetForm();
    try {
        const updated = await api.update(q.id, body);
        replace(updated);
        composer.value = null;
        forcedNotice.value = updated.forcedSecret && !q.forcedSecret;
        toast.success(t('groupBuyQna.edited'));
    } catch (e) {
        // 그 사이 답변이 달렸으면 고칠 수 없다 — 목록을 새로 받는다
        if (e instanceof ApiError && e.code === 'GROUP_BUY_QUESTION_ANSWERED') {
            toast.error(e.message, 8000);
            closeComposer();
            void load(false);
        } else formFailed(e);
    } finally {
        saving.value = false;
    }
}

/* ── 지우기 · 비밀글로 ── */

const busyId = ref<string | null>(null);
const busyKind = ref<'secret' | 'delete' | null>(null);

async function work(q: GroupBuyQuestion, kind: 'secret' | 'delete', call: () => Promise<void>): Promise<void> {
    busyId.value = q.id;
    busyKind.value = kind;
    try {
        await call();
    } catch (e) {
        toast.error(UtilGroupBuyMessages.error(t, e), 8000);
    } finally {
        busyId.value = null;
        busyKind.value = null;
    }
}

async function makeSecret(q: GroupBuyQuestion): Promise<void> {
    if (!(await confirm({ title: t('groupBuyQna.makeSecretTitle'), message: t('groupBuyQna.makeSecretText'), confirmLabel: t('groupBuyQna.actMakeSecret') }))) return;
    await work(q, 'secret', async () => {
        replace(await api.makeSecret(q.id));
        toast.success(t('groupBuyQna.madeSecret'));
    });
}

async function remove(q: GroupBuyQuestion): Promise<void> {
    if (!(await confirm({ title: t('groupBuyQna.deleteTitle'), message: t('groupBuyQna.deleteText'), confirmLabel: t('groupBuyQna.actDelete'), tone: 'danger' }))) return;
    await work(q, 'delete', async () => {
        await api.remove(q.id);
        items.value = items.value.filter((o) => o.id !== q.id);
        allTotal.value = Math.max(0, allTotal.value - 1);
        emit('count', allTotal.value);
        if (page.value) page.value.total = Math.max(0, page.value.total - 1);
        if (composer.value === q.id) closeComposer();
        toast.success(t('groupBuyQna.deleted'));
    });
}

/* ── 신고 ── */

const reportOpen = ref(false);
const reportTarget = ref<GroupBuyQuestion | null>(null);
const reporting = ref(false);
const reportError = ref('');
const reportFieldError = ref('');
const reportSummary = computed(() => (reportTarget.value?.body ?? '').slice(0, 80));

function openReport(q: GroupBuyQuestion): void {
    reportTarget.value = q;
    reportError.value = '';
    reportFieldError.value = '';
    reportOpen.value = true;
}

async function submitReport(value: { reason: ReportReason; detail: string | null }): Promise<void> {
    const target = reportTarget.value;
    if (!target) return;
    reporting.value = true;
    reportError.value = '';
    reportFieldError.value = '';
    try {
        await api.report(target.id, value);
        reportOpen.value = false;
        toast.success(t('groupBuyQna.reported'));
    } catch (e) {
        const field = e instanceof ApiError ? e.field('detail') : null;
        if (field) reportFieldError.value = field;
        else if (e instanceof ApiError && e.status !== 500 && e.status !== 429) {
            // 다시 눌러도 같은 거절(이미 신고·본인 글·정지 등) — 닫고 서버 문구를 알린다
            reportOpen.value = false;
            toast.error(e.message, 8000);
        } else reportError.value = UtilGroupBuyMessages.error(t, e);
    } finally {
        reporting.value = false;
    }
}
</script>
