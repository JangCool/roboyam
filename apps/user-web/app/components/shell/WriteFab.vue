<template>
    <!-- 떠 있는 글쓰기 단추 — 홈(로그인한 사람) · 게시판 목록(그 게시판에 쓸 수 있을 때 — 머리의 글쓰기 단추와 함께 늘) · 글 상세(그 게시판에 쓸 수 있을 때)에서만. 모양·자리는 `WriteFloatButton` -->
    <WriteFloatButton v-if="visible" :to="target" />
</template>

<script setup lang="ts">
import UtilBusiness from '~/utils/util-business';

/**
 * 게시판 목록에서는 게시판 화면이 알려 주는 글쓰기 판정(`useCurrentBoard().write` — 서버의 canWrite)을 따른다: 쓸 수 있을 때만 그린다(비회원 글쓰기 게시판이면 비회원에게도).
 * 게시판 머리에 글쓰기 단추가 있어도 늘 보인다(2026-11-06 사용자 — 전에는 머리가 스크롤로 가려졌을 때만, `useCurrentBoard().writeInView`).
 * 글 상세(`/posts/{id}` — 소모임 글 제외)는 글 화면이 알려 준 그 게시판 판정을 따르고, 누르면 그 게시판 글쓰기로 간다(머리 단추가 없어 늘 보인다).
 * 홈에서는 로그인했고 사업자 전용 계정이 아니면 그린다(게시판은 글쓰기 화면에서 고른다). 모바일·데스크톱 모두(2026-11-05 — 전에는 모바일만).
 * ⚠ community 의 직접홍보·핫딜 사업자 규칙(`promoBlocked`·`businessMayWrite`)은 그 게시판 종류가 아직 없어 가져오지 않았다.
 */
const route = useRoute();
const { user } = useAuth();
const { slug, write } = useCurrentBoard();

/** 글쓰기 버튼이 있는 곳 — 글쓰기·게시판 허브·내 정보에는 없다(가젯 페이지는 그 페이지가 따로 띄운다) */
const onHome = computed(() => route.path === '/');
const onBoard = computed(() => route.path.startsWith('/boards/') && route.path !== '/boards/');
const onPost = computed(() => /^\/posts\/[^/]+\/?$/.test(route.path));

/** 사업자 전용 계정(H-2)은 일반 게시판에 글을 쓸 수 없다 — 버튼을 그리지 않는다 */
const businessAccount = computed(() => UtilBusiness.isBusinessAccount(user.value?.role));

/** 지금 게시판에서 쓸 수 있는가 — 게시판 화면이 판정을 알려 준 뒤에만 */
const boardWritable = computed(() => Boolean(write.value && write.value.slug === slug.value && write.value.canWrite));

const visible = computed(() => {
    if (businessAccount.value) return false;
    if (onBoard.value || onPost.value) return boardWritable.value;
    return onHome.value && Boolean(user.value);
});

/** 게시판 목록·글 상세면 그 게시판으로, 홈이면 게시판을 고르는 글쓰기 화면으로 */
const target = computed(() => ((onBoard.value || onPost.value) && slug.value ? { path: '/write', query: { board: slug.value } } : { path: '/write' }));
</script>
