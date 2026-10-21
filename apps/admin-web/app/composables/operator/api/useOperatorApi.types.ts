/**
 * 운영자 API 가 주고받는 모양 — 후보 계약이고 확정 전이다. 칸 이름은 hamonica3 `MemberController`·`MemberDtos` 를 따르되 이 제품의 말로 바꿨다
 * (`userId` → `operatorId`, `userName` → `operatorName`, `approvalYn`·`lockYn`·`expireYn`·`useYn` → 불리언 `approved`·`locked`·`expired`·`enabled`, 역할 하나 `roleId` → 여럿 `roles`).
 * 용어: 회원 = 공개 사이트 이용자, **운영자 = 관리자 앱 계정**. 프로필 칸(국가·전화·회사·설명)은 운영자 정책에 없어 뺐다.
 * 시각은 UTC ISO(`Instant`)다 — 화면은 `useDisplayTime()` 으로 그린다. ⚠ ID 는 문자열이다(Snowflake). 숫자로 바꾸지 않는다.
 */

/** 목록을 가르는 탭 — 서버가 나눈다. `ACTIVE` 쓰는 사람 · `PENDING` 승인 대기(신청 또는 초대 수락 뒤) · `BLOCKED` 못 쓰는 사람(잠금·만료·사용 중지·기간 밖) */
export type OperatorTab = 'ACTIVE' | 'PENDING' | 'BLOCKED';

/** 운영자가 받은 역할 한 줄 */
export interface OperatorRoleRef {
    roleId: string;
    roleName: string;
}

export interface OperatorView {
    operatorId: string;
    email: string;
    operatorName: string;
    /** 최고 관리자(루트 계정) — 역할·상태·기간을 바꿀 수 없다. 역할은 비어 있다 */
    isSuper: boolean;
    roles: OperatorRoleRef[];
    /** 이 시각부터. 비면 제한 없음 — UTC ISO */
    accountStartAt: string | null;
    /** 이 시각까지. 비면 제한 없음 — UTC ISO */
    accountEndAt: string | null;
    /** 승인됨 */
    approved: boolean;
    /** 잠김 */
    locked: boolean;
    /** 만료 표시 */
    expired: boolean;
    /** 사용 중 (false 면 사용 중지) */
    enabled: boolean;
    /** 기간이 끝났나(또는 아직 시작 전인가) — 표시(`expired`)와 별개로 지금 시각으로 판정한 값 */
    periodOver: boolean;
    createdAt: string;
}

export interface OperatorCounts {
    active: number;
    pending: number;
    blocked: number;
}

export interface OperatorPage {
    items: OperatorView[];
    total: number;
    counts: OperatorCounts;
}

/** 이름·역할·계정 기간을 고친다. 이메일은 로그인 아이디라 못 바꾼다 · 비밀번호는 여기서 못 정한다(재설정 메일) */
export interface OperatorUpdateRequest {
    operatorName: string;
    /** 없으면 역할은 그대로 둔다 — 역할 변경 권한(`role.grant`)이 없는 운영자는 보내지 않는다 */
    roleIds?: string[];
    /** 둘 다 비면 무제한 */
    accountStartAt: string | null;
    accountEndAt: string | null;
}

/** 계정 상태 — 보낸 칸만 바꾼다. 켠 쪽이 "쓸 수 있는" 방향이 아니라 값 그대로다(`locked: true` = 잠금) */
export interface OperatorStatusRequest {
    approved?: boolean;
    locked?: boolean;
    expired?: boolean;
    enabled?: boolean;
}

/** 「운영자 추가」 — 최고 관리자(또는 권한 있는 운영자)가 계정을 바로 만든다. 승인된 상태로 만들어지고 임시 비밀번호는 서버가 이메일로 안내한다 */
export interface OperatorCreateRequest {
    operatorName: string;
    email: string;
    roleIds: string[];
}

/** 「초대」 — 초대 링크 메일을 보낸다. 받은 사람이 링크에서 이름·비밀번호를 정한다. 한 번에 한 주소(여럿이면 여러 번 부른다) */
export interface OperatorInviteRequest {
    email: string;
    roleIds: string[];
}

/** 대기 중인 초대 한 건 — 아직 수락하지 않은 초대 링크. 유효기간(기본 72시간 — 가정)이 지나면 `expired` 이고 링크로 등록할 수 없다. 수락하면 목록에서 사라지고 운영자가 생긴다 */
export interface OperatorInvitationView {
    invitationId: string;
    email: string;
    /** 초대할 때 정한 역할 — 수락한 운영자가 받는다 */
    roles: OperatorRoleRef[];
    /** 초대를 보낸 시각 — UTC ISO */
    invitedAt: string;
    /** 링크가 유효한 마지막 시각 — UTC ISO */
    expiresAt: string;
    /** 유효기간이 지났나 — 지금 시각으로 판정한 값 */
    expired: boolean;
}

/** 승인 대기(신청) 반려 — 사유는 선택이다(비어 있어도 된다). 반려하면 신청이 목록에서 사라진다 */
export interface OperatorRejectRequest {
    reason: string;
}
