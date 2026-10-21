/**
 * 알림 이벤트의 이름·설명·수신 대상 문구 — **목업 전용**이다. 실제로는 서버가 `messages_ko/en.properties`(`notification.event.<코드>.name` · `.description`)로 요청 언어에 맞춰 풀어 준다.
 * 목업(`composables/notification/api/mock/notification-event-mock.ts`)이 서버 응답을 흉내 내려고 이 번역표에서 지금 언어로 푼다. 서버가 붙으면 이 파일과 `en/notificationEvent.ts`, 두 `index.ts` 의 등록 줄을 함께 지운다.
 * 키는 `notificationEvent.<이벤트 코드>.name|description|recipient`. `en/notificationEvent.ts` 와 같은 키, 같은 차례. 이벤트 정의는 `docs/features/notification-contract.md`.
 */
export default {
    // ── 발행처가 이미 있는 이벤트 ──
    BATCH_RUN_FAILED: {
        name: '배치 실행 실패',
        description: '배치가 오류로 끝나면 알려요. 같은 배치가 계속 실패하면 30분 안에는 한 번만 알려요.',
        recipient: '배치 조회 권한을 가진 운영자',
    },
    OPERATOR_INVITATION_ACCEPTED: {
        name: '운영자 초대 수락',
        description: '초대받은 사람이 초대를 수락해 운영자 계정이 만들어지면 알려요.',
        recipient: '초대한 운영자와 운영자 관리 권한을 가진 운영자(수락한 본인 제외)',
    },
    OPERATOR_ACCOUNT_CREATED: {
        name: '운영자 직접 추가',
        description: '운영자를 직접 추가하면 알려요.',
        recipient: '운영자 관리 권한을 가진 운영자(추가한 본인 제외)',
    },
    OPERATOR_STATUS_CHANGED_ME: {
        name: '내 계정 상태 변경',
        description: '내 계정이 잠기거나 사용 중지·승인되거나 사용 기간이 바뀌면 알려요.',
        recipient: '계정이 바뀐 본인',
    },
    OPERATOR_LOCKED_BY_FAILURES: {
        name: '로그인 실패로 계정 잠금',
        description: '로그인에 5회 연속 실패해 계정이 잠기기 시작하면 알려요.',
        recipient: '운영자 잠금 권한을 가진 운영자',
    },
    ROLES_CHANGED_ME: {
        name: '내 역할·권한 변경',
        description: '내 역할이 추가·회수되거나 내 역할의 권한이 바뀌면 알려요.',
        recipient: '역할이 바뀐 본인',
    },
    PASSWORD_CHANGED_ME: {
        name: '비밀번호 변경',
        description: '내 비밀번호가 바뀌면 알려요.',
        recipient: '비밀번호를 바꾼 본인',
    },
    PASSWORD_RESET_DONE_ME: {
        name: '비밀번호 재설정',
        description: '내 비밀번호가 재설정되면 알려요.',
        recipient: '비밀번호가 재설정된 본인',
    },
    SITE_SETTINGS_CHANGED: {
        name: '사이트 설정 변경',
        description: '사이트 이름·표시 기본값 같은 설정이 바뀌면 알려요. 60초 안의 연속 변경은 한 번만 알려요.',
        recipient: '사이트 설정 조회 권한을 가진 운영자(바꾼 본인 제외)',
    },
    GRADE_SETTINGS_SAVED: {
        name: '등급 설정 저장',
        description: '등급 승급 조건이 저장되면 알려요.',
        recipient: '등급 설정 조회 권한을 가진 운영자(저장한 본인 제외)',
    },
    GRADE_PROMOTION_RUN: {
        name: '등급 승급 배치 종료',
        description: '일수 승급 배치가 끝나고 등급이 오른 회원이 있으면 알려요.',
        recipient: '등급 설정 조회 권한을 가진 운영자',
    },

    // ── 예약 이벤트(발행처가 아직 없다 — 「준비 중」) ──
    REPORT_RECEIVED: {
        name: '신고 접수',
        description: '새 신고가 들어와 검토를 기다리면 알려요.',
        recipient: '신고 처리 권한을 가진 운영자',
    },
    SANCTION_APPEAL_FILED: {
        name: '제재 이의제기 접수',
        description: '회원이 제재에 이의를 제기하면 알려요.',
        recipient: '이의제기 처리 권한을 가진 운영자',
    },
    BOARD_SANCTION_REPEAT_REVIEW: {
        name: '게시판 제재 반복 검토',
        description: '같은 대상에게 제재가 반복돼 검토가 필요하면 알려요.',
        recipient: '제재 처리 권한을 가진 운영자',
    },
    NICKNAME_IMAGE_PENDING: {
        name: '닉네임 이미지 심사 대기',
        description: '심사를 기다리는 닉네임 이미지가 생기면 알려요.',
        recipient: '닉네임 이미지 처리 권한을 가진 운영자',
    },
    CLUB_OPEN_REQUEST_PENDING: {
        name: '소모임 개설 신청 대기',
        description: '소모임 개설 신청이 들어오면 알려요.',
        recipient: '소모임 처리 권한을 가진 운영자',
    },
    HOTDEAL_SUSPECTED_PROMO: {
        name: '핫딜 홍보 의심',
        description: '핫딜 글이 홍보로 의심되면 알려요.',
        recipient: '핫딜 처리 권한을 가진 운영자',
    },
    COMMERCE_ORDER_ANOMALY: {
        name: '주문 이상 감지',
        description: '공동구매 주문에 이상 징후가 보이면 바로 알려요.',
        recipient: '주문 처리 권한을 가진 운영자',
    },
    COMMERCE_REFUND_REQUESTED: {
        name: '환불 요청',
        description: '환불 요청이 들어오면 알려요.',
        recipient: '환불 승인 권한을 가진 운영자',
    },
    BUSINESS_APPLICATION_PENDING: {
        name: '사업자 신청 대기',
        description: '사업자 신청이 들어와 심사를 기다리면 알려요.',
        recipient: '사업자 처리 권한을 가진 운영자',
    },
    AD_REVIEW_PENDING: {
        name: '광고 심사 대기',
        description: '심사를 기다리는 광고가 생기면 알려요.',
        recipient: '광고 처리 권한을 가진 운영자',
    },
    OPERATOR_APPLICATION_PENDING: {
        name: '운영자 신청 대기',
        description: '운영자 신청이 들어오면 알려요.',
        recipient: '운영자 관리 권한을 가진 운영자',
    },
};
