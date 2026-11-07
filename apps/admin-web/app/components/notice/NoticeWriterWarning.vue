<template>
    <!--
        공지 작성 계정 경고 띠 — 내 운영자 회원 계정이 없거나 멈춰 있어 공지를 쓸 수 없을 때(operator-member-account-contract).
        공지 목록(`/notices`)과 작성·수정 화면(`/notices/{id}`)이 같이 쓴다. 연결됐으면 아무것도 그리지 않는다.
        [운영자 설정에서 연결] → `/settings/operators`(운영자 서랍 「사이트 계정」 탭에서 잇는다).
    -->
    <Message v-if="writer.state !== 'LINKED'" severity="warn" :closable="false" size="small">
        <div class="flex flex-col gap-1">
            <p class="m-0">{{ t('notice.writerMissing') }}</p>
            <p v-if="writer.state === 'PAUSED' && writer.pausedReasons.length" class="m-0 text-xs">
                {{ t('operatorMember.pausedTitle') }}: {{ writer.pausedReasons.map((r) => t(`operatorMember.paused.${r}`)).join(' ') }}
            </p>
            <NuxtLink to="/settings/operators" class="self-start text-xs font-medium underline">{{ t('notice.writerLink') }}</NuxtLink>
        </div>
    </Message>
</template>

<script setup lang="ts">
import type { NoticeWriter } from '~/composables/notice/api/useNoticeApi.types';

interface Props {
    writer: NoticeWriter;
}

withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
</script>
