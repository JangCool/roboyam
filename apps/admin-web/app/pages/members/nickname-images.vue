<template>
    <!--
        닉네임 이미지 심사 — 회원이 신청한 닉네임 이미지(서버가 240×60 으로 맞춰 저장)를 보고 승인·반려하고, 적용 중인 이미지를 해제한다(계약 `docs/features/member-social-contract.md`).
        **탭이 목록을 가른다**: 심사 대기(수가 붙는다) · 승인 · 반려 · 해제. 닉네임 검색은 「검색」(또는 Enter)을 눌렀을 때만 서버로 간다. 표는 서버가 한 쪽씩 준다(`lazy`, 쪽은 1부터).
        미리보기는 실제 크기 그대로(240×60, 투명 부분은 체크 무늬) 그리고 누르면 확대해 본다. 승인은 바로, 반려·해제는 사유(1~200자, 회원에게 보인다)를 받는 대화상자에서.
        권한: 보기 `nickname-image.read`(메뉴·셸이 막는다), 처리 `nickname-image.manage` — 없으면 처리 단추 칸을 그리지 않고 안내 한 줄을 보인다(감추는 것은 편의이고 막는 것은 서버다).
        이미 다른 운영자가 처리했으면(`404`·`409`) 서버 문구를 토스트로 보이고 목록을 다시 받는다. 운영자 알림 `NICKNAME_IMAGE_PENDING` 의 링크가 이 화면이다(`?status=` 로 첫 탭을 고를 수 있다).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('nicknameImage.title')" :subtitle="t('nicknameImage.subtitle')" />

        <!-- 탭 줄 — 심사 대기에는 수를 함께 보인다. 의미는 글자로 전한다 -->
        <SelectButton v-model="status" :options="tabOptions" option-label="label" option-value="value" :allow-empty="false" :aria-label="t('nicknameImage.tabsLabel')" class="nickimg-tabs self-start" @update:model-value="onTab">
            <template #option="{ option }">
                <span class="inline-flex items-center gap-1.5">
                    <i :class="option.icon" class="text-xs" aria-hidden="true" />
                    {{ option.label }}
                    <span v-if="option.count !== null" class="nickimg-tabs-count text-[11px] tabular-nums">{{ option.count }}</span>
                </span>
            </template>
        </SelectButton>

        <!-- 검색 줄 -->
        <form class="flex flex-wrap items-center gap-2" role="search" @submit.prevent="search">
            <InputText v-model="keyword" type="search" class="w-60" :placeholder="t('nicknameImage.searchPlaceholder')" :aria-label="t('nicknameImage.searchPlaceholder')" />
            <Button type="submit" :label="t('common.search')" icon="pi pi-search" size="small" :loading="busy" />
            <Button v-if="appliedKeyword" type="button" :label="t('nicknameImage.clearSearch')" size="small" severity="secondary" variant="text" @click="clearKeyword" />
        </form>

        <p v-if="!canManage" class="m-0 flex items-center gap-1.5 text-xs text-muted-color">
            <i class="pi pi-lock" aria-hidden="true" />
            {{ t('nicknameImage.readOnlyHint') }}
        </p>

        <div class="min-w-0 overflow-hidden rounded-xl border border-(--line) bg-(--p-content-background)">
            <DataTable
                :value="rows"
                data-key="id"
                lazy
                paginator
                :first="(page - 1) * size"
                :rows="size"
                :total-records="total"
                :rows-per-page-options="[20, 50, 100]"
                :loading="busy"
                :size="prefs.density === 'compact' ? 'small' : undefined"
                scrollable
                :current-page-report-template="totalTemplate"
                paginator-template="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown CurrentPageReport"
                class="min-w-0"
                @page="onPage"
            >
                <template #empty>
                    <ErrorState v-if="loadError" :title="t('nicknameImage.loadFailed')" :description="loadError.message" retry :retrying="busy" @retry="reload" />
                    <div v-else-if="busy" class="h-32" aria-hidden="true" />
                    <!-- 빈 상태를 구분한다 — 검색 때문인지 그 상태의 신청이 없는 건지 -->
                    <EmptyState v-else-if="appliedKeyword" icon="pi pi-filter-slash" :title="t('common.emptyFiltered')">
                        <Tag :value="`「${appliedKeyword}」`" severity="secondary" class="text-[11px]!" />
                        <Button type="button" :label="t('nicknameImage.clearSearch')" size="small" severity="secondary" variant="outlined" @click="clearKeyword" />
                    </EmptyState>
                    <EmptyState v-else :icon="UtilNicknameImage.statusIcon(status)" :title="emptyTitle" :description="status === 'PENDING' ? t('nicknameImage.emptyPendingHint') : ''" />
                </template>

                <!-- 그림 — 실제 크기(240×60) 그대로. 누르면 확대 -->
                <Column :header="t('nicknameImage.colImage')" style="min-width: 17rem">
                    <template #body="{ data }">
                        <span
                            v-if="!data.url"
                            class="grid place-items-center rounded-md border border-dashed border-(--line) px-2 text-center text-xs text-muted-color"
                            :style="{ width: `${UtilNicknameImage.WIDTH + 2}px`, height: `${UtilNicknameImage.HEIGHT + 2}px` }"
                            >{{ t('nicknameImage.fileGone') }}</span
                        >
                        <button
                            v-else
                            type="button"
                            class="nickimg-frame group relative block rounded-md border border-(--line) focus-visible:outline-2 focus-visible:outline-primary"
                            :style="{ width: `${UtilNicknameImage.WIDTH + 2}px`, height: `${UtilNicknameImage.HEIGHT + 2}px` }"
                            :aria-label="t('nicknameImage.zoomLabel', { name: data.member.nickname })"
                            @click="zoomTarget = data"
                        >
                            <img :src="data.url" :alt="t('nicknameImage.imageAlt', { name: data.member.nickname })" :width="UtilNicknameImage.WIDTH" :height="UtilNicknameImage.HEIGHT" class="block object-contain" loading="lazy" decoding="async" />
                            <span class="absolute right-1 bottom-1 hidden items-center gap-1 rounded bg-black/60 px-1.5 py-0.5 text-[11px] leading-none text-white group-hover:inline-flex group-focus-visible:inline-flex" aria-hidden="true">
                                <i class="pi pi-search-plus text-[10px]" />{{ t('nicknameImage.zoom') }}
                            </span>
                        </button>
                    </template>
                </Column>

                <Column :header="t('nicknameImage.colMember')" style="min-width: 10rem">
                    <template #body="{ data }">
                        <div class="flex min-w-0 flex-wrap items-center gap-1.5">
                            <GradeBadge v-bind="badgeOf(data)" />
                            <span class="min-w-0 truncate text-[13px]" :title="data.member.nickname">{{ data.member.nickname }}</span>
                        </div>
                    </template>
                </Column>

                <Column :header="t('nicknameImage.colFile')" style="min-width: 9rem">
                    <template #body="{ data }">
                        <div class="flex flex-col gap-0.5 text-[12px] tabular-nums">
                            <span>{{ t('nicknameImage.fileLine', { format: UtilNicknameImage.formatName(data.contentType), size: UtilNicknameImage.kilobytes(data.bytes, locale) }) }}</span>
                            <span class="text-muted-color">
                                {{ t('nicknameImage.sizeLine', { width: data.width, height: data.height }) }} ·
                                {{ data.frames > 1 ? t('nicknameImage.frames', { count: data.frames }) : t('nicknameImage.still') }}
                            </span>
                        </div>
                    </template>
                </Column>

                <Column :header="t('nicknameImage.colCreatedAt')" style="min-width: 10rem">
                    <template #body="{ data }">
                        <span class="text-[12px] tabular-nums text-muted-color">{{ time.full(data.createdAt) }}</span>
                    </template>
                </Column>

                <!-- 처리 정보 — 승인·반려는 처리 시각·운영자, 반려·해제는 사유도 -->
                <Column v-if="status !== 'PENDING'" :header="t('nicknameImage.colReview')" style="min-width: 15rem">
                    <template #body="{ data }">
                        <div class="flex min-w-0 flex-col gap-1">
                            <div class="flex flex-wrap items-center gap-1.5">
                                <Tag :value="t(`nicknameImage.status.${data.status}`)" :icon="UtilNicknameImage.statusIcon(data.status)" :severity="UtilNicknameImage.statusSeverity(data.status)" />
                                <span v-if="reviewTime(data)" class="text-[12px] tabular-nums text-muted-color">
                                    {{ data.reviewer?.name ? t('nicknameImage.reviewedBy', { time: time.full(reviewTime(data)!), name: data.reviewer.name }) : time.full(reviewTime(data)!) }}
                                </span>
                            </div>
                            <p v-if="reasonOf(data)" class="m-0 text-[12px] leading-snug break-words">
                                <span class="text-muted-color">{{ t('nicknameImage.reasonLabel') }}:</span> {{ reasonOf(data) }}
                            </p>
                        </div>
                    </template>
                </Column>

                <!-- 처리 단추 — 심사 대기는 승인·반려, 승인은 해제. 처리 권한이 없으면 칸이 없다 -->
                <Column v-if="canManage && (status === 'PENDING' || status === 'APPROVED')" header="" style="width: 11rem">
                    <template #body="{ data }">
                        <div v-if="data.status === 'PENDING'" class="flex items-center gap-1">
                            <Button
                                type="button"
                                :label="t('nicknameImage.approve')"
                                icon="pi pi-check"
                                size="small"
                                severity="secondary"
                                outlined
                                :aria-label="t('nicknameImage.approveLabel', { name: data.member.nickname })"
                                :loading="approvingId === data.id"
                                :disabled="approvingId !== null && approvingId !== data.id"
                                @click="approve(data)"
                            />
                            <Button
                                type="button"
                                :label="t('nicknameImage.reject')"
                                icon="pi pi-times"
                                size="small"
                                severity="danger"
                                variant="outlined"
                                :aria-label="t('nicknameImage.rejectLabel', { name: data.member.nickname })"
                                :disabled="approvingId === data.id"
                                @click="openReason('REJECT', data)"
                            />
                        </div>
                        <Button
                            v-else-if="data.status === 'APPROVED'"
                            type="button"
                            :label="t('nicknameImage.revoke')"
                            icon="pi pi-ban"
                            size="small"
                            severity="danger"
                            variant="outlined"
                            :aria-label="t('nicknameImage.revokeLabel', { name: data.member.nickname })"
                            @click="openReason('REVOKE', data)"
                        />
                    </template>
                </Column>
            </DataTable>
        </div>

        <!-- 확대 보기 -->
        <Dialog :visible="zoomTarget !== null" modal dismissable-mask :draggable="false" :header="zoomTarget ? t('nicknameImage.zoomTitle', { name: zoomTarget.member.nickname }) : ''" :style="{ width: 'min(48rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => !v && (zoomTarget = null)">
            <div v-if="zoomTarget" class="flex flex-col gap-3">
                <div class="nickimg-frame w-full overflow-hidden rounded-md border border-(--line)" :style="{ aspectRatio: `${UtilNicknameImage.WIDTH} / ${UtilNicknameImage.HEIGHT}`, maxWidth: `${UtilNicknameImage.WIDTH * UtilNicknameImage.ZOOM}px` }">
                    <img v-if="zoomTarget.url" :src="zoomTarget.url" :alt="t('nicknameImage.imageAlt', { name: zoomTarget.member.nickname })" class="block size-full object-contain" />
                </div>
                <p class="m-0 text-xs text-muted-color">{{ t('nicknameImage.zoomNote', { zoom: UtilNicknameImage.ZOOM }) }}</p>
            </div>
        </Dialog>

        <!-- 반려·해제 사유 — 열 때마다 비운다. 사유는 회원에게 보인다 -->
        <Dialog v-model:visible="reasonOpen" modal :draggable="false" :header="reasonMode === 'REJECT' ? t('nicknameImage.rejectTitle') : t('nicknameImage.revokeTitle')" :style="{ width: 'min(30rem, calc(100vw - 2rem))' }">
            <form :id="reasonFormId" class="flex flex-col gap-4" novalidate @submit.prevent="submitReason">
                <p class="m-0 text-sm leading-relaxed">
                    {{ reasonMode === 'REJECT' ? t('nicknameImage.rejectMessage', { name: reasonTarget?.member.nickname ?? '' }) : t('nicknameImage.revokeMessage', { name: reasonTarget?.member.nickname ?? '' }) }}
                </p>

                <LabeledField :label="t('nicknameImage.reasonLabel')" :help="t('nicknameImage.reasonHelp', { max: UtilNicknameImage.REASON_MAX })" :error="reasonProblemText || reasonFieldError">
                    <template #default="{ id, describedBy, invalid }">
                        <Textarea :id="id" v-model="reason" name="reason" rows="3" fluid :placeholder="t('nicknameImage.reasonPlaceholder')" :disabled="reasonBusy" :invalid="invalid" :aria-describedby="describedBy" />
                    </template>
                </LabeledField>

                <Message v-if="reasonError" severity="error" size="small" variant="simple" :closable="false">{{ reasonError }}</Message>
            </form>

            <template #footer>
                <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="reasonBusy" @click="reasonOpen = false" />
                <Button type="submit" :form="reasonFormId" :label="reasonMode === 'REJECT' ? t('nicknameImage.reject') : t('nicknameImage.revoke')" severity="danger" :loading="reasonBusy" />
            </template>
        </Dialog>
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { NicknameImageStatus, NicknameImageView } from '~/composables/member/api/useNicknameImageApi.types';
import UtilNicknameImage from '~/utils/util-nickname-image';

const { t, locale } = useI18n();
useSeoMeta({ title: () => t('nicknameImage.title') });

const api = useNicknameImageApi();
const route = useRoute();
const toast = useAppToast();
const errorOf = useApiErrorOf();
const time = useDisplayTime();
const { can } = useAdminPermission();
// ⚠ 통째로 받아 `prefs.density` 를 쓰지 않으면 조밀 모드가 이 표에만 안 먹는다 — 템플릿에서 `prefs.density` 로 읽는다
const { prefs } = useLayoutPrefs();
// 등급 뱃지의 색·아이콘 — 등급 목록을 볼 권한이 있을 때만 받는다(없으면 중립 뱃지 + 서버가 준 등급 이름)
const gradeState = useGradeAppearance();

/** 승인·반려·해제 권한(정의: util-permission-catalog.ts) — 숨기는 것은 편의이고 막는 것은 서버다 */
const canManage = computed(() => can('nickname-image.manage'));

/** 처리 결과가 이미 바뀐 신청(다른 운영자가 먼저 처리했거나 지워졌다) — 목록을 다시 받는다 */
const STALE_CODES: ReadonlySet<string> = new Set(['NICKNAME_IMAGE_NOT_FOUND', 'NICKNAME_IMAGE_NOT_PENDING', 'NICKNAME_IMAGE_NOT_ACTIVE']);

const status = ref<NicknameImageStatus>(UtilNicknameImage.statusOf(route.query.status) ?? 'PENDING');
const keyword = ref('');
/** 마지막으로 「검색」한 검색어 — 쪽·탭은 이것으로 다시 묻는다(타이핑 중인 글자가 아니다) */
const appliedKeyword = ref('');
const rows = ref<NicknameImageView[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);
const pendingCount = ref<number | null>(null);
const busy = ref(false);
/** 목록을 받지 못했을 때의 오류 — 표 자리에 오류 상태(다시 시도)로 보인다 */
const loadError = ref<ApiError | null>(null);
const approvingId = ref<string | null>(null);
const zoomTarget = ref<NicknameImageView | null>(null);

// ⚠ `computed` 다 — 그냥 배열이면 처음 읽은 순간의 언어로 굳는다
const tabOptions = computed(() =>
    UtilNicknameImage.STATUSES.map((value) => ({
        value,
        label: t(`nicknameImage.status.${value}`),
        icon: UtilNicknameImage.statusIcon(value),
        count: value === 'PENDING' ? pendingCount.value : null,
    })),
);

const emptyTitle = computed(() => ({ PENDING: t('nicknameImage.emptyPending'), APPROVED: t('nicknameImage.emptyApproved'), REJECTED: t('nicknameImage.emptyRejected'), REVOKED: t('nicknameImage.emptyRevoked') })[status.value]);
const totalTemplate = computed(() => t('common.totalCount', { count: '{totalRecords}' }));

onMounted(() => {
    if (can('grade.read')) void gradeState.ensureLoaded();
    void reload();
});

let request = 0;
async function reload(): Promise<void> {
    const mine = ++request;
    busy.value = true;
    loadError.value = null;
    try {
        const found = await api.list({ status: status.value, keyword: appliedKeyword.value, page: page.value, size: size.value });
        if (mine !== request) return;
        // 처리해서 마지막 쪽이 비었으면 한 쪽 앞으로
        const lastPage = Math.max(1, Math.ceil(found.total / size.value));
        if (found.items.length === 0 && page.value > lastPage) {
            page.value = lastPage;
            void reload();
            return;
        }
        rows.value = found.items;
        total.value = found.total;
        pendingCount.value = found.pendingCount;
    } catch (e) {
        if (mine !== request) return;
        loadError.value = errorOf(e);
        rows.value = [];
        total.value = 0;
    } finally {
        if (mine === request) busy.value = false;
    }
}

/** 탭을 바꾸면 첫 쪽부터 */
function onTab(): void {
    page.value = 1;
    void reload();
}

/** 「검색」·Enter — 검색어를 굳히고 첫 쪽부터 */
function search(): void {
    appliedKeyword.value = keyword.value.trim();
    page.value = 1;
    void reload();
}

function clearKeyword(): void {
    keyword.value = '';
    search();
}

function onPage(e: { first: number; rows: number }): void {
    size.value = e.rows;
    page.value = Math.floor(e.first / e.rows) + 1;
    void reload();
}

/** 뱃지 — 색·아이콘은 등급 목록에서, 이름은 서버가 준 등급 이름 */
function badgeOf(row: NicknameImageView): { label: string; color: string; icon: string | null } {
    const base = gradeState.badgeOf(row.member.grade);
    return { ...base, label: row.member.gradeLabel || base.label };
}

/** 처리 시각 — 해제는 해제한 시각, 그 밖은 승인·반려한 시각 */
function reviewTime(row: NicknameImageView): string | null {
    return row.status === 'REVOKED' ? (row.revokedAt ?? row.reviewedAt) : row.reviewedAt;
}

function reasonOf(row: NicknameImageView): string | null {
    if (row.status === 'REJECTED') return row.rejectReason;
    if (row.status === 'REVOKED') return row.revokeReason;
    return null;
}

/** 처리 실패 — 이미 바뀐 신청이면 서버 문구를 토스트로 보이고 목록을 다시 받는다. 처리했으면 true */
function handleStale(error: ApiError): boolean {
    if (!STALE_CODES.has(error.code)) return false;
    toast.error(error.message, 8000);
    void reload();
    return true;
}

async function approve(row: NicknameImageView): Promise<void> {
    if (approvingId.value !== null) return;
    approvingId.value = row.id;
    try {
        await api.approve(row.id);
        toast.success(t('nicknameImage.approved', { name: row.member.nickname }));
        await reload();
    } catch (e) {
        const error = errorOf(e);
        if (!handleStale(error)) toast.error(error.message, 8000);
    } finally {
        approvingId.value = null;
    }
}

// ── 반려·해제 사유 대화상자
type ReasonMode = 'REJECT' | 'REVOKE';
const reasonFormId = useId();
const reasonOpen = ref(false);
const reasonMode = ref<ReasonMode>('REJECT');
const reasonTarget = ref<NicknameImageView | null>(null);
const reason = ref('');
const reasonSubmitted = ref(false);
const reasonBusy = ref(false);
/** 칸에 붙지 않는 오류 */
const reasonError = ref('');
/** 서버가 칸에 붙여 준 오류(`fields.reason`) */
const reasonFieldError = ref('');

const reasonProblemText = computed(() => {
    if (!reasonSubmitted.value) return '';
    const problem = UtilNicknameImage.reasonProblem(reason.value);
    if (problem === 'REQUIRED') return t('nicknameImage.reasonRequired');
    if (problem === 'TOO_LONG') return t('nicknameImage.reasonTooLong', { max: UtilNicknameImage.REASON_MAX });
    return '';
});

watch(reason, () => (reasonFieldError.value = ''));

/** 열 때마다 비운다 — 앞 신청에 적던 사유가 다음 신청에 딸려 가면 안 된다 */
function openReason(mode: ReasonMode, row: NicknameImageView): void {
    reasonMode.value = mode;
    reasonTarget.value = row;
    reason.value = '';
    reasonSubmitted.value = false;
    reasonError.value = '';
    reasonFieldError.value = '';
    reasonOpen.value = true;
}

async function submitReason(): Promise<void> {
    const target = reasonTarget.value;
    if (!target || reasonBusy.value) return;
    reasonSubmitted.value = true;
    if (reasonProblemText.value) return;
    reasonBusy.value = true;
    reasonError.value = '';
    reasonFieldError.value = '';
    const body = { reason: reason.value.trim() };
    try {
        if (reasonMode.value === 'REJECT') {
            await api.reject(target.id, body);
            toast.success(t('nicknameImage.rejected', { name: target.member.nickname }));
        } else {
            await api.revoke(target.id, body);
            toast.success(t('nicknameImage.revoked', { name: target.member.nickname }));
        }
        reasonOpen.value = false;
        await reload();
    } catch (e) {
        const error = errorOf(e);
        if (error.fields?.reason) {
            reasonFieldError.value = error.fields.reason;
        } else if (STALE_CODES.has(error.code)) {
            reasonOpen.value = false;
            handleStale(error);
        } else {
            reasonError.value = error.message;
        }
    } finally {
        reasonBusy.value = false;
    }
}
</script>

<style scoped>
/* 탭 — 고른 탭을 강조색으로 짚는다(운영자 관리 탭과 같은 모양). 색만으로 전하지 않도록 아이콘·글자도 함께 있다 */
.nickimg-tabs :deep(.p-togglebutton-checked) {
    background: var(--p-primary-color);
    color: var(--p-primary-contrast-color);
    border-color: var(--p-primary-color);
}

.nickimg-tabs :deep(.p-togglebutton-checked) .nickimg-tabs-count {
    color: var(--p-primary-contrast-color);
    opacity: 0.85;
}

.nickimg-tabs :deep(.p-togglebutton-content) {
    background: transparent;
    box-shadow: none;
}

/* 투명한 부분이 보이게 체크 무늬 바탕 */
.nickimg-frame {
    background-color: var(--p-content-background);
    background-image: conic-gradient(
        light-dark(var(--p-surface-200), var(--p-surface-700)) 25%,
        transparent 0 50%,
        light-dark(var(--p-surface-200), var(--p-surface-700)) 0 75%,
        transparent 0
    );
    background-size: 12px 12px;
}
</style>
