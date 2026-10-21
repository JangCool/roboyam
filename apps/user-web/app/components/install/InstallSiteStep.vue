<template>
    <SiteFields v-model="model" :errors="UtilInstall.sliceErrors(errors, 'site')" :disabled="disabled">
        <!-- 지금 접속한 주소로 채워 준다 / 이 컴퓨터에서만 열리는 주소면 알려 준다 -->
        <Message v-if="isLocal" severity="warn" :closable="false" size="small" data-testid="site-local-note">{{ t('site.publicUrlLocal') }}</Message>
        <p v-else-if="prefilled" class="text-xs text-muted-color" data-testid="site-prefilled-note">{{ t('site.publicUrlPrefilled') }}</p>

        <details class="rounded-lg bg-surface-100 px-3 py-2 text-sm dark:bg-surface-800" data-testid="site-url-what">
            <summary class="cursor-pointer rounded font-medium focus-visible:outline-2 focus-visible:outline-primary">{{ t('site.publicUrlWhatTitle') }}</summary>
            <p class="mt-1.5 text-xs leading-relaxed text-muted-color">{{ t('site.publicUrlWhat') }}</p>
        </details>
    </SiteFields>
</template>

<script setup lang="ts">
import UtilInstall, { type InstallFieldErrors } from '~/utils/util-install';
import UtilSite, { type SiteForm } from '~/utils/util-site';

/**
 * 3단계 — 사이트 이름·공개 주소. 공개 주소는 마법사가 **지금 접속한 주소**로 미리 채워 두고 운영자가 고친다.
 * "공개 주소란?" 은 접어 둔 설명이다(메일 링크·소셜 로그인 콜백·검색 메타의 기준 주소).
 * 값은 부모의 객체를 그대로 고친다(`v-model`) — 서버는 부르지 않는다.
 */
const model = defineModel<SiteForm>({ required: true });

const { t } = useI18n();

interface Props {
    /** 칸 경로(`site.name`·`site.publicUrl`) → 오류 문구 */
    errors: InstallFieldErrors;
    disabled?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
    disabled: false,
});

/** 아직 접속한 주소 그대로인가(고쳤으면 "미리 채웠어요" 안내를 걷는다) */
const prefilled = computed(() => UtilSite.currentOrigin() !== '' && UtilSite.samePublicUrl(model.value.publicUrl, UtilSite.currentOrigin()));
const isLocal = computed(() => UtilSite.isLocal(model.value.publicUrl));
</script>
