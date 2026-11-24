import type { OperatorMemberCreateRequest, OperatorMemberCreateResult } from '~/composables/operator/api/useOperatorMemberAccountApi.types';

/** 「연결할 운영자」 고르개의 한 줄 — 아직 사이트 계정이 이어지지 않은 운영자(최고 관리자는 로그인한 본인일 때만) */
export interface OperatorMemberCreateOperator {
    operatorId: string;
    operatorName: string;
    email: string;
    isSuper: boolean;
    /** 로그인한 운영자 본인 */
    isSelf: boolean;
}

/** 새 계정 만들기 대화창(`OperatorMemberCreateDialog`)이 그리는 값 — 흐름은 `useOperatorMemberCreate` */
export interface OperatorMemberCreateView {
    /** 운영자가 정해져 열렸다(운영자 서랍에서) — 고르개 대신 이름만 보인다 */
    fixed: boolean;
    /** 고를 수 있는 운영자. null = 받는 중이거나 못 받았다 */
    operators: OperatorMemberCreateOperator[] | null;
    operatorsLoading: boolean;
    /** 운영자 목록을 못 받았을 때 서버 문구 */
    operatorsError: string;
    /** 처음 골라 둘 운영자 — 로그인한 본인(연결 안 됐을 때), 없으면 하나뿐일 때 그 운영자 */
    defaultOperatorId: string | null;
    busy: boolean;
    /** 칸에 붙지 않는 오류(서버 문구 그대로) */
    error: string;
    /** 칸에 붙는 서버 오류 — 키 `email`·`nickname`·`passwordMode`·`password` */
    fieldErrors: Record<string, string>;
    /** 마지막 실패의 서버 코드 — `EMAIL_TAKEN` 이면 이메일 칸 아래에 「그 회원을 연결하세요」 안내를 붙인다 */
    errorCode: string;
    /** 만들었다 — 값이 있으면 대화창이 결과 화면으로 바뀐다 */
    result: OperatorMemberCreateResult | null;
}

/** 대화창이 올리는 값 */
export interface OperatorMemberCreateSubmit {
    operatorId: string;
    body: OperatorMemberCreateRequest;
}
