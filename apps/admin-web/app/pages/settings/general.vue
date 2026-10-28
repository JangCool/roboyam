<template>
    <!--
        일반 — 설정 메뉴의 첫 화면(`/settings/general`). hamonica3 의 설정 > 일반(제품 기본 정보 + 표시 기본값)과 같은 짜임이다: 구획 둘, **저장 단추는 구획마다 따로**다(이름을 고치려던 사람이 표시 기본값까지 같이 저장하지 않게).
          ① 사이트 기본 정보(`site-basic`) — 사이트 이름(필수)·한 줄 소개(선택)·로고·사이트 아이콘·링크 미리보기 이미지. 이름·소개는 서버 `SiteSettingEntity` 에 실제 있는 칸이다. 공개 주소는 뺐다(소셜 로그인 콜백·메일 링크의 기준이라 변경 정책이 먼저다).
            로고는 hamonica3 「제품 기본 정보」의 로고 줄과 같다: 이미지 업로드(PNG·JPG·GIF·WEBP 2MB) 또는 SVG 소스 입력(SVG 는 스크립트를 품을 수 있는 문서라 파일로 받지 않고 소스로 받는다) · 제거. **[저장]이 없고 올리는 즉시 적용**되며(이름·소개의 [저장]과 따로),
            관리자 화면 상단 로고 자리(`SiteBrand` — `useAdminAuth().siteLogoUrl`)와 공개 사이트 로고 자리가 같이 쓴다. 그림은 `<img src>` 하나로만 그린다(`v-html` 금지). 서버 API(`/api/admin/site/**`·`/api/admin/files`)에 연결돼 있고, 올린 그림 주소는 `/media/…` 상대 주소다.
            로고 줄 바로 아래에 이미지 줄이 둘 더 있고(`SiteImageField`), 둘 다 로고처럼 **[저장] 없이 올리면 즉시 적용**되며 소제목 옆 "올리면 바로 적용" 표시가 그 차이를 말한다. 아이콘·링크 이미지는 이름·소개와 따로 읽는다(불러오지 못해도 이름·소개 입력은 쓸 수 있고 그 줄에만 오류·다시 시도가 보인다).
              · 사이트 아이콘(탭 아이콘·홈 화면·알림 아이콘): 정사각 PNG 1장, 2MB 이하, 한 변 512~4000px. 파일 머리(PNG 서명 + IHDR 가로·세로)를 브라우저가 먼저 읽어 서버와 같은 순서로 거른다(빈 파일 → 형식 → 용량 → 읽기 → 너무 큼 → 정사각 아님 → 너무 작음, 지금 크기 포함 — `UtilSiteImage`). SVG 는 파일이 아니라 소스 입력(로고의 SVG 소스 입력과 같은 방식, 검사는 `UtilSiteSetting.logoSvg` 재사용, 정사각 여부는 서버 몫).
                올려 둔 게 있을 때만 [기본으로 되돌리기](확인창). 올려 둔 게 없으면 "기본 아이콘" 상태 문구. 미리보기 타일은 버튼(누르면 파일 선택).
              · 링크 미리보기 이미지(og:image — 카톡·SNS 에 주소를 붙일 때 보이는 그림): PNG 1장, 2MB 이하, 가로 1200~4000px(세로도 4000 이하), 비율 1200:630 ±2% — 안 맞으면 **자르지 않고 거절**(비율 검사가 크기 부족 검사보다 먼저). 썸네일은 1200:630 비율. 나머지는 아이콘 줄과 같고 SVG 입구는 없다.
              두 줄 모두 서버 API 에 연결돼 있다(계약은 `useSiteSettingsApi.ts` 머리말) — 서버 거절 문구는 가공하지 않고 `fields.file`/`fields.svgSource` 그대로 보인다. 권한 `site.manage` 가 없으면 단추가 숨고 읽기 전용이다.
          ② 표시 기본값(`display-defaults`) — 언어·시간대·시간 형식·날짜 형식(공유 레이어의 `DisplayValueForm`). 이 값이 곧 내 정보 > 개인 설정 탭의 "기본 설정"(내 표시 형식을 쓰지 않을 때 따르는 값)이다.
          (공개 사이트 테마·메뉴 설정·홈 배치는 설정 메뉴 「테마·레이아웃」 묶음의 따로 화면이다 — `/settings/theme`·`/settings/menu-layout`·`/settings/home-layout`, 사용자 결정 2026-10-28.)
        구획은 서로 따로 읽고 저장한다 — 한쪽이 실패해도 다른 쪽은 보인다. 구획 주소(`anchor`)는 우측 구역 내비가 쓴다.
        권한: 조회 `site.read`(메뉴), 변경 `site.manage` — 없으면 저장 줄이 사라지고 입력칸은 `readonly`(서버 값이 그대로 보인다). 감추는 것은 편의이고 막는 것은 서버다.
        표시 기본값을 저장하면 내 표시 값(`useMyDisplay`)을 다시 받아 지금 화면에도 바로 적용한다(내 표시 형식을 쓰고 있으면 그대로다). 내 정보 > 개인 설정의 "기본 설정"(`inherited`)이 이 저장 값을 따른다(서버가 요청마다 사이트 표시 기본값을 읽어 준다). 사이트 이름을 저장하면 로고 글자·탭 제목이 바로 새 이름을 따른다.
    -->
    <div class="flex flex-col gap-4">
        <PageTitle :title="t('site.title')" :subtitle="t('site.subtitle')" />

        <Message v-if="!canManage" severity="info" :closable="false" size="small">{{ t('site.readOnly') }}</Message>

        <!-- ① 사이트 기본 정보 -->
        <AppGadget :title="t('site.basicTitle')" anchor="site-basic" :changes="basicChanges" :invalid="basicInvalid">
            <div v-if="basicLoading && !basicSaved" class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
                <Skeleton height="2.25rem" width="min(24rem, 100%)" />
                <Skeleton height="2.25rem" width="min(36rem, 100%)" />
            </div>

            <ErrorState v-else-if="!basicSaved" :title="t('site.basicLoadFailed')" :description="basicLoadError?.message" retry :retrying="basicLoading" @retry="loadBasic" />

            <form v-else class="flex flex-col gap-4" novalidate @submit.prevent="saveBasic">
                <LabeledField :label="t('site.nameLabel')" :hint="t('site.nameHint', { max: UtilSiteSetting.NAME_MAX })" :help="t('site.nameHelp')" :error="nameError">
                    <template #default="{ id, describedBy, invalid }">
                        <InputText v-model="nameInput" :id="id" name="siteName" autocomplete="off" size="small" :readonly="!canManage || basicSaving" :invalid="invalid" :aria-describedby="describedBy" aria-required="true" class="w-full max-w-sm" />
                    </template>
                </LabeledField>

                <LabeledField :label="t('site.taglineLabel')" :hint="t('site.taglineHint', { max: UtilSiteSetting.TAGLINE_MAX })" :help="t('site.taglineHelp')" :error="taglineError">
                    <template #default="{ id, describedBy, invalid }">
                        <InputText v-model="taglineInput" :id="id" name="tagline" autocomplete="off" size="small" :readonly="!canManage || basicSaving" :invalid="invalid" :aria-describedby="describedBy" class="w-full max-w-xl" />
                    </template>
                </LabeledField>

                <!-- 로고 — 올리는 즉시 적용된다(아래 [저장]은 이름·소개만). 관리자 화면 상단과 공개 사이트 로고 자리에 같이 보인다 -->
                <LabeledField :label="t('site.logoLabel')" :error="logoError">
                    <template #default="{ id, describedBy }">
                        <div class="flex items-start gap-3">
                            <!-- 미리보기를 누르면 바로 그림을 고른다 — 누를 수 있다는 건 눌러 보기 전엔 모르니 단추도 함께 둔다 -->
                            <button
                                :id="id"
                                v-tooltip.top="canManage ? t('site.logoPreviewTooltip') : undefined"
                                type="button"
                                class="flex size-14 shrink-0 items-center justify-center overflow-hidden rounded-xl border border-(--line) bg-surface-100 transition hover:border-primary focus-visible:outline-2 focus-visible:outline-primary disabled:cursor-default disabled:hover:border-(--line) dark:bg-surface-800"
                                :disabled="!canManage || logoBusy"
                                :aria-label="logoUrl ? t('site.logoChange') : t('site.logoAdd')"
                                :aria-describedby="describedBy"
                                @click="pickLogoFile"
                            >
                                <img v-if="logoUrl && !logoBroken" :src="logoUrl" alt="" class="size-full object-contain" @error="logoBroken = true" />
                                <i v-else class="pi pi-image text-lg text-muted-color" aria-hidden="true" />
                            </button>

                            <div class="flex min-w-0 flex-col gap-1.5">
                                <div v-if="canManage" class="flex flex-wrap gap-2">
                                    <Button type="button" :label="t('site.logoUpload')" icon="pi pi-upload" size="small" variant="outlined" :loading="logoBusy" @click="pickLogoFile" />
                                    <Button type="button" :label="t('site.logoFromSvg')" icon="pi pi-code" size="small" variant="outlined" :disabled="logoBusy" :aria-expanded="showSvgInput" @click="openSvgInput" />
                                    <Button v-if="logoUrl" type="button" :label="t('site.logoRemove')" icon="pi pi-trash" size="small" severity="danger" variant="text" :disabled="logoBusy" @click="removeLogo" />
                                </div>
                                <p class="text-xs leading-relaxed text-muted-color">
                                    {{ t('site.logoHint') }}
                                    <!-- SVG 를 파일로 안 받는 이유를 적어 둔다 — 안 적으면 "왜 svg 를 못 올리지"로 읽힌다 -->
                                    <strong class="font-semibold text-color">{{ t('site.logoSvgWhy') }}</strong>
                                    {{ t('site.logoSvgWhyTail') }}
                                    <code class="text-[0.6875rem]">xmlns</code> {{ t('site.logoSvgXmlns') }}
                                </p>
                                <input ref="logoInput" type="file" :accept="UtilSiteSetting.LOGO_ACCEPT" class="hidden" tabindex="-1" :aria-label="t('site.logoUpload')" @change="onLogoFileChange" />
                            </div>
                        </div>
                    </template>
                </LabeledField>

                <div v-if="showSvgInput && canManage" class="flex flex-col gap-1.5 rounded-xl bg-surface-100 p-3 dark:bg-surface-800">
                    <label :for="svgInputId" class="text-xs font-medium">{{ t('site.logoSvgSource') }}</label>
                    <Textarea :id="svgInputId" v-model="svgDraft" rows="5" fluid :invalid="Boolean(svgError)" :readonly="logoBusy" :aria-describedby="svgError ? `${svgInputId}-msg` : undefined" placeholder='<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">...</svg>' class="font-mono! text-xs!" @input="svgError = ''" />
                    <p v-if="svgError" :id="`${svgInputId}-msg`" role="alert" class="text-xs text-red-600 dark:text-red-400">{{ svgError }}</p>
                    <div class="flex gap-2">
                        <Button type="button" :label="t('common.apply')" size="small" :disabled="!svgDraft.trim()" :loading="logoBusy" @click="applySvgLogo" />
                        <Button type="button" :label="t('common.cancel')" size="small" severity="secondary" variant="text" :disabled="logoBusy" @click="closeSvgInput" />
                    </div>
                </div>

                <!-- 사이트 아이콘 — 로고 줄 바로 아래. 올리는 즉시 적용된다(아래 [저장]은 이름·소개만) -->
                <SiteImageField
                    :title="t('site.iconLabel')"
                    :help="t('site.iconHelp')"
                    :hint="UtilSiteImageMessages.iconHint(t)"
                    :image-url="iconUrl"
                    :custom="Boolean(iconView?.custom)"
                    :status-label="iconView?.custom ? t('site.iconStatusCustom') : t('site.iconStatusDefault')"
                    :tile-label="iconView?.custom ? t('site.iconTileChange') : t('site.iconTileAdd')"
                    :reset-label="t('site.iconReset')"
                    :editable="canManage"
                    :busy="iconBusy"
                    :loading="iconLoading && !iconView"
                    :load-error="iconView ? '' : (iconLoadError?.message ?? '')"
                    :load-failed-title="t('site.iconLoadFailed')"
                    :error="iconError"
                    svg
                    :svg-label="t('site.iconFromSvg')"
                    :svg-open="showIconSvg"
                    @pick="onIconPicked"
                    @open-svg="openIconSvg"
                    @reset="resetIcon"
                    @retry="loadIcon"
                >
                    <div v-if="showIconSvg && canManage" class="flex flex-col gap-1.5 rounded-xl bg-surface-100 p-3 dark:bg-surface-800">
                        <label :for="iconSvgInputId" class="text-xs font-medium">{{ t('site.iconSvgSource') }}</label>
                        <Textarea :id="iconSvgInputId" v-model="iconSvgDraft" rows="5" fluid :invalid="Boolean(iconSvgError)" :readonly="iconBusy" :aria-describedby="`${iconSvgInputId}-note${iconSvgError ? ` ${iconSvgInputId}-msg` : ''}`" placeholder='<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">...</svg>' class="font-mono! text-xs!" @input="iconSvgError = ''" />
                        <p :id="`${iconSvgInputId}-note`" class="text-xs leading-relaxed text-muted-color">{{ t('site.iconSvgNote') }}</p>
                        <p v-if="iconSvgError" :id="`${iconSvgInputId}-msg`" role="alert" class="text-xs text-red-600 dark:text-red-400">{{ iconSvgError }}</p>
                        <div class="flex gap-2">
                            <Button type="button" :label="t('common.apply')" size="small" :disabled="!iconSvgDraft.trim()" :loading="iconBusy" @click="applyIconSvg" />
                            <Button type="button" :label="t('common.cancel')" size="small" severity="secondary" variant="text" :disabled="iconBusy" @click="closeIconSvg" />
                        </div>
                    </div>
                </SiteImageField>

                <!-- 링크 미리보기 이미지(og:image) — 아이콘과 같은 동작. SVG 입구는 없다 -->
                <SiteImageField
                    :title="t('site.ogLabel')"
                    :help="t('site.ogHelp')"
                    :hint="UtilSiteImageMessages.ogHint(t)"
                    shape="wide"
                    :image-url="ogUrl"
                    :custom="Boolean(ogView?.configured)"
                    :status-label="ogView?.configured ? t('site.ogStatusCustom') : t('site.ogStatusDefault')"
                    :tile-label="ogView?.configured ? t('site.ogTileChange') : t('site.ogTileAdd')"
                    :reset-label="t('site.ogReset')"
                    :editable="canManage"
                    :busy="ogBusy"
                    :loading="ogLoading && !ogView"
                    :load-error="ogView ? '' : (ogLoadError?.message ?? '')"
                    :load-failed-title="t('site.ogLoadFailed')"
                    :error="ogError"
                    @pick="onOgPicked"
                    @reset="resetOg"
                    @retry="loadOg"
                />

                <Message v-if="basicFormError" severity="error" variant="simple" size="small">{{ basicFormError }}</Message>

                <div v-if="canManage" class="flex flex-wrap items-center justify-end gap-2">
                    <Button type="button" :label="t('common.reset')" severity="secondary" variant="outlined" :disabled="basicSaving || basicChanges === 0" @click="resetBasic" />
                    <Button type="submit" :label="t('common.save')" icon="pi pi-check" :loading="basicSaving" :disabled="basicChanges === 0" />
                </div>
            </form>
        </AppGadget>

        <!-- ② 표시 기본값 -->
        <AppGadget :title="t('site.displayTitle')" anchor="display-defaults" :changes="displayChanged ? 1 : 0" :invalid="displayInvalid">
            <div v-if="displayLoading && !displaySaved" class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
                <Skeleton height="2.25rem" width="14rem" />
                <Skeleton height="2.25rem" width="min(36rem, 100%)" />
                <Skeleton height="4rem" width="min(36rem, 100%)" />
            </div>

            <ErrorState v-else-if="!displaySaved || !displayValues" :title="t('site.displayLoadFailed')" :description="displayLoadError?.message" retry :retrying="displayLoading" @retry="loadDisplay" />

            <div v-else class="flex flex-col gap-4">
                <Message severity="info" :closable="false" size="small">{{ t('site.displayScope') }}</Message>

                <DisplayValueForm v-model="displayValues" class="display-form-sm" :errors="displayErrors" :disabled="!canManage || displaySaving" />

                <Message v-if="displayFormError" severity="error" variant="simple" size="small">{{ displayFormError }}</Message>

                <div v-if="canManage" class="flex flex-wrap items-center justify-end gap-2">
                    <Button type="button" :label="t('common.reset')" severity="secondary" variant="outlined" :disabled="displaySaving || !displayChanged" @click="resetDisplay" />
                    <Button type="button" :label="t('common.save')" icon="pi pi-check" :loading="displaySaving" :disabled="!displayChanged" @click="saveDisplay" />
                </div>
            </div>
        </AppGadget>

    </div>
</template>

<script setup lang="ts">
import UtilDisplayForm, { type DisplayFieldErrors } from '#layers/display-time/utils/util-display-form';
import UtilDisplayValues from '#layers/display-time/utils/util-display-values';
import type { ApiError } from '~/composables/common/useApiClient';
import type {
    DisplayValues,
    SiteBasicResponse,
    SiteDisplayDefaultsResponse,
    SiteIconResponse,
    SiteOgImageResponse,
} from '~/composables/site/api/useSiteSettingsApi.types';
import UtilSiteImage from '~/utils/util-site-image';
import UtilSiteImageMessages from '~/utils/util-site-image-messages';
import UtilSiteSetting from '~/utils/util-site-setting';
import UtilSiteSettingMessages from '~/utils/util-site-setting-messages';

const { t } = useI18n();
useSeoMeta({ title: () => t('site.seoTitle') });

const api = useSiteSettingsApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const { can } = useAdminPermission();
const { setSiteName, setSiteLogoUrl } = useAdminAuth();
const { confirm } = useConfirmDialog();
const { load: reloadMyDisplay } = useMyDisplay();

/** 이 화면에서 하는 일에 필요한 권한(정의: util-permission-catalog.ts) — 숨기는 것은 편의이고 막는 것은 서버다 */
const canManage = computed(() => can('site.manage'));

/* ── ① 사이트 기본 정보 ─────────────────────────── */

/** 서버가 준 값(저장된 값). `null` = 아직 못 받았다 */
const basicSaved = ref<SiteBasicResponse | null>(null);
const basicLoading = ref(false);
const basicLoadError = ref<ApiError | null>(null);
const basicSaving = ref(false);
const nameInput = ref('');
const taglineInput = ref('');
/** 저장을 눌러 본 적이 있는가 — 그 전에는 "비어 있음" 문제를 빨갛게 보이지 않는다 */
const basicTried = ref(false);
/** 서버가 거절한 칸 문구 — 그 칸을 고치면 지운다 */
const serverNameError = ref('');
const serverTaglineError = ref('');
/** 칸에 붙지 않는 저장 오류 */
const basicFormError = ref('');

const problems = computed(() => UtilSiteSetting.validate(nameInput.value, taglineInput.value));
const nameError = computed(() => {
    const problem = problems.value.siteName;
    if (problem && (problem !== 'REQUIRED' || basicTried.value)) return UtilSiteSettingMessages.siteNameProblem(t, problem);
    return serverNameError.value;
});
const taglineError = computed(() => {
    const problem = problems.value.tagline;
    return problem ? UtilSiteSettingMessages.taglineProblem(t, problem) : serverTaglineError.value;
});
const basicInvalid = computed(() => Number(Boolean(nameError.value)) + Number(Boolean(taglineError.value)));
const basicChanges = computed(() => (basicSaved.value ? UtilSiteSetting.changes(basicSaved.value, nameInput.value, taglineInput.value) : 0));

watch(nameInput, () => (serverNameError.value = ''));
watch(taglineInput, () => (serverTaglineError.value = ''));

function fillBasic(res: SiteBasicResponse): void {
    basicSaved.value = res;
    nameInput.value = res.siteName;
    taglineInput.value = res.tagline ?? '';
    logoError.value = '';
    basicTried.value = false;
    serverNameError.value = '';
    serverTaglineError.value = '';
    basicFormError.value = '';
}

function resetBasic(): void {
    if (basicSaved.value) fillBasic(basicSaved.value);
}

async function loadBasic(): Promise<void> {
    basicLoading.value = true;
    basicLoadError.value = null;
    try {
        fillBasic(await api.basic());
    } catch (e) {
        basicLoadError.value = toApiError(e);
    } finally {
        basicLoading.value = false;
    }
}

async function saveBasic(): Promise<void> {
    if (basicSaving.value || !canManage.value || basicChanges.value === 0) return;
    basicTried.value = true;
    if (Object.keys(problems.value).length > 0) return;
    basicSaving.value = true;
    basicFormError.value = '';
    try {
        const res = await api.saveBasic({ siteName: nameInput.value.trim(), tagline: taglineInput.value.trim() || null });
        fillBasic(res);
        setSiteName(res.siteName); // 로고 글자·탭 제목도 새 이름으로
        toast.success(t('site.basicSaved'));
    } catch (e) {
        const error = toApiError(e);
        serverNameError.value = error.field('siteName') ?? '';
        serverTaglineError.value = error.field('tagline') ?? '';
        // 칸에 붙지 않는 오류만 폼 위쪽에 모은다 — 서버 문구를 그대로 보인다
        if (!serverNameError.value && !serverTaglineError.value) basicFormError.value = error.message;
    } finally {
        basicSaving.value = false;
    }
}

/* ── ① 로고 — 저장 단추 없이 올리는 즉시 적용 ────────── */

const logoInput = useTemplateRef<HTMLInputElement>('logoInput');
const svgInputId = useId();
const logoBusy = ref(false);
const logoError = ref('');
const showSvgInput = ref(false);
const svgDraft = ref('');
const svgError = ref('');
/** 그림이 안 떠도(깨진 주소) 단추는 남긴다 — 다시 올릴 수 있어야 한다 */
const logoBroken = ref(false);

const logoUrl = computed(() => basicSaved.value?.logoUrl ?? null);
watch(logoUrl, () => (logoBroken.value = false));

/** 바뀐 로고를 이 화면과 상단 로고 자리에 바로 먹인다. 입력 중인 이름·소개는 건드리지 않는다 */
function applyLogo(url: string | null): void {
    if (basicSaved.value) basicSaved.value = { ...basicSaved.value, logoUrl: url };
    setSiteLogoUrl(url);
}

function pickLogoFile(): void {
    if (!canManage.value || logoBusy.value) return;
    logoInput.value?.click();
}

function openSvgInput(): void {
    svgDraft.value = '';
    svgError.value = '';
    showSvgInput.value = !showSvgInput.value;
}

function closeSvgInput(): void {
    showSvgInput.value = false;
    svgError.value = '';
}

/** 그림을 골랐다 — 바이트를 올려 `fileId` 를 받고, 그 파일을 로고로 삼는다(두 걸음) */
async function onLogoFileChange(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    // 고르기를 취소해도 change 가 오는 브라우저가 있다 — 그때 files 는 비어 있다
    if (!file || logoBusy.value) return;
    logoError.value = '';

    const problem = UtilSiteSetting.logoFile(file);
    if (problem) {
        logoError.value = UtilSiteSettingMessages.logoFileProblem(t, problem);
        input.value = '';
        return;
    }

    logoBusy.value = true;
    try {
        const { fileId } = await api.uploadLogoFile(file);
        const res = await api.setLogoFile({ fileId });
        applyLogo(res.logoUrl);
        closeSvgInput();
        toast.success(t('site.logoChanged'));
    } catch (e) {
        const error = toApiError(e);
        logoError.value = error.field('file') ?? error.field('fileId') ?? error.message;
    } finally {
        logoBusy.value = false;
        // 같은 파일을 다시 고를 수 있게 비운다 — 안 비우면 change 가 안 온다
        input.value = '';
    }
}

async function applySvgLogo(): Promise<void> {
    if (logoBusy.value) return;
    const problem = UtilSiteSetting.logoSvg(svgDraft.value);
    if (problem) {
        svgError.value = UtilSiteSettingMessages.logoSvgProblem(t, problem);
        return;
    }
    logoBusy.value = true;
    svgError.value = '';
    logoError.value = '';
    try {
        const res = await api.setLogoSvg({ svgSource: svgDraft.value.trim() });
        applyLogo(res.logoUrl);
        closeSvgInput();
        toast.success(t('site.logoSvgApplied'));
    } catch (e) {
        const error = toApiError(e);
        svgError.value = error.field('svgSource') ?? error.message;
    } finally {
        logoBusy.value = false;
    }
}

async function removeLogo(): Promise<void> {
    if (logoBusy.value) return;
    const ok = await confirm({ title: t('site.logoRemoveTitle'), message: t('site.logoRemoveMessage'), confirmLabel: t('site.logoRemove'), tone: 'danger' });
    if (!ok) return;
    logoBusy.value = true;
    logoError.value = '';
    try {
        const res = await api.clearLogo();
        applyLogo(res.logoUrl);
        toast.success(t('site.logoRemoved'));
    } catch (e) {
        logoError.value = toApiError(e).message;
    } finally {
        logoBusy.value = false;
    }
}

/* ── ① 사이트 아이콘 — 로고처럼 저장 단추 없이 올리는 즉시 적용 ─── */

/** 서버가 준 아이콘 상태. `null` = 아직 못 받았다. 이름·소개 구획과 따로 읽는다 */
const iconView = ref<SiteIconResponse | null>(null);
const iconLoading = ref(false);
const iconLoadError = ref<ApiError | null>(null);
const iconBusy = ref(false);
/** 이 줄 아래 거절 문구 — 화면이 먼저 거른 것도, 서버가 거절한 것(그대로)도 */
const iconError = ref('');
const showIconSvg = ref(false);
const iconSvgDraft = ref('');
const iconSvgError = ref('');
const iconSvgInputId = useId();

/** 올려 둔 아이콘이 있으면 그것, 없으면 기본 아이콘 */
const iconUrl = computed(() => (iconView.value ? (iconView.value.previewUrl ?? iconView.value.defaultPreviewUrl) : ''));

async function loadIcon(): Promise<void> {
    iconLoading.value = true;
    iconLoadError.value = null;
    try {
        iconView.value = await api.icon();
    } catch (e) {
        iconLoadError.value = toApiError(e);
    } finally {
        iconLoading.value = false;
    }
}

function openIconSvg(): void {
    iconSvgDraft.value = '';
    iconSvgError.value = '';
    showIconSvg.value = !showIconSvg.value;
}

function closeIconSvg(): void {
    showIconSvg.value = false;
    iconSvgError.value = '';
}

/** 그림을 골랐다 — 파일 머리를 먼저 읽어 서버와 같은 순서로 거르고, 통과하면 올린다(바로 적용) */
async function onIconPicked(file: File): Promise<void> {
    if (iconBusy.value || !canManage.value) return;
    iconError.value = '';
    iconBusy.value = true;
    try {
        const { problem, dims } = await UtilSiteImage.inspect('icon', file);
        if (problem) {
            iconError.value = UtilSiteImageMessages.problem(t, 'icon', problem, { dims, size: file.size });
            return;
        }
        iconView.value = await api.uploadIcon(file);
        closeIconSvg();
        toast.success(t('site.iconChanged'));
    } catch (e) {
        const error = toApiError(e);
        iconError.value = error.field('file') ?? error.field('site') ?? error.message;
    } finally {
        iconBusy.value = false;
    }
}

/** SVG 소스 — 비었음·길이·`<svg` 만 먼저 보고(로고와 같은 검사) 정사각 여부 등은 서버 문구를 그대로 보인다 */
async function applyIconSvg(): Promise<void> {
    if (iconBusy.value || !canManage.value) return;
    const problem = UtilSiteSetting.logoSvg(iconSvgDraft.value);
    if (problem) {
        iconSvgError.value = UtilSiteSettingMessages.logoSvgProblem(t, problem);
        return;
    }
    iconBusy.value = true;
    iconSvgError.value = '';
    iconError.value = '';
    try {
        iconView.value = await api.setIconSvg({ svgSource: iconSvgDraft.value.trim() });
        closeIconSvg();
        toast.success(t('site.iconSvgApplied'));
    } catch (e) {
        const error = toApiError(e);
        iconSvgError.value = error.field('svgSource') ?? error.field('site') ?? error.message;
    } finally {
        iconBusy.value = false;
    }
}

async function resetIcon(): Promise<void> {
    if (iconBusy.value || !canManage.value) return;
    const ok = await confirm({ title: t('site.imageResetTitle'), message: t('site.iconResetMessage'), confirmLabel: t('site.iconReset'), tone: 'danger' });
    if (!ok) return;
    iconBusy.value = true;
    iconError.value = '';
    try {
        iconView.value = await api.clearIcon();
        closeIconSvg();
        toast.success(t('site.iconResetDone'));
    } catch (e) {
        iconError.value = toApiError(e).message;
    } finally {
        iconBusy.value = false;
    }
}

/* ── ① 링크 미리보기 이미지(og:image) — 아이콘과 같은 동작, SVG 입구는 없다 ─── */

const ogView = ref<SiteOgImageResponse | null>(null);
const ogLoading = ref(false);
const ogLoadError = ref<ApiError | null>(null);
const ogBusy = ref(false);
const ogError = ref('');

/** 올려 둔 이미지가 있으면 그것, 없으면 기본 이미지 */
const ogUrl = computed(() => (ogView.value ? (ogView.value.url ?? ogView.value.defaultUrl) : ''));

async function loadOg(): Promise<void> {
    ogLoading.value = true;
    ogLoadError.value = null;
    try {
        ogView.value = await api.ogImage();
    } catch (e) {
        ogLoadError.value = toApiError(e);
    } finally {
        ogLoading.value = false;
    }
}

/** 그림을 골랐다 — 비율이 안 맞으면 자르지 않고 거절한다(비율 검사가 크기 부족 검사보다 먼저, 순서는 `UtilSiteImage.check`) */
async function onOgPicked(file: File): Promise<void> {
    if (ogBusy.value || !canManage.value) return;
    ogError.value = '';
    ogBusy.value = true;
    try {
        const { problem, dims } = await UtilSiteImage.inspect('og', file);
        if (problem) {
            ogError.value = UtilSiteImageMessages.problem(t, 'og', problem, { dims, size: file.size });
            return;
        }
        ogView.value = await api.uploadOgImage(file);
        toast.success(t('site.ogChanged'));
    } catch (e) {
        const error = toApiError(e);
        ogError.value = error.field('file') ?? error.field('site') ?? error.message;
    } finally {
        ogBusy.value = false;
    }
}

async function resetOg(): Promise<void> {
    if (ogBusy.value || !canManage.value) return;
    const ok = await confirm({ title: t('site.imageResetTitle'), message: t('site.ogResetMessage'), confirmLabel: t('site.ogReset'), tone: 'danger' });
    if (!ok) return;
    ogBusy.value = true;
    ogError.value = '';
    try {
        ogView.value = await api.clearOgImage();
        toast.success(t('site.ogResetDone'));
    } catch (e) {
        ogError.value = toApiError(e).message;
    } finally {
        ogBusy.value = false;
    }
}

/* ── ② 표시 기본값 ──────────────────────────────── */

const displaySaved = ref<SiteDisplayDefaultsResponse | null>(null);
const displayValues = ref<DisplayValues | null>(null);
const displayLoading = ref(false);
const displayLoadError = ref<ApiError | null>(null);
const displaySaving = ref(false);
const displayErrors = ref<DisplayFieldErrors>({});
const displayFormError = ref('');

const displayChanged = computed(() => displaySaved.value !== null && displayValues.value !== null && !UtilDisplayValues.isSame(displaySaved.value.values, displayValues.value));
const displayInvalid = computed(() => Object.keys(displayErrors.value).length);

function fillDisplay(res: SiteDisplayDefaultsResponse): void {
    displaySaved.value = res;
    displayValues.value = UtilDisplayValues.copy(res.values);
    displayErrors.value = {};
    displayFormError.value = '';
}

function resetDisplay(): void {
    if (displaySaved.value) fillDisplay(displaySaved.value);
}

async function loadDisplay(): Promise<void> {
    displayLoading.value = true;
    displayLoadError.value = null;
    try {
        fillDisplay(await api.displayDefaults());
    } catch (e) {
        displayLoadError.value = toApiError(e);
    } finally {
        displayLoading.value = false;
    }
}

async function saveDisplay(): Promise<void> {
    if (displaySaving.value || !canManage.value || !displayChanged.value || !displayValues.value) return;
    displaySaving.value = true;
    displayErrors.value = {};
    displayFormError.value = '';
    try {
        fillDisplay(await api.saveDisplayDefaults({ values: displayValues.value }));
        // 이 값이 "기본 설정"이므로 내 표시 값(사이트 표시 기본값 ← 내 표시 형식)을 다시 받아 지금 화면에도 먹인다 — 저장했는데 화면이 안 바뀌면 저장됐는지 알 수 없다
        await reloadMyDisplay();
        toast.success(t('site.displaySaved'));
    } catch (e) {
        displayErrors.value = UtilDisplayForm.fieldErrors(e);
        if (Object.keys(displayErrors.value).length === 0) displayFormError.value = toApiError(e).message;
    } finally {
        displaySaving.value = false;
    }
}


onMounted(() => {
    void loadBasic();
    void loadIcon();
    void loadOg();
    void loadDisplay();
});
</script>

<style scoped>
/*
 * 공유 레이어(`@roboyam/display-time`)의 `DisplayValueForm` 은 user-web 과 함께 쓰므로 레이어는 고치지 않고 이 화면에서만 글자를 줄인다(내 정보 > 개인 설정 탭과 같은 모양).
 */
.display-form-sm :deep(label:not(.cursor-pointer)),
.display-form-sm :deep(legend) {
    font-size: 0.75rem;
    font-weight: 700;
    color: var(--p-text-color);
}
.display-form-sm :deep(label.cursor-pointer) {
    font-size: 0.8125rem;
}
.display-form-sm :deep(fieldset > div) {
    row-gap: 0.25rem;
}
.display-form-sm :deep(fieldset > div > div) {
    min-height: 1.375rem;
}
</style>
