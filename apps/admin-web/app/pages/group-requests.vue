<template>
    <!--
        소모임 개설 승인(`/group-requests`) — community 관리자 앱 `groups/index.vue`(개설 승인)를 옮겼다. 승인 대기 신청 목록 · 승인 · 반려(사유)만 있다 — 열린 소모임의 비활성화·다시 열기는 소모임 목록(`/groups`).
        - 목록은 오래된 신청이 위. 카드마다 이름·분류·가입 방식·정원·오프라인·공개 범위·게시판·소개(펼치기)·신청자·신청일. 투자 고지 게시판이면 「공부·산업 해설 목적 동의」 줄을 강조한다.
        - 승인은 확인 없이 바로 — 카드가 빠지고 토스트. 반려는 대화상자에서 사유(필수 1~200자, 신청자에게 그대로 전달). 서버가 사유를 거절하면(`fields.reason`) 칸 아래에.
        - 그 사이 다른 운영자가 처리했으면(`404 GROUP_NOT_FOUND`) 카드를 빼고 「이미 처리된 신청이에요」. 한 번에 한 건만 처리한다(다른 카드의 단추도 잠근다).
        - 새 신청 알림: 운영자 알림 이벤트 `CLUB_OPEN_REQUEST_PENDING`(지금은 예약됨 — 「준비 중」)과 이어진다. 서버가 개설 신청을 받을 때 발행하고 `featureReady` 를 켜는 것은 서버 단계다. `notification.read` 가 있으면 그 이벤트 화면 링크를 둔다.
        권한: 화면 `group.read` · 승인·반려 `group.manage`(없으면 단추를 막고 이유를 보인다). ⚠ 목업 API(`useGroupAdminApi` — `?mock=requests-fail` · `requests-empty` · `approve-gone`).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('group.requestsTitle')" :subtitle="t('group.requestsSubtitle')">
            <template #actions>
                <Button :label="t('group.refresh')" icon="pi pi-refresh" size="small" severity="secondary" variant="outlined" :loading="refreshing" :disabled="loading || busyId !== null" @click="load(true)" />
            </template>
        </PageTitle>

        <Message v-if="!canWrite" severity="info" :closable="false" size="small">{{ t('group.manageReadOnly') }}</Message>

        <p class="m-0 flex flex-wrap items-center gap-x-1.5 text-xs text-muted-color">
            <i class="pi pi-bell text-[11px]" aria-hidden="true" />
            <span>{{ t('group.eventHint') }}</span>
            <NuxtLink v-if="can('notification.read')" :to="UtilGroup.OPEN_REQUEST_EVENT_PATH" class="whitespace-nowrap text-primary hover:underline">{{ t('group.eventLink') }} ›</NuxtLink>
        </p>

        <AppGadget v-if="loading && !items" :title="t('group.requestsTitle')">
            <div class="grid grid-cols-[repeat(auto-fill,minmax(min(100%,22rem),1fr))] gap-3" role="status" :aria-label="t('common.loading')">
                <Skeleton v-for="n in 2" :key="n" height="12rem" border-radius="0.75rem" />
            </div>
        </AppGadget>

        <AppGadget v-else-if="!items" :title="t('group.requestsTitle')">
            <ErrorState :title="t('group.requestsLoadFailed')" :description="loadError?.message" retry :retrying="loading" @retry="load()" />
        </AppGadget>

        <AppGadget v-else :title="t('group.requestsHeading', { n: items.length })">
            <template #actions>
                <span class="text-xs text-muted-color">
                    {{ t('group.manageLead') }}
                    <NuxtLink :to="UtilGroup.LIST_PATH" class="font-medium whitespace-nowrap text-primary hover:underline">{{ t('group.manageLink') }} ›</NuxtLink>
                </span>
            </template>

            <EmptyState v-if="!items.length" icon="pi pi-check-circle" :title="t('group.requestsEmptyTitle')" :description="t('group.requestsEmptyBody')" />

            <ul v-else class="m-0 grid list-none grid-cols-[repeat(auto-fill,minmax(min(100%,22rem),1fr))] items-stretch gap-3 p-0" :aria-busy="refreshing">
                <li v-for="request in items" :key="request.id">
                    <GroupRequestCard :request="request" :busy="busyId !== null" :approving="busyId === request.id && !rejecting" :can-write="canWrite" @approve="approve(request)" @reject="openReject(request)" />
                </li>
            </ul>
        </AppGadget>

        <GroupReasonDialog
            v-model:visible="rejectOpen"
            :title="rejectTarget ? t('group.rejectTitle', { name: rejectTarget.name }) : t('group.reject')"
            :description="t('group.rejectBody')"
            :label="t('group.rejectReasonLabel')"
            :hint="t('group.requiredHint')"
            :help="t('group.rejectReasonHelp')"
            :placeholder="t('group.rejectPlaceholder')"
            :max="UtilGroup.REASON_MAX"
            required
            :confirm-label="t('group.rejectConfirm')"
            :busy="rejecting"
            :error="rejectError"
            @submit="submitReject"
        />
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { GroupOpenRequest } from '~/composables/group/api/useGroupAdminApi.types';
import UtilGroup from '~/utils/util-group';
import UtilGroupMessages from '~/utils/util-group-messages';

const { t } = useI18n();
useSeoMeta({ title: () => t('group.requestsTitle') });

const api = useGroupAdminApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const { can } = useAdminPermission();

/** 처리 권한(정의: util-permission-catalog.ts) */
const canWrite = computed(() => can('group.manage'));

/** 승인 대기 목록. null = 아직 못 받았다 */
const items = ref<GroupOpenRequest[] | null>(null);
const loading = ref(false);
const refreshing = ref(false);
const loadError = ref<ApiError | null>(null);
/** 승인·반려가 가는 중인 신청 — 한 번에 하나만 */
const busyId = ref<string | null>(null);

onMounted(() => void load());

/** 받는다 — `silent` 면 보이는 카드를 그대로 두고(실패하면 토스트) 새로 받는다 */
async function load(silent = false): Promise<void> {
    if (silent) refreshing.value = true;
    else loading.value = true;
    loadError.value = null;
    try {
        items.value = (await api.requests()).items;
    } catch (e) {
        if (silent && items.value) toast.error(UtilGroupMessages.failed(t, 'group.refreshFailed', e), 8000);
        else {
            items.value = null;
            loadError.value = toApiError(e);
        }
    } finally {
        loading.value = false;
        refreshing.value = false;
    }
}

function removeItem(id: string): void {
    items.value = (items.value ?? []).filter((r) => r.id !== id);
}

async function approve(request: GroupOpenRequest): Promise<void> {
    if (busyId.value !== null || !canWrite.value) return;
    busyId.value = request.id;
    try {
        await api.approve(request.id);
        removeItem(request.id);
        toast.success(t('group.approved'), 5000);
    } catch (e) {
        if (UtilGroup.isGone(e)) {
            removeItem(request.id);
            toast.show(t('group.requestGone'), { life: 5000 });
        } else {
            toast.error(UtilGroupMessages.failed(t, 'group.approveFailed', e), 8000);
        }
    } finally {
        busyId.value = null;
    }
}

/* ── 반려 대화상자 ── */

const rejectOpen = ref(false);
const rejectTarget = ref<GroupOpenRequest | null>(null);
const rejectError = ref('');
const rejecting = ref(false);

function openReject(request: GroupOpenRequest): void {
    if (busyId.value !== null || !canWrite.value) return;
    rejectTarget.value = request;
    rejectError.value = '';
    rejectOpen.value = true;
}

async function submitReject(reason: string): Promise<void> {
    const target = rejectTarget.value;
    if (!target || rejecting.value) return;
    rejecting.value = true;
    busyId.value = target.id;
    rejectError.value = '';
    try {
        await api.reject(target.id, reason);
        removeItem(target.id);
        rejectOpen.value = false;
        toast.success(t('group.rejected'), 5000);
    } catch (e) {
        if (UtilGroup.isGone(e)) {
            removeItem(target.id);
            rejectOpen.value = false;
            toast.show(t('group.requestGone'), { life: 5000 });
        } else {
            rejectError.value = UtilGroup.fieldError(e, 'reason') ?? UtilGroupMessages.of(t, e);
        }
    } finally {
        rejecting.value = false;
        busyId.value = null;
    }
}
</script>
