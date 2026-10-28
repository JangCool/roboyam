<template>
    <!--
        네이버식 데스크톱 셸 — 윗줄(흰 면 · 높이 56 · 맨 위 2px 주 색상 강조선 · 아래 1px 선: 로고 + 최상위 항목 작은 링크들 … 계정 부분) + 주 색상 탭 바(`NaverMenuBar`, 창 위에 붙는다) + 본문 + 푸터.
        메뉴 위치(`menuPosition`, 레이아웃이 정한다): TOP(테마 기본값) = 윗줄 아래 주 색상 탭 바 · TOP_LEFT·TOP_RIGHT = 탭 바(상단 띠 그룹) + 세로 메뉴(나머지 그룹, `UtilMenu.split`) ·
          LEFT·RIGHT = 탭 바를 빼고 본문 옆에 세로 메뉴
          (`AppSideMenu` + `shell-card` — 흰 면 · 반경 10 · 그림자 없음, 지금 있는 곳은 연한 바탕 + 본문 쪽 3px 주 색상 막대 — `main.css`). 윗줄은 어느 위치든 그대로다.
        최상위 항목(`topItems`): LOGO(테마 기본값) = 윗줄 로고 옆 작은 링크 줄 · MENU = 메뉴 안(탭 바 맨 앞 또는 세로 메뉴).
        강조선·탭 바는 상표색이 아니라 사이트 주 색상이다.
        모바일(md 미만)은 테마 공통 — 기본 상단 바(`AppHeader`, 안쪽 화면이면 숨김)를 그대로 쓰고 윗줄·탭 바는 숨는다. 하단 탭·드로어·상단 탭은 레이아웃이 그린다.
        props·slots 계약은 `ThemeShell.types.ts`. `sideMenu=false`(글쓰기)는 사이드 메뉴만 숨기고 상단 메뉴는 그대로 둔다.
        상단 메뉴 폭(`menuWidth`)은 이 셸에서 모양이 같다 — 탭 바의 주 색상 바탕이 이미 창 폭이고 탭은 본문 폭 상자 안에서 시작한다.
        탭 바가 창 위에 붙으려면 레이아웃 세로 줄의 직속 자식이어야 해서 윗줄과 따로 둔다(셸은 루트가 여럿이다).
        메뉴 넘침 표시(`menuOverflow`)는 탭 바·로고 옆 링크 줄에 그대로 전한다(얇은 스크롤이면 탭 바가 예약 자리만큼 높아 사이드 메뉴의 붙는 높이도 `barOverflow` 로 맞춘다).
        로그인 단추의 작은 네모 상자 모양은 2단계 CSS(`main.css` 의 `html[data-theme='naver'] .shell-topbar .nav-btn-outline`)다.
    -->
    <AppHeader :class="inner ? 'hidden' : 'md:hidden'" @open-menu="emit('open-menu')" />

    <header class="shell-topbar relative border-b border-hairline bg-card [--scroll-fade-bg:var(--card-bg)] before:absolute before:inset-x-0 before:top-0 before:h-0.5 before:bg-primary max-md:hidden">
        <div class="mx-auto flex h-14 w-full max-w-[var(--content-max)] items-center gap-1 px-4">
            <NuxtLink
                to="/"
                class="flex min-w-0 shrink-0 items-center rounded pr-3 text-xl font-extrabold tracking-tight focus-visible:outline-2 focus-visible:outline-primary"
                :aria-label="t('shell.homeLink', { site: siteLabel })"
            >
                <SiteBrand />
            </NuxtLink>

            <!-- 최상위 항목(홈·공동구매 …) — 「로고 옆」(테마 기본값)일 때 작은 흐린 링크 줄(`AppLogoLinks`) -->
            <AppLogoLinks v-if="topItems === 'LOGO'" :overflow="menuOverflow" />

            <span class="flex-1" />
            <AppHeaderActions />
        </div>
    </header>
    <NaverMenuBar v-if="hasBar" :overflow="menuOverflow" />
    <slot name="after-header" />

    <div class="mx-auto flex w-full max-w-[var(--content-max)] flex-1 items-start" :class="side ? 'md:gap-4 md:px-4' : ''">
        <!-- 사이드 메뉴(데스크톱, 사이드 위치일 때만) — 흰 면 카드. 본문 첫 줄과 높이를 맞춘다 -->
        <AppSideMenu v-if="side" :side="side" :with-bar="hasBar" :bar-overflow="menuOverflow" class="shell-card mt-6 py-2" />
        <main id="main-content" class="min-w-0 flex-1 px-4 py-4 md:py-6" :class="side ? 'md:px-0' : ''">
            <slot />
        </main>
    </div>

    <AppFooter class="max-md:hidden" />
</template>

<script setup lang="ts">
import type { ThemeShellProps } from '~/components/themes/ThemeShell.types';
import UtilMenuPosition from '~/utils/util-menu-position';

interface Props extends ThemeShellProps {}
const props = withDefaults(defineProps<Props>(), { inner: false, sideMenu: true });
const emit = defineEmits<{ (e: 'open-menu'): void }>();

const { t } = useI18n();
const { siteLabel } = useSiteName();
/** 사이드 메뉴의 쪽 — 상단 위치이거나 페이지가 메뉴를 껐으면 null */
const side = computed(() => (props.sideMenu ? UtilMenuPosition.sideOf(props.menuPosition) : null));
/** 상단 탭 바가 있는 배치인가 */
const hasBar = computed(() => UtilMenuPosition.hasBar(props.menuPosition));
</script>
