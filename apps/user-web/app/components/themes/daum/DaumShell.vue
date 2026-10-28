<template>
    <!--
        다음식 데스크톱 셸 — 윗줄(페이지 바탕 위 로고 + 계정 부분, 높이 60) + 메뉴 + 본문 + 푸터. 윗줄은 창 위에 붙지 않는다.
        메뉴 위치(`menuPosition`, 레이아웃이 정한다): TOP(테마 기본값) = 윗줄 아래 흰 메뉴 카드(GNB·LNB, `DaumMenuBar`) ·
          LEFT·RIGHT = 메뉴 카드를 빼고 본문 옆에 흰 카드 모양 세로 메뉴(`AppSideMenu` + `shell-card`, 폭 176, 창 위 16px 에 붙고 안에서 스크롤) ·
          TOP_LEFT·TOP_RIGHT = 메뉴 카드(운영자가 고른 상단 띠 그룹) + 세로 메뉴(나머지 그룹) — 나누기는 `UtilMenu.split`.
        최상위 항목(`topItems`): MENU(테마 기본값) = 메뉴 안 · LOGO = 로고 옆 서비스 링크 자리의 링크 줄(`AppLogoLinks`, 다음식은 16px — `main.css`).
        모바일(md 미만)은 테마 공통 — 기본 상단 바(`AppHeader`, 안쪽 화면이면 숨김)를 그대로 쓰고 이 윗줄·카드는 숨는다. 하단 탭·드로어·상단 탭은 레이아웃이 그린다.
        props·slots 계약은 `ThemeShell.types.ts`. `sideMenu=false`(글쓰기)는 사이드 메뉴만 숨기고 상단 메뉴는 그대로 둔다.
        값(바탕·카드·선·탭 글자색·폭 1168)은 역할 변수로 읽고 `main.css` 의 다음식 값(`html:is([data-theme='daum'], [data-theme='roboyam'])`)이 채운다.
        로보얌 테마(`roboyam`)도 이 셸을 그대로 쓴다(`util-theme.ts` 의 같은 슬롯 로더) — 다른 것은 기본 배치(상단 + 사이드 왼쪽)뿐이다.
        상단 메뉴 폭(`menuWidth`): CONTENT(테마 기본값) = 메뉴 카드가 본문 폭 · FULL = 메뉴 카드가 창 좌우 끝까지 닿는 흰 띠(반경 0), 칸의 시작은 본문 열 왼쪽 끝 그대로.
        메뉴 넘침 표시(`menuOverflow`)는 메뉴 카드·로고 옆 링크 줄에 그대로 전한다(GNB·LNB 는 줄 안 아래 여백에 스크롤 표시를 두어 높이가 그대로다 — 메뉴 카드는 창 위에 붙지 않는다).
    -->
    <AppHeader :class="inner ? 'hidden' : 'md:hidden'" @open-menu="emit('open-menu')" />

    <header class="max-md:hidden">
        <div class="mx-auto w-full max-w-[var(--content-max)] px-4">
            <div class="flex h-[60px] items-center gap-1">
                <NuxtLink to="/" class="flex min-w-0 items-center rounded text-xl font-bold focus-visible:outline-2 focus-visible:outline-primary" :aria-label="t('shell.homeLink', { site: siteLabel })">
                    <SiteBrand />
                </NuxtLink>
                <!-- 서비스 링크 자리(다음 뉴스의 로고 옆 링크) — 최상위 항목이 「로고 옆」일 때 그 링크 줄 -->
                <AppLogoLinks v-if="topItems === 'LOGO'" :overflow="menuOverflow" class="ml-3" />
                <span class="flex-1" />
                <AppHeaderActions />
            </div>
        </div>
    </header>
    <!--
        메뉴 카드 — 윗줄과 나눠 셸 바로 아래에 둔다(그래야 창 위에 붙을 수 있다). 메뉴 붙이기(`stickyMenu` — 운영자 설정 > 테마 기본, 로보얌 기본 켜짐)면
        본문을 내릴 때 윗줄(로고·알림)은 위로 사라지고 이 카드가 창 맨 위에 붙는다(바탕은 페이지 바탕 — 본문 폭 카드 양옆으로 글이 비치지 않게).
        화면 전체 폭이면 본문 폭 상자 밖에 두고, 칸 정렬은 메뉴 카드 안에서 본문 폭으로 맞춘다.
    -->
    <div v-if="hasBar" class="max-md:hidden" :class="stickyMenu ? 'sticky top-0 z-20 bg-page' : ''" data-testid="daum-menu-wrap">
        <div v-if="!fullBar" class="mx-auto w-full max-w-[var(--content-max)] px-4">
            <DaumMenuBar :inline-groups="inlineGroups" :overflow="menuOverflow" :side-tabs="sideTabs" :compact="compact" :description="menuDescription" />
        </div>
        <DaumMenuBar v-else full :inline-groups="inlineGroups" :overflow="menuOverflow" :side-tabs="sideTabs" :compact="compact" :description="menuDescription" />
    </div>
    <slot name="after-header" />

    <div class="mx-auto flex w-full max-w-[var(--content-max)] flex-1 items-start" :class="side ? 'md:gap-4 md:px-4' : ''" :style="bodyStyle">
        <!-- 사이드 메뉴(데스크톱, 사이드 위치일 때만) — 흰 카드. 본문 첫 줄과 높이를 맞춘다 -->
        <AppSideMenu v-if="side" :side="side" :picked-only="sideTabs" class="shell-card mt-6 py-2" />
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
/** 상단 메뉴 카드가 있는 배치인가 */
const hasBar = computed(() => UtilMenuPosition.hasBar(props.menuPosition));
/** 메뉴 카드를 창 좌우 끝까지 늘리는가 */
const fullBar = computed(() => props.menuWidth === 'FULL');
/**
 * 메뉴 모양(운영자 설정 > 테마 기본, `useMenuStyle`) — 그룹을 둘째 줄 「이름 + 칸」으로(`inlineGroups`) · 사이드 그룹을 첫 줄 탭으로 골라 보기(`pickSideGroups`) ·
 * 메뉴 카드 한 단계 작게(`compactBar`) · 본문을 내리면 메뉴 카드를 창 위에 붙이기(`stickyMenu`, 윗줄은 사라진다)
 */
const { inlineGroups, pickSideGroups, compactBar: compact, stickyMenu, menuDescription } = useMenuStyle();
/** 사이드 그룹 탭 — 설정이 「골라 보기」이고 상단 막대와 사이드가 함께 있는 배치일 때만 */
const sideTabs = computed(() => pickSideGroups.value && hasBar.value && side.value !== null);
/**
 * 메뉴 카드가 붙을 때 사이드 메뉴가 그 아래에 붙도록 붙는 머리 높이(`--shell-header-h`, `main.css` 의 `.side-menu`)를 카드 높이로 채운다 —
 * 작은 카드 = 첫 줄 56 + 선 1 + 둘째 줄 46 = 103px, 보통 = 70 + 1 + 55 = 126px(`DaumMenuBar` 의 줄 높이). 붙지 않으면 테마 값(0) 그대로.
 */
const bodyStyle = computed(() => (stickyMenu.value && hasBar.value ? { '--shell-header-h': compact.value ? '103px' : '126px' } : undefined));
</script>
