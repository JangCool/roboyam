<template>
    <!--
        회원 상세 「칭호」 구획(member-title-contract 「화면」 — 회원 상세에 칭호 구획) — 받은 칭호와 회수한 칭호(회수해도 줄은 남는다, 흐리게).
        한 줄: 칭호 칩 · 기간(이달의 작가 달 · 올해의 작가 해) · 받은 길 · 받은 때 · (회수했으면) 회수 때·사유. [칭호 주기]·줄의 [회수]는 `title.manage` 이고 부모가 대화상자를 띄운다(이벤트).
        목록은 부모(회원 관리 화면)가 `GET /api/admin/members/{id}/titles` 로 받아 내려 준다(`view`). 탈퇴 회원은 단추가 없다.
    -->
    <div class="flex flex-col gap-3">
        <ErrorState v-if="view?.error" :title="t('memberTitle.member.loadFailed')" :description="view.error.message" retry :retrying="view.loading" @retry="emit('retry-titles')" />
        <div v-else-if="!view || view.items === null" class="flex flex-col gap-2" aria-hidden="true">
            <Skeleton height="1.75rem" />
            <Skeleton height="1.75rem" width="70%" />
        </div>
        <template v-else>
            <p v-if="view.items.length === 0" class="m-0 text-[13px] text-muted-color">{{ t('memberTitle.member.empty') }}</p>
            <ul v-else class="m-0 flex list-none flex-col p-0">
                <li v-for="row in view.items" :key="row.id" class="flex flex-wrap items-center gap-x-3 gap-y-1 border-b border-(--line) py-2 last:border-b-0">
                    <TitleChip :title="row" :muted="!row.active" />
                    <span v-if="row.period" class="text-[12px] tabular-nums text-muted-color">{{ UtilTitleMessages.period(t, row.period) }}</span>
                    <span class="text-[12px] text-muted-color">{{ t(`memberTitle.source.${row.source}`) }} · <span class="tabular-nums">{{ time.date(row.grantedAt) }}</span></span>
                    <span v-if="!row.active" class="basis-full text-[12px] text-red-700 dark:text-red-400">{{ t('memberTitle.member.revokedLine', { at: row.revokedAt ? time.date(row.revokedAt) : '—', reason: row.revokedReason ?? '' }) }}</span>
                    <Button v-else-if="canManage" type="button" :label="t('memberTitle.revoke')" icon="pi pi-times" severity="danger" variant="text" size="small" class="ml-auto" @click="emit('revoke-title', row)" />
                </li>
            </ul>
            <div v-if="canManage" class="flex justify-end">
                <Button type="button" :label="t('memberTitle.member.grant')" icon="pi pi-star" size="small" severity="secondary" variant="outlined" data-testid="member-title-grant" @click="emit('grant-title')" />
            </div>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { MemberTitlesView } from '~/components/member/MemberDetailContent.types';
import type { TitleGrantRow } from '~/composables/title/api/useTitleApi.types';
import UtilTitleMessages from '~/utils/util-title-messages';

interface Props {
    /** 부모가 받은 칭호 목록 — 없으면 뼈대 */
    view?: MemberTitlesView | null;
    /** 칭호 주기·회수(`title.manage`, 탈퇴 회원이 아니고 부모가 대화상자를 다룰 때) */
    canManage?: boolean;
}

withDefaults(defineProps<Props>(), { view: null, canManage: false });
const emit = defineEmits<{ 'grant-title': []; 'revoke-title': [row: TitleGrantRow]; 'retry-titles': [] }>();

const { t } = useI18n();
const time = useDisplayTime();
</script>
