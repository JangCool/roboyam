<template>
    <!--
        가젯 페이지 만들기·고치기 창 — 테마 화면 「페이지」의 [새 페이지] · [페이지 정보](`GadgetPagePicker`) (사용자 결정 2026-10-29).
        칸: 이름(1~40자) · 영문 이름(선택) · 영문 주소(`/p/{key}` — 소문자로 시작하는 소문자·숫자·- 2~30자, 겹치면 서버가 거절) · 설명(200자) ·
            기준 폴더(공개 메뉴 main 의 폴더 — 「이 페이지가 연결된 폴더의 게시판 전체」와 처음 배치가 쓴다, 없음 가능) · 머리의 글쓰기 단추.
        만들 때: 기준 폴더를 고르면 이름이 비어 있을 때 폴더 이름을, 주소가 비어 있을 때 폴더 영문 이름으로 만든 주소를 채운다.
          아래에 처음 배치(폴더의 게시판으로 채울 가젯 목록 — `UtilGadgetPage.initialLayout`)를 미리 보인다. 배치는 모든 테마 같은 것으로 시작하고, 테마마다 따로 고친다.
        고칠 때: 기준 폴더를 바꿔도 이미 만든 배치는 그대로다(「폴더 게시판 전체」 가젯만 새 폴더를 따른다). [페이지 지우기]는 이 페이지를 가리키던 메뉴 연결도 비운다(묻는다).
        저장 거절(400 `fields.*`)은 칸 아래에 보인다. 권한 `site.manage`(창을 여는 단추가 없다).
    -->
    <Dialog :visible="visible" modal :draggable="false" :header="page ? t('gadgetPage.editTitle') : t('gadgetPage.createTitle')" :style="{ width: 'min(34rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <div class="flex flex-col gap-1.5">
                <label :for="`${formId}-folder`" class="text-sm font-medium">{{ t('gadgetPage.folder') }}</label>
                <Select
                    v-model="form.folderId"
                    :input-id="`${formId}-folder`"
                    :options="folderOptions"
                    option-label="label"
                    option-value="value"
                    :loading="folderLoading"
                    :placeholder="t('gadgetPage.folderNone')"
                    show-clear
                    :aria-describedby="`${formId}-folder-hint`"
                    fluid
                    @update:model-value="onFolder"
                />
                <small :id="`${formId}-folder-hint`" class="text-xs leading-relaxed text-muted-color">{{ folderError || t('gadgetPage.folderHint') }}</small>
            </div>

            <LabeledField :label="t('gadgetPage.name')" :hint="t('gadgetPage.nameHint', { max: UtilGadgetPage.NAME_MAX })" :error="errorOf('name')">
                <template #default="{ id, describedBy, invalid }">
                    <InputText :id="id" v-model="form.name" :invalid="invalid" :aria-describedby="describedBy" :aria-required="true" :maxlength="UtilGadgetPage.NAME_MAX" autocomplete="off" fluid />
                </template>
            </LabeledField>

            <LabeledField :label="t('gadgetPage.nameEn')" :hint="t('gadgetPage.optional')" :error="errorOf('nameEn')">
                <template #default="{ id, describedBy, invalid }">
                    <InputText :id="id" v-model="form.nameEn" :invalid="invalid" :aria-describedby="describedBy" :maxlength="UtilGadgetPage.NAME_MAX" autocomplete="off" fluid />
                </template>
            </LabeledField>

            <LabeledField :label="t('gadgetPage.key')" :help="t('gadgetPage.keyHelp')" :error="errorOf('key')">
                <template #default="{ id, describedBy, invalid }">
                    <InputGroup>
                        <InputGroupAddon>/p/</InputGroupAddon>
                        <InputText
                            :id="id"
                            v-model="form.key"
                            :invalid="invalid"
                            :aria-describedby="describedBy"
                            :aria-required="true"
                            :maxlength="UtilGadgetPage.KEY_MAX"
                            placeholder="community"
                            autocomplete="off"
                            autocapitalize="off"
                            spellcheck="false"
                            @blur="form.key = form.key.trim().toLowerCase()"
                        />
                    </InputGroup>
                </template>
            </LabeledField>

            <LabeledField :label="t('gadgetPage.description')" :hint="t('gadgetPage.descriptionHint', { max: UtilGadgetPage.DESCRIPTION_MAX })" :error="errorOf('description')">
                <template #default="{ id, describedBy, invalid }">
                    <Textarea :id="id" v-model="form.description" :invalid="invalid" :aria-describedby="describedBy" :maxlength="UtilGadgetPage.DESCRIPTION_MAX" rows="2" auto-resize fluid />
                </template>
            </LabeledField>

            <div class="flex flex-col gap-1">
                <div class="flex items-center gap-2">
                    <ToggleSwitch v-model="form.writeButton" :input-id="`${formId}-write`" />
                    <label :for="`${formId}-write`" class="cursor-pointer text-sm">{{ t('gadgetPage.writeButton') }}</label>
                </div>
                <p class="m-0 text-xs text-muted-color">{{ t('gadgetPage.writeButtonHelp') }}</p>
            </div>

            <!-- 처음 배치 미리(만들 때만) -->
            <div v-if="!page" class="flex flex-col gap-1.5 rounded-lg border border-(--line) px-3 py-2.5" data-testid="gadget-page-initial">
                <span class="text-sm font-medium">{{ t('gadgetPage.initialTitle') }}</span>
                <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('gadgetPage.initialLead') }}</p>
                <ol class="m-0 flex list-decimal flex-col gap-0.5 pl-5 text-xs">
                    <li v-for="row in initialRows" :key="row.id">
                        {{ row.title }} <span class="text-muted-color">· {{ t(`homeLayout.kind.${row.type}`) }} · {{ t('gadgetPage.initialWidth', { w: row.w }) }}</span>
                    </li>
                </ol>
            </div>

            <p v-if="formError" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ formError }}</p>
        </form>

        <template #footer>
            <Button v-if="page" type="button" :label="t('gadgetPage.delete')" icon="pi pi-trash" severity="danger" variant="text" class="mr-auto" :disabled="saving" @click="emit('delete', page)" />
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="saving" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="page ? t('common.save') : t('gadgetPage.create')" :loading="saving" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { GadgetPage, GadgetPageCreateRequest } from '~/composables/page/api/useGadgetPageApi.types';
import UtilGadgetPage from '~/utils/util-gadget-page';

interface Props {
    visible: boolean;
    /** 고칠 페이지 — null 이면 새로 만들기 */
    page?: GadgetPage | null;
    /** 저장 중인가(부모가 API 를 부른다) */
    saving?: boolean;
    /** 저장 거절 — 칸 문구는 칸 아래, 그 밖은 아래 한 줄 */
    error?: ApiError | null;
}
const props = withDefaults(defineProps<Props>(), { page: null, saving: false, error: null });
const emit = defineEmits<{ (e: 'update:visible', value: boolean): void; (e: 'submit', body: GadgetPageCreateRequest): void; (e: 'delete', page: GadgetPage): void }>();

const { t } = useI18n();
const formId = useId();
const { folders, loading: folderLoading, error: folderError, load: loadFolders, folderOf } = useGadgetPageFolders();

const form = ref<GadgetPageCreateRequest>(blank());
const submitted = ref(false);

function blank(): GadgetPageCreateRequest {
    return { key: '', name: '', nameEn: '', description: '', folderId: null, writeButton: true };
}

watch(
    () => props.visible,
    (open) => {
        if (!open) return;
        const p = props.page;
        form.value = p ? { key: p.key, name: p.name, nameEn: p.nameEn ?? '', description: p.description, folderId: p.folderId, writeButton: p.writeButton } : blank();
        submitted.value = false;
        void loadFolders();
    },
    { immediate: true },
);

const folderOptions = computed(() =>
    folders.value.map((f) => {
        const count = f.boards.filter((b) => b.active).length;
        return { value: f.id, label: count ? `${f.label} (${t('gadgetPage.folderBoards', { count })})` : f.label };
    }),
);

/** 기준 폴더를 고르면(만들 때) 빈 이름·주소를 폴더로 채운다 */
function onFolder(id: string | null): void {
    if (props.page) return;
    const folder = folderOf(id);
    if (!folder) return;
    if (!form.value.name.trim()) form.value.name = folder.name;
    if (!form.value.key.trim()) {
        const guess = (folder.nameEn ?? '')
            .toLowerCase()
            .replace(/[^a-z0-9]+/g, '-')
            .replace(/^-+|-+$/g, '');
        if (UtilGadgetPage.validKey(guess)) form.value.key = guess;
    }
    if (!form.value.nameEn?.trim() && folder.nameEn) form.value.nameEn = folder.nameEn;
}

const initialRows = computed(() => {
    const folder = folderOf(form.value.folderId);
    const body = UtilGadgetPage.initialLayout(folder?.name ?? null, folder?.boards ?? [], {
        popular: (name) => t('gadgetPage.seedPopular', { folder: name }),
        latest: (name) => t('gadgetPage.seedLatest', { folder: name }),
        all: t('gadgetPage.seedAll'),
    });
    return body.gadgets.map((g) => ({ id: g.id, type: g.type, title: g.title, w: body.layout.items.find((it) => it.gadgetId === g.id)?.w ?? 0 }));
});

/** 화면 검사 문구 — 보낸 뒤에만. 서버 문구가 있으면 그것을 먼저 */
function errorOf(field: 'name' | 'nameEn' | 'key' | 'description'): string {
    const server = props.error?.field(field);
    if (server) return server;
    if (!submitted.value) return '';
    if (field === 'name' && !form.value.name.trim()) return t('gadgetPage.nameRequired');
    if (field === 'key' && !UtilGadgetPage.validKey(form.value.key.trim())) return t('gadgetPage.keyInvalid');
    return '';
}

const formError = computed(() => (props.error && !props.error.fields ? props.error.message : ''));

function submit(): void {
    submitted.value = true;
    form.value.key = form.value.key.trim().toLowerCase();
    if (errorOf('name') || errorOf('key')) return;
    emit('submit', {
        key: form.value.key,
        name: form.value.name.trim(),
        nameEn: form.value.nameEn?.trim() || null,
        description: form.value.description.trim(),
        folderId: form.value.folderId,
        writeButton: form.value.writeButton,
    });
}
</script>
