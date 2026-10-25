<template>
    <!--
        공개 약관 본문 화면(`/terms`·`/privacy` — `docs/features/terms-contract.md` 「변경 안내 모양」 ②③·「빈 상태」). 두 페이지가 같은 모양이라 부품 하나로 그린다(불러오기는 페이지가 `useTermsDocument` 로 한다).
        맨 위 안내 상자: 시행 예정 버전이 있으면 「YYYY-MM-DD부터 바뀐 이용약관이 시행돼요 [바뀐 약관 보기]」 + 변경 요지(그 버전을 보고 있을 때는 접는다).
        본문: 주소에 `?version=N` 이 있으면 그 버전(시행 예정·지난 표시와 [지금 약관 보기]), 없으면 지금 약관. 게시본이 없으면 「아직 게시된 이용약관이 없어요」.
        아래 「이전 버전」: 지금 보는 것을 뺀 게시본(버전·시행일·상태) — 누르면 `?version=N`.
        ⚠ 본문은 서버가 정화한 HTML(`p br strong em u s a ul ol li blockquote h2 h3 hr`)만 `v-html` 로 그린다 — 다른 글자(제목·변경 요지·날짜)는 글자로 따로 그린다.
    -->
    <div class="mx-auto w-full max-w-3xl">
        <h1 class="m-0 text-xl font-bold">{{ title }}</h1>

        <div v-if="status === 'loading'" class="mt-4" role="status" :aria-label="t('common.loading')" data-testid="terms-loading">
            <div class="h-4 w-1/3 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
            <div class="mt-4 h-64 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
        </div>

        <ErrorState v-else-if="status === 'error'" :title="t('terms.loadError')" :description="errorMessage" retry data-testid="terms-error" @retry="emit('retry')" />

        <template v-else-if="doc">
            <!-- ② 시행 예정 안내 상자 -->
            <section
                v-if="doc.upcoming && shown?.version !== doc.upcoming.version"
                class="mt-4 rounded-xl border border-primary-200 bg-primary-50 p-4 text-sm dark:border-primary-900 dark:bg-primary-950/40"
                :aria-label="t('terms.upcomingLabel')"
                data-testid="terms-upcoming"
            >
                <p class="m-0 font-semibold">{{ t(`terms.upcomingBox.${kind}`, { date: time.serviceDate(doc.upcoming.effectiveDate) }) }}</p>
                <p v-if="doc.upcoming.changeNote" class="mb-0 mt-1 whitespace-pre-line break-words leading-relaxed text-muted-color">{{ doc.upcoming.changeNote }}</p>
                <NuxtLink :to="UtilTerms.versionTo(kind, doc.upcoming.version)" class="mt-2 inline-flex min-h-9 items-center text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary" data-testid="terms-upcoming-link">
                    {{ t('terms.viewUpcoming') }}
                </NuxtLink>
            </section>

            <div v-if="versionLoading" class="mt-4" role="status" :aria-label="t('common.loading')">
                <div class="h-64 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
            </div>

            <EmptyState v-else-if="versionMissing" icon="pi pi-file" :title="t('terms.versionMissing')" data-testid="terms-version-missing">
                <NuxtLink :to="UtilTerms.versionTo(kind, null)" class="text-sm text-primary hover:underline">{{ t('terms.viewCurrent') }}</NuxtLink>
            </EmptyState>

            <ErrorState v-else-if="versionError" :title="t('terms.loadError')" :description="versionError" retry @retry="emit('retry')" />

            <article v-else-if="shown" class="mt-4" :aria-label="title" data-testid="terms-body">
                <p class="m-0 text-xs text-muted-color" data-testid="terms-meta">
                    {{ t('terms.versionLine', { version: shown.version, date: time.serviceDate(shown.effectiveDate) }) }}
                </p>
                <!-- 지금 약관이 아닌 버전 — 시행 예정·지난 -->
                <p v-if="shown.status !== 'CURRENT'" class="mb-0 mt-2 rounded-lg bg-surface-100 px-3 py-2 text-sm dark:bg-surface-800/60" data-testid="terms-status-note">
                    {{ shown.status === 'UPCOMING' ? t('terms.viewingUpcoming', { date: time.serviceDate(shown.effectiveDate) }) : t('terms.viewingPast') }}
                    <NuxtLink v-if="doc.current" :to="UtilTerms.versionTo(kind, null)" class="ml-1 text-primary hover:underline">{{ t('terms.viewCurrent') }}</NuxtLink>
                </p>
                <p v-if="shown.changeNote" class="mb-0 mt-2 whitespace-pre-line break-words text-sm leading-relaxed text-muted-color">
                    <b class="font-medium text-color">{{ t('terms.changeNote') }}</b> {{ shown.changeNote }}
                </p>
                <!-- eslint-disable-next-line vue/no-v-html -->
                <div class="rich-text mt-4 border-t border-surface pt-4 text-[0.9375rem]" v-html="shown.body" />
            </article>

            <EmptyState v-else :title="t(`terms.emptyTitle.${kind}`)" icon="pi pi-file" data-testid="terms-empty" />

            <!-- ③ 이전 버전 -->
            <section v-if="others.length" class="mt-8" :aria-labelledby="historyId" data-testid="terms-history">
                <h2 :id="historyId" class="m-0 text-base font-semibold">{{ t('terms.historyTitle') }}</h2>
                <ul class="m-0 mt-2 list-none border-t border-surface p-0">
                    <li v-for="item in others" :key="item.version" class="border-b border-surface">
                        <NuxtLink :to="UtilTerms.versionTo(kind, item.status === 'CURRENT' ? null : item.version)" class="flex min-h-11 items-center gap-2 py-2 text-sm hover:text-primary">
                            <span class="tabular-nums">{{ t('terms.versionLine', { version: item.version, date: time.serviceDate(item.effectiveDate) }) }}</span>
                            <span class="text-xs text-muted-color">· {{ t(`terms.status.${item.status}`) }}</span>
                        </NuxtLink>
                    </li>
                </ul>
            </section>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { TermsDocument, TermsKind } from '~/composables/terms/api/useTermsApi.types';
import type { TermsShown } from '~/composables/terms/useTermsDocument.types';
import UtilTerms from '~/utils/util-terms';

interface Props {
    kind: TermsKind;
    status: 'loading' | 'error' | 'ready';
    doc?: TermsDocument | null;
    /** 그릴 본문 — 게시본이 없거나 아직 받는 중이면 null */
    shown?: TermsShown | null;
    errorMessage?: string;
    versionLoading?: boolean;
    /** 주소의 버전이 없다(404) */
    versionMissing?: boolean;
    versionError?: string;
}

const props = withDefaults(defineProps<Props>(), {
    doc: null,
    shown: null,
    errorMessage: '',
    versionLoading: false,
    versionMissing: false,
    versionError: '',
});

const emit = defineEmits<{ retry: [] }>();

const { t } = useI18n();
const time = useDisplayTime();
const historyId = useId();

const title = computed(() => (props.kind === 'TERMS' ? t('common.terms') : t('common.privacy')));

/** 이전 버전 목록 — 지금 보는 버전은 뺀다 */
const others = computed(() => (props.doc?.history ?? []).filter((item) => item.version !== props.shown?.version));
</script>
