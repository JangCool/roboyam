import type { DocIntakeBundle, DocIntakeCreateRequest, DocIntakeDisposeRequest, DocIntakeHandlingRequest } from '~/composables/docintake/api/useDocIntakeApi.types';

/** 열린 대화상자 — [접수 기록] 은 새 줄, 나머지는 그 줄(id) */
export type DocIntakeDialog = { kind: 'create' } | { kind: 'handling' | 'dispose' | 'void'; id: string };

/**
 * 접수 기록 화면 묶음 — 화면(심사 화면·서랍을 가진 페이지)이 `useDocIntake` 로 만들어 `DocIntakeSection` 까지 한 값으로 내려준다.
 * 가운데 컴포넌트는 이 값 하나와 `DocIntakeEvent` 하나만 넘긴다.
 */
export interface DocIntakeHost {
    /** 받는 중이면 null */
    bundle: DocIntakeBundle | null;
    /** 접수자(자동) — 로그인 운영자 이름 */
    receiver: string;
    busy: boolean;
    /** 대화상자 안 오류(서버 문구 그대로) */
    error: string;
    fieldErrors: Record<string, string>;
    dialog: DocIntakeDialog | null;
}

/** 폐기 때 고르는 스캔본 후보이자 줄의 스캔본 이름 찾기 — 대체된 파일은 `active: false`(고를 수 없다) */
export interface DocScanFile {
    id: string;
    label: string;
    active: boolean;
}

/** 접수 기록 쪽 동작 — 부모 화면이 `useDocIntake().handle` 로 받는다 */
export type DocIntakeEvent =
    | { type: 'dialog'; dialog: DocIntakeDialog | null }
    | { type: 'create'; body: DocIntakeCreateRequest }
    | { type: 'createFile'; file: File }
    | { type: 'handling'; id: string; body: DocIntakeHandlingRequest }
    | { type: 'dispose'; id: string; body: DocIntakeDisposeRequest }
    | { type: 'void'; id: string; reason: string };
