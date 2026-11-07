<template>
    <!--
        아바타 올리기 심사 — 회원이 아바타 변경권으로 신청한 직접 만든 그림(정사각형, 정지 PNG·JPG·WebP · 정지·움직이는 GIF)을 보고 승인·반려하고, 지금 쓰는 그림을 해제한다.
        **서버 연결됨**(`/api/admin/avatar-uploads` — 계약 `docs/features/commerce-motion-avatar-contract.md` 「API 계약」). 모양은 닉네임 이미지 심사(`/members/nickname-images`)를 그대로 따른다:
        탭(심사 대기 수) · 닉네임 검색(「검색」을 눌렀을 때만) · 쪽 표 · 확대 보기 · 승인은 바로, 반려·해제는 사유(1~200자, 회원에게 보인다).
        아바타라 더한 것: 원형 미리보기(움직이는 GIF 는 움직이고, 글 목록 크기는 정지 그림) · 변경권 칸(예약·사용·반환) · 승인된 그림의 「사용 중」·「새 그림으로 바뀜」 ·
        대기 중 움직이는 GIF 가 회원의 움직이는 닉네임 이미지를 쉬게 하는지(하나만 규칙) 표시.
        권한: 닉네임 이미지 심사와 같은 `nickname-image.read`(보기) · `nickname-image.manage`(처리) — 없으면 처리 단추 칸을 그리지 않는다(감추는 것은 편의이고 막는 것은 서버다).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('avatarUpload.title')" :subtitle="t('avatarUpload.subtitle')" />

        <SelectButton v-model="status" :options="tabOptions" option-label="label" option-value="value" :allow-empty="false" :aria-label="t('nicknameImage.tabsLabel')" class="avup-tabs self-start" @update:model-value="onTab">
            <template #option="{ option }">
                <span class="inline-flex items-center gap-1.5">
                    <i :class="option.icon" class="text-xs" aria-hidden="true" />
                    {{ option.label }}
                    <span v-if="option.count !== null" class="avup-tabs-count text-[11px] tabular-nums">{{ option.count }}</span>
                </span>
            </template>
        </SelectButton>

        <form class="flex flex-wrap items-center gap-2" role="search" @submit.prevent="search">
            <InputText v-model="keyword" type="search" class="w-60" :placeholder="t('nicknameImage.searchPlaceholder')" :aria-label="t('nicknameImage.searchPlaceholder')" />
            <Button type="submit" :label="t('common.search')" icon="pi pi-search" size="small" :loading="busy" />
            <Button v-if="appliedKeyword" type="button" :label="t('nicknameImage.clearSearch')" size="small" severity="secondary" variant="text" @click="clearKeyword" />
        </form>

        <p v-if="!canManage" class="m-0 flex items-center gap-1.5 text-xs text-muted-color">
            <i class="pi pi-lock" aria-hidden="true" />
            {{ t('avatarUpload.readOnlyHint') }}
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
                    <ErrorState v-if="loadError" :title="t('avatarUpload.loadFailed')" :description="loadError.message" retry :retrying="busy" @retry="reload" />
                    <div v-else-if="busy" class="h-32" aria-hidden="true" />
                    <EmptyState v-else-if="appliedKeyword" icon="pi pi-filter-slash" :title="t('common.emptyFiltered')">
                        <Tag :value="`「${appliedKeyword}」`" severity="secondary" class="text-[11px]!" />
                        <Button type="button" :label="t('nicknameImage.clearSearch')" size="small" severity="secondary" variant="outlined" @click="clearKeyword" />
                    </EmptyState>
                    <EmptyState v-else :icon="UtilNicknameImage.statusIcon(status)" :title="emptyTitle" :description="status === 'PENDING' ? t('avatarUpload.emptyPendingHint') : ''" />
                </template>

                <!-- 그림 — 원형 64px(체크 무늬 = 투명), 움직이는 GIF 는 움직인다. 누르면 확대 -->
                <Column :header="t('avatarUpload.colImage')" style="min-width: 7rem">
                    <template #body="{ data }">
                        <span v-if="!data.url" class="grid size-16 place-items-center rounded-full border border-dashed border-(--line) px-1 text-center text-[10px] leading-tight text-muted-color">{{
                            t('nicknameImage.fileGone')
                        }}</span>
                        <button
                            v-else
                            type="button"
                            class="avup-frame group relative block size-16 rounded-full border border-(--line) focus-visible:outline-2 focus-visible:outline-primary"
                            :aria-label="t('avatarUpload.zoomLabel', { name: data.member.nickname })"
                            @click="zoomTarget = data"
                        >
                            <img :src="data.url" :alt="t('avatarUpload.imageAlt', { name: data.member.nickname })" class="block size-full rounded-full object-contain" loading="lazy" decoding="async" />
                            <Tag v-if="data.frames > 1" :value="t('avatarUpload.motion')" severity="info" class="absolute -right-2 -bottom-1 px-1! py-0! text-[10px]!" />
                        </button>
                    </template>
                </Column>

                <Column :header="t('nicknameImage.colMember')" style="min-width: 10rem">
                    <template #body="{ data }">
                        <div class="flex min-w-0 flex-col gap-1">
                            <div class="flex min-w-0 flex-wrap items-center gap-1.5">
                                <GradeBadge v-bind="badgeOf(data)" />
                                <button
                                    v-if="canViewMember"
                                    type="button"
                                    class="avup-member-link min-w-0 truncate text-left text-[13px] text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary"
                                    :title="data.member.nickname"
                                    :aria-label="t('nicknameImage.openMember', { name: data.member.nickname })"
                                    @click="openMember(data)"
                                >
                                    {{ data.member.nickname }}
                                </button>
                                <span v-else class="min-w-0 truncate text-[13px]" :title="data.member.nickname">{{ data.member.nickname }}</span>
                            </div>
                            <!-- 하나만 규칙 — 승인하면 회원의 움직이는 닉네임 이미지가 쉰다(운영자가 알고 승인하게) -->
                            <span v-if="data.status === 'PENDING' && data.pausesNicknameImage" class="inline-flex items-center gap-1 text-[11px] leading-snug text-amber-700 dark:text-amber-300">
                                <i class="pi pi-info-circle text-[10px]" aria-hidden="true" />{{ t('avatarUpload.pausesNickname') }}
                            </span>
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

                <!-- 변경권 — 예약(대기) · 사용(승인·해제) · 반환(반려). 승인된 그림은 지금 쓰는지도 -->
                <Column :header="t('avatarUpload.colTicket')" style="min-width: 8rem">
                    <template #body="{ data }">
                        <div class="flex flex-col items-start gap-1">
                            <Tag :value="t(`avatarUpload.ticket.${data.ticket}`)" :severity="ticketSeverity(data.ticket)" class="text-[11px]!" />
                            <span v-if="data.status === 'APPROVED'" class="text-[11px] text-muted-color">{{ !data.current ? t('avatarUpload.replaced') : data.inUse ? t('avatarUpload.inUse') : t('avatarUpload.notInUse') }}</span>
                        </div>
                    </template>
                </Column>

                <Column :header="t('nicknameImage.colCreatedAt')" style="min-width: 10rem">
                    <template #body="{ data }">
                        <span class="text-[12px] tabular-nums text-muted-color">{{ time.full(data.createdAt) }}</span>
                    </template>
                </Column>

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

                <!-- 처리 — 심사 대기는 승인·반려, 승인(지금 그 회원의 그림)은 해제 -->
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
                                :aria-label="t('avatarUpload.approveLabel', { name: data.member.nickname })"
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
                                :aria-label="t('avatarUpload.rejectLabel', { name: data.member.nickname })"
                                :disabled="approvingId === data.id"
                                @click="openReason('REJECT', data)"
                            />
                        </div>
                        <Button
                            v-else-if="data.status === 'APPROVED' && data.current"
                            type="button"
                            :label="t('nicknameImage.revoke')"
                            icon="pi pi-ban"
                            size="small"
                            severity="danger"
                            variant="outlined"
                            :aria-label="t('avatarUpload.revokeLabel', { name: data.member.nickname })"
                            @click="openReason('REVOKE', data)"
                        />
                    </template>
                </Column>
            </DataTable>
        </div>

        <!-- 확대 보기 — 256px 정사각(체크 무늬) + 공개 사이트 자리 크기(프로필 112 · 글 보기 24 · 글 목록 20 = 정지 그림) -->
        <Dialog :visible="zoomTarget !== null" modal dismissable-mask :draggable="false" :header="zoomTarget ? t('avatarUpload.zoomTitle', { name: zoomTarget.member.nickname }) : ''" :style="{ width: 'min(36rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => !v && (zoomTarget = null)">
            <div v-if="zoomTarget?.url" class="flex flex-col gap-4">
                <div class="avup-frame mx-auto aspect-square w-full max-w-64 overflow-hidden rounded-md border border-(--line)">
                    <img :src="zoomTarget.url" :alt="t('avatarUpload.imageAlt', { name: zoomTarget.member.nickname })" class="block size-full object-contain" />
                </div>
                <div class="flex flex-wrap items-end justify-center gap-5 text-[11px] text-muted-color">
                    <span class="flex flex-col items-center gap-1"><img :src="zoomTarget.url" alt="" class="size-28 rounded-full border border-(--line) object-contain" />{{ t('avatarUpload.whereProfile') }}</span>
                    <span class="flex flex-col items-center gap-1"><img :src="zoomTarget.url" alt="" class="size-6 rounded-full border border-(--line) object-contain" />{{ t('avatarUpload.whereView') }}</span>
                    <span class="flex flex-col items-center gap-1"><img :src="zoomTarget.stillUrl ?? zoomTarget.url" alt="" class="size-5 rounded-full border border-(--line) object-contain" />{{ t('avatarUpload.whereList') }}</span>
                </div>
                <p class="m-0 text-xs text-muted-color">{{ t('avatarUpload.zoomNote') }}</p>
            </div>
        </Dialog>

        <MemberDetailDrawer v-if="canViewMember" v-model:visible="memberDrawerOpen" ignore-selector=".avup-member-link" :member="null" :detail="memberDetail" :loading="memberLoading" :error="memberError" @retry="retryMember" />

        <!-- 반려·해제 사유 — 열 때마다 비운다. 변경권이 어떻게 되는지 함께 알린다 -->
        <Dialog v-model:visible="reasonOpen" modal :draggable="false" :header="reasonMode === 'REJECT' ? t('avatarUpload.rejectTitle') : t('avatarUpload.revokeTitle')" :style="{ width: 'min(30rem, calc(100vw - 2rem))' }">
            <form :id="reasonFormId" class="flex flex-col gap-4" novalidate @submit.prevent="submitReason">
                <p class="m-0 text-sm leading-relaxed">
                    {{ reasonMode === 'REJECT' ? t('avatarUpload.rejectMessage', { name: reasonTarget?.member.nickname ?? '' }) : t('avatarUpload.revokeMessage', { name: reasonTarget?.member.nickname ?? '' }) }}
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
import type { AvatarUploadStatus, AvatarUploadTicket, AvatarUploadView } from '~/composables/member/api/useAvatarUploadApi.types';
import UtilNicknameImage from '~/utils/util-nickname-image';

const { t, locale } = useI18n();
useSeoMeta({ title: () => t('avatarUpload.title') });

const api = useAvatarUploadApi();
const route = useRoute();
const toast = useAppToast();
const errorOf = useApiErrorOf();
const time = useDisplayTime();
const { can } = useAdminPermission();
const { prefs } = useLayoutPrefs();
const gradeState = useGradeAppearance();

/** 처리 권한(닉네임 이미지 심사와 같은 권한 — 서버 `nickname-image.manage`) */
const canManage = computed(() => can('nickname-image.manage'));

const canViewMember = computed(() => can('member.read'));
const { detail: memberDetail, loading: memberLoading, error: memberError, open: openMemberDetail, retry: retryMember, clear: clearMember } = useMemberDetail();
const memberDrawerOpen = ref(false);
function openMember(row: AvatarUploadView): void {
    memberDrawerOpen.value = true;
    void openMemberDetail(row.member.id);
}
watch(memberDrawerOpen, (isOpen) => {
    if (!isOpen) clearMember();
});

/** 처리 결과가 이미 바뀐 신청 — 목록을 다시 받는다 */
const STALE_CODES: ReadonlySet<string> = new Set(['AVATAR_UPLOAD_NOT_FOUND', 'AVATAR_UPLOAD_NOT_PENDING', 'AVATAR_UPLOAD_NOT_ACTIVE']);

const status = ref<AvatarUploadStatus>(UtilNicknameImage.statusOf(route.query.status) ?? 'PENDING');
const keyword = ref('');
const appliedKeyword = ref('');
const rows = ref<AvatarUploadView[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);
const pendingCount = ref<number | null>(null);
const busy = ref(false);
const loadError = ref<ApiError | null>(null);
const approvingId = ref<string | null>(null);
const zoomTarget = ref<AvatarUploadView | null>(null);

const tabOptions = computed(() =>
    UtilNicknameImage.STATUSES.map((value) => ({
        value,
        label: t(`nicknameImage.status.${value}`),
        icon: UtilNicknameImage.statusIcon(value),
        count: value === 'PENDING' ? pendingCount.value : null,
    })),
);
const emptyTitle = computed(() => ({ PENDING: t('avatarUpload.emptyPending'), APPROVED: t('avatarUpload.emptyApproved'), REJECTED: t('avatarUpload.emptyRejected'), REVOKED: t('avatarUpload.emptyRevoked') })[status.value]);
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

function onTab(): void {
    page.value = 1;
    void reload();
}
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

function badgeOf(row: AvatarUploadView): { label: string; color: string; icon: string | null } {
    const base = gradeState.badgeOf(row.member.grade);
    return { ...base, label: row.member.gradeLabel || base.label };
}
function ticketSeverity(ticket: AvatarUploadTicket): 'warn' | 'secondary' | 'success' {
    return ticket === 'RESERVED' ? 'warn' : ticket === 'RETURNED' ? 'success' : 'secondary';
}
function reviewTime(row: AvatarUploadView): string | null {
    return row.status === 'REVOKED' ? (row.revokedAt ?? row.reviewedAt) : row.reviewedAt;
}
function reasonOf(row: AvatarUploadView): string | null {
    if (row.status === 'REJECTED') return row.rejectReason;
    if (row.status === 'REVOKED') return row.revokeReason;
    return null;
}
function handleStale(error: ApiError): boolean {
    if (!STALE_CODES.has(error.code)) return false;
    toast.error(error.message, 8000);
    void reload();
    return true;
}

async function approve(row: AvatarUploadView): Promise<void> {
    if (approvingId.value !== null) return;
    approvingId.value = row.id;
    try {
        await api.approve(row.id);
        toast.success(t('avatarUpload.approved', { name: row.member.nickname }));
        await reload();
    } catch (e) {
        const error = errorOf(e);
        if (!handleStale(error)) toast.error(error.message, 8000);
    } finally {
        approvingId.value = null;
    }
}

// ── 반려·해제 사유 대화상자(닉네임 이미지 심사와 같은 검사)
type ReasonMode = 'REJECT' | 'REVOKE';
const reasonFormId = useId();
const reasonOpen = ref(false);
const reasonMode = ref<ReasonMode>('REJECT');
const reasonTarget = ref<AvatarUploadView | null>(null);
const reason = ref('');
const reasonSubmitted = ref(false);
const reasonBusy = ref(false);
const reasonError = ref('');
const reasonFieldError = ref('');

const reasonProblemText = computed(() => {
    if (!reasonSubmitted.value) return '';
    const problem = UtilNicknameImage.reasonProblem(reason.value);
    if (problem === 'REQUIRED') return t('nicknameImage.reasonRequired');
    if (problem === 'TOO_LONG') return t('nicknameImage.reasonTooLong', { max: UtilNicknameImage.REASON_MAX });
    return '';
});
watch(reason, () => (reasonFieldError.value = ''));

function openReason(mode: ReasonMode, row: AvatarUploadView): void {
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
            toast.success(t('avatarUpload.rejected', { name: target.member.nickname }));
        } else {
            await api.revoke(target.id, body);
            toast.success(t('avatarUpload.revoked', { name: target.member.nickname }));
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
/* 탭 — 닉네임 이미지 심사와 같은 모양 */
.avup-tabs :deep(.p-togglebutton-checked) {
    background: var(--p-primary-color);
    color: var(--p-primary-contrast-color);
    border-color: var(--p-primary-color);
}

.avup-tabs :deep(.p-togglebutton-checked) .avup-tabs-count {
    color: var(--p-primary-contrast-color);
    opacity: 0.85;
}

.avup-tabs :deep(.p-togglebutton-content) {
    background: transparent;
    box-shadow: none;
}

/* 투명한 부분이 보이게 체크 무늬 바탕 */
.avup-frame {
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
