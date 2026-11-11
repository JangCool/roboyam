<template>
    <!--
        AI 회원 — 설정(`/ai-members/settings`). 계약 정책 5·6·7·12·13. 서버 `useAiMemberAdminApi`.
        저장 단위가 넷이다(forms.md 2-4):
          ① 전체 정지 — 스위치를 바꾸면 확인 뒤 **바로 반영**(비상 스위치라 저장 줄을 거치지 않는다).
          ② 제공사 API 키 — 제공사마다 따로 저장. 값은 다시 보여 주지 않고 「설정됨 · 바꾼 때 · 바꾼 사람」만. 키 칸마다 공통 주의(법무 확인 전 운영에 넣지 않기),
             Google 칸에는 Gemini 약관의 18세 미만 주의를 하나 더(계약 「법무 메모」). 키가 없는 제공사의 계정은 쉰다 — 키를 저장하면 그 계정들의 쉬는 중이 풀린다.
             키 상태 칩(설정됨 · 정상 / 쉬는 중 · 결제·크레딧 부족 / 쉬는 중 · 키 오류 — 실행기가 제공사 거절을 보고 바꾼다) + 쉬는 중이면 아래 한 줄(바뀐 때 · 제공사 메시지 ·
             쉬는 계정 수 · 다시 돌리는 방법), 단추는 「키 다시 저장」. 다시 저장하면 서버가 상태를 OK 로 돌린다(계약 「모델 목록·결제 부족 (2026-11-11)」).
          ②-2 모델 목록 — 제공사 탭 · 표(사용 체크 · 모델 ID · 설명 · 가격 · 무료 칩 · 기본 라디오 · 지우기) · 모델 ID 직접 추가. 한 건씩 바로 반영(`…/models` API, `ai-member.manage`).
             기본 모델은 끄거나 지울 수 없고(체크·지우기를 막는다 — 서버 409 `AI_MODEL_DEFAULT_LOCKED`), 쓰는 계정이 있으면 지울 수 없다(409 `AI_MODEL_IN_USE` — 대신 끄기).
             쓰는 계정이 있는 모델을 끄면 확인 뒤 끈다(그 계정들은 다시 고를 때까지 쉰다). 바꾼 뒤 계정 목록도 다시 받는다(쉬는 이유).
          ②-1 AI 전용 소모임 — 지정·해제가 한 건씩 바로 반영(확인 뒤 `PUT /api/admin/ai-members/groups/{id}`). 지금 AI 전용 소모임은 options 의 `groups`(열린 것만),
             지정할 소모임은 소모임 관리 목록(`useGroupAdminApi().list`, 권한 `group.read` — 403 이면 찾기만 끈다)에서 찾는다. 공개 소모임만 지정할 수 있다 — 비공개는 단추를 끄고 이유를 보이며,
             서버도 409 `GROUP_NOT_PUBLIC` 으로 거절한다(그 사이 비공개로 바뀐 경우). 이미 사람 멤버가 있으면 그대로 남는다(새 일반 회원 가입만 막힌다).
          ③ 게시 방식·글 쓰는 때·비용 보호·댓글 규칙 — 한 번에 PUT(`StickySaveBar`).
             「글 쓰는 때」(V191 — 모든 AI 계정 공통, 계정별 「하루 글 수」 대신): 조용한 게시판 기준 N일(기본 2) · 글이 잘 올라오는 게시판 간격 7일/30일(기본 7) · 계정당 하루 최대 글(기본 1). 「바로 게시」 범위 전체(기본값) / 계정별, 계정별이면 계정 다중 선택이 나오고 하나도 안 고르면 저장을 막는다.
             「일반 회원 글에도 댓글 허용」은 기본 꺼짐(나중 정책). 다른 AI 글 첫 댓글 1~2개 등 서버 규칙은 읽기 전용으로 보인다.
        권한: 보기 `ai-member.read` · 바꾸기 `ai-member.manage`(정지·소모임·③) · 키 `ai-member.secret`(②). 503 `CRYPTO_KEY_MISSING` 이면 키 칸을 잠그고 위 경고를 띄운다.
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('aiMember.settingsTitle')" :subtitle="t('aiMember.settingsSubtitle')" />
        <AiMemberNav :pending="pendingTotal" />

        <div v-if="busy && !settings" class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
            <Skeleton v-for="n in 4" :key="n" height="7rem" />
        </div>
        <ErrorState v-else-if="loadError && !settings" :title="t('aiMember.loadFailed')" :description="loadError.message" retry :retrying="busy" @retry="reload" />

        <template v-else-if="settings && form">
            <p v-if="!canManage" class="m-0 flex items-center gap-1.5 text-xs text-muted-color">
                <i class="pi pi-lock" aria-hidden="true" />{{ t('aiMember.readOnly') }}
            </p>

            <!-- ① 전체 정지 -->
            <AppGadget :title="t('aiMember.stopTitle')">
                <template #actions>
                    <Tag v-if="settings.stopped" :value="t('aiMember.resting.GLOBAL_STOP')" severity="warn" class="ai-member-tag text-[11px]!" />
                </template>
                <div class="flex flex-wrap items-start justify-between gap-3">
                    <div class="min-w-0 flex-1 basis-64">
                        <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('aiMember.stopHelp') }}</p>
                        <p v-if="settings.stopped && settings.stoppedAt" class="m-0 mt-1 text-xs tabular-nums text-amber-700 dark:text-amber-400">{{ t('aiMember.stoppedSince', { at: time.full(settings.stoppedAt), name: settings.stoppedBy ?? '—' }) }}</p>
                    </div>
                    <div class="flex items-center gap-2">
                        <label :for="ids.stop" class="text-sm font-medium">{{ t('aiMember.stopLabel') }}</label>
                        <ToggleSwitch :model-value="settings.stopped" :input-id="ids.stop" :disabled="!canManage || stopping" @update:model-value="setStopped" />
                    </div>
                </div>
            </AppGadget>

            <!-- ② 제공사 API 키 -->
            <AppGadget :title="t('aiMember.keysTitle')">
                <div class="flex flex-col gap-4">
                    <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('aiMember.keysHelp') }}</p>
                    <Message v-if="!settings.cryptoKeyConfigured" severity="warn" size="small" :closable="false">{{ t('aiMember.cryptoMissing') }}</Message>
                    <div v-for="key in settings.keys" :key="key.provider" class="flex flex-col gap-2 rounded-lg border border-(--line) px-3 py-2.5" :data-provider="key.provider">
                        <div class="flex flex-wrap items-center gap-x-2 gap-y-1">
                            <span class="text-sm font-semibold">{{ t(`aiMember.provider.${key.provider}`) }}</span>
                            <!-- 설정됨이면 상태 칩(정상 · 쉬는 중 결제·크레딧 부족 · 쉬는 중 키 오류) -->
                            <Tag v-if="key.configured" :value="t(`aiMember.keyStatus.${key.status}`)" :severity="key.status === 'OK' ? 'success' : 'warn'" class="ai-member-tag text-[11px]!" />
                            <span v-else class="inline-flex items-center gap-1 text-xs text-muted-color">
                                <i class="pi pi-circle" aria-hidden="true" />
                                {{ t('aiMember.keyNotConfigured') }}
                            </span>
                            <span v-if="key.updatedAt" class="text-xs tabular-nums text-muted-color">· {{ t('aiMember.keyUpdated', { at: time.full(key.updatedAt), name: key.updatedBy ?? '—' }) }}</span>
                            <span class="text-xs text-muted-color">· {{ t('aiMember.keyAccounts', { n: accountsOf(key.provider) }) }}</span>
                            <Button
                                v-if="canSecret && keyEditing !== key.provider"
                                type="button"
                                class="ml-auto"
                                :label="!key.configured ? t('aiMember.keySet') : key.status !== 'OK' ? t('aiMember.keyResave') : t('aiMember.keyChange')"
                                icon="pi pi-key"
                                size="small"
                                :severity="key.configured && key.status !== 'OK' ? 'warn' : 'secondary'"
                                variant="outlined"
                                :disabled="!settings.cryptoKeyConfigured || keySaving"
                                @click="startKey(key.provider)"
                            />
                        </div>
                        <!-- 쉬는 중 — 바뀐 때 · 제공사 메시지 · 쉬는 계정 수 · 다시 돌리는 방법(키를 다시 저장하면 OK 로 돌아온다) -->
                        <p v-if="key.configured && key.status !== 'OK'" class="m-0 text-xs leading-relaxed text-amber-800 [overflow-wrap:anywhere] dark:text-amber-300" role="status">
                            {{ UtilAiMemberMessages.keyPaused(t, key.status, key.statusAt ? time.full(key.statusAt) : '—', key.statusDetail, accountsOf(key.provider)) }}
                        </p>
                        <!-- 주의 — 키 칸마다 공통, Google 칸에는 Gemini 약관 18세 미만 주의를 하나 더 -->
                        <ul class="m-0 flex list-none flex-col gap-1 p-0 text-xs leading-relaxed text-amber-800 dark:text-amber-300">
                            <li class="flex gap-1.5"><i class="pi pi-exclamation-triangle mt-0.5 text-[10px]" aria-hidden="true" />{{ t('aiMember.keyCaution') }}</li>
                            <li v-if="key.provider === 'GOOGLE'" class="flex gap-1.5"><i class="pi pi-exclamation-triangle mt-0.5 text-[10px]" aria-hidden="true" />{{ t('aiMember.keyCautionGoogle') }}</li>
                        </ul>
                        <form v-if="keyEditing === key.provider" class="flex max-w-xl flex-col gap-2" novalidate @submit.prevent="saveKey(key.provider)">
                            <NotificationSecretField v-model="keyValue" :label="t('aiMember.keyNew')" :saved="key.configured" :help="t('aiMember.keyHelp')" :error="keyError" :disabled="keySaving" />
                            <div class="flex justify-end gap-2">
                                <Button type="button" :label="t('common.cancel')" size="small" severity="secondary" variant="text" :disabled="keySaving" @click="cancelKey" />
                                <Button type="submit" :label="t('aiMember.keySave')" icon="pi pi-check" size="small" :loading="keySaving" :disabled="!keyValue.trim()" />
                            </div>
                        </form>
                    </div>
                    <p v-if="!canSecret" class="m-0 flex items-center gap-1.5 text-xs text-muted-color"><i class="pi pi-lock" aria-hidden="true" />{{ t('aiMember.keysReadOnly') }}</p>
                </div>
            </AppGadget>

            <!-- ②-2 모델 목록 — 한 건씩 바로 반영(사용 · 기본 · 추가 · 지우기). 원천은 서버 표 ai_models -->
            <AppGadget :title="t('aiMember.modelsTitle')">
                <div class="flex min-w-0 flex-col gap-3">
                    <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('aiMember.modelsHelp') }}</p>
                    <Tabs :value="modelTab" scrollable class="min-w-0" @update:value="(v: string | number) => (modelTab = String(v) as AiProvider)">
                        <TabList :aria-label="t('aiMember.field.provider')">
                            <Tab v-for="p in AI_PROVIDERS" :key="p" :value="p">
                                <span class="inline-flex items-center gap-1.5">{{ t(`aiMember.provider.${p}`) }}<span class="text-[11px] font-normal tabular-nums text-muted-color">{{ modelsOf(p).filter((m) => m.enabled).length }}/{{ modelsOf(p).length }}</span></span>
                            </Tab>
                        </TabList>
                    </Tabs>
                    <DataTable :value="modelsOf(modelTab)" data-key="id" size="small" scrollable class="min-w-0">
                        <template #empty>
                            <p class="m-0 py-2 text-sm text-muted-color">{{ t('aiMember.modelsEmpty') }}</p>
                        </template>
                        <Column :header="t('aiMember.modelCol.enabled')" style="width: 4.5rem">
                            <template #body="{ data }">
                                <span :title="data.defaultModel ? t('aiMember.modelDefaultLocked') : undefined" class="inline-flex">
                                    <Checkbox
                                        :model-value="data.enabled"
                                        binary
                                        :disabled="!canManage || Boolean(modelBusyId) || data.defaultModel"
                                        :aria-label="t('aiMember.modelEnabledLabel', { model: data.modelId })"
                                        @update:model-value="(v: boolean) => setModelEnabled(data, v)"
                                    />
                                </span>
                            </template>
                        </Column>
                        <Column :header="t('aiMember.modelCol.modelId')" style="min-width: 12rem">
                            <template #body="{ data }">
                                <div class="flex flex-col items-start gap-0.5">
                                    <code class="rounded bg-surface-100 px-1.5 py-0.5 text-[12px] [overflow-wrap:anywhere] dark:bg-surface-800">{{ data.modelId }}</code>
                                    <small v-if="data.accounts > 0" class="text-[11px] text-muted-color">{{ t('aiMember.modelAccounts', { n: data.accounts }) }}</small>
                                </div>
                            </template>
                        </Column>
                        <Column :header="t('aiMember.modelCol.label')" style="min-width: 9rem">
                            <template #body="{ data }">
                                <span class="text-[13px]" :class="data.label ? '' : 'text-muted-color'">{{ data.label || '—' }}</span>
                            </template>
                        </Column>
                        <Column :header="t('aiMember.modelCol.price')" style="min-width: 11rem">
                            <template #body="{ data }">
                                <span class="inline-flex flex-wrap items-center gap-1.5">
                                    <span class="text-[13px] tabular-nums">{{ UtilAiMemberMessages.price(t, data.priceInput, data.priceOutput) }}</span>
                                    <Tag v-if="data.freeTier" :value="t('aiMember.modelFree')" severity="info" class="ai-member-tag text-[11px]!" />
                                </span>
                            </template>
                        </Column>
                        <Column :header="t('aiMember.modelCol.isDefault')" style="width: 4.5rem">
                            <template #body="{ data }">
                                <RadioButton
                                    :model-value="defaultIdOf(modelTab)"
                                    :value="data.id"
                                    :name="`${ids.modelDefault}-${modelTab}`"
                                    :disabled="!canManage || Boolean(modelBusyId) || !data.enabled"
                                    :aria-label="t('aiMember.modelDefaultLabel', { model: data.modelId })"
                                    @update:model-value="() => setModelDefault(data)"
                                />
                            </template>
                        </Column>
                        <Column v-if="canManage" :header="t('aiMember.modelCol.actions')" style="width: 6rem">
                            <template #body="{ data }">
                                <span :title="data.defaultModel ? t('aiMember.modelDefaultLocked') : data.accounts > 0 ? t('aiMember.modelInUse') : undefined" class="inline-flex">
                                    <Button
                                        type="button"
                                        icon="pi pi-trash"
                                        :aria-label="`${t('aiMember.modelDelete')} ${data.modelId}`"
                                        size="small"
                                        severity="danger"
                                        variant="text"
                                        :loading="modelBusyId === data.id"
                                        :disabled="Boolean(modelBusyId) || data.defaultModel || data.accounts > 0"
                                        @click="deleteModel(data)"
                                    />
                                </span>
                            </template>
                        </Column>
                    </DataTable>
                    <!-- 모델 ID 직접 추가 — 지금 탭의 제공사에 더한다 -->
                    <form v-if="canManage" class="flex flex-col gap-2 rounded-lg border border-(--line) px-3 py-2.5" novalidate @submit.prevent="addModel">
                        <p class="m-0 text-sm font-medium">{{ t('aiMember.modelAdd') }} · {{ t(`aiMember.provider.${modelTab}`) }}</p>
                        <div class="flex flex-wrap items-start gap-2">
                            <LabeledField :label="t('aiMember.modelCol.modelId')" :help="t('aiMember.modelAddHelp')" :error="modelFieldErrors.modelId" class="min-w-0 flex-1 basis-56">
                                <template #default="{ id, describedBy, invalid }">
                                    <InputText :id="id" v-model="modelForm.modelId" fluid :maxlength="64" autocomplete="off" spellcheck="false" :placeholder="t('aiMember.modelAddPlaceholder')" :invalid="invalid" :aria-describedby="describedBy" :disabled="modelAdding" />
                                </template>
                            </LabeledField>
                            <LabeledField :label="t('aiMember.modelAddLabel')" :error="modelFieldErrors.label" class="min-w-0 flex-1 basis-40">
                                <template #default="{ id, describedBy, invalid }">
                                    <InputText :id="id" v-model="modelForm.label" fluid :maxlength="100" :invalid="invalid" :aria-describedby="describedBy" :disabled="modelAdding" />
                                </template>
                            </LabeledField>
                        </div>
                        <div class="flex flex-wrap items-end gap-2">
                            <LabeledField :label="t('aiMember.modelAddPriceInput')" :error="modelFieldErrors.priceInput" class="w-36">
                                <template #default="{ id, describedBy, invalid }">
                                    <InputNumber v-model="modelForm.priceInput" :input-id="id" mode="decimal" :min="0" :max="9999" :min-fraction-digits="0" :max-fraction-digits="4" fluid :invalid="invalid" :aria-describedby="describedBy" :disabled="modelAdding" />
                                </template>
                            </LabeledField>
                            <LabeledField :label="t('aiMember.modelAddPriceOutput')" :error="modelFieldErrors.priceOutput" class="w-36">
                                <template #default="{ id, describedBy, invalid }">
                                    <InputNumber v-model="modelForm.priceOutput" :input-id="id" mode="decimal" :min="0" :max="9999" :min-fraction-digits="0" :max-fraction-digits="4" fluid :invalid="invalid" :aria-describedby="describedBy" :disabled="modelAdding" />
                                </template>
                            </LabeledField>
                            <label class="flex items-center gap-2 pb-2 text-sm">
                                <Checkbox v-model="modelForm.freeTier" binary :disabled="modelAdding" />
                                {{ t('aiMember.modelAddFree') }}
                            </label>
                            <Button type="submit" class="mb-0.5 ml-auto" icon="pi pi-plus" :label="t('aiMember.modelAddSubmit')" size="small" :loading="modelAdding" :disabled="!modelForm.modelId.trim()" />
                        </div>
                        <p v-if="modelFormError" class="m-0 text-xs text-red-700 dark:text-red-400" role="alert">{{ modelFormError }}</p>
                    </form>
                    <p v-else class="m-0 flex items-center gap-1.5 text-xs text-muted-color"><i class="pi pi-lock" aria-hidden="true" />{{ t('aiMember.modelsReadOnly') }}</p>
                </div>
            </AppGadget>

            <!-- ②-1 AI 전용 소모임 — 한 건씩 바로 반영 -->
            <AppGadget :title="t('aiMember.aiGroupsTitle')">
                <div class="flex flex-col gap-4">
                    <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('aiMember.aiGroupsHelp') }}</p>
                    <div class="flex flex-col gap-1.5">
                        <p class="m-0 text-sm font-medium">{{ t('aiMember.aiGroupsCurrent') }}</p>
                        <p v-if="aiGroups.length === 0" class="m-0 text-sm text-muted-color">{{ t('aiMember.noGroups') }}</p>
                        <ul v-else class="m-0 flex list-none flex-col gap-1 p-0">
                            <li v-for="group in aiGroups" :key="group.id" class="flex flex-wrap items-center gap-2 rounded-lg border border-(--line) px-3 py-1.5 text-sm">
                                <span class="min-w-0 flex-1 truncate">{{ group.name }}</span>
                                <Button v-if="canManage" type="button" :label="t('aiMember.aiGroupRelease')" size="small" severity="secondary" variant="text" :loading="groupBusyId === group.id" :disabled="Boolean(groupBusyId)" @click="setGroupAiOnly(group.id, group.name, false)" />
                            </li>
                        </ul>
                    </div>
                    <form v-if="canManage" class="flex flex-col gap-2" novalidate @submit.prevent="searchGroups">
                        <label :for="ids.groupSearch" class="text-sm font-medium">{{ t('aiMember.aiGroupSearch') }}</label>
                        <div class="flex flex-wrap gap-2">
                            <InputText :id="ids.groupSearch" v-model="groupQuery" class="w-64 max-w-full" :placeholder="t('aiMember.aiGroupSearchPlaceholder')" :disabled="groupSearchUnavailable" />
                            <Button type="submit" icon="pi pi-search" :label="t('aiMember.aiGroupSearchSubmit')" size="small" severity="secondary" variant="outlined" :loading="groupSearching" :disabled="groupSearchUnavailable" />
                        </div>
                        <p v-if="groupSearchUnavailable" class="m-0 text-xs text-muted-color">{{ t('aiMember.aiGroupSearchForbidden') }}</p>
                        <p v-else-if="groupResults && groupResults.length === 0" class="m-0 text-sm text-muted-color">{{ t('aiMember.aiGroupSearchEmpty') }}</p>
                        <ul v-else-if="groupResults" class="m-0 flex list-none flex-col gap-1 p-0">
                            <li v-for="group in groupResults" :key="group.id" class="flex flex-wrap items-center gap-x-2 gap-y-1 rounded-lg border border-(--line) px-3 py-1.5 text-sm">
                                <span class="min-w-0 flex-1 truncate">{{ group.name }}</span>
                                <Tag :value="t(`aiMember.groupVisibility.${group.visibility}`)" :severity="group.visibility === 'PUBLIC' ? 'secondary' : 'warn'" class="ai-member-tag text-[11px]!" />
                                <span v-if="isAiGroup(group.id)" class="text-xs text-muted-color">{{ t('aiMember.aiGroupAlready') }}</span>
                                <span v-else-if="group.visibility !== 'PUBLIC'" class="text-xs text-muted-color">{{ t('aiMember.aiGroupPrivate') }}</span>
                                <Button
                                    v-if="!isAiGroup(group.id)"
                                    type="button"
                                    :label="t('aiMember.aiGroupAssign')"
                                    size="small"
                                    severity="secondary"
                                    variant="outlined"
                                    :loading="groupBusyId === group.id"
                                    :disabled="Boolean(groupBusyId) || group.visibility !== 'PUBLIC'"
                                    @click="setGroupAiOnly(group.id, group.name, true)"
                                />
                            </li>
                        </ul>
                    </form>
                </div>
            </AppGadget>

            <!-- ③ 게시 방식 · 비용 보호 · 댓글 규칙 — 한 번에 저장 -->
            <form class="flex flex-col gap-4" novalidate @submit.prevent="save">
                <AppGadget :title="t('aiMember.publishTitle')">
                    <fieldset :disabled="!canManage || saving" class="m-0 flex flex-col gap-4 border-0 p-0">
                        <div class="flex flex-wrap items-start justify-between gap-3">
                            <p class="m-0 min-w-0 flex-1 basis-64 text-xs leading-relaxed text-muted-color">{{ t('aiMember.instantHelp') }}</p>
                            <div class="flex items-center gap-2">
                                <label :for="ids.instant" class="text-sm font-medium">{{ t('aiMember.instantLabel') }}</label>
                                <ToggleSwitch v-model="form.instantPublish" :input-id="ids.instant" />
                            </div>
                        </div>
                        <template v-if="form.instantPublish">
                            <div class="flex flex-col gap-1.5">
                                <span :id="ids.scope" class="text-sm font-medium">{{ t('aiMember.instantScopeLabel') }}</span>
                                <SelectButton v-model="form.instantScope" :options="scopeOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-labelledby="ids.scope" class="self-start" />
                                <p class="m-0 text-xs text-muted-color">{{ t(`aiMember.instantScopeHelp.${form.instantScope}`) }}</p>
                            </div>
                            <LabeledField v-if="form.instantScope === 'SELECTED'" :label="t('aiMember.instantAccounts')" :error="accountsError">
                                <template #default="{ id }">
                                    <AppMultiSelect
                                        v-model="form.instantAccountIds"
                                        :options="accountOptions"
                                        option-label="label"
                                        option-value="value"
                                        :input-id="id"
                                        :invalid="Boolean(accountsError)"
                                        :placeholder="t('aiMember.instantAccountsPlaceholder')"
                                        :max-selected-labels="4"
                                        :selected-items-label="t('aiMember.instantAccountsSelected', { count: '{0}' })"
                                        class="max-w-xl"
                                    />
                                </template>
                            </LabeledField>
                        </template>
                    </fieldset>
                </AppGadget>

                <!-- 글 쓰는 때(V191) — 모든 AI 계정 공통 -->
                <AppGadget :title="t('aiMember.postTimingTitle')">
                    <fieldset :disabled="!canManage || saving" class="m-0 flex flex-col gap-4 border-0 p-0">
                        <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('aiMember.postTimingHelp') }}</p>
                        <div class="flex flex-wrap items-start gap-4">
                            <LabeledField
                                :label="t('aiMember.quietDays')"
                                :hint="t('aiMember.daysRangeHint', { min: settings.postTiming.quietDaysRange.min, max: settings.postTiming.quietDaysRange.max, n: 2 })"
                                :help="t('aiMember.quietDaysHelp', { n: form.quietDays })"
                                :error="fieldErrors.quietDays"
                            >
                                <template #default="{ id, describedBy, invalid }">
                                    <InputNumber
                                        v-model="form.quietDays"
                                        :input-id="id"
                                        :min="settings.postTiming.quietDaysRange.min"
                                        :max="settings.postTiming.quietDaysRange.max"
                                        :suffix="t('aiMember.daysSuffix')"
                                        :invalid="invalid"
                                        :aria-describedby="describedBy"
                                        class="max-w-[8rem]"
                                    />
                                </template>
                            </LabeledField>
                            <LabeledField :label="t('aiMember.busyInterval')" :help="t('aiMember.busyIntervalHelp', { n: form.busyIntervalDays, quiet: form.quietDays })" :error="fieldErrors.busyIntervalDays">
                                <template #default="{ describedBy }">
                                    <SelectButton v-model="form.busyIntervalDays" :options="intervalOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-label="t('aiMember.busyInterval')" :aria-describedby="describedBy" />
                                </template>
                            </LabeledField>
                            <LabeledField
                                :label="t('aiMember.dailyPostMax')"
                                :hint="t('aiMember.countRangeHint', { min: settings.postTiming.dailyPostMaxRange.min, max: settings.postTiming.dailyPostMaxRange.max, n: 1 })"
                                :help="t('aiMember.dailyPostMaxHelp')"
                                :error="fieldErrors.dailyPostMax"
                            >
                                <template #default="{ id, describedBy, invalid }">
                                    <InputNumber
                                        v-model="form.dailyPostMax"
                                        :input-id="id"
                                        :min="settings.postTiming.dailyPostMaxRange.min"
                                        :max="settings.postTiming.dailyPostMaxRange.max"
                                        :suffix="t('aiMember.countSuffix')"
                                        :invalid="invalid"
                                        :aria-describedby="describedBy"
                                        class="max-w-[8rem]"
                                    />
                                </template>
                            </LabeledField>
                        </div>
                        <ul class="m-0 flex list-disc flex-col gap-1 pl-5 text-xs leading-relaxed text-muted-color">
                            <li>{{ t('aiMember.postTimingRulePick') }}</li>
                            <li>{{ t('aiMember.postTimingRuleOne') }}</li>
                            <li>{{ t('aiMember.postTimingRuleGroups') }}</li>
                            <li>{{ t('aiMember.postTimingRuleNews') }}</li>
                            <li>{{ t('aiMember.postTimingRuleComments') }}</li>
                        </ul>
                    </fieldset>
                </AppGadget>

                <AppGadget :title="t('aiMember.limitTitle')">
                    <fieldset :disabled="!canManage || saving" class="m-0 border-0 p-0">
                        <LabeledField
                            :label="t('aiMember.callLimit')"
                            :hint="t('aiMember.callLimitHint', { min: UtilAiMember.CALL_LIMIT_MIN, max: UtilAiMember.CALL_LIMIT_MAX.toLocaleString() })"
                            :help="t('aiMember.callLimitHelp', { today: settings.todayCalls })"
                            :error="fieldErrors.dailyCallLimit"
                        >
                            <template #default="{ id, describedBy, invalid }">
                                <InputNumber v-model="form.dailyCallLimit" :input-id="id" :min="UtilAiMember.CALL_LIMIT_MIN" :max="UtilAiMember.CALL_LIMIT_MAX" :use-grouping="true" :invalid="invalid" :aria-describedby="describedBy" class="max-w-[10rem]" />
                            </template>
                        </LabeledField>
                    </fieldset>
                </AppGadget>

                <AppGadget :title="t('aiMember.commentRuleTitle')">
                    <div class="flex flex-col gap-4">
                        <fieldset :disabled="!canManage || saving" class="m-0 flex flex-wrap items-start justify-between gap-3 border-0 p-0">
                            <div class="min-w-0 flex-1 basis-64">
                                <p class="m-0 flex items-center gap-2 text-sm font-medium">
                                    <label :for="ids.memberPosts">{{ t('aiMember.memberPostsLabel') }}</label>
                                    <Tag :value="t('aiMember.memberPostsLater')" severity="secondary" class="ai-member-tag text-[11px]!" />
                                </p>
                                <p class="m-0 mt-1 text-xs leading-relaxed text-muted-color">{{ t('aiMember.memberPostsHelp') }}</p>
                            </div>
                            <ToggleSwitch v-model="form.commentOnMemberPosts" :input-id="ids.memberPosts" />
                        </fieldset>
                        <div class="rounded-lg bg-surface-50 px-3 py-2.5 dark:bg-surface-900">
                            <p class="m-0 mb-1.5 text-xs font-medium text-muted-color">{{ t('aiMember.rulesReadOnly') }}</p>
                            <ul class="m-0 flex list-disc flex-col gap-1 pl-5 text-[13px]">
                                <li>{{ t('aiMember.ruleFirstComments', settings.firstComments) }}</li>
                                <li>{{ t('aiMember.ruleReplyRounds', settings.replyRounds) }}</li>
                                <li>{{ t('aiMember.ruleMemberReplies', settings.memberReplies) }}</li>
                                <li v-if="!settings.commentOnMemberPosts">{{ t('aiMember.ruleNoMemberPosts') }}</li>
                            </ul>
                        </div>
                    </div>
                </AppGadget>

                <StickySaveBar v-if="canManage" :dirty="changes > 0" :count="changes" :saving="saving" :disabled="Boolean(accountsLocalError)" :error-count="errorCount" :error="formError" revertable submit @revert="revert" />
            </form>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import { AI_PROVIDERS, type AiGroupOption, type AiMemberListView, type AiMemberSettings, type AiMemberSettingsRequest, type AiModelRow, type AiProvider } from '~/composables/aimember/api/useAiMemberAdminApi.types';
import type { GroupRow } from '~/composables/group/api/useGroupAdminApi.types';
import UtilAiMember from '~/utils/util-ai-member';
import UtilAiMemberMessages from '~/utils/util-ai-member-messages';

const { t } = useI18n();
useSeoMeta({ title: () => t('aiMember.settingsTitle') });

const api = useAiMemberAdminApi();
const groupApi = useGroupAdminApi();
const toast = useAppToast();
const errorOf = useApiErrorOf();
const time = useDisplayTime();
const { confirm } = useConfirmDialog();
const { can } = useAdminPermission();
const ids = { stop: useId(), instant: useId(), scope: useId(), memberPosts: useId(), groupSearch: useId(), modelDefault: useId() };

/** 바꾸기(정지·AI 전용 소모임·게시 방식) — 서버 `ai-member.manage` */
const canManage = computed(() => can(UtilAiMember.PERMISSION_MANAGE));
/** 제공사 키 — 서버 `ai-member.secret` */
const canSecret = computed(() => can(UtilAiMember.PERMISSION_SECRET));

const settings = ref<AiMemberSettings | null>(null);
const accounts = ref<AiMemberListView | null>(null);
const busy = ref(false);
const loadError = ref<ApiError | null>(null);

const pendingTotal = computed(() => (accounts.value ? accounts.value.items.reduce((sum, row) => sum + row.pendingDrafts, 0) : null));
const accountOptions = computed(() => (accounts.value?.items ?? []).map((row) => ({ value: row.id, label: row.member.nickname })));
const scopeOptions = computed(() => (['ALL', 'SELECTED'] as const).map((value) => ({ value, label: t(`aiMember.instantScopeOption.${value}`) })));
/** 글이 잘 올라오는 게시판 간격 — 7일 · 30일(서버에 다른 값이 저장돼 있으면 그 값도 보인다) */
const intervalOptions = computed(() => {
    const values = [...UtilAiMember.BUSY_INTERVAL_OPTIONS];
    const saved = initial.value?.busyIntervalDays;
    if (saved !== undefined && !values.includes(saved)) values.push(saved);
    return values.sort((a, b) => a - b).map((value) => ({ value, label: t('aiMember.daysLine', { n: value }) }));
});

function accountsOf(provider: AiProvider): number {
    return (accounts.value?.items ?? []).filter((row) => row.provider === provider).length;
}

/* ── 한 번에 저장하는 칸 ── */
const form = ref<AiMemberSettingsRequest | null>(null);
const initial = ref<AiMemberSettingsRequest | null>(null);
const saving = ref(false);
const fieldErrors = ref<Record<string, string>>({});
const formError = ref('');

function requestOf(s: AiMemberSettings): AiMemberSettingsRequest {
    return {
        instantPublish: s.instantPublish,
        instantScope: s.instantScope,
        instantAccountIds: [...s.instantAccountIds],
        dailyCallLimit: s.dailyCallLimit,
        commentOnMemberPosts: s.commentOnMemberPosts,
        quietDays: s.postTiming.quietDays,
        busyIntervalDays: s.postTiming.busyIntervalDays,
        dailyPostMax: s.postTiming.dailyPostMax,
    };
}

/** 서버 값으로 칸을 다시 만든다 — 처음 받을 때와 저장한 뒤 */
function fill(s: AiMemberSettings): void {
    settings.value = s;
    initial.value = requestOf(s);
    form.value = requestOf(s);
}

function revert(): void {
    if (initial.value) form.value = { ...initial.value, instantAccountIds: [...initial.value.instantAccountIds] };
    fieldErrors.value = {};
    formError.value = '';
}

const changes = computed(() => {
    const a = form.value;
    const b = initial.value;
    if (!a || !b) return 0;
    let n = 0;
    if (a.instantPublish !== b.instantPublish) n++;
    if (a.instantScope !== b.instantScope) n++;
    if ([...a.instantAccountIds].sort().join() !== [...b.instantAccountIds].sort().join()) n++;
    if (a.dailyCallLimit !== b.dailyCallLimit) n++;
    if (a.commentOnMemberPosts !== b.commentOnMemberPosts) n++;
    if (a.quietDays !== b.quietDays) n++;
    if (a.busyIntervalDays !== b.busyIntervalDays) n++;
    if (a.dailyPostMax !== b.dailyPostMax) n++;
    return n;
});

/** 계정별인데 하나도 안 골랐다 — 서버도 같은 규칙으로 거절한다(`fields.instantAccountIds`) */
const accountsLocalError = computed(() => (form.value?.instantPublish && form.value.instantScope === 'SELECTED' && form.value.instantAccountIds.length === 0 ? t('aiMember.instantAccountsRequired') : ''));
const accountsError = computed(() => fieldErrors.value.instantAccountIds || accountsLocalError.value);
const errorCount = computed(() => Object.keys(fieldErrors.value).length + (accountsLocalError.value && !fieldErrors.value.instantAccountIds ? 1 : 0));

let seq = 0;
async function reload(): Promise<void> {
    const mine = ++seq;
    busy.value = true;
    loadError.value = null;
    try {
        const [s, list, opts, modelList] = await Promise.all([api.settings(), api.list(), api.options(), api.models()]);
        if (mine !== seq) return;
        fill(s);
        accounts.value = list;
        aiGroups.value = opts.groups;
        models.value = modelList.items;
    } catch (e) {
        if (mine !== seq) return;
        loadError.value = errorOf(e);
    } finally {
        if (mine === seq) busy.value = false;
    }
}

onMounted(() => void reload());

async function save(): Promise<void> {
    const f = form.value;
    if (!f || saving.value || changes.value === 0 || accountsLocalError.value) return;
    saving.value = true;
    fieldErrors.value = {};
    formError.value = '';
    try {
        // 서버에 넘기는 본문은 평범한 객체로(_frontend-core 2-3)
        const saved = await api.saveSettings({ ...toRaw(f), instantAccountIds: f.instantAccountIds.map((id) => id) });
        fill(saved);
        toast.success(t('aiMember.settingsSaved'));
    } catch (e) {
        const error = errorOf(e);
        if (error.fields && Object.keys(error.fields).length) {
            fieldErrors.value = { ...error.fields };
            formError.value = Object.entries(error.fields)
                .filter(([key]) => !['instantAccountIds', 'dailyCallLimit', 'quietDays', 'busyIntervalDays', 'dailyPostMax'].includes(key))
                .map(([, message]) => message)
                .join(' ');
        } else {
            formError.value = UtilAiMemberMessages.error(t, error);
        }
    } finally {
        saving.value = false;
    }
}

/* ── 전체 정지 — 바로 반영 ── */
const stopping = ref(false);

async function setStopped(stopped: boolean): Promise<void> {
    if (!settings.value || stopping.value) return;
    const ok = await confirm(
        stopped
            ? { title: t('aiMember.stopConfirmTitle'), message: t('aiMember.stopConfirmMessage'), confirmLabel: t('aiMember.stopConfirm'), tone: 'danger' }
            : { title: t('aiMember.resumeConfirmTitle'), message: t('aiMember.resumeConfirmMessage'), confirmLabel: t('aiMember.resumeConfirm') },
    );
    if (!ok) return;
    stopping.value = true;
    try {
        const next = await api.setStopped(stopped);
        // 한 번에 저장하는 칸은 고치던 값을 그대로 둔다 — 정지 칸만 바꾼다
        settings.value = { ...settings.value, stopped: next.stopped, stoppedAt: next.stoppedAt, stoppedBy: next.stoppedBy };
        toast.success(stopped ? t('aiMember.stoppedToast') : t('aiMember.resumedToast'));
    } catch (e) {
        toast.error(UtilAiMemberMessages.error(t, errorOf(e)), 8000);
    } finally {
        stopping.value = false;
    }
}

/* ── 제공사 키 — 제공사마다 따로 저장. 입력값은 이 화면 메모리에만(forms.md 2-1) ── */
const keyEditing = ref<AiProvider | null>(null);
const keyValue = ref('');
const keyError = ref('');
const keySaving = ref(false);

function startKey(provider: AiProvider): void {
    keyEditing.value = provider;
    keyValue.value = '';
    keyError.value = '';
}

function cancelKey(): void {
    keyEditing.value = null;
    keyValue.value = '';
    keyError.value = '';
}

async function saveKey(provider: AiProvider): Promise<void> {
    if (keySaving.value || !keyValue.value.trim() || !settings.value) return;
    keySaving.value = true;
    keyError.value = '';
    try {
        const next = await api.setKey(provider, keyValue.value.trim());
        settings.value = { ...settings.value, keys: next.keys };
        cancelKey();
        toast.success(t('aiMember.keySaved', { provider: t(`aiMember.provider.${provider}`) }));
        // 키가 생긴 제공사의 계정은 쉬는 중이 풀린다 — 계정 수·대기 건수를 다시 받는다
        accounts.value = await api.list();
    } catch (e) {
        const error = errorOf(e);
        // 서버 암호화 키가 없다 — 키 칸을 잠그고 위 경고를 띄운다
        if (error.code === 'CRYPTO_KEY_MISSING') {
            settings.value = { ...settings.value, cryptoKeyConfigured: false };
            cancelKey();
            toast.error(UtilAiMemberMessages.error(t, error), 8000);
        } else {
            keyError.value = error.fields?.key ?? UtilAiMemberMessages.error(t, error);
        }
    } finally {
        keySaving.value = false;
        keyValue.value = keyEditing.value ? keyValue.value : '';
    }
}

/* ── 모델 목록 — 한 건씩 바로 반영. 바꾸기는 모두 새 목록 전체를 돌려준다 ── */
const models = ref<AiModelRow[]>([]);
const modelTab = ref<AiProvider>(AI_PROVIDERS[0]!);
const modelBusyId = ref<string | null>(null);
const modelAdding = ref(false);
const modelForm = ref<{ modelId: string; label: string; priceInput: number | null; priceOutput: number | null; freeTier: boolean }>(blankModelForm());
const modelFieldErrors = ref<Record<string, string>>({});
const modelFormError = ref('');

function blankModelForm() {
    return { modelId: '', label: '', priceInput: null, priceOutput: null, freeTier: false };
}

function modelsOf(provider: AiProvider): AiModelRow[] {
    return models.value.filter((m) => m.provider === provider);
}

function defaultIdOf(provider: AiProvider): string | null {
    return modelsOf(provider).find((m) => m.defaultModel)?.id ?? null;
}

/** 모델 목록이 바뀌면 계정 목록의 쉬는 이유(모델 없음)도 바뀔 수 있다 — 같이 다시 받는다 */
async function afterModelChange(next: AiModelRow[], message: string): Promise<void> {
    models.value = next;
    toast.success(message);
    try {
        accounts.value = await api.list();
    } catch {
        // 계정 목록은 다음에 새로 고칠 때 받는다 — 모델 목록 저장은 이미 끝났다
    }
}

async function setModelEnabled(row: AiModelRow, enabled: boolean): Promise<void> {
    if (modelBusyId.value || row.enabled === enabled) return;
    // 쓰는 계정이 있는 모델을 끄면 그 계정들이 쉰다 — 확인을 받는다
    if (!enabled && row.accounts > 0) {
        const ok = await confirm({
            title: t('aiMember.modelDisableTitle', { model: row.modelId }),
            message: t('aiMember.modelDisableMessage', { n: row.accounts }),
            confirmLabel: t('aiMember.modelDisableConfirm'),
            tone: 'danger',
        });
        if (!ok) return;
    }
    modelBusyId.value = row.id;
    try {
        await afterModelChange((await api.setModelEnabled(row.id, enabled)).items, t('aiMember.modelSaved'));
    } catch (e) {
        toast.error(UtilAiMemberMessages.error(t, errorOf(e)), 8000);
    } finally {
        modelBusyId.value = null;
    }
}

async function setModelDefault(row: AiModelRow): Promise<void> {
    if (modelBusyId.value || row.defaultModel) return;
    modelBusyId.value = row.id;
    try {
        await afterModelChange((await api.setModelDefault(row.id)).items, t('aiMember.modelSaved'));
    } catch (e) {
        toast.error(UtilAiMemberMessages.error(t, errorOf(e)), 8000);
    } finally {
        modelBusyId.value = null;
    }
}

async function deleteModel(row: AiModelRow): Promise<void> {
    if (modelBusyId.value) return;
    const ok = await confirm({ title: t('aiMember.modelDeleteTitle', { model: row.modelId }), message: t('aiMember.modelDeleteMessage'), confirmLabel: t('aiMember.modelDelete'), tone: 'danger' });
    if (!ok) return;
    modelBusyId.value = row.id;
    try {
        await afterModelChange((await api.deleteModel(row.id)).items, t('aiMember.modelDeleted', { model: row.modelId }));
    } catch (e) {
        const error = errorOf(e);
        toast.error(UtilAiMemberMessages.error(t, error), 8000);
        // 그 사이 계정이 이 모델을 골랐거나 기본이 됐다 — 목록을 다시 받는다
        if (error.code === 'AI_MODEL_IN_USE' || error.code === 'AI_MODEL_DEFAULT_LOCKED' || error.code === 'AI_MODEL_NOT_FOUND') {
            try {
                models.value = (await api.models()).items;
            } catch {
                // 다음 새로 고침 때 받는다
            }
        }
    } finally {
        modelBusyId.value = null;
    }
}

async function addModel(): Promise<void> {
    const f = modelForm.value;
    const modelId = f.modelId.trim();
    if (modelAdding.value || !modelId) return;
    modelAdding.value = true;
    modelFieldErrors.value = {};
    modelFormError.value = '';
    try {
        const next = await api.createModel({
            provider: modelTab.value,
            modelId,
            label: f.label.trim() || null,
            priceInput: f.priceInput,
            priceOutput: f.priceOutput,
            freeTier: f.freeTier,
        });
        modelForm.value = blankModelForm();
        await afterModelChange(next.items, t('aiMember.modelAdded', { model: modelId }));
    } catch (e) {
        const error = errorOf(e);
        if (error.fields && Object.keys(error.fields).length) {
            modelFieldErrors.value = { ...error.fields };
            // 칸이 없는 오류(제공사 등)는 아래 한 줄로
            modelFormError.value = Object.entries(error.fields)
                .filter(([key]) => !['modelId', 'label', 'priceInput', 'priceOutput'].includes(key))
                .map(([, message]) => message)
                .join(' ');
        } else {
            modelFormError.value = UtilAiMemberMessages.error(t, error);
        }
    } finally {
        modelAdding.value = false;
    }
}

/* ── AI 전용 소모임 — 한 건씩 바로 반영 ── */
const aiGroups = ref<AiGroupOption[]>([]);
const groupQuery = ref('');
const groupResults = ref<GroupRow[] | null>(null);
const groupSearching = ref(false);
const groupSearchUnavailable = ref(false);
const groupBusyId = ref<string | null>(null);
const GROUP_SEARCH_SIZE = 10;

function isAiGroup(id: string): boolean {
    return aiGroups.value.some((group) => group.id === id);
}

/** 열린 소모임을 이름으로 찾는다(공개·비공개 모두 — 비공개는 이유와 함께 단추를 끈다) */
async function searchGroups(): Promise<void> {
    if (groupSearching.value) return;
    groupSearching.value = true;
    try {
        const page = await groupApi.list({ q: groupQuery.value.trim() || undefined, status: 'ACTIVE', size: GROUP_SEARCH_SIZE });
        groupResults.value = page.items;
    } catch (e) {
        const error = errorOf(e);
        // 소모임 조회 권한이 없다 — 오류가 아니라 찾기만 끈다(forms.md 2-2)
        if (error.status === 403) groupSearchUnavailable.value = true;
        else toast.error(UtilAiMemberMessages.error(t, error), 8000);
    } finally {
        groupSearching.value = false;
    }
}

async function setGroupAiOnly(id: string, name: string, aiOnly: boolean): Promise<void> {
    if (groupBusyId.value) return;
    const ok = await confirm(
        aiOnly
            ? { title: t('aiMember.aiGroupAssignTitle', { name }), message: t('aiMember.aiGroupAssignMessage'), confirmLabel: t('aiMember.aiGroupAssign') }
            : { title: t('aiMember.aiGroupReleaseTitle', { name }), message: t('aiMember.aiGroupReleaseMessage'), confirmLabel: t('aiMember.aiGroupRelease'), tone: 'danger' },
    );
    if (!ok) return;
    groupBusyId.value = id;
    try {
        const result = await api.setGroupAiOnly(id, aiOnly);
        aiGroups.value = result.aiOnly
            ? [...aiGroups.value.filter((group) => group.id !== result.id), { id: result.id, name: result.name }]
            : aiGroups.value.filter((group) => group.id !== result.id);
        toast.success(result.aiOnly ? t('aiMember.aiGroupAssigned', { name: result.name }) : t('aiMember.aiGroupReleased', { name: result.name }));
    } catch (e) {
        const error = errorOf(e);
        toast.error(UtilAiMemberMessages.error(t, error), 8000);
        // 그 사이 비공개로 바뀌었거나 없어졌다 — 찾은 목록을 다시 받는다
        if ((error.code === 'GROUP_NOT_PUBLIC' || error.code === 'GROUP_NOT_FOUND') && groupResults.value) void searchGroups();
    } finally {
        groupBusyId.value = null;
    }
}

onBeforeRouteLeave(async () => {
    if (changes.value === 0) return true;
    return confirm({ title: t('aiMember.leaveTitle'), message: t('aiMember.leaveMessage'), confirmLabel: t('aiMember.leaveConfirm'), tone: 'danger' });
});
</script>

<style scoped>
/* 한글 글꼴 보정 — 알약 안에서 글자가 아래로 붙어 보이지 않게 글자만 올린다(components.md 2-4) */
.ai-member-tag :deep(.p-tag-label) {
    position: relative;
    top: -0.09em;
}
</style>
