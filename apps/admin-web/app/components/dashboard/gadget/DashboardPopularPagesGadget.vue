<template>
    <!--
        인기 페이지 — 오늘 많이 본 페이지 TOP N(순위 · 화면 종류 꼬리표 · 제목 · 조회수). 제목은 서버 이름(게시판 이름 …), 묶음 주소(`/posts/{id}` …)는 화면 이름(「글 보기」 — `UtilVisitPathMessages`), 둘 다 없으면 경로(툴팁은 늘 경로).
        서버가 1분마다 몰아 센 오늘 페이지별 집계를 읽는다 — 3단계부터 「방문자·페이지뷰」(통계 수집 `visits`)에 딸린다. 수집이 꺼져 있으면 틀이 「수집이 꺼져 있어요」.
        바로 가기 = 「방문자 통계 › 페이지」의 오늘. 값은 묶음 응답의 `POPULAR_PAGES`(`useDashboardGadgetApi`).
    -->
    <DashboardGadgetFrame
        :title="title"
        :at="state.at.value"
        :loading="state.loading.value"
        :error="state.error.value"
        :ready="!!v"
        :forbidden="state.forbidden.value"
        :unavailable="state.unavailable.value"
        :disabled="state.disabled.value"
        :collect="state.collect"
        :more="moreLink"
        :empty="!v?.items.length"
        empty-icon="pi pi-desktop"
        :empty-title="t('dashboard.popularPagesNone')"
        @refresh="state.reload"
    >
        <ol v-if="v" class="m-0 flex list-none flex-col divide-y divide-(--line) p-0">
            <li v-for="(p, i) in v.items" :key="p.path" class="flex items-center gap-2 py-2 text-[13px]">
                <span class="w-5 shrink-0 text-center font-semibold tabular-nums" :class="i < 3 ? 'text-primary' : 'text-muted-color'">{{ i + 1 }}</span>
                <span class="inline-flex shrink-0 items-center justify-center rounded bg-surface-100 px-1.5 py-1 text-[11px] leading-none dark:bg-surface-800">{{ kindName(p.kind) }}</span>
                <span class="min-w-0 flex-1 truncate" :class="UtilVisitPathMessages.named(p) ? '' : 'font-mono text-[12px] text-muted-color'" :title="p.path">{{ UtilVisitPathMessages.label(t, localizedName, p) }}</span>
                <span class="inline-flex shrink-0 items-center gap-1 text-[11px] text-muted-color tabular-nums">
                    <i class="pi pi-eye text-[10px]" aria-hidden="true" /><span class="sr-only">{{ t('dashboard.pageViewsShort') }}</span>{{ p.views.toLocaleString(locale) }}
                </span>
            </li>
        </ol>
    </DashboardGadgetFrame>
</template>

<script setup lang="ts">
import type { DashboardPageKind } from '~/composables/dashboard/api/useDashboardGadgetApi.types';
import type { DashboardGadgetItem } from '~/composables/dashboard/api/useDashboardLayoutApi.types';
import UtilVisitAnalytics from '~/utils/util-visit-analytics';
import UtilVisitPathMessages from '~/utils/util-visit-path-messages';

interface Props {
    gadget: DashboardGadgetItem;
    title: string;
}

const props = withDefaults(defineProps<Props>(), {});

const { t, te, locale } = useI18n();
const { localizedName } = useLocalizedName();
const state = useDashboardKindData('POPULAR_PAGES', () => props.gadget);
const v = computed(() => state.data.value);
/** 바로 가기 — 방문 분석 화면의 오늘(날짜는 그릴 때 계산) */
const moreLink = computed(() => UtilVisitAnalytics.link('TODAY', UtilVisitAnalytics.PAGES.pages));

/** 화면 종류 꼬리표 — 모르는 종류는 「기타」 */
function kindName(kind: DashboardPageKind | string): string {
    const key = `dashboard.pageKind.${kind}`;
    return te(key) ? t(key) : t('dashboard.pageKind.OTHER');
}
</script>
