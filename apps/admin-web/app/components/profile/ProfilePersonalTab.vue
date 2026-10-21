<template>
    <!--
        내 정보 > 개인 설정 — hamonica 의 개인 설정 탭과 같은 구성이다:
          ① "내 표시 형식을 쓴다" 카드(스위치) ② 지금 무엇이 적용되는지 안내 ③ "언어 및 지역 · 시간 및 날짜 형식" 카드(공유 레이어의 `DisplayValueForm`) ④ 취소/저장 ⑤ "알림 설정" 카드(이메일 알림 수신 + 이벤트별 알림)
        표시 값은 두 층이다: **사이트 표시 기본값 ← 내 표시 형식**(「기본 설정」 = 일반 설정에서 저장한 사이트 표시 기본값, 서버가 `inherited` 로 준다). 켜면 내 값이 이기고, 꺼도 내 값은 남는다. 꺼 두면 값 입력은 잠기고 기본 설정 값을 읽기 전용으로 보인다.
        저장하면 응답의 효과값을 **바로** 화면에 적용한다(`useMyDisplay`). 화면 설정(색·밀도·메뉴 위치 등)은 이 탭이 아니라 화면 설정 서랍(상단 네비의 팔레트 단추)에서 바꾼다.
        알림 수신(이메일 · 이벤트별 받기)은 바꾸면 곧바로 저장한다. 구획 주소(`anchor`)는 우측 구역 내비가 쓴다 — 바뀐 칸 수·오류 칸 수를 구획마다 넘긴다.
    -->
    <div class="flex flex-col gap-4">
        <div v-if="loading && !view" class="flex justify-center py-8">
            <ProgressSpinner style="width: 2.25rem; height: 2.25rem" :aria-label="t('common.loading')" />
        </div>

        <ErrorState v-else-if="error && !view" :title="t('profile.displayLoadFailed')" :description="error.message" retry :retrying="loading" @retry="load()" />

        <template v-else-if="view">
            <AppGadget :title="t('profile.displayUseMine')" anchor="display-source" :changes="useChanged ? 1 : 0">
                <div class="flex flex-wrap items-start justify-between gap-3">
                    <p class="m-0 min-w-0 text-xs leading-relaxed text-muted-color">
                        <template v-if="use">
                            <strong class="text-color">{{ t('profile.displayMineOn') }}</strong>
                            {{ t('profile.displayMineOnTail') }}
                        </template>
                        <template v-else>
                            <strong class="text-color">{{ t('profile.displayMineOff') }}</strong>
                            {{ t('profile.displayMineOffTail') }}
                        </template>
                    </p>
                    <ToggleSwitch v-model="use" class="shrink-0" :disabled="saving" :aria-label="t('profile.displayUseMine')" />
                </div>
            </AppGadget>

            <Message severity="info" :closable="false" size="small">
                <i18n-t keypath="profile.displayAppliedNow" tag="span" scope="global">
                    <template #from>
                        <strong>{{ fromLabel }}</strong>
                    </template>
                </i18n-t>
            </Message>

            <AppGadget :title="t('profile.displaySection')" anchor="locale-format" :changes="valueChanges" :invalid="invalidCount">
                <!-- 꺼져 있을 때는 물려받는 값(사이트 표시 기본값)을 읽기 전용으로 보인다 — 내 값을 그리면 위에서 바꾼 것이 화면에 안 비친다 -->
                <DisplayValueForm v-model="shown" class="display-form-sm" :errors="fieldErrors" :disabled="!use || saving" />
            </AppGadget>

            <div class="flex flex-wrap items-center justify-end gap-2">
                <Button type="button" :label="t('common.cancel')" severity="secondary" variant="outlined" :disabled="saving || !dirty" @click="reset" />
                <Button type="button" :label="t('common.save')" icon="pi pi-check" :loading="saving" :disabled="!dirty" @click="save" />
            </div>
        </template>

        <AppGadget :title="t('profile.notifications')" anchor="notification">
            <div class="flex items-center justify-between gap-4 py-1.5">
                <div>
                    <label :for="emailId" class="text-sm font-medium">{{ t('profile.emailNotification') }}</label>
                    <p class="m-0 mt-0.5 text-xs text-muted-color">{{ t('profile.emailNotificationHint') }}</p>
                </div>
                <!-- 바꾸면 곧바로 저장한다. 읽는 중·저장 중·못 읽었을 때는 잠근다 -->
                <ToggleSwitch :input-id="emailId" :model-value="emailEnabled" :disabled="emailLocked" @update:model-value="changeEmail" />
            </div>

            <!-- 이벤트별 알림 — 내가 볼 수 있는 이벤트마다 받을지 정한다(켜 두면 받는다, 끄면 그 이벤트의 알림이 서랍·메일에 오지 않는다). 바꾸면 곧바로 저장하고, 필수(보안) 이벤트는 끌 수 없다 -->
            <div class="mt-3 border-t border-(--line) pt-4">
                <h3 class="text-sm font-medium">{{ t('profile.events') }}</h3>
                <p class="m-0 mt-0.5 text-xs text-muted-color">{{ t('profile.eventsHint') }}</p>

                <div v-if="eventsLoading && !events" class="mt-3 flex flex-col gap-2" aria-busy="true">
                    <span class="sr-only">{{ t('common.loading') }}</span>
                    <Skeleton v-for="n in 4" :key="n" height="2.25rem" />
                </div>

                <div v-else-if="eventsError" class="mt-3 flex flex-wrap items-center gap-x-3 gap-y-2" role="alert">
                    <Message severity="error" variant="simple" size="small">{{ t('profile.eventsLoadFailed') }} {{ eventsError.message }}</Message>
                    <Button type="button" :label="t('common.retry')" icon="pi pi-refresh" size="small" severity="secondary" variant="outlined" :loading="eventsLoading" @click="loadEvents()" />
                </div>

                <p v-else-if="!events?.length" class="m-0 mt-3 text-xs text-muted-color">{{ t('profile.eventsEmpty') }}</p>

                <div v-else class="mt-3 flex flex-col gap-4">
                    <section v-for="group in eventGroups" :key="group.area" :aria-labelledby="`${eventsId}-${group.area}`">
                        <h4 :id="`${eventsId}-${group.area}`" class="m-0 flex items-center gap-1.5 text-xs font-semibold text-muted-color">
                            <i v-if="UtilNotification.areaIcon(group.area)" :class="UtilNotification.areaIcon(group.area)" class="text-[11px]" aria-hidden="true" />
                            {{ UtilNotificationMessages.areaLabel(t, group.area) }}
                        </h4>
                        <ul class="m-0 mt-1 list-none p-0">
                            <li v-for="event in group.items" :key="event.code" class="flex items-start justify-between gap-4 border-b border-(--line) py-2.5 last:border-b-0">
                                <div class="min-w-0">
                                    <div class="flex flex-wrap items-center gap-x-2 gap-y-1">
                                        <label :for="`${eventsId}-${event.code}`" class="text-sm font-medium">{{ event.name }}</label>
                                        <NotificationSeverityTag :kind="event.severity" />
                                    </div>
                                    <p class="m-0 mt-0.5 text-xs text-muted-color">{{ event.description }}</p>
                                    <!-- 필수 이벤트는 끌 수 없다 — 잠금 표시와 이유를 보인다 -->
                                    <p v-if="event.mandatory" class="m-0 mt-1 flex items-center gap-1 text-xs text-muted-color">
                                        <i class="pi pi-lock text-[11px]" aria-hidden="true" />
                                        {{ t('profile.eventMandatory') }}
                                    </p>
                                </div>
                                <!-- 켜짐 = 받는다. 필수 이벤트는 켜진 채 잠긴다 -->
                                <ToggleSwitch
                                    class="shrink-0"
                                    :input-id="`${eventsId}-${event.code}`"
                                    :model-value="!event.muted"
                                    :disabled="event.mandatory || isEventSaving(event.code)"
                                    @update:model-value="(receive: boolean) => setMuted(event, !receive)"
                                />
                            </li>
                        </ul>
                    </section>
                </div>
            </div>
        </AppGadget>
    </div>
</template>

<script setup lang="ts">
import UtilDisplayForm, { type DisplayFieldErrors } from '#layers/display-time/utils/util-display-form';
import UtilDisplayValues from '#layers/display-time/utils/util-display-values';
import type { DisplayValues } from '~/composables/profile/api/useProfileApi.types';
import UtilNotification from '~/utils/util-notification';
import UtilNotificationMessages from '~/utils/util-notification-messages';

const { t } = useI18n();
const toast = useAppToast();
const { view, loading, error, load, save: saveDisplay } = useMyDisplay();
const { emailEnabled, locked: emailLocked, load: loadEmail, change: changeEmail } = useMyNotification();
const { events, loading: eventsLoading, error: eventsError, isSaving: isEventSaving, load: loadEvents, setMuted } = useMyNotificationEvents();

const emailId = useId();
const eventsId = useId();

/** 영역별 묶음 — 영역 차례는 `UtilNotification.AREA_ORDER` */
const eventGroups = computed(() => UtilNotification.groupByArea(events.value ?? []));

/** 폼의 값 — 받아 온 내 값에서 시작한다. 꺼 둔 동안에도 값은 남는다 */
const use = ref(false);
const values = ref<DisplayValues | null>(null);
const saving = ref(false);
const fieldErrors = ref<DisplayFieldErrors>({});

/** 폼에 그릴 값 — 켜져 있으면 내 값, 꺼져 있으면 물려받는 값(사이트 표시 기본값) */
const shown = computed<DisplayValues>({
    get: () => (use.value ? (values.value ?? (view.value as NonNullable<typeof view.value>).values) : (view.value as NonNullable<typeof view.value>).inherited),
    set: (next) => {
        values.value = next;
    },
});

/** 지금 화면에 적용되는 값의 출처 글자 */
const fromLabel = computed(() => (view.value?.effectiveFrom === 'USER' ? t('profile.displayFromUser') : t('profile.displayFromSystem')));

const FIELDS = ['language', 'zoneId', 'timeFormat', 'dateFormat'] as const;
const useChanged = computed(() => view.value !== undefined && use.value !== view.value.use);
/** 값 네 칸 중 받아 온 값과 달라진 칸 수 */
const valueChanges = computed(() => (view.value && values.value ? FIELDS.filter((field) => values.value?.[field] !== view.value?.values[field]).length : 0));
const invalidCount = computed(() => Object.keys(fieldErrors.value).length);
const dirty = computed(() => useChanged.value || valueChanges.value > 0);

function fill(): void {
    if (!view.value) return;
    use.value = view.value.use;
    values.value = UtilDisplayValues.copy(view.value.values);
    fieldErrors.value = {};
}

function reset(): void {
    fill();
}

// 받아 온 값이 (다시) 오면 폼을 그 값으로 채운다 — 저장하는 동안은 건드리지 않는다
watch(
    view,
    () => {
        if (!saving.value) fill();
    },
    { immediate: true },
);

onMounted(() => {
    void load();
    // 메일 스위치는 표시 값과 무관하다 — 기다리지 않고 따로 읽는다
    void loadEmail();
    // 이벤트별 목록도 따로 읽는다 — 한쪽이 실패해도 다른 쪽은 보인다
    void loadEvents();
});

async function save(): Promise<void> {
    if (saving.value || !dirty.value || !values.value) return;
    saving.value = true;
    fieldErrors.value = {};
    try {
        // 저장하자마자 화면에 먹인다(`useMyDisplay.save` 가 응답의 효과값을 적용한다) — 저장은 됐는데 화면이 안 바뀌면 사람은 자기가 뭘 잘못했나 찾는다
        const res = await saveDisplay(use.value, values.value);
        toast.success(res.use ? t('profile.displayMineSaved') : t('profile.displayMineOffSaved'));
    } catch (e) {
        fieldErrors.value = UtilDisplayForm.fieldErrors(e);
        if (Object.keys(fieldErrors.value).length === 0) toast.error(e instanceof Error && e.message ? e.message : t('common.failed'));
    } finally {
        saving.value = false;
    }
}
</script>

<style scoped>
/*
 * 공유 레이어(`@roboyam/display-time`)의 `DisplayValueForm` 은 user-web 과 함께 쓰므로 레이어는 고치지 않고 이 화면에서만 글자를 줄인다(하모니카3 기준).
 * 항목 이름(언어·시간대·시간 형식·날짜 형식)은 12px 볼드 · 기본 글자색, 라디오 선택지 글자는 13px.
 */
.display-form-sm :deep(label:not(.cursor-pointer)),
.display-form-sm :deep(legend) {
    font-size: 0.75rem;
    font-weight: 700;
    color: var(--p-text-color);
}
.display-form-sm :deep(label.cursor-pointer) {
    font-size: 0.8125rem;
}
/* 라디오 줄 — 한 줄 높이 22px · 줄 간격 4px 로 26px 간격(하모니카3 와 같은 여유). 레이어 기본은 19px · 6px 라 빽빽해 보인다 */
.display-form-sm :deep(fieldset > div) {
    row-gap: 0.25rem;
}
.display-form-sm :deep(fieldset > div > div) {
    min-height: 1.375rem;
}
</style>
