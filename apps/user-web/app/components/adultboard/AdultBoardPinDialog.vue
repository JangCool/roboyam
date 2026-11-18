<template>
    <!--
        보던 도중 핀 확인이 끝났을 때의 핀 입력 대화상자 — 글 보기(댓글·추천·신고·투표·아래 목록 쪽 넘기기)·글쓰기(등록·태그 추천)에서 서버가 「핀 필요」
        (`403 ADULT_BOARD_PIN_REQUIRED`)라고 하면 화면을 갈아 끼우지 않고 위에 띄운다 — 쓰던 댓글·글이 그대로 남는다(adult-board-pin-contract).
        맞게 넣으면 닫고 `unlocked` 를 알린다(부모는 화면을 다시 읽지 않고, 회원이 하던 일을 다시 누른다). 처음 읽을 때의 핀 입력은 내용 자리의 `AdultBoardPinGate` 다.
    -->
    <Dialog
        :visible="visible"
        modal
        :draggable="false"
        :header="t('adultBoard.title')"
        :style="{ width: 'min(26rem, calc(100vw - 2rem))' }"
        data-testid="adult-board-pin-dialog"
        @update:visible="emit('update:visible', $event)"
    >
        <p class="m-0 text-center text-sm leading-relaxed text-muted-color" data-testid="adult-board-pin-expired">{{ t('adultBoard.gateExpired') }}</p>
        <AdultBoardPinGate :locked-until="lockedUntil" :heading-level="2" @unlocked="onUnlocked" />
    </Dialog>
</template>

<script setup lang="ts">
interface Props {
    visible: boolean;
    /** 틀림 잠금의 끝 시각(ISO) — 「핀 필요」 알림의 `lockedUntil`, 없으면 null */
    lockedUntil?: string | null;
}

withDefaults(defineProps<Props>(), { lockedUntil: null });
const emit = defineEmits<{ 'update:visible': [value: boolean]; unlocked: [] }>();

const { t } = useI18n();

function onUnlocked(): void {
    emit('update:visible', false);
    emit('unlocked');
}
</script>
