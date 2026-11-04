import type { ApiError } from '~/composables/common/useApiClient';
import type { SanctionKind, SanctionPreview, SanctionTarget } from '~/composables/sanction/api/useSanctionApi.types';

/** 제재 대상 회원 — 이미 정해졌으면(회원 상세·신고 서랍) 찾기를 건너뛴다 */
export interface SanctionIssueMember {
    id: string;
    nickname: string;
}

/** [제재하기] 대화창이 그리는 값 — `useSanctionIssue().view` */
export interface SanctionIssueView {
    /** 회원이 처음부터 정해져 있었다(찾기를 보이지 않는다) */
    fixed: boolean;
    member: SanctionIssueMember | null;
    /** 찾은 회원 — 아직 찾지 않았으면 null */
    targets: SanctionTarget[] | null;
    targetsLoading: boolean;
    /** 찾기 실패(서버 문구 그대로) */
    targetsError: string;
    preview: SanctionPreview | null;
    previewLoading: boolean;
    previewError: ApiError | null;
    busy: boolean;
    /** 칸에 붙지 않는 오류(서버 문구 그대로) */
    error: string;
    /** 서버 칸 오류(`kind`·`reason`·`skipStep`·`confirmNickname`) */
    fieldErrors: Record<string, string>;
}

/** 단계 고르개(`SanctionStepPicker`)가 고른 값 — 대화창·신고 처리 대화창이 같이 쓴다 */
export interface SanctionStepValue {
    kind: SanctionKind | null;
    /** 건너뛰기 근거를 확인했다(건너뛰기일 때만 뜻이 있다) */
    skipConfirmed: boolean;
    /** 영구 정지 확인 — 회원 닉네임을 그대로 */
    confirmNickname: string;
}
