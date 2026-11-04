<template>
    <!--
        회원 상세 — 「게시판 제재」 구획(docs/features/board-sanction-contract.md 「화면 > admin-web」). 사이트 제재 구획(`MemberDetailSanctionSection`)과 따로 센다.
        위에 한 줄 요약(무효 아닌 n건 · 진행 중 쓰기 제한 n건 · 열린 반복 검토 n건) + [게시판 제재 이력 보기](`/board-sanctions?q=<닉네임>`, `sanction.read`),
        가운데 게시판별 건수(경고 n · 쓰기 제한 n · 진행 중이면 끝 시각), 아래 최근 이력 몇 줄(`BoardSanctionHistoryList` — 번호는 게시판 제재 서랍 링크).
        값은 회원 상세 응답의 `boardSanctions`(⚠ MOCK: 서버가 아직 주지 않아 `useMemberApi` 가 목업으로 채운다). 없으면(null — 탈퇴 회원 등) 「아직 연동되지 않았어요」 안내.
        조치(해제·무효)는 여기서 하지 않는다 — 게시판 제재 화면에서 한다.
    -->
    <EmptyState v-if="!summary" icon="pi pi-link" :title="t('member.detail.notReady.title')" :description="t('boardSanction.memberSection.notReady')" />
    <div v-else class="flex flex-col gap-4">
        <div class="flex flex-wrap items-center justify-between gap-2">
            <p class="m-0 flex flex-wrap items-center gap-x-3 gap-y-1 text-[13px]">
                <span class="tabular-nums">{{ t('boardSanction.memberSection.summary', { total: summary.total, active: summary.active }) }}</span>
                <NuxtLink v-if="summary.openReviews > 0 && canRead" to="/board-sanctions/reviews" class="inline-flex items-center gap-1 rounded-full bg-amber-100 px-2 py-0.5 text-[11px] font-medium text-amber-800 hover:underline focus-visible:outline-2 focus-visible:outline-primary dark:bg-amber-950 dark:text-amber-300">
                    <i class="pi pi-eye text-[9px]" aria-hidden="true" />{{ t('boardSanction.memberSection.openReviews', { n: summary.openReviews }) }}
                </NuxtLink>
            </p>
            <NuxtLink v-if="canRead" :to="{ path: '/board-sanctions', query: { q: detail.nickname } }" class="inline-flex items-center gap-1 rounded px-1 text-xs text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary">
                <i class="pi pi-list-check text-[10px]" aria-hidden="true" />{{ t('boardSanction.memberSection.viewAll') }}
            </NuxtLink>
        </div>

        <p v-if="!summary.boards.length && !summary.recent.length" class="m-0 text-[13px] text-muted-color">{{ t('boardSanction.memberSection.empty') }}</p>

        <section v-if="summary.boards.length" :aria-labelledby="boardsId">
            <h3 :id="boardsId" class="mb-2 text-[13px] font-semibold">{{ t('boardSanction.memberSection.boardsTitle') }}</h3>
            <ul class="m-0 grid list-none grid-cols-1 gap-2 p-0 @md:grid-cols-2">
                <li v-for="row in summary.boards" :key="row.board.id" class="flex flex-col gap-0.5 rounded-lg border border-(--line) px-3 py-2">
                    <span class="truncate text-[13px] font-medium">{{ localizedName(row.board.name, row.board.nameEn) }}</span>
                    <span class="text-xs tabular-nums text-muted-color">{{ t('boardSanction.memberSection.boardLine', { w: row.warningCount, r: row.restrictCount }) }}</span>
                    <span v-if="row.activeUntil" class="text-xs tabular-nums text-red-600 dark:text-red-400">{{ t('boardSanction.review.activeUntil', { at: time.full(row.activeUntil) }) }}</span>
                </li>
            </ul>
        </section>

        <section v-if="summary.recent.length" :aria-labelledby="recentId">
            <h3 :id="recentId" class="mb-2 text-[13px] font-semibold">{{ t('boardSanction.memberSection.recentTitle') }}</h3>
            <BoardSanctionHistoryList :lines="summary.recent.slice(0, UtilBoardSanction.MEMBER_RECENT)" :link-sanction="canRead" />
        </section>
    </div>
</template>

<script setup lang="ts">
import type { MemberDetail } from '~/composables/member/api/useMemberApi.types';
import UtilBoardSanction from '~/utils/util-board-sanction';

interface Props {
    detail: MemberDetail;
}

const props = withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
const time = useDisplayTime();
const { localizedName } = useLocalizedName();
const permission = useSanctionPermission();
const canRead = computed(() => permission.canRead.value);
const boardsId = useId();
const recentId = useId();

const summary = computed(() => props.detail.boardSanctions ?? null);
</script>
