<template>
    <div>
        <!--
            운영정책(`/policy`) — 기본 레이아웃, 비회원도 본다. `?version=N` 이면 그 버전. 모양은 `TermsDocumentView`, 불러오기는 `useTermsDocument`(이용약관·개인정보처리방침과 같다).
            서버 `GET /api/terms/operation_policy`(·`/versions/{N}`). 불리한 변경 안내 메일의 링크가 `/policy?version=N` 이다. 가입 동의·변경 안내 띠 대상이 아니다.
        -->
        <TermsDocumentView
            kind="OPERATION_POLICY"
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
useSeoMeta({ title: () => t('terms.operationPolicy') });

const route = useRoute();
/** 주소의 버전 — 없거나 맞지 않으면 null(지금 운영정책) */
const version = computed(() => UtilTerms.parseVersion(route.query.version));
const { doc, status, errorMessage, shown, versionLoading, versionMissing, versionError, load } = useTermsDocument('OPERATION_POLICY', version);

onMounted(() => void load());
</script>
