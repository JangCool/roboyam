<template>
    <!--
        앱 셸 (design 01-shell) — 테마와 무관한 일만 한다. 데스크톱 배치(헤더·메뉴 위치·본문 폭·푸터)는 현재 테마의 셸 컴포넌트가 그린다(`UtilTheme.resolve(…, 'shell')`, 계약은 `components/themes/ThemeShell.types.ts`).
          모바일(테마 공통): 상단 바 + 상단 가로 탭 + 하단 탭(홈·최신글·커뮤니티·소모임·게시판·내 정보) + 글쓰기 버튼, 햄버거 메뉴
          데스크톱: 테마가 정한다 — 기본 테마는 상단 바 + 왼쪽 메뉴 + 본문 + 푸터, 다음식·네이버식은 상단 메뉴(왼쪽 메뉴 없음)
        안쪽 화면(글 상세·글쓰기)은 `definePageMeta({ shell: 'inner' })` — 모바일에서 상단 바·탭·하단 탭을 숨기고 페이지가 ‹ 헤더를 그린다.
        오른쪽 사이드(광고·공동구매 등)는 가젯이 본문 옆에 그린다.
    -->
    <!-- 바탕은 역할 변수 `--page-bg`(테마가 채운다, 기본 = surface-50 · 다크 surface-950) -->
    <!-- `relative` — 날개 배너 층(`AdWings`)이 이 세로 줄을 기준으로 본문 바깥에 뜬다.
         `overflow-x-clip` — 어느 화면이든 화면 폭보다 넓은 것(가로 넘김 상자를 빠져나온 절대 위치 `sr-only` 글자 등)이 문서를 넓히지 못하게 자른다.
         문서가 넓어지면 모바일에서 페이지가 옆으로 밀려 상단 바·하단 탭이 어긋난다. `hidden` 과 달리 `clip` 은 스크롤 상자를 만들지 않아 sticky 막대가 그대로 붙는다 -->
    <div class="relative flex min-h-dvh flex-col overflow-x-clip bg-page">
        <a
            href="#main-content"
            class="sr-only focus:not-sr-only focus:fixed focus:left-2 focus:top-2 focus:z-[3000] focus:rounded focus:bg-surface-0 focus:px-3 focus:py-2 focus:text-sm focus:shadow-lg dark:focus:bg-surface-900"
        >
            {{ t('shell.skipToContent') }}
        </a>

        <component :is="shell" :inner="inner" :side-menu="sideMenu" :menu-position="menuPosition" :top-items="topItems" :menu-width="menuWidth" :menu-overflow="menuOverflow" @open-menu="menuOpen = true">
            <!-- 헤더 아래(테마가 놓는 자리) — 정지 중 안내(7-2, design 20-system 3)는 기간 정지·영구 정지 계정에만 보이고 제재 안내(`/sanction`)로 이어 준다(모바일은 상단 탭 위).
                 상단 가로 탭은 모바일 전용이고, 이메일 인증 전 안내는 로그인은 됐지만 글·댓글·추천·쪽지는 인증 뒤에 할 수 있다는 안내다 -->
            <template #after-header>
                <RestrictionBanner />
                <!-- 탈퇴 진행 중 안내 띠(로그인 정보 `withdrawalScheduled`) — 탈퇴 화면(`/settings/account/withdraw`)으로 이어 준다 -->
                <WithdrawalBanner />
                <AppTopTabs v-if="!inner" class="md:hidden" />
                <EmailVerifyBanner />
                <!-- 약관 변경 안내 띠(약관 요약의 `notice`, 닫기는 이 브라우저에 기억) -->
                <TermsChangeNotice />
                <!-- 공지 띠(운영자 설정 > 일반 「공지 띠」) — 메뉴 줄 아래·본문 위, 모든 화면. 모바일 안쪽 화면(셸 막대를 숨기는 곳)에서는 숨긴다 -->
                <SiteNoticeBar :class="inner ? 'max-md:hidden' : ''" />
            </template>

            <!-- 내 정보 묶음 화면(`definePageMeta({ meMenu })`)은 오른쪽 메뉴 틀 안에 — 틀이 화면 사이에 남아 메뉴는 그대로, 왼쪽만 바뀐다 -->
            <MeFrame v-if="meMenu"><slot /></MeFrame>
            <slot v-else />
        </component>

        <AdWings />

        <!-- 팝업(운영자 관리자 「팝업 관리」) — 들어오면 화면 가운데 뜨는 이벤트·프로모션 창. 보일 곳(홈만/모든 화면)·기기는 부품이 가린다 -->
        <SitePopupLayer />

        <!-- 캐시 영역 겹침 층 — 관리자 캐시 관리 「미리보기」 iframe 안(`cachePreview=1`)에서만 그린다. 공개 화면에는 없다 -->
        <CacheAreaOverlay />

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
import type { MeMenuMeta } from '~/composables/me/useMeMenu';
import UtilTheme from '~/utils/util-theme';

const { t } = useI18n();
const route = useRoute();
const menuOpen = ref(false);

/** 안쪽 화면인가 — 모바일에서 셸의 위·아래 막대를 숨긴다 */
const inner = computed(() => route.meta.shell === 'inner');
/** 데스크톱 메뉴 — 글쓰기처럼 넓은 화면이 필요한 곳은 페이지가 끈다. 어디에 놓느냐는 테마가 정한다 */
const sideMenu = computed(() => route.meta.sideMenu !== false);
/** 내 정보 메뉴 틀 — 로그인했을 때만. 공개 프로필(`'self'`)은 내 프로필일 때만 */
const { user } = useAuth();
const meMenu = computed(() => {
    const meta = route.meta.meMenu as MeMenuMeta | undefined;
    if (!user.value || !meta) return false;
    return meta === true || String(route.params.id ?? '') === user.value.id;
});

/** 현재 테마의 셸 컴포넌트(없으면 기본 테마 것) */
const { theme } = useTheme();
const shell = computed(() => UtilTheme.resolve(theme.value, 'shell'));
/** 데스크톱 메뉴 배치 — 위치(회원 보기 설정 > 운영자 설정 > 테마 기본값)·최상위 항목 위치·상단 메뉴 폭(운영자 > 테마 기본)(`useMenuPosition`). 각 배치의 모양은 셸이 정한다 */
const { position: menuPosition, topItems, menuWidth, menuOverflow } = useMenuPosition();

// 게시판 목록(색 점 판정·기본 메뉴·허브·글쓰기가 쓴다)과 메뉴(`GET /api/menu` — 메뉴·탭·허브가 모두 쓴다)는 앱이 열릴 때 한 번 받는다.
// 실패해도 화면은 뜬다(메뉴를 못 받으면 게시판 목록으로 기본 메뉴를 그리고 다시 시도가 보인다). 로그인·로그아웃으로 사용자가 바뀌면 `useMenu` 가 메뉴를 다시 받는다
const { ensureLoaded } = useBoards();
void ensureLoaded();
void useMenu().ensureLoaded();

// 안 읽은 알림 수(상단 바의 알림 종) — 셸이 뜰 때 한 번만 주기 조회를 시작한다(로그인해 있고 탭이 보일 때 60초 간격). 셸이 사라지면 멈춘다
useNotificationBadge().start();
// 안 읽은 쪽지 대화 수(데스크톱 헤더의 쪽지 아이콘·메뉴의 "쪽지함" 줄) — 알림과 같은 방식으로 한 번만 시작한다
useDmBadge().start();
</script>
