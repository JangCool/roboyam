<template>
    <!--
        네이버식 탭 바(데스크톱 전용, 창 위에 붙는다) — 주 색상 바(`.app-nav`, 높이 50) 위에 최상위 그룹마다 "그룹 이름(작은 글자) + 직속 칸 탭". 탭은 바 아래에 붙는다.
        활성 탭은 흰 글자 굵게 + 3px 흰 밑줄(위쪽만 둥글게), 비활성은 흰 글자 82%. 그룹 안의 하위 그룹은 드롭다운(`AppTopMenuDropdown`).
        최상위 항목(홈·공동구매 …)은 「로고 옆」(테마 기본)이면 윗줄의 작은 링크(`AppLogoLinks`)이고, 「메뉴 안」이면 이 바 맨 앞에 제목 없이 탭으로 온다.
        구역 구조는 `AppTopMenu` 와 같은 `useMenuItems('bar')`(섞인 배치면 상단 띠 그룹만, `UtilMenu.split`)이고 겉모양만 다르다. 로딩·오류 안내는 사이드가 없을 때만 여기서 그린다.
        넘치면 옆으로 밀고 활성 칸을 가운데로 끌어온다. 모바일은 테마 공통 셸이 맡는다(md 미만에서 숨는다).
        넘칠 때의 표시(`overflow`)는 공통 `AppScrollRow` 가 그린다. 탭이 바 바닥에 붙어(활성 3px 밑줄이 바닥) 줄 안에 빈 아래 여백이 없으므로,
        얇은 스크롤(`SCROLLBAR`)이면 탭 줄(50) 아래에 예약 자리(`--scroll-reserve` 7px)를 처음부터 덧붙인다(`reserve` — 바 57px, 넘쳐도 그대로). 화살표 단추는 바 색(`--nav-bg`)에서 사라진다.
    -->
    <nav :aria-label="t('menu.navLabel')" class="app-nav sticky top-0 z-30 max-md:hidden" data-testid="naver-menu">
        <div class="mx-auto flex w-full max-w-[var(--content-max)] px-4">
            <AppScrollRow ref="row" :mode="overflow" reserve class="flex-1" scroller-class="flex h-[50px] items-end whitespace-nowrap">
                <AppMenuBarState v-if="ownsState" :state="state" :loading="loading" class="self-center" @reload="reload" />

                <template v-if="showEntries">
                    <section
                        v-for="(section, index) in bar"
                        :key="section.key"
                        :aria-labelledby="section.title ? `${uid}-${section.key}` : undefined"
                        class="flex shrink-0 items-end"
                        :class="index > 0 ? 'ml-5' : ''"
                    >
                        <!-- 그룹 이름 — 「누르면 가는 곳」이 있으면 링크 -->
                        <h2 v-if="section.title" :id="`${uid}-${section.key}`" class="m-0 self-center pr-1 text-xs font-normal text-muted-color">
                            <AppMenuGroupLabel v-if="section.group" :group="section.group" />
                            <template v-else>{{ localizedName(section.title, section.titleEn) }}</template>
                        </h2>
                        <ul class="m-0 flex list-none items-end gap-1.5 p-0">
                            <li v-for="entry in section.entries" :key="entry.key">
                                <AppMenuLink
                                    v-if="entry.kind === 'item'"
                                    :node="entry.row.node"
                                    :class="tabClass(entry.row.active)"
                                    :data-active="entry.row.active ? 'true' : undefined"
                                    :aria-current="entry.row.active ? 'page' : undefined"
                                >
                                    <i v-if="entry.row.color" class="inline-block size-2 shrink-0 rounded-full" :style="UtilBoard.dotStyle(entry.row.color)" aria-hidden="true" data-testid="menu-dot-color" />
                                    <span>{{ localizedName(entry.row.node.label, entry.row.node.nameEn) }}</span>
                                    <small v-if="entry.row.notice" class="shrink-0 text-xs font-normal text-muted-color">{{ t('menu.investmentNotice') }}</small>
                                </AppMenuLink>
                                <!-- 활성 표시(`data-active`)는 드롭다운 단추가 스스로 단다 -->
                                <AppTopMenuDropdown v-else :entry="entry" :button-class="tabClass(entry.active)" />
                            </li>
                        </ul>
                    </section>
                </template>
            </AppScrollRow>
        </div>
    </nav>
</template>

<script setup lang="ts">
import UtilBoard from '~/utils/util-board';
import type { MenuOverflow } from '~/utils/util-menu-position';

interface Props {
    /** 줄이 넘칠 때의 표시(운영자 설정 > 테마 기본, 셸이 넘긴다) — 얇은 스크롤 · 양끝 화살표 단추 */
    overflow?: MenuOverflow;
}
withDefaults(defineProps<Props>(), { overflow: 'SCROLLBAR' });

const { t } = useI18n();
const { bar, state, ownsState, activeId, loading, reload } = useMenuItems('bar');
const uid = useId();
// 메뉴 이름은 운영자가 만든 동적 이름이다 — `label`(한국어)과 선택 영문명 `nameEn` 을 지금 언어로 고른다
const { localizedName } = useLocalizedName();
/** 탭 줄(`AppScrollRow`)과 그 안의 스크롤 상자 — 활성 칸 가운데로 끌어오기가 쓴다 */
const { row, scroller } = useScrollRowRef();

/** 칸을 그릴 수 있는가 — 받는 중·빈 메뉴는 상태 자리만 */
const showEntries = computed(() => state.value !== 'loading' && state.value !== 'empty');

/** 탭 한 칸 — 16.5px · 패딩 12/10/13. 활성은 굵게(800) + 3px 밑줄(좌우 패딩만큼 들임, 위쪽만 둥글게) */
function tabClass(active: boolean): string {
    return [
        'relative flex shrink-0 items-center gap-1.5 px-2.5 pb-[13px] pt-3 text-[16.5px] leading-6 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary',
        active
            ? 'font-extrabold text-nav-fg after:absolute after:inset-x-2.5 after:bottom-0 after:h-[3px] after:rounded-t-[3px] after:bg-nav-fg'
            : 'font-medium text-nav-tab hover:text-nav-fg',
    ].join(' ');
}

// 활성 칸이 화면 밖이면 가운데로 끌어온다(`AppTopMenu` 와 같은 방식)
useCenterActive(scroller, [activeId, state], { onMount: true });
</script>
