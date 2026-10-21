export type OperatorCreateMode = 'CREATE' | 'INVITE';

/** 다이얼로그가 내보내는 값 — 직접 추가는 한 명, 초대는 이메일 목록 */
export type OperatorCreateSubmit = { mode: 'CREATE'; operatorName: string; email: string; roleIds: string[] } | { mode: 'INVITE'; emails: string[]; roleIds: string[] };
