<template>
    <!--
        결제 직전 본인인증 확인 — 레이아웃에 한 번 놓는다. 띄우는 것은 `useIdentityGate().ensure({ purpose, requireAdult })`.
        - 미인증(`NEED`): 용도별 한 줄(「포인트 충전하려면 본인인증이 필요해요」) + 왜·무엇을 저장하는지 짧게 + [본인인증 하기]. 인증이 실패하면 대화상자 안 한 줄(사용자가 인증창을 닫으면 조용히 그대로).
        - 만 19세 미만(`MINOR`): 막는 안내 + 19세 생일이 지나면 다시 인증하지 않아도 된다 + [확인].
        - 상태를 받지 못함(`LOAD_ERROR`): 서버 문구 + [다시 시도].
        인증 흐름이 진행 중이면(가짜 대화상자가 위에 떠 있는 동안 포함) 닫을 수 없다.
    -->
    <Dialog
        :visible="state.open"
        modal
        :draggable="false"
        :closable="!state.busy"
        :close-on-escape="!state.busy"
        :header="title"
        :style="{ width: 'min(26rem, calc(100vw - 2rem))' }"
        data-testid="identity-gate-dialog"
        @update:visible="(v: boolean) => !v && gate.close()"
    >
        <div class="flex flex-col gap-3 text-sm leading-relaxed">
            <template v-if="state.mode === 'NEED'">
                <p class="m-0">{{ t('identity.gateNeedText') }}</p>
                <p class="m-0 text-xs text-muted-color">{{ t('identity.storedShort') }}</p>
                <Message v-if="failureCopy" severity="error" size="small" variant="simple" :closable="false" data-testid="identity-gate-failure">
                    <b>{{ failureCopy.title }}</b> {{ failureCopy.text }}
                </Message>
            </template>

            <template v-else-if="state.mode === 'MINOR'">
                <p class="m-0">{{ t('identity.minorBlocked') }}</p>
                <p class="m-0 text-muted-color">{{ UtilIdentityMessages.minorRelease(t, state.adultFrom) }}</p>
            </template>

            <Message v-else severity="error" size="small" variant="simple" :closable="false">{{ state.loadError || t('identity.loadFailed') }}</Message>
        </div>

        <template #footer>
            <template v-if="state.mode === 'NEED'">
                <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="state.busy" @click="gate.close()" />
                <Button type="button" :label="t('identity.verifyButton')" icon="pi pi-mobile" :loading="state.busy" data-testid="identity-gate-verify" @click="gate.startVerify()" />
            </template>
            <template v-else-if="state.mode === 'MINOR'">
                <Button type="button" :label="t('common.confirm')" @click="gate.close()" />
            </template>
            <template v-else>
                <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="state.busy" @click="gate.close()" />
                <Button type="button" :label="t('common.retry')" :loading="state.busy" @click="gate.retryLoad()" />
            </template>
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import UtilIdentityMessages from '~/utils/util-identity-messages';

const { t } = useI18n();
const time = useDisplayTime();
const gate = useIdentityGate();
const state = gate.state;

const title = computed(() => {
    if (state.value.mode === 'MINOR') return t('identity.minorTitle');
    return t(`identity.gateNeed_${state.value.purpose}`);
});

const failureCopy = computed(() => (state.value.failure ? UtilIdentityMessages.failure(t, state.value.failure, time.value) : null));
</script>
