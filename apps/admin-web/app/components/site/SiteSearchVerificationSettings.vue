<template>
    <!--
        설정 > 일반 ⑤ 검색 엔진 등록(`search-verification`) — 공개 사이트 첫 HTML 머리에 넣을 검색 엔진 소유 확인 코드 셋(네이버·구글·빙, 모두 선택).
          칸마다 메타 태그 `content` 값 하나. 사이트에서 복사한 태그 전체를 붙여 넣으면 이 칸 서비스의 태그일 때 `content` 만 뽑아 넣고, 다른 서비스 태그면 오류를 보인다(`UtilSiteSearchVerification`).
          빈 칸은 그 태그를 넣지 않는다는 뜻이다. 아래 상자는 저장된 값으로 지금 공개 사이트에 들어가는 태그다.
        서버 API(`useSiteSearchVerificationApi`)에 연결돼 있다. 공개 사이트 첫 HTML 머리에는 서버 `IndexHtmlRenderer` 가 값이 있는 것만 넣는다.
        다른 구획과 따로 읽고 저장한다. 권한: 조회 `site.read`, 변경 `site.manage` — 없으면 저장 줄이 사라지고 입력칸은 `readonly`.
    -->
    <AppGadget :title="t('searchVerification.title')" anchor="search-verification" :changes="changeCount" :invalid="invalidCount">
        <div v-if="loading && !saved" class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
            <Skeleton height="2.25rem" width="min(36rem, 100%)" />
            <Skeleton height="2.25rem" width="min(36rem, 100%)" />
            <Skeleton height="2.25rem" width="min(36rem, 100%)" />
        </div>

        <ErrorState v-else-if="!saved" :title="t('searchVerification.loadFailed')" :description="loadError?.message" retry :retrying="loading" @retry="load" />

        <form v-else class="flex flex-col gap-4" novalidate @submit.prevent="save">
            <p class="text-xs leading-relaxed text-muted-color">{{ t('searchVerification.intro') }}</p>

            <LabeledField
                v-for="engine in UtilSiteSearchVerification.ENGINES"
                :key="engine"
                :label="t(`searchVerification.${engine}Label`)"
                :hint="t('searchVerification.hint', { max: UtilSiteSearchVerification.MAX })"
                :help="t(`searchVerification.${engine}Help`)"
                :error="errors[engine]"
                class="max-w-xl"
            >
                <template #aside>
                    <a :href="UtilSiteSearchVerification.SITES[engine]" target="_blank" rel="noopener noreferrer" class="inline-flex shrink-0 items-center gap-1 text-xs font-medium text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary">
                        {{ t('searchVerification.openSite') }}<i class="pi pi-external-link text-[0.625rem]" aria-hidden="true" /><span class="sr-only">({{ t('searchVerification.newTab') }})</span>
                    </a>
                </template>
                <template #default="{ id, describedBy, invalid }">
                    <InputText
                        :model-value="form[engine]"
                        :id="id"
                        :name="`searchVerification-${engine}`"
                        autocomplete="off"
                        spellcheck="false"
                        size="small"
                        :placeholder="t('searchVerification.placeholder')"
                        :readonly="!canManage || saving"
                        :invalid="invalid"
                        :aria-describedby="describedBy"
                        class="w-full font-mono! text-xs!"
                        @update:model-value="(value) => onInput(engine, value ?? '')"
                    />
                </template>
            </LabeledField>

            <!-- 저장된 값으로 지금 공개 사이트 머리에 들어가는 태그 -->
            <div class="flex flex-col gap-1.5 rounded-lg bg-(--p-content-hover-background) p-3">
                <span class="text-xs text-muted-color">{{ t('searchVerification.previewLabel') }}</span>
                <p v-if="!savedTags.length" class="text-xs">{{ t('searchVerification.previewEmpty') }}</p>
                <ul v-else class="flex flex-col gap-1">
                    <li v-for="tag in savedTags" :key="tag" class="min-w-0 break-all font-mono text-xs">{{ tag }}</li>
                </ul>
            </div>

            <Message v-if="formError" severity="error" variant="simple" size="small">{{ formError }}</Message>

            <div v-if="canManage" class="flex flex-wrap items-center justify-end gap-2">
                <Button type="button" :label="t('common.reset')" severity="secondary" variant="outlined" :disabled="saving || changeCount === 0" @click="reset" />
                <Button type="submit" :label="t('common.save')" icon="pi pi-check" :loading="saving" :disabled="changeCount === 0" />
            </div>
        </form>
    </AppGadget>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { SiteSearchVerificationResponse } from '~/composables/site/api/useSiteSearchVerificationApi.types';
import UtilSiteSearchVerification, { type SearchEngine, type SearchVerificationForm, type SearchVerificationIssue } from '~/utils/util-site-search-verification';

const { t } = useI18n();
const api = useSiteSearchVerificationApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const { can } = useAdminPermission();
const canManage = computed(() => can('site.manage'));

/** 서버가 준 값(저장된 값). `null` = 아직 못 받았다 */
const saved = ref<SiteSearchVerificationResponse | null>(null);
const loading = ref(false);
const loadError = ref<ApiError | null>(null);
const saving = ref(false);
const form = reactive<SearchVerificationForm>({ naver: '', google: '', bing: '' });
/** 서버가 거절한 칸 문구 — 그 칸을 고치면 지운다 */
const serverErrors = reactive<Record<SearchEngine, string>>({ naver: '', google: '', bing: '' });
/** 칸에 붙지 않는 저장 오류 */
const formError = ref('');

function issueText(issue: SearchVerificationIssue): string {
    if (issue.code === 'OTHER_TAG') return t('searchVerification.problemOtherTag', { service: t(`searchVerification.${issue.other}Name`) });
    if (issue.code === 'LENGTH') return t('searchVerification.problemLength', { max: UtilSiteSearchVerification.MAX });
    if (issue.code === 'CHARS') return t('searchVerification.problemChars');
    return t('searchVerification.problemTag');
}

const issues = computed(() => {
    const out: Partial<Record<SearchEngine, SearchVerificationIssue>> = {};
    for (const engine of UtilSiteSearchVerification.ENGINES) {
        const issue = UtilSiteSearchVerification.issue(engine, form[engine]);
        if (issue) out[engine] = issue;
    }
    return out;
});
const errors = computed<Record<SearchEngine, string>>(() => {
    const pick = (engine: SearchEngine): string => {
        const issue = issues.value[engine];
        return issue ? issueText(issue) : serverErrors[engine];
    };
    return { naver: pick('naver'), google: pick('google'), bing: pick('bing') };
});
const invalidCount = computed(() => UtilSiteSearchVerification.ENGINES.filter((e) => Boolean(errors.value[e])).length);
const changeCount = computed(() => (saved.value ? UtilSiteSearchVerification.changes(saved.value, form) : 0));

/** 저장된 값으로 공개 사이트 머리에 들어가는 태그 줄 */
const savedTags = computed(() => {
    const value = saved.value;
    if (!value) return [];
    return UtilSiteSearchVerification.ENGINES.flatMap((e) => {
        const content = value[e];
        return content ? [`<meta name="${UtilSiteSearchVerification.META_NAMES[e]}" content="${content}" />`] : [];
    });
});

/**
 * 입력 — 이 칸 서비스의 태그 전체를 붙여 넣으면 `content` 만 남긴다.
 * 뽑은 값이 지금 값과 같으면 입력칸에 붙여 넣은 글자가 그대로 남으므로, 받은 글자를 먼저 두고 다음 틱에 뽑은 값으로 바꾼다.
 */
function onInput(engine: SearchEngine, raw: string): void {
    serverErrors[engine] = '';
    const value = UtilSiteSearchVerification.normalize(engine, raw);
    form[engine] = raw;
    if (value !== raw) void nextTick(() => (form[engine] = value));
}

function fill(res: SiteSearchVerificationResponse): void {
    saved.value = res;
    Object.assign(form, UtilSiteSearchVerification.form(res));
    Object.assign(serverErrors, { naver: '', google: '', bing: '' });
    formError.value = '';
}

async function load(): Promise<void> {
    loading.value = true;
    loadError.value = null;
    try {
        fill(await api.get());
    } catch (e) {
        loadError.value = toApiError(e);
    } finally {
        loading.value = false;
    }
}

function reset(): void {
    if (saved.value) fill(saved.value);
}

async function save(): Promise<void> {
    if (saving.value || !canManage.value || changeCount.value === 0) return;
    if (Object.keys(issues.value).length > 0) return;
    saving.value = true;
    formError.value = '';
    try {
        fill(await api.save(UtilSiteSearchVerification.request(form)));
        toast.success(t('searchVerification.saved'));
    } catch (e) {
        const error = toApiError(e);
        for (const engine of UtilSiteSearchVerification.ENGINES) serverErrors[engine] = error.field(engine) ?? '';
        // 칸에 붙지 않는 오류만 아래에 모은다 — 서버 문구를 그대로 보인다
        if (UtilSiteSearchVerification.ENGINES.every((e) => !serverErrors[e])) formError.value = error.message;
    } finally {
        saving.value = false;
    }
}

void load();
</script>
