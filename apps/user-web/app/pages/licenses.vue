<template>
    <div>
        <!--
            오픈소스 라이선스(`/licenses`) — 기본 레이아웃, 비회원도 본다. 계약 `docs/features/oss-licenses-contract.md`(사용자 확정 2026-11-10). 푸터 「약관·정책」 맨 아래 링크.
            모양: 약관 화면과 같은 흰 카드(`gadget-card` data-headless) + `PageTitleHead`(설명 = 관리자 안내 문구, 없으면 기본 문구 — `{사이트}` 는 사이트 이름 + 맞춘 조사) + 굵은 밑줄.
            자료: 자동 수집 목록(정적 파일 `/oss-licenses.json` — 빌드 때 모은다) + 안내 문구·직접 추가 항목(`GET /api/site/oss-licenses`)을 이름순 한 목록으로 합친다(`UtilOssLicense.rows`).
              정적 파일을 못 받으면 오류 화면(다시 시도), 서버 쪽만 못 받으면 기본 문구로 자동 목록만 보인다.
            거르기 칩(전체 · 공개 화면 · 관리자 화면 · 서버 · 글꼴·아이콘 — 직접 추가 항목은 운영자가 고른 묶음) + 이름 찾기 + 라이선스 종류별 개수 한 줄.
            줄을 누르면 펼친다(`aria-expanded`) — 쓰는 곳·라이선스 · 저작권 고지 · NOTICE · 라이선스 전문(고정폭, 줄바꿈 그대로, 긴 글은 접힌 높이 + [전문 펼치기]) · 원본 저장소(새 탭).
            수백 줄이라도 가볍게: 펼친 줄만 전문을 그리고(v-if), 줄마다 `content-visibility: auto` 로 화면 밖 줄의 그리기를 미룬다.
        -->
        <div class="gadget-card" data-headless data-cache-area="oss.notice" data-testid="oss-licenses">
            <PageTitleHead :title="t('ossLicense.title')" :description="intro" divider />

            <div v-if="status === 'loading'" class="mt-4" role="status" :aria-label="t('common.loading')" data-testid="oss-loading">
                <div class="h-8 w-2/3 animate-pulse rounded-full bg-surface-200 dark:bg-surface-800" />
                <div v-for="n in 6" :key="n" class="mt-3 h-10 animate-pulse rounded-lg bg-surface-200 dark:bg-surface-800" />
            </div>

            <ErrorState v-else-if="status === 'error'" :title="t('ossLicense.loadError')" :description="errorMessage" retry data-testid="oss-error" @retry="load" />

            <template v-else>
                <div class="mt-4 flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
                    <EmoticonChipGroup v-model="filter" :options="chipOptions" :label="t('ossLicense.filterLabel')" testid="oss-filter" />
                    <IconField class="w-full md:w-60">
                        <InputIcon class="pi pi-search" />
                        <InputText v-model="query" type="search" size="small" :placeholder="t('ossLicense.searchPlaceholder')" :aria-label="t('ossLicense.searchLabel')" fluid data-testid="oss-search" />
                    </IconField>
                </div>

                <p v-if="distribution.items.length" class="m-0 mt-3 text-xs leading-relaxed text-muted-color" data-testid="oss-distribution">
                    <span class="font-semibold text-color">{{ t('ossLicense.distributionLabel') }}</span>
                    <template v-for="item in distribution.items" :key="item.license">
                        <span aria-hidden="true"> · </span><span class="tabular-nums">{{ t('ossLicense.distributionItem', { license: item.license, n: item.count }) }}</span>
                    </template>
                    <template v-if="distribution.rest">
                        <span aria-hidden="true"> · </span><span class="tabular-nums">{{ t('ossLicense.distributionRest', { n: distribution.rest }) }}</span>
                    </template>
                </p>
                <p class="sr-only" role="status" aria-live="polite">{{ t('ossLicense.resultCount', { n: shown.length }) }}</p>

                <EmptyState v-if="!shown.length" icon="pi pi-search" :title="t('ossLicense.empty')" :description="t('ossLicense.emptyHint')" data-testid="oss-empty" />

                <ul v-else class="m-0 mt-3 list-none border-t border-surface p-0" data-testid="oss-list">
                    <li v-for="(row, index) in shown" :key="row.key" class="oss-row border-b border-surface" data-testid="oss-row">
                        <h2 class="m-0 text-sm font-normal">
                            <button
                                :id="`${baseId}-h${index}`"
                                type="button"
                                class="flex min-h-11 w-full cursor-pointer items-center gap-2 bg-transparent py-2 text-left text-color hover:text-primary focus-visible:outline-2 focus-visible:outline-primary"
                                :aria-expanded="open.has(row.key)"
                                :aria-controls="`${baseId}-p${index}`"
                                @click="toggle('open', row.key)"
                            >
                                <i class="pi text-[0.625rem] text-muted-color" :class="open.has(row.key) ? 'pi-chevron-down' : 'pi-chevron-right'" aria-hidden="true" />
                                <span class="min-w-0 flex-1">
                                    <span class="break-all font-medium [overflow-wrap:anywhere]">{{ row.name }}</span>
                                    <span v-if="row.version" class="ml-1.5 text-xs tabular-nums text-muted-color">{{ row.version }}</span>
                                    <span class="ml-2 hidden text-xs text-muted-color sm:inline">{{ usedInLabel(row) }}</span>
                                </span>
                                <span
                                    class="inline-flex max-w-[45%] shrink-0 items-center justify-center rounded-full border border-surface px-2 py-1 text-[0.6875rem] leading-none text-muted-color"
                                    :title="row.license"
                                >
                                    <span class="relative top-[-0.09em] truncate">{{ row.license }}</span>
                                </span>
                            </button>
                        </h2>

                        <div v-if="open.has(row.key)" :id="`${baseId}-p${index}`" role="region" :aria-labelledby="`${baseId}-h${index}`" class="pb-4 pl-5 text-sm" data-testid="oss-detail">
                            <dl class="m-0 grid grid-cols-[auto_minmax(0,1fr)] gap-x-4 gap-y-1 text-xs">
                                <dt class="text-muted-color">{{ t('ossLicense.usedInLabel') }}</dt>
                                <dd class="m-0">{{ usedInLabel(row) }}</dd>
                                <dt class="text-muted-color">{{ t('ossLicense.licenseText') }}</dt>
                                <dd class="m-0 break-words">{{ row.license }}</dd>
                            </dl>

                            <section v-if="row.copyright" class="mt-3">
                                <h3 class="m-0 text-xs font-semibold">{{ t('ossLicense.copyright') }}</h3>
                                <p class="m-0 mt-1 whitespace-pre-line break-words text-[0.8125rem] leading-relaxed">{{ row.copyright }}</p>
                            </section>

                            <section v-if="row.notice" class="mt-3">
                                <h3 class="m-0 text-xs font-semibold">{{ t('ossLicense.notice') }}</h3>
                                <pre class="oss-pre bg-surface-100 dark:bg-surface-800/60" :class="UtilOssLicense.isLong(row.notice) && !full.has(`n:${row.key}`) ? 'oss-pre--folded' : ''">{{ row.notice }}</pre>
                                <button v-if="UtilOssLicense.isLong(row.notice)" type="button" class="oss-fold" :aria-expanded="full.has(`n:${row.key}`)" @click="toggle('full', `n:${row.key}`)">
                                    {{ full.has(`n:${row.key}`) ? t('ossLicense.collapseText') : t('ossLicense.expandText') }}
                                </button>
                            </section>

                            <section class="mt-3">
                                <h3 class="m-0 text-xs font-semibold">{{ t('ossLicense.licenseText') }}</h3>
                                <p v-if="row.textSource === 'template'" class="m-0 mt-1 text-xs text-muted-color">{{ t('ossLicense.textTemplate') }}</p>
                                <template v-if="row.text">
                                    <pre class="oss-pre bg-surface-100 dark:bg-surface-800/60" :class="UtilOssLicense.isLong(row.text) && !full.has(row.key) ? 'oss-pre--folded' : ''">{{ row.text }}</pre>
                                    <button v-if="UtilOssLicense.isLong(row.text)" type="button" class="oss-fold" :aria-expanded="full.has(row.key)" @click="toggle('full', row.key)">
                                        {{ full.has(row.key) ? t('ossLicense.collapseText') : t('ossLicense.expandText') }}
                                    </button>
                                </template>
                                <p v-else class="m-0 mt-1 text-xs text-muted-color">{{ t('ossLicense.textNone') }}</p>
                            </section>

                            <p v-if="UtilOssLicense.safeUrl(row.repository) || UtilOssLicense.safeUrl(row.licenseUrl)" class="m-0 mt-3 flex flex-wrap gap-x-4 gap-y-1 text-xs">
                                <a v-if="UtilOssLicense.safeUrl(row.repository)" :href="UtilOssLicense.safeUrl(row.repository)!" target="_blank" rel="noopener" class="inline-flex items-center gap-1 text-primary hover:underline">
                                    {{ t('ossLicense.repository') }}<i class="pi pi-external-link text-[0.5625rem]" aria-hidden="true" /><span class="sr-only">({{ t('ossLicense.newTab') }})</span>
                                </a>
                                <a v-if="UtilOssLicense.safeUrl(row.licenseUrl)" :href="UtilOssLicense.safeUrl(row.licenseUrl)!" target="_blank" rel="noopener" class="inline-flex items-center gap-1 text-primary hover:underline">
                                    {{ t('ossLicense.licenseLink') }}<i class="pi pi-external-link text-[0.5625rem]" aria-hidden="true" /><span class="sr-only">({{ t('ossLicense.newTab') }})</span>
                                </a>
                            </p>
                        </div>
                    </li>
                </ul>

                <p v-if="file" class="m-0 mt-4 text-xs text-muted-color" data-testid="oss-collected-at">{{ t('ossLicense.collectedAt', { date: time.serviceDate(file.collectedAt) }) }}</p>
            </template>
        </div>
    </div>
</template>

<script setup lang="ts">
import type { EmoticonChipOption } from '~/components/emoticon/EmoticonChipGroup.types';
import type { OssCollectedFile, OssNoticePublic } from '~/composables/osslicense/api/useOssLicenseApi.types';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilFooterNotice from '~/utils/util-footer-notice';
import UtilOssLicense, { type OssFilter, type OssLicenseRow } from '~/utils/util-oss-license';

const { t } = useI18n();
useSeoMeta({ title: () => t('ossLicense.title') });

const api = useOssLicenseApi();
const time = useDisplayTime();
const { siteLabel } = useSiteName();
const baseId = useId();

const file = ref<OssCollectedFile | null>(null);
const notice = ref<OssNoticePublic | null>(null);
const status = ref<'loading' | 'error' | 'ready'>('loading');
const errorMessage = ref('');

/** 정적 파일 + 서버 — 정적 파일을 못 받으면 오류 화면, 서버 쪽만 못 받으면 기본 문구로 자동 목록만 */
async function load(): Promise<void> {
    status.value = 'loading';
    errorMessage.value = '';
    const [collected, manual] = await Promise.allSettled([api.collected(), api.notice()]);
    notice.value = manual.status === 'fulfilled' ? manual.value : null;
    if (collected.status === 'fulfilled') {
        file.value = collected.value;
        status.value = 'ready';
    } else {
        file.value = null;
        errorMessage.value = UtilBoardMessages.of(t, collected.reason);
        status.value = 'error';
    }
}
onMounted(() => void load());

/** 제목 아래 설명 — 관리자 안내 문구(없으면 기본 문구)의 `{사이트}` 를 사이트 이름 + 맞춘 조사로 */
const intro = computed(() => UtilFooterNotice.render(notice.value?.intro ?? t('ossLicense.defaultIntro', { site: '{사이트}' }), siteLabel.value));

const rows = computed(() => UtilOssLicense.rows(file.value, notice.value?.entries ?? []));
const counts = computed(() => UtilOssLicense.counts(rows.value));
const filter = ref<OssFilter>('all');
const query = ref('');
const shown = computed(() => UtilOssLicense.filter(rows.value, filter.value, query.value));
const distribution = computed(() => UtilOssLicense.distribution(shown.value));
const chipOptions = computed<EmoticonChipOption<OssFilter>[]>(() =>
    // 항목이 없는 묶음 칩은 숨긴다(서버 라이브러리는 기본으로 싣지 않아 「서버」는 직접 추가한 항목이 있을 때만 — 사용자 2026-11-10 「진짜 필요한 고지만」)
    UtilOssLicense.FILTERS.filter((value) => value === 'all' || counts.value[value] > 0).map((value) => ({ value, label: t('ossLicense.chip', { label: t(`ossLicense.filter.${value}`), n: counts.value[value] }) })),
);

/** 펼친 줄 · 전문을 끝까지 펼친 줄(NOTICE 는 `n:` 앞머리) */
const open = ref<Set<string>>(new Set());
const full = ref<Set<string>>(new Set());
function toggle(which: 'open' | 'full', key: string): void {
    const target = which === 'open' ? open : full;
    const next = new Set(target.value);
    if (next.has(key)) next.delete(key);
    else next.add(key);
    target.value = next;
}

/** 쓰는 곳 글자 — 공개·관리자·서버(+ 글꼴·아이콘, 직접 추가) */
function usedInLabel(row: OssLicenseRow): string {
    const parts = row.usedIn.map((u) => t(`ossLicense.usedIn.${u}`));
    if (row.fontIcon) parts.push(t('ossLicense.fontIcon'));
    if (row.manual) parts.push(t('ossLicense.manual'));
    return parts.join(' · ') || '—';
}
</script>

<style scoped>
/* 화면 밖 줄은 그리기를 미룬다(수백 줄) — 접힌 줄 높이로 자리를 잡는다 */
.oss-row {
    content-visibility: auto;
    contain-intrinsic-size: auto 2.75rem;
}
.oss-pre {
    margin: 0.25rem 0 0;
    padding: 0.75rem;
    border-radius: 0.5rem;
    font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
    font-size: 0.75rem;
    line-height: 1.6;
    white-space: pre-wrap;
    overflow-wrap: anywhere;
}
/* 접힌 전문 — 정해진 높이 + 아래로 흐려짐 */
.oss-pre--folded {
    max-height: 18rem;
    overflow: hidden;
    mask-image: linear-gradient(to bottom, #000 70%, transparent);
}
.oss-fold {
    margin-top: 0.25rem;
    min-height: 2.25rem;
    padding: 0;
    border: 0;
    background: none;
    color: var(--p-primary-color);
    font-size: 0.75rem;
    cursor: pointer;
}
.oss-fold:hover {
    text-decoration: underline;
}
.oss-fold:focus-visible {
    outline: 2px solid var(--p-primary-color);
    outline-offset: 2px;
}
</style>
