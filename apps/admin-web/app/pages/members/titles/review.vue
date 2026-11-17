<template>
    <!--
        회원 › 회원 칭호 › 확인할 일(`/members/titles/review?status=DONE|ALL&type=`, member-title-contract 「화면」 · trial-group-contract 「구현 결정」 7).
        칭호 신청과 체험단 확인 일(구매 인증 · 반납 확인 · 파손 신고 · 협찬 숨김)이 한 목록이다 — 「광고 › 체험단」 확인할 일(`/ads/trials/review`)과 같은 부품(`ReviewQueueList`)·같은 받기(`useReviewQueue`).
        이 탭은 처음에 칭호 신청만 걸러 보인다(주소에 `type` 이 없으면 `TITLE_APPLICATION`, 「전체 종류」는 `type=ALL`). 승인·거절은 감사 기록(서버).
        권한: 칭호 심사 `title.review` · 체험단 확인 `trial.review`(없는 종류는 거르기·단추에서 빠진다).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <TitleAdminTabs active="review" />
        <ReviewQueueList
            :rows="queue.rows.value"
            :loading="queue.loading.value"
            :load-error="queue.loadError.value"
            :status="status"
            :type="type"
            :pending-counts="queue.pendingCounts.value"
            :can-title="can('title.review')"
            :can-trial="can('trial.review')"
            :can-read-member="can('member.read')"
            :site-origin="origin"
            :actions="queue.actions"
            @update:status="(v) => go({ status: v })"
            @update:type="(v) => go({ type: v })"
        />
    </div>
</template>

<script setup lang="ts">
import type { ReviewQueueStatus, ReviewQueueType } from '~/composables/review/api/useReviewQueueApi.types';
import UtilTitle from '~/utils/util-title';

const { t } = useI18n();
const route = useRoute();
const router = useRouter();
const { can } = useAdminPermission();
const { origin } = useUserWebOrigin();

const status = computed<ReviewQueueStatus>(() => (route.query.status === 'ALL' || route.query.status === 'DONE' ? route.query.status : 'PENDING'));
/** 이 탭의 처음 거르기는 칭호 신청 — `type=ALL` 이면 전체 종류 */
const type = computed<ReviewQueueType | null>(() => {
    const v = typeof route.query.type === 'string' ? route.query.type : '';
    if (v === 'ALL') return null;
    return (v || 'TITLE_APPLICATION') as ReviewQueueType;
});
const queue = useReviewQueue(type, status);

function go(change: { status?: ReviewQueueStatus; type?: ReviewQueueType | null }): void {
    const nextStatus = change.status ?? status.value;
    const nextType = 'type' in change ? change.type : type.value;
    void router.replace({
        path: UtilTitle.PATHS.review,
        query: { ...(nextStatus !== 'PENDING' ? { status: nextStatus } : {}), ...(nextType === null ? { type: 'ALL' } : nextType !== 'TITLE_APPLICATION' ? { type: nextType } : {}) },
    });
}

useSeoMeta({ title: () => `${t('memberTitle.tab.review')} · ${t('memberTitle.title')}` });
</script>
