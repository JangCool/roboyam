<template>
    <!--
        알림 템플릿 편집(`/settings/notification-templates/templates/[id]`, 새로 만들기 `new?target=<대상 코드>&channel=<채널>` · 복사 `new?from=<id>`) — 계약 `docs/features/message-template-contract.md` 「개정 3」 템플릿 편집.
        한 화면: 머리(이름·딱지·「변경 이력」·복사·삭제·목록으로) → 왼쪽 편집 칸 → 오른쪽 미리보기. 저장은 화면 하나로 한 번(`POST`·`PUT templates`) → 하단 떠 있는 저장 줄(`StickySaveBar`, 「기본으로 되돌리기」는 그 줄의 왼쪽 단추).
        공통 칸: 이름 · 대상(이벤트 · 필수 안내 메일 — 새로 만들 때만 고른다) · 채널(새로 만들 때만, 대상이 나가는 채널 중에서) · 받는 사람(읽기 전용 — 대상이 정한다) · 언어 · 변수 넣기.
        채널마다 칸이 다르다:
          · 이메일 — 레이아웃(받는 사람이 같거나 `ANY`) · 제목 · 본문(간단 에디터) · 버튼 블록 → 미리보기(레이아웃 적용, 격리된 iframe — 서버가 그린다) + 「나에게 시험 발송」(`MailTestDialog`).
          · 채팅 — 본문 한 칸(간단 마크다운: `**굵게**` · `[글자](주소)` · `- 목록`) → 채팅 글 모양 미리보기(화면이 견본 값으로 그린다) + 「알림 도구로 시험 보내기」(실제 알림 도구 목록에서 Slack·Mattermost 도구를 고른다 — 보내기는 목업).
          · 앱 알림 — 제목 한 줄 · 본문(평문) → 받는 사람에 따라 관리자 종 서랍 한 줄(운영자) 또는 회원 알림함 한 줄(회원) 모양 미리보기.
        - 새로 만들기: 대상·채널을 바꾸면 그 대상·채널·언어의 기본 템플릿 내용으로 채운다(고친 내용이 있으면 묻는다). 이메일은 그 받는 사람의 기본 레이아웃으로 시작한다. 복사: 원본의 대상·채널·내용·언어·레이아웃, 이름 「원본 (복사)」.
        - 기본 템플릿과 권한이 `template.read` 뿐이면 읽기 전용(입력 `readonly`, 에디터 잠금, 저장 줄 없음). 기본 템플릿은 「복사해서 만들기」를 안내한다.
        - 검사: 이메일 `UtilMailTemplate.validate` · 채팅·앱 알림 `UtilNotificationTemplate.validateChat|validateInApp`(서버와 같은 규칙). 서버 `fields.*` 가 오면 그 칸 아래에 그대로.
        - 언어: 그 언어의 「쓸 템플릿」인 사용자 템플릿은 언어를 바꿀 수 없다. 삭제: 쓰는 중이면 단추를 잠그고, 서버도 409 `TEMPLATE_IN_USE`.
        - 변경 이력: 제목 옆 단추 → 오른쪽 `SettingHistoryDrawer`(처음 열 때 받는다 — components.md 2-5). 저장된 사용자 템플릿만.
        권한: 화면 `template.read` · 바꾸기·시험 `template.manage`. 저장하지 않은 변경이 있는 채 떠나면 묻는다.
        ⚠ MOCK: API 는 메모리 목업이다(`useNotificationTemplateApi` · `useMailLayoutApi`). 채팅 시험의 도구 목록만 실제 API(`useNotificationChannels`)다.
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <template v-if="loading && !ready">
            <PageTitle :title="isNew ? t('notificationTemplate.newTitle') : t('notificationTemplate.editTitle')" />
            <div class="grid gap-4 xl:grid-cols-[minmax(0,1fr)_minmax(0,26rem)]" role="status" :aria-label="t('common.loading')">
                <Skeleton height="28rem" />
                <Skeleton height="28rem" />
            </div>
        </template>

        <template v-else-if="!ready || !target || !channelSummary">
            <PageTitle :title="isNew ? t('notificationTemplate.newTitle') : t('notificationTemplate.editTitle')">
                <template #actions>
                    <Button :label="t('notificationTemplate.backToList')" icon="pi pi-arrow-left" size="small" severity="secondary" variant="outlined" @click="router.push(LIST_PATH)" />
                </template>
            </PageTitle>
            <Message v-if="denied" severity="info" :closable="false" size="small">{{ t('notificationTemplate.denied') }}</Message>
            <AppGadget v-else :title="t('notificationTemplate.editTitle')">
                <ErrorState v-if="notFound" code="404" :title="t('notificationTemplate.templateNotFound')" :description="loadError">
                    <Button :label="t('notificationTemplate.backToList')" icon="pi pi-arrow-left" severity="secondary" variant="outlined" @click="router.push(LIST_PATH)" />
                </ErrorState>
                <ErrorState v-else :title="t('notificationTemplate.templateLoadFailed')" :description="loadError" retry :retrying="loading" @retry="load" />
            </AppGadget>
        </template>

        <template v-else>
            <PageTitle :title="pageTitle">
                <template #actions>
                    <Button v-if="historyAvailable" type="button" :label="t('settingHistory.open')" icon="pi pi-history" size="small" severity="secondary" variant="outlined" @click="openHistory" />
                    <span v-if="canManage && saved" v-tooltip.top="copyBlockedReason || undefined" class="inline-flex">
                        <Button type="button" :label="t('notificationTemplate.copyCreate')" icon="pi pi-copy" size="small" severity="secondary" variant="outlined" :disabled="Boolean(copyBlockedReason)" :aria-description="copyBlockedReason || undefined" @click="copySaved" />
                    </span>
                    <span v-if="canManage && saved && !saved.builtin" v-tooltip.top="deleteBlockedReason || undefined" class="inline-flex">
                        <Button type="button" :label="t('common.delete')" icon="pi pi-trash" size="small" severity="danger" variant="outlined" :disabled="Boolean(deleteBlockedReason) || deleting" :loading="deleting" :aria-description="deleteBlockedReason || undefined" @click="confirmDelete" />
                    </span>
                    <Button :label="t('notificationTemplate.backToList')" icon="pi pi-arrow-left" size="small" severity="secondary" variant="outlined" @click="router.push(LIST_PATH)" />
                </template>
            </PageTitle>
            <div class="-mt-2 flex flex-wrap items-center gap-2">
                <Tag :value="UtilNotificationTemplateMessages.channelLabel(t, channel)" :icon="UtilNotificationTemplateMessages.channelIcon(channel)" severity="secondary" class="mail-template-tag text-[11px]!" />
                <Tag :value="t('notificationTemplate.audienceTag', { audience: UtilNotificationTemplateMessages.audienceLabel(t, target.audience) })" :icon="UtilNotificationTemplateMessages.audienceIcon(target.audience)" severity="secondary" class="mail-template-tag text-[11px]!" />
                <Tag v-if="saved?.builtin" :value="t('notificationTemplate.builtinTag')" icon="pi pi-lock" severity="secondary" class="mail-template-tag text-[11px]!" />
                <Tag v-if="target.security" :value="t('notificationTemplate.securityTag')" icon="pi pi-shield" severity="warn" class="mail-template-tag text-[11px]!" />
                <Tag v-for="label in usage" :key="label" :value="label" severity="info" class="mail-template-tag text-[11px]!" />
                <span v-if="saved?.updatedAt" class="text-xs text-muted-color">{{ saved.updatedByName ? t('notificationTemplate.updatedBy', { at: time.full(saved.updatedAt), name: saved.updatedByName }) : time.full(saved.updatedAt) }}</span>
            </div>

            <Message v-if="saved?.builtin" severity="secondary" :closable="false" size="small">
                <span>{{ t('notificationTemplate.builtinReadOnly') }}</span>
                <Button v-if="canManage" type="button" :label="t('notificationTemplate.copyCreate')" icon="pi pi-copy" size="small" variant="link" class="ml-1 p-0!" :disabled="Boolean(copyBlockedReason)" @click="copySaved" />
            </Message>
            <Message v-else-if="!canManage" severity="info" :closable="false" size="small">{{ t('notificationTemplate.readOnly') }}</Message>
            <Message v-if="target.security && isEmail && !readonly" severity="warn" :closable="false" size="small">{{ t('notificationTemplate.securityHint', { names: requiredNames }) }}</Message>

            <form class="flex flex-col gap-4" novalidate @submit.prevent="save">
                <div class="grid items-start gap-4 xl:grid-cols-[minmax(0,1fr)_minmax(0,26rem)]">
                    <!-- 왼쪽 — 편집 칸 -->
                    <AppGadget :title="t('notificationTemplate.contentTitle')">
                        <div :key="formKey" class="flex flex-col gap-4">
                            <div class="grid gap-4 sm:grid-cols-2">
                                <LabeledField :label="t('notificationTemplate.nameLabel')" :hint="t('notificationTemplate.maxHint', { max: UtilMailTemplate.NAME_MAX })" :error="fieldError('name')">
                                    <template #default="{ id, describedBy, invalid }">
                                        <InputText :id="id" v-model="form.name" name="name" autocomplete="off" :readonly="readonly || saving" :invalid="invalid" :aria-describedby="describedBy" aria-required="true" fluid />
                                    </template>
                                </LabeledField>

                                <!-- 대상 — 새로 만들 때만 고른다(복사는 원본 대상으로 고정) -->
                                <LabeledField :label="t('notificationTemplate.targetLabel')" :help="pickable ? t('notificationTemplate.targetPickHelp') : readonly ? '' : t('notificationTemplate.targetLocked')">
                                    <template #aside>
                                        <NuxtLink :to="UtilNotificationTemplateMessages.targetPath(target)" class="text-xs text-primary hover:underline">{{ t('notificationTemplate.openTarget') }}</NuxtLink>
                                    </template>
                                    <template #default="{ id, describedBy }">
                                        <Select v-if="pickable" :model-value="target.code" :input-id="id" :options="targetOptions" option-label="label" option-value="value" option-group-label="label" option-group-children="items" filter :disabled="saving || switching" :aria-describedby="describedBy" fluid @update:model-value="pickTarget" />
                                        <InputText v-else :id="id" :model-value="`${target.name} · ${UtilNotificationTemplateMessages.targetGroupLabel(t, target.type, target.audience)}`" readonly :aria-describedby="describedBy" fluid />
                                    </template>
                                </LabeledField>

                                <!-- 채널 — 새로 만들 때만(대상이 나가는 채널 중에서) -->
                                <div class="flex flex-col gap-1.5">
                                    <span :id="channelLabelId" class="text-sm font-medium">{{ t('notificationTemplate.channelLabel') }}</span>
                                    <SelectButton v-if="pickable" :model-value="channel" :options="channelOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :disabled="saving || switching" :aria-labelledby="channelLabelId" class="self-start" @update:model-value="pickChannel" />
                                    <span v-else class="inline-flex items-center gap-1.5 text-sm">
                                        <i :class="UtilNotificationTemplateMessages.channelIcon(channel)" class="text-xs text-muted-color" aria-hidden="true" />
                                        {{ UtilNotificationTemplateMessages.channelLabel(t, channel) }}
                                    </span>
                                    <p class="m-0 text-xs text-muted-color">{{ t(`notificationTemplate.channelHelp.${channel}`) }}</p>
                                    <p v-if="fieldError('channel')" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ fieldError('channel') }}</p>
                                </div>

                                <!-- 받는 사람 — 대상에서 정해진다(바꿀 수 없다) -->
                                <LabeledField :label="t('notificationTemplate.audienceLabel')" :help="t('notificationTemplate.audienceHelp')">
                                    <template #default="{ id, describedBy }">
                                        <InputText :id="id" :model-value="UtilNotificationTemplateMessages.audienceLabel(t, target.audience)" readonly :aria-describedby="describedBy" fluid />
                                    </template>
                                </LabeledField>

                                <div class="flex flex-col gap-1.5">
                                    <span :id="localeLabelId" class="text-sm font-medium">{{ t('notificationTemplate.localeLabel') }}</span>
                                    <SelectButton :model-value="form.locale" :options="localeOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :disabled="readonly || saving || localeLocked || switching" :aria-labelledby="localeLabelId" class="self-start" @update:model-value="pickLocale" />
                                    <p v-if="fieldError('locale')" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ fieldError('locale') }}</p>
                                    <p v-else-if="localeLocked && !readonly" class="m-0 text-xs text-muted-color">{{ t('notificationTemplate.localeLocked') }}</p>
                                </div>

                                <LabeledField v-if="isEmail" :label="t('notificationTemplate.layoutLabel')" :help="t('notificationTemplate.layoutHelp')" :error="fieldError('layoutId')">
                                    <template #aside>
                                        <NuxtLink v-if="form.layoutId" :to="`/settings/notification-templates/layouts/${encodeURIComponent(form.layoutId)}`" class="text-xs text-primary hover:underline">{{ t('notificationTemplate.openLayout') }}</NuxtLink>
                                    </template>
                                    <template #default="{ id, describedBy, invalid }">
                                        <Select v-model="form.layoutId" :input-id="id" :options="layoutOptions" option-label="label" option-value="value" :disabled="readonly || saving" :invalid="invalid" :aria-describedby="describedBy" fluid />
                                    </template>
                                </LabeledField>
                            </div>

                            <!-- 제목 — 이메일 제목 · 앱 알림 제목(채팅은 없다) -->
                            <LabeledField
                                v-if="channel !== 'CHAT'"
                                :label="isEmail ? t('notificationTemplate.subjectLabel') : t('notificationTemplate.inAppTitleLabel')"
                                :hint="t('notificationTemplate.maxHint', { max: isEmail ? UtilMailTemplate.SUBJECT_MAX : UtilNotificationTemplate.IN_APP_TITLE_MAX })"
                                :help="isEmail ? t('notificationTemplate.subjectHelp') : t('notificationTemplate.inAppTitleHelp')"
                                :error="fieldError('subject')"
                            >
                                <template #aside>
                                    <MailTemplateVariableMenu v-if="!readonly" :variables="variables" @pick="insertSubject" />
                                </template>
                                <template #default="{ id, describedBy, invalid }">
                                    <InputText :id="id" ref="subjectInput" v-model="form.subject" name="subject" autocomplete="off" :readonly="readonly || saving" :invalid="invalid" :aria-describedby="describedBy" aria-required="true" fluid @keydown.enter.prevent />
                                </template>
                            </LabeledField>

                            <!-- 본문 — 이메일은 간단 에디터 + 버튼, 채팅·앱 알림은 글 칸 -->
                            <div v-if="isEmail" class="flex flex-col gap-1.5">
                                <div class="flex items-baseline justify-between gap-2">
                                    <span :id="bodyLabelId" class="text-sm font-medium">{{ t('notificationTemplate.bodyLabel') }}</span>
                                    <MailTemplateVariableMenu v-if="!readonly" :variables="variables" @pick="insertEditor" />
                                </div>
                                <RichEditor
                                    :key="`editor-${formKey}`"
                                    ref="editor"
                                    v-model="editorHtml"
                                    level="SIMPLE"
                                    compact
                                    :disabled="readonly || saving"
                                    :invalid="Boolean(fieldError('body'))"
                                    :labelledby="bodyLabelId"
                                    :describedby="bodyMessageId"
                                    :placeholder="t('notificationTemplate.bodyPlaceholder')"
                                    @ready="onEditorReady"
                                />
                                <div :id="bodyMessageId" aria-live="polite" class="text-xs leading-relaxed">
                                    <p v-if="fieldError('body')" role="alert" class="m-0 text-red-600 dark:text-red-400">{{ fieldError('body') }}</p>
                                    <p v-else class="m-0 text-muted-color">{{ t('notificationTemplate.bodyHelp') }}</p>
                                </div>
                            </div>
                            <LabeledField
                                v-else
                                :label="channel === 'CHAT' ? t('notificationTemplate.chatBodyLabel') : t('notificationTemplate.inAppBodyLabel')"
                                :hint="t('notificationTemplate.maxHint', { max: channel === 'CHAT' ? UtilNotificationTemplate.CHAT_BODY_MAX : UtilNotificationTemplate.IN_APP_BODY_MAX })"
                                :help="channel === 'CHAT' ? t('notificationTemplate.chatBodyHelp') : t('notificationTemplate.inAppBodyHelp')"
                                :error="fieldError('body')"
                            >
                                <template #aside>
                                    <MailTemplateVariableMenu v-if="!readonly" :variables="variables" @pick="insertBodyText" />
                                </template>
                                <template #default="{ id, describedBy, invalid }">
                                    <Textarea :id="id" ref="bodyInput" v-model="form.body" name="body" :rows="channel === 'CHAT' ? 8 : 3" auto-resize :readonly="readonly || saving" :invalid="invalid" :aria-describedby="describedBy" :class="channel === 'CHAT' ? 'font-mono text-[13px]!' : ''" fluid />
                                </template>
                            </LabeledField>

                            <!-- 「버튼」 블록 — 이메일만, 링크 변수로만 가는 단추 하나 -->
                            <section v-if="isEmail" class="flex flex-col gap-3 rounded-lg border border-(--line) p-3" :aria-labelledby="buttonTitleId">
                                <div class="flex flex-wrap items-center gap-2">
                                    <h3 :id="buttonTitleId" class="m-0 text-sm font-medium">{{ t('notificationTemplate.buttonTitle') }}</h3>
                                    <span class="text-xs text-muted-color">{{ linkVariables.length ? t('notificationTemplate.buttonHint') : t('notificationTemplate.buttonNoLink') }}</span>
                                    <div v-if="!readonly" class="ml-auto">
                                        <Button v-if="!button" type="button" :label="t('notificationTemplate.buttonAdd')" icon="pi pi-plus" size="small" severity="secondary" variant="outlined" :disabled="!linkVariables.length || saving" @click="addButton" />
                                        <Button v-else type="button" :label="t('notificationTemplate.buttonRemove')" icon="pi pi-times" size="small" severity="secondary" variant="text" :disabled="saving" @click="removeButton" />
                                    </div>
                                </div>
                                <div v-if="button" class="grid gap-3 sm:grid-cols-2">
                                    <LabeledField :label="t('notificationTemplate.buttonLabel')" :hint="t('notificationTemplate.maxHint', { max: UtilMailTemplate.BUTTON_LABEL_MAX })">
                                        <template #default="{ id, describedBy, invalid }">
                                            <InputText :id="id" v-model="button.label" name="buttonLabel" autocomplete="off" :readonly="readonly || saving" :invalid="invalid || Boolean(fieldError('button'))" :aria-describedby="describedBy" fluid />
                                        </template>
                                    </LabeledField>
                                    <LabeledField :label="t('notificationTemplate.buttonLink')">
                                        <template #default="{ id, describedBy, invalid }">
                                            <Select v-model="button.link" :input-id="id" :options="linkOptions" option-label="label" option-value="value" :disabled="readonly || saving" :invalid="invalid || Boolean(fieldError('button'))" :aria-describedby="describedBy" fluid />
                                        </template>
                                    </LabeledField>
                                </div>
                                <p v-if="fieldError('button')" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ fieldError('button') }}</p>
                                <p v-else-if="button" class="m-0 text-xs text-muted-color">{{ t('notificationTemplate.buttonPlaceHint', { token: buttonToken }) }}</p>
                            </section>
                        </div>
                    </AppGadget>

                    <!-- 오른쪽 — 미리보기 -->
                    <div class="flex min-w-0 flex-col gap-4 xl:sticky xl:top-4">
                        <AppGadget :title="t('notificationTemplate.previewTitle')">
                            <template v-if="canManage && channel !== 'IN_APP'" #actions>
                                <span v-tooltip.top="testBlockedReason || undefined" class="inline-flex">
                                    <Button v-if="isEmail" type="button" :label="t('notificationTemplate.testButton')" icon="pi pi-send" size="small" severity="secondary" variant="outlined" :disabled="Boolean(testBlockedReason) || saving" :aria-description="testBlockedReason || undefined" @click="openTest" />
                                    <Button v-else type="button" :label="t('notificationTemplate.chatTestButton')" icon="pi pi-send" size="small" severity="secondary" variant="outlined" :disabled="Boolean(testBlockedReason) || saving" :aria-description="testBlockedReason || undefined" @click="openChatTest" />
                                </span>
                            </template>

                            <MailTemplatePreviewFrame v-if="isEmail" :subject="preview.subject" :html="preview.html" :loading="preview.loading" :error="preview.error" height="34rem" />

                            <!-- 채팅 글 모양(Slack·Mattermost 글 한 덩어리) -->
                            <div v-else-if="channel === 'CHAT'" class="flex flex-col gap-2">
                                <div class="flex gap-2.5 rounded-lg border border-(--line) bg-surface-50 p-3 dark:bg-surface-900">
                                    <span class="grid size-8 shrink-0 place-items-center rounded-md bg-primary text-xs font-bold text-white" aria-hidden="true">{{ siteInitial }}</span>
                                    <div class="min-w-0 flex-1">
                                        <p class="m-0 text-[12px] font-semibold">{{ t('notificationTemplate.chatBotName') }} <span class="font-normal text-muted-color">{{ t('notificationTemplate.chatNow') }}</span></p>
                                        <!-- eslint-disable-next-line vue/no-v-html -- 화면이 만든 태그(strong·a·ul·li·br)만 들어 있다(값은 가렸다 — UtilNotificationTemplate.renderChat) -->
                                        <div class="chat-preview mt-1 text-[13px] leading-relaxed break-words" v-html="chatPreview" />
                                    </div>
                                </div>
                                <p class="m-0 text-xs text-muted-color">{{ t('notificationTemplate.chatPreviewNote') }}</p>
                            </div>

                            <!-- 앱 알림 한 줄 — 운영자는 관리자 종 서랍, 회원은 회원 알림함 -->
                            <div v-else class="flex flex-col gap-2">
                                <p class="m-0 text-xs font-medium text-muted-color">{{ target.audience === 'OPERATOR' ? t('notificationTemplate.inAppDrawerLook') : t('notificationTemplate.inAppInboxLook') }}</p>
                                <div class="flex items-start gap-3 rounded-lg border border-(--line) p-3" :class="target.audience === 'OPERATOR' ? 'bg-primary-50/50 dark:bg-primary-950/30' : ''">
                                    <span class="mt-0.5 grid size-8 shrink-0 place-items-center rounded-full bg-surface-100 text-muted-color dark:bg-surface-800" aria-hidden="true">
                                        <i :class="target.audience === 'OPERATOR' ? 'pi pi-bell' : 'pi pi-comment'" />
                                    </span>
                                    <span class="flex min-w-0 flex-1 flex-col gap-0.5">
                                        <span class="flex items-center gap-2">
                                            <span class="min-w-0 flex-1 truncate text-sm font-semibold">{{ inAppPreview.title || t('notificationTemplate.inAppEmptyTitle') }}</span>
                                            <span class="size-2 shrink-0 rounded-full bg-primary" aria-hidden="true" />
                                        </span>
                                        <span v-if="inAppPreview.body" class="line-clamp-2 text-xs text-muted-color">{{ inAppPreview.body }}</span>
                                        <span class="text-xs text-muted-color">{{ target.area ? UtilNotificationMessages.areaLabel(t, target.area) : '' }} · {{ t('notificationTemplate.chatNow') }}</span>
                                    </span>
                                </div>
                                <p class="m-0 text-xs text-muted-color">{{ t('notificationTemplate.inAppPreviewNote') }}</p>
                            </div>
                        </AppGadget>
                    </div>
                </div>

                <StickySaveBar v-if="!readonly" submit :revertable="!isNew" :dirty="isNew || dirty" :saving="saving" :disabled="invalidCount > 0 || switching" :count="changeCount" :error-count="invalidCount" :error="formError" @revert="revert">
                    <template #extra>
                        <Button v-if="defaultFor(form.locale)" type="button" :label="t('notificationTemplate.resetDefault')" icon="pi pi-replay" size="small" severity="secondary" variant="text" :disabled="saving" @click="resetToDefault" />
                    </template>
                </StickySaveBar>
            </form>
        </template>

        <SettingHistoryDrawer v-model:visible="historyOpen" :hint="t('notificationTemplate.historyHint')">
            <SettingHistoryTable
                :items="history.rows.value"
                :total="history.total.value"
                :first="history.first.value"
                :page-size="history.size.value"
                :loading="history.loading.value"
                :loaded="history.loaded.value"
                :error="history.error.value"
                :compact="prefs.density === 'compact'"
                @page="history.onPage"
                @retry="history.load"
            />
        </SettingHistoryDrawer>

        <MailTestDialog v-model:visible="testOpen" v-model:to="testTo" :help="t('notificationTemplate.testHelp')" :to-error="testToError" :error="testError" :testing="testing" :result="testResult" @send="runTest" />

        <!-- 채팅 — 알림 도구로 시험 보내기(도구 목록은 실제 API, 보내기는 목업) -->
        <Dialog v-model:visible="chatTestOpen" modal :header="t('notificationTemplate.chatTestTitle')" class="w-[28rem] max-w-[calc(100vw-2rem)]">
            <form class="flex flex-col gap-3" novalidate @submit.prevent="runChatTest">
                <p v-if="toolsLoading && !chatTools.length" class="m-0 text-sm text-muted-color">{{ t('common.loading') }}</p>
                <Message v-else-if="!chatTools.length" severity="warn" :closable="false" size="small">
                    <span>{{ t('notificationTemplate.chatTestNoTools') }}</span>
                    <NuxtLink to="/settings/notification-channels" class="ml-2 font-medium underline underline-offset-2">{{ t('notification.channelsLink') }}</NuxtLink>
                </Message>
                <LabeledField v-else :label="t('notificationTemplate.chatTestTool')" :help="t('notificationTemplate.chatTestHelp')" :error="chatTestToolError">
                    <template #default="{ id, describedBy, invalid }">
                        <Select v-model="chatTestTool" :input-id="id" :options="chatToolOptions" option-label="label" option-value="value" option-disabled="disabled" :invalid="invalid" :aria-describedby="describedBy" :disabled="chatTesting" fluid />
                    </template>
                </LabeledField>
                <div v-if="chatTestResult" class="flex items-start gap-2 rounded-lg p-3 text-sm" :class="chatTestResult.ok ? 'bg-green-50 text-green-800 dark:bg-green-950 dark:text-green-300' : 'bg-red-50 text-red-700 dark:bg-red-950 dark:text-red-300'" :role="chatTestResult.ok ? 'status' : 'alert'">
                    <i :class="chatTestResult.ok ? 'pi pi-check-circle' : 'pi pi-times-circle'" class="mt-0.5" aria-hidden="true" />
                    <span class="min-w-0">{{ chatTestResult.message }}</span>
                </div>
                <Message v-if="chatTestError" severity="error" variant="simple" size="small">{{ chatTestError }}</Message>
                <div class="flex justify-end gap-2">
                    <Button type="button" :label="t('common.close')" severity="secondary" variant="text" @click="chatTestOpen = false" />
                    <Button type="submit" :label="t('notificationTemplate.chatTestSend')" icon="pi pi-send" :loading="chatTesting" :disabled="!chatTestTool" />
                </div>
            </form>
        </Dialog>
    </div>
</template>

<script setup lang="ts">
import type { MailTestDialogResult } from '~/components/mail/MailTestDialog.vue';
import { ApiError } from '~/composables/common/useApiClient';
import { mockTemplateCan } from '~/composables/template/api/mock/notification-template-mock';
import type { MailLayoutListItemView } from '~/composables/template/api/useMailLayoutApi.types';
import type { ChatTemplateTestResultView, TemplateAudience, TemplateChannel, TemplateLocale, TemplateTargetDetailView, TemplateTargetView, TemplateView } from '~/composables/template/api/useNotificationTemplateApi.types';
import { TEMPLATE_LIMIT, TEMPLATE_LOCALES } from '~/composables/template/api/useNotificationTemplateApi.types';
import type { MailButton, MailTemplateProblem } from '~/utils/util-mail-template';
import UtilMailSettingsMessages from '~/utils/util-mail-settings-messages';
import UtilMailTemplate from '~/utils/util-mail-template';
import UtilNotificationChannelMessages from '~/utils/util-notification-channel-messages';
import UtilNotificationMessages from '~/utils/util-notification-messages';
import type { NotificationTemplateField } from '~/utils/util-notification-template';
import UtilNotificationTemplate from '~/utils/util-notification-template';
import UtilNotificationTemplateMessages from '~/utils/util-notification-template-messages';
import UtilValidation from '~/utils/util-validation';

const { t } = useI18n();

const LIST_PATH = '/settings/notification-templates/templates';
const api = useNotificationTemplateApi();
const layoutApi = useMailLayoutApi();
const { channels: tools, loading: toolsLoading, load: loadTools } = useNotificationChannels();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const time = useDisplayTime();
const route = useRoute();
const router = useRouter();
const { user } = useAdminAuth();
const { confirm } = useConfirmDialog();
const { prefs } = useLayoutPrefs();
// MOCK: 목업 스위치(`?mock=template-readonly|template-denied`)로 권한을 흉내 낸다 — 서버가 붙으면 아래 REAL 줄로 바꾼다
const can = mockTemplateCan(useAdminPermission().can);
// REAL: const { can } = useAdminPermission();

const canManage = computed(() => can('template.manage'));

const channelLabelId = useId();
const localeLabelId = useId();
const bodyLabelId = useId();
const bodyMessageId = useId();
const buttonTitleId = useId();
const buttonToken = UtilMailTemplate.token(UtilMailTemplate.BUTTON);

const id = computed(() => String(route.params.id ?? ''));
const isNew = computed(() => id.value === 'new');
const queryText = (value: unknown) => (typeof value === 'string' && value ? value : null);

/** 칸 — 서버 `fields.*` 와 같은 이름(`button` 은 화면 전용 — 서버는 `body` 로 준다) */
type Field = NotificationTemplateField | 'button' | 'locale' | 'layoutId' | 'channel';

/* ── 받은 것 ── */

const targets = ref<TemplateTargetView[]>([]);
const layouts = ref<MailLayoutListItemView[]>([]);
/** 저장된 템플릿(새로 만드는 중이면 null) */
const saved = ref<TemplateView | null>(null);
/** 복사 원본(`?from=`) */
const source = ref<TemplateView | null>(null);
/** 대상 상세 — 채널마다 기본 템플릿(되돌리기·새로 만들기 내용), 쓰는 곳(언어 잠금·삭제 잠금) */
const detail = ref<TemplateTargetDetailView | null>(null);
/** 지금 채널 */
const channel = ref<TemplateChannel>('EMAIL');
const ready = ref(false);
const loading = ref(false);
const loadError = ref('');
const notFound = ref(false);
const denied = ref(false);
/** 새로 만들기에서 대상을 바꾸는 중(그 대상 상세를 받는 중) */
const switching = ref(false);

const target = computed(() => detail.value?.summary ?? null);
const channelSummary = computed(() => target.value?.channels.find((item) => item.channel === channel.value) ?? null);
const channelDetail = computed(() => detail.value?.channels.find((item) => item.channel === channel.value) ?? null);
const variables = computed(() => channelSummary.value?.variables ?? []);
const isEmail = computed(() => channel.value === 'EMAIL');

const defaultLayoutIdOf = (audience: TemplateAudience) => layouts.value.find((item) => item.builtin && item.audience === audience)?.id ?? '';
const layoutUsable = (layoutId: string, audience: TemplateAudience) => {
    const layout = layouts.value.find((item) => item.id === layoutId);
    return Boolean(layout && UtilMailTemplate.layoutFits(layout.audience, audience));
};
/** 대상·채널을 고를 수 있다 — 새로 만들기(복사가 아닐 때)만 */
const pickable = computed(() => isNew.value && !source.value);
const readonly = computed(() => !canManage.value || Boolean(saved.value?.builtin));
const historyAvailable = computed(() => Boolean(saved.value && !saved.value.builtin));
const requiredNames = computed(() => variables.value.filter((v) => v.required).map((v) => UtilMailTemplate.token(v.name)).join(', '));
const pageTitle = computed(() => (isNew.value ? t('notificationTemplate.newTitle') : (saved.value?.name ?? t('notificationTemplate.editTitle'))));

useSeoMeta({ title: () => pageTitle.value });

const localeOptions = computed(() => TEMPLATE_LOCALES.map((value) => ({ value, label: UtilNotificationTemplateMessages.localeLabel(t, value) })));
const channelOptions = computed(() => (target.value?.channels ?? []).map((item) => ({ value: item.channel, label: UtilNotificationTemplateMessages.channelLabel(t, item.channel) })));
/** 레이아웃 고르기 — 받는 사람이 같거나 `ANY` 인 것만(기본 레이아웃 먼저 — 받은 차례) */
const layoutOptions = computed(() => {
    const audience = target.value?.audience;
    if (!audience) return [];
    return layouts.value
        .filter((item) => UtilMailTemplate.layoutFits(item.audience, audience))
        .map((item) => ({
            value: item.id,
            label: item.builtin ? t('notificationTemplate.layoutBuiltinOption', { name: item.name }) : item.audience === 'ANY' ? t('notificationTemplate.layoutAnyOption', { name: item.name }) : item.name,
        }));
});
/** 대상 고르기 — 회원 이벤트 / 운영자 이벤트 · 영역 / 필수 안내 메일 묶음 */
const targetOptions = computed(() => {
    const groups = new Map<string, { label: string; items: { value: string; label: string }[] }>();
    for (const item of targets.value) {
        const label = UtilNotificationTemplateMessages.targetGroupLabel(t, item.type, item.audience, item.area);
        const group = groups.get(label) ?? { label, items: [] };
        group.items.push({ value: item.code, label: item.name });
        groups.set(label, group);
    }
    return [...groups.values()];
});
const linkVariables = computed(() => variables.value.filter((v) => v.link));
const linkOptions = computed(() => linkVariables.value.map((v) => ({ value: v.name, label: `${UtilMailTemplate.token(v.name)} · ${v.description}` })));

/* ── 편집 값 ── */

interface Baseline {
    name: string;
    locale: TemplateLocale;
    layoutId: string;
    subject: string;
    body: string;
    editorHtml: string;
    button: MailButton | null;
}

const form = reactive<{ name: string; locale: TemplateLocale; layoutId: string; subject: string; body: string }>({ name: '', locale: 'ko', layoutId: '', subject: '', body: '' });
/** 이메일 본문(에디터 값)과 버튼 — 채팅·앱 알림은 `form.body` */
const editorHtml = ref('');
const button = ref<MailButton | null>(null);
/** 처음 값 — 「고쳤는가」의 기준. 이메일 본문은 에디터가 정규화한 값으로 바꿔 둔다(`onEditorReady`) */
const baseline = ref<Baseline>({ name: '', locale: 'ko', layoutId: '', subject: '', body: '', editorHtml: '', button: null });
const formKey = ref(0);
const tried = ref(false);
const serverErrors = ref<Record<string, string>>({});
const formError = ref('');
const saving = ref(false);

/** 템플릿 내용(제목·본문·버튼)을 칸에 넣는다 */
function setContent(template: Pick<TemplateView, 'subject' | 'body'>): void {
    form.subject = template.subject;
    if (isEmail.value) {
        const split = UtilMailTemplate.splitBody(template.body);
        editorHtml.value = split.editorHtml;
        button.value = split.button ? { ...split.button } : null;
        form.body = '';
    } else {
        form.body = template.body;
        editorHtml.value = '';
        button.value = null;
    }
}

function setBaseline(): void {
    baseline.value = { name: form.name, locale: form.locale, layoutId: form.layoutId, subject: form.subject, body: form.body, editorHtml: editorHtml.value, button: button.value ? { ...button.value } : null };
    tried.value = false;
    serverErrors.value = {};
    formError.value = '';
    formKey.value += 1;
}

function copyName(from: TemplateView): string {
    if (from.builtin) return t('notificationTemplate.copyNameBuiltin', { target: target.value?.name ?? '' }).slice(0, UtilMailTemplate.NAME_MAX);
    return t('notificationTemplate.copyName', { name: from.name }).slice(0, UtilMailTemplate.NAME_MAX);
}

/** 이 대상·채널·언어의 기본 템플릿 */
function defaultFor(locale: TemplateLocale): TemplateView | undefined {
    const view = channelDetail.value;
    return view?.templates.find((item) => item.id === view.defaults[locale]);
}

/** 칸을 채운다 — 저장된 것 · 복사 원본 · 새로 만들기(그 대상·채널·언어의 기본 템플릿 + 기본 레이아웃) */
function fill(): void {
    if (saved.value) {
        form.name = saved.value.name;
        form.locale = saved.value.locale;
        form.layoutId = saved.value.layoutId ?? '';
        setContent(saved.value);
    } else if (source.value) {
        form.name = copyName(source.value);
        form.locale = source.value.locale;
        form.layoutId = source.value.layoutId ?? '';
        setContent(source.value);
    } else {
        form.name = '';
        form.layoutId = isEmail.value && target.value ? defaultLayoutIdOf(target.value.audience) : '';
        const seed = defaultFor(form.locale);
        if (seed) setContent(seed);
    }
    setBaseline();
}

function onEditorReady(html: string): void {
    if (editorHtml.value === baseline.value.editorHtml) {
        baseline.value = { ...baseline.value, editorHtml: html };
        editorHtml.value = html;
    }
}

/** 저장할 본문 — 이메일은 에디터 값 + 버튼 블록 */
const composed = computed(() => (isEmail.value ? UtilMailTemplate.joinBody(editorHtml.value, button.value) : form.body));

const bodyChanged = computed(() => {
    const b = baseline.value;
    if (!isEmail.value) return form.body !== b.body;
    return editorHtml.value !== b.editorHtml || (button.value?.label ?? null) !== (b.button?.label ?? null) || (button.value?.link ?? null) !== (b.button?.link ?? null);
});
const contentChanged = computed(() => form.subject !== baseline.value.subject || bodyChanged.value);
const changeCount = computed(() => {
    const b = baseline.value;
    let n = 0;
    if (form.name !== b.name) n += 1;
    if (form.locale !== b.locale) n += 1;
    if (isEmail.value && form.layoutId !== b.layoutId) n += 1;
    if (form.subject !== b.subject) n += 1;
    if (bodyChanged.value) n += 1;
    return n;
});
const dirty = computed(() => ready.value && changeCount.value > 0);

watch(
    () => form.name,
    () => clearServer('name'),
);
watch(
    () => form.locale,
    () => clearServer('locale'),
);
watch(
    () => form.layoutId,
    () => clearServer('layoutId'),
);
watch(
    () => form.subject,
    () => clearServer('subject'),
);
watch(composed, () => clearServer('body'));
function clearServer(field: string): void {
    if (serverErrors.value[field]) serverErrors.value = { ...serverErrors.value, [field]: '' };
}

// 이메일 에디터 안의 `{{button}}` 과 버튼 칸을 맞춘다 — 자리가 생기면 기본 버튼, 사라지면 버튼도 없앤다
watch(editorHtml, (html) => {
    if (!isEmail.value) return;
    const tokens = UtilMailTemplate.buttonTokens(html);
    if (tokens > 0 && !button.value && linkVariables.value[0]) button.value = { label: t('notificationTemplate.buttonDefaultLabel'), link: linkVariables.value[0].name };
    else if (tokens === 0 && button.value) button.value = null;
});

/* ── 검사 ── */

/** 채널마다의 화면 검사 — 이메일 문제의 `bodyHtml` 은 `body` 칸이다 */
const problems = computed<Partial<Record<Field, MailTemplateProblem>>>(() => {
    const current = target.value;
    if (!current) return {};
    const rules = { audience: current.audience, variables: variables.value };
    if (isEmail.value) {
        const found = UtilMailTemplate.validate({ name: form.name, subject: form.subject, bodyHtml: composed.value }, { ...rules, security: current.security });
        const { bodyHtml, ...rest } = found;
        return bodyHtml ? { ...rest, body: bodyHtml } : rest;
    }
    if (channel.value === 'CHAT') return UtilNotificationTemplate.validateChat({ name: form.name, body: form.body }, rules);
    return UtilNotificationTemplate.validateInApp({ name: form.name, subject: form.subject, body: form.body }, rules);
});

function fieldError(field: Field): string {
    const server = serverErrors.value[field];
    if (server) return server;
    if (field === 'locale' || field === 'channel' || readonly.value) return '';
    if (field === 'layoutId') return !isEmail.value || form.layoutId ? '' : tried.value ? t('notificationTemplate.layoutRequired') : '';
    const problem = problems.value[field];
    if (!problem) return '';
    if (problem.code === 'REQUIRED' && !tried.value) return '';
    return UtilNotificationTemplateMessages.problem(t, field === 'button' ? 'button' : field, problem);
}

const FIELDS: readonly Field[] = ['name', 'channel', 'locale', 'layoutId', 'subject', 'body', 'button'];
const invalidCount = computed(() => FIELDS.filter((field) => Boolean(fieldError(field))).length);

/** 시험은 제목·본문이 맞을 때만 — 이름은 상관없다 */
const testBlockedReason = computed(() => {
    const p = problems.value;
    if (p.subject || p.body || p.button) return t('notificationTemplate.testNeedsValid');
    return '';
});

/** 그 언어의 쓸 템플릿으로 쓰는 중이면 언어를 바꿀 수 없다 */
const localeLocked = computed(() => Boolean(saved.value && channelDetail.value?.selected[saved.value.locale] === saved.value.id));

/** 저장된 상태에서 이 템플릿을 쓰는 곳 */
const usage = computed(() => {
    const view = channelDetail.value;
    const template = saved.value;
    if (!view || !template) return [];
    const out: string[] = [];
    for (const locale of TEMPLATE_LOCALES) if (view.selected[locale] === template.id) out.push(t('notificationTemplate.usedAsDefault', { locale: UtilNotificationTemplateMessages.localeLabel(t, locale) }));
    const rules = view.rules.filter((rule) => rule.templateId === template.id).length;
    if (rules > 0) out.push(t('notificationTemplate.usedByRules', { count: rules }));
    return out;
});
const deleteBlockedReason = computed(() => (usage.value.length ? t('notificationTemplate.deleteInUse') : ''));
const copyBlockedReason = computed(() => {
    const count = (channelDetail.value?.templates ?? []).filter((item) => !item.builtin).length;
    return count >= TEMPLATE_LIMIT ? t('notificationTemplate.limitReached', { max: TEMPLATE_LIMIT }) : '';
});

/* ── 읽기 ── */

let loadSeq = 0;

async function load(): Promise<void> {
    const mine = ++loadSeq;
    loading.value = true;
    loadError.value = '';
    notFound.value = false;
    ready.value = false;
    try {
        const [targetList, layoutList] = await Promise.all([api.targets(), layoutApi.list()]);
        let template: TemplateView | null = null;
        let from: TemplateView | null = null;
        let pick: TemplateTargetView | undefined;
        let wantedChannel: string | null = null;
        if (!isNew.value) {
            template = await api.template(id.value);
            pick = targetList.find((item) => item.code === template?.target.code);
            wantedChannel = template.channel;
        } else {
            const fromId = queryText(route.query.from);
            if (fromId) {
                from = await api.template(fromId);
                pick = targetList.find((item) => item.code === from?.target.code);
                wantedChannel = from.channel;
            } else {
                // `?target=` 그 대상, 없으면 `?channel=` 채널이 있는 첫 대상, 둘 다 없으면 첫 대상(회원 이벤트 먼저)
                const wanted = queryText(route.query.target);
                wantedChannel = queryText(route.query.channel);
                pick = targetList.find((item) => item.code === wanted) ?? targetList.find((item) => item.channels.some((c) => c.channel === wantedChannel)) ?? targetList[0];
            }
        }
        // 대상이 하나도 없으면 만들 수 없다(없는 템플릿과 같게 보인다)
        if (!pick) throw new ApiError({ status: 404, code: 'TEMPLATE_TARGET_NOT_FOUND', message: t('notificationTemplate.templatesEmptyBody') });
        const view = await api.target(pick.type, pick.code);
        if (mine !== loadSeq) return;
        targets.value = targetList;
        layouts.value = layoutList;
        saved.value = template;
        source.value = from;
        detail.value = view;
        channel.value = view.channels.find((item) => item.channel === wantedChannel)?.channel ?? (view.channels[0]?.channel as TemplateChannel);
        form.locale = 'ko';
        fill();
        ready.value = true;
    } catch (e) {
        if (mine !== loadSeq) return;
        const error = toApiError(e);
        if (error.status === 403) denied.value = true;
        else {
            notFound.value = error.status === 404;
            loadError.value = error.message;
        }
    } finally {
        if (mine === loadSeq) loading.value = false;
    }
}

/* ── 새로 만들기 — 대상·채널·언어 바꾸기 ── */

const switchConfirm = () => confirm({ title: t('notificationTemplate.switchTargetTitle'), message: t('notificationTemplate.switchTargetMessage'), confirmLabel: t('notificationTemplate.switchConfirm'), tone: 'danger' });

async function pickTarget(next: string): Promise<void> {
    const item = targets.value.find((other) => other.code === next);
    if (!item || next === target.value?.code || switching.value) return;
    // 쓸 수 있는 변수가 달라 그 대상의 기본 내용으로 바꾼다 — 고친 것이 있으면 묻는다
    if (contentChanged.value && !(await switchConfirm())) return;
    switching.value = true;
    const prevAudience = target.value?.audience;
    try {
        detail.value = await api.target(item.type, item.code);
        // 채널은 그대로 두되 새 대상에 없으면 첫 채널로
        if (!detail.value.channels.some((c) => c.channel === channel.value)) channel.value = detail.value.channels[0]?.channel as TemplateChannel;
        reseed(defaultFor(form.locale));
        adjustLayout(prevAudience);
        void router.replace({ query: { ...route.query, target: next, channel: channel.value } });
    } catch (e) {
        toast.error(toApiError(e).message, 8000);
    } finally {
        switching.value = false;
    }
}

async function pickChannel(next: TemplateChannel): Promise<void> {
    if (!next || next === channel.value || switching.value) return;
    if (contentChanged.value && !(await switchConfirm())) return;
    channel.value = next;
    reseed(defaultFor(form.locale));
    adjustLayout(target.value?.audience);
    void router.replace({ query: { ...route.query, channel: next } });
}

/** 이메일이면 받는 사람에 맞는 레이아웃으로 — 이메일이 아니면 레이아웃을 비운다 */
function adjustLayout(prevAudience: TemplateAudience | undefined): void {
    const audience = target.value?.audience;
    if (!audience) return;
    const untouched = baseline.value.layoutId === form.layoutId;
    if (!isEmail.value) form.layoutId = '';
    else if (!form.layoutId || (prevAudience && prevAudience !== audience && form.layoutId === defaultLayoutIdOf(prevAudience)) || !layoutUsable(form.layoutId, audience)) form.layoutId = defaultLayoutIdOf(audience);
    if (untouched) baseline.value = { ...baseline.value, layoutId: form.layoutId };
}

async function pickLocale(next: TemplateLocale): Promise<void> {
    if (!next || next === form.locale) return;
    form.locale = next;
    // 새로 만들기(복사 아님)에서 내용을 아직 안 고쳤으면 그 언어의 기본 내용으로 바꾼다
    if (pickable.value && !contentChanged.value) reseed(defaultFor(next));
}

/** 새로 만들기에서 내용만 다시 채운다 — 이름·언어·레이아웃은 고친 값과 「고쳤는가」 기준을 그대로 둔다 */
function reseed(seed: TemplateView | undefined): void {
    const prev = baseline.value;
    if (seed) setContent(seed);
    else setContent({ subject: '', body: '' });
    setBaseline();
    baseline.value = { ...baseline.value, name: prev.name, locale: prev.locale, layoutId: prev.layoutId };
}

/* ── 변수 넣기 · 버튼 ── */

const subjectInput = ref<{ $el: HTMLInputElement } | null>(null);
const bodyInput = ref<{ $el: HTMLTextAreaElement } | null>(null);
const editor = ref<{ insertText: (text: string) => void; insertParagraph: (text: string) => void } | null>(null);

/** 글 칸의 커서 자리(고른 글자가 있으면 그 자리)에 변수를 넣는다 */
function insertAt(el: HTMLInputElement | HTMLTextAreaElement | undefined, value: string, name: string, set: (next: string) => void): void {
    const token = UtilMailTemplate.token(name);
    const start = el?.selectionStart ?? value.length;
    const end = el?.selectionEnd ?? start;
    set(value.slice(0, start) + token + value.slice(end));
    const caret = start + token.length;
    void nextTick(() => {
        el?.focus();
        el?.setSelectionRange(caret, caret);
    });
}

function insertSubject(name: string): void {
    insertAt(subjectInput.value?.$el, form.subject, name, (next) => (form.subject = next));
}

function insertBodyText(name: string): void {
    insertAt(bodyInput.value?.$el, form.body, name, (next) => (form.body = next));
}

function insertEditor(name: string): void {
    editor.value?.insertText(UtilMailTemplate.token(name));
}

function addButton(): void {
    editor.value?.insertParagraph(buttonToken);
}

function removeButton(): void {
    editorHtml.value = UtilMailTemplate.removeButtonTokens(editorHtml.value);
    button.value = null;
}

/* ── 미리보기 ── */

/** 이메일 — 고칠 때마다 잠깐 기다렸다가 서버에서 받는다(고른 레이아웃으로) */
const preview = reactive({ subject: '', html: '', loading: false, error: '' });
let previewTimer: ReturnType<typeof setTimeout> | null = null;
let previewSeq = 0;

function schedulePreview(): void {
    if (previewTimer) clearTimeout(previewTimer);
    if (!ready.value || !target.value || !isEmail.value) return;
    previewTimer = setTimeout(() => void runPreview(), 400);
}

async function runPreview(): Promise<void> {
    const current = target.value;
    if (!current) return;
    const mine = ++previewSeq;
    preview.loading = true;
    try {
        const result = await api.preview({ target: { type: current.type, code: current.code }, locale: form.locale, subject: form.subject, bodyHtml: composed.value, layoutId: form.layoutId || undefined });
        if (mine !== previewSeq) return;
        preview.subject = result.subject;
        preview.html = result.html;
        preview.error = '';
    } catch (e) {
        if (mine !== previewSeq) return;
        preview.error = toApiError(e).message;
    } finally {
        if (mine === previewSeq) preview.loading = false;
    }
}

watch(() => [ready.value, target.value?.code, channel.value, form.locale, form.layoutId, form.subject, composed.value] as const, schedulePreview);
onBeforeUnmount(() => {
    if (previewTimer) clearTimeout(previewTimer);
});

/** 채팅·앱 알림 — 화면이 견본 값으로 그린다(목업 가정 — 서버는 보낼 때 값의 멘션·마크다운을 무력화한다) */
const chatPreview = computed(() => (channel.value === 'CHAT' ? UtilNotificationTemplate.renderChat(form.body, variables.value) : ''));
const inAppPreview = computed(() => UtilNotificationTemplate.renderInApp(form.subject, form.body, variables.value));
const siteInitial = computed(() => (variables.value.find((v) => v.name === 'siteName')?.sample ?? 'R').slice(0, 1));

/* ── 저장 · 되돌리기 · 기본으로 ── */

async function resetToDefault(): Promise<void> {
    const seed = defaultFor(form.locale);
    if (!seed) return;
    const ok = await confirm({ title: t('notificationTemplate.resetDefaultTitle'), message: t('notificationTemplate.resetDefaultMessage'), confirmLabel: t('notificationTemplate.resetDefaultConfirm'), tone: 'danger' });
    if (!ok) return;
    setContent(seed);
}

function revert(): void {
    if (saved.value) fill();
}

let leaving = false;

async function save(): Promise<void> {
    const current = target.value;
    if (readonly.value || saving.value || !current || switching.value) return;
    if (!isNew.value && !dirty.value) return;
    tried.value = true;
    if (Object.keys(problems.value).length > 0 || (isEmail.value && !form.layoutId)) return;
    saving.value = true;
    formError.value = '';
    serverErrors.value = {};
    const body = {
        targetType: current.type,
        targetCode: current.code,
        channel: channel.value,
        name: form.name.trim(),
        locale: form.locale,
        layoutId: isEmail.value ? form.layoutId : null,
        subject: channel.value === 'CHAT' ? '' : form.subject,
        body: composed.value,
    };
    try {
        if (isNew.value || !saved.value) {
            const created = await api.create(body);
            toast.success(t('notificationTemplate.created'));
            leaving = true;
            await router.replace(`${LIST_PATH}/${encodeURIComponent(created.id)}`);
            return;
        }
        saved.value = await api.update(saved.value.id, body);
        toast.success(t('notificationTemplate.saved'));
        fill();
        if (detail.value) detail.value = { ...detail.value, channels: detail.value.channels.map((ch) => (ch.channel === channel.value ? { ...ch, templates: ch.templates.map((item) => (item.id === saved.value?.id ? (saved.value as TemplateView) : item)) } : ch)) };
        if (historyLoaded) void history.reloadFirst();
    } catch (e) {
        const error = toApiError(e);
        const found: Record<string, string> = {};
        for (const field of ['name', 'channel', 'locale', 'layoutId', 'subject', 'body']) {
            const message = error.field(field);
            if (message) found[field] = message;
        }
        serverErrors.value = found;
        if (Object.keys(found).length === 0) formError.value = error.message;
    } finally {
        saving.value = false;
    }
}

function copySaved(): void {
    if (!saved.value || copyBlockedReason.value) return;
    void router.push({ path: `${LIST_PATH}/new`, query: { from: saved.value.id } });
}

const deleting = ref(false);

async function confirmDelete(): Promise<void> {
    const template = saved.value;
    if (!template || deleting.value) return;
    const ok = await confirm({ title: t('notificationTemplate.deleteTitle', { name: template.name }), message: t('notificationTemplate.deleteMessage'), confirmLabel: t('common.delete'), tone: 'danger' });
    if (!ok) return;
    deleting.value = true;
    try {
        await api.remove(template.id);
        toast.success(t('notificationTemplate.deleted'));
        leaving = true;
        await router.push(LIST_PATH);
    } catch (e) {
        toast.error(toApiError(e).message, 8000);
    } finally {
        deleting.value = false;
    }
}

/* ── 변경 이력(오른쪽 서랍) ── */

const historyOpen = ref(false);
let historyLoaded = false;
const history = useSettingHistory((page, size) => api.history(saved.value?.id ?? '', page, size));

function openHistory(): void {
    if (!saved.value) return;
    historyOpen.value = true;
    if (!historyLoaded) {
        historyLoaded = true;
        void history.reloadFirst();
    }
}

/* ── 이메일 — 나에게 시험 발송 ── */

const testOpen = ref(false);
const testTo = ref('');
const testing = ref(false);
const testToError = ref('');
const testError = ref('');
const testResult = ref<MailTestDialogResult | null>(null);

watch(testTo, () => (testToError.value = ''));

function openTest(): void {
    if (testBlockedReason.value) return;
    testTo.value = user.value?.email ?? '';
    testToError.value = '';
    testError.value = '';
    testResult.value = null;
    testOpen.value = true;
}

async function runTest(): Promise<void> {
    const current = target.value;
    if (testing.value || !current) return;
    const to = testTo.value.trim();
    const problem = UtilValidation.email(to);
    if (problem) {
        testToError.value = t(problem === 'REQUIRED' ? 'mail.testToRequired' : 'mail.testToFormat');
        return;
    }
    testing.value = true;
    testError.value = '';
    testResult.value = null;
    try {
        const result = await api.test({ target: { type: current.type, code: current.code }, locale: form.locale, layoutId: form.layoutId || undefined, subject: form.subject, bodyHtml: composed.value, to });
        testResult.value = { succeeded: result.succeeded, text: result.succeeded ? '' : UtilMailSettingsMessages.testFailure(t, result), to };
    } catch (e) {
        const error = toApiError(e);
        const field = error.field('to');
        if (field) testToError.value = field;
        else if (error.code === 'MAIL_TEST_RATE_LIMITED') testError.value = t('mail.testRateLimited', { seconds: error.retryAfterSeconds ?? 60 });
        else testError.value = error.fields?.subject || error.fields?.body || error.message;
    } finally {
        testing.value = false;
    }
}

/* ── 채팅 — 알림 도구로 시험 보내기 ── */

const chatTestOpen = ref(false);
const chatTestTool = ref<string | null>(null);
const chatTesting = ref(false);
const chatTestToolError = ref('');
const chatTestError = ref('');
const chatTestResult = ref<ChatTemplateTestResultView | null>(null);

/** 시험할 수 있는 도구 — 실제 알림 도구 목록의 Slack·Mattermost(꺼진 도구는 흐리게) */
const chatTools = computed(() => (tools.value ?? []).filter((tool) => tool.type === 'SLACK' || tool.type === 'MATTERMOST'));
const chatToolOptions = computed(() =>
    chatTools.value.map((tool) => ({ value: tool.channelId, label: `${UtilNotificationChannelMessages.displayName(t, tool)}${tool.enabled ? '' : ` — ${t('notification.channelBlocked.disabled')}`}`, disabled: !tool.enabled })),
);
watch(chatTestTool, () => (chatTestToolError.value = ''));

async function openChatTest(): Promise<void> {
    if (testBlockedReason.value) return;
    chatTestError.value = '';
    chatTestToolError.value = '';
    chatTestResult.value = null;
    chatTestOpen.value = true;
    if (tools.value === undefined) await loadTools();
    if (!chatTestTool.value || !chatTools.value.some((tool) => tool.channelId === chatTestTool.value && tool.enabled)) chatTestTool.value = chatTools.value.find((tool) => tool.enabled)?.channelId ?? null;
}

async function runChatTest(): Promise<void> {
    const current = target.value;
    if (chatTesting.value || !current || !chatTestTool.value) return;
    chatTesting.value = true;
    chatTestError.value = '';
    chatTestResult.value = null;
    try {
        chatTestResult.value = await api.chatTest({ target: { type: current.type, code: current.code }, locale: form.locale, body: form.body, channelId: chatTestTool.value });
    } catch (e) {
        const error = toApiError(e);
        const field = error.field('channelId');
        if (field) chatTestToolError.value = field;
        else if (error.code === 'MAIL_TEST_RATE_LIMITED') chatTestError.value = t('mail.testRateLimited', { seconds: error.retryAfterSeconds ?? 60 });
        else chatTestError.value = error.fields?.body || error.message;
    } finally {
        chatTesting.value = false;
    }
}

/* ── 떠나기 · 주소 바뀜 ── */

onBeforeRouteLeave(async () => {
    if (leaving || !dirty.value) return true;
    return confirm({ title: t('notificationTemplate.leaveTitle'), message: t('notificationTemplate.leaveMessage'), confirmLabel: t('notificationTemplate.leaveConfirm'), tone: 'danger' });
});

// 같은 화면에서 다른 템플릿으로(복사해서 만들기 · 만든 뒤 주소 바꾸기) — 고친 것이 있으면 묻고 다시 읽는다. 새로 만들기 안의 대상·채널 바꾸기(쿼리만)는 다시 읽지 않는다
onBeforeRouteUpdate(async (to, from) => {
    if (to.params.id === from.params.id && to.query.from === from.query.from) return true;
    if (leaving || !dirty.value) return true;
    return confirm({ title: t('notificationTemplate.leaveTitle'), message: t('notificationTemplate.leaveMessage'), confirmLabel: t('notificationTemplate.leaveConfirm'), tone: 'danger' });
});
watch(
    () => [id.value, route.query.from] as const,
    () => {
        leaving = false;
        historyOpen.value = false;
        historyLoaded = false;
        void load();
    },
);

onMounted(load);
</script>

<style scoped>
/* 뱃지 글자 상하 중앙 + 한글 글꼴 보정(components.md 2-4) */
.mail-template-tag {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    line-height: 1;
}
.mail-template-tag :deep(.p-tag-label) {
    position: relative;
    top: -0.09em;
    white-space: nowrap;
}
/* 채팅 미리보기 — 화면이 만든 목록·링크만 */
.chat-preview :deep(ul) {
    margin: 0.25rem 0;
    padding-left: 1.25rem;
    list-style: disc;
}
.chat-preview :deep(a) {
    color: var(--p-primary-color);
    text-decoration: underline;
}
</style>
