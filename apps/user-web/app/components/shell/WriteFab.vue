<template>
    <!-- 글쓰기 플로팅 버튼(모바일) — 홈(로그인한 사람)과 게시판 목록(그 게시판에 쓸 수 있을 때)에서만 (design 01-shell) -->
    <NuxtLink
        v-if="visible"
        :to="target"
        class="fixed right-4 bottom-[calc(4.5rem+env(safe-area-inset-bottom))] z-30 flex items-center gap-1.5 rounded-full bg-surface-900 px-4 py-2.5 text-sm font-semibold text-surface-0 shadow-lg focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary md:hidden dark:bg-surface-100 dark:text-surface-900"
        :aria-label="t('shell.write')"
    >
        <i class="pi pi-pencil text-sm" aria-hidden="true" />
        {{ t('shell.write') }}
    </NuxtLink>
</template>

<script setup lang="ts">
import UtilBusiness from '~/utils/util-business';

/**
 * 게시판 목록에서는 게시판 화면이 알려 주는 글쓰기 판정(`useCurrentBoard().write` — 서버의 canWrite)을 따른다: 쓸 수 있을 때만 그린다(비회원 글쓰기 게시판이면 비회원에게도).
 * 홈에서는 로그인했고 사업자 전용 계정이 아니면 그린다(게시판은 글쓰기 화면에서 고른다).
 * ⚠ community 의 직접홍보·핫딜 사업자 규칙(`promoBlocked`·`businessMayWrite`)은 그 게시판 종류가 아직 없어 가져오지 않았다.
 */
const { t } = useI18n();
const route = useRoute();
const { user } = useAuth();
const { slug, write } = useCurrentBoard();

/** 글쓰기 버튼이 있는 곳 — 글 상세·글쓰기·게시판 허브·내 정보에는 없다(안쪽 화면은 하단 탭도 없다) */
const onHome = computed(() => route.path === '/');
const onBoard = computed(() => route.path.startsWith('/boards/') && route.path !== '/boards/');

/** 사업자 전용 계정(H-2)은 일반 게시판에 글을 쓸 수 없다 — 버튼을 그리지 않는다 */
const businessAccount = computed(() => UtilBusiness.isBusinessAccount(user.value?.role));

/** 지금 게시판에서 쓸 수 있는가 — 게시판 화면이 판정을 알려 준 뒤에만 */
const boardWritable = computed(() => Boolean(write.value && write.value.slug === slug.value && write.value.canWrite));

const visible = computed(() => !businessAccount.value && (onBoard.value ? boardWritable.value : onHome.value && Boolean(user.value)));

/** 게시판 목록이면 그 게시판으로, 홈이면 게시판을 고르는 글쓰기 화면으로 */
const target = computed(() => (onBoard.value && slug.value ? { path: '/write', query: { board: slug.value } } : { path: '/write' }));
</script>
