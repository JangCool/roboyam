<template>
    <!--
        로고 묶음 한 벌을 올리거나 고치는 창(로고 보관함) — 이름 + 유형(캐릭터 로고·글자 로고 — `fieldErrors.style`) + 4칸(① 한글·밝은 ② 한글·어두운 ③ 영문·밝은 ④ 영문·어두운, 칸마다 `SiteLogoVariantTile`) + 「로고에 사이트 이름이 들어 있음」.
        예전 일반 화면의 4칸은 올리는 즉시 적용이었지만, 묶음은 이름·칸·스위치를 **[저장] 한 번으로** 함께 저장한다(칸을 고르는 동안은 이 창의 초안이다).
        빈 칸은 대체 순서(`UtilSiteLogo.resolve`)대로 같은 묶음의 다른 칸이 대신하고 타일이 그 그림을 흐리게 보인다. SVG 소스 칸은 한 번에 한 칸만 열린다.
        `readonly`(기본 제공 묶음이거나 권한 없음)면 보기만 한다 — 단 기본 제공 묶음이고 권한이 있으면(`nameInLogoEditable`) 「이름 포함」 토글과 그 값만 보내는 [저장](`saveNameInLogo`)이 있다(2026-11-19). 삭제는 없다(올린 로고는 보관 — 사용자 결정).
        서버는 부르지 않는다 — 값은 `submit` 으로 올리고, 서버 거절 문구는 `fieldErrors`(`name`·`cells`·`cells.<칸>` — 그 칸 타일 아래)·`error` 로 받는다.
    -->
    <Dialog :visible="visible" modal :draggable="false" :closable="!busy" :header="header" :style="{ width: 'min(44rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <Message v-if="builtIn" severity="info" :closable="false" size="small">{{ t('siteLogo.builtInNote') }}</Message>

            <LabeledField :label="t('siteLogo.bundleNameLabel')" :hint="t('siteLogo.bundleNameHint', { max: SITE_LOGO_BUNDLE_NAME_MAX })" :error="nameError">
                <template #default="{ id, describedBy, invalid }">
                    <InputText :id="id" v-model="name" name="bundleName" autocomplete="off" size="small" :maxlength="SITE_LOGO_BUNDLE_NAME_MAX" :readonly="readonly || busy" :invalid="invalid" :aria-describedby="describedBy" aria-required="true" class="w-full max-w-sm" />
                </template>
            </LabeledField>

            <!-- 유형 — 올린 묶음은 운영자가 고른다. 보기만일 때(기본 제공·권한 없음)는 글자로만 보인다 -->
            <div class="flex flex-col gap-1.5">
                <span :id="styleLabelId" class="text-sm font-medium">{{ t('siteLogo.bundleStyleLabel') }}</span>
                <p v-if="readonly" class="text-sm">{{ styleOptions.find((option) => option.value === style)?.label }}</p>
                <template v-else>
                    <SelectButton v-model="style" :options="styleOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :disabled="busy" :invalid="Boolean(fieldErrors.style)" :aria-labelledby="styleLabelId" :aria-describedby="`${styleLabelId}-help`" class="self-start" />
                    <p :id="`${styleLabelId}-help`" class="text-xs leading-relaxed text-muted-color">{{ t('siteLogo.bundleStyleHelp') }}</p>
                    <p v-if="fieldErrors.style" role="alert" class="text-xs text-red-600 dark:text-red-400">{{ fieldErrors.style }}</p>
                </template>
            </div>

            <div role="group" :aria-labelledby="cellsTitleId" class="flex flex-col gap-2">
                <h3 :id="cellsTitleId" class="text-sm font-medium">{{ t('siteLogo.cellsLabel') }}</h3>
                <p class="text-xs leading-relaxed text-muted-color">{{ t('site.logoVariantsHelp') }} {{ t('site.logoFallbackOrder') }}</p>

                <div class="grid gap-3 sm:grid-cols-2">
                    <SiteLogoVariantTile
                        v-for="variant in UtilSiteLogo.VARIANTS"
                        :key="variant"
                        :title="UtilSiteSettingMessages.logoVariantTitle(t, variant)"
                        :tone="UtilSiteLogo.toneOf(variant)"
                        :preview-url="UtilSiteLogo.resolve(previews, variant)?.url ?? null"
                        :fallback="!previews[variant]"
                        :status-label="UtilSiteSettingMessages.logoVariantStatus(t, previews, variant)"
                        :name-text="siteName"
                        :tile-label="t(previews[variant] ? 'site.logoVariantTileChange' : 'site.logoVariantTileAdd', { variant: UtilSiteSettingMessages.logoVariantTitle(t, variant) })"
                        :editable="!readonly"
                        :busy="busy"
                        :svg-open="svgVariant === variant"
                        :error="cellErrorVariant === variant ? cellError : (fieldErrors[`cells.${variant}`] ?? '')"
                        @pick="(file) => onPicked(variant, file)"
                        @open-svg="openSvgInput(variant)"
                        @remove="clearCell(variant)"
                    />
                </div>

                <div v-if="svgVariant && !readonly" class="flex flex-col gap-1.5 rounded-xl bg-surface-100 p-3 dark:bg-surface-800">
                    <label :for="svgInputId" class="text-xs font-medium">{{ t('site.logoSvgSourceFor', { variant: UtilSiteSettingMessages.logoVariantTitle(t, svgVariant) }) }}</label>
                    <Textarea :id="svgInputId" v-model="svgDraft" rows="5" fluid :invalid="Boolean(svgError)" :readonly="busy" :aria-describedby="svgError ? `${svgInputId}-msg` : undefined" placeholder='<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 36">...</svg>' class="font-mono! text-xs!" @input="svgError = ''" />
                    <p v-if="svgError" :id="`${svgInputId}-msg`" role="alert" class="text-xs text-red-600 dark:text-red-400">{{ svgError }}</p>
                    <div class="flex gap-2">
                        <Button type="button" :label="t('siteLogo.svgUse')" size="small" :disabled="!svgDraft.trim() || busy" @click="applySvg" />
                        <Button type="button" :label="t('common.cancel')" size="small" severity="secondary" variant="text" :disabled="busy" @click="closeSvgInput" />
                    </div>
                </div>

                <p v-if="cellsError" role="alert" class="text-xs text-red-600 dark:text-red-400">{{ cellsError }}</p>

                <p v-if="!readonly" class="text-xs leading-relaxed text-muted-color">
                    {{ t('siteLogo.cellsHint') }}
                    <strong class="font-semibold text-color">{{ t('site.logoSvgWhy') }}</strong>
                    {{ t('site.logoSvgWhyTail') }}
                    <code class="text-[0.6875rem]">xmlns</code> {{ t('site.logoSvgXmlns') }}
                </p>
            </div>

            <!-- 「이름 포함」 — 묶음마다 따로. 켜면 이 묶음이 보일 때 공개 헤더가 로고 옆 이름 글자를 붙이지 않는다 -->
            <div class="flex items-center gap-3">
                <ToggleSwitch v-model="nameInLogo" :input-id="nameInLogoId" :disabled="(readonly && !nameInLogoEditable) || busy" :aria-describedby="`${nameInLogoId}-help`" />
                <div>
                    <label :for="nameInLogoId" class="text-sm">{{ t('site.logoNameInLogo') }}</label>
                    <p :id="`${nameInLogoId}-help`" class="text-xs text-muted-color">{{ t('siteLogo.nameInLogoHelp') }}</p>
                </div>
            </div>

            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ error }}</Message>
        </form>
        <template #footer>
            <template v-if="readonly">
                <Button type="button" :label="t('common.close')" severity="secondary" variant="text" :disabled="busy" @click="emit('update:visible', false)" />
                <!-- 기본 제공 묶음도 「이름 포함」은 바꿀 수 있다(권한이 있을 때) — 그 값만 저장 -->
                <Button v-if="nameInLogoEditable && bundle" type="button" :label="t('common.save')" icon="pi pi-check" :loading="busy" :disabled="nameInLogo === bundle.nameInLogo" @click="emit('saveNameInLogo', nameInLogo)" />
            </template>
            <template v-else>
                <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="emit('update:visible', false)" />
                <Button type="submit" :form="formId" :label="t('common.save')" icon="pi pi-check" :loading="busy" />
            </template>
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { SiteLogoBundle, SiteLogoBundleInput, SiteLogoCellInput, SiteLogoStyle } from '~/composables/site/api/useSiteLogoLibraryApi.types';
import { SITE_LOGO_BUNDLE_NAME_MAX } from '~/composables/site/api/useSiteLogoLibraryApi.types';
import UtilSiteLogo, { type SiteLogoSet, type SiteLogoVariant } from '~/utils/util-site-logo';
import UtilSiteSetting from '~/utils/util-site-setting';
import UtilSiteSettingMessages from '~/utils/util-site-setting-messages';

interface Props {
    visible: boolean;
    /** 고칠(또는 볼) 묶음 — null 이면 새로 올리기 */
    bundle?: SiteLogoBundle | null;
    /** 보기만(기본 제공 묶음·권한 없음) */
    readonly?: boolean;
    /** 보기만인 창(기본 제공 묶음)에서도 「이름 포함」은 바꿀 수 있다 — 권한(`site.manage`)이 있을 때. [저장] 이 그 값만 보낸다(`saveNameInLogo`) */
    nameInLogoEditable?: boolean;
    busy?: boolean;
    /** 칸에 붙지 않는 서버 오류 */
    error?: string;
    /** 서버가 칸에 붙여 준 오류 — 열쇠는 서버 경로 그대로(`name` · `cells` 한 칸 이상 · `cells.koLight`… 그 칸의 파일·SVG 검사) */
    fieldErrors?: Record<string, string>;
    /** 쓸 그림이 없는 칸에 흐리게 보일 사이트 이름 글자 */
    siteName?: string;
}

const props = withDefaults(defineProps<Props>(), { bundle: null, readonly: false, nameInLogoEditable: false, busy: false, error: '', fieldErrors: () => ({}), siteName: '' });
const emit = defineEmits<{ 'update:visible': [visible: boolean]; submit: [input: SiteLogoBundleInput]; saveNameInLogo: [nameInLogo: boolean] }>();

const { t } = useI18n();
const formId = useId();
const cellsTitleId = useId();
const svgInputId = useId();
const nameInLogoId = useId();
const styleLabelId = useId();

const builtIn = computed(() => Boolean(props.bundle?.builtIn));
const header = computed(() => {
    if (!props.bundle) return t('siteLogo.createTitle');
    return props.readonly ? props.bundle.name : t('siteLogo.editTitle');
});

/* ── 초안 — 창을 열 때마다 묶음 값으로 다시 채운다 ── */

const name = ref('');
const nameInLogo = ref(true);
/** 유형 — 새로 올릴 때는 캐릭터 로고, 고칠 때는 지금 값 */
const style = ref<SiteLogoStyle>('CHARACTER');
const styleOptions = computed<{ label: string; value: SiteLogoStyle }[]>(() => [
    { label: t('siteLogo.holidayStyleCharacter'), value: 'CHARACTER' },
    { label: t('siteLogo.holidayStyleWordmark'), value: 'WORDMARK' },
]);
/** 칸에 넣을 것(`KEEP` = 지금 그대로) */
const cells = ref<Record<SiteLogoVariant, SiteLogoCellInput>>({ koLight: null, koDark: null, enLight: null, enDark: null });
/** 칸의 미리보기 주소 — 저장된 그림 주소, 방금 고른 파일의 임시 주소, SVG data URI */
const previews = ref<SiteLogoSet>({ ...UtilSiteLogo.EMPTY });
/** 고른 파일의 임시 주소(`URL.createObjectURL`) — 바꾸거나 닫을 때 놓아 준다 */
const objectUrls = new Map<SiteLogoVariant, string>();
const tried = ref(false);
const cellError = ref('');
const cellErrorVariant = ref<SiteLogoVariant | null>(null);
const svgVariant = ref<SiteLogoVariant | null>(null);
const svgDraft = ref('');
const svgError = ref('');

function releaseUrl(variant: SiteLogoVariant): void {
    const url = objectUrls.get(variant);
    if (url) URL.revokeObjectURL(url);
    objectUrls.delete(variant);
}

function fill(): void {
    for (const variant of UtilSiteLogo.VARIANTS) releaseUrl(variant);
    const bundle = props.bundle;
    name.value = bundle?.name ?? '';
    nameInLogo.value = bundle?.nameInLogo ?? true;
    style.value = bundle?.style === 'WORDMARK' ? 'WORDMARK' : 'CHARACTER';
    previews.value = bundle ? { ...bundle.logos } : { ...UtilSiteLogo.EMPTY };
    cells.value = {
        koLight: bundle?.logos.koLight ? 'KEEP' : null,
        koDark: bundle?.logos.koDark ? 'KEEP' : null,
        enLight: bundle?.logos.enLight ? 'KEEP' : null,
        enDark: bundle?.logos.enDark ? 'KEEP' : null,
    };
    tried.value = false;
    cellError.value = '';
    cellErrorVariant.value = null;
    svgVariant.value = null;
    svgDraft.value = '';
    svgError.value = '';
}

// 창이 열릴 때 초안을 그 묶음 값으로 맞춘다(부수 효과 — 임시 주소도 놓아 준다)
watch(
    () => props.visible,
    (visible) => {
        if (visible) fill();
    },
    { immediate: true },
);

onBeforeUnmount(() => {
    for (const variant of UtilSiteLogo.VARIANTS) releaseUrl(variant);
});

function setCell(variant: SiteLogoVariant, input: SiteLogoCellInput, preview: string | null): void {
    cells.value = { ...cells.value, [variant]: input };
    previews.value = { ...previews.value, [variant]: preview };
}

/** 그림을 골랐다 — 형식·용량을 먼저 거른다(기존 로고 검사 `UtilSiteSetting.logoFile`) */
function onPicked(variant: SiteLogoVariant, file: File): void {
    if (props.readonly || props.busy) return;
    cellError.value = '';
    cellErrorVariant.value = null;
    const problem = UtilSiteSetting.logoFile(file);
    if (problem) {
        cellErrorVariant.value = variant;
        cellError.value = UtilSiteSettingMessages.logoFileProblem(t, problem);
        return;
    }
    releaseUrl(variant);
    const url = URL.createObjectURL(file);
    objectUrls.set(variant, url);
    setCell(variant, { file }, url);
    if (svgVariant.value === variant) closeSvgInput();
}

/** 같은 칸을 다시 누르면 닫는다. 다른 칸이면 그 칸으로 바꿔 연다 */
function openSvgInput(variant: SiteLogoVariant): void {
    const same = svgVariant.value === variant;
    svgDraft.value = '';
    svgError.value = '';
    svgVariant.value = same ? null : variant;
}

function closeSvgInput(): void {
    svgVariant.value = null;
    svgError.value = '';
}

/** SVG 소스를 그 칸에 넣는다 — 미리보기는 `<img src>` 의 data URI 로만 그린다(`v-html` 금지) */
function applySvg(): void {
    const variant = svgVariant.value;
    if (!variant || props.busy) return;
    const problem = UtilSiteSetting.logoSvg(svgDraft.value);
    if (problem) {
        svgError.value = UtilSiteSettingMessages.logoSvgProblem(t, problem);
        return;
    }
    const source = svgDraft.value.trim();
    releaseUrl(variant);
    setCell(variant, { svgSource: source }, `data:image/svg+xml;charset=utf-8,${encodeURIComponent(source)}`);
    closeSvgInput();
}

/** 칸을 비운다 — 초안이라 묻지 않는다(저장해야 반영된다) */
function clearCell(variant: SiteLogoVariant): void {
    if (props.readonly || props.busy) return;
    releaseUrl(variant);
    setCell(variant, null, null);
    if (svgVariant.value === variant) closeSvgInput();
}

/* ── 검사·저장 ── */

const nameError = computed(() => {
    const trimmed = name.value.trim();
    if (!trimmed) return tried.value ? t('siteLogo.bundleNameRequired') : '';
    if (trimmed.length > SITE_LOGO_BUNDLE_NAME_MAX) return t('siteLogo.bundleNameLength', { max: SITE_LOGO_BUNDLE_NAME_MAX });
    return props.fieldErrors.name ?? '';
});
const hasCell = computed(() => UtilSiteLogo.VARIANTS.some((variant) => cells.value[variant] !== null));
const cellsError = computed(() => {
    if (!hasCell.value) return tried.value ? t('siteLogo.cellsRequired') : '';
    return props.fieldErrors.cells ?? '';
});

function submit(): void {
    if (props.readonly || props.busy) return;
    tried.value = true;
    const trimmed = name.value.trim();
    if (!trimmed || trimmed.length > SITE_LOGO_BUNDLE_NAME_MAX || !hasCell.value) return;
    // 반응형 껍데기 없이 평범한 값으로 올린다(파일 객체는 그대로)
    const raw = toRaw(cells.value);
    emit('submit', { name: trimmed, nameInLogo: nameInLogo.value, style: style.value, cells: { koLight: raw.koLight, koDark: raw.koDark, enLight: raw.enLight, enDark: raw.enDark } });
}
</script>
