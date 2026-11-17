<template>
    <!--
        체험단 줄의 회원 이름(서버 `TrialViews.Member`) — 모집 상세(신청자 · 뽑힌 줄 · 파손 신고)와 확인할 일 목록이 같이 쓴다.
        회원 번호(`memberNo`)가 있고 회원 조회 권한(`linkable` — 화면이 `member.read` 로 정한다)이 있으면 회원 관리(`/members?memberNo=`)로 가는 링크, 아니면 글자만.
        닉네임이 없으면(탈퇴) 「탈퇴한 회원」, 회원이 없으면 「—」.
    -->
    <NuxtLink v-if="to" :to="to" class="font-medium text-(--p-primary-color) hover:underline">{{ name }}</NuxtLink>
    <span v-else>{{ name }}</span>
</template>

<script setup lang="ts">
import type { TrialMember } from '~/composables/trial/api/useTrialApi.types';
import UtilTrial from '~/utils/util-trial';

interface Props {
    member?: TrialMember | null;
    /** 회원 관리로 가는 링크를 걸어도 되나(`member.read`) */
    linkable?: boolean;
}

const props = withDefaults(defineProps<Props>(), { member: null, linkable: false });

const { t } = useI18n();

const name = computed(() => (props.member ? (props.member.nickname ?? t('trial.withdrawn')) : '—'));
const to = computed(() => (props.linkable && props.member?.memberNo ? UtilTrial.memberLink(props.member.memberNo) : null));
</script>
