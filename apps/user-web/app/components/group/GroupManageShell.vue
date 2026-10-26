<template>
    <!-- 소모임 관리 화면의 틀(모임장·매니저) — 뒤로 링크 · 제목 · 불러오는 중 · 모임장도 매니저도 아님 · 맡기지 않은 일(매니저) · 모임장 고정 · 없는·비활성 소모임 · 불러오기 실패 · (쓸 수 있을 때) 정지·인증 전 띠 + 본문 슬롯 -->
    <div class="mx-auto max-w-2xl">
        <NuxtLink :to="backTo" class="inline-flex items-center gap-1 rounded text-xs text-muted-color hover:underline focus-visible:outline-2 focus-visible:outline-primary" data-testid="group-manage-back">
            <i class="pi pi-angle-left text-xs" aria-hidden="true" />{{ backLabel }}
        </NuxtLink>
        <div class="mt-1 flex items-center gap-2">
            <h1 class="min-w-0 flex-1 text-lg font-bold leading-tight">{{ title }}</h1>
            <slot name="actions" />
        </div>

        <div v-if="state === 'loading'" class="mt-4 flex flex-col gap-3" role="status" :aria-label="t('common.loading')" data-testid="group-manage-loading">
            <div v-for="n in 4" :key="n" class="h-14 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
        </div>

        <!-- 모임장도 매니저도 아니다(해임당한 경우 포함) — 서버도 403 GROUP_LEADER_ONLY 다 -->
        <ErrorState v-else-if="state === 'denied'" :title="t('group.deniedTitle')" :description="t('group.deniedText')" data-testid="group-manage-denied">
            <Button as="router-link" :to="groupPath" :label="t('group.backToGroup')" />
        </ErrorState>

        <!-- 매니저인데 이 화면의 일은 모임장이 맡기지 않았다(직접 주소) — 관리로 돌아가는 길을 준다 -->
        <ErrorState v-else-if="state === 'forbidden'" :title="forbidden.title" :description="forbidden.text" data-testid="group-manage-forbidden">
            <Button as="router-link" :to="UtilGroup.managePath(groupId)" :label="t('groupManager.backToManage')" />
        </ErrorState>

        <!-- 모임장 고정 화면(정보 수정·채팅 링크)에 매니저가 들어왔다 -->
        <ErrorState v-else-if="state === 'leader-only'" :title="t('group.leaderOnlyTitle')" :description="t('group.leaderOnlyText')" data-testid="group-manage-leader-only">
            <Button as="router-link" :to="groupPath" :label="t('group.backToGroup')" />
        </ErrorState>

        <ErrorState v-else-if="state === 'notfound'" code="404" :title="t('group.notFoundTitle')" :description="t('group.notFoundText')" data-testid="group-manage-notfound">
            <Button as="router-link" :to="UtilGroup.LIST_PATH" :label="t('group.backToList')" />
        </ErrorState>

        <ErrorState v-else-if="state === 'inactive'" :title="t('group.inactiveTitle')" :description="t('group.inactiveManageText')" data-testid="group-manage-inactive">
            <Button as="router-link" :to="UtilGroup.LIST_PATH" :label="t('group.backToList')" />
        </ErrorState>

        <ErrorState v-else-if="state === 'error'" :title="t('group.loadError')" :description="errorMessage" retry data-testid="group-manage-error" @retry="emit('retry')" />

        <div v-else class="mt-4">
            <!-- 이용 제한 중·이메일 인증 전 — 화면은 열리고(목록 열람) 쓰기 단추만 꺼진다 -->
            <p v-if="blockedReason" class="mb-3 rounded-lg bg-amber-100 px-3 py-2.5 text-sm leading-relaxed text-amber-900 dark:bg-amber-900/40 dark:text-amber-200" role="status" data-testid="group-manage-blocked">
                {{ UtilGroupMessages.blockedBand(t, blockedReason) }}
            </p>
            <slot />
        </div>
    </div>
</template>

<script setup lang="ts">
import type { GroupManageBlockedReason, GroupManagerPermission } from '~/composables/group/api/useGroupApi.types';
import type { GroupManageState } from '~/composables/group/useGroupManage';
import UtilGroup from '~/utils/util-group';
import UtilGroupMessages from '~/utils/util-group-messages';
import UtilGroupManagerMessages from '~/utils/util-group-manager-messages';

interface Props {
    title: string;
    state: GroupManageState;
    errorMessage?: string;
    groupId: string;
    /** 뒤로 링크(관리 메인은 소모임 상세, 하위 화면은 관리 메인) */
    backTo: string;
    backLabel: string;
    blockedReason?: GroupManageBlockedReason | null;
    /** 이 화면이 요구하는 맡길 일(가입 신청·멤버 관리) — 「맡기지 않은 일」 안내 문구에 쓴다 */
    permission?: GroupManagerPermission | null;
}

const props = withDefaults(defineProps<Props>(), {
    errorMessage: '',
    blockedReason: null,
    permission: null,
});

const emit = defineEmits<{ retry: [] }>();

const { t } = useI18n();
const groupPath = computed(() => UtilGroup.detailPath(props.groupId));
const forbidden = computed(() =>
    props.permission ? UtilGroupManagerMessages.notDelegated(t, props.permission) : { title: t('groupManager.notDelegatedTitle'), text: t('groupManager.revoked') },
);
</script>
