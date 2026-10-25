<template>
    <!--
        앱 셸 (design 01-shell) — 테마와 무관한 일만 한다. 데스크톱 배치(헤더·메뉴 위치·본문 폭·푸터)는 현재 테마의 셸 컴포넌트가 그린다(`UtilTheme.resolve(…, 'shell')`, 계약은 `components/themes/ThemeShell.types.ts`).
          모바일(테마 공통): 상단 바 + 상단 가로 탭 + 하단 탭(홈·게시판·알림·내 정보) + 글쓰기 버튼, 햄버거 메뉴
          데스크톱: 테마가 정한다 — 기본 테마는 상단 바 + 왼쪽 메뉴 + 본문 + 푸터
        안쪽 화면(글 상세·글쓰기)은 `definePageMeta({ shell: 'inner' })` — 모바일에서 상단 바·탭·하단 탭을 숨기고 페이지가 ‹ 헤더를 그린다.
        오른쪽 사이드(광고·공동구매 등)는 홈 가젯이 본문 옆에 그린다.
    -->
    <div class="flex min-h-dvh flex-col bg-surface-50 dark:bg-surface-950">
        <a
            href="#main-content"
            class="sr-only focus:not-sr-only focus:fixed focus:left-2 focus:top-2 focus:z-[3000] focus:rounded focus:bg-surface-0 focus:px-3 focus:py-2 focus:text-sm focus:shadow-lg dark:focus:bg-surface-900"
        >
            {{ t('shell.skipToContent') }}
        </a>

        <component :is="shell" :inner="inner" :side-menu="sideMenu" @open-menu="menuOpen = true">
            <!-- 헤더 아래(테마가 놓는 자리) — 정지 중 안내(7-2, design 20-system 3)는 기간 정지·영구 정지 계정에만 보이고 제재 안내(`/sanction`)로 이어 준다(모바일은 상단 탭 위).
                 상단 가로 탭은 모바일 전용이고, 이메일 인증 전 안내는 로그인은 됐지만 글·댓글·추천·쪽지는 인증 뒤에 할 수 있다는 안내다 -->
            <template #after-header>
                <RestrictionBanner />
                <AppTopTabs v-if="!inner" class="md:hidden" />
                <EmailVerifyBanner />
                <!-- 약관 변경 안내 띠(약관 요약의 `notice`, 닫기는 이 브라우저에 기억) -->
                <TermsChangeNotice />
            </template>

            <slot />
        </component>

        <AppBottomTabs v-if="!inner" class="md:hidden" />

        <WriteFab />
        <AppDrawer v-model:visible="menuOpen" />
        <LoginPromptSheet />
        <!-- 이용자 메모 시트 — 프로필 ⋯ 메뉴·쪽지 대화방·설정 > 이용자 메모가 `useMemoSheet().open` 으로 연다 -->
        <MemoSheet />
        <!-- 원본(community)에는 여기에 신고 시트(`ReportSheet`, 7-1)가 있다 — 신고를 여는 화면(글·댓글·프로필 …)이 아직 없어 가져오지 않았다 -->
        <!-- 결제 직전 본인인증 확인(`useIdentityGate`)과 가짜 본인인증(개발용 — 서버가 FAKE 일 때) 대화상자 -->
        <IdentityGateDialog />
        <IdentityFakeDialog />
        <AppConfirm />
        <AppToaster :with-tabs="!inner" />
    </div>
</template>

<script setup lang="ts">
import UtilTheme from '~/utils/util-theme';

const { t } = useI18n();
const route = useRoute();
const menuOpen = ref(false);

/** 안쪽 화면인가 — 모바일에서 셸의 위·아래 막대를 숨긴다 */
const inner = computed(() => route.meta.shell === 'inner');
/** 데스크톱 메뉴 — 글쓰기처럼 넓은 화면이 필요한 곳은 페이지가 끈다. 어디에 놓느냐는 테마가 정한다 */
const sideMenu = computed(() => route.meta.sideMenu !== false);

/** 현재 테마의 셸 컴포넌트(없으면 기본 테마 것) */
const { theme } = useTheme();
const shell = computed(() => UtilTheme.resolve(theme.value, 'shell'));

// 게시판 목록(색 점 판정·기본 메뉴·허브·글쓰기가 쓴다)과 메뉴(`GET /api/menu` — 메뉴·탭·허브가 모두 쓴다)는 앱이 열릴 때 한 번 받는다.
// 실패해도 화면은 뜬다(메뉴를 못 받으면 게시판 목록으로 기본 메뉴를 그리고 다시 시도가 보인다). 로그인·로그아웃으로 사용자가 바뀌면 `useMenu` 가 메뉴를 다시 받는다
const { ensureLoaded } = useBoards();
void ensureLoaded();
void useMenu().ensureLoaded();

// 안 읽은 알림 수(헤더의 종·하단 탭의 알림) — 셸이 뜰 때 한 번만 주기 조회를 시작한다(로그인해 있고 탭이 보일 때 60초 간격). 셸이 사라지면 멈춘다
useNotificationBadge().start();
// 안 읽은 쪽지 대화 수(데스크톱 헤더의 쪽지 아이콘·메뉴의 "쪽지함" 줄) — 알림과 같은 방식으로 한 번만 시작한다
useDmBadge().start();
</script>
