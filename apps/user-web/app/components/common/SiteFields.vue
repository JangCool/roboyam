<template>
    <!-- 사이트 이름 + 한 줄 소개(선택) + 공개 주소 — 설치 마법사 3단계가 쓴다(운영자 설정 화면은 관리자 앱 `admin/` 에 같은 컴포넌트의 사본이 있다). 아래 슬롯에 그 화면만의 안내를 넣는다 -->
    <div class="flex flex-col gap-4">
        <LabeledField :label="t('site.nameLabel')" :hint="t('site.nameHint', { max: UtilSite.NAME_MAX })" :error="errors?.name">
            <template #default="{ id, describedBy, invalid }">
                <InputText
                    :id="id"
                    v-model="model.name"
                    type="text"
                    name="siteName"
                    autocomplete="off"
                    :disabled="disabled"
                    :invalid="invalid"
                    :aria-describedby="describedBy"
                    fluid
                    data-testid="site-name"
                />
            </template>
        </LabeledField>

        <!-- 선택 입력 — 길이·글자 검사는 `UtilSite` 가 한다(`maxlength` 를 걸면 붙여 넣은 긴 글이 조용히 잘린다) -->
        <LabeledField :label="t('site.taglineLabel')" :hint="t('site.taglineHint', { max: UtilSite.TAGLINE_MAX })" :help="t('site.taglineHelp')" :error="errors?.tagline">
            <template #default="{ id, describedBy, invalid }">
                <InputText
                    :id="id"
                    v-model="model.tagline"
                    type="text"
                    name="siteTagline"
                    autocomplete="off"
                    :disabled="disabled"
                    :invalid="invalid"
                    :aria-describedby="describedBy"
                    fluid
                    data-testid="site-tagline"
                />
            </template>
        </LabeledField>

        <LabeledField :label="t('site.publicUrlLabel')" :help="t('site.publicUrlHelp')" :error="errors?.publicUrl">
            <template #default="{ id, describedBy, invalid }">
                <InputText
                    :id="id"
                    v-model="model.publicUrl"
                    type="url"
                    name="sitePublicUrl"
                    inputmode="url"
                    autocomplete="off"
                    autocapitalize="off"
                    spellcheck="false"
                    :placeholder="PUBLIC_URL_PLACEHOLDER"
                    :disabled="disabled"
                    :invalid="invalid"
                    :aria-describedby="describedBy"
                    fluid
                    data-testid="site-public-url"
                />
            </template>
        </LabeledField>

        <slot />
    </div>
</template>

<script setup lang="ts">
import type { SiteFieldErrors } from '~/utils/util-site';
import UtilSite, { type SiteForm } from '~/utils/util-site';

/**
 * 사이트 이름·한 줄 소개(선택)·공개 주소 입력칸(`UtilSite` 규칙). 값은 부모의 객체를 그대로 고친다(`v-model`) — 서버는 부르지 않는다.
 * 오류 문구(`errors`)는 부모가 미리 검사하거나 서버가 거절한 것을 넘겨 준다.
 */
const model = defineModel<SiteForm>({ required: true });

const { t } = useI18n();

// 템플릿 속성에 `//` 가 든 글자를 직접 쓰면 vue-tsc 3 가 슬롯 값(id 등)을 잃는다 — 상수로 뺀다
const PUBLIC_URL_PLACEHOLDER = 'https://example.com';

interface Props {
    errors?: SiteFieldErrors;
    disabled?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
    errors: () => ({}),
    disabled: false,
});
</script>
