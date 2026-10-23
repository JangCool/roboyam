// MOCK: 알림 문구·알림 템플릿 API(`/api/admin/notification-messages/**` · `/api/admin/notification-templates/{mail|slack|mattermost|kakao}/**`, 계약 `docs/features/message-template-contract.md` 「개정 4」)의 메모리 목업
// — `useNotificationMessageApi.ts` · `useMailTemplateApi.ts` · `useChatTemplateApi.ts` · `useKakaoTemplateApi.ts` 가 이 파일의 함수를 부른다(문구가 템플릿을 가리키고 템플릿이 쓰는 문구 수를 세므로 상태를 한 곳에 둔다).
// 서버가 붙으면 `template/api/mock/` 과 함께 지운다. 바꾼 것은 메모리에만 남아 **새로고침하면 처음 상태로 돌아간다**.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` (쉼표로 여러 개) ─
//   template-error          목록(대상·문구·템플릿 목록)이 `500 INTERNAL` 로 실패(화면 오류 상태와 다시 시도)
//   template-detail-error   상세(대상 상세·문구 하나·템플릿 하나)가 `500` 으로 실패
//   template-empty          대상(이벤트·필수 안내 메일)이 하나도 없다 — 문구 목록·필수 안내 메일 목록이 빈 상태, 이벤트 목록의 「쓰는 문구」가 모두 「—」(템플릿은 기본 템플릿이 늘 있다)
//   template-denied         `template.read` 가 없다 — 읽기가 모두 `403 PERMISSION_DENIED`(권한 없음 안내, 이벤트 목록은 「쓰는 문구」 칸을 감춘다)
//   template-readonly       `template.read` 만 있다 — 화면이 읽기 전용, 쓰기는 `403`
//   template-save-error     쓰기(대상 저장·문구·템플릿 만들기·저장·삭제)가 `500` 으로 실패
//   message-in-use          문구 지우기가 늘 `409 MESSAGE_IN_USE`
//   template-in-use         템플릿(메일·Slack·Mattermost) 지우기가 늘 `409 TEMPLATE_IN_USE`(개정 3 의 `layout-in-use`)
//   template-test-fail      이메일 시험 발송이 실패 결과(`CONNECT_FAILED`) · Slack·Mattermost 시험이 실패 결과
//   template-test-limit     이메일·Slack·Mattermost 시험이 `429 MAIL_TEST_RATE_LIMITED`(30초)
//   template-history-error  변경 이력(문구·템플릿)이 `500` 으로 실패
//
// ── 흉내 ─
//   대상(개정 3 그대로): ① 운영자 이벤트 — 지금 `AdminNotificationEvent` 의 24종(준비 중 포함). 채널 앱 알림·이메일·Slack·Mattermost·카카오톡(준비 중), 본인에게만 가는 4종(`*_ME`)은 Slack·Mattermost 없음.
//     ② 회원 이벤트 — 지금 실제로 일어나는 5종(`MemberNotificationType`). 채널 앱 알림·이메일·카카오톡(준비 중). ③ 필수 안내 메일 5종 — 채널 이메일뿐.
//   기본 문구: 이메일 = 지금 메일 글(`mail.*`·`notification.mail.*`), 앱 알림 = 지금 알림 글(`notification.event.*` · `member.notification.*` — `{0}` 자리는 이름 붙은 변수로),
//     Slack·Mattermost = 제목 `{{title}}` + 본문 `{{body}}`(개정 3 의 채팅 글 `{{severityIcon}} **{{title}}**…[열기]` 중 그림·굵게·「열기」는 기본 템플릿이 맡는다 — 지금 `ChannelMessageFormatter` 모양).
//     카카오톡은 기본 문구가 없다(알림톡 템플릿을 카카오에 먼저 등록해야 한다) — 예시 문구 1개(회원 「내 글에 댓글」).
//   템플릿: 메일 5개(회원 기본 · 운영자 기본 · 보안 안내(모두) · 공지(회원) · 간단(운영자) — 개정 3 의 레이아웃 그대로) · Slack 2개(기본 · 짧은 카드) · Mattermost 2개(기본 · 운영 채널) · 카카오톡 1개(예시).
//   사용자 문구 9개 · 규칙 4개. 공통 변수는 받는 사람별: 회원 `siteName`·`siteUrl`·`recipientName`, 운영자 `siteName`·`adminUrl`·`recipientName`. 메일 템플릿 `ANY` 는 `siteName`·`recipientName` 만.
//   서버 규칙 흉내: 이메일 `UtilMailTemplate.validateMessage` · 앱 알림·Slack·Mattermost `UtilNotificationMessage.validateInApp|validateChat` → 400 `VALIDATION` `fields.name|subject|body|templateId`,
//   대상에 없는 채널(`fields.channel`) · 준비 중 채널(409 `CHANNEL_PREPARING`), 기본 문구·템플릿 고치기·지우기 409, 쓰는 중 지우기 409, 대상·채널당 20개 409 `MESSAGE_LIMIT`, 규칙의 조건·문구(`fields.channels.EMAIL.rules[0].messageId` …).
//   오류 문구·변수 설명·조건 이름·대상 이름·기본 문구/템플릿 이름은 서버가 요청 언어로 줄 글이라 한국어로 둔다(`i18n.md` 5).

import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { SettingHistoryChange } from '~/composables/common/useSettingHistory.types';
import type { ChatTemplateColor, ChatTemplateHistoryPage, ChatTemplateListItemView, ChatTemplateSaveRequest, ChatTemplateType, ChatTemplateView } from '~/composables/template/api/useChatTemplateApi.types';
import { CHAT_SEVERITIES, CHAT_TEMPLATE_LIMIT } from '~/composables/template/api/useChatTemplateApi.types';
import type { KakaoTemplateListItemView, KakaoTemplateView } from '~/composables/template/api/useKakaoTemplateApi.types';
import type {
    MailTemplateAudience,
    MailTemplateAudienceQuery,
    MailTemplateColor,
    MailTemplateHistoryPage,
    MailTemplateListItemView,
    MailTemplateSaveRequest,
    MailTemplateView,
} from '~/composables/template/api/useMailTemplateApi.types';
import { MAIL_TEMPLATE_AUDIENCES, MAIL_TEMPLATE_LIMIT } from '~/composables/template/api/useMailTemplateApi.types';
import type {
    ChatMessageTestRequest,
    ChatMessageTestResultView,
    MailMessageTestRequest,
    MailMessageTestResultView,
    MailPreviewRequest,
    MailPreviewView,
    MessageAudience,
    MessageChannel,
    MessageChannelDetailView,
    MessageChannelSummaryView,
    MessageConditionView,
    MessageHistoryItemView,
    MessageHistoryPage,
    MessageListItemView,
    MessageLocale,
    MessageRuleView,
    MessageSaveRequest,
    MessageSeverity,
    MessageTargetDetailView,
    MessageTargetQuery,
    MessageTargetSaveRequest,
    MessageTargetType,
    MessageTargetView,
    MessageVariableView,
    MessageView,
} from '~/composables/template/api/useNotificationMessageApi.types';
import { MESSAGE_LIMIT, MESSAGE_LOCALES, MESSAGE_RULE_MAX } from '~/composables/template/api/useNotificationMessageApi.types';
import type { ChatTemplateField } from '~/utils/util-chat-template';
import UtilChatTemplate from '~/utils/util-chat-template';
import type { ContentProblem, MailMessageField, MailTemplateField } from '~/utils/util-mail-template';
import UtilMailTemplate from '~/utils/util-mail-template';
import type { NotificationMessageField } from '~/utils/util-notification-message';
import UtilNotificationMessage from '~/utils/util-notification-message';

type Can = (code: string) => boolean;
type Content = Record<MessageLocale, { subject: string; body: string }>;

/** 목업 스위치가 정하는 내 권한 — 서버 `Permission` 에는 아직 `template.*` 가 없어 최고 관리자만 화면이 보인다(메뉴는 실제 권한을 본다) */
export function mockTemplateCan(can: Can): Can {
    return (code: string) => {
        const flags = mockFlags();
        if (code.startsWith('template.') && flags.has('template-denied')) return false;
        if (code === 'template.manage' && flags.has('template-readonly')) return false;
        return can(code);
    };
}

/* ── 변수 ── */

const v = (name: string, description: string, sample: string, extra: { required?: boolean; link?: boolean } = {}): MessageVariableView => ({ name, description, sample, required: extra.required ?? false, link: extra.link ?? false });

const SITE_NAME_VAR = v('siteName', '사이트 이름', '로보얌');
const SITE_URL_VAR = v('siteUrl', '공개 사이트 주소', 'https://roboyam.example.com', { link: true });
const ADMIN_URL_VAR = v('adminUrl', '관리 사이트 주소', 'https://admin.roboyam.example.com', { link: true });
const RECIPIENT_VAR = v('recipientName', '받는 사람 이름(없으면 이메일)', '홍길동');

/** 받는 사람(메일 템플릿은 쓰는 대상)의 공통 변수 */
function commonVars(audience: MailTemplateAudience): MessageVariableView[] {
    if (audience === 'MEMBER') return [SITE_NAME_VAR, SITE_URL_VAR, RECIPIENT_VAR];
    if (audience === 'OPERATOR') return [SITE_NAME_VAR, ADMIN_URL_VAR, RECIPIENT_VAR];
    return [SITE_NAME_VAR, RECIPIENT_VAR];
}

const btn = (link: string, label: string) => `<p data-mail-button=""><a href="{{${link}}}">${label}</a></p>`;

/* ── 대상 정의(코드가 정하는 것) ── */

interface ChannelDef {
    channel: MessageChannel;
    variables: MessageVariableView[];
    conditions: MessageConditionView[];
    /** 기본 문구 — 없는 채널(카카오톡)은 null */
    defaults: Content | null;
}

interface TargetDef {
    type: MessageTargetType;
    code: string;
    audience: MessageAudience;
    area: string | null;
    severity: MessageSeverity | null;
    name: string;
    description: string;
    security: boolean;
    channels: ChannelDef[];
}

/** 준비 중인 채널 — 알림 도구에서 카카오가 준비 중(업체 계약 전) */
const PREPARING: readonly MessageChannel[] = ['KAKAO'];

const RECIPIENT_SOURCE: MessageConditionView = {
    key: 'recipientSource',
    label: '받는 운영자',
    options: [
        { value: 'DEFAULT', label: '기본 수신 규칙으로 받는 운영자' },
        { value: 'TEAM', label: '추가 수신 팀으로 받는 운영자' },
    ],
};

/** 운영자 이벤트 하나 — 지금 서버 번들(`notification.event.<코드>.*`)의 이름·설명·제목·본문. `{0}` 자리는 `params` 의 이름으로 */
interface OperatorEventSeed {
    code: string;
    area: string;
    severity: MessageSeverity;
    name: string;
    description: string;
    params: [name: string, description: string, sample: string][];
    title: Record<MessageLocale, string>;
    body: Record<MessageLocale, string>;
    path: string;
    /** 본인에게만 가는 이벤트 — Slack·Mattermost(공유 채널)로 보내지 않는다 */
    self?: boolean;
    extraConditions?: Partial<Record<MessageChannel, MessageConditionView[]>>;
}

const BATCH_JOB: MessageConditionView = {
    key: 'jobName',
    label: '배치',
    options: [
        { value: 'grade-promotion', label: '등급 일수 승급' },
        { value: 'member-notification-cleanup', label: '회원 알림 정리' },
    ],
};

const pending = (code: string, area: string, severity: MessageSeverity, name: string, description: string, ko: [string, string], en: [string, string]): OperatorEventSeed => ({
    code,
    area,
    severity,
    name,
    description,
    params: [],
    title: { ko: ko[0], en: en[0] },
    body: { ko: ko[1], en: en[1] },
    path: '/',
});

const OPERATOR_EVENTS: OperatorEventSeed[] = [
    {
        code: 'BATCH_RUN_FAILED',
        area: 'system',
        severity: 'WARNING',
        name: '배치 실패',
        description: '배치가 실패하면 알려요. 같은 배치가 계속 실패하면 30분에 한 번만 알려요.',
        params: [
            ['jobName', '배치 이름', 'grade-promotion'],
            ['reason', '실패 까닭 요약', 'DB 연결 시간이 지났어요.'],
        ],
        title: { ko: '배치가 실패했어요', en: 'A batch failed' },
        body: { ko: '{{jobName}} 배치가 실패했어요. {{reason}}', en: 'The batch {{jobName}} failed. {{reason}}' },
        path: '/settings/batch-logs?jobName=grade-promotion',
        extraConditions: { EMAIL: [BATCH_JOB], SLACK: [BATCH_JOB], MATTERMOST: [BATCH_JOB] },
    },
    {
        code: 'OPERATOR_INVITATION_ACCEPTED',
        area: 'system',
        severity: 'INFO',
        name: '운영자 초대 수락',
        description: '초대받은 사람이 초대를 수락해 운영자가 되면 알려요.',
        params: [['operatorName', '운영자 이름', '박운영']],
        title: { ko: '새 운영자가 초대를 수락했어요', en: 'A new operator accepted the invitation' },
        body: { ko: '{{operatorName}} 님이 초대를 수락해 운영자가 되었어요.', en: '{{operatorName}} accepted the invitation and became an operator.' },
        path: '/settings/operators',
    },
    {
        code: 'OPERATOR_ACCOUNT_CREATED',
        area: 'system',
        severity: 'INFO',
        name: '운영자 직접 추가',
        description: '운영자 계정을 직접 만들면 알려요.',
        params: [['operatorName', '운영자 이름', '박운영']],
        title: { ko: '운영자 계정이 추가됐어요', en: 'An operator account was created' },
        body: { ko: '{{operatorName}} 님이 운영자로 등록됐어요.', en: '{{operatorName}} was registered as an operator.' },
        path: '/settings/operators',
    },
    {
        code: 'OPERATOR_STATUS_CHANGED_ME',
        area: 'security',
        severity: 'WARNING',
        name: '내 계정 상태 변경',
        description: '내 계정이 잠기거나 사용 중지·승인·사용 기간이 바뀌면 알려요. 보안 알림이라 끌 수 없어요.',
        params: [['change', '바뀐 내용(서버가 요청 언어로 푼 한 줄)', '계정 기간이 2026-12-31 까지로 바뀌었어요.']],
        title: { ko: '내 계정 상태가 바뀌었어요', en: 'Your account status changed' },
        body: { ko: '{{change}}', en: '{{change}}' },
        path: '/me',
        self: true,
    },
    {
        code: 'OPERATOR_LOCKED_BY_FAILURES',
        area: 'security',
        severity: 'CRITICAL',
        name: '로그인 실패로 계정 잠금',
        description: '로그인에 연달아 실패해 계정이 잠기기 시작하면 알려요. 무차별 대입 시도를 알아채는 데 써요.',
        params: [['operatorName', '잠긴 운영자 이름', '박운영']],
        title: { ko: '로그인 실패로 계정이 잠겼어요', en: 'An account was locked after failed logins' },
        body: { ko: '{{operatorName}} 님의 계정이 로그인 실패가 이어져 잠겼어요. 일정 시간이 지나면 저절로 풀려요.', en: 'The account of {{operatorName}} was locked after repeated failed logins. It unlocks automatically after a while.' },
        path: '/settings/operators',
    },
    {
        code: 'ROLES_CHANGED_ME',
        area: 'security',
        severity: 'INFO',
        name: '내 역할 변경',
        description: '내 역할이 더해지거나 회수되거나 내 역할의 권한이 바뀌면 알려요. 보안 알림이라 끌 수 없어요.',
        params: [['change', '바뀐 내용(서버가 요청 언어로 푼 한 줄)', '「게시판 관리자」 역할이 더해졌어요.']],
        title: { ko: '내 역할이 바뀌었어요', en: 'Your roles changed' },
        body: { ko: '{{change}}', en: '{{change}}' },
        path: '/me',
        self: true,
    },
    {
        code: 'PASSWORD_CHANGED_ME',
        area: 'security',
        severity: 'WARNING',
        name: '내 비밀번호 변경',
        description: '내 비밀번호가 바뀌면 알려요. 내가 바꾼 것이 아니라면 바로 확인해야 해서 끌 수 없어요.',
        params: [],
        title: { ko: '비밀번호가 변경됐어요', en: 'Your password was changed' },
        body: { ko: '내 계정의 비밀번호가 변경됐어요. 직접 바꾼 것이 아니라면 바로 관리자에게 알려 주세요.', en: 'The password of your account was changed. If you did not do this, tell an administrator right away.' },
        path: '/me?tab=password',
        self: true,
    },
    {
        code: 'PASSWORD_RESET_DONE_ME',
        area: 'security',
        severity: 'WARNING',
        name: '내 비밀번호 재설정',
        description: '승인번호로 내 비밀번호가 재설정되면 알려요. 내가 한 것이 아니라면 바로 확인해야 해서 끌 수 없어요.',
        params: [],
        title: { ko: '비밀번호가 재설정됐어요', en: 'Your password was reset' },
        body: {
            ko: '승인번호로 내 계정의 비밀번호가 재설정됐어요. 직접 한 것이 아니라면 바로 관리자에게 알려 주세요.',
            en: 'The password of your account was reset with a verification code. If you did not do this, tell an administrator right away.',
        },
        path: '/me?tab=password',
        self: true,
    },
    {
        code: 'SITE_SETTINGS_CHANGED',
        area: 'system',
        severity: 'INFO',
        name: '사이트 설정 변경',
        description: '사이트 기본 정보·로고·아이콘·링크 미리보기 이미지·표시 기본값이 바뀌면 알려요. 연달아 바꾸면 1분에 한 번만 알려요.',
        params: [['changedItems', '바뀐 항목', '사이트 이름, 로고']],
        title: { ko: '사이트 설정이 바뀌었어요', en: 'Site settings changed' },
        body: { ko: '바뀐 항목: {{changedItems}}', en: 'Changed: {{changedItems}}' },
        path: '/settings/general',
    },
    {
        code: 'GRADE_SETTINGS_SAVED',
        area: 'member',
        severity: 'INFO',
        name: '등급 설정 저장',
        description: '등급 목록이나 승급 조건이 저장되면 알려요.',
        params: [],
        title: { ko: '등급 설정이 저장됐어요', en: 'Grade settings were saved' },
        body: { ko: '등급 목록이나 승급 조건이 바뀌었어요.', en: 'The grade list or the promotion conditions changed.' },
        path: '/grades',
    },
    {
        code: 'GRADE_PROMOTION_RUN',
        area: 'member',
        severity: 'INFO',
        name: '등급 일수 승급 실행',
        description: '가입 일수 승급 배치가 회원을 승급시키고 끝나면 알려요. 승급한 회원이 없으면 알리지 않아요.',
        params: [['count', '승급한 회원 수', '12']],
        title: { ko: '회원 등급 승급 배치가 끝났어요', en: 'The member grade promotion batch finished' },
        body: { ko: '{{count}}명이 승급했어요.', en: '{{count}} members were promoted.' },
        path: '/settings/batch-logs?jobName=grade-promotion',
    },
    {
        code: 'REPORT_RECEIVED',
        area: 'safety',
        severity: 'INFO',
        name: '신고 접수',
        description: '새 신고가 접수되면 알려요.',
        params: [],
        title: { ko: '새 신고가 접수됐어요', en: 'A new report was received' },
        body: { ko: '신고 내용은 신고 처리 기능이 생기면 확인할 수 있어요.', en: 'You can review report details once report handling is available.' },
        path: '/',
    },
    pending('SANCTION_APPEAL_FILED', 'safety', 'INFO', '제재 이의 신청', '회원이 제재에 이의를 신청하면 알려요.', ['제재 이의 신청이 들어왔어요', '이의 신청 화면에서 확인해 주세요.'], ['A sanction appeal was filed', 'Check the appeal screen.']),
    pending('BOARD_SANCTION_REPEAT_REVIEW', 'safety', 'WARNING', '반복 제재 검토 필요', '게시판 제재가 반복돼 운영자의 검토가 필요하면 알려요.', ['반복 제재 회원을 검토해 주세요', '제재 화면에서 확인해 주세요.'], ['Please review a repeatedly sanctioned member', 'Check the sanction screen.']),
    pending('NICKNAME_IMAGE_PENDING', 'member', 'INFO', '닉네임 이미지 승인 대기', '회원이 올린 닉네임 이미지가 승인을 기다리면 알려요.', ['승인을 기다리는 닉네임 이미지가 있어요', '닉네임 이미지 화면에서 확인해 주세요.'], ['A nickname image is waiting for approval', 'Check the nickname image screen.']),
    pending('CLUB_OPEN_REQUEST_PENDING', 'club', 'INFO', '모임 개설 신청 대기', '모임 개설 신청이 승인을 기다리면 알려요.', ['승인을 기다리는 모임 개설 신청이 있어요', '모임 화면에서 확인해 주세요.'], ['A club opening request is waiting for approval', 'Check the club screen.']),
    pending('HOTDEAL_SUSPECTED_PROMO', 'hotdeal', 'WARNING', '핫딜 홍보 의심', '핫딜 글이 홍보로 의심되면 알려요.', ['홍보가 의심되는 핫딜이 있어요', '핫딜 화면에서 확인해 주세요.'], ['A hot deal looks like a promotion', 'Check the hot deal screen.']),
    pending('COMMERCE_ORDER_ANOMALY', 'groupbuy', 'CRITICAL', '주문 이상 감지', '공동구매 주문·결제에서 이상이 감지되면 알려요.', ['주문에서 이상이 감지됐어요', '주문 화면에서 바로 확인해 주세요.'], ['An anomaly was detected in an order', 'Check the order screen right away.']),
    pending('COMMERCE_REFUND_REQUESTED', 'groupbuy', 'WARNING', '환불 요청', '환불 요청이 들어오면 알려요.', ['환불 요청이 들어왔어요', '주문 화면에서 확인해 주세요.'], ['A refund was requested', 'Check the order screen.']),
    pending('BUSINESS_APPLICATION_PENDING', 'bizAd', 'INFO', '사업자 신청 대기', '사업자 회원 신청이 심사를 기다리면 알려요.', ['심사를 기다리는 사업자 신청이 있어요', '사업자 심사 화면에서 확인해 주세요.'], ['A business application is waiting for review', 'Check the business review screen.']),
    pending('AD_REVIEW_PENDING', 'bizAd', 'INFO', '광고 심사 대기', '광고가 심사를 기다리면 알려요.', ['심사를 기다리는 광고가 있어요', '광고 화면에서 확인해 주세요.'], ['An ad is waiting for review', 'Check the ad screen.']),
    pending('OPERATOR_APPLICATION_PENDING', 'system', 'INFO', '운영자 신청 대기', '운영자 신청이 승인을 기다리면 알려요.', ['승인을 기다리는 운영자 신청이 있어요', '운영자 화면에서 확인해 주세요.'], ['An operator application is waiting for approval', 'Check the operator screen.']),
];

/** 관리자 알림 메일의 한 양식(`NotificationMailTemplate` — 번들 `notification.mail.*`) — 운영자 이벤트마다 같은 기본 이메일 템플릿이다 */
const OPERATOR_EMAIL: Content = {
    ko: {
        subject: '{{title}}',
        body:
            '<p><strong>{{title}}</strong></p><p>{{body}}</p>' +
            btn('link', '관리자 앱에서 보기') +
            '<p>{{link}}</p><p>관리자 알림 설정에 따라 보낸 메일이에요. 내 정보 &gt; 개인 설정 &gt; 알림 설정에서 알림 메일 수신을 끌 수 있어요.</p>',
    },
    en: {
        subject: '{{title}}',
        body:
            '<p><strong>{{title}}</strong></p><p>{{body}}</p>' +
            btn('link', 'Open in the admin app') +
            '<p>{{link}}</p><p>This email was sent according to your admin notification settings. You can turn notification emails off under My info &gt; Personal settings &gt; Notification settings.</p>',
    },
};

/** Slack·Mattermost 기본 문구 — 제목 한 줄 + 본문(그림·굵게·「열기」 버튼·꼬리말은 기본 템플릿이 붙인다 — 지금 `ChannelMessageFormatter.format` 의 모양을 문구와 템플릿으로 나눴다) */
const OPERATOR_CHAT: Content = {
    ko: { subject: '{{title}}', body: '{{body}}' },
    en: { subject: '{{title}}', body: '{{body}}' },
};

/** 이메일 · Slack · Mattermost · 카카오톡이 같이 쓰는 「앱 알림 문구로 만든 글」 변수(개정 3 가정 그대로) */
function sentVars(title: string, body: string, link: string, linkDescription: string): MessageVariableView[] {
    return [
        v('title', '앱 알림 제목(이 이벤트의 앱 알림 문구로 만든 글)', title),
        v('body', '앱 알림 본문(이 이벤트의 앱 알림 문구로 만든 글)', body),
        v('link', linkDescription, link, { link: true }),
        v('occurredAt', '일어난 시각(받는 사람의 시간대)', '2026-10-23 09:30'),
    ];
}

/** 카카오톡 채널(준비 중) — 변수는 이메일과 같고 조건은 없다. 기본 문구는 없다 */
const kakaoChannel = (variables: MessageVariableView[]): ChannelDef => ({ channel: 'KAKAO', variables, conditions: [], defaults: null });

function operatorTarget(seed: OperatorEventSeed): TargetDef {
    const common = commonVars('OPERATOR');
    const params = seed.params.map(([name, description, sample]) => v(name, description, sample));
    const sent = sentVars(UtilNotificationMessage.fillSamples(seed.title.ko, params), UtilNotificationMessage.fillSamples(seed.body.ko, params), `https://admin.roboyam.example.com${seed.path}`, '관리자 앱에서 볼 주소');
    const channels: ChannelDef[] = [
        { channel: 'IN_APP', variables: [...common, ...params], conditions: [], defaults: { ko: { subject: seed.title.ko, body: seed.body.ko }, en: { subject: seed.title.en, body: seed.body.en } } },
        { channel: 'EMAIL', variables: [...common, ...params, ...sent], conditions: [RECIPIENT_SOURCE, ...(seed.extraConditions?.EMAIL ?? [])], defaults: OPERATOR_EMAIL },
    ];
    if (!seed.self) {
        for (const channel of ['SLACK', 'MATTERMOST'] as const) channels.push({ channel, variables: [...common, ...params, ...sent], conditions: seed.extraConditions?.[channel] ?? [], defaults: OPERATOR_CHAT });
    }
    channels.push(kakaoChannel([...common, ...params, ...sent]));
    return { type: 'EVENT', code: seed.code, audience: 'OPERATOR', area: seed.area, severity: seed.severity, name: seed.name, description: seed.description, security: false, channels };
}

/** 회원 이벤트 하나 — 지금 서버 `MemberNotificationType` · 번들 `member.notification.*` */
interface MemberEventSeed {
    code: string;
    area: string;
    name: string;
    description: string;
    params: [name: string, description: string, sample: string][];
    title: Record<MessageLocale, string>;
    body: Record<MessageLocale, string>;
    path: string;
    conditions: MessageConditionView[];
}

const MEMBER_EVENTS: MemberEventSeed[] = [
    {
        code: 'COMMENT',
        area: 'content',
        name: '내 글에 댓글',
        description: '내 글에 새 댓글이 달리면 글쓴이에게 알려요. 같은 글의 새 댓글은 묶음 시간 안에서 한 줄로 묶어요.',
        params: [
            ['actorName', '댓글 쓴 회원 닉네임', '로보얌이'],
            ['postTitle', '글 제목', '주말 등산 같이 가실 분'],
            ['excerpt', '댓글 앞부분', '저도 갈게요! 몇 시에 모이나요?'],
            ['otherCount', '묶인 다른 댓글 수', '3'],
        ],
        title: { ko: '{{actorName}}님이 내 글에 댓글을 달았어요', en: '{{actorName}} commented on your post' },
        body: { ko: '{{excerpt}}', en: '{{excerpt}}' },
        path: '/boards/free/123',
        conditions: [
            {
                key: 'grouped',
                label: '묶음',
                options: [
                    { value: 'SINGLE', label: '댓글 하나' },
                    { value: 'GROUPED', label: '묶인 댓글(같은 글의 여러 댓글)' },
                ],
            },
        ],
    },
    {
        code: 'REPLY',
        area: 'content',
        name: '내 댓글에 답글',
        description: '내 댓글에 답글이 달리면 알려요.',
        params: [
            ['actorName', '답글 쓴 회원 닉네임', '로보얌이'],
            ['excerpt', '답글 앞부분', '좋은 생각이에요!'],
        ],
        title: { ko: '{{actorName}}님이 내 댓글에 답글을 달았어요', en: '{{actorName}} replied to your comment' },
        body: { ko: '{{excerpt}}', en: '{{excerpt}}' },
        path: '/boards/free/123#c-456',
        conditions: [],
    },
    {
        code: 'POINTS_REVOKED',
        area: 'member',
        name: '포인트 회수',
        description: '운영 조정으로 내 포인트가 회수되면 알려요. 계정 알림이라 회원이 끌 수 없어요.',
        params: [
            ['amount', '회수한 포인트', '50'],
            ['reason', '회수 까닭(서버가 요청 언어로 푼 한 줄)', '운영 조정: 중복 지급 정정'],
        ],
        title: { ko: '{{amount}}P가 회수됐어요', en: '{{amount}}P was taken back' },
        body: { ko: '{{reason}}', en: '{{reason}}' },
        path: '/me/points',
        conditions: [],
    },
    {
        code: 'POST_HIDDEN',
        area: 'safety',
        name: '내 글 숨김(신고)',
        description: '신고가 쌓이거나 불법촬영물·권리 침해 신고로 내 글이 저절로 숨겨지면 알려요. 계정 알림이라 회원이 끌 수 없어요.',
        params: [['postTitle', '숨겨진 글 제목', '주말 등산 같이 가실 분']],
        title: { ko: '신고가 접수되어 내 글이 숨겨졌어요', en: 'Your post was hidden after being reported' },
        body: { ko: '{{postTitle}}', en: '{{postTitle}}' },
        path: '/me/posts',
        conditions: [
            {
                key: 'hideReason',
                label: '숨긴 까닭',
                options: [
                    { value: 'REPORTS', label: '신고가 쌓여 숨김' },
                    { value: 'RIGHTS', label: '권리 침해 신고로 임시 숨김' },
                ],
            },
        ],
    },
    {
        code: 'COMMENT_HIDDEN',
        area: 'safety',
        name: '내 댓글 숨김(신고)',
        description: '신고로 내 댓글이 저절로 숨겨지면 알려요. 계정 알림이라 회원이 끌 수 없어요.',
        params: [['excerpt', '숨겨진 댓글 앞부분', '저도 갈게요!']],
        title: { ko: '신고가 접수되어 내 댓글이 숨겨졌어요', en: 'Your comment was hidden after being reported' },
        body: { ko: '{{excerpt}}', en: '{{excerpt}}' },
        path: '/me/comments',
        conditions: [
            {
                key: 'hideReason',
                label: '숨긴 까닭',
                options: [
                    { value: 'REPORTS', label: '신고가 쌓여 숨김' },
                    { value: 'RIGHTS', label: '권리 침해 신고로 임시 숨김' },
                ],
            },
        ],
    },
];

/** 회원 이벤트 이메일 — 지금은 없다(새 문구, 운영자 알림 메일과 같은 꼴) */
const MEMBER_EMAIL: Content = {
    ko: {
        subject: '{{title}}',
        body: '<p><strong>{{title}}</strong></p><p>{{body}}</p>' + btn('link', '사이트에서 보기') + '<p>{{link}}</p><p>{{siteName}} 알림 설정에 따라 보낸 메일이에요.</p>',
    },
    en: {
        subject: '{{title}}',
        body: '<p><strong>{{title}}</strong></p><p>{{body}}</p>' + btn('link', 'Open on the site') + '<p>{{link}}</p><p>This email was sent according to your {{siteName}} notification settings.</p>',
    },
};

function memberTarget(seed: MemberEventSeed): TargetDef {
    const common = commonVars('MEMBER');
    const params = seed.params.map(([name, description, sample]) => v(name, description, sample));
    const sent = sentVars(UtilNotificationMessage.fillSamples(seed.title.ko, params), UtilNotificationMessage.fillSamples(seed.body.ko, params), `https://roboyam.example.com${seed.path}`, '공개 사이트에서 볼 주소');
    return {
        type: 'EVENT',
        code: seed.code,
        audience: 'MEMBER',
        area: seed.area,
        severity: null,
        name: seed.name,
        description: seed.description,
        security: false,
        channels: [
            { channel: 'IN_APP', variables: [...common, ...params], conditions: seed.conditions, defaults: { ko: { subject: seed.title.ko, body: seed.body.ko }, en: { subject: seed.title.en, body: seed.body.en } } },
            { channel: 'EMAIL', variables: [...common, ...params, ...sent], conditions: seed.conditions, defaults: MEMBER_EMAIL },
            kakaoChannel([...common, ...params, ...sent]),
        ],
    };
}

/** 회원 이벤트 정의(이름·설명·영역) — 회원 이벤트 목록 목업(`member-event-mock.ts`)이 같은 이름을 쓴다 */
export function mockMemberEventSeeds(): { code: string; area: string; name: string; description: string }[] {
    return MEMBER_EVENTS.map(({ code, area, name, description }) => ({ code, area, name, description }));
}

function requiredMail(code: string, audience: MessageAudience, name: string, description: string, security: boolean, variables: MessageVariableView[], conditions: MessageConditionView[], defaults: Content): TargetDef {
    return { type: 'REQUIRED_MAIL', code, audience, area: null, severity: null, name, description, security, channels: [{ channel: 'EMAIL', variables: [...commonVars(audience), ...variables], conditions, defaults }] };
}

const REQUIRED_MAILS: TargetDef[] = [
    requiredMail(
        'MEMBER_EMAIL_VERIFY',
        'MEMBER',
        '회원 이메일 인증',
        '회원이 가입하거나 이메일을 바꿀 때 인증 링크를 보내요.',
        true,
        [v('nickname', '회원 닉네임', '로보얌이'), v('verifyLink', '이메일 인증 링크', 'https://roboyam.example.com/verify-email?token=sample', { required: true, link: true }), v('validHours', '링크를 쓸 수 있는 시간', '24')],
        [
            {
                key: 'signupMethod',
                label: '가입 방식',
                options: [
                    { value: 'EMAIL', label: '이메일 가입' },
                    { value: 'SOCIAL', label: '소셜 가입(미확인 이메일)' },
                ],
            },
        ],
        {
            ko: {
                subject: '이메일 인증을 완료해 주세요',
                body:
                    '<p><strong>이메일 인증</strong></p><p>{{nickname}}님, {{siteName}}에 가입해 주셔서 고마워요.</p><p>아래 버튼을 눌러 이메일 인증을 완료해 주세요. 인증하기 전에는 글·댓글·추천·쪽지를 쓸 수 없어요.</p>' +
                    btn('verifyLink', '이메일 인증하기') +
                    '<p>이 링크는 {{validHours}}시간 동안 쓸 수 있어요. 버튼이 안 눌리면 아래 주소를 브라우저에 붙여 넣어 주세요.</p><p>{{verifyLink}}</p><p>본인이 가입한 게 아니라면 이 메일을 무시해 주세요.</p>',
            },
            en: {
                subject: 'Complete your email verification',
                body:
                    '<p><strong>Email verification</strong></p><p>Hi {{nickname}}, thanks for joining {{siteName}}.</p><p>Click the button below to verify your email. Until you verify, you cannot post, comment, like, or send messages.</p>' +
                    btn('verifyLink', 'Verify email') +
                    '<p>This link is valid for {{validHours}} hours. If the button does not work, paste the address below into your browser.</p><p>{{verifyLink}}</p><p>If you did not sign up, you can ignore this email.</p>',
            },
        },
    ),
    requiredMail(
        'OPERATOR_INVITE',
        'OPERATOR',
        '운영자 초대',
        '관리자 앱 운영자로 초대할 때 수락 링크를 보내요.',
        true,
        [v('inviteLink', '초대 수락 링크', 'https://admin.roboyam.example.com/invitation?token=sample', { required: true, link: true }), v('validHours', '링크를 쓸 수 있는 시간', '72')],
        [
            {
                key: 'inviteRole',
                label: '초대받는 역할',
                options: [
                    { value: 'ROLE_SITE_ADMIN', label: '사이트 관리자' },
                    { value: 'ROLE_BOARD_ADMIN', label: '게시판 관리자' },
                    { value: 'ROLE_MEMBER_ADMIN', label: '회원 관리자' },
                ],
            },
        ],
        {
            ko: {
                subject: '관리자 앱 운영자로 초대됐어요',
                body:
                    '<p><strong>운영자 초대</strong></p><p>{{siteName}}의 관리자 앱 운영자로 초대됐어요.</p><p>아래 버튼을 눌러 이름과 비밀번호를 정하면 계정이 만들어져요.</p>' +
                    btn('inviteLink', '초대 수락하기') +
                    '<p>이 링크는 {{validHours}}시간 동안 쓸 수 있어요. 버튼이 안 눌리면 아래 주소를 브라우저에 붙여 넣어 주세요.</p><p>{{inviteLink}}</p><p>초대받을 이유가 없다면 이 메일을 무시해 주세요.</p>',
            },
            en: {
                subject: 'You have been invited as an admin app operator',
                body:
                    '<p><strong>Operator invitation</strong></p><p>You have been invited as an operator of the {{siteName}} admin app.</p><p>Click the button below and choose your name and password to create your account.</p>' +
                    btn('inviteLink', 'Accept invitation') +
                    '<p>This link is valid for {{validHours}} hours. If the button does not work, paste the address below into your browser.</p><p>{{inviteLink}}</p><p>If you have no reason to be invited, you can ignore this email.</p>',
            },
        },
    ),
    requiredMail(
        'OPERATOR_CREDENTIALS',
        'OPERATOR',
        '운영자 임시 비밀번호',
        '운영자를 직접 추가할 때 임시 비밀번호를 보내요.',
        true,
        [v('operatorName', '운영자 이름', '박운영'), v('temporaryPassword', '임시 비밀번호', 'Tmp-8f3K2q', { required: true })],
        [],
        {
            ko: {
                subject: '관리자 앱 운영자 계정이 만들어졌어요',
                body: '<p><strong>운영자 계정 안내</strong></p><p>{{operatorName}}님, {{siteName}}의 관리자 앱 운영자 계정이 만들어졌어요.</p><p>이 메일을 받은 주소와 아래 임시 비밀번호로 로그인해 주세요.</p><p>임시 비밀번호: <strong>{{temporaryPassword}}</strong></p><p>관리자 앱 주소: {{adminUrl}}</p><p>이 메일을 다른 사람에게 전달하지 마세요.</p>',
            },
            en: {
                subject: 'Your admin app operator account has been created',
                body: '<p><strong>Operator account</strong></p><p>Hi {{operatorName}}, an operator account for the {{siteName}} admin app has been created.</p><p>Log in with the address that received this email and the temporary password below.</p><p>Temporary password: <strong>{{temporaryPassword}}</strong></p><p>Admin app address: {{adminUrl}}</p><p>Do not forward this email to anyone.</p>',
            },
        },
    ),
    requiredMail(
        'OPERATOR_RESET_CODE',
        'OPERATOR',
        '운영자 비밀번호 재설정 승인번호',
        '운영자가 비밀번호 재설정을 요청하면 승인번호를 보내요.',
        true,
        [v('code', '승인번호', '482913', { required: true }), v('validMinutes', '승인번호를 쓸 수 있는 시간(분)', '10')],
        [],
        {
            ko: {
                subject: '관리자 앱 비밀번호 재설정 승인번호',
                body: '<p><strong>비밀번호 재설정 승인번호</strong></p><p>{{siteName}} 관리자 앱의 비밀번호 재설정을 요청하셨어요.</p><p>아래 승인번호를 비밀번호 재설정 화면에 입력해 새 비밀번호를 정해 주세요.</p><p>승인번호: <strong>{{code}}</strong></p><p>이 승인번호는 {{validMinutes}}분 동안 쓸 수 있고, 다시 받으면 이전 번호는 쓸 수 없어요.</p><p>승인번호를 다른 사람에게 알려 주지 마세요.</p><p>비밀번호 재설정을 요청하지 않았다면 이 메일을 무시해 주세요. 비밀번호는 바뀌지 않아요.</p>',
            },
            en: {
                subject: 'Admin app password reset verification code',
                body: '<p><strong>Password reset verification code</strong></p><p>A password reset was requested for the {{siteName}} admin app.</p><p>Enter the verification code below on the password reset page to choose a new password.</p><p>Verification code: <strong>{{code}}</strong></p><p>This code is valid for {{validMinutes}} minutes. If you request a new one, the previous code stops working.</p><p>Do not share this code with anyone.</p><p>If you did not request a password reset, you can ignore this email. Your password has not changed.</p>',
            },
        },
    ),
    requiredMail('MAIL_TEST', 'OPERATOR', '메일 서버 시험', '메일 서버 설정의 「시험 발송」이 보내요.', false, [], [], {
        ko: { subject: '메일 서버 설정 시험 메일', body: '<p>이 메일이 보이면 관리자 앱의 메일 서버 설정으로 메일이 잘 나가고 있는 거예요.</p>' },
        en: { subject: 'Mail server settings test', body: '<p>If you can see this email, mail is being sent correctly with the mail server settings of the admin app.</p>' },
    }),
];

/** 모든 대상 — 회원 이벤트 · 운영자 이벤트 · 필수 안내 메일 차례 */
const TARGETS: TargetDef[] = [...MEMBER_EVENTS.map(memberTarget), ...OPERATOR_EVENTS.map(operatorTarget), ...REQUIRED_MAILS];

/** 목업의 「나」 — 저장·이력의 운영자 이름 */
const ME = '김관리';

const SITE_NAME = '로보얌';
/** 로고는 사이트 설정 「일반」의 것 — 목업은 로고가 없는 사이트로 둔다 */
const LOGO_URL: string | null = null;
const CONTENT_VAR = v('content', '문구의 본문이 들어갈 자리(지울 수 없어요)', '', { required: true });
const AUDIENCE_NAMES: Record<MailTemplateAudience, string> = { MEMBER: '회원', OPERATOR: '운영자', ANY: '모두' };
const CHANNEL_NAMES: Record<MessageChannel, string> = { IN_APP: '앱 알림', EMAIL: '이메일', SLACK: 'Slack', MATTERMOST: 'Mattermost', KAKAO: '카카오톡' };

/* ── 메일 템플릿 정의(개정 3 의 메일 레이아웃) ── */

/** 받는 사람별 기본 메일 템플릿(builtin) — 새 이메일 문구가 이것으로 시작한다 */
const DEFAULT_MAIL_TEMPLATE_ID: Record<MessageAudience, string> = { MEMBER: 'mail-member', OPERATOR: 'mail-operator' };
const MAIL_COLOR_NAMES: Record<MailTemplateColor, string> = { BLUE: '파랑', INDIGO: '남색', TEAL: '청록', GREEN: '초록', AMBER: '호박', ORANGE: '주황', RED: '빨강', SLATE: '먹색' };

const mailTemplateVars = (audience: MailTemplateAudience): MessageVariableView[] => [...commonVars(audience), CONTENT_VAR];
const commonNames = (audience: MailTemplateAudience): string[] => commonVars(audience).map((item) => item.name);

interface MailTemplateRow {
    id: string;
    name: string;
    builtin: boolean;
    audience: MailTemplateAudience;
    showLogo: boolean;
    showSiteName: boolean;
    headerColor: MailTemplateColor | null;
    footer: Record<MessageLocale, string>;
    frames: Record<MessageLocale, string>;
    updatedAt: string | null;
    updatedByName: string | null;
}

const PLAIN_FRAMES: Record<MessageLocale, string> = { ko: '<p>{{content}}</p>', en: '<p>{{content}}</p>' };

function initialMailTemplates(): MailTemplateRow[] {
    return [
        {
            id: DEFAULT_MAIL_TEMPLATE_ID.MEMBER,
            name: '회원 기본',
            builtin: true,
            audience: 'MEMBER',
            showLogo: true,
            showSiteName: true,
            headerColor: null,
            footer: { ko: '이 메일은 {{siteName}}에서 보낸 발신 전용 메일이에요. 회신하지 마세요.\n{{siteUrl}}', en: 'This is a send-only email from {{siteName}}. Please do not reply.\n{{siteUrl}}' },
            frames: { ...PLAIN_FRAMES },
            updatedAt: null,
            updatedByName: null,
        },
        {
            id: DEFAULT_MAIL_TEMPLATE_ID.OPERATOR,
            name: '운영자 기본',
            builtin: true,
            audience: 'OPERATOR',
            showLogo: true,
            showSiteName: true,
            headerColor: null,
            footer: {
                ko: '{{siteName}} 관리 사이트의 운영자에게 보내는 메일이에요. 발신 전용이라 회신하지 마세요.\n관리 사이트: {{adminUrl}}',
                en: 'This email is for operators of the {{siteName}} admin site. It is send-only, so please do not reply.\nAdmin site: {{adminUrl}}',
            },
            frames: { ...PLAIN_FRAMES },
            updatedAt: null,
            updatedByName: null,
        },
        {
            id: 'mail-security',
            name: '보안 안내',
            builtin: false,
            audience: 'ANY',
            showLogo: true,
            showSiteName: true,
            headerColor: 'RED',
            footer: {
                ko: '본인이 요청하지 않았다면 이 메일을 무시하고 관리자에게 알려 주세요.\n{{siteName}} 은 메일로 비밀번호를 묻지 않아요.',
                en: 'If you did not request this, ignore this email and tell an administrator.\n{{siteName}} never asks for your password by email.',
            },
            frames: { ...PLAIN_FRAMES },
            updatedAt: '2026-10-18T02:10:00Z',
            updatedByName: ME,
        },
        {
            id: 'mail-notice',
            name: '공지',
            builtin: false,
            audience: 'MEMBER',
            showLogo: true,
            showSiteName: true,
            headerColor: 'BLUE',
            footer: { ko: '{{siteName}} 운영팀 드림 · {{siteUrl}}', en: 'From the {{siteName}} team · {{siteUrl}}' },
            frames: { ko: '<p>{{recipientName}}님, 안녕하세요.</p><p>{{content}}</p><p>고마워요.</p>', en: '<p>Hello {{recipientName}},</p><p>{{content}}</p><p>Thank you.</p>' },
            updatedAt: '2026-10-19T07:45:00Z',
            updatedByName: '박운영',
        },
        {
            id: 'mail-simple',
            name: '간단',
            builtin: false,
            audience: 'OPERATOR',
            showLogo: false,
            showSiteName: false,
            headerColor: null,
            footer: { ko: '', en: '' },
            frames: { ...PLAIN_FRAMES },
            updatedAt: '2026-10-20T00:30:00Z',
            updatedByName: ME,
        },
    ];
}

/* ── Slack · Mattermost 템플릿 정의 ── */

/** 종류마다 기본 템플릿(builtin) — 새 Slack·Mattermost 문구가 이것으로 시작한다 */
const DEFAULT_CHAT_TEMPLATE_ID: Record<ChatTemplateType, string> = { SLACK: 'slack-default', MATTERMOST: 'mattermost-default' };
const CHAT_COLOR_NAMES: Record<ChatTemplateColor, string> = { GREEN: '초록', BLUE: '파랑', TEAL: '청록', PURPLE: '보라', AMBER: '호박', ORANGE: '주황', RED: '빨강', GRAY: '회색' };
/** 틀에서 쓸 수 있는 변수 — 본문 자리 + 운영자 공통 변수(Slack·Mattermost 는 운영자 이벤트만 보낸다) */
const CHAT_TEMPLATE_VARS: MessageVariableView[] = [CONTENT_VAR, ...commonVars('OPERATOR')];

interface ChatTemplateRow extends ChatTemplateSaveRequest {
    id: string;
    type: ChatTemplateType;
    builtin: boolean;
    updatedAt: string | null;
    updatedByName: string | null;
}

const DEFAULT_CHAT: ChatTemplateSaveRequest = {
    name: '기본 템플릿',
    showSeverityIcon: true,
    boldTitle: true,
    colors: { INFO: 'BLUE', WARNING: 'AMBER', CRITICAL: 'RED' },
    showSiteName: true,
    showSentAt: true,
    showOpenButton: true,
    openLabel: { ko: '열기', en: 'Open' },
    frames: { ko: '{{content}}', en: '{{content}}' },
};

function initialChatTemplates(): ChatTemplateRow[] {
    return [
        { ...clone(DEFAULT_CHAT), id: DEFAULT_CHAT_TEMPLATE_ID.SLACK, type: 'SLACK', builtin: true, updatedAt: null, updatedByName: null },
        {
            ...clone(DEFAULT_CHAT),
            id: 'slack-compact',
            type: 'SLACK',
            name: '짧은 카드',
            showSeverityIcon: false,
            showSiteName: false,
            showOpenButton: false,
            colors: { INFO: 'GRAY', WARNING: 'ORANGE', CRITICAL: 'RED' },
            builtin: false,
            updatedAt: '2026-10-21T03:00:00Z',
            updatedByName: '박운영',
        },
        { ...clone(DEFAULT_CHAT), id: DEFAULT_CHAT_TEMPLATE_ID.MATTERMOST, type: 'MATTERMOST', builtin: true, updatedAt: null, updatedByName: null },
        {
            ...clone(DEFAULT_CHAT),
            id: 'mattermost-ops',
            type: 'MATTERMOST',
            name: '운영 채널(눈에 띄게)',
            colors: { INFO: 'TEAL', WARNING: 'ORANGE', CRITICAL: 'PURPLE' },
            openLabel: { ko: '관리자 앱에서 보기', en: 'View in admin app' },
            frames: { ko: '{{content}}\n\n*{{siteName}} 운영 알림*', en: '{{content}}\n\n*{{siteName}} ops alert*' },
            builtin: false,
            updatedAt: '2026-10-22T09:15:00Z',
            updatedByName: ME,
        },
    ];
}

/* ── 카카오톡 템플릿(준비 중 — 예시 하나, 읽기 전용) ── */

const KAKAO_TEMPLATES: Omit<KakaoTemplateView, 'messageCount' | 'messageCounts'>[] = [
    {
        id: 'kakao-comment',
        name: '댓글 알림톡(예시)',
        templateCode: 'RBY_COMMENT_01',
        status: 'APPROVED',
        emphasis: '새 댓글',
        approvedBody: '[#{사이트}] 내 글에 새 댓글이 달렸어요.\n\n#{닉네임}님: #{댓글}\n글: #{글제목}\n\n아래 버튼으로 확인해 주세요.',
        mappings: [
            { placeholder: '사이트', variable: 'siteName', sample: '로보얌' },
            { placeholder: '닉네임', variable: 'actorName', sample: '로보얌이' },
            { placeholder: '댓글', variable: 'excerpt', sample: '저도 갈게요! 몇 시에 모이나요?' },
            { placeholder: '글제목', variable: 'postTitle', sample: '주말 등산 같이 가실 분' },
        ],
        buttons: [{ name: '글 보기', linkVariable: 'link' }],
        channelName: '로보얌',
        updatedAt: '2026-10-22T13:00:00Z',
        updatedByName: ME,
    },
];

/* ── 상태(메모리) ── */

interface ChannelState {
    selected: Record<MessageLocale, string>;
    rules: MessageRuleView[];
}

interface HistoryRow extends MessageHistoryItemView {
    targetId: string;
}

interface Store {
    messages: MessageView[];
    /** 열쇠 `대상 코드|채널` */
    channels: Map<string, ChannelState>;
    mailTemplates: MailTemplateRow[];
    chatTemplates: ChatTemplateRow[];
    history: HistoryRow[];
}

let store: Store | null = null;
let nextId = 200;

const keyOf = (code: string, channel: MessageChannel) => `${code}|${channel}`;
const builtinId = (code: string, channel: MessageChannel, locale: MessageLocale) => `builtin-${code}-${channel}-${locale}`;
const refOf = (def: TargetDef) => ({ type: def.type, code: def.code, audience: def.audience });

/** 채널마다 기본 문구가 쓰는 템플릿 — 이메일은 받는 사람의 기본 메일 템플릿, Slack·Mattermost 는 그 종류의 기본 템플릿 */
function defaultTemplateId(channel: MessageChannel, audience: MessageAudience): string | null {
    if (channel === 'EMAIL') return DEFAULT_MAIL_TEMPLATE_ID[audience];
    if (channel === 'SLACK' || channel === 'MATTERMOST') return DEFAULT_CHAT_TEMPLATE_ID[channel];
    return null;
}

function builtins(): MessageView[] {
    return TARGETS.flatMap((def) =>
        def.channels.flatMap((ch) => {
            const defaults = ch.defaults;
            if (!defaults) return [];
            return MESSAGE_LOCALES.map((locale) => ({
                id: builtinId(def.code, ch.channel, locale),
                target: refOf(def),
                channel: ch.channel,
                // 이름은 서버가 요청 언어로 준다(목업은 한국어)
                name: '기본 문구',
                locale,
                templateId: defaultTemplateId(ch.channel, def.audience),
                subject: defaults[locale].subject,
                body: defaults[locale].body,
                builtin: true,
                updatedAt: null,
                updatedByName: null,
            }));
        }),
    );
}

function userMessage(id: string, code: string, channel: MessageChannel, name: string, locale: MessageLocale, templateId: string | null, subject: string, body: string, updatedAt: string, by: string): MessageView {
    return { id, target: refOf(targetOf('', code)), channel, name, locale, templateId, subject, body, builtin: false, updatedAt, updatedByName: by };
}

function state(): Store {
    if (store) return store;
    const verify = (REQUIRED_MAILS[0] as TargetDef).channels[0] as ChannelDef;
    const user: MessageView[] = [
        userMessage(
            'msg-101',
            'MEMBER_EMAIL_VERIFY',
            'EMAIL',
            '소셜 가입 회원용',
            'ko',
            'mail-security',
            '받은 이메일이 맞는지 확인해 주세요',
            (verify.defaults as Content).ko.body.replace('{{nickname}}님, {{siteName}}에 가입해 주셔서 고마워요.', '{{nickname}}님, 소셜 계정으로 {{siteName}}에 가입하셨네요. 소셜 계정의 이메일이 맞는지 한 번만 확인해 주세요.'),
            '2026-10-20T05:12:00Z',
            ME,
        ),
        userMessage(
            'msg-102',
            'BATCH_RUN_FAILED',
            'EMAIL',
            '긴급 배치 실패',
            'ko',
            'mail-security',
            '[긴급] {{title}}',
            '<p><strong>[긴급] {{title}}</strong></p><p>{{body}}</p><p>{{occurredAt}} 에 일어났어요. 바로 확인해 주세요.</p>' + btn('link', '배치 로그 보기') + '<p>{{link}}</p>',
            '2026-10-21T01:40:00Z',
            '박운영',
        ),
        userMessage(
            'msg-103',
            'OPERATOR_INVITE',
            'EMAIL',
            '초대 메일(간결)',
            'ko',
            'mail-simple',
            '{{siteName}} 관리자 앱 초대',
            '<p>{{siteName}} 관리자 앱 운영자로 초대됐어요. {{validHours}}시간 안에 수락해 주세요.</p>' + btn('inviteLink', '초대 수락하기') + '<p>{{inviteLink}}</p>',
            '2026-10-22T08:05:00Z',
            ME,
        ),
        userMessage('msg-104', 'MAIL_TEST', 'EMAIL', 'Plain test', 'en', DEFAULT_MAIL_TEMPLATE_ID.OPERATOR, 'Test mail from {{siteName}}', '<p>This is a test mail from {{siteName}}.</p>', '2026-10-22T10:30:00Z', ME),
        // 개정 3 의 채팅 문구 「운영 채널용(짧게)」를 Slack · Mattermost 로 나눴다(표기법이 다르다)
        userMessage('msg-105', 'BATCH_RUN_FAILED', 'SLACK', '운영 채널용(짧게)', 'ko', 'slack-compact', '{{jobName}} 실패', '{{reason}}\n<{{link}}|배치 로그>', '2026-10-22T11:10:00Z', '박운영'),
        userMessage('msg-108', 'BATCH_RUN_FAILED', 'MATTERMOST', '운영 채널용(짧게)', 'ko', 'mattermost-ops', '{{jobName}} 실패', '{{reason}}\n[배치 로그]({{link}})', '2026-10-22T11:12:00Z', '박운영'),
        userMessage('msg-106', 'COMMENT', 'IN_APP', '묶인 댓글', 'ko', null, '{{actorName}}님 외 {{otherCount}}개의 댓글이 내 글에 달렸어요', '{{postTitle}}', '2026-10-22T12:00:00Z', ME),
        userMessage('msg-107', 'POST_HIDDEN', 'IN_APP', '권리 침해 임시 숨김', 'ko', null, '권리 침해 신고로 내 글이 임시로 숨겨졌어요', '{{postTitle}} — 이의가 있으면 고객센터로 알려 주세요.', '2026-10-22T12:20:00Z', ME),
        // 카카오톡(준비 중) 예시 — 알림톡은 승인된 본문 그대로 가서 제목·본문이 없다
        userMessage('msg-109', 'COMMENT', 'KAKAO', '댓글 알림톡', 'ko', 'kakao-comment', '', '', '2026-10-22T13:05:00Z', ME),
    ];
    const channels = new Map<string, ChannelState>();
    for (const def of TARGETS) {
        for (const ch of def.channels) {
            const selected = ch.defaults ? { ko: builtinId(def.code, ch.channel, 'ko'), en: builtinId(def.code, ch.channel, 'en') } : { ko: '', en: '' };
            channels.set(keyOf(def.code, ch.channel), { selected, rules: [] });
        }
    }
    const at = (code: string, channel: MessageChannel) => channels.get(keyOf(code, channel)) as ChannelState;
    at('MEMBER_EMAIL_VERIFY', 'EMAIL').rules = [{ id: 'rule-1', condition: { key: 'signupMethod', value: 'SOCIAL' }, messageId: 'msg-101' }];
    at('BATCH_RUN_FAILED', 'EMAIL').rules = [{ id: 'rule-2', condition: { key: 'jobName', value: 'grade-promotion' }, messageId: 'msg-102' }];
    at('OPERATOR_INVITE', 'EMAIL').selected.ko = 'msg-103';
    at('BATCH_RUN_FAILED', 'SLACK').selected.ko = 'msg-105';
    at('BATCH_RUN_FAILED', 'MATTERMOST').selected.ko = 'msg-108';
    at('COMMENT', 'IN_APP').rules = [{ id: 'rule-3', condition: { key: 'grouped', value: 'GROUPED' }, messageId: 'msg-106' }];
    at('POST_HIDDEN', 'IN_APP').rules = [{ id: 'rule-4', condition: { key: 'hideReason', value: 'RIGHTS' }, messageId: 'msg-107' }];
    at('COMMENT', 'KAKAO').selected.ko = 'msg-109';

    const history: HistoryRow[] = [
        historyRow('msg-101', 'UPDATE', '2026-10-20T05:12:00Z', ME, [change('subject', '이메일 인증을 완료해 주세요', '받은 이메일이 맞는지 확인해 주세요'), change('templateId', '회원 기본', '보안 안내')]),
        historyRow('msg-101', 'CREATE', '2026-10-19T23:58:00Z', ME, [change('name', null, '소셜 가입 회원용'), change('locale', null, 'ko'), change('templateId', null, '회원 기본')]),
        historyRow('msg-102', 'CREATE', '2026-10-21T01:40:00Z', '박운영', [change('name', null, '긴급 배치 실패'), change('locale', null, 'ko'), change('templateId', null, '보안 안내')]),
        historyRow('msg-103', 'CREATE', '2026-10-22T08:05:00Z', ME, [change('name', null, '초대 메일(간결)'), change('locale', null, 'ko'), change('templateId', null, '간단')]),
        historyRow('msg-104', 'CREATE', '2026-10-22T10:30:00Z', ME, [change('name', null, 'Plain test'), change('locale', null, 'en'), change('templateId', null, '운영자 기본')]),
        historyRow('msg-105', 'CREATE', '2026-10-22T11:10:00Z', '박운영', [change('name', null, '운영 채널용(짧게)'), change('locale', null, 'ko'), change('templateId', null, '짧은 카드')]),
        historyRow('msg-108', 'CREATE', '2026-10-22T11:12:00Z', '박운영', [change('name', null, '운영 채널용(짧게)'), change('locale', null, 'ko'), change('templateId', null, '운영 채널(눈에 띄게)')]),
        historyRow('msg-106', 'CREATE', '2026-10-22T12:00:00Z', ME, [change('name', null, '묶인 댓글'), change('locale', null, 'ko')]),
        historyRow('msg-107', 'CREATE', '2026-10-22T12:20:00Z', ME, [change('name', null, '권리 침해 임시 숨김'), change('locale', null, 'ko')]),
        historyRow('msg-109', 'CREATE', '2026-10-22T13:05:00Z', ME, [change('name', null, '댓글 알림톡'), change('locale', null, 'ko'), change('templateId', null, '댓글 알림톡(예시)')]),
        historyRow('mail-security', 'UPDATE', '2026-10-18T02:10:00Z', ME, [change('headerColor', '주황', '빨강'), change('audience', '회원', '모두')]),
        historyRow('mail-security', 'CREATE', '2026-10-17T09:00:00Z', ME, [change('name', null, '보안 안내')]),
        historyRow('mail-notice', 'CREATE', '2026-10-19T07:45:00Z', '박운영', [change('name', null, '공지')]),
        historyRow('mail-simple', 'CREATE', '2026-10-20T00:30:00Z', ME, [change('name', null, '간단')]),
        historyRow('slack-compact', 'UPDATE', '2026-10-21T03:00:00Z', '박운영', [change('showOpenButton', 'true', 'false'), change('colorInfo', '파랑', '회색')]),
        historyRow('slack-compact', 'CREATE', '2026-10-21T02:40:00Z', '박운영', [change('name', null, '짧은 카드')]),
        historyRow('mattermost-ops', 'CREATE', '2026-10-22T09:15:00Z', ME, [change('name', null, '운영 채널(눈에 띄게)')]),
    ];

    store = { messages: [...builtins(), ...user], channels, mailTemplates: initialMailTemplates(), chatTemplates: initialChatTemplates(), history };
    return store;
}

function change(field: string, before: string | null, after: string | null): SettingHistoryChange {
    return { field, labelKey: `settingHistory.field.${field}`, before, after };
}

function historyRow(targetId: string, action: 'CREATE' | 'UPDATE', changedAt: string, operatorName: string, changes: SettingHistoryChange[]): HistoryRow {
    nextId += 1;
    return { id: `h-${nextId}`, targetId, action, source: 'ADMIN', operatorId: '1001', operatorName, operatorDeleted: false, changedAt, changes };
}

/* ── 공통 도우미 ── */

const messageNotFound = () => new ApiError({ status: 404, code: 'MESSAGE_NOT_FOUND', message: '문구를 찾을 수 없어요.' });
const targetNotFound = () => new ApiError({ status: 404, code: 'MESSAGE_TARGET_NOT_FOUND', message: '이벤트나 필수 안내 메일을 찾을 수 없어요.' });
const templateNotFound = () => new ApiError({ status: 404, code: 'TEMPLATE_NOT_FOUND', message: '템플릿을 찾을 수 없어요.' });
const preparing = (channel: MessageChannel) => new ApiError({ status: 409, code: 'CHANNEL_PREPARING', message: `${CHANNEL_NAMES[channel]}은(는) 알림 도구에서 준비 중이라 아직 만들거나 고칠 수 없어요.` });
const invalid = (fields: Record<string, string>) => new ApiError({ status: 400, code: 'VALIDATION', message: '입력값을 확인해 주세요.', fields });

/** 대상 — `type` 이 비면 코드만으로 찾는다(목업 안에서만 — 코드는 겹치지 않는다) */
function targetOf(type: MessageTargetType | '', code: string): TargetDef {
    const def = TARGETS.find((item) => item.code === code && (!type || item.type === type));
    if (!def) throw targetNotFound();
    return def;
}

function channelDefOf(def: TargetDef, channel: MessageChannel): ChannelDef | undefined {
    return def.channels.find((item) => item.channel === channel);
}

function messageOf(id: string): MessageView {
    const found = state().messages.find((item) => item.id === id);
    if (!found) throw messageNotFound();
    return found;
}

function mailTemplateOf(id: string): MailTemplateRow {
    const found = state().mailTemplates.find((item) => item.id === id);
    if (!found) throw templateNotFound();
    return found;
}

function chatTemplateOf(type: ChatTemplateType, id: string): ChatTemplateRow {
    const found = state().chatTemplates.find((item) => item.id === id && item.type === type);
    if (!found) throw templateNotFound();
    return found;
}

/** 채널의 템플릿 이름(이력·목록) — 없으면 null */
function templateNameOf(channel: MessageChannel, id: string | null): string | null {
    if (!id) return null;
    if (channel === 'EMAIL') return state().mailTemplates.find((item) => item.id === id)?.name ?? null;
    if (channel === 'SLACK' || channel === 'MATTERMOST') return state().chatTemplates.find((item) => item.id === id)?.name ?? null;
    if (channel === 'KAKAO') return KAKAO_TEMPLATES.find((item) => item.id === id)?.name ?? null;
    return null;
}

async function read(can: Can): Promise<void> {
    await mockDelay();
    if (!can('template.read')) throw MOCK_ERRORS.denied();
}

async function write(can: Can): Promise<void> {
    await mockDelay();
    if (!can('template.manage')) throw MOCK_ERRORS.denied();
    if (mockFlags().has('template-save-error')) throw MOCK_ERRORS.internal();
}

async function readList(can: Can): Promise<void> {
    await read(can);
    if (mockFlags().has('template-error')) throw MOCK_ERRORS.internal();
}

async function readDetail(can: Can): Promise<void> {
    await read(can);
    if (mockFlags().has('template-detail-error')) throw MOCK_ERRORS.internal();
}

function clone<T>(value: T): T {
    return JSON.parse(JSON.stringify(value)) as T;
}

/** 이 문구를 쓰는 곳 — 언어별 쓸 문구 또는 규칙 */
function usageOf(message: MessageView): { defaultFor: MessageLocale[]; ruleCount: number } {
    const ch = state().channels.get(keyOf(message.target.code, message.channel));
    if (!ch) return { defaultFor: [], ruleCount: 0 };
    return { defaultFor: MESSAGE_LOCALES.filter((locale) => ch.selected[locale] === message.id), ruleCount: ch.rules.filter((rule) => rule.messageId === message.id).length };
}

function inUse(message: MessageView): boolean {
    const usage = usageOf(message);
    return usage.defaultFor.length > 0 || usage.ruleCount > 0;
}

/** 템플릿을 쓰는 문구(채널 · 템플릿 id) */
const usingMessages = (channel: MessageChannel, templateId: string) => state().messages.filter((item) => item.channel === channel && item.templateId === templateId);
const usingCounts = (channel: MessageChannel, templateId: string): Record<MessageAudience, number> => {
    const using = usingMessages(channel, templateId);
    return { MEMBER: using.filter((item) => item.target.audience === 'MEMBER').length, OPERATOR: using.filter((item) => item.target.audience === 'OPERATOR').length };
};

/** 검사 문제 → 서버 문구(목업 — 서버가 요청 언어로 줄 글) */
function problemText(field: string, problem: ContentProblem): string {
    const names = (problem.names ?? []).map((name) => `{{${name}}}`).join(', ');
    switch (problem.code) {
        case 'REQUIRED':
            return field === 'name' ? '이름을 입력해 주세요.' : field === 'subject' ? '제목을 입력해 주세요.' : '본문을 입력해 주세요.';
        case 'TOO_LONG':
            return `${problem.max}자까지 입력할 수 있어요.`;
        case 'MULTI_LINE':
            return '제목은 한 줄로 입력해 주세요.';
        case 'UNKNOWN_VARIABLE':
            return `여기서 쓸 수 없는 변수예요: ${names}`;
        case 'AUDIENCE_VARIABLE':
            return `여기서는 쓸 수 없는 변수예요: ${names} — ${problem.audience === 'OPERATOR' ? '운영자' : '회원'}에게 가는 문구·템플릿에서만 쓸 수 있어요.`;
        case 'MISSING_REQUIRED':
            return `보안 메일이라 꼭 넣어야 하는 변수가 빠졌어요: ${names}`;
        case 'BUTTON_MANY':
            return '버튼은 하나만 넣을 수 있어요.';
        case 'BUTTON_LABEL':
            return '버튼 글자를 입력해 주세요.';
        case 'BUTTON_LINK':
            return '버튼 주소는 링크 변수 중에서 골라 주세요.';
        case 'CONTENT_MISSING':
            return '본문 자리 {{content}} 를 지울 수 없어요.';
        case 'CONTENT_MANY':
            return '본문 자리 {{content}} 는 한 번만 넣을 수 있어요.';
    }
    return '입력값을 확인해 주세요.';
}

/** 문구 내용 검사 — 채널마다 다른 규칙(서버도 같은 규칙) */
function validateMessage(body: MessageSaveRequest, def: TargetDef): void {
    const ch = channelDefOf(def, body.channel);
    if (!ch) throw invalid({ channel: `「${def.name}」은(는) ${CHANNEL_NAMES[body.channel]}(으)로 나가지 않아요.` });
    if (PREPARING.includes(body.channel)) throw preparing(body.channel);
    const fields: Record<string, string> = {};
    if (body.channel === 'EMAIL') {
        const problems = UtilMailTemplate.validateMessage({ name: body.name, subject: body.subject, bodyHtml: body.body }, { audience: def.audience, security: def.security, variables: ch.variables });
        for (const [field, problem] of Object.entries(problems) as [MailMessageField, ContentProblem][]) {
            // 버튼 문제는 서버에서는 본문 칸의 문제다
            const key = field === 'button' || field === 'bodyHtml' ? 'body' : field;
            if (!fields[key]) fields[key] = problemText(field === 'bodyHtml' ? 'body' : field, problem);
        }
        const template = state().mailTemplates.find((item) => item.id === body.templateId);
        if (!template) fields.templateId = '메일 템플릿을 골라 주세요.';
        else if (!UtilMailTemplate.templateFits(template.audience, def.audience)) fields.templateId = `${AUDIENCE_NAMES[def.audience]} 메일에는 쓸 수 없는 템플릿이에요. 쓰는 대상이 ${AUDIENCE_NAMES[def.audience]} 또는 모두인 메일 템플릿을 골라 주세요.`;
    } else {
        const rules = { audience: def.audience, variables: ch.variables };
        const problems = UtilNotificationMessage.isChat(body.channel) ? UtilNotificationMessage.validateChat(body, rules) : UtilNotificationMessage.validateInApp(body, rules);
        for (const [field, problem] of Object.entries(problems) as [NotificationMessageField, ContentProblem][]) fields[field] = problemText(field, problem);
        if (UtilNotificationMessage.isChat(body.channel) && !state().chatTemplates.some((item) => item.type === body.channel && item.id === body.templateId)) fields.templateId = `${CHANNEL_NAMES[body.channel]} 템플릿을 골라 주세요.`;
    }
    if (body.locale !== 'ko' && body.locale !== 'en') fields.locale = '언어는 ko · en 중 하나예요.';
    if (Object.keys(fields).length) throw invalid(fields);
}

function selectedView(channel: MessageChannel, id: string): MessageChannelSummaryView['selected']['ko'] {
    if (!id) return null;
    const message = state().messages.find((item) => item.id === id && item.channel === channel);
    return message ? { id: message.id, name: message.name, builtin: message.builtin } : null;
}

function channelSummary(def: TargetDef, ch: ChannelDef): MessageChannelSummaryView {
    const s = state();
    const cs = s.channels.get(keyOf(def.code, ch.channel)) as ChannelState;
    return {
        channel: ch.channel,
        preparing: PREPARING.includes(ch.channel),
        variables: ch.variables,
        conditions: ch.conditions,
        selected: { ko: selectedView(ch.channel, cs.selected.ko), en: selectedView(ch.channel, cs.selected.en) },
        ruleCount: cs.rules.length,
        messageCount: s.messages.filter((item) => item.target.code === def.code && item.channel === ch.channel && !item.builtin).length,
    };
}

function targetView(def: TargetDef): MessageTargetView {
    return { type: def.type, code: def.code, audience: def.audience, area: def.area, severity: def.severity, name: def.name, description: def.description, security: def.security, channels: def.channels.map((ch) => channelSummary(def, ch)) };
}

function detailView(def: TargetDef): MessageTargetDetailView {
    const s = state();
    return clone({
        summary: targetView(def),
        channels: def.channels.map((ch): MessageChannelDetailView => {
            const cs = s.channels.get(keyOf(def.code, ch.channel)) as ChannelState;
            return {
                channel: ch.channel,
                defaults: ch.defaults ? { ko: builtinId(def.code, ch.channel, 'ko'), en: builtinId(def.code, ch.channel, 'en') } : { ko: null, en: null },
                selected: cs.selected,
                rules: cs.rules,
                messages: s.messages.filter((item) => item.target.code === def.code && item.channel === ch.channel),
            };
        }),
    });
}

function listItem(message: MessageView): MessageListItemView {
    const def = targetOf(message.target.type, message.target.code);
    const { body: _body, subject: _subject, ...rest } = message;
    return { ...rest, targetName: def.name, targetArea: def.area, security: def.security, preparing: PREPARING.includes(message.channel), templateName: templateNameOf(message.channel, message.templateId), ...usageOf(message) };
}

/** 이력의 본문 값 — 글자 요약 80자(가정) */
function excerpt(text: string): string {
    const plain = UtilMailTemplate.plainText(text);
    return plain.length > 80 ? `${plain.slice(0, 80)}…` : plain;
}

function pageOf(targetId: string, page: number, size: number): MessageHistoryPage {
    const rows = state().history.filter((row) => row.targetId === targetId);
    const items = rows.slice(page * size, page * size + size).map(({ targetId: _omit, ...rest }) => rest);
    return clone({ items, total: rows.length, page, size });
}

/* ── API — 대상(이벤트 · 필수 안내 메일) ── */

export async function mockTargets(can: Can, query: MessageTargetQuery = {}): Promise<MessageTargetView[]> {
    await readList(can);
    if (mockFlags().has('template-empty')) return [];
    return clone(TARGETS.filter((def) => (!query.type || def.type === query.type) && (!query.audience || def.audience === query.audience)).map(targetView));
}

export async function mockTarget(can: Can, type: MessageTargetType, code: string): Promise<MessageTargetDetailView> {
    await readDetail(can);
    if (mockFlags().has('template-empty')) throw targetNotFound();
    return detailView(targetOf(type, code));
}

export async function mockSaveTarget(can: Can, type: MessageTargetType, code: string, body: MessageTargetSaveRequest): Promise<MessageTargetDetailView> {
    await write(can);
    const def = targetOf(type, code);
    const s = state();
    const fields: Record<string, string> = {};
    for (const [channel, value] of Object.entries(body.channels) as [MessageChannel, NonNullable<MessageTargetSaveRequest['channels'][MessageChannel]>][]) {
        const ch = channelDefOf(def, channel);
        const prefix = `channels.${channel}`;
        if (!ch) {
            fields[prefix] = `「${def.name}」은(는) ${CHANNEL_NAMES[channel]}(으)로 나가지 않아요.`;
            continue;
        }
        if (PREPARING.includes(channel)) {
            fields[prefix] = `${CHANNEL_NAMES[channel]}은(는) 알림 도구에서 준비 중이라 아직 고를 수 없어요.`;
            continue;
        }
        const own = (id: string) => s.messages.find((item) => item.id === id && item.target.code === code && item.channel === channel);
        for (const locale of MESSAGE_LOCALES) {
            const message = own(value.selected[locale]);
            if (!message) fields[`${prefix}.selected.${locale}`] = '이 채널의 문구를 골라 주세요.';
            else if (message.locale !== locale) fields[`${prefix}.selected.${locale}`] = '고른 언어의 문구만 쓸 수 있어요.';
        }
        value.rules.forEach((rule, at) => {
            const condition = ch.conditions.find((item) => item.key === rule.condition.key);
            if (!condition || !condition.options.some((option) => option.value === rule.condition.value)) fields[`${prefix}.rules[${at}].condition`] = '이 채널에서 고를 수 있는 조건이 아니에요.';
            if (!own(rule.messageId)) fields[`${prefix}.rules[${at}].messageId`] = '이 채널의 문구를 골라 주세요.';
        });
        if (value.rules.length > MESSAGE_RULE_MAX) fields[`${prefix}.rules`] = `규칙은 ${MESSAGE_RULE_MAX}개까지 둘 수 있어요.`;
    }
    if (Object.keys(fields).length) throw invalid(fields);
    for (const [channel, value] of Object.entries(body.channels) as [MessageChannel, NonNullable<MessageTargetSaveRequest['channels'][MessageChannel]>][]) {
        const cs = s.channels.get(keyOf(code, channel)) as ChannelState;
        cs.selected = { ...value.selected };
        cs.rules = value.rules.map((rule) => ({ id: rule.id ?? `rule-${(nextId += 1)}`, condition: { ...rule.condition }, messageId: rule.messageId }));
    }
    return detailView(def);
}

/* ── API — 문구 ── */

export async function mockMessages(can: Can): Promise<MessageListItemView[]> {
    await readList(can);
    if (mockFlags().has('template-empty')) return [];
    return clone(state().messages.map(listItem));
}

export async function mockMessage(can: Can, id: string): Promise<MessageView> {
    await readDetail(can);
    return clone(messageOf(id));
}

/** 저장할 칸 — 채널마다 쓰지 않는 칸은 비운다 */
function messageFields(body: MessageSaveRequest): Pick<MessageView, 'name' | 'locale' | 'templateId' | 'subject' | 'body'> {
    const email = body.channel === 'EMAIL';
    return {
        name: body.name.trim(),
        locale: body.locale,
        templateId: body.channel === 'IN_APP' ? null : body.templateId,
        subject: body.channel === 'KAKAO' ? '' : body.subject,
        body: body.channel === 'KAKAO' ? '' : email ? UtilMailTemplate.cleanHrefs(body.body) : body.body,
    };
}

export async function mockCreateMessage(can: Can, body: MessageSaveRequest): Promise<MessageView> {
    await write(can);
    const def = targetOf(body.targetType, body.targetCode);
    const s = state();
    if (s.messages.filter((item) => item.target.code === def.code && item.channel === body.channel && !item.builtin).length >= MESSAGE_LIMIT) {
        throw new ApiError({ status: 409, code: 'MESSAGE_LIMIT', message: `한 대상의 한 채널에 문구는 ${MESSAGE_LIMIT}개까지 만들 수 있어요.` });
    }
    validateMessage(body, def);
    nextId += 1;
    const created: MessageView = { id: `msg-${nextId}`, target: refOf(def), channel: body.channel, ...messageFields(body), builtin: false, updatedAt: new Date().toISOString(), updatedByName: ME };
    s.messages.push(created);
    const changes = [change('name', null, created.name), change('locale', null, created.locale)];
    if (created.templateId) changes.push(change('templateId', null, templateNameOf(created.channel, created.templateId)));
    s.history.unshift(historyRow(created.id, 'CREATE', created.updatedAt as string, ME, changes));
    return clone(created);
}

export async function mockUpdateMessage(can: Can, id: string, body: MessageSaveRequest): Promise<MessageView> {
    await write(can);
    const message = messageOf(id);
    if (message.builtin) throw new ApiError({ status: 409, code: 'MESSAGE_BUILTIN', message: '기본 문구는 고칠 수 없어요. 복사해서 만들어 주세요.' });
    const def = targetOf(message.target.type, message.target.code);
    // 대상·채널은 바꾸지 못한다 — 원래 값으로 본다
    const fixed = { ...body, targetType: message.target.type, targetCode: message.target.code, channel: message.channel };
    validateMessage(fixed, def);
    const next = messageFields(fixed);
    const cs = state().channels.get(keyOf(message.target.code, message.channel)) as ChannelState;
    if (next.locale !== message.locale && cs.selected[message.locale] === message.id) throw invalid({ locale: '이 언어의 쓸 문구로 쓰는 중이라 언어를 바꿀 수 없어요.' });
    const changes: SettingHistoryChange[] = [];
    if (next.name !== message.name) changes.push(change('name', message.name, next.name));
    if (next.locale !== message.locale) changes.push(change('locale', message.locale, next.locale));
    if (next.templateId !== message.templateId) changes.push(change('templateId', templateNameOf(message.channel, message.templateId), templateNameOf(message.channel, next.templateId)));
    if (next.subject !== message.subject) changes.push(change('subject', message.subject, next.subject));
    if (next.body !== message.body) changes.push(change('body', excerpt(message.body), excerpt(next.body)));
    if (changes.length === 0) return clone(message);
    Object.assign(message, next, { updatedAt: new Date().toISOString(), updatedByName: ME });
    state().history.unshift(historyRow(id, 'UPDATE', message.updatedAt as string, ME, changes));
    return clone(message);
}

export async function mockDeleteMessage(can: Can, id: string): Promise<void> {
    await write(can);
    const message = messageOf(id);
    if (message.builtin) throw new ApiError({ status: 409, code: 'MESSAGE_BUILTIN', message: '기본 문구는 지울 수 없어요.' });
    if (PREPARING.includes(message.channel)) throw preparing(message.channel);
    if (inUse(message) || mockFlags().has('message-in-use')) {
        throw new ApiError({ status: 409, code: 'MESSAGE_IN_USE', message: '쓰는 중인 문구라 지울 수 없어요. 이벤트(또는 필수 안내 메일)의 쓸 문구와 특수 상황 규칙에서 먼저 빼 주세요.' });
    }
    const s = state();
    s.messages = s.messages.filter((item) => item.id !== id);
}

export async function mockPreview(can: Can, body: MailPreviewRequest): Promise<MailPreviewView> {
    await read(can);
    const def = body.target ? targetOf(body.target.type, body.target.code) : null;
    const ch = def ? channelDefOf(def, 'EMAIL') : undefined;
    // 템플릿이 없으면 그 받는 사람의 기본 메일 템플릿(대상도 없으면 회원 기본)
    const template = body.template ?? mailTemplateOf(body.templateId ?? DEFAULT_MAIL_TEMPLATE_ID[def?.audience ?? 'MEMBER']);
    // 견본 값 — 대상이 있으면 그 이메일 채널의 변수, 없으면(메일 템플릿 미리보기) 템플릿 쓰는 대상의 공통 변수
    const variables = ch ? ch.variables : commonVars(template.audience);
    return UtilMailTemplate.render({
        locale: body.locale,
        subject: body.subject,
        bodyHtml: UtilMailTemplate.cleanHrefs(body.bodyHtml),
        variables,
        template: { showLogo: template.showLogo, showSiteName: template.showSiteName, headerColor: template.headerColor, footer: template.footer, frames: template.frames },
        siteName: SITE_NAME,
        logoUrl: LOGO_URL,
    });
}

export async function mockTest(can: Can, body: MailMessageTestRequest): Promise<MailMessageTestResultView> {
    await write(can);
    const to = body.to.trim();
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(to)) throw invalid({ to: '받는 이메일 형식이 올바르지 않아요.' });
    const flags = mockFlags();
    if (flags.has('template-test-limit')) throw new ApiError({ status: 429, code: 'MAIL_TEST_RATE_LIMITED', message: '시험 발송이 너무 잦아요.', retryAfterSeconds: 30 });
    const def = targetOf(body.target.type, body.target.code);
    validateMessage({ targetType: def.type, targetCode: def.code, channel: 'EMAIL', name: 'test', locale: body.locale, templateId: body.templateId ?? DEFAULT_MAIL_TEMPLATE_ID[def.audience], subject: body.subject, body: body.bodyHtml }, def);
    if (flags.has('template-test-fail')) return { succeeded: false, reason: 'CONNECT_FAILED', detail: '메일 서버에 접속하지 못했어요. 서버 주소와 포트를 확인해 주세요.' };
    return { succeeded: true, reason: null, detail: null };
}

/** Slack·Mattermost 시험 — 실제로 올리지 않는다(목업). 도구는 화면이 실제 알림 도구 목록에서 고른 id 다 */
export async function mockChatTest(can: Can, body: ChatMessageTestRequest): Promise<ChatMessageTestResultView> {
    await write(can);
    if (!body.channelId) throw invalid({ channelId: '시험할 알림 도구를 골라 주세요.' });
    const flags = mockFlags();
    if (flags.has('template-test-limit')) throw new ApiError({ status: 429, code: 'MAIL_TEST_RATE_LIMITED', message: '시험이 너무 잦아요.', retryAfterSeconds: 30 });
    const def = targetOf(body.target.type, body.target.code);
    validateMessage({ targetType: def.type, targetCode: def.code, channel: body.channel, name: 'test', locale: body.locale, templateId: body.templateId, subject: body.subject, body: body.body }, def);
    if (flags.has('template-test-fail')) return { ok: false, message: '채널에 글을 올리지 못했어요. 봇 토큰과 채널을 확인해 주세요.' };
    return { ok: true, message: '시험 글을 올렸어요. 채널에서 확인해 보세요.' };
}

export async function mockMessageHistory(can: Can, id: string, page: number, size: number): Promise<MessageHistoryPage> {
    await read(can);
    if (mockFlags().has('template-history-error')) throw MOCK_ERRORS.internal();
    messageOf(id);
    return pageOf(id, page, size);
}

/* ── API — 메일 템플릿 ── */

function mailListItem(row: MailTemplateRow): MailTemplateListItemView {
    return { id: row.id, name: row.name, builtin: row.builtin, audience: row.audience, headerColor: row.headerColor, messageCount: usingMessages('EMAIL', row.id).length, messageCounts: usingCounts('EMAIL', row.id), updatedAt: row.updatedAt, updatedByName: row.updatedByName };
}

function mailView(row: MailTemplateRow): MailTemplateView {
    const variables = Object.fromEntries(MAIL_TEMPLATE_AUDIENCES.map((audience) => [audience, mailTemplateVars(audience)])) as Record<MailTemplateAudience, MessageVariableView[]>;
    return clone({ ...row, messageCount: usingMessages('EMAIL', row.id).length, messageCounts: usingCounts('EMAIL', row.id), logoUrl: LOGO_URL, siteName: SITE_NAME, variables });
}

function validateMailTemplate(body: MailTemplateSaveRequest, current: MailTemplateRow | null = null): void {
    const fields: Record<string, string> = {};
    if (!MAIL_TEMPLATE_AUDIENCES.includes(body.audience)) throw invalid({ audience: '쓰는 대상은 회원 · 운영자 · 모두 중 하나예요.' });
    for (const [field, problem] of Object.entries(UtilMailTemplate.validateTemplate(body, commonNames(body.audience))) as [MailTemplateField, ContentProblem][]) {
        fields[field] = field === 'name' && problem.code === 'REQUIRED' ? '이름을 입력해 주세요.' : problemText(field, problem);
    }
    if (body.headerColor !== null && !UtilMailTemplate.HEADER_COLORS.includes(body.headerColor)) fields.headerColor = '팔레트의 색 중에서 골라 주세요.';
    if (current && body.audience !== current.audience && body.audience !== 'ANY') {
        const counts = usingCounts('EMAIL', current.id);
        const other: MessageAudience = body.audience === 'MEMBER' ? 'OPERATOR' : 'MEMBER';
        if (counts[other] > 0) fields.audience = `이 템플릿을 쓰는 ${AUDIENCE_NAMES[other]} 이메일 문구가 ${counts[other]}개 있어 쓰는 대상을 바꿀 수 없어요. 그 문구의 템플릿을 먼저 바꿔 주세요.`;
    }
    if (Object.keys(fields).length) throw invalid(fields);
}

function mailRowOf(body: MailTemplateSaveRequest): Omit<MailTemplateRow, 'id' | 'builtin' | 'updatedAt' | 'updatedByName'> {
    return {
        name: body.name.trim(),
        audience: body.audience,
        showLogo: body.showLogo,
        showSiteName: body.showSiteName,
        headerColor: body.headerColor,
        footer: { ko: body.footer.ko.trim(), en: body.footer.en.trim() },
        frames: { ko: UtilMailTemplate.cleanHrefs(body.frames.ko), en: UtilMailTemplate.cleanHrefs(body.frames.en) },
    };
}

const mailColorName = (color: MailTemplateColor | null) => (color ? MAIL_COLOR_NAMES[color] : '없음');

export async function mockMailTemplates(can: Can, query: MailTemplateAudienceQuery = {}): Promise<MailTemplateListItemView[]> {
    await readList(can);
    return clone(
        state()
            .mailTemplates.filter((row) => !query.audience || row.audience === query.audience)
            .map(mailListItem),
    );
}

export async function mockMailTemplate(can: Can, id: string): Promise<MailTemplateView> {
    await readDetail(can);
    return mailView(mailTemplateOf(id));
}

export async function mockCreateMailTemplate(can: Can, body: MailTemplateSaveRequest): Promise<MailTemplateView> {
    await write(can);
    const s = state();
    if (s.mailTemplates.filter((item) => !item.builtin).length >= MAIL_TEMPLATE_LIMIT) throw new ApiError({ status: 409, code: 'TEMPLATE_LIMIT', message: `메일 템플릿은 ${MAIL_TEMPLATE_LIMIT}개까지 만들 수 있어요.` });
    validateMailTemplate(body);
    nextId += 1;
    const created: MailTemplateRow = { id: `mail-${nextId}`, builtin: false, ...mailRowOf(body), updatedAt: new Date().toISOString(), updatedByName: ME };
    s.mailTemplates.push(created);
    s.history.unshift(historyRow(created.id, 'CREATE', created.updatedAt as string, ME, [change('name', null, created.name), change('audience', null, AUDIENCE_NAMES[created.audience])]));
    return mailView(created);
}

export async function mockUpdateMailTemplate(can: Can, id: string, body: MailTemplateSaveRequest): Promise<MailTemplateView> {
    await write(can);
    const row = mailTemplateOf(id);
    if (row.builtin) throw new ApiError({ status: 409, code: 'TEMPLATE_BUILTIN', message: '기본 템플릿은 고칠 수 없어요. 복사해서 만들어 주세요.' });
    validateMailTemplate(body, row);
    const next = mailRowOf(body);
    const changes: SettingHistoryChange[] = [];
    if (next.name !== row.name) changes.push(change('name', row.name, next.name));
    if (next.audience !== row.audience) changes.push(change('audience', AUDIENCE_NAMES[row.audience], AUDIENCE_NAMES[next.audience]));
    if (next.showLogo !== row.showLogo) changes.push(change('showLogo', String(row.showLogo), String(next.showLogo)));
    if (next.showSiteName !== row.showSiteName) changes.push(change('showSiteName', String(row.showSiteName), String(next.showSiteName)));
    if (next.headerColor !== row.headerColor) changes.push(change('headerColor', mailColorName(row.headerColor), mailColorName(next.headerColor)));
    for (const locale of MESSAGE_LOCALES) {
        const suffix = locale === 'ko' ? 'Ko' : 'En';
        if (next.footer[locale] !== row.footer[locale]) changes.push(change(`footer${suffix}`, row.footer[locale], next.footer[locale]));
        if (next.frames[locale] !== row.frames[locale]) changes.push(change(`frame${suffix}`, excerpt(row.frames[locale]), excerpt(next.frames[locale])));
    }
    if (changes.length === 0) return mailView(row);
    Object.assign(row, next, { updatedAt: new Date().toISOString(), updatedByName: ME });
    state().history.unshift(historyRow(id, 'UPDATE', row.updatedAt as string, ME, changes));
    return mailView(row);
}

export async function mockDeleteMailTemplate(can: Can, id: string): Promise<void> {
    await write(can);
    const row = mailTemplateOf(id);
    if (row.builtin) throw new ApiError({ status: 409, code: 'TEMPLATE_BUILTIN', message: '기본 템플릿은 지울 수 없어요.' });
    const count = usingMessages('EMAIL', id).length;
    if (count > 0 || mockFlags().has('template-in-use')) throw new ApiError({ status: 409, code: 'TEMPLATE_IN_USE', message: `이 템플릿을 쓰는 문구가 ${Math.max(count, 1)}개 있어 지울 수 없어요. 문구의 템플릿을 먼저 바꿔 주세요.` });
    const s = state();
    s.mailTemplates = s.mailTemplates.filter((item) => item.id !== id);
}

export async function mockMailTemplateHistory(can: Can, id: string, page: number, size: number): Promise<MailTemplateHistoryPage> {
    await read(can);
    if (mockFlags().has('template-history-error')) throw MOCK_ERRORS.internal();
    mailTemplateOf(id);
    return pageOf(id, page, size);
}

/* ── API — Slack · Mattermost 템플릿 ── */

function chatListItem(row: ChatTemplateRow): ChatTemplateListItemView {
    return { id: row.id, type: row.type, name: row.name, builtin: row.builtin, colors: { ...row.colors }, messageCount: usingMessages(row.type, row.id).length, updatedAt: row.updatedAt, updatedByName: row.updatedByName };
}

function chatView(row: ChatTemplateRow): ChatTemplateView {
    return clone({ ...row, messageCount: usingMessages(row.type, row.id).length, siteName: SITE_NAME, variables: CHAT_TEMPLATE_VARS });
}

function chatRowOf(body: ChatTemplateSaveRequest): ChatTemplateSaveRequest {
    return {
        name: body.name.trim(),
        showSeverityIcon: body.showSeverityIcon,
        boldTitle: body.boldTitle,
        colors: { ...body.colors },
        showSiteName: body.showSiteName,
        showSentAt: body.showSentAt,
        showOpenButton: body.showOpenButton,
        openLabel: { ko: body.openLabel.ko.trim(), en: body.openLabel.en.trim() },
        frames: { ko: body.frames.ko, en: body.frames.en },
    };
}

function validateChatTemplate(body: ChatTemplateSaveRequest): void {
    const fields: Record<string, string> = {};
    const known = CHAT_TEMPLATE_VARS.map((item) => item.name).filter((name) => name !== 'content');
    for (const [field, problem] of Object.entries(UtilChatTemplate.validate(body, known)) as [ChatTemplateField, ContentProblem][]) fields[field] = problemText(field, problem);
    for (const severity of CHAT_SEVERITIES) if (!UtilChatTemplate.COLORS.includes(body.colors[severity])) fields[`colors.${severity}`] = '팔레트의 색 중에서 골라 주세요.';
    if (Object.keys(fields).length) throw invalid(fields);
}

export async function mockChatTemplates(can: Can, type: ChatTemplateType): Promise<ChatTemplateListItemView[]> {
    await readList(can);
    return clone(state().chatTemplates.filter((row) => row.type === type).map(chatListItem));
}

export async function mockChatTemplate(can: Can, type: ChatTemplateType, id: string): Promise<ChatTemplateView> {
    await readDetail(can);
    return chatView(chatTemplateOf(type, id));
}

export async function mockCreateChatTemplate(can: Can, type: ChatTemplateType, body: ChatTemplateSaveRequest): Promise<ChatTemplateView> {
    await write(can);
    const s = state();
    if (s.chatTemplates.filter((item) => item.type === type && !item.builtin).length >= CHAT_TEMPLATE_LIMIT) throw new ApiError({ status: 409, code: 'TEMPLATE_LIMIT', message: `${CHANNEL_NAMES[type]} 템플릿은 ${CHAT_TEMPLATE_LIMIT}개까지 만들 수 있어요.` });
    validateChatTemplate(body);
    nextId += 1;
    const created: ChatTemplateRow = { ...chatRowOf(body), id: `${type.toLowerCase()}-${nextId}`, type, builtin: false, updatedAt: new Date().toISOString(), updatedByName: ME };
    s.chatTemplates.push(created);
    s.history.unshift(historyRow(created.id, 'CREATE', created.updatedAt as string, ME, [change('name', null, created.name)]));
    return chatView(created);
}

export async function mockUpdateChatTemplate(can: Can, type: ChatTemplateType, id: string, body: ChatTemplateSaveRequest): Promise<ChatTemplateView> {
    await write(can);
    const row = chatTemplateOf(type, id);
    if (row.builtin) throw new ApiError({ status: 409, code: 'TEMPLATE_BUILTIN', message: '기본 템플릿은 고칠 수 없어요. 복사해서 만들어 주세요.' });
    validateChatTemplate(body);
    const next = chatRowOf(body);
    const changes: SettingHistoryChange[] = [];
    if (next.name !== row.name) changes.push(change('name', row.name, next.name));
    for (const key of ['showSeverityIcon', 'boldTitle', 'showSiteName', 'showSentAt', 'showOpenButton'] as const) if (next[key] !== row[key]) changes.push(change(key, String(row[key]), String(next[key])));
    for (const severity of CHAT_SEVERITIES) {
        const field = `color${severity.charAt(0)}${severity.slice(1).toLowerCase()}`;
        if (next.colors[severity] !== row.colors[severity]) changes.push(change(field, CHAT_COLOR_NAMES[row.colors[severity]], CHAT_COLOR_NAMES[next.colors[severity]]));
    }
    for (const locale of MESSAGE_LOCALES) {
        const suffix = locale === 'ko' ? 'Ko' : 'En';
        if (next.openLabel[locale] !== row.openLabel[locale]) changes.push(change(`openLabel${suffix}`, row.openLabel[locale], next.openLabel[locale]));
        if (next.frames[locale] !== row.frames[locale]) changes.push(change(`frame${suffix}`, excerpt(row.frames[locale]), excerpt(next.frames[locale])));
    }
    if (changes.length === 0) return chatView(row);
    Object.assign(row, next, { updatedAt: new Date().toISOString(), updatedByName: ME });
    state().history.unshift(historyRow(id, 'UPDATE', row.updatedAt as string, ME, changes));
    return chatView(row);
}

export async function mockDeleteChatTemplate(can: Can, type: ChatTemplateType, id: string): Promise<void> {
    await write(can);
    const row = chatTemplateOf(type, id);
    if (row.builtin) throw new ApiError({ status: 409, code: 'TEMPLATE_BUILTIN', message: '기본 템플릿은 지울 수 없어요.' });
    const count = usingMessages(type, id).length;
    if (count > 0 || mockFlags().has('template-in-use')) throw new ApiError({ status: 409, code: 'TEMPLATE_IN_USE', message: `이 템플릿을 쓰는 문구가 ${Math.max(count, 1)}개 있어 지울 수 없어요. 문구의 템플릿을 먼저 바꿔 주세요.` });
    const s = state();
    s.chatTemplates = s.chatTemplates.filter((item) => item.id !== id);
}

export async function mockChatTemplateHistory(can: Can, type: ChatTemplateType, id: string, page: number, size: number): Promise<ChatTemplateHistoryPage> {
    await read(can);
    if (mockFlags().has('template-history-error')) throw MOCK_ERRORS.internal();
    chatTemplateOf(type, id);
    return pageOf(id, page, size);
}

/* ── API — 카카오톡 템플릿(준비 중 — 읽기만) ── */

function kakaoView(row: (typeof KAKAO_TEMPLATES)[number]): KakaoTemplateView {
    return clone({ ...row, messageCount: usingMessages('KAKAO', row.id).length, messageCounts: usingCounts('KAKAO', row.id) });
}

export async function mockKakaoTemplates(can: Can): Promise<KakaoTemplateListItemView[]> {
    await readList(can);
    return KAKAO_TEMPLATES.map(kakaoView).map(({ id, name, templateCode, status, messageCount, messageCounts, updatedAt, updatedByName }) => ({ id, name, templateCode, status, messageCount, messageCounts, updatedAt, updatedByName }));
}

export async function mockKakaoTemplate(can: Can, id: string): Promise<KakaoTemplateView> {
    await readDetail(can);
    const row = KAKAO_TEMPLATES.find((item) => item.id === id);
    if (!row) throw templateNotFound();
    return kakaoView(row);
}

