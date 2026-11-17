<template>
    <!--
        회원 › 회원 칭호 › 확인할 일(`/members/titles/review?status=PENDING|ALL&key=`, member-title-contract 「화면」) — 칭호 신청 심사(체험단 쪽 확인 일이 생기면 같은 목록에 붙는다).
        한 줄(서버 `AdminTitleApplicationView`): 칭호 칩 · 신청한 회원(→ 회원 관리) · 신청 시각 · 상태(심사한 운영자·매니저 · 거절 사유 · 다시 신청할 수 있는 날). 심사 중이면 [승인](확인 대화상자 — 메모 없음)·[거절](사유 필수 — 회원에게 보인다).
        승인·거절은 감사 기록(서버). 그새 다른 사람(게시판 매니저 포함)이 심사했으면 409 → 토스트 + 다시 받기. 권한: 심사 `title.review`(없으면 읽기만).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <TitleAdminTabs active="review" />

        <div class="flex flex-wrap items-center gap-2">
            <SelectButton :model-value="status" :options="statusOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-label="t('memberTitle.review.statusLabel')" @update:model-value="onStatus" />
            <Select :model-value="keyFilter" :options="keyOptions" option-label="label" option-value="value" size="small" class="w-44" :aria-label="t('memberTitle.review.keyLabel')" @update:model-value="onKey" />
            <span class="ml-auto text-[13px] text-muted-color" aria-live="polite">{{ t('memberTitle.review.total', { count: rows.length }) }}</span>
        </div>

        <div class="min-w-0 overflow-hidden rounded-xl border border-(--line) bg-(--p-content-background)">
            <DataTable :value="rows" data-key="id" :loading="loading" :size="prefs.density === 'compact' ? 'small' : undefined" scrollable>
                <template #empty>
                    <ErrorState v-if="loadError" :title="t('memberTitle.review.loadFailed')" :description="loadError" retry :retrying="loading" @retry="load" />
                    <div v-else-if="loading" class="h-32" aria-hidden="true" />
                    <EmptyState v-else icon="pi pi-check-square" :title="status === 'PENDING' ? t('memberTitle.review.emptyPending') : t('memberTitle.review.empty')" />
                </template>
                <Column :header="t('memberTitle.col.title')" style="min-width: 9rem">
                    <template #body="{ data }"><TitleChip :title="{ ...data, tier: null }" /></template>
                </Column>
                <Column :header="t('memberTitle.col.member')" style="min-width: 9rem">
                    <template #body="{ data }">
                        <NuxtLink v-if="canReadMember" :to="{ path: '/members', query: { memberNo: data.member.id } }" class="text-[13px] font-medium text-(--p-primary-color) hover:underline">{{ data.member.nickname ?? t('memberTitle.withdrawn') }}</NuxtLink>
                        <span v-else class="text-[13px]">{{ data.member.nickname ?? t('memberTitle.withdrawn') }}</span>
                    </template>
                </Column>
                <Column :header="t('memberTitle.col.createdAt')" style="min-width: 9rem">
                    <template #body="{ data }"><span class="text-[12px] tabular-nums">{{ time.full(data.createdAt) }}</span></template>
                </Column>
                <Column :header="t('memberTitle.col.status')" style="min-width: 12rem">
                    <template #body="{ data }">
                        <div class="flex flex-col gap-1">
                            <Tag :value="t(`memberTitle.status.${data.status}`)" :severity="data.status === 'APPROVED' ? 'success' : data.status === 'REJECTED' ? 'danger' : 'warn'" class="self-start" />
                            <span v-if="reviewerOf(data)" class="text-[11px] text-muted-color">{{ t('memberTitle.review.reviewedBy', { name: reviewerOf(data), at: data.reviewedAt ? time.full(data.reviewedAt) : '' }) }}</span>
                            <span v-if="data.reason" class="text-[11px]">{{ t('memberTitle.review.reasonLine', { reason: data.reason }) }}</span>
                            <span v-if="data.status === 'REJECTED' && data.reapplyAt" class="text-[11px] text-muted-color">{{ t('memberTitle.review.reapplyAt', { at: time.date(data.reapplyAt) }) }}</span>
                        </div>
                    </template>
                </Column>
                <Column v-if="canReview" style="min-width: 11rem">
                    <template #body="{ data }">
                        <div v-if="data.status === 'PENDING'" class="flex gap-1.5">
                            <Button type="button" :label="t('memberTitle.review.approve')" icon="pi pi-check" size="small" :disabled="busy" @click="approve(data)" />
                            <Button type="button" :label="t('memberTitle.review.reject')" icon="pi pi-times" size="small" severity="danger" variant="outlined" :disabled="busy" @click="openReject(data)" />
                        </div>
                    </template>
                </Column>
            </DataTable>
        </div>

        <MemberReasonDialog
            v-model:visible="rejectOpen"
            :title="t('memberTitle.review.rejectTitle')"
            :label="t('memberTitle.review.rejectReason')"
            :submit-label="t('memberTitle.review.reject')"
            submit-icon="pi pi-times"
            danger
            :notice="t('memberTitle.review.rejectNotice')"
            :min="1"
            :max="UtilTitle.REASON_MAX"
            :busy="busy"
            :error="rejectError"
            :field-error="rejectFieldError"
            @submit="submitReject"
        >
            <p v-if="rejectTarget" class="m-0 text-sm">{{ t('memberTitle.review.rejectText', { name: rejectTarget.member.nickname ?? '', title: rejectTarget.label }) }}</p>
        </MemberReasonDialog>
    </div>
</template>

<script setup lang="ts">
import type { AdminTitle, AdminTitleApplication, TitleApplicationStatus } from '~/composables/title/api/useTitleApi.types';
import UtilTitle from '~/utils/util-title';

type StatusFilter = 'PENDING' | 'ALL';
const ALL = '__all__';

const { t } = useI18n();
const route = useRoute();
const router = useRouter();
const api = useTitleApi();
const toast = useAppToast();
const errorOf = useApiErrorOf();
const time = useDisplayTime();
const { can } = useAdminPermission();
const { prefs } = useLayoutPrefs();
const { confirm } = useConfirmDialog();
const { localizedName } = useLocalizedName();

const canReview = computed(() => can('title.review'));
const canReadMember = computed(() => can('member.read'));

const status = computed<StatusFilter>(() => (route.query.status === 'ALL' ? 'ALL' : 'PENDING'));
const keyFilter = computed(() => (typeof route.query.key === 'string' && route.query.key ? route.query.key : ALL));
const statusOptions = computed(() => [
    { value: 'PENDING' as const, label: t('memberTitle.status.PENDING') },
    { value: 'ALL' as const, label: t('common.all') },
]);
/** 신청형 칭호만 거르기 목록에 */
const applyTitles = ref<AdminTitle[]>([]);
const keyOptions = computed(() => [{ value: ALL, label: t('memberTitle.review.allTitles') }, ...applyTitles.value.map((x) => ({ value: x.key, label: localizedName(x.name, x.nameEn) }))]);

const rows = ref<AdminTitleApplication[]>([]);
const loading = ref(true);
const loadError = ref('');
const busy = ref(false);

let seq = 0;
async function load(): Promise<void> {
    const mine = ++seq;
    loading.value = true;
    loadError.value = '';
    try {
        const res = await api.applications(status.value === 'ALL' ? null : ('PENDING' satisfies TitleApplicationStatus), keyFilter.value === ALL ? null : keyFilter.value);
        if (mine !== seq) return;
        // 목업은 거르지 않고 다 주므로 화면도 한 번 거른다(서버가 거르면 그대로다)
        rows.value = res.filter((r) => (status.value === 'ALL' || r.status === 'PENDING') && (keyFilter.value === ALL || r.key === keyFilter.value));
    } catch (e) {
        if (mine !== seq) return;
        loadError.value = errorOf(e).message;
        rows.value = [];
    } finally {
        if (mine === seq) loading.value = false;
    }
}

function query(change: { status?: StatusFilter; key?: string }) {
    const nextStatus = change.status ?? status.value;
    const nextKey = change.key ?? keyFilter.value;
    return { path: UtilTitle.PATHS.review, query: { ...(nextStatus === 'ALL' ? { status: 'ALL' } : {}), ...(nextKey !== ALL ? { key: nextKey } : {}) } };
}
function onStatus(value: StatusFilter | null): void {
    if (value) void router.replace(query({ status: value }));
}
function onKey(value: string | null): void {
    void router.replace(query({ key: value ?? ALL }));
}

watch(
    () => [status.value, keyFilter.value] as const,
    () => void load(),
    { immediate: true },
);
void api
    .list()
    .then((list) => (applyTitles.value = list.filter((x) => x.method === 'APPLY')))
    .catch(() => undefined);

/** 심사한 사람 — 운영자 이름, 게시판 매니저면 닉네임 */
function reviewerOf(row: AdminTitleApplication): string {
    return row.reviewedByName ?? row.reviewedByMember?.nickname ?? '';
}

/** 그새 다른 사람이 심사했거나 권한이 바뀌었다 — 알리고 다시 받는다 */
function onStale(e: unknown): boolean {
    const error = errorOf(e);
    if (error.status === 409 || error.status === 404 || error.status === 403) {
        toast.error(error.message, 8000);
        void load();
        return true;
    }
    return false;
}

async function approve(row: AdminTitleApplication): Promise<void> {
    if (busy.value) return;
    const title = row.label;
    const ok = await confirm({ title: t('memberTitle.review.approveTitle'), message: t('memberTitle.review.approveText', { name: row.member.nickname ?? '', title }), confirmLabel: t('memberTitle.review.approve') });
    if (!ok) return;
    busy.value = true;
    try {
        await api.approve(row.id, { reason: '' });
        toast.success(t('memberTitle.review.approved', { name: row.member.nickname ?? '' }));
        await load();
    } catch (e) {
        if (!onStale(e)) toast.error(errorOf(e).message, 8000);
    } finally {
        busy.value = false;
    }
}

const rejectOpen = ref(false);
const rejectTarget = ref<AdminTitleApplication | null>(null);
const rejectError = ref('');
const rejectFieldError = ref('');
function openReject(row: AdminTitleApplication): void {
    rejectTarget.value = row;
    rejectError.value = '';
    rejectFieldError.value = '';
    rejectOpen.value = true;
}
async function submitReject(reason: string): Promise<void> {
    const row = rejectTarget.value;
    if (!row || busy.value) return;
    busy.value = true;
    rejectError.value = '';
    rejectFieldError.value = '';
    try {
        await api.reject(row.id, { reason });
        rejectOpen.value = false;
        toast.success(t('memberTitle.review.rejected', { name: row.member.nickname ?? '' }));
        await load();
    } catch (e) {
        const error = errorOf(e);
        if (error.fields?.reason) rejectFieldError.value = error.fields.reason;
        else if (onStale(e)) rejectOpen.value = false;
        else rejectError.value = error.message;
    } finally {
        busy.value = false;
    }
}

useSeoMeta({ title: () => `${t('memberTitle.tab.review')} · ${t('memberTitle.title')}` });
</script>
