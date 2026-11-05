<template>
    <div>
        <!--
            로보티콘 상품화 계약(`/robotikon/contract`) — 유료 팩 작가가 신청 때 동의하는 계약의 공개 화면(emoticon-contract S2, 약관 종류 `ROBOTIKON_CONTRACT`).
            기본 레이아웃, 비회원도 본다. `?version=N` 이면 그 버전(시행 예정·지난). 모양·불러오기는 이용약관(`/terms`)과 같다(`TermsDocumentView` · `useTermsDocument`).
            가입·로그인 화면의 약관 링크와 변경 안내 띠에는 들어가지 않는다(서버 요약에서 빠진다). 스튜디오 신청 화면의 「전문 보기」가 새 창으로 이 화면을 연다.
        -->
        <TermsDocumentView
            kind="ROBOTIKON_CONTRACT"
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
useSeoMeta({ title: () => t('terms.robotikonContract') });

const route = useRoute();
/** 주소의 버전 — 없거나 맞지 않으면 null(지금 계약) */
const version = computed(() => UtilTerms.parseVersion(route.query.version));
const { doc, status, errorMessage, shown, versionLoading, versionMissing, versionError, load } = useTermsDocument('ROBOTIKON_CONTRACT', version);

onMounted(() => void load());
</script>
