<template>
    <!--
        설정 > 일반 ④ 사이트 소개(`site-about`) — 공개 사이트 `/about` 페이지 본문과 푸터 「사이트 소개」 링크(소개가 있을 때만 보인다).
          편집기는 약관 편집(`/settings/terms`)과 같은 `RichEditor` 문서 수준(`DOCUMENT` — 굵게·기울임·밑줄·취소선·링크·목록·인용·제목·구분선). 서버가 HTML 을 정화해 저장하고 정화한 본문을 돌려준다.
          내용 없이 저장하면 `html: null`(소개 없음 → 공개 푸터 링크가 숨는다). [공개 페이지 보기]는 서버가 준 공개 주소(`publicUrl`, 없으면 `useUserWebOrigin`)를 알 때만 새 탭으로 연다.
        다른 구획과 따로 읽고 저장한다. 권한: 조회 `site.read`, 변경 `site.manage` — 없으면 저장 줄이 사라지고 편집기는 잠긴다. 서버 거절 문구는 `fields.html` 그대로 보인다.
    -->
    <AppGadget :title="t('siteAbout.title')" anchor="site-about" :changes="dirty ? 1 : 0" :invalid="htmlError ? 1 : 0">
        <template v-if="publicUrl" #actions>
            <a :href="publicUrl" target="_blank" rel="noopener noreferrer" class="inline-flex items-center gap-1 text-xs font-medium text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary" data-testid="site-about-public">
                {{ t('siteAbout.viewPublic') }}<i class="pi pi-external-link text-[0.625rem]" aria-hidden="true" /><span class="sr-only">({{ t('siteAbout.newTab') }})</span>
            </a>
        </template>

        <div v-if="loading && !saved" class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
            <Skeleton height="2.25rem" width="min(36rem, 100%)" />
            <Skeleton height="12rem" />
        </div>

        <ErrorState v-else-if="!saved" :title="t('siteAbout.loadFailed')" :description="loadError?.message" retry :retrying="loading" @retry="load" />

        <form v-else class="flex flex-col gap-3" novalidate @submit.prevent="save">
            <p class="text-xs leading-relaxed text-muted-color">{{ t('siteAbout.intro') }}</p>

            <div class="flex flex-col gap-1.5">
                <div class="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
                    <span :id="labelId" class="text-sm">{{ t('siteAbout.bodyLabel') }}</span>
                    <span class="text-xs text-muted-color" data-testid="site-about-updated">{{ updatedLine }}</span>
                </div>
                <RichEditor
                    :key="`site-about-editor-${editorKey}`"
                    v-model="editorHtml"
                    level="DOCUMENT"
                    :disabled="!canManage || saving"
                    :invalid="Boolean(htmlError)"
                    :labelledby="labelId"
                    :describedby="messageId"
                    :placeholder="t('siteAbout.bodyPlaceholder')"
                    @ready="onEditorReady"
                />
                <div :id="messageId" aria-live="polite" class="text-xs leading-relaxed">
                    <p v-if="htmlError" role="alert" class="m-0 text-red-600 dark:text-red-400">{{ htmlError }}</p>
                    <p v-else class="m-0 text-muted-color">{{ t('siteAbout.bodyHelp') }}</p>
                </div>
            </div>

            <Message v-if="formError" severity="error" variant="simple" size="small">{{ formError }}</Message>

            <div v-if="canManage" class="flex flex-wrap items-center justify-end gap-2">
                <Button type="button" :label="t('common.reset')" severity="secondary" variant="outlined" :disabled="saving || !dirty" @click="reset" />
                <Button type="submit" :label="t('common.save')" icon="pi pi-check" :loading="saving" :disabled="!dirty" />
            </div>
        </form>
    </AppGadget>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { SiteAboutAdminResponse } from '~/composables/site/api/useSiteAboutApi.types';
import UtilRichText from '#layers/rich-editor/utils/util-rich-text';

const { t } = useI18n();
const api = useSiteAboutApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const time = useDisplayTime();
const { can } = useAdminPermission();
const { origin: userWebOrigin } = useUserWebOrigin();
const canManage = computed(() => can('site.manage'));

const labelId = useId();
const messageId = useId();

/** 서버가 준 값(저장된 값). `null` = 아직 못 받았다 */
const saved = ref<SiteAboutAdminResponse | null>(null);

/** 공개 페이지 주소 — 서버가 준 사이트 공개 주소를 먼저, 없으면 화면이 아는 공개 사이트 주소. 둘 다 모르면 빈 글자(링크를 숨긴다) */
const publicUrl = computed(() => {
    const base = (saved.value?.publicUrl ?? userWebOrigin.value ?? '').replace(/\/+$/, '');
    return base ? `${base}/about` : '';
});
const loading = ref(false);
const loadError = ref<ApiError | null>(null);
const saving = ref(false);
/** 서버가 거절한 본문 문구(`fields.html`) — 본문을 고치면 지운다 */
const htmlError = ref('');
/** 칸에 붙지 않는 저장 오류 */
const formError = ref('');

/** 편집기 값과 「고쳤는가」의 기준 — 기준은 편집기가 정규화한 값으로 바꿔 둔다(`onEditorReady`, 약관 편집과 같다) */
const editorHtml = ref('');
const baseline = ref('');
const editorKey = ref(0);
const dirty = computed(() => editorHtml.value !== baseline.value);

watch(editorHtml, () => (htmlError.value = ''));

const updatedLine = computed(() => {
    const at = saved.value?.updatedAt;
    if (!saved.value?.html) return t('siteAbout.none');
    return at ? t('siteAbout.updatedAt', { time: time.value.full(at) }) : '';
});

/** 서버 값으로 편집기를 새로 만든다 — 정화된 본문이 편집기 정규화와 달라도 「고침」으로 보이지 않게 기준을 `ready` 에서 다시 잡는다 */
function fill(res: SiteAboutAdminResponse): void {
    saved.value = res;
    editorHtml.value = res.html ?? '';
    baseline.value = editorHtml.value;
    editorKey.value += 1;
    htmlError.value = '';
    formError.value = '';
}

function onEditorReady(html: string): void {
    if (editorHtml.value === baseline.value) {
        baseline.value = html;
        editorHtml.value = html;
    }
}

function reset(): void {
    if (saved.value) fill(saved.value);
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

async function save(): Promise<void> {
    if (saving.value || !canManage.value || !dirty.value) return;
    saving.value = true;
    htmlError.value = '';
    formError.value = '';
    try {
        // 내용이 없으면(빈 문단만) 소개 없음 — 공개 푸터 링크가 숨는다
        const html = UtilRichText.hasContent(editorHtml.value) ? editorHtml.value : null;
        const res = await api.save({ html });
        fill(res);
        toast.success(t(res.html ? 'siteAbout.saved' : 'siteAbout.cleared'));
    } catch (e) {
        const error = toApiError(e);
        const field = error.field('html');
        // 칸 오류는 편집기 아래, 칸에 붙지 않는 오류만 아래에 모은다 — 서버 문구를 그대로 보인다
        if (field) htmlError.value = field;
        else formError.value = error.message;
    } finally {
        saving.value = false;
    }
}

void load();
</script>
