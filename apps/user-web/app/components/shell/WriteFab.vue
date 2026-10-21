<template>
    <!-- 글쓰기 플로팅 버튼(모바일) — 로그인한 사람에게만, 홈과 게시판 목록에서만 (design 01-shell) -->
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
 * ⚠ 단순 버전 — 원본(community)은 게시판 화면이 알려 주는 글쓰기 가능 여부(`useCurrentBoard().write` — 운영진 전용·정지·등급 미달·쓰기 잠금 …)와
 * 직접홍보 게시판(`UtilPromo`)·핫딜 게시판의 사업자 규칙으로 버튼을 숨긴다. 게시판 화면이 아직 없어 그 판정 입력이 없으므로, 로그인했고 사업자 전용 계정이 아니면
 * 홈과 게시판 목록에서 버튼을 그린다. 게시판 화면을 만들 때 원본의 `cannotWrite`·`promoBlocked`·`businessMayWrite` 를 되살린다.
 */
const { t } = useI18n();
const route = useRoute();
const { user } = useAuth();
const { slug } = useCurrentBoard();

/** 글쓰기 버튼이 있는 곳 — 글 상세·글쓰기·게시판 허브·내 정보에는 없다(안쪽 화면은 하단 탭도 없다) */
const onHome = computed(() => route.path === '/');
const onBoard = computed(() => route.path.startsWith('/boards/') && route.path !== '/boards/');

/** 사업자 전용 계정(H-2)은 일반 게시판에 글을 쓸 수 없다 — 버튼을 그리지 않는다 */
const businessAccount = computed(() => UtilBusiness.isBusinessAccount(user.value?.role));

const visible = computed(() => Boolean(user.value) && !businessAccount.value && (onHome.value || onBoard.value));

/** 게시판 목록이면 그 게시판으로, 홈이면 게시판을 고르는 글쓰기 화면으로 */
const target = computed(() => (onBoard.value && slug.value ? { path: '/write', query: { board: slug.value } } : { path: '/write' }));
</script>
