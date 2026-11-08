<template>
    <!--
        핫딜 게시판 목록형(LIST) — 가격 줄 목록(`HotdealRowItem`). 모바일·데스크톱이 같은 줄 모양이다(가격 비교가 목적이라 표 대신 줄). 칩 위치 설정과 상관없이 늘 같은 모양.
        고정 공지는 맨 위에 배경, 신고로 숨겨진 글은 자리만, 차단한 회원·단어의 글은 접힌 한 줄. 한 줄 광고(`feedAd`)는 그 차례 뒤에.
        속성 계약은 일반 목록과 같다(`BoardPostListProps`).
    -->
    <ul class="-mx-4 m-0 list-none p-0 md:mx-0" :aria-label="t('hotdeal.listCaption')" data-testid="hotdeal-list">
        <template v-for="(row, i) in all" :key="`${pinnedIds.has(row.id) ? 'p' : ''}${row.id}`">
            <li class="border-b border-surface" :class="pinnedIds.has(row.id) ? 'bg-surface-100 dark:bg-surface-800/60' : ''">
                <p v-if="row.hiddenReason" class="flex items-center gap-1.5 px-4 py-3 text-sm text-muted-color md:px-1">
                    <i class="pi pi-eye-slash text-xs" aria-hidden="true" />{{ t(row.hiddenReason === 'STAFF' ? 'board.hiddenPostStaff' : 'board.hiddenPost') }}
                </p>
                <FoldedRow v-else :reason="overrides.blockedOf(row.author.memberId, row.blocked)" kind="post" class="px-4 py-2.5 text-sm" hide-class="px-4 pb-2">
                    <HotdealRowItem :row="row" :date-display="dateDisplay" :new-post-hours="newPostHours" />
                </FoldedRow>
            </li>
            <li v-if="UtilAd.feedAdAt(feedAd, i - pinned.length)" class="border-b border-surface"><AdFeedRow :ad="UtilAd.feedAdAt(feedAd, i - pinned.length)!" variant="compact" /></li>
        </template>
    </ul>
</template>

<script setup lang="ts">
import UtilAd from '~/utils/util-ad';
import type { BoardPostListProps } from '~/components/board/BoardPostList.types';

interface Props extends BoardPostListProps {}

/** 칩 위치(`chipPosition`)는 계약이라 받기만 한다 — 핫딜 목록은 사용자 시안(2026-11-08)의 한 모양 고정(윗줄 칩·몰 이름·추천·조회 → 사진·제목·가격 → 작성자·시각·댓글, `HotdealRowItem`) */
const props = withDefaults(defineProps<Props>(), { feedAd: null, chipPosition: 'ABOVE' });

const { t } = useI18n();
const overrides = useBlockOverrides();
const pinnedIds = computed(() => new Set(props.pinned.map((p) => p.id)));
const all = computed(() => [...props.pinned, ...props.rows]);
/** `all` 에서 한 줄 광고가 뒤에 붙는 차례 — 공지는 세지 않는다 */
</script>
