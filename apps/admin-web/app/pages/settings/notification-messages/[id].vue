<template>
    <!--
        문구 편집(`/settings/notification-messages/[id]`, 새로 만들기 `new?audience=<받는 사람>&target=<대상 코드>&channel=<채널>` · 복사 `new?from=<id>`) — 계약 `docs/features/message-template-contract.md` 「개정 4」(개정 3 의 「템플릿 편집」 — 이벤트·채널별 내용은 이제 「문구」).
        「목록으로」·지운 뒤에는 그 문구의 받는 사람 목록(메뉴 「문구 › 운영자」 `…/operator` · 「문구 › 회원」 `…/member`)으로 간다. 두 메뉴 어디에도 속하지 않아 메뉴에서는 부모 「문구」만 활성이다(`UtilAdminMenu.parents`).
        한 화면: 머리(이름·딱지·「변경 이력」·복사·삭제·목록으로) → 왼쪽 편집 칸 → 오른쪽 미리보기. 저장은 화면 하나로 한 번(`POST`·`PUT messages`) → 하단 떠 있는 저장 줄(`StickySaveBar`, 「기본으로 되돌리기」는 그 줄의 왼쪽 단추).
        공통 칸: 이름 · 대상(새로 만들 때만 고른다) · 채널(새로 만들 때만, 대상이 나가는 채널 중에서 — 카카오톡은 준비 중이라 고를 수 없다) · 받는 사람(읽기 전용) · 언어 · 변수 넣기.
        채널마다 칸과 미리보기가 다르다 — 앱 알림이 아니면 **그 채널의 템플릿 고르기**(「템플릿 고르기」) + **고른 템플릿에 이 문구를 씌운 미리보기**:
          · 이메일 — 메일 템플릿(받는 사람이 같거나 `ANY`) · 제목 · 본문(간단 에디터) · 버튼 블록 → 메일 미리보기(격리된 iframe — 서버가 그린다) + 「나에게 시험 발송」(`MailTestDialog`).
          · Slack · Mattermost — 그 종류의 템플릿 · 제목 한 줄 · 본문(그 앱의 표기법) → 그 앱의 메시지 카드(`ChatMessageCard` — 템플릿의 색 막대·아이콘·꼬리말·버튼, 이벤트의 심각도) + 「알림 도구로 시험 보내기」(실제 알림 도구 목록에서 그 종류의 도구 — 보내기는 목업).
          · 카카오톡(준비 중) — 알림톡 템플릿 → 알림톡 말풍선(`KakaoMessageBubble` — 승인 본문을 이 대상의 견본 값으로). 본문을 쓰지 않는다. 모든 칸 읽기 전용.
          · 앱 알림 — 제목 한 줄 · 본문(평문) → 관리자 종 서랍 한 줄(운영자) 또는 회원 알림함 한 줄(회원). 템플릿 없음.
        - 새로 만들기: 대상·채널을 바꾸면 그 대상·채널·언어의 기본 문구 내용으로 채운다(고친 내용이 있으면 묻는다). 템플릿은 그 채널(받는 사람)의 기본 템플릿으로 시작한다. 복사: 원본의 대상·채널·내용·언어·템플릿, 이름 「원본 (복사)」.
        - 기본 문구 · 준비 중 채널 · 권한이 `template.read` 뿐이면 읽기 전용(입력 `readonly`, 에디터 잠금, 저장 줄 없음). 기본 문구는 「복사해서 만들기」를 안내한다.
        - 검사: 이메일 `UtilMailTemplate.validateMessage` · 앱 알림·Slack·Mattermost `UtilNotificationMessage.validateInApp|validateChat`(서버와 같은 규칙). 서버 `fields.*` 가 오면 그 칸 아래에 그대로.
        - 언어: 그 언어의 「쓸 문구」인 사용자 문구는 언어를 바꿀 수 없다. 삭제: 쓰는 중이면 단추를 잠그고, 서버도 409 `MESSAGE_IN_USE`.
        - 변경 이력: 제목 옆 단추 → 오른쪽 `SettingHistoryDrawer`(처음 열 때 받는다 — components.md 2-5). 저장된 사용자 문구만.
        권한: 화면 `template.read` · 바꾸기·시험 `template.manage`. 저장하지 않은 변경이 있는 채 떠나면 묻는다.
        ⚠ MOCK: API 는 메모리 목업이다(`useNotificationMessageApi` · `useMailTemplateApi` · `useChatTemplateApi` · `useKakaoTemplateApi`). Slack·Mattermost 시험의 도구 목록만 실제 API(`useNotificationChannels`)다.
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <template v-if="loading && !ready">
            <PageTitle :title="isNew ? t('notificationMessage.newTitle') : t('notificationMessage.editTitle')" />
            <div class="grid gap-4 xl:grid-cols-[minmax(0,1fr)_minmax(0,26rem)]" role="status" :aria-label="t('common.loading')">
                <Skeleton height="28rem" />
                <Skeleton height="28rem" />
            </div>
        </template>

        <template v-else-if="!ready || !target || !channelSummary">
            <PageTitle :title="isNew ? t('notificationMessage.newTitle') : t('notificationMessage.editTitle')">
                <template #actions>
                    <Button :label="t('notificationTemplate.backToList')" icon="pi pi-arrow-left" size="small" severity="secondary" variant="outlined" @click="router.push(listPath)" />
                </template>
            </PageTitle>
            <Message v-if="denied" severity="info" :closable="false" size="small">{{ t('notificationTemplate.denied') }}</Message>
            <AppGadget v-else :title="t('notificationMessage.editTitle')">
                <ErrorState v-if="notFound" code="404" :title="t('notificationMessage.messageNotFound')" :description="loadError">
                    <Button :label="t('notificationTemplate.backToList')" icon="pi pi-arrow-left" severity="secondary" variant="outlined" @click="router.push(listPath)" />
                </ErrorState>
                <ErrorState v-else :title="t('notificationMessage.messageLoadFailed')" :description="loadError" retry :retrying="loading" @retry="load" />
            </AppGadget>
        </template>

        <template v-else>
            <PageTitle :title="pageTitle">
                <template #actions>
                    <Button v-if="historyAvailable" type="button" :label="t('settingHistory.open')" icon="pi pi-history" size="small" severity="secondary" variant="outlined" @click="openHistory" />
                    <span v-if="canManage && saved && !preparing" v-tooltip.top="copyBlockedReason || undefined" class="inline-flex">
                        <Button type="button" :label="t('notificationTemplate.copyCreate')" icon="pi pi-copy" size="small" severity="secondary" variant="outlined" :disabled="Boolean(copyBlockedReason)" :aria-description="copyBlockedReason || undefined" @click="copySaved" />
                    </span>
                    <span v-if="canManage && saved && !saved.builtin && !preparing" v-tooltip.top="deleteBlockedReason || undefined" class="inline-flex">
                        <Button type="button" :label="t('common.delete')" icon="pi pi-trash" size="small" severity="danger" variant="outlined" :disabled="Boolean(deleteBlockedReason) || deleting" :loading="deleting" :aria-description="deleteBlockedReason || undefined" @click="confirmDelete" />
                    </span>
                    <Button :label="t('notificationTemplate.backToList')" icon="pi pi-arrow-left" size="small" severity="secondary" variant="outlined" @click="router.push(listPath)" />
                </template>
            </PageTitle>
            <div class="-mt-2 flex flex-wrap items-center gap-2">
                <Tag :value="UtilNotificationTemplateMessages.channelLabel(t, channel)" :icon="UtilNotificationTemplateMessages.channelIcon(channel)" severity="secondary" class="message-tag text-[11px]!" />
                <Tag v-if="preparing" :value="t('notificationMessage.preparingTag')" severity="secondary" class="message-tag text-[11px]!" />
                <Tag :value="t('notificationTemplate.audienceTag', { audience: UtilNotificationTemplateMessages.audienceLabel(t, target.audience) })" :icon="UtilNotificationTemplateMessages.audienceIcon(target.audience)" severity="secondary" class="message-tag text-[11px]!" />
                <Tag v-if="saved?.builtin" :value="t('notificationTemplate.builtinTag')" icon="pi pi-lock" severity="secondary" class="message-tag text-[11px]!" />
                <Tag v-if="target.security" :value="t('notificationMessage.securityTag')" icon="pi pi-shield" severity="warn" class="message-tag text-[11px]!" />
                <Tag v-for="label in usage" :key="label" :value="label" severity="info" class="message-tag text-[11px]!" />
                <span v-if="saved?.updatedAt" class="text-xs text-muted-color">{{ saved.updatedByName ? t('notificationTemplate.updatedBy', { at: time.full(saved.updatedAt), name: saved.updatedByName }) : time.full(saved.updatedAt) }}</span>
            </div>

            <Message v-if="preparing" severity="secondary" :closable="false" size="small">
                <span>{{ t('notificationMessage.kakaoPreparingBody') }}</span>
                <NuxtLink to="/settings/notification-channels" class="ml-2 font-medium underline underline-offset-2">{{ t('notification.channelsLink') }}</NuxtLink>
            </Message>
            <Message v-else-if="saved?.builtin" severity="secondary" :closable="false" size="small">
                <span>{{ t('notificationMessage.builtinReadOnly') }}</span>
                <Button v-if="canManage" type="button" :label="t('notificationTemplate.copyCreate')" icon="pi pi-copy" size="small" variant="link" class="ml-1 p-0!" :disabled="Boolean(copyBlockedReason)" @click="copySaved" />
            </Message>
            <Message v-else-if="!canManage" severity="info" :closable="false" size="small">{{ t('notificationTemplate.readOnly') }}</Message>
            <Message v-if="target.security && isEmail && !readonly" severity="warn" :closable="false" size="small">{{ t('notificationMessage.securityHint', { names: requiredNames }) }}</Message>

            <form class="flex flex-col gap-4" novalidate @submit.prevent="save">
                <div class="grid items-start gap-4 xl:grid-cols-[minmax(0,1fr)_minmax(0,26rem)]">
                    <!-- 왼쪽 — 편집 칸 -->
                    <AppGadget :title="t('notificationMessage.contentTitle')">
                        <div :key="formKey" class="flex flex-col gap-4">
                            <div class="grid gap-4 sm:grid-cols-2">
                                <LabeledField :label="t('notificationTemplate.nameLabel')" :hint="t('notificationTemplate.maxHint', { max: UtilMailTemplate.NAME_MAX })" :error="fieldError('name')">
                                    <template #default="{ id, describedBy, invalid }">
                                        <InputText :id="id" v-model="form.name" name="name" autocomplete="off" :readonly="readonly || saving" :invalid="invalid" :aria-describedby="describedBy" aria-required="true" fluid />
                                    </template>
                                </LabeledField>

                                <!-- 대상 — 새로 만들 때만 고른다(복사는 원본 대상으로 고정) -->
                                <LabeledField :label="t('notificationMessage.targetLabel')" :help="pickable ? t('notificationMessage.targetPickHelp') : readonly ? '' : t('notificationMessage.targetLocked')">
                                    <template #aside>
                                        <NuxtLink :to="UtilNotificationTemplateMessages.targetPath(target)" class="text-xs text-primary hover:underline">{{ t('notificationMessage.openTarget') }}</NuxtLink>
                                    </template>
                                    <template #default="{ id, describedBy }">
                                        <Select v-if="pickable" :model-value="target.code" :input-id="id" :options="targetOptions" option-label="label" option-value="value" option-group-label="label" option-group-children="items" filter :disabled="saving || switching" :aria-describedby="describedBy" fluid @update:model-value="pickTarget" />
                                        <InputText v-else :id="id" :model-value="`${target.name} · ${UtilNotificationTemplateMessages.targetGroupLabel(t, target.type, target.audience)}`" readonly :aria-describedby="describedBy" fluid />
                                    </template>
                                </LabeledField>

                                <!-- 채널 — 새로 만들 때만(대상이 나가는 채널 중에서, 준비 중 채널은 고를 수 없다) -->
                                <div class="flex flex-col gap-1.5 sm:col-span-2">
                                    <span :id="channelLabelId" class="text-sm font-medium">{{ t('notificationMessage.channelLabel') }}</span>
                                    <SelectButton v-if="pickable" :model-value="channel" :options="channelOptions" option-label="label" option-value="value" option-disabled="disabled" :allow-empty="false" size="small" :disabled="saving || switching" :aria-labelledby="channelLabelId" class="self-start" @update:model-value="pickChannel" />
                                    <span v-else class="inline-flex items-center gap-1.5 text-sm">
                                        <i :class="UtilNotificationTemplateMessages.channelIcon(channel)" class="text-xs text-muted-color" aria-hidden="true" />
                                        {{ UtilNotificationTemplateMessages.channelLabel(t, channel) }}
                                    </span>
                                    <p class="m-0 text-xs text-muted-color">{{ t(`notificationMessage.channelHelp.${channel}`) }}</p>
                                    <p v-if="pickable && hasPreparingChannel" class="m-0 text-xs text-muted-color">{{ t('notificationMessage.kakaoPreparing') }}</p>
                                    <p v-if="fieldError('channel')" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ fieldError('channel') }}</p>
                                </div>

                                <!-- 받는 사람 — 대상에서 정해진다(바꿀 수 없다) -->
                                <LabeledField :label="t('notificationTemplate.audienceLabel')" :help="t('notificationMessage.audienceHelp')">
                                    <template #default="{ id, describedBy }">
                                        <InputText :id="id" :model-value="UtilNotificationTemplateMessages.audienceLabel(t, target.audience)" readonly :aria-describedby="describedBy" fluid />
                                    </template>
                                </LabeledField>

                                <div class="flex flex-col gap-1.5">
                                    <span :id="localeLabelId" class="text-sm font-medium">{{ t('notificationTemplate.localeLabel') }}</span>
                                    <SelectButton :model-value="form.locale" :options="localeOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :disabled="readonly || saving || localeLocked || switching" :aria-labelledby="localeLabelId" class="self-start" @update:model-value="pickLocale" />
                                    <p v-if="fieldError('locale')" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ fieldError('locale') }}</p>
                                    <p v-else-if="localeLocked && !readonly" class="m-0 text-xs text-muted-color">{{ t('notificationMessage.localeLocked') }}</p>
                                </div>

                                <!-- 템플릿 고르기 — 앱 알림이 아닌 채널(그 채널의 템플릿 — 겉모양) -->
                                <LabeledField v-if="templateKind" class="sm:col-span-2" :label="t('notificationMessage.templateLabel')" :help="t(`notificationMessage.templateHelp.${channel}`)" :error="fieldError('templateId')">
                                    <template #aside>
                                        <NuxtLink v-if="form.templateId" :to="UtilNotificationTemplateMessages.templatePath(templateKind, form.templateId)" class="text-xs text-primary hover:underline">{{ t('notificationMessage.openTemplate') }}</NuxtLink>
                                    </template>
                                    <template #default="{ id, describedBy, invalid }">
                                        <Select v-model="form.templateId" :input-id="id" :options="templateOptions" option-label="label" option-value="value" :disabled="readonly || saving" :invalid="invalid" :aria-describedby="describedBy" fluid />
                                    </template>
                                </LabeledField>
                            </div>

                            <!-- 제목 — 앱 알림 · 이메일 · Slack · Mattermost(카카오톡은 승인 본문 그대로) -->
                            <LabeledField v-if="channel !== 'KAKAO'" :label="subjectLabel" :hint="t('notificationTemplate.maxHint', { max: subjectMax })" :help="t(`notificationMessage.subjectHelp.${channel}`)" :error="fieldError('subject')">
                                <template #aside>
                                    <NotificationVariableMenu v-if="!readonly" :variables="variables" @pick="insertSubject" />
                                </template>
                                <template #default="{ id, describedBy, invalid }">
                                    <InputText :id="id" ref="subjectInput" v-model="form.subject" name="subject" autocomplete="off" :readonly="readonly || saving" :invalid="invalid" :aria-describedby="describedBy" aria-required="true" fluid @keydown.enter.prevent />
                                </template>
                            </LabeledField>

                            <!-- 본문 — 이메일은 간단 에디터 + 버튼, 앱 알림·Slack·Mattermost 는 글 칸, 카카오톡은 없다 -->
                            <div v-if="isEmail" class="flex flex-col gap-1.5">
                                <div class="flex items-baseline justify-between gap-2">
                                    <span :id="bodyLabelId" class="text-sm font-medium">{{ t('notificationMessage.bodyLabel') }}</span>
                                    <NotificationVariableMenu v-if="!readonly" :variables="variables" @pick="insertEditor" />
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
                                    :placeholder="t('notificationMessage.bodyPlaceholder')"
                                    @ready="onEditorReady"
                                />
                                <div :id="bodyMessageId" aria-live="polite" class="text-xs leading-relaxed">
                                    <p v-if="fieldError('body')" role="alert" class="m-0 text-red-600 dark:text-red-400">{{ fieldError('body') }}</p>
                                    <p v-else class="m-0 text-muted-color">{{ t('notificationMessage.bodyHelp.EMAIL') }}</p>
                                </div>
                            </div>
                            <LabeledField v-else-if="channel !== 'KAKAO'" :label="bodyLabel" :hint="t('notificationTemplate.maxHint', { max: bodyMax })" :help="t(`notificationMessage.bodyHelp.${channel}`)" :error="fieldError('body')">
                                <template #aside>
                                    <NotificationVariableMenu v-if="!readonly" :variables="variables" @pick="insertBodyText" />
                                </template>
                                <template #default="{ id, describedBy, invalid }">
                                    <Textarea :id="id" ref="bodyInput" v-model="form.body" name="body" :rows="isChat ? 6 : 3" auto-resize :readonly="readonly || saving" :invalid="invalid" :aria-describedby="describedBy" :class="isChat ? 'font-mono text-[13px]!' : ''" fluid />
                                </template>
                            </LabeledField>
                            <p v-else class="m-0 rounded-lg border border-dashed border-(--line) p-3 text-xs leading-relaxed text-muted-color">{{ t('notificationMessage.kakaoNoBody') }}</p>

                            <!-- 「버튼」 블록 — 이메일만, 링크 변수로만 가는 단추 하나 -->
                            <section v-if="isEmail" class="flex flex-col gap-3 rounded-lg border border-(--line) p-3" :aria-labelledby="buttonTitleId">
                                <div class="flex flex-wrap items-center gap-2">
                                    <h3 :id="buttonTitleId" class="m-0 text-sm font-medium">{{ t('notificationMessage.buttonTitle') }}</h3>
                                    <span class="text-xs text-muted-color">{{ linkVariables.length ? t('notificationMessage.buttonHint') : t('notificationMessage.buttonNoLink') }}</span>
                                    <div v-if="!readonly" class="ml-auto">
                                        <Button v-if="!button" type="button" :label="t('notificationMessage.buttonAdd')" icon="pi pi-plus" size="small" severity="secondary" variant="outlined" :disabled="!linkVariables.length || saving" @click="addButton" />
                                        <Button v-else type="button" :label="t('notificationMessage.buttonRemove')" icon="pi pi-times" size="small" severity="secondary" variant="text" :disabled="saving" @click="removeButton" />
                                    </div>
                                </div>
                                <div v-if="button" class="grid gap-3 sm:grid-cols-2">
                                    <LabeledField :label="t('notificationMessage.buttonLabel')" :hint="t('notificationTemplate.maxHint', { max: UtilMailTemplate.BUTTON_LABEL_MAX })">
                                        <template #default="{ id, describedBy, invalid }">
                                            <InputText :id="id" v-model="button.label" name="buttonLabel" autocomplete="off" :readonly="readonly || saving" :invalid="invalid || Boolean(fieldError('button'))" :aria-describedby="describedBy" fluid />
                                        </template>
                                    </LabeledField>
                                    <LabeledField :label="t('notificationMessage.buttonLink')">
                                        <template #default="{ id, describedBy, invalid }">
                                            <Select v-model="button.link" :input-id="id" :options="linkOptions" option-label="label" option-value="value" :disabled="readonly || saving" :invalid="invalid || Boolean(fieldError('button'))" :aria-describedby="describedBy" fluid />
                                        </template>
                                    </LabeledField>
                                </div>
                                <p v-if="fieldError('button')" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ fieldError('button') }}</p>
                                <p v-else-if="button" class="m-0 text-xs text-muted-color">{{ t('notificationMessage.buttonPlaceHint', { token: buttonToken }) }}</p>
                            </section>
                        </div>
                    </AppGadget>

                    <!-- 오른쪽 — 미리보기(고른 템플릿에 이 문구를 씌운 것) -->
                    <div class="flex min-w-0 flex-col gap-4 xl:sticky xl:top-4">
                        <AppGadget :title="t('notificationTemplate.previewTitle')">
                            <template v-if="canManage && (isEmail || isChat) && !preparing" #actions>
                                <span v-tooltip.top="testBlockedReason || undefined" class="inline-flex">
                                    <Button v-if="isEmail" type="button" :label="t('notificationMessage.testButton')" icon="pi pi-send" size="small" severity="secondary" variant="outlined" :disabled="Boolean(testBlockedReason) || saving" :aria-description="testBlockedReason || undefined" @click="openTest" />
                                    <Button v-else type="button" :label="t('notificationMessage.chatTestButton')" icon="pi pi-send" size="small" severity="secondary" variant="outlined" :disabled="Boolean(testBlockedReason) || saving" :aria-description="testBlockedReason || undefined" @click="openChatTest" />
                                </span>
                            </template>

                            <MailTemplatePreviewFrame v-if="isEmail" :subject="preview.subject" :html="preview.html" :loading="preview.loading" :error="preview.error" height="34rem" />

                            <!-- Slack · Mattermost 메시지 카드(고른 템플릿 + 이 문구 + 이벤트 심각도) -->
                            <template v-else-if="isChat">
                                <ChatMessageCard v-if="chatCard" :card="chatCard" :note="t('notificationMessage.chatPreviewNote', { tool: UtilNotificationTemplateMessages.channelLabel(t, channel) })" />
                                <p v-else class="m-0 text-xs text-muted-color">{{ chatTemplateLoading ? t('common.loading') : t('notificationMessage.pickTemplateFirst') }}</p>
                            </template>

                            <!-- 카카오톡 알림톡 말풍선(고른 알림톡 템플릿의 승인 본문을 이 대상의 견본 값으로) -->
                            <template v-else-if="channel === 'KAKAO'">
                                <KakaoMessageBubble
                                    v-if="kakaoTemplate"
                                    class="opacity-80"
                                    :channel-name="kakaoTemplate.channelName"
                                    :emphasis="kakaoTemplate.emphasis"
                                    :body="kakaoBody"
                                    :buttons="kakaoTemplate.buttons.map((item) => item.name)"
                                    :note="t('notificationMessage.kakaoPreviewNote')"
                                />
                                <p v-else class="m-0 text-xs text-muted-color">{{ kakaoTemplateLoading ? t('common.loading') : t('notificationMessage.pickTemplateFirst') }}</p>
                            </template>

                            <!-- 앱 알림 한 줄 — 운영자는 관리자 종 서랍, 회원은 회원 알림함 -->
                            <div v-else class="flex flex-col gap-2">
                                <p class="m-0 text-xs font-medium text-muted-color">{{ target.audience === 'OPERATOR' ? t('notificationMessage.inAppDrawerLook') : t('notificationMessage.inAppInboxLook') }}</p>
                                <div class="flex items-start gap-3 rounded-lg border border-(--line) p-3" :class="target.audience === 'OPERATOR' ? 'bg-primary-50/50 dark:bg-primary-950/30' : ''">
                                    <span class="mt-0.5 grid size-8 shrink-0 place-items-center rounded-full bg-surface-100 text-muted-color dark:bg-surface-800" aria-hidden="true">
                                        <i :class="target.audience === 'OPERATOR' ? 'pi pi-bell' : 'pi pi-comment'" />
                                    </span>
                                    <span class="flex min-w-0 flex-1 flex-col gap-0.5">
                                        <span class="flex items-center gap-2">
                                            <span class="min-w-0 flex-1 truncate text-sm font-semibold">{{ inAppPreview.title || t('notificationMessage.inAppEmptyTitle') }}</span>
                                            <span class="size-2 shrink-0 rounded-full bg-primary" aria-hidden="true" />
                                        </span>
                                        <span v-if="inAppPreview.body" class="line-clamp-2 text-xs text-muted-color">{{ inAppPreview.body }}</span>
                                        <span class="text-xs text-muted-color">{{ target.area ? UtilNotificationMessages.areaLabel(t, target.area) : '' }} · {{ t('notificationTemplate.chatNow') }}</span>
                                    </span>
                                </div>
                                <p class="m-0 text-xs text-muted-color">{{ t('notificationMessage.inAppPreviewNote') }}</p>
                            </div>
                        </AppGadget>
                    </div>
                </div>

                <StickySaveBar v-if="!readonly" submit :revertable="!isNew" :dirty="isNew || dirty" :saving="saving" :disabled="invalidCount > 0 || switching" :count="changeCount" :error-count="invalidCount" :error="formError" @revert="revert">
                    <template #extra>
                        <Button v-if="defaultFor(form.locale)" type="button" :label="t('notificationMessage.resetDefault')" icon="pi pi-replay" size="small" severity="secondary" variant="text" :disabled="saving" @click="resetToDefault" />
                    </template>
                </StickySaveBar>
            </form>
        </template>

        <SettingHistoryDrawer v-model:visible="historyOpen" :hint="t('notificationMessage.historyHint')">
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

        <MailTestDialog v-model:visible="testOpen" v-model:to="testTo" :help="t('notificationMessage.testHelp')" :to-error="testToError" :error="testError" :testing="testing" :result="testResult" @send="runTest" />

        <!-- Slack · Mattermost — 알림 도구로 시험 보내기(도구 목록은 실제 API, 보내기는 목업) -->
        <Dialog v-model:visible="chatTestOpen" modal :header="t('notificationMessage.chatTestTitle', { tool: UtilNotificationTemplateMessages.channelLabel(t, channel) })" class="w-[28rem] max-w-[calc(100vw-2rem)]">
            <form class="flex flex-col gap-3" novalidate @submit.prevent="runChatTest">
                <p v-if="toolsLoading && !chatTools.length" class="m-0 text-sm text-muted-color">{{ t('common.loading') }}</p>
                <Message v-else-if="!chatTools.length" severity="warn" :closable="false" size="small">
                    <span>{{ t('notificationMessage.chatTestNoTools', { tool: UtilNotificationTemplateMessages.channelLabel(t, channel) }) }}</span>
                    <NuxtLink to="/settings/notification-channels" class="ml-2 font-medium underline underline-offset-2">{{ t('notification.channelsLink') }}</NuxtLink>
                </Message>
                <LabeledField v-else :label="t('notificationMessage.chatTestTool')" :help="t('notificationMessage.chatTestHelp')" :error="chatTestToolError">
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
                    <Button type="submit" :label="t('notificationMessage.chatTestSend')" icon="pi pi-send" :loading="chatTesting" :disabled="!chatTestTool" />
                </div>
            </form>
        </Dialog>
    </div>
</template>

<script setup lang="ts">
import type { MailTestDialogResult } from '~/components/mail/MailTestDialog.vue';
import { ApiError } from '~/composables/common/useApiClient';
import { mockTemplateCan } from '~/composables/template/api/mock/notification-template-mock';
import type { ChatTemplateListItemView, ChatTemplateType, ChatTemplateView } from '~/composables/template/api/useChatTemplateApi.types';
import type { KakaoTemplateListItemView, KakaoTemplateView } from '~/composables/template/api/useKakaoTemplateApi.types';
import type { MailTemplateListItemView } from '~/composables/template/api/useMailTemplateApi.types';
import type { ChatMessageTestResultView, MessageAudience, MessageChannel, MessageLocale, MessageTargetDetailView, MessageTargetView, MessageView } from '~/composables/template/api/useNotificationMessageApi.types';
import { MESSAGE_LIMIT, MESSAGE_LOCALES } from '~/composables/template/api/useNotificationMessageApi.types';
import UtilChatTemplate from '~/utils/util-chat-template';
import UtilKakaoTemplate from '~/utils/util-kakao-template';
import type { ContentProblem, MailButton } from '~/utils/util-mail-template';
import UtilMailSettingsMessages from '~/utils/util-mail-settings-messages';
import UtilMailTemplate from '~/utils/util-mail-template';
import UtilNotificationChannelMessages from '~/utils/util-notification-channel-messages';
import type { NotificationMessageField } from '~/utils/util-notification-message';
import UtilNotificationMessage from '~/utils/util-notification-message';
import UtilNotificationMessages from '~/utils/util-notification-messages';
import UtilNotificationTemplateMessages from '~/utils/util-notification-template-messages';
import UtilValidation from '~/utils/util-validation';

const { t } = useI18n();

const api = useNotificationMessageApi();
const mailApi = useMailTemplateApi();
const chatApi = useChatTemplateApi();
const kakaoApi = useKakaoTemplateApi();
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
type Field = NotificationMessageField | 'button' | 'locale' | 'templateId' | 'channel';

/* ── 받은 것 ── */

const targets = ref<MessageTargetView[]>([]);
const mailTemplates = ref<MailTemplateListItemView[]>([]);
const chatTemplates = ref<Record<ChatTemplateType, ChatTemplateListItemView[]>>({ SLACK: [], MATTERMOST: [] });
const kakaoTemplates = ref<KakaoTemplateListItemView[]>([]);
/** 저장된 문구(새로 만드는 중이면 null) */
const saved = ref<MessageView | null>(null);
/** 복사 원본(`?from=`) */
const source = ref<MessageView | null>(null);
/** 대상 상세 — 채널마다 기본 문구(되돌리기·새로 만들기 내용), 쓰는 곳(언어 잠금·삭제 잠금) */
const detail = ref<MessageTargetDetailView | null>(null);
/** 지금 채널 */
const channel = ref<MessageChannel>('EMAIL');
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
const isChat = computed(() => UtilNotificationMessage.isChat(channel.value));
/** 준비 중 채널(카카오톡) — 보기만 한다 */
const preparing = computed(() => Boolean(channelSummary.value?.preparing));
/** 이 채널이 고르는 템플릿의 유형(앱 알림은 null) */
const templateKind = computed(() => (channel.value === 'IN_APP' ? null : UtilNotificationTemplateMessages.templateKindOf(channel.value)));
/** 주소의 받는 사람(`?audience=` — 받는 사람 목록의 「새 문구」가 붙인다). 새로 만들기는 이 받는 사람의 대상으로 시작한다 */
const queryAudience = computed<MessageAudience | null>(() => (route.query.audience === 'MEMBER' || route.query.audience === 'OPERATOR' ? route.query.audience : null));
/** 이 문구가 속한 받는 사람 목록(메뉴 「문구 › 운영자」 · 「문구 › 회원」) — 대상이 정한다. 받기 전·못 받았으면 주소 값, 그것도 없으면 운영자 */
const listAudience = computed<MessageAudience>(() => target.value?.audience ?? queryAudience.value ?? 'OPERATOR');
/** 「목록으로」 · 지운 뒤 갈 곳 — 그 받는 사람의 문구 목록 */
const listPath = computed(() => UtilNotificationTemplateMessages.messageListPath(listAudience.value));

/** 채널마다 새 문구가 시작할 템플릿 — 이메일은 받는 사람의 기본 메일 템플릿, Slack·Mattermost 는 그 종류의 기본 템플릿, 카카오톡은 첫 알림톡 템플릿 */
function defaultTemplateIdOf(next: MessageChannel, audience: MessageAudience): string {
    if (next === 'EMAIL') return mailTemplates.value.find((item) => item.builtin && item.audience === audience)?.id ?? '';
    if (next === 'SLACK' || next === 'MATTERMOST') return chatTemplates.value[next].find((item) => item.builtin)?.id ?? '';
    if (next === 'KAKAO') return kakaoTemplates.value[0]?.id ?? '';
    return '';
}
/** 이 채널·받는 사람이 그 템플릿을 고를 수 있는가 */
function templateUsable(templateId: string, next: MessageChannel, audience: MessageAudience): boolean {
    if (next === 'EMAIL') {
        const found = mailTemplates.value.find((item) => item.id === templateId);
        return Boolean(found && UtilMailTemplate.templateFits(found.audience, audience));
    }
    if (next === 'SLACK' || next === 'MATTERMOST') return chatTemplates.value[next].some((item) => item.id === templateId);
    if (next === 'KAKAO') return kakaoTemplates.value.some((item) => item.id === templateId);
    return templateId === '';
}
/** 대상·채널을 고를 수 있다 — 새로 만들기(복사가 아닐 때)만 */
const pickable = computed(() => isNew.value && !source.value);
const readonly = computed(() => !canManage.value || Boolean(saved.value?.builtin) || preparing.value);
const historyAvailable = computed(() => Boolean(saved.value && !saved.value.builtin));
const requiredNames = computed(() => variables.value.filter((v) => v.required).map((v) => UtilMailTemplate.token(v.name)).join(', '));
const pageTitle = computed(() => (isNew.value ? t('notificationMessage.newTitle') : (saved.value?.name ?? t('notificationMessage.editTitle'))));

useSeoMeta({ title: () => pageTitle.value });

const localeOptions = computed(() => MESSAGE_LOCALES.map((value) => ({ value, label: UtilNotificationTemplateMessages.localeLabel(t, value) })));
/** 채널 고르기 — 대상이 나가는 채널(준비 중 채널은 고를 수 없게) */
const channelOptions = computed(() =>
    (target.value?.channels ?? []).map((item) => ({ value: item.channel, label: item.preparing ? t('notificationMessage.channelPreparingOption', { channel: UtilNotificationTemplateMessages.channelLabel(t, item.channel) }) : UtilNotificationTemplateMessages.channelLabel(t, item.channel), disabled: item.preparing })),
);
const hasPreparingChannel = computed(() => (target.value?.channels ?? []).some((item) => item.preparing));
/** 템플릿 고르기 — 이 채널의 템플릿(메일은 받는 사람이 같거나 `ANY` 인 것만, 기본 먼저 — 받은 차례) */
const templateOptions = computed(() => {
    const audience = target.value?.audience;
    if (!audience) return [];
    const builtinLabel = (name: string) => t('notificationMessage.templateBuiltinOption', { name });
    if (channel.value === 'EMAIL') {
        return mailTemplates.value
            .filter((item) => UtilMailTemplate.templateFits(item.audience, audience))
            .map((item) => ({ value: item.id, label: item.builtin ? builtinLabel(item.name) : item.audience === 'ANY' ? t('notificationMessage.templateAnyOption', { name: item.name }) : item.name }));
    }
    if (channel.value === 'SLACK' || channel.value === 'MATTERMOST') return chatTemplates.value[channel.value].map((item) => ({ value: item.id, label: item.builtin ? builtinLabel(item.name) : item.name }));
    if (channel.value === 'KAKAO') return kakaoTemplates.value.map((item) => ({ value: item.id, label: `${item.name} · ${item.templateCode}` }));
    return [];
});
/** 대상 고르기 — 이 받는 사람의 대상만(받는 사람은 메뉴가 정한다), 운영자 이벤트 · 영역(회원 이벤트) / 필수 안내 메일 묶음 */
const targetOptions = computed(() => {
    const groups = new Map<string, { label: string; items: { value: string; label: string }[] }>();
    for (const item of targets.value) {
        if (item.audience !== listAudience.value) continue;
        const label = UtilNotificationTemplateMessages.targetGroupLabel(t, item.type, item.audience, item.area);
        const group = groups.get(label) ?? { label, items: [] };
        group.items.push({ value: item.code, label: item.name });
        groups.set(label, group);
    }
    return [...groups.values()];
});
const linkVariables = computed(() => variables.value.filter((v) => v.link));
const linkOptions = computed(() => linkVariables.value.map((v) => ({ value: v.name, label: `${UtilMailTemplate.token(v.name)} · ${v.description}` })));

const subjectLabel = computed(() => (channel.value === 'IN_APP' ? t('notificationMessage.inAppTitleLabel') : isChat.value ? t('notificationMessage.chatTitleLabel') : t('notificationMessage.subjectLabel')));
const subjectMax = computed(() => (channel.value === 'IN_APP' ? UtilNotificationMessage.IN_APP_TITLE_MAX : isChat.value ? UtilNotificationMessage.CHAT_TITLE_MAX : UtilMailTemplate.SUBJECT_MAX));
const bodyLabel = computed(() => (channel.value === 'IN_APP' ? t('notificationMessage.inAppBodyLabel') : t('notificationMessage.bodyLabel')));
const bodyMax = computed(() => (channel.value === 'IN_APP' ? UtilNotificationMessage.IN_APP_BODY_MAX : UtilNotificationMessage.CHAT_BODY_MAX));

/* ── 편집 값 ── */

interface Baseline {
    name: string;
    locale: MessageLocale;
    templateId: string;
    subject: string;
    body: string;
    editorHtml: string;
    button: MailButton | null;
}

const form = reactive<{ name: string; locale: MessageLocale; templateId: string; subject: string; body: string }>({ name: '', locale: 'ko', templateId: '', subject: '', body: '' });
/** 이메일 본문(에디터 값)과 버튼 — 그 밖의 채널은 `form.body` */
const editorHtml = ref('');
const button = ref<MailButton | null>(null);
/** 처음 값 — 「고쳤는가」의 기준. 이메일 본문은 에디터가 정규화한 값으로 바꿔 둔다(`onEditorReady`) */
const baseline = ref<Baseline>({ name: '', locale: 'ko', templateId: '', subject: '', body: '', editorHtml: '', button: null });
const formKey = ref(0);
const tried = ref(false);
const serverErrors = ref<Record<string, string>>({});
const formError = ref('');
const saving = ref(false);

/** 문구 내용(제목·본문·버튼)을 칸에 넣는다 */
function setContent(message: Pick<MessageView, 'subject' | 'body'>): void {
    form.subject = message.subject;
    if (isEmail.value) {
        const split = UtilMailTemplate.splitBody(message.body);
        editorHtml.value = split.editorHtml;
        button.value = split.button ? { ...split.button } : null;
        form.body = '';
    } else {
        form.body = message.body;
        editorHtml.value = '';
        button.value = null;
    }
}

function setBaseline(): void {
    baseline.value = { name: form.name, locale: form.locale, templateId: form.templateId, subject: form.subject, body: form.body, editorHtml: editorHtml.value, button: button.value ? { ...button.value } : null };
    tried.value = false;
    serverErrors.value = {};
    formError.value = '';
    formKey.value += 1;
}

function copyName(from: MessageView): string {
    if (from.builtin) return t('notificationMessage.copyNameBuiltin', { target: target.value?.name ?? '' }).slice(0, UtilMailTemplate.NAME_MAX);
    return t('notificationTemplate.copyName', { name: from.name }).slice(0, UtilMailTemplate.NAME_MAX);
}

/** 이 대상·채널·언어의 기본 문구(카카오톡은 없다) */
function defaultFor(locale: MessageLocale): MessageView | undefined {
    const view = channelDetail.value;
    const wanted = view?.defaults[locale];
    return wanted ? view?.messages.find((item) => item.id === wanted) : undefined;
}

/** 칸을 채운다 — 저장된 것 · 복사 원본 · 새로 만들기(그 대상·채널·언어의 기본 문구 + 그 채널의 기본 템플릿) */
function fill(): void {
    if (saved.value) {
        form.name = saved.value.name;
        form.locale = saved.value.locale;
        form.templateId = saved.value.templateId ?? '';
        setContent(saved.value);
    } else if (source.value) {
        form.name = copyName(source.value);
        form.locale = source.value.locale;
        form.templateId = source.value.templateId ?? '';
        setContent(source.value);
    } else {
        form.name = '';
        form.templateId = target.value ? defaultTemplateIdOf(channel.value, target.value.audience) : '';
        const seed = defaultFor(form.locale);
        if (seed) setContent(seed);
        else setContent({ subject: '', body: '' });
    }
    setBaseline();
}

function onEditorReady(html: string): void {
    if (editorHtml.value === baseline.value.editorHtml) {
        baseline.value = { ...baseline.value, editorHtml: html };
        editorHtml.value = html;
    }
}

/** 저장할 본문 — 이메일은 에디터 값 + 버튼 블록, 카카오톡은 빈 글자 */
const composed = computed(() => (isEmail.value ? UtilMailTemplate.joinBody(editorHtml.value, button.value) : channel.value === 'KAKAO' ? '' : form.body));

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
    if (templateKind.value && form.templateId !== b.templateId) n += 1;
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
    () => form.templateId,
    () => clearServer('templateId'),
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
    if (tokens > 0 && !button.value && linkVariables.value[0]) button.value = { label: t('notificationMessage.buttonDefaultLabel'), link: linkVariables.value[0].name };
    else if (tokens === 0 && button.value) button.value = null;
});

/* ── 검사 ── */

/** 채널마다의 화면 검사 — 이메일 문제의 `bodyHtml` 은 `body` 칸이다 */
const problems = computed<Partial<Record<Field, ContentProblem>>>(() => {
    const current = target.value;
    if (!current) return {};
    const rules = { audience: current.audience, variables: variables.value };
    if (isEmail.value) {
        const found = UtilMailTemplate.validateMessage({ name: form.name, subject: form.subject, bodyHtml: composed.value }, { ...rules, security: current.security });
        const { bodyHtml, ...rest } = found;
        return bodyHtml ? { ...rest, body: bodyHtml } : rest;
    }
    if (channel.value === 'KAKAO') return UtilNotificationMessage.validateKakao({ name: form.name });
    if (isChat.value) return UtilNotificationMessage.validateChat({ name: form.name, subject: form.subject, body: form.body }, rules);
    return UtilNotificationMessage.validateInApp({ name: form.name, subject: form.subject, body: form.body }, rules);
});

function fieldError(field: Field): string {
    const server = serverErrors.value[field];
    if (server) return server;
    if (field === 'locale' || field === 'channel' || readonly.value) return '';
    if (field === 'templateId') return !templateKind.value || form.templateId ? '' : tried.value ? t('notificationMessage.templateRequired') : '';
    const problem = problems.value[field];
    if (!problem) return '';
    if (problem.code === 'REQUIRED' && !tried.value) return '';
    return UtilNotificationTemplateMessages.problem(t, field === 'button' ? 'button' : field, problem);
}

const FIELDS: readonly Field[] = ['name', 'channel', 'locale', 'templateId', 'subject', 'body', 'button'];
const invalidCount = computed(() => FIELDS.filter((field) => Boolean(fieldError(field))).length);

/** 시험은 템플릿을 골랐고 제목·본문이 맞을 때만 — 이름은 상관없다 */
const testBlockedReason = computed(() => {
    const p = problems.value;
    if (p.subject || p.body || p.button || (templateKind.value && !form.templateId)) return t('notificationMessage.testNeedsValid');
    return '';
});

/** 그 언어의 쓸 문구로 쓰는 중이면 언어를 바꿀 수 없다 */
const localeLocked = computed(() => Boolean(saved.value && channelDetail.value?.selected[saved.value.locale] === saved.value.id));

/** 저장된 상태에서 이 문구를 쓰는 곳 */
const usage = computed(() => {
    const view = channelDetail.value;
    const message = saved.value;
    if (!view || !message) return [];
    const out: string[] = [];
    for (const locale of MESSAGE_LOCALES) if (view.selected[locale] === message.id) out.push(t('notificationMessage.usedAsDefault', { locale: UtilNotificationTemplateMessages.localeLabel(t, locale) }));
    const rules = view.rules.filter((rule) => rule.messageId === message.id).length;
    if (rules > 0) out.push(t('notificationMessage.usedByRules', { count: rules }));
    return out;
});
const deleteBlockedReason = computed(() => (usage.value.length ? t('notificationMessage.deleteInUse') : ''));
const copyBlockedReason = computed(() => {
    const count = (channelDetail.value?.messages ?? []).filter((item) => !item.builtin).length;
    return count >= MESSAGE_LIMIT ? t('notificationMessage.limitReached', { max: MESSAGE_LIMIT }) : '';
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
        const [targetList, mailList, slackList, mattermostList, kakaoList] = await Promise.all([api.targets(), mailApi.list(), chatApi.list('SLACK'), chatApi.list('MATTERMOST'), kakaoApi.list()]);
        let message: MessageView | null = null;
        let from: MessageView | null = null;
        let pick: MessageTargetView | undefined;
        let wantedChannel: string | null = null;
        if (!isNew.value) {
            message = await api.message(id.value);
            pick = targetList.find((item) => item.code === message?.target.code);
            wantedChannel = message.channel;
        } else {
            const fromId = queryText(route.query.from);
            if (fromId) {
                from = await api.message(fromId);
                pick = targetList.find((item) => item.code === from?.target.code);
                wantedChannel = from.channel;
            } else {
                // `?target=` 그 대상, 없으면 `?audience=` 받는 사람(없으면 모두) 중 `?channel=` 채널이 있는 첫 대상, 그것도 없으면 그 받는 사람의 첫 대상
                const wanted = queryText(route.query.target);
                wantedChannel = queryText(route.query.channel);
                const pool = queryAudience.value ? targetList.filter((item) => item.audience === queryAudience.value) : targetList;
                pick = targetList.find((item) => item.code === wanted) ?? pool.find((item) => item.channels.some((c) => c.channel === wantedChannel)) ?? pool[0];
            }
        }
        // 대상이 하나도 없으면 만들 수 없다(없는 문구와 같게 보인다)
        if (!pick) throw new ApiError({ status: 404, code: 'MESSAGE_TARGET_NOT_FOUND', message: t('notificationMessage.messagesEmptyBody') });
        const view = await api.target(pick.type, pick.code);
        if (mine !== loadSeq) return;
        targets.value = targetList;
        mailTemplates.value = mailList;
        chatTemplates.value = { SLACK: slackList, MATTERMOST: mattermostList };
        kakaoTemplates.value = kakaoList;
        saved.value = message;
        source.value = from;
        detail.value = view;
        // 새로 만들기는 준비 중 채널로 시작하지 않는다(그 채널을 원했으면 첫 채널로)
        const startable = (item: { channel: MessageChannel; preparing: boolean }) => !(isNew.value && item.preparing);
        const wantedSummary = view.summary.channels.find((item) => item.channel === wantedChannel && startable(item));
        channel.value = wantedSummary?.channel ?? (view.summary.channels.find(startable)?.channel as MessageChannel);
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

const switchConfirm = () => confirm({ title: t('notificationMessage.switchTargetTitle'), message: t('notificationMessage.switchTargetMessage'), confirmLabel: t('notificationMessage.switchConfirm'), tone: 'danger' });

async function pickTarget(next: string): Promise<void> {
    const item = targets.value.find((other) => other.code === next);
    if (!item || next === target.value?.code || switching.value) return;
    // 쓸 수 있는 변수가 달라 그 대상의 기본 내용으로 바꾼다 — 고친 것이 있으면 묻는다
    if (contentChanged.value && !(await switchConfirm())) return;
    switching.value = true;
    const prevAudience = target.value?.audience;
    try {
        detail.value = await api.target(item.type, item.code);
        // 채널은 그대로 두되 새 대상에 없거나 준비 중이면 첫 채널로
        if (!detail.value.summary.channels.some((c) => c.channel === channel.value && !c.preparing)) channel.value = detail.value.summary.channels.find((c) => !c.preparing)?.channel as MessageChannel;
        reseed(defaultFor(form.locale));
        adjustTemplate(prevAudience);
        void router.replace({ query: { ...route.query, target: next, channel: channel.value } });
    } catch (e) {
        toast.error(toApiError(e).message, 8000);
    } finally {
        switching.value = false;
    }
}

async function pickChannel(next: MessageChannel): Promise<void> {
    if (!next || next === channel.value || switching.value) return;
    if (UtilNotificationMessage.isPreparing(next)) return;
    if (contentChanged.value && !(await switchConfirm())) return;
    const prevChannel = channel.value;
    channel.value = next;
    reseed(defaultFor(form.locale));
    adjustTemplate(target.value?.audience, prevChannel);
    void router.replace({ query: { ...route.query, channel: next } });
}

/** 템플릿을 이 채널·받는 사람에 맞게 — 앱 알림은 비우고, 채널이 바뀌었거나 맞지 않거나 앞 받는 사람의 기본이었으면 지금의 기본으로 */
function adjustTemplate(prevAudience: MessageAudience | undefined, prevChannel: MessageChannel = channel.value): void {
    const audience = target.value?.audience;
    if (!audience) return;
    const untouched = baseline.value.templateId === form.templateId;
    const wasDefault = prevAudience !== undefined && form.templateId === defaultTemplateIdOf(prevChannel, prevAudience);
    if (!templateKind.value) form.templateId = '';
    else if (prevChannel !== channel.value || !form.templateId || (prevAudience !== audience && wasDefault) || !templateUsable(form.templateId, channel.value, audience)) form.templateId = defaultTemplateIdOf(channel.value, audience);
    if (untouched) baseline.value = { ...baseline.value, templateId: form.templateId };
}

async function pickLocale(next: MessageLocale): Promise<void> {
    if (!next || next === form.locale) return;
    form.locale = next;
    // 새로 만들기(복사 아님)에서 내용을 아직 안 고쳤으면 그 언어의 기본 내용으로 바꾼다
    if (pickable.value && !contentChanged.value) reseed(defaultFor(next));
}

/** 새로 만들기에서 내용만 다시 채운다 — 이름·언어·템플릿은 고친 값과 「고쳤는가」 기준을 그대로 둔다 */
function reseed(seed: MessageView | undefined): void {
    const prev = baseline.value;
    if (seed) setContent(seed);
    else setContent({ subject: '', body: '' });
    setBaseline();
    baseline.value = { ...baseline.value, name: prev.name, locale: prev.locale, templateId: prev.templateId };
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

/** 이메일 — 고칠 때마다 잠깐 기다렸다가 서버에서 받는다(고른 메일 템플릿으로) */
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
        const result = await api.preview({ target: { type: current.type, code: current.code }, locale: form.locale, subject: form.subject, bodyHtml: composed.value, templateId: form.templateId || undefined });
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

watch(() => [ready.value, target.value?.code, channel.value, form.locale, form.templateId, form.subject, composed.value] as const, schedulePreview);
onBeforeUnmount(() => {
    if (previewTimer) clearTimeout(previewTimer);
});

/** Slack·Mattermost·카카오톡 — 고른 템플릿 하나(값 전부)를 받아 둔다(같은 것은 다시 받지 않는다). 그리기는 화면이 한다 */
const chatTemplate = ref<ChatTemplateView | null>(null);
const chatTemplateLoading = ref(false);
const kakaoTemplate = ref<KakaoTemplateView | null>(null);
const kakaoTemplateLoading = ref(false);
let templateSeq = 0;

async function loadPickedTemplate(): Promise<void> {
    const mine = ++templateSeq;
    const picked = form.templateId;
    const now = channel.value;
    if (now === 'SLACK' || now === 'MATTERMOST') {
        if (!picked) chatTemplate.value = null;
        if (!picked || (chatTemplate.value?.id === picked && chatTemplate.value.type === now)) return;
        chatTemplateLoading.value = true;
        try {
            const found = await chatApi.get(now, picked);
            if (mine === templateSeq) chatTemplate.value = found;
        } catch {
            if (mine === templateSeq) chatTemplate.value = null;
        } finally {
            if (mine === templateSeq) chatTemplateLoading.value = false;
        }
    } else if (now === 'KAKAO') {
        if (!picked) kakaoTemplate.value = null;
        if (!picked || kakaoTemplate.value?.id === picked) return;
        kakaoTemplateLoading.value = true;
        try {
            const found = await kakaoApi.get(picked);
            if (mine === templateSeq) kakaoTemplate.value = found;
        } catch {
            if (mine === templateSeq) kakaoTemplate.value = null;
        } finally {
            if (mine === templateSeq) kakaoTemplateLoading.value = false;
        }
    }
}

watch(() => [ready.value, channel.value, form.templateId] as const, () => {
    if (ready.value) void loadPickedTemplate();
});

/** Slack·Mattermost 카드 — 고른 템플릿(저장된 값)에 이 문구(고치는 중인 값)를 씌운다. 색·그림은 이벤트의 심각도 */
const chatCard = computed(() => {
    const template = chatTemplate.value;
    const current = target.value;
    if (!template || !current || !isChat.value || template.type !== channel.value || template.id !== form.templateId) return null;
    return UtilChatTemplate.card({
        tool: template.type,
        template,
        subject: form.subject,
        body: form.body,
        variables: variables.value,
        locale: form.locale,
        severity: current.severity,
        siteName: template.siteName,
        sentAt: time.value.full(new Date().toISOString()),
    });
});
/** 카카오톡 말풍선 본문 — 승인 본문을 이 대상의 견본 값으로 */
const kakaoBody = computed(() => (kakaoTemplate.value ? UtilKakaoTemplate.fill(kakaoTemplate.value.approvedBody, kakaoTemplate.value.mappings, variables.value) : ''));
/** 앱 알림 — 화면이 견본 값으로 그린다 */
const inAppPreview = computed(() => UtilNotificationMessage.renderInApp(form.subject, form.body, variables.value));

/* ── 저장 · 되돌리기 · 기본으로 ── */

async function resetToDefault(): Promise<void> {
    const seed = defaultFor(form.locale);
    if (!seed) return;
    const ok = await confirm({ title: t('notificationMessage.resetDefaultTitle'), message: t('notificationMessage.resetDefaultMessage'), confirmLabel: t('notificationMessage.resetDefaultConfirm'), tone: 'danger' });
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
    if (Object.keys(problems.value).length > 0 || (templateKind.value && !form.templateId)) return;
    saving.value = true;
    formError.value = '';
    serverErrors.value = {};
    const body = {
        targetType: current.type,
        targetCode: current.code,
        channel: channel.value,
        name: form.name.trim(),
        locale: form.locale,
        templateId: templateKind.value ? form.templateId : null,
        subject: channel.value === 'KAKAO' ? '' : form.subject,
        body: composed.value,
    };
    try {
        if (isNew.value || !saved.value) {
            const created = await api.create(body);
            toast.success(t('notificationMessage.created'));
            leaving = true;
            await router.replace(UtilNotificationTemplateMessages.messagePath(created.id));
            return;
        }
        saved.value = await api.update(saved.value.id, body);
        toast.success(t('notificationMessage.saved'));
        fill();
        if (detail.value) detail.value = { ...detail.value, channels: detail.value.channels.map((ch) => (ch.channel === channel.value ? { ...ch, messages: ch.messages.map((item) => (item.id === saved.value?.id ? (saved.value as MessageView) : item)) } : ch)) };
        if (historyLoaded) void history.reloadFirst();
    } catch (e) {
        const error = toApiError(e);
        const found: Record<string, string> = {};
        for (const field of ['name', 'channel', 'locale', 'templateId', 'subject', 'body']) {
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
    void router.push({ path: UtilNotificationTemplateMessages.messagePath('new'), query: { from: saved.value.id } });
}

const deleting = ref(false);

async function confirmDelete(): Promise<void> {
    const message = saved.value;
    if (!message || deleting.value) return;
    const ok = await confirm({ title: t('notificationMessage.deleteTitle', { name: message.name }), message: t('notificationMessage.deleteMessage'), confirmLabel: t('common.delete'), tone: 'danger' });
    if (!ok) return;
    deleting.value = true;
    try {
        await api.remove(message.id);
        toast.success(t('notificationMessage.deleted'));
        leaving = true;
        await router.push(listPath.value);
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
        const result = await api.test({ target: { type: current.type, code: current.code }, locale: form.locale, templateId: form.templateId || undefined, subject: form.subject, bodyHtml: composed.value, to });
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

/* ── Slack · Mattermost — 알림 도구로 시험 보내기 ── */

const chatTestOpen = ref(false);
const chatTestTool = ref<string | null>(null);
const chatTesting = ref(false);
const chatTestToolError = ref('');
const chatTestError = ref('');
const chatTestResult = ref<ChatMessageTestResultView | null>(null);

/** 시험할 수 있는 도구 — 실제 알림 도구 목록에서 이 채널과 같은 종류(꺼진 도구는 흐리게) */
const chatTools = computed(() => (tools.value ?? []).filter((tool) => tool.type === channel.value));
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
    const now = channel.value;
    if (chatTesting.value || !current || !chatTestTool.value || (now !== 'SLACK' && now !== 'MATTERMOST')) return;
    chatTesting.value = true;
    chatTestError.value = '';
    chatTestResult.value = null;
    try {
        chatTestResult.value = await api.chatTest({ target: { type: current.type, code: current.code }, channel: now, locale: form.locale, templateId: form.templateId, subject: form.subject, body: form.body, channelId: chatTestTool.value });
    } catch (e) {
        const error = toApiError(e);
        const field = error.field('channelId');
        if (field) chatTestToolError.value = field;
        else if (error.code === 'MAIL_TEST_RATE_LIMITED') chatTestError.value = t('mail.testRateLimited', { seconds: error.retryAfterSeconds ?? 60 });
        else chatTestError.value = error.fields?.subject || error.fields?.body || error.fields?.templateId || error.message;
    } finally {
        chatTesting.value = false;
    }
}

/* ── 떠나기 · 주소 바뀜 ── */

onBeforeRouteLeave(async () => {
    if (leaving || !dirty.value) return true;
    return confirm({ title: t('notificationTemplate.leaveTitle'), message: t('notificationTemplate.leaveMessage'), confirmLabel: t('notificationTemplate.leaveConfirm'), tone: 'danger' });
});

// 같은 화면에서 다른 문구로(복사해서 만들기 · 만든 뒤 주소 바꾸기) — 고친 것이 있으면 묻고 다시 읽는다. 새로 만들기 안의 대상·채널 바꾸기(쿼리만)는 다시 읽지 않는다
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
.message-tag {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    line-height: 1;
}
.message-tag :deep(.p-tag-label) {
    position: relative;
    top: -0.09em;
    white-space: nowrap;
}
</style>
