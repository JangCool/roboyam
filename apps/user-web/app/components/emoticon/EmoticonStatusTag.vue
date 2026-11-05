<template>
    <!-- 신청 상태 딱지(임시·심사 대기·판매 중·반려·판매 중단) — 스튜디오 목록·고치기 화면이 같이 쓴다. 모양은 포인트 상점 딱지와 같다 -->
    <PointShopTag :tone="UtilEmoticon.statusTone(status)">{{ UtilEmoticonMessages.status(t, status) }}</PointShopTag>
</template>

<script setup lang="ts">
import type { EmoticonStudioStatus } from '~/composables/emoticon/api/useEmoticonStudioApi.types';
import UtilEmoticon from '~/utils/util-emoticon';
import UtilEmoticonMessages from '~/utils/util-emoticon-messages';

interface Props {
    status: EmoticonStudioStatus;
}

defineProps<Props>();

const { t } = useI18n();
</script>
