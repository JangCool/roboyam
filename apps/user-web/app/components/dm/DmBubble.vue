<template>
    <!-- 말풍선 한 개 — 상대는 왼쪽(회색), 내 쪽지는 오른쪽(주 색상). 프로필 이미지는 쓰지 않는다.
         본문은 일반 글자다: `v-html` 을 쓰지 않고 `linkify` 가 쪼갠 조각마다 글자 또는 `<a>` 를 그린다. 줄바꿈은 그대로 -->
    <div class="flex px-3.5" :class="mine ? 'justify-end' : 'justify-start'" :data-testid="failed ? 'dm-bubble-failed' : mine ? 'dm-bubble-mine' : 'dm-bubble'">
        <div class="max-w-[78%] rounded-2xl px-3 py-2 text-sm" :class="[mine ? 'rounded-br-sm' : 'rounded-bl-sm', failed ? FAILED : mine ? MINE : THEIRS]">
            <p class="m-0 whitespace-pre-wrap leading-relaxed [overflow-wrap:anywhere]"><template v-for="(part, i) in parts" :key="i"><a v-if="part.href" :href="part.href" target="_blank" rel="noopener noreferrer nofollow" class="underline underline-offset-2 hover:opacity-80 focus-visible:outline-2 focus-visible:outline-primary">{{ part.text }}</a><template v-else>{{ part.text }}</template></template></p>

            <p v-if="failed" class="m-0 mt-1 text-right text-[0.6875rem] text-red-700 dark:text-red-300">
                <template v-if="retrying">{{ t('dm.resending') }}</template>
                <template v-else>
                    {{ t('dm.sendFailed') }} ·
                    <button type="button" class="font-semibold underline underline-offset-2 focus-visible:outline-2 focus-visible:outline-primary" data-testid="dm-resend" @click="$emit('resend')">{{ t('dm.resend') }}</button>
                    ·
                    <button type="button" class="underline underline-offset-2 focus-visible:outline-2 focus-visible:outline-primary" data-testid="dm-discard" @click="$emit('discard')">{{ t('common.delete') }}</button>
                </template>
            </p>
            <p v-else class="m-0 mt-0.5 text-[0.6875rem] text-muted-color" :class="mine ? 'text-right' : ''">
                <time :datetime="createdAt" :title="time.full(createdAt)">{{ time.clock(createdAt) }}</time>
            </p>
        </div>
    </div>
</template>

<script setup lang="ts">
import UtilDm from '~/utils/util-dm';

/** 말풍선 — `failed` 는 전송하지 못해 화면에만 남은 쪽지(붉게, 「다시 보내기 · 삭제」). 다시 보내는 중(`retrying`)이면 「보내는 중…」 */
interface Props {
    mine: boolean;
    body: string;
    createdAt: string;
    failed?: boolean;
    retrying?: boolean;
}

const props = withDefaults(defineProps<Props>(), { failed: false, retrying: false });

defineEmits<{ resend: []; discard: [] }>();

const { t } = useI18n();
const time = useDisplayTime();
const parts = computed(() => UtilDm.linkify(props.body));

const THEIRS = 'bg-surface-100 text-color dark:bg-surface-800';
const MINE = 'bg-primary-100 text-primary-950 dark:bg-primary-900/50 dark:text-primary-50';
const FAILED = 'border border-red-500 bg-red-50 text-red-950 dark:border-red-400 dark:bg-red-950/40 dark:text-red-100';
</script>
