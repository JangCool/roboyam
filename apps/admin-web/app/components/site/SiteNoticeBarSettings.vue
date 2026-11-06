<template>
    <!--
        설정 > 일반 ③ 공지 띠(`notice-bar`) — 공개 사이트 모든 화면의 메뉴 줄 아래 한 줄 공지(user-web `SiteNoticeBar`).
          켜기 · 내용(공지 게시판 최근 글 5·10개를 5초마다 넘김 / 직접 입력 문구 + 선택 링크) · 저장된 값으로 지금 띠에 보이는 줄 미리보기.
          출처가 아닌 쪽 칸도 그대로 저장한다(출처를 바꿔 볼 때 지운 듯 보이지 않게). 띠 내용이 바뀌면 방문자가 닫아 둔 띠가 다시 보인다(닫음 기록은 공지 묶음 표식별 — 서버 몫).
        다른 구획과 따로 읽고 저장한다. 권한: 조회 `site.read`, 변경 `site.manage` — 없으면 저장 줄이 사라지고 입력칸은 잠긴다.
    -->
    <AppGadget :title="t('noticeBar.title')" anchor="notice-bar" :changes="changeCount" :invalid="invalidCount">
        <div v-if="loading && !saved" class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
            <Skeleton height="2.25rem" width="14rem" />
            <Skeleton height="2.25rem" width="min(36rem, 100%)" />
        </div>

        <ErrorState v-else-if="!saved" :title="t('noticeBar.loadFailed')" :description="loadError?.message" retry :retrying="loading" @retry="load" />

        <form v-else class="flex flex-col gap-4" novalidate @submit.prevent="save">
            <p class="text-xs leading-relaxed text-muted-color">{{ t('noticeBar.intro') }}</p>

            <div class="flex items-center gap-3">
                <ToggleSwitch v-model="form.enabled" :input-id="enabledId" :disabled="!canManage || saving" :aria-describedby="`${enabledId}-help`" />
                <div>
                    <label :for="enabledId" class="text-sm">{{ t('noticeBar.enabledLabel') }}</label>
                    <p :id="`${enabledId}-help`" class="text-xs text-muted-color">{{ t('noticeBar.enabledHelp') }}</p>
                </div>
            </div>

            <div class="flex flex-col gap-1.5">
                <span :id="`${uid}-source`" class="text-sm">{{ t('noticeBar.sourceLabel') }}</span>
                <SelectButton
                    v-model="form.source"
                    :options="sourceOptions"
                    option-label="label"
                    option-value="value"
                    :allow-empty="false"
                    size="small"
                    :disabled="!canManage || saving"
                    :aria-labelledby="`${uid}-source`"
                    class="self-start"
                />
            </div>

            <template v-if="form.source === 'BOARD'">
                <Message v-if="!saved.noticeBoard" severity="warn" :closable="false" size="small">{{ t('noticeBar.boardMissing') }}</Message>
                <div class="flex flex-col gap-1.5">
                    <span :id="`${uid}-count`" class="text-sm">{{ t('noticeBar.countLabel') }}</span>
                    <SelectButton
                        v-model="form.boardCount"
                        :options="countOptions"
                        option-label="label"
                        option-value="value"
                        :allow-empty="false"
                        size="small"
                        :disabled="!canManage || saving"
                        :aria-labelledby="`${uid}-count`"
                        :aria-describedby="`${uid}-count-help`"
                        class="self-start"
                    />
                    <p v-if="saved.noticeBoard" :id="`${uid}-count-help`" class="text-xs text-muted-color">{{ t('noticeBar.boardHelp', { name: localizedName(saved.noticeBoard.name, saved.noticeBoard.nameEn) }) }}</p>
                </div>
            </template>

            <template v-else>
                <LabeledField :label="t('noticeBar.textLabel')" :hint="t('noticeBar.textHint', { max: UtilSiteNoticeBar.TEXT_MAX })" :error="textError">
                    <template #default="{ id, describedBy, invalid }">
                        <InputText v-model="customText" :id="id" name="customText" autocomplete="off" size="small" :readonly="!canManage || saving" :invalid="invalid" :aria-describedby="describedBy" aria-required="true" class="w-full max-w-xl" />
                    </template>
                </LabeledField>
                <LabeledField :label="t('noticeBar.linkLabel')" :help="t('noticeBar.linkHelp')" :error="linkError">
                    <template #default="{ id, describedBy, invalid }">
                        <InputText v-model="customLink" :id="id" name="customLink" autocomplete="off" inputmode="url" size="small" :readonly="!canManage || saving" :invalid="invalid" :aria-describedby="describedBy" class="w-full max-w-xl" />
                    </template>
                </LabeledField>
            </template>

            <!-- 저장된 값으로 지금 띠에 보이는 줄 -->
            <div class="flex flex-col gap-1.5 rounded-lg bg-(--p-content-hover-background) p-3">
                <span class="text-xs text-muted-color">{{ t('noticeBar.previewLabel') }}</span>
                <p v-if="!saved.enabled" class="text-xs">{{ t('noticeBar.previewOff') }}</p>
                <p v-else-if="!saved.preview.length" class="text-xs">{{ t('noticeBar.previewEmpty') }}</p>
                <ol v-else class="flex flex-col gap-1 text-xs">
                    <li v-for="(line, index) in saved.preview" :key="index" class="flex gap-2">
                        <span class="w-5 shrink-0 text-right tabular-nums text-muted-color">{{ index + 1 }}</span>
                        <span class="min-w-0 truncate">{{ line }}</span>
                    </li>
                </ol>
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
import type { SiteNoticeBarRequest, SiteNoticeBarSettingsResponse } from '~/composables/site/api/useSiteSettingsApi.types';
import UtilSiteNoticeBar from '~/utils/util-site-notice-bar';

const { t } = useI18n();
const api = useSiteSettingsApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const { localizedName } = useLocalizedName();
const { can } = useAdminPermission();
const canManage = computed(() => can('site.manage'));

const uid = useId();
const enabledId = `${uid}-enabled`;

const saved = ref<SiteNoticeBarSettingsResponse | null>(null);
const loading = ref(false);
const loadError = ref<ApiError | null>(null);
const saving = ref(false);
const form = reactive<SiteNoticeBarRequest>({ enabled: true, source: 'BOARD', boardCount: 5, customText: null, customLink: null });
/** 저장을 눌러 본 적이 있는가 — 그 전에는 "비어 있음" 문제를 빨갛게 보이지 않는다 */
const tried = ref(false);
const serverErrors = ref<{ customText: string; customLink: string }>({ customText: '', customLink: '' });
const formError = ref('');

/** 입력칸은 문자열로 묶고 폼에는 null 로 둔다 */
const customText = computed({
    get: () => form.customText ?? '',
    set: (value: string) => {
        form.customText = value;
        serverErrors.value.customText = '';
    },
});
const customLink = computed({
    get: () => form.customLink ?? '',
    set: (value: string) => {
        form.customLink = value;
        serverErrors.value.customLink = '';
    },
});

const sourceOptions = computed(() => [
    { label: t('noticeBar.sourceBoard'), value: 'BOARD' },
    { label: t('noticeBar.sourceCustom'), value: 'CUSTOM' },
]);
const countOptions = computed(() => UtilSiteNoticeBar.COUNTS.map((count) => ({ label: t('noticeBar.countOption', { count }), value: count })));

const problems = computed(() => UtilSiteNoticeBar.validate(form));
const textError = computed(() => {
    const problem = problems.value.customText;
    if (problem === 'REQUIRED') return tried.value ? t('noticeBar.textRequired') : '';
    if (problem === 'LENGTH') return t('noticeBar.textLength', { max: UtilSiteNoticeBar.TEXT_MAX });
    if (problem === 'CHARS') return t('noticeBar.textChars');
    return serverErrors.value.customText;
});
const linkError = computed(() => {
    const problem = problems.value.customLink;
    if (problem === 'LENGTH') return t('noticeBar.linkLength', { max: UtilSiteNoticeBar.LINK_MAX });
    if (problem === 'LINK') return t('noticeBar.linkShape');
    return serverErrors.value.customLink;
});
const invalidCount = computed(() => Number(Boolean(textError.value)) + Number(Boolean(linkError.value)));
const changeCount = computed(() => (saved.value ? UtilSiteNoticeBar.changes(saved.value, form) : 0));

function fill(res: SiteNoticeBarSettingsResponse): void {
    saved.value = res;
    Object.assign(form, { enabled: res.enabled, source: res.source, boardCount: res.boardCount, customText: res.customText, customLink: res.customLink });
    tried.value = false;
    serverErrors.value = { customText: '', customLink: '' };
    formError.value = '';
}

async function load(): Promise<void> {
    loading.value = true;
    loadError.value = null;
    try {
        fill(await api.noticeBar());
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
    tried.value = true;
    if (Object.keys(problems.value).length > 0) return;
    saving.value = true;
    formError.value = '';
    try {
        fill(await api.saveNoticeBar(UtilSiteNoticeBar.request(form)));
        toast.success(t('noticeBar.saved'));
    } catch (e) {
        const error = toApiError(e);
        serverErrors.value = { customText: error.field('customText') ?? '', customLink: error.field('customLink') ?? '' };
        // 칸에 붙지 않는 오류만 아래에 모은다 — 서버 문구를 그대로 보인다
        if (!serverErrors.value.customText && !serverErrors.value.customLink) formError.value = error.message;
    } finally {
        saving.value = false;
    }
}

void load();
</script>
