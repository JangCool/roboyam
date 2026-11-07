<template>
    <!--
        보상 이벤트 만들기·고치기 칸 — `/reward-events/new` 와 상세의 [고치기](`/reward-events/{id}?edit=1`)가 같이 쓴다.
        위에서 아래로: ① 기본(이름 — 회원 알림·포인트 내역에 보임 · 운영 메모 · 기간 · 안내 글 주소) ② 방식(선착순 자동 / 당첨자 선정 / 일괄 지급 + 방식별 설정)
        ③ 제외(운영자 회원 계정 — 정지·탈퇴·중복은 늘) ④ 등수·보상(당첨자 선정은 등수 여럿, 나머지는 하나) ⑤ 합계 미리 보기.
        값은 부모가 들고 있다(`v-model` — `RewardEventFormModel`), 저장 줄·서버 호출은 부모 몫이다(`components.md`). 미리 보기(댓글 후보 수·일괄 대상 수)는 `preview-*` 로 올리고 결과를 props 로 받는다 —
        결과는 그때 보낸 조건(`key`)과 함께 와서, 조건이 바뀌면 「조건이 바뀌었어요」로 다시 묻게 한다.
        고치기 범위(`scope`)는 서버가 상태로 정한다(`UtilRewardEvent.editable`): 준비 = 모두, 진행 중 = 이름·메모·안내 글·끝 시각·인원, 마감 = 메모·안내 글·등수 인원, 지급 끝·취소 = 메모만.
        칸 문제: 화면 검사(`UtilRewardEvent.validate`)는 제출을 한 번 누른 뒤(`showErrors`)부터 보이고, 서버 문구(`serverErrors`)는 늘 그 칸 아래에 그대로 보인다
        (고치기 범위 밖 칸 `409 REWARD_EVENT_LOCKED_FIELD` 의 `fields` 도 — `mode`·`firstCome.trigger`·`tiers` 등 묶음 칸은 그 묶음 아래).
        고액 안내: 1인 포인트가 문턱을 넘거나, 일괄 지급에 포인트 줄이 있으면(금액과 상관없이 — 서버 `RewardGrantService`) 지급 실행 때 `point.adjust.large` 가 필요하다.
    -->
    <div class="flex flex-col gap-4">
        <Message v-if="scope !== 'FULL'" severity="info" :closable="false" size="small">{{ t(`rewardEvent.form.scopeHelp.${scope}`) }}</Message>

        <!-- ① 기본 -->
        <AppGadget :title="t('rewardEvent.form.secBasic')" anchor="re-basic">
            <div class="flex flex-col gap-4">
                <LabeledField :label="t('rewardEvent.form.name')" :hint="t('rewardEvent.form.nameHint', { max: UtilRewardEvent.NAME_MAX })" :help="t('rewardEvent.form.nameHelp')" :error="fe('name')">
                    <template #default="{ id, describedBy, invalid }">
                        <InputText :id="id" v-model="model.name" :maxlength="UtilRewardEvent.NAME_MAX" :disabled="!can('name')" :invalid="invalid" :aria-describedby="describedBy" fluid />
                    </template>
                </LabeledField>

                <LabeledField :label="t('rewardEvent.form.memo')" :hint="t('rewardEvent.form.optional')" :help="t('rewardEvent.form.memoHelp')" :error="fe('memo')">
                    <template #default="{ id, describedBy, invalid }">
                        <Textarea :id="id" v-model="model.memo" rows="2" auto-resize :maxlength="UtilRewardEvent.MEMO_MAX" :disabled="!can('memo')" :invalid="invalid" :aria-describedby="describedBy" fluid />
                    </template>
                </LabeledField>

                <div class="grid gap-4 sm:grid-cols-2">
                    <LabeledField :label="t('rewardEvent.form.startAt')" :error="fe('startAt')">
                        <template #default="{ id, invalid }">
                            <AppDateTimePicker v-model="model.startAt" :input-id="id" :minute-step="10" :disabled="!can('startAt')" :invalid="invalid" />
                        </template>
                    </LabeledField>
                    <LabeledField :label="t('rewardEvent.form.endAt')" :error="fe('endAt')">
                        <template #default="{ id, invalid }">
                            <AppDateTimePicker v-model="model.endAt" :input-id="id" :minute-step="10" :min="model.startAt || undefined" :disabled="!can('endAt')" :invalid="invalid" />
                        </template>
                    </LabeledField>
                </div>

                <LabeledField :label="t('rewardEvent.form.noticeUrl')" :hint="t('rewardEvent.form.optional')" :help="t('rewardEvent.form.noticeUrlHelp')" :error="fe('noticeUrl')">
                    <template #default="{ id, describedBy, invalid }">
                        <InputText :id="id" v-model="model.noticeUrl" type="url" maxlength="500" placeholder="/boards/notice/1234" :disabled="!can('noticeUrl')" :invalid="invalid" :aria-describedby="describedBy" fluid />
                    </template>
                </LabeledField>
            </div>
        </AppGadget>

        <!-- ② 방식 -->
        <AppGadget :title="t('rewardEvent.form.secMode')" anchor="re-mode">
            <div class="flex flex-col gap-4">
                <fieldset class="m-0 min-w-0 border-0 p-0">
                    <legend class="sr-only">{{ t('rewardEvent.form.secMode') }}</legend>
                    <div class="grid gap-2 md:grid-cols-3">
                        <label
                            v-for="mode in REWARD_EVENT_MODES"
                            :key="mode"
                            class="flex cursor-pointer items-start gap-2.5 rounded-lg border p-3"
                            :class="[model.mode === mode ? 'border-(--p-primary-color) bg-(--p-highlight-background)' : 'border-(--line)', !can('mode') ? 'cursor-not-allowed opacity-70' : '']"
                        >
                            <RadioButton v-model="model.mode" :value="mode" :name="`${uid}-mode`" :disabled="!can('mode')" class="mt-0.5 shrink-0" />
                            <span class="flex min-w-0 flex-col gap-0.5">
                                <span class="text-sm font-medium">{{ t(`rewardEvent.mode.${mode}`) }}</span>
                                <span class="text-xs leading-relaxed text-muted-color">{{ t(`rewardEvent.form.modeHelp.${mode}`) }}</span>
                            </span>
                        </label>
                    </div>
                    <p v-if="fe('mode')" role="alert" class="mt-1 text-xs text-red-600 dark:text-red-400">{{ fe('mode') }}</p>
                </fieldset>

                <!-- a. 선착순 자동 -->
                <div v-if="model.mode === 'FIRST_COME'" class="flex flex-col gap-4 border-t border-(--line) pt-4">
                    <fieldset class="m-0 min-w-0 border-0 p-0">
                        <legend class="mb-1.5 p-0 text-sm font-medium">{{ t('rewardEvent.form.trigger') }}</legend>
                        <div class="flex flex-col gap-2">
                            <label v-for="tr in REWARD_EVENT_TRIGGERS" :key="tr" class="flex cursor-pointer items-start gap-2.5">
                                <RadioButton v-model="model.firstCome.trigger" :value="tr" :name="`${uid}-trigger`" :disabled="!can('config')" class="mt-0.5 shrink-0" />
                                <span class="flex min-w-0 flex-col gap-0.5">
                                    <span class="inline-flex items-center gap-1.5 text-sm">
                                        {{ t(`rewardEvent.trigger.${tr}`) }}
                                        <Tag v-if="tr === 'IDENTITY_VERIFIED'" :value="t('rewardEvent.form.recommended')" severity="success" />
                                    </span>
                                    <span class="text-xs leading-relaxed text-muted-color">{{ t(`rewardEvent.form.triggerHelp.${tr}`) }}</span>
                                </span>
                            </label>
                        </div>
                        <p v-if="fe('firstCome.trigger')" role="alert" class="mt-1 text-xs text-red-600 dark:text-red-400">{{ fe('firstCome.trigger') }}</p>
                    </fieldset>
                    <Message v-if="model.firstCome.trigger === 'EMAIL_VERIFIED'" severity="warn" :closable="false" size="small">{{ t('rewardEvent.form.emailTriggerWarn') }}</Message>
                    <LabeledField :label="t('rewardEvent.form.limit')" :hint="t('rewardEvent.form.limitHint')" :help="granted ? t('rewardEvent.form.grantedFloor', { n: granted.toLocaleString(locale) }) : ''" :error="fe('firstCome.limit')">
                        <template #default="{ id, describedBy, invalid }">
                            <InputNumber v-model="model.firstCome.limit" :input-id="id" :min="1" :max="UtilRewardEvent.LIMIT_MAX" :suffix="t('rewardEvent.peopleSuffix')" :disabled="!can('limit')" :invalid="invalid" :aria-describedby="describedBy" class="max-w-56" fluid />
                        </template>
                    </LabeledField>
                    <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('rewardEvent.form.firstComeHelp') }}</p>
                </div>

                <!-- b. 당첨자 선정 -->
                <div v-else-if="model.mode === 'SELECTION'" class="flex flex-col gap-4 border-t border-(--line) pt-4">
                    <fieldset class="m-0 min-w-0 border-0 p-0" :aria-describedby="fe('selection.sources') ? `${uid}-src-err` : undefined">
                        <legend class="mb-1.5 p-0 text-sm font-medium">{{ t('rewardEvent.form.sources') }}</legend>
                        <div class="flex flex-col gap-2">
                            <label v-for="src in REWARD_EVENT_CANDIDATE_SOURCES" :key="src" class="flex cursor-pointer items-start gap-2.5">
                                <Checkbox v-model="model.selectionSources" :value="src" :input-id="`${uid}-src-${src}`" :disabled="!can('config')" class="mt-0.5 shrink-0" />
                                <span class="flex min-w-0 flex-col gap-0.5">
                                    <span class="text-sm">{{ t(`rewardEvent.source.${src}`) }}</span>
                                    <span class="text-xs leading-relaxed text-muted-color">{{ t(`rewardEvent.form.sourceHelp.${src}`) }}</span>
                                </span>
                            </label>
                        </div>
                        <p v-if="fe('selection.sources')" :id="`${uid}-src-err`" role="alert" class="mt-1 text-xs text-red-600 dark:text-red-400">{{ fe('selection.sources') }}</p>
                    </fieldset>

                    <div v-if="model.selectionSources.includes('COMMENT_DRAW')" class="flex flex-col gap-3 rounded-lg border border-(--line) p-3">
                        <h3 class="m-0 text-sm font-medium">{{ t('rewardEvent.source.COMMENT_DRAW') }}</h3>
                        <LabeledField :label="t('rewardEvent.form.postRef')" :help="t('rewardEvent.form.postRefHelp')" :error="fe('selection.commentDraw.postRef') || commentPreviewError">
                            <template #default="{ id, describedBy, invalid }">
                                <InputText :id="id" v-model="model.commentDraw.postRef" maxlength="300" :placeholder="t('rewardEvent.form.postRefPlaceholder')" :disabled="!can('config')" :invalid="invalid" :aria-describedby="describedBy" fluid />
                            </template>
                        </LabeledField>
                        <label class="flex cursor-pointer items-center gap-2 text-sm">
                            <Checkbox v-model="model.commentSameAsPeriod" binary :input-id="`${uid}-same`" :disabled="!can('config')" />
                            {{ t('rewardEvent.form.commentSamePeriod') }}
                        </label>
                        <div v-if="!model.commentSameAsPeriod" class="grid gap-3 sm:grid-cols-2">
                            <LabeledField :label="t('rewardEvent.form.commentFrom')">
                                <template #default="{ id }"><AppDateTimePicker v-model="model.commentDraw.from" :input-id="id" :minute-step="10" :show-zone="false" :disabled="!can('config')" /></template>
                            </LabeledField>
                            <LabeledField :label="t('rewardEvent.form.commentTo')" :error="fe('selection.commentDraw.to')">
                                <template #default="{ id, invalid }"><AppDateTimePicker v-model="model.commentDraw.to" :input-id="id" :minute-step="10" :show-zone="false" :disabled="!can('config')" :invalid="invalid" /></template>
                            </LabeledField>
                        </div>
                        <label class="flex cursor-pointer items-center gap-2 text-sm">
                            <Checkbox v-model="model.commentDraw.excludeAuthor" binary :input-id="`${uid}-author`" :disabled="!can('config')" />
                            {{ t('rewardEvent.form.excludeAuthor') }}
                        </label>
                        <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('rewardEvent.form.commentRules') }}</p>
                        <div class="flex flex-wrap items-center gap-2">
                            <Button type="button" :label="t('rewardEvent.form.previewCandidates')" icon="pi pi-users" size="small" severity="secondary" variant="outlined" :loading="previewBusy === 'comments'" :disabled="!model.commentDraw.postRef.trim()" @click="previewComments" />
                            <template v-if="commentPreview">
                                <span v-if="commentPreview.key !== commentKey" class="text-xs text-amber-700 dark:text-amber-400">{{ t('rewardEvent.form.previewStale') }}</span>
                                <span v-else class="text-xs">
                                    <b class="font-medium">{{ commentPreview.result.postTitle }}</b>
                                    · {{ t('rewardEvent.form.candidateLine', { comments: commentPreview.result.commentCount.toLocaleString(locale), n: commentPreview.result.candidateCount.toLocaleString(locale) }) }}
                                    <span class="text-muted-color">· {{ t('rewardEvent.form.excludedLine', { author: commentPreview.result.excluded.author, operators: commentPreview.result.excluded.operators, suspended: commentPreview.result.excluded.suspended, withdrawn: commentPreview.result.excluded.withdrawn }) }}</span>
                                </span>
                            </template>
                        </div>
                        <Message severity="secondary" :closable="false" size="small">{{ t('rewardEvent.form.drawServerHelp') }}</Message>
                    </div>
                    <p v-if="model.selectionSources.includes('MANUAL')" class="m-0 text-xs leading-relaxed text-muted-color">{{ isNew ? t('rewardEvent.form.manualAfterSave') : t('rewardEvent.form.manualInTab') }}</p>
                </div>

                <!-- c. 일괄 지급 -->
                <div v-else class="flex flex-col gap-4 border-t border-(--line) pt-4">
                    <fieldset class="m-0 min-w-0 border-0 p-0">
                        <legend class="mb-1.5 p-0 text-sm font-medium">{{ t('rewardEvent.form.bulkScope') }}</legend>
                        <div class="flex flex-wrap gap-4">
                            <label v-for="sc in BULK_SCOPES" :key="sc" class="flex cursor-pointer items-center gap-2 text-sm">
                                <RadioButton v-model="model.bulk.scope" :value="sc" :name="`${uid}-scope`" :disabled="!can('config')" />
                                {{ t(`rewardEvent.bulkScope.${sc}`) }}
                            </label>
                        </div>
                    </fieldset>
                    <div v-if="model.bulk.scope === 'CONDITION'" class="grid gap-4 rounded-lg border border-(--line) p-3 md:grid-cols-2">
                        <LabeledField :label="t('rewardEvent.form.minGrade')">
                            <template #default="{ id }">
                                <Select v-model="model.bulk.minGradeLevel" :input-id="id" :options="gradeOptions" option-label="label" option-value="value" :disabled="!can('config')" fluid />
                            </template>
                        </LabeledField>
                        <LabeledField :label="t('rewardEvent.form.activeWithin')" :hint="t('rewardEvent.form.activeWithinHint')" :error="fe('bulk.activeWithinDays')">
                            <template #default="{ id, invalid }">
                                <InputNumber v-model="model.bulk.activeWithinDays" :input-id="id" :min="1" :max="UtilRewardEvent.ACTIVE_DAYS_MAX" :suffix="t('rewardEvent.daysSuffix')" :placeholder="t('rewardEvent.form.noCondition')" :disabled="!can('config')" :invalid="invalid" fluid />
                            </template>
                        </LabeledField>
                        <div class="flex flex-col gap-1.5 md:col-span-2">
                            <span class="text-sm font-medium">{{ t('rewardEvent.form.joinedRange') }}</span>
                            <DateRangeField v-model:from="joinedFrom" v-model:to="joinedTo" :from-label="t('rewardEvent.form.joinedFrom')" :to-label="t('rewardEvent.form.joinedTo')" :disabled="!can('config')" />
                            <p v-if="fe('bulk.joinedTo')" role="alert" class="text-xs text-red-600 dark:text-red-400">{{ fe('bulk.joinedTo') }}</p>
                        </div>
                        <label class="flex cursor-pointer items-center gap-2 text-sm md:col-span-2">
                            <Checkbox v-model="model.bulk.identityVerifiedOnly" binary :input-id="`${uid}-idv`" :disabled="!can('config')" />
                            {{ t('rewardEvent.form.identityOnly') }}
                        </label>
                    </div>
                    <div class="flex flex-wrap items-center gap-2">
                        <Button type="button" :label="t('rewardEvent.form.previewTargets')" icon="pi pi-users" size="small" severity="secondary" variant="outlined" :loading="previewBusy === 'targets'" @click="previewTargets" />
                        <template v-if="targetPreview">
                            <span v-if="targetPreview.key !== targetKey" class="text-xs text-amber-700 dark:text-amber-400">{{ t('rewardEvent.form.previewStale') }}</span>
                            <span v-else class="text-xs">
                                <b class="font-medium tabular-nums">{{ t('rewardEvent.peopleValue', { n: targetPreview.result.count.toLocaleString(locale) }) }}</b>
                                <span class="text-muted-color"> · {{ t('rewardEvent.form.targetExcluded', { suspended: targetPreview.result.excludedSuspended.toLocaleString(locale), operators: targetPreview.result.excludedOperators.toLocaleString(locale) }) }}</span>
                            </span>
                        </template>
                    </div>
                    <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('rewardEvent.form.bulkHelp') }}</p>
                </div>
            </div>
        </AppGadget>

        <!-- ③ 제외 -->
        <AppGadget :title="t('rewardEvent.form.secExclude')" anchor="re-exclude">
            <div class="flex flex-col gap-2">
                <label class="flex cursor-pointer items-center gap-2 text-sm">
                    <Checkbox v-model="model.excludeOperators" binary :input-id="`${uid}-ops`" :disabled="!can('config')" />
                    {{ t('rewardEvent.form.excludeOperators') }}
                </label>
                <ul class="m-0 flex list-disc flex-col gap-0.5 pl-5 text-xs leading-relaxed text-muted-color">
                    <li>{{ t('rewardEvent.form.alwaysExcluded') }}</li>
                    <li>{{ t('rewardEvent.form.oncePerEvent') }}</li>
                </ul>
            </div>
        </AppGadget>

        <!-- ④ 등수·보상 -->
        <AppGadget :title="t('rewardEvent.form.secTiers')" anchor="re-tiers" :invalid="tierProblemCount">
            <div class="flex flex-col gap-4">
                <p class="m-0 text-xs leading-relaxed text-muted-color">{{ model.mode === 'SELECTION' ? t('rewardEvent.form.tiersHelpMulti') : t('rewardEvent.form.tiersHelpSingle') }}</p>
                <Message v-if="model.mode !== 'SELECTION' && model.tiers.length > 1" severity="warn" :closable="false" size="small">{{ t('rewardEvent.form.extraTiersIgnored', { n: model.tiers.length - 1 }) }}</Message>
                <p v-if="fe('tiers')" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ fe('tiers') }}</p>

                <section v-for="(tier, ti) in shownTiers" :key="tier.id" class="flex flex-col gap-3 rounded-lg border border-(--line) p-3" :aria-label="tier.name || t('rewardEvent.form.tierN', { n: ti + 1 })">
                    <div class="flex flex-wrap items-start gap-3">
                        <LabeledField class="min-w-40 flex-1" :label="t('rewardEvent.form.tierName')" :error="fe(`tiers[${ti}].name`)">
                            <template #default="{ id, invalid }">
                                <InputText :id="id" v-model="tier.name" :maxlength="UtilRewardEvent.TIER_NAME_MAX" :disabled="!can('tiers')" :invalid="invalid" fluid />
                            </template>
                        </LabeledField>
                        <LabeledField v-if="model.mode === 'SELECTION'" class="w-40" :label="t('rewardEvent.form.tierQuota')" :error="fe(`tiers[${ti}].quota`)">
                            <template #default="{ id, invalid }">
                                <InputNumber v-model="tier.quota" :input-id="id" :min="1" :max="UtilRewardEvent.LIMIT_MAX" :suffix="t('rewardEvent.peopleSuffix')" :disabled="!can('quota')" :invalid="invalid" fluid />
                            </template>
                        </LabeledField>
                        <div v-else class="flex w-40 flex-col gap-1.5">
                            <span class="text-sm font-medium">{{ t('rewardEvent.form.tierQuota') }}</span>
                            <span class="py-2 text-sm text-muted-color">{{ model.mode === 'FIRST_COME' ? t('rewardEvent.peopleValue', { n: (model.firstCome.limit ?? 0).toLocaleString(locale) }) : t('rewardEvent.form.quotaByTargets') }}</span>
                        </div>
                        <Button v-if="model.mode === 'SELECTION' && model.tiers.length > 1 && can('tiers')" type="button" icon="pi pi-trash" size="small" severity="secondary" variant="text" class="mt-6" :aria-label="t('rewardEvent.form.removeTier', { name: tier.name })" @click="removeTier(ti)" />
                    </div>

                    <!-- 보상 줄 -->
                    <div class="flex flex-col gap-2">
                        <div v-for="(line, li) in tier.rewards" :key="line.id" class="flex flex-col gap-2 rounded-md bg-(--p-content-hover-background) p-3">
                            <div class="flex flex-wrap items-center gap-2">
                                <i :class="UtilRewardEvent.kindIcon(line.kind)" class="text-sm text-muted-color" aria-hidden="true" />
                                <span class="text-sm font-medium">{{ t(`rewardEvent.kind.${line.kind}`) }}</span>
                                <span class="flex-1" />
                                <Button v-if="can('rewards')" type="button" icon="pi pi-times" size="small" severity="secondary" variant="text" :aria-label="t('rewardEvent.form.removeReward')" @click="tier.rewards.splice(li, 1)" />
                            </div>

                            <!-- 무상 포인트 -->
                            <template v-if="line.kind === 'POINT'">
                                <LabeledField :label="t('rewardEvent.form.points')" :error="fe(`tiers[${ti}].rewards[${li}].points`)">
                                    <template #default="{ id, invalid }">
                                        <InputNumber v-model="line.points" :input-id="id" :min="1" :max="UtilRewardEvent.POINT_MAX" suffix=" P" :disabled="!can('rewards')" :invalid="invalid" class="max-w-56" fluid />
                                    </template>
                                </LabeledField>
                                <Message v-if="model.mode === 'BULK'" severity="warn" :closable="false" size="small">{{ t('rewardEvent.form.pointLargeBulk') }}</Message>
                                <Message v-else-if="(line.points ?? 0) > largeThreshold" severity="warn" :closable="false" size="small">{{ t('rewardEvent.form.pointLarge', { n: largeThreshold.toLocaleString(locale) }) }}</Message>
                                <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('rewardEvent.form.pointHelp', { n: largeThreshold.toLocaleString(locale) }) }}</p>
                            </template>

                            <!-- 포인트 상점 아이템 -->
                            <template v-else-if="line.kind === 'SHOP_ITEM'">
                                <div class="flex flex-wrap items-start gap-3">
                                    <LabeledField class="min-w-56 flex-1" :label="t('rewardEvent.form.item')" :error="fe(`tiers[${ti}].rewards[${li}].itemId`)">
                                        <template #default="{ id, invalid }">
                                            <Select :model-value="line.itemId" :input-id="id" :options="itemOptions" option-label="label" option-value="value" filter :placeholder="t('rewardEvent.form.itemPlaceholder')" :disabled="!can('rewards')" :invalid="invalid" fluid @update:model-value="(v: string) => pickItem(line, v)" />
                                        </template>
                                    </LabeledField>
                                    <LabeledField v-if="line.itemUsage === 'CONSUMABLE'" class="w-36" :label="t('rewardEvent.form.quantity')" :error="fe(`tiers[${ti}].rewards[${li}].quantity`)">
                                        <template #default="{ id, invalid }">
                                            <InputNumber v-model="line.quantity" :input-id="id" :min="1" :max="UtilRewardEvent.QUANTITY_MAX" show-buttons :disabled="!can('rewards')" :invalid="invalid" fluid />
                                        </template>
                                    </LabeledField>
                                </div>
                                <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('rewardEvent.form.itemHelp') }}</p>
                            </template>

                            <!-- 로보티콘 팩 -->
                            <template v-else-if="line.kind === 'EMOTICON_PACK'">
                                <LabeledField :label="t('rewardEvent.form.pack')" :help="t('rewardEvent.form.packPaidBlocked')" :error="fe(`tiers[${ti}].rewards[${li}].packId`)">
                                    <template #default="{ id, describedBy, invalid }">
                                        <Select :model-value="line.packId" :input-id="id" :options="packOptions" option-label="label" option-value="value" option-disabled="disabled" :placeholder="t('rewardEvent.form.packPlaceholder')" :disabled="!can('rewards')" :invalid="invalid" :aria-describedby="describedBy" fluid @update:model-value="(v: string) => pickPack(line, v)" />
                                    </template>
                                </LabeledField>
                                <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('rewardEvent.form.packHelp') }}</p>
                            </template>

                            <!-- 직접 전달 -->
                            <template v-else>
                                <div class="grid gap-3 md:grid-cols-2">
                                    <LabeledField :label="t('rewardEvent.form.manualName')" :error="fe(`tiers[${ti}].rewards[${li}].manualName`)">
                                        <template #default="{ id, invalid }">
                                            <InputText :id="id" v-model="line.manualName" :maxlength="UtilRewardEvent.MANUAL_NAME_MAX" :placeholder="t('rewardEvent.form.manualNamePlaceholder')" :disabled="!can('rewards')" :invalid="invalid" fluid />
                                        </template>
                                    </LabeledField>
                                    <LabeledField :label="t('rewardEvent.form.manualMethod')" :hint="t('rewardEvent.form.optional')" :error="fe(`tiers[${ti}].rewards[${li}].manualMethod`)">
                                        <template #default="{ id, invalid }">
                                            <InputText :id="id" v-model="line.manualMethod" :maxlength="UtilRewardEvent.MANUAL_METHOD_MAX" :placeholder="t('rewardEvent.form.manualMethodPlaceholder')" :disabled="!can('rewards')" :invalid="invalid" fluid />
                                        </template>
                                    </LabeledField>
                                </div>
                                <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('rewardEvent.form.manualHelp') }}</p>
                            </template>
                        </div>
                        <p v-if="fe(`tiers[${ti}].rewards`)" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ fe(`tiers[${ti}].rewards`) }}</p>

                        <div v-if="can('rewards') && tier.rewards.length < UtilRewardEvent.REWARD_MAX" class="flex flex-wrap items-center gap-1">
                            <span class="mr-1 text-xs text-muted-color">{{ t('rewardEvent.form.addReward') }}</span>
                            <Button v-for="kind in REWARD_KINDS" :key="kind" type="button" :label="t(`rewardEvent.kind.${kind}`)" :icon="UtilRewardEvent.kindIcon(kind)" size="small" severity="secondary" variant="outlined" @click="tier.rewards.push(UtilRewardEvent.emptyLine(kind))" />
                        </div>
                    </div>

                    <p class="m-0 text-xs text-muted-color">{{ t('rewardEvent.form.perPerson', { rewards: UtilRewardEventMessages.summary(t, tier.rewards, locale) }) }}</p>
                </section>

                <Button v-if="model.mode === 'SELECTION' && can('tiers') && model.tiers.length < UtilRewardEvent.TIER_MAX" type="button" :label="t('rewardEvent.form.addTier')" icon="pi pi-plus" size="small" severity="secondary" variant="outlined" class="self-start" @click="addTier" />
            </div>
        </AppGadget>

        <!-- ⑤ 합계 미리 보기 -->
        <AppGadget :title="t('rewardEvent.form.secTotals')" anchor="re-totals">
            <dl class="m-0 grid gap-3 sm:grid-cols-4">
                <div class="flex flex-col gap-0.5">
                    <dt class="text-xs text-muted-color">{{ t('rewardEvent.form.totalPeople') }}</dt>
                    <dd class="m-0 text-lg font-semibold tabular-nums">{{ totals.people === null ? '—' : t('rewardEvent.peopleValue', { n: totals.people.toLocaleString(locale) }) }}</dd>
                </div>
                <div class="flex flex-col gap-0.5">
                    <dt class="text-xs text-muted-color">{{ t('rewardEvent.form.totalPoints') }}</dt>
                    <dd class="m-0 text-lg font-semibold tabular-nums">{{ totals.points === null ? '—' : UtilRewardEventMessages.points(t, totals.points, locale) }}</dd>
                </div>
                <div class="flex flex-col gap-0.5">
                    <dt class="text-xs text-muted-color">{{ t('rewardEvent.form.totalItems') }}</dt>
                    <dd class="m-0 text-lg font-semibold tabular-nums">{{ totals.items === null ? '—' : totals.items.toLocaleString(locale) }}</dd>
                </div>
                <div class="flex flex-col gap-0.5">
                    <dt class="text-xs text-muted-color">{{ t('rewardEvent.form.totalManual') }}</dt>
                    <dd class="m-0 text-lg font-semibold tabular-nums">{{ totals.manual === null ? '—' : totals.manual.toLocaleString(locale) }}</dd>
                </div>
            </dl>
            <p class="mt-3 mb-0 text-xs text-muted-color">
                {{ model.mode === 'BULK' && totals.people === null ? t('rewardEvent.form.totalsNeedPreview') : model.mode === 'SELECTION' ? t('rewardEvent.form.totalsByQuota') : t('rewardEvent.form.totalsByLimit') }}
            </p>
            <Message v-if="model.mode === 'BULK' && totals.maxPerPerson > 0" severity="warn" :closable="false" size="small" class="mt-2">{{ t('rewardEvent.form.largeNoticeBulk') }}</Message>
            <Message v-else-if="totals.maxPerPerson > largeThreshold" severity="warn" :closable="false" size="small" class="mt-2">{{ t('rewardEvent.form.largeNotice', { n: largeThreshold.toLocaleString(locale) }) }}</Message>
        </AppGadget>
    </div>
</template>

<script setup lang="ts">
import type { RewardCommentPreview, RewardCommentPreviewRequest, RewardEventBulkScope, RewardEventCatalog, RewardEventEditScope, RewardLine, RewardTargetPreview, RewardTargetPreviewRequest } from '~/composables/rewardevent/api/useRewardEventAdminApi.types';
import { REWARD_EVENT_CANDIDATE_SOURCES, REWARD_EVENT_MODES, REWARD_EVENT_TRIGGERS, REWARD_KINDS } from '~/composables/rewardevent/api/useRewardEventAdminApi.types';
import UtilRewardEvent, { type RewardEventFormField, type RewardEventFormModel } from '~/utils/util-reward-event';
import UtilRewardEventMessages from '~/utils/util-reward-event-messages';

const model = defineModel<RewardEventFormModel>({ required: true });

interface Props {
    /** 고치기 범위 — 새로 만들 때는 FULL */
    scope: RewardEventEditScope;
    catalog: RewardEventCatalog | null;
    isNew?: boolean;
    /** 서버 칸 문구(`400 fields`) — 그대로 보인다 */
    serverErrors?: Record<string, string>;
    /** 제출을 한 번 눌렀다 — 화면 검사 문구를 보인다 */
    showErrors?: boolean;
    commentPreview?: { key: string; result: RewardCommentPreview } | null;
    commentPreviewError?: string;
    targetPreview?: { key: string; result: RewardTargetPreview } | null;
    previewBusy?: 'comments' | 'targets' | null;
    /** 이미 받은(선정된) 사람 수 — 인원 칸 아래 안내 */
    granted?: number;
}

const props = withDefaults(defineProps<Props>(), {
    isNew: false,
    serverErrors: () => ({}),
    showErrors: false,
    commentPreview: null,
    commentPreviewError: '',
    targetPreview: null,
    previewBusy: null,
    granted: 0,
});

const emit = defineEmits<{
    (e: 'preview-comments', body: RewardCommentPreviewRequest): void;
    (e: 'preview-targets', body: RewardTargetPreviewRequest): void;
}>();

const { t, locale } = useI18n();
const uid = useId();
const BULK_SCOPES: readonly RewardEventBulkScope[] = ['ALL', 'CONDITION'];

function can(field: RewardEventFormField): boolean {
    return UtilRewardEvent.editable(props.scope, field);
}

const largeThreshold = computed(() => props.catalog?.largeThreshold ?? 10_000);
const request = computed(() => UtilRewardEvent.toRequest(model.value));
const problems = computed(() => UtilRewardEvent.validate(request.value, props.catalog));

/** 칸 아래 문구 — 서버 문구가 먼저, 화면 검사는 제출을 누른 뒤부터 */
function fe(path: string): string {
    const server = props.serverErrors[path];
    if (server) return server;
    if (!props.showErrors) return '';
    const p = problems.value[path];
    return p ? UtilRewardEventMessages.problem(t, path, p, locale.value) : '';
}

const tierProblemCount = computed(() => {
    if (!props.showErrors) return Object.keys(props.serverErrors).filter((k) => k.startsWith('tiers')).length;
    return new Set([...Object.keys(problems.value), ...Object.keys(props.serverErrors)].filter((k) => k.startsWith('tiers'))).size;
});

/** 당첨자 선정이 아니면 첫 등수만 보인다(나머지는 보낼 때 빠진다) */
const shownTiers = computed(() => (model.value.mode === 'SELECTION' ? model.value.tiers : model.value.tiers.slice(0, 1)));

const gradeOptions = computed(() => [{ value: null as number | null, label: t('rewardEvent.form.noCondition') }, ...(props.catalog?.grades ?? []).map((g) => ({ value: g.level as number | null, label: t('rewardEvent.form.gradeOrAbove', { name: g.label }) }))]);

const itemOptions = computed(() =>
    (props.catalog?.items ?? []).map((i) => ({
        value: i.id,
        label: `${i.name} · ${t(`rewardEvent.usage.${i.usage}`, { days: i.durationDays ?? 0 })}${i.onSale ? '' : ` · ${t('rewardEvent.form.itemNotOnSale')}`}`,
    })),
);

const packOptions = computed(() =>
    (props.catalog?.packs ?? []).map((p) => ({
        value: p.id,
        disabled: !p.selectable,
        label: `${p.name} · ${p.source === 'OFFICIAL' ? t('rewardEvent.form.packOfficial') : p.price === 0 ? t('rewardEvent.form.packFree') : t('rewardEvent.form.packPaid')}`,
    })),
);

function pickItem(line: RewardLine, id: string): void {
    const item = props.catalog?.items.find((i) => i.id === id);
    if (!item) return;
    line.itemId = item.id;
    line.itemName = item.name;
    line.itemKind = item.kind;
    line.itemUsage = item.usage;
    line.itemDurationDays = item.durationDays;
    line.quantity = item.usage === 'CONSUMABLE' ? Math.max(1, line.quantity ?? 1) : 1;
}

function pickPack(line: RewardLine, id: string): void {
    const pack = props.catalog?.packs.find((p) => p.id === id);
    if (!pack || !pack.selectable) return;
    line.packId = pack.id;
    line.packName = pack.name;
}

function addTier(): void {
    const n = model.value.tiers.length + 1;
    model.value.tiers.push(UtilRewardEvent.emptyTier(t('rewardEvent.form.tierDefaultName', { n }), 1));
}

function removeTier(index: number): void {
    model.value.tiers.splice(index, 1);
}

// 가입일 범위 — 칸은 `YYYY-MM-DD` 글자('' = 없음), 모델은 null
const joinedFrom = computed({ get: () => model.value.bulk.joinedFrom ?? '', set: (v: string) => (model.value.bulk.joinedFrom = v || null) });
const joinedTo = computed({ get: () => model.value.bulk.joinedTo ?? '', set: (v: string) => (model.value.bulk.joinedTo = v || null) });

// ── 미리 보기 ─
function commentBody(): RewardCommentPreviewRequest {
    const c = request.value.selection?.commentDraw;
    return { postRef: model.value.commentDraw.postRef.trim(), from: c?.from ?? model.value.startAt, to: c?.to ?? model.value.endAt, excludeAuthor: model.value.commentDraw.excludeAuthor, excludeOperators: model.value.excludeOperators };
}
function targetBody(): RewardTargetPreviewRequest {
    return { bulk: { ...model.value.bulk }, excludeOperators: model.value.excludeOperators };
}
/** 결과가 지금 조건의 것인가를 가리는 열쇠 — 부모가 보낸 본문을 같은 방법으로 글자로 만든다 */
const commentKey = computed(() => JSON.stringify(commentBody()));
const targetKey = computed(() => JSON.stringify(targetBody()));

function previewComments(): void {
    emit('preview-comments', commentBody());
}
function previewTargets(): void {
    emit('preview-targets', targetBody());
}

const totals = computed(() => {
    const preview = props.targetPreview && props.targetPreview.key === targetKey.value ? props.targetPreview.result.count : null;
    return UtilRewardEvent.totals(request.value, preview);
});
</script>
