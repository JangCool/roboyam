<template>
    <!--
        광고 › 체험단 › 확인할 일(`/ads/trials/review?status=DONE|ALL&type=`, trial-group-contract 「관리자 화면」·「구현 결정」 7).
        구매 인증 · 반납 확인 · 파손 신고 · 협찬 숨김 + 칭호 신청이 한 목록 — 칭호 관리 「확인할 일」 탭과 같은 부품(`ReviewQueueList`)·같은 받기(`useReviewQueue`). 이 화면은 처음에 전체 종류.
        권한: 체험단 확인 `trial.review` · 칭호 심사 `title.review`(없는 종류는 거르기·단추에서 빠진다).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <TrialAdminTabs active="review" />
        <ReviewQueueList
            :rows="queue.rows.value"
            :loading="queue.loading.value"
            :load-error="queue.loadError.value"
            :status="status"
            :type="type"
            :pending-counts="queue.pendingCounts.value"
            :can-title="can('title.review')"
            :can-trial="can('trial.review')"
            :site-origin="origin"
            :actions="queue.actions"
            @update:status="(v) => go({ status: v })"
            @update:type="(v) => go({ type: v })"
        />
    </div>
</template>

<script setup lang="ts">
import type { ReviewQueueStatus, ReviewQueueType } from '~/composables/review/api/useReviewQueueApi.types';
import UtilTrial from '~/utils/util-trial';

const TYPES: readonly ReviewQueueType[] = ['TITLE_APPLICATION', 'PURCHASE_PROOF', 'RETURN_CHECK', 'DAMAGE_REPORT', 'SPONSOR_HIDDEN'];

const { t } = useI18n();
const route = useRoute();
const router = useRouter();
const { can } = useAdminPermission();
const { origin } = useUserWebOrigin();

const status = computed<ReviewQueueStatus>(() => (route.query.status === 'ALL' || route.query.status === 'DONE' ? route.query.status : 'PENDING'));
const type = computed<ReviewQueueType | null>(() => {
    const v = typeof route.query.type === 'string' ? route.query.type : '';
    return (TYPES as readonly string[]).includes(v) ? (v as ReviewQueueType) : null;
});
const queue = useReviewQueue(type, status);

function go(change: { status?: ReviewQueueStatus; type?: ReviewQueueType | null }): void {
    const nextStatus = change.status ?? status.value;
    const nextType = 'type' in change ? change.type : type.value;
    void router.replace({ path: UtilTrial.PATHS.review, query: { ...(nextStatus !== 'PENDING' ? { status: nextStatus } : {}), ...(nextType ? { type: nextType } : {}) } });
}

useSeoMeta({ title: () => `${t('trial.tab.review')} · ${t('trial.title')}` });
</script>
