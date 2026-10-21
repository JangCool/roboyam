<template>
    <!--
        내 정보 > 로그인 이력 — 본인의 최근 로그인 시도 목록. 한 줄: 시각 · IP · 성공/실패(실패면 사유), 그 아래 접속 환경(브라우저 · 운영체제 · 기기 종류, 기기 아이콘).
        접속 환경은 서버가 로그인 감사 기록에 남긴 요약(`Chrome / Windows / Desktop` — 낱말은 영문 고정)을 **그대로** 보이고, 기기 낱말로 아이콘만 고른다(`UtilUserAgent.summaryDevice`).
        요약이 아닌 값(브라우저·OS 를 못 알아낸 정제 원문 앞 80글자)은 풀어 보고(`UtilUserAgent.parse`), 하나도 못 풀면 그 글자를 그대로 보인다. `agent` 가 null(UA 를 남기기 전의 옛 기록)이면 접속 환경 줄을 그리지 않는다.
        처음 열 때 한 번 받고 탭을 오가도 다시 받지 않는다(`useLoginHistory`). 행 리스트라 좁은 화면에서 줄이 감싸지고 환경 줄은 말줄임으로 자른다. 읽는 중·오류(다시 시도)·빈 결과를 모두 다룬다.
    -->
    <div aria-live="polite" :aria-busy="loading">
        <AppGadget :title="t('profile.loginHistory')" anchor="login-history">
            <div v-if="loading && !items" class="flex justify-center py-8">
                <ProgressSpinner style="width: 2.25rem; height: 2.25rem" :aria-label="t('common.loading')" />
            </div>

            <ErrorState v-else-if="error && !items" :title="t('profile.loginHistoryLoadFailed')" :description="error.message" retry :retrying="loading" @retry="reload()" />

            <EmptyState v-else-if="!items?.length" icon="pi pi-history" :title="t('profile.noLoginHistory')" :description="t('profile.noLoginHistoryBody')" />

            <ul v-else class="m-0 flex list-none flex-col divide-y divide-(--line) p-0">
                <li v-for="entry in rows" :key="entry.id" class="flex flex-col gap-1 py-2.5">
                    <div class="flex flex-wrap items-center justify-between gap-x-3 gap-y-1">
                        <span class="text-sm tabular-nums">{{ time.full(entry.at) }}</span>
                        <span class="ml-auto flex flex-wrap items-center justify-end gap-x-3 gap-y-1">
                            <span v-if="entry.result === 'FAIL' && entry.reason" class="text-xs text-muted-color">{{ UtilProfileMessages.loginReason(t, entry.reason) }}</span>
                            <span class="text-xs tabular-nums text-muted-color">{{ entry.ip ?? '-' }}</span>
                            <Tag :value="entry.result === 'OK' ? t('profile.signInOk') : t('profile.signInFail')" :severity="entry.result === 'OK' ? 'success' : 'danger'" />
                        </span>
                    </div>
                    <!-- 접속 환경 — 아이콘은 장식(`aria-hidden`)이고 의미는 글자가 말한다. 원문 UA 는 툴팁으로 -->
                    <p v-if="entry.agent" class="m-0 flex min-w-0 items-center gap-1.5 text-xs text-muted-color" :title="entry.raw || undefined" data-testid="login-agent">
                        <i :class="[UtilUserAgent.icon(entry.device), 'shrink-0']" aria-hidden="true" />
                        <span class="min-w-0 truncate">{{ entry.text }}</span>
                    </p>
                </li>
            </ul>
        </AppGadget>
    </div>
</template>

<script setup lang="ts">
import UtilProfileMessages from '~/utils/util-profile-messages';
import UtilUserAgent from '~/utils/util-user-agent';

const { t } = useI18n();
const time = useDisplayTime();
const { items, loading, error, reload, ensureLoaded } = useLoginHistory();

/** 줄마다 접속 환경을 한 번씩만 정해 둔다 — 언어가 바뀌면 문구가 다시 계산된다 */
const rows = computed(() =>
    (items.value ?? []).map((entry) => {
        // 서버 요약(`Chrome / Windows / Desktop`)이면 글자 그대로, 기기 낱말로 아이콘만 고른다
        const summaryDevice = UtilUserAgent.summaryDevice(entry.agent);
        if (summaryDevice) return { ...entry, device: summaryDevice, text: entry.agent!.trim(), raw: '' };
        const parsed = UtilUserAgent.parse(entry.agent);
        const known = parsed.browser !== null || parsed.os !== null || parsed.device !== 'unknown';
        const text = known ? UtilProfileMessages.agent(t, parsed) : (entry.agent ?? '').trim() || t('profile.agentUnknown');
        return { ...entry, device: parsed.device, text, raw: UtilUserAgent.truncate(entry.agent) };
    }),
);

// 탭을 처음 열 때 한 번 받는다(이미 받았으면 건너뛴다)
onMounted(() => void ensureLoaded());
</script>
