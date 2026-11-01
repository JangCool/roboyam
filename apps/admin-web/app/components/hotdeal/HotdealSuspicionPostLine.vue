<template>
    <!--
        의심 후보 서랍의 글 한 줄 — 회원 후보 목록과 판매처 후보의 회원별 목록이 같이 쓴다.
        상태 · 제목(핫딜 글 서랍 `/hotdeals?view=ALL&deal=`) · 게시판 · 딜가 · 시각 · 신고 대기(신고 관리) · [글 관리에서 숨기기](글 관리 서랍 `/posts?post=` — 숨김과 사유 창은 그쪽 것을 쓴다).
    -->
    <div class="flex flex-col gap-1">
        <div class="flex min-w-0 flex-wrap items-center gap-1.5">
            <HotdealViewTag :view="post.view" />
            <NuxtLink :to="{ path: '/hotdeals', query: { view: 'ALL', deal: post.postId } }" class="min-w-0 text-[13px] font-medium break-words text-primary hover:underline">{{ post.title }}</NuxtLink>
        </div>
        <p class="m-0 flex flex-wrap items-center gap-x-2 gap-y-0.5 text-xs text-muted-color tabular-nums">
            <span>{{ localizedName(post.board.name, post.board.nameEn) }}</span>
            <span>{{ UtilHotdealMessages.won(t, locale, post.dealPrice) }}</span>
            <time :datetime="post.createdAt" :title="time.full(post.createdAt)">{{ time.listLabel(post.createdAt) }}</time>
            <NuxtLink v-if="post.openReports > 0 && canReadReports" :to="UtilPost.reportsLink(post.postId)" class="text-red-600 hover:underline dark:text-red-400">{{ t('hotdeal.reportsPending', { n: post.openReports }) }}</NuxtLink>
            <span v-else-if="post.openReports > 0" class="text-red-600 dark:text-red-400">{{ t('hotdeal.reportsPending', { n: post.openReports }) }}</span>
            <NuxtLink v-if="canReadPosts" :to="{ path: '/posts', query: { post: post.postId } }" class="ml-auto inline-flex items-center gap-1 text-primary hover:underline" :aria-label="t('hotdeal.suspicion.hidePostLabel', { title: post.title })">
                <i class="pi pi-eye-slash text-[10px]" aria-hidden="true" />{{ t('hotdeal.suspicion.hidePost') }}
            </NuxtLink>
        </p>
    </div>
</template>

<script setup lang="ts">
import type { DealSuspicionPost } from '~/composables/hotdeal/api/useHotdealSuspicionApi.types';
import UtilHotdealMessages from '~/utils/util-hotdeal-messages';
import UtilPost from '~/utils/util-post';

interface Props {
    post: DealSuspicionPost;
    canReadReports?: boolean;
    canReadPosts?: boolean;
}

withDefaults(defineProps<Props>(), { canReadReports: false, canReadPosts: false });

const { t, locale } = useI18n();
const { localizedName } = useLocalizedName();
const time = useDisplayTime();
</script>
