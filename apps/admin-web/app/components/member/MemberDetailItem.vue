<template>
    <!-- 회원 상세 서랍의 한 줄(이름 + 값) — `<dl>` 안에서 쓴다. 값이 없으면 `-`, 가려진 값(`masked`)은 이메일처럼 가려졌다는 표시를 눈과 스크린리더에 함께 준다 -->
    <div class="grid grid-cols-[7.5rem_minmax(0,1fr)] gap-x-3 border-b border-(--line) py-2.5 last:border-b-0">
        <dt class="text-[13px] text-muted-color">{{ label }}</dt>
        <dd class="m-0 min-w-0 text-[13px] break-words">
            <span v-if="empty" class="text-muted-color">{{ t('member.detail.empty') }}</span>
            <template v-else>
                <slot />
                <span v-if="masked" class="sr-only"> {{ t('member.detail.maskedSuffix') }}</span>
                <span v-if="masked" class="ml-1.5 inline-flex items-center gap-1 text-[11px] text-muted-color" aria-hidden="true">
                    <i class="pi pi-eye-slash" />
                    {{ t('member.detail.maskedSuffix') }}
                </span>
            </template>
        </dd>
    </div>
</template>

<script setup lang="ts">
interface Props {
    label: string;
    /** 보일 값이 없다 — `-` 를 그린다 */
    empty?: boolean;
    /** 값이 가려져 왔다(`member.pii` 없음) */
    masked?: boolean;
}

withDefaults(defineProps<Props>(), { empty: false, masked: false });

const { t } = useI18n();
</script>
