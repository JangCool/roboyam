import type { MessageAudience } from '~/composables/template/api/useNotificationMessageApi.types';

/**
 * 카카오톡(알림톡) 템플릿 API 의 모양 — `docs/features/message-template-contract.md` 「개정 4」 유형별 템플릿 설정 칸. **준비 중**(알림 도구에서 카카오가 업체 계약 전) — 화면은 보기만 하고 만들기를 막는다.
 * 알림톡은 카카오에 등록·승인된 본문만 보낼 수 있다 — 템플릿 = 알림톡 템플릿 코드 · 승인된 본문(읽기 전용) · 변수 대응표(`#{이름}` ↔ 로보얌 변수) · 버튼(이름 · 링크 변수).
 * 문구(카카오톡 채널)는 이 템플릿 하나를 고를 뿐 본문을 따로 쓰지 않는다. 기본 템플릿은 없다(코드가 카카오 승인을 대신할 수 없다).
 */

/** 카카오 검수 상태 — 승인된 것만 보낼 수 있다 */
export type KakaoTemplateStatus = 'REQUESTED' | 'APPROVED' | 'REJECTED';

/** 변수 대응 한 줄 — 승인 본문의 `#{이름}` 자리에 들어갈 로보얌 변수 */
export interface KakaoVariableMapping {
    /** `#{…}` 안의 이름(카카오 등록 때 정한 글자) */
    placeholder: string;
    /** 로보얌 변수 이름(`{{이름}}` 의 이름) */
    variable: string;
    /** 미리보기 견본 값 */
    sample: string;
}

/** 알림톡 버튼 하나 — 웹 링크만(가정) */
export interface KakaoButtonView {
    name: string;
    /** 링크 변수 이름(`link` …) */
    linkVariable: string;
}

/** `GET /api/admin/notification-templates/kakao` 의 한 줄 */
export interface KakaoTemplateListItemView {
    id: string;
    name: string;
    /** 카카오에 등록한 알림톡 템플릿 코드 */
    templateCode: string;
    status: KakaoTemplateStatus;
    /** 이 템플릿을 쓰는 문구 수 */
    messageCount: number;
    /** 쓰는 문구 수(받는 사람별 — 카카오톡은 회원·운영자 이벤트 모두에 있다) */
    messageCounts: Record<MessageAudience, number>;
    updatedAt: string | null;
    updatedByName: string | null;
}

/** `GET /api/admin/notification-templates/kakao/{id}` */
export interface KakaoTemplateView extends KakaoTemplateListItemView {
    /** 카카오에 승인된 본문(읽기 전용 — `#{이름}` 자리 포함) */
    approvedBody: string;
    /** 알림톡 강조 제목(승인된 것 — 없으면 빈 글자) */
    emphasis: string;
    mappings: KakaoVariableMapping[];
    buttons: KakaoButtonView[];
    /** 카카오톡 채널(발신 프로필) 이름 */
    channelName: string;
}
