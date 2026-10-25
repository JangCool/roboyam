<template>
    <div>
        <!--
            이용약관(`/terms`) — 기본 레이아웃, 비회원도 본다. `?version=N` 이면 그 버전(시행 예정·지난). 모양은 `TermsDocumentView`, 불러오기는 `useTermsDocument`(`docs/features/terms-contract.md`).
        -->
        <TermsDocumentView
            kind="TERMS"
            :status="status"
            :doc="doc"
            :shown="shown"
            :error-message="errorMessage"
            :version-loading="versionLoading"
            :version-missing="versionMissing"
            :version-error="versionError"
            @retry="load"
        />
    </div>
</template>

<script setup lang="ts">
import UtilTerms from '~/utils/util-terms';

const { t } = useI18n();
useSeoMeta({ title: () => t('common.terms') });

const route = useRoute();
/** 주소의 버전 — 없거나 맞지 않으면 null(지금 약관) */
const version = computed(() => UtilTerms.parseVersion(route.query.version));
const { doc, status, errorMessage, shown, versionLoading, versionMissing, versionError, load } = useTermsDocument('TERMS', version);

onMounted(() => void load());
</script>
