<template>
    <!--
        우측 구역 내비(앵커 메뉴) — 한 화면 안의 구획(`AppGadget` 의 `anchor`)으로 바로 가는 글자 목록. 구획은 스스로 등록하고(`useSectionNav`) 이 부품은 그리기만 한다. 셸(`layouts/default.vue`)이 한 번 놓는다.
        모양 둘: `list`(본문 오른쪽 열, 본문 영역이 충분히 넓을 때 셸 CSS 가 보인다) · `select`(좁을 때 본문 맨 위의 "구역 이동" 선택 상자).
        현재 구획은 왼쪽 선 + 연한 바탕 + 굵은 글자(색만으로 말하지 않는다), `aria-current="location"`. 항목은 진짜 링크(`href="#anchor"`)이고 누르면 스크롤 이동 + 제목 초점 + 테두리 강조(`useSectionNav.go`).
        항목 옆에 오류 N건(빨강 숫자) → 바뀜(노랑 점)이 붙는다 — 점·숫자만 보이고 글자는 스크린리더가 읽는 `sr-only` 가 말한다. 구획이 2개 미만이면 아무것도 그리지 않는다.
    -->
    <nav v-if="visible && variant === 'list'" :aria-label="t('section.navLabel')" class="section-nav-list" data-variant="list">
        <!-- 건너뛰기 — 평소엔 안 보이고 초점이 닿으면 목록 맨 위에 선다. 목록을 지나 첫 구획 제목으로 간다 -->
        <a
            :href="firstAnchor ? `#${firstAnchor}` : '#'"
            class="sr-only focus:not-sr-only focus:mb-1 focus:block focus:rounded focus:bg-surface-0 focus:px-2 focus:py-1 focus:text-xs focus:font-medium focus:text-primary-700 focus-visible:outline-2 focus-visible:outline-primary dark:focus:bg-surface-900 dark:focus:text-primary-300"
            @click.prevent="skip"
        >
            {{ t('section.skip') }}
        </a>
        <p class="m-0 px-2 pb-1 text-xs font-medium text-muted-color" aria-hidden="true">{{ t('section.navLabel') }}</p>
        <ul class="m-0 ml-1.5 list-none border-l border-(--line) p-0">
            <li v-for="item in items" :key="item.anchor">
                <a
                    :href="`#${item.anchor}`"
                    class="-ml-px flex items-start gap-1.5 border-l-2 px-2 py-1 text-[0.8125rem] leading-snug focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary"
                    :class="
                        item.anchor === active
                            ? 'border-primary bg-primary-50 font-semibold text-primary-700 dark:bg-primary-950 dark:text-primary-300'
                            : 'border-transparent text-surface-600 hover:bg-surface-100 hover:text-surface-900 dark:text-surface-300 dark:hover:bg-surface-800 dark:hover:text-surface-0'
                    "
                    :aria-current="item.anchor === active ? 'location' : undefined"
                    @click.prevent="go(item.anchor)"
                >
                    <span class="min-w-0 flex-1 [overflow-wrap:anywhere]">{{ item.label }}</span>
                    <Badge v-if="countOf(item.invalid) > 0" :value="countOf(item.invalid)" severity="danger" size="small" class="shrink-0 self-center" :title="UtilSectionNavMessages.invalid(t, countOf(item.invalid))" aria-hidden="true" />
                    <Badge v-if="countOf(item.changes) > 0" severity="warn" class="shrink-0 self-center" :title="UtilSectionNavMessages.changed(t, countOf(item.changes))" aria-hidden="true" />
                    <span v-if="statusOf(item)" class="sr-only">({{ statusOf(item) }})</span>
                </a>
            </li>
        </ul>
    </nav>

    <!-- 좁을 때의 "구역 이동" 선택 상자 — 다른 선택 상자와 같은 `Select` 부품이다(화살표·Enter 로 고른다). 항목 글자에 바뀜·오류가 덧붙는다(점·숫자 배지를 못 그리므로) -->
    <nav v-else-if="visible && variant === 'select'" class="section-nav-select flex items-center gap-2" :aria-label="t('section.navLabel')" data-variant="select">
        <label :for="selectId" class="shrink-0 text-xs font-medium text-muted-color">{{ t('section.selectLabel') }}</label>
        <Select :model-value="active ?? undefined" :options="selectOptions" option-label="text" option-value="anchor" :input-id="selectId" size="small" class="min-w-0 flex-1" @update:model-value="onSelect" @hide="onSelectHide" />
    </nav>
</template>

<script setup lang="ts">
import type { SectionNavEntry } from '~/utils/util-section-nav';
import UtilSectionNav from '~/utils/util-section-nav';
import UtilSectionNavMessages from '~/utils/util-section-nav-messages';

interface Props {
    variant?: 'list' | 'select';
}

withDefaults(defineProps<Props>(), { variant: 'list' });

const { t } = useI18n();
const { items, visible, active, go } = useSectionNav();
const selectId = useId();

const firstAnchor = computed(() => items.value[0]?.anchor ?? '');

/** 선택 상자 항목 — 구획 이름 + 바뀜·오류 글자 */
const selectOptions = computed(() => items.value.map((item) => ({ anchor: item.anchor, text: UtilSectionNavMessages.optionText(t, item.label, UtilSectionNav.count(item.changes), UtilSectionNav.count(item.invalid)) })));

const countOf = (value: number | undefined) => UtilSectionNav.count(value);
const statusOf = (item: SectionNavEntry) => UtilSectionNavMessages.statusText(t, countOf(item.changes), countOf(item.invalid));

/**
 * 선택 상자로 고른 구획 — **목록이 닫힌 뒤에** 이동한다(`onSelectHide`). PrimeVue Select 는 값을 고르면 자기 입력칸(화면 위쪽)으로 초점을 되돌리는데,
 * 그 초점이 스크롤 영역을 다시 위로 끌어 올려 방금 시작한 부드러운 스크롤을 취소한다 — 그래서 초점 복귀가 끝난 뒤에 이동한다.
 */
let pendingAnchor: string | null = null;

function onSelect(value: string | null | undefined): void {
    if (!value) return;
    pendingAnchor = value;
}

/** PrimeVue Select 가 값을 고른 뒤 자기 입력칸으로 초점을 되돌리는 타이머가 모두 지난 뒤로 잡는 시간(ms) */
const REFOCUS_SETTLE_MS = 60;

/** 선택 상자의 목록이 닫혔다 — 고른 직후라면 그 구획으로 이동하고 제목에 초점을 준다(다음 Tab 이 그 구획의 첫 입력칸으로 간다). 고르지 않고 닫은 경우(Esc·바깥 누르기)는 아무 일도 하지 않는다 */
function onSelectHide(): void {
    if (pendingAnchor === null) return;
    const anchor = pendingAnchor;
    pendingAnchor = null;
    setTimeout(() => go(anchor), REFOCUS_SETTLE_MS);
}

/** 건너뛰기 — 목록을 지나 첫 구획 제목으로 */
function skip(): void {
    if (firstAnchor.value) go(firstAnchor.value, { instant: true });
}
</script>
