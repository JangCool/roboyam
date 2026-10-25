<template>
    <!--
        약관 변경 안내 띠(`docs/features/terms-contract.md` 「변경 안내 모양」 ①) — 기본 레이아웃의 헤더 아래에 한 번 놓는다. 재동의 없이 알림만 한다.
        「이용약관이 바뀌어요 · 시행 YYYY-MM-DD [보기] [닫기]」 — 약관 요약(`useTermsSummary`)의 종류별 `notice` 가 있을 때만(보일지 판정은 서버: 시행 예정이 있거나, 지금 약관이 2번 이상이고 시행된 지 7일 안).
        닫으면 이 브라우저에서 그 종류·버전은 다시 안 보인다(localStorage `roboyam:terms-notice:<종류>` = 닫은 버전). 저장소를 못 쓰면(사생활 보호 창 등) 이번 화면에서만 닫힌다.
        그 종류의 약관 화면(`/terms`·`/privacy`)에서는 화면 맨 위 안내 상자가 같은 말을 하므로 띠에서 뺀다.
    -->
    <section
        v-if="items.length"
        class="border-b border-primary-200 bg-primary-50 text-primary-950 dark:border-primary-900 dark:bg-primary-950/40 dark:text-primary-100"
        :aria-label="t('terms.noticeLabel')"
        data-testid="terms-change-notice"
    >
        <div class="mx-auto flex w-full max-w-[var(--content-max)] flex-col px-4">
            <div v-for="item in items" :key="item.kind" class="flex items-center gap-x-3 py-1" :data-kind="item.kind">
                <p class="m-0 min-w-0 flex-1 truncate text-sm">{{ item.text }}</p>
                <NuxtLink :to="item.to" class="inline-flex min-h-9 shrink-0 items-center text-xs underline focus-visible:outline-2 focus-visible:outline-primary" data-testid="terms-notice-view">
                    {{ t('terms.noticeView') }}
                </NuxtLink>
                <button
                    type="button"
                    class="-mr-2 inline-flex size-9 shrink-0 items-center justify-center rounded-full hover:bg-primary-100 focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-primary-900/60"
                    :aria-label="t('terms.noticeClose', { name: item.name })"
                    data-testid="terms-notice-close"
                    @click="dismiss(item.kind, item.version)"
                >
                    <i class="pi pi-times text-xs" aria-hidden="true" />
                </button>
            </div>
        </div>
    </section>
</template>

<script setup lang="ts">
import type { TermsKind } from '~/composables/terms/api/useTermsApi.types';
import UtilTerms from '~/utils/util-terms';

const { t } = useI18n();
const route = useRoute();
const time = useDisplayTime();
const { summary, ensureLoaded } = useTermsSummary();
void ensureLoaded();

/** 닫은 버전(종류별) — 마운트 뒤 저장소에서 읽는다. 읽기 전에는 띠를 그리지 않는다(닫은 띠가 잠깐 비치지 않게) */
const dismissed = ref<Partial<Record<TermsKind, number>>>({});
const storageRead = ref(false);

function readDismissed(kind: TermsKind): number | undefined {
    try {
        const raw = window.localStorage.getItem(UtilTerms.noticeStorageKey(kind));
        const value = raw === null ? NaN : Number(raw);
        return Number.isInteger(value) ? value : undefined;
    } catch {
        return undefined;
    }
}

function dismiss(kind: TermsKind, version: number): void {
    dismissed.value = { ...dismissed.value, [kind]: version };
    try {
        window.localStorage.setItem(UtilTerms.noticeStorageKey(kind), String(version));
    } catch {
        // 저장소를 못 쓴다 — 이번 화면에서만 닫는다
    }
}

onMounted(() => {
    dismissed.value = { TERMS: readDismissed('TERMS'), PRIVACY: readDismissed('PRIVACY') };
    storageRead.value = true;
});

const items = computed(() => {
    if (!storageRead.value || !summary.value) return [];
    return summary.value.kinds.flatMap((entry) => {
        const notice = entry.notice;
        if (!notice || dismissed.value[entry.kind] === notice.version || route.path === UtilTerms.pathOf(entry.kind)) return [];
        const date = time.value.serviceDate(notice.effectiveDate);
        return [
            {
                kind: entry.kind,
                version: notice.version,
                name: entry.kind === 'TERMS' ? t('common.terms') : t('common.privacy'),
                text: notice.upcoming ? t(`terms.noticeUpcoming.${entry.kind}`, { date }) : t(`terms.noticeRecent.${entry.kind}`, { date }),
                to: UtilTerms.noticeTo(entry.kind, notice),
            },
        ];
    });
});
</script>
