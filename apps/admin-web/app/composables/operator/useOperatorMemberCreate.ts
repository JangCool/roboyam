import type { OperatorView } from '~/composables/operator/api/useOperatorApi.types';
import type { OperatorMemberCreateResult } from '~/composables/operator/api/useOperatorMemberAccountApi.types';
import type { OperatorMemberCreateOperator, OperatorMemberCreateSubmit, OperatorMemberCreateView } from '~/composables/operator/useOperatorMemberCreate.types';

/** 칸에 붙는 서버 오류 — `400 VALIDATION` 의 `fields` 키 */
const FIELD_KEYS = ['email', 'nickname', 'passwordMode', 'password'];
/**
 * 칸에 붙는 409 — 코드 → 칸. 문구는 서버 그대로.
 * `REJOIN_BLOCKED`(탈퇴 뒤 재가입 제한)는 공개 가입 화면과 같이 서버 문구(다시 가입할 수 있는 날 포함)를 그대로 보인다 — `retryAfterSeconds`·`extras.rejoinAvailableAt` 은 따로 쓰지 않는다
 */
const FIELD_CODES: Record<string, string> = { EMAIL_TAKEN: 'email', NICKNAME_TAKEN: 'nickname', REJOIN_BLOCKED: 'email' };

/**
 * 운영자 회원 계정 「새 계정 만들기」 대화창(`OperatorMemberCreateDialog`)의 흐름 — **연결할 운영자 고르기 → 이메일·닉네임·비밀번호 → 만들기 → 결과**.
 * 회원 관리(`/members` 머리 단추 — 운영자를 고른다)와 운영자 서랍 「사이트 계정」 탭(운영자가 정해져 있다)이 부른다. 화면마다 새 상태다.
 *  - `start(operator?)`: 열면서 비운다. 운영자가 없으면 활성 운영자 목록을 받아 아직 사이트 계정이 없는 운영자만 고르게 한다(최고 관리자는 본인일 때만 — 계약 정책 5).
 *  - `submit({ operatorId, body })`: 성공하면 결과(`result`)를 채우고 대화창은 결과 화면이 된다(닫지 않는다). `400 fields.*`·`EMAIL_TAKEN`·`REJOIN_BLOCKED`(이메일 칸)·`NICKNAME_TAKEN` 은 칸 아래,
 *    그 밖은 대화창 안 한 줄(서버 문구 그대로). `OPERATOR_MEMBER_ALREADY_LINKED` 면 고르개 목록을 다시 받는다.
 * 비밀번호는 여기 남기지 않는다 — 대화창 메모리에만 있다(`forms.md` 2-1).
 * ⚠ 컴포넌트 setup 에서 부른다.
 */
export function useOperatorMemberCreate() {
    const api = useOperatorMemberAccountApi();
    const operatorApi = useOperatorApi();
    const errorOf = useApiErrorOf();
    const { user } = useAdminAuth();

    const open = ref(false);
    const fixed = ref(false);
    const operators = ref<OperatorMemberCreateOperator[] | null>(null);
    const operatorsLoading = ref(false);
    const operatorsError = ref('');
    const busy = ref(false);
    const error = ref('');
    const fieldErrors = ref<Record<string, string>>({});
    const errorCode = ref('');
    const result = ref<OperatorMemberCreateResult | null>(null);
    let ticket = 0;

    function toOption(operator: OperatorView): OperatorMemberCreateOperator {
        return { operatorId: operator.operatorId, operatorName: operator.operatorName, email: operator.email, isSuper: operator.isSuper, isSelf: operator.operatorId === user.value?.id };
    }

    const defaultOperatorId = computed<string | null>(() => {
        const list = operators.value ?? [];
        const self = list.find((o) => o.isSelf);
        if (self) return self.operatorId;
        return list.length === 1 ? list[0]!.operatorId : null;
    });

    function start(operator: OperatorView | null = null): void {
        ticket++;
        error.value = '';
        fieldErrors.value = {};
        errorCode.value = '';
        result.value = null;
        busy.value = false;
        operatorsError.value = '';
        operatorsLoading.value = false;
        fixed.value = operator !== null;
        operators.value = operator ? [toOption(operator)] : null;
        open.value = true;
        if (!operator) void loadOperators();
    }

    /** 아직 사이트 계정이 없는 활성 운영자 — 최고 관리자는 로그인한 본인일 때만 */
    async function loadOperators(): Promise<void> {
        if (fixed.value) return;
        const mine = ++ticket;
        operatorsLoading.value = true;
        operatorsError.value = '';
        try {
            const page = await operatorApi.list('ACTIVE', '');
            if (mine !== ticket) return;
            operators.value = page.items.filter((o) => o.memberAccount === null && (!o.isSuper || o.operatorId === user.value?.id)).map(toOption);
        } catch (e) {
            if (mine !== ticket) return;
            operators.value = null;
            operatorsError.value = errorOf(e).message;
        } finally {
            if (mine === ticket) operatorsLoading.value = false;
        }
    }

    async function submit(payload: OperatorMemberCreateSubmit): Promise<OperatorMemberCreateResult | null> {
        if (busy.value) return null;
        busy.value = true;
        error.value = '';
        fieldErrors.value = {};
        errorCode.value = '';
        try {
            // 반응형 값을 넘기지 않는다 — 평범한 사본(`_frontend-core.md` 2-3)
            const created = await api.create(payload.operatorId, { ...toRaw(payload.body) });
            result.value = created;
            return created;
        } catch (e) {
            const failure = errorOf(e);
            errorCode.value = failure.code;
            const field = FIELD_CODES[failure.code];
            const fields: Record<string, string> = {};
            for (const key of FIELD_KEYS) {
                const message = failure.field(key);
                if (message) fields[key] = message;
            }
            if (field) fields[field] = failure.message;
            if (Object.keys(fields).length > 0) fieldErrors.value = fields;
            else error.value = failure.message;
            if (failure.code === 'OPERATOR_MEMBER_ALREADY_LINKED') void loadOperators();
            return null;
        } finally {
            busy.value = false;
        }
    }

    const view = computed<OperatorMemberCreateView>(() => ({
        fixed: fixed.value,
        operators: operators.value,
        operatorsLoading: operatorsLoading.value,
        operatorsError: operatorsError.value,
        defaultOperatorId: defaultOperatorId.value,
        busy: busy.value,
        error: error.value,
        fieldErrors: fieldErrors.value,
        errorCode: errorCode.value,
        result: result.value,
    }));

    return { open, view, result, start, loadOperators, submit };
}
