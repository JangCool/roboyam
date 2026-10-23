// MOCK: 알림 템플릿·메일 레이아웃 API(`/api/admin/notification-templates/**`·`/api/admin/mail-layouts/**`, 계약 `docs/features/message-template-contract.md` 「개정 3」)의 메모리 목업
// — `useNotificationTemplateApi.ts`·`useMailLayoutApi.ts` 가 이 파일의 함수를 부른다(이메일 템플릿이 레이아웃을 가리키므로 상태를 한 곳에 둔다).
// 서버가 붙으면 `template/api/mock/` 과 함께 지운다. 바꾼 것은 메모리에만 남아 **새로고침하면 처음 상태로 돌아간다**.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` (쉼표로 여러 개) ─
//   template-error          목록(대상·템플릿·레이아웃 목록)이 `500 INTERNAL` 로 실패(화면 오류 상태와 다시 시도)
//   template-detail-error   상세(대상 상세·템플릿 하나·레이아웃 하나)가 `500` 으로 실패
//   template-empty          대상(이벤트·필수 안내 메일)이 하나도 없다 — 템플릿 목록·필수 안내 메일 목록이 빈 상태, 이벤트 목록의 「쓰는 템플릿」이 모두 「—」(레이아웃은 기본 레이아웃이 늘 있다)
//   template-denied         `template.read` 가 없다 — 읽기가 모두 `403 PERMISSION_DENIED`(권한 없음 안내, 이벤트 목록은 「쓰는 템플릿」 칸을 감춘다)
//   template-readonly       `template.read` 만 있다 — 화면이 읽기 전용, 쓰기는 `403`
//   template-save-error     쓰기(대상 저장·템플릿·레이아웃 만들기·저장·삭제)가 `500` 으로 실패
//   template-in-use         템플릿 지우기가 늘 `409 TEMPLATE_IN_USE`
//   layout-in-use           레이아웃 지우기가 늘 `409 LAYOUT_IN_USE`
//   template-test-fail      이메일 시험 발송이 실패 결과(`CONNECT_FAILED`) · 채팅 시험이 실패 결과
//   template-test-limit     이메일·채팅 시험이 `429 MAIL_TEST_RATE_LIMITED`(30초)
//   template-history-error  변경 이력(템플릿·레이아웃)이 `500` 으로 실패
//
// ── 흉내 ─
//   대상(개정 3): ① 운영자 이벤트 — 지금 `AdminNotificationEvent` 의 24종(준비 중 포함). 채널 앱 알림·이메일·채팅, 본인에게만 가는 4종(`*_ME`)은 채팅 없음.
//     ② 회원 이벤트 — 지금 실제로 일어나는 5종(`MemberNotificationType`: 댓글·답글·포인트 회수·글 숨김·댓글 숨김). 채널 앱 알림·이메일. 「준비 중」 회원 이벤트(게시판 기본 설정 「알림」에만 있는 것)는 템플릿 대상이 아니다.
//     ③ 필수 안내 메일 5종 — 회원 이메일 인증 · 운영자 초대 · 임시 비밀번호 · 재설정 승인번호 · 메일 서버 시험(앞의 4종은 보안 메일). 채널 이메일뿐.
//   기본 템플릿: 이메일 = 지금 메일 문구(`mail.*`·`notification.mail.*` — 알림 이벤트는 한 양식 `NotificationMailTemplate`, 회원 이벤트 이메일은 지금 없어 같은 꼴의 새 문구),
//     앱 알림 = 지금 알림 문구(`notification.event.*.title|body` · `member.notification.*` — `{0}` 자리는 이름 붙은 변수로), 채팅 = 지금 `ChannelMessageFormatter` 가 만드는 모양(심각도 그림 + 굵은 제목 + 본문 + 「열기」 링크).
//     이메일·채팅의 `{{title}}`·`{{body}}` 는 그 이벤트의 앱 알림 템플릿으로 만든 제목·본문이다(목업 가정).
//   공통 변수는 받는 사람별: 회원 `siteName`·`siteUrl`·`recipientName`, 운영자 `siteName`·`adminUrl`·`recipientName`. 레이아웃 `ANY` 는 `siteName`·`recipientName` 만.
//   사용자 템플릿 7개 · 규칙 4개 · 레이아웃 5개(회원 기본 · 운영자 기본 · 보안 안내(모두) · 공지(회원) · 간단(운영자)).
//   서버 규칙 흉내: 이메일 `UtilMailTemplate.validate` · 채팅·앱 알림 `UtilNotificationTemplate.validateChat|validateInApp` → 400 `VALIDATION` `fields.name|subject|body|layoutId`,
//   대상에 없는 채널(`fields.channel`), 기본 템플릿·레이아웃 고치기·지우기 409, 쓰는 중 지우기 409, 대상·채널당 20개 409 `TEMPLATE_LIMIT`, 규칙의 조건·템플릿(`fields.channels.EMAIL.rules[0].templateId` …).
//   오류 문구·변수 설명·조건 이름·대상 이름·기본 템플릿/레이아웃 이름은 서버가 요청 언어로 줄 글이라 한국어로 둔다(`i18n.md` 5).

import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { SettingHistoryChange } from '~/composables/common/useSettingHistory.types';
import type {
    MailLayoutAudience,
    MailLayoutAudienceQuery,
    MailLayoutColor,
    MailLayoutHistoryPage,
    MailLayoutListItemView,
    MailLayoutSaveRequest,
    MailLayoutView,
} from '~/composables/template/api/useMailLayoutApi.types';
import { MAIL_LAYOUT_AUDIENCES, MAIL_LAYOUT_LIMIT } from '~/composables/template/api/useMailLayoutApi.types';
import type {
    ChatTemplateTestRequest,
    ChatTemplateTestResultView,
    MailPreviewRequest,
    MailPreviewView,
    MailTemplateTestRequest,
    MailTemplateTestResultView,
    TemplateAudience,
    TemplateChannel,
    TemplateChannelDetailView,
    TemplateChannelSummaryView,
    TemplateConditionView,
    TemplateHistoryItemView,
    TemplateHistoryPage,
    TemplateListItemView,
    TemplateLocale,
    TemplateRuleView,
    TemplateSaveRequest,
    TemplateTargetDetailView,
    TemplateTargetQuery,
    TemplateTargetSaveRequest,
    TemplateTargetType,
    TemplateTargetView,
    TemplateVariableView,
    TemplateView,
} from '~/composables/template/api/useNotificationTemplateApi.types';
import { TEMPLATE_LIMIT, TEMPLATE_LOCALES, TEMPLATE_RULE_MAX } from '~/composables/template/api/useNotificationTemplateApi.types';
import type { MailLayoutField, MailTemplateField, MailTemplateProblem } from '~/utils/util-mail-template';
import UtilMailTemplate from '~/utils/util-mail-template';
import type { NotificationTemplateField } from '~/utils/util-notification-template';
import UtilNotificationTemplate from '~/utils/util-notification-template';

type Can = (code: string) => boolean;
type Content = Record<TemplateLocale, { subject: string; body: string }>;

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

const v = (name: string, description: string, sample: string, extra: { required?: boolean; link?: boolean } = {}): TemplateVariableView => ({ name, description, sample, required: extra.required ?? false, link: extra.link ?? false });

const SITE_NAME_VAR = v('siteName', '사이트 이름', '로보얌');
const SITE_URL_VAR = v('siteUrl', '공개 사이트 주소', 'https://roboyam.example.com', { link: true });
const ADMIN_URL_VAR = v('adminUrl', '관리 사이트 주소', 'https://admin.roboyam.example.com', { link: true });
const RECIPIENT_VAR = v('recipientName', '받는 사람 이름(없으면 이메일)', '홍길동');

/** 받는 사람(레이아웃은 쓰는 대상)의 공통 변수 */
function commonVars(audience: MailLayoutAudience): TemplateVariableView[] {
    if (audience === 'MEMBER') return [SITE_NAME_VAR, SITE_URL_VAR, RECIPIENT_VAR];
    if (audience === 'OPERATOR') return [SITE_NAME_VAR, ADMIN_URL_VAR, RECIPIENT_VAR];
    return [SITE_NAME_VAR, RECIPIENT_VAR];
}

const btn = (link: string, label: string) => `<p data-mail-button=""><a href="{{${link}}}">${label}</a></p>`;

/* ── 대상 정의(코드가 정하는 것) ── */

interface ChannelDef {
    channel: TemplateChannel;
    variables: TemplateVariableView[];
    conditions: TemplateConditionView[];
    defaults: Content;
}

interface TargetDef {
    type: TemplateTargetType;
    code: string;
    audience: TemplateAudience;
    area: string | null;
    name: string;
    description: string;
    security: boolean;
    channels: ChannelDef[];
}

const RECIPIENT_SOURCE: TemplateConditionView = {
    key: 'recipientSource',
    label: '받는 운영자',
    options: [
        { value: 'DEFAULT', label: '기본 수신 규칙으로 받는 운영자' },
        { value: 'TEAM', label: '추가 수신 팀으로 받는 운영자' },
    ],
};
const CHAT_TOOL: TemplateConditionView = {
    key: 'chatTool',
    label: '알림 도구 종류',
    options: [
        { value: 'SLACK', label: 'Slack' },
        { value: 'MATTERMOST', label: 'Mattermost' },
    ],
};

/** 운영자 이벤트 하나 — 지금 서버 번들(`notification.event.<코드>.*`)의 이름·설명·제목·본문. `{0}` 자리는 `params` 의 이름으로 */
interface OperatorEventSeed {
    code: string;
    area: string;
    severity: 'INFO' | 'WARNING' | 'CRITICAL';
    name: string;
    description: string;
    params: [name: string, description: string, sample: string][];
    title: Record<TemplateLocale, string>;
    body: Record<TemplateLocale, string>;
    path: string;
    /** 본인에게만 가는 이벤트 — 채팅(공유 채널)으로 보내지 않는다 */
    self?: boolean;
    extraConditions?: Partial<Record<TemplateChannel, TemplateConditionView[]>>;
}

const BATCH_JOB: TemplateConditionView = {
    key: 'jobName',
    label: '배치',
    options: [
        { value: 'grade-promotion', label: '등급 일수 승급' },
        { value: 'member-notification-cleanup', label: '회원 알림 정리' },
    ],
};

const SEVERITY_ICON: Record<OperatorEventSeed['severity'], string> = { INFO: ':information_source:', WARNING: ':warning:', CRITICAL: ':rotating_light:' };
const pending = (code: string, area: string, severity: OperatorEventSeed['severity'], name: string, description: string, ko: [string, string], en: [string, string]): OperatorEventSeed => ({
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
        extraConditions: { EMAIL: [BATCH_JOB], CHAT: [BATCH_JOB] },
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

/** 채팅 글 — 지금 `ChannelMessageFormatter.format` 의 모양(심각도 그림 + 굵은 제목 + 본문 + 「열기」 링크, 번들 `notification.channel.post.open`) */
const OPERATOR_CHAT: Content = {
    ko: { subject: '', body: '{{severityIcon}} **{{title}}**\n{{body}}\n[열기]({{link}})' },
    en: { subject: '', body: '{{severityIcon}} **{{title}}**\n{{body}}\n[Open]({{link}})' },
};

function operatorTarget(seed: OperatorEventSeed): TargetDef {
    const common = commonVars('OPERATOR');
    const params = seed.params.map(([name, description, sample]) => v(name, description, sample));
    const filledTitle = UtilNotificationTemplate.fillSamples(seed.title.ko, params);
    const filledBody = UtilNotificationTemplate.fillSamples(seed.body.ko, params);
    const sent = [
        v('title', '앱 알림 제목(이 이벤트의 앱 알림 템플릿으로 만든 글)', filledTitle),
        v('body', '앱 알림 본문(이 이벤트의 앱 알림 템플릿으로 만든 글)', filledBody),
        v('link', '관리자 앱에서 볼 주소', `https://admin.roboyam.example.com${seed.path}`, { link: true }),
        v('occurredAt', '일어난 시각(받는 사람의 시간대)', '2026-10-23 09:30'),
    ];
    const channels: ChannelDef[] = [
        { channel: 'IN_APP', variables: [...common, ...params], conditions: [], defaults: { ko: { subject: seed.title.ko, body: seed.body.ko }, en: { subject: seed.title.en, body: seed.body.en } } },
        { channel: 'EMAIL', variables: [...common, ...params, ...sent], conditions: [RECIPIENT_SOURCE, ...(seed.extraConditions?.EMAIL ?? [])], defaults: OPERATOR_EMAIL },
    ];
    if (!seed.self) {
        channels.push({
            channel: 'CHAT',
            variables: [...common, ...params, ...sent, v('severityIcon', '심각도 그림(Slack·Mattermost 그림 이름)', SEVERITY_ICON[seed.severity])],
            conditions: [CHAT_TOOL, ...(seed.extraConditions?.CHAT ?? [])],
            defaults: OPERATOR_CHAT,
        });
    }
    return { type: 'EVENT', code: seed.code, audience: 'OPERATOR', area: seed.area, name: seed.name, description: seed.description, security: false, channels };
}

/** 회원 이벤트 하나 — 지금 서버 `MemberNotificationType` · 번들 `member.notification.*` */
interface MemberEventSeed {
    code: string;
    area: string;
    name: string;
    description: string;
    params: [name: string, description: string, sample: string][];
    title: Record<TemplateLocale, string>;
    body: Record<TemplateLocale, string>;
    path: string;
    conditions: TemplateConditionView[];
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
    const sent = [
        v('title', '앱 알림 제목(이 이벤트의 앱 알림 템플릿으로 만든 글)', UtilNotificationTemplate.fillSamples(seed.title.ko, params)),
        v('body', '앱 알림 본문(이 이벤트의 앱 알림 템플릿으로 만든 글)', UtilNotificationTemplate.fillSamples(seed.body.ko, params)),
        v('link', '공개 사이트에서 볼 주소', `https://roboyam.example.com${seed.path}`, { link: true }),
        v('occurredAt', '일어난 시각(받는 사람의 시간대)', '2026-10-23 09:30'),
    ];
    return {
        type: 'EVENT',
        code: seed.code,
        audience: 'MEMBER',
        area: seed.area,
        name: seed.name,
        description: seed.description,
        security: false,
        channels: [
            { channel: 'IN_APP', variables: [...common, ...params], conditions: seed.conditions, defaults: { ko: { subject: seed.title.ko, body: seed.body.ko }, en: { subject: seed.title.en, body: seed.body.en } } },
            { channel: 'EMAIL', variables: [...common, ...params, ...sent], conditions: seed.conditions, defaults: MEMBER_EMAIL },
        ],
    };
}

/** 회원 이벤트 정의(이름·설명·영역) — 회원 이벤트 목록 목업(`member-event-mock.ts`)이 같은 이름을 쓴다 */
export function mockMemberEventSeeds(): { code: string; area: string; name: string; description: string }[] {
    return MEMBER_EVENTS.map(({ code, area, name, description }) => ({ code, area, name, description }));
}

function requiredMail(code: string, audience: TemplateAudience, name: string, description: string, security: boolean, variables: TemplateVariableView[], conditions: TemplateConditionView[], defaults: Content): TargetDef {
    return { type: 'REQUIRED_MAIL', code, audience, area: null, name, description, security, channels: [{ channel: 'EMAIL', variables: [...commonVars(audience), ...variables], conditions, defaults }] };
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

/* ── 레이아웃 정의 ── */

const SITE_NAME = '로보얌';
/** 로고는 사이트 설정 「일반」의 것 — 목업은 로고가 없는 사이트로 둔다 */
const LOGO_URL: string | null = null;
const CONTENT_VAR = v('content', '메일 본문이 들어갈 자리(지울 수 없어요)', '', { required: true });
/** 받는 사람별 기본 레이아웃(builtin) — 새 이메일 템플릿이 이것으로 시작한다 */
const DEFAULT_LAYOUT_ID: Record<TemplateAudience, string> = { MEMBER: 'layout-member', OPERATOR: 'layout-operator' };
const COLOR_NAMES: Record<MailLayoutColor, string> = { BLUE: '파랑', INDIGO: '남색', TEAL: '청록', GREEN: '초록', AMBER: '호박', ORANGE: '주황', RED: '빨강', SLATE: '먹색' };
const AUDIENCE_NAMES: Record<MailLayoutAudience, string> = { MEMBER: '회원', OPERATOR: '운영자', ANY: '모두' };
const CHANNEL_NAMES: Record<TemplateChannel, string> = { IN_APP: '앱 알림', EMAIL: '이메일', CHAT: '채팅' };

const layoutVars = (audience: MailLayoutAudience): TemplateVariableView[] => [...commonVars(audience), CONTENT_VAR];
const commonNames = (audience: MailLayoutAudience): string[] => commonVars(audience).map((item) => item.name);

interface LayoutRow {
    id: string;
    name: string;
    builtin: boolean;
    audience: MailLayoutAudience;
    showLogo: boolean;
    showSiteName: boolean;
    headerColor: MailLayoutColor | null;
    footer: Record<TemplateLocale, string>;
    frames: Record<TemplateLocale, string>;
    updatedAt: string | null;
    updatedByName: string | null;
}

const PLAIN_FRAMES: Record<TemplateLocale, string> = { ko: '<p>{{content}}</p>', en: '<p>{{content}}</p>' };

function initialLayouts(): LayoutRow[] {
    return [
        {
            id: DEFAULT_LAYOUT_ID.MEMBER,
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
            id: DEFAULT_LAYOUT_ID.OPERATOR,
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
            id: 'layout-security',
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
            id: 'layout-notice',
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
            id: 'layout-simple',
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

/* ── 상태(메모리) ── */

interface ChannelState {
    selected: Record<TemplateLocale, string>;
    rules: TemplateRuleView[];
}

interface HistoryRow extends TemplateHistoryItemView {
    targetId: string;
}

interface Store {
    templates: TemplateView[];
    /** 열쇠 `대상 코드|채널` */
    channels: Map<string, ChannelState>;
    layouts: LayoutRow[];
    history: HistoryRow[];
}

let store: Store | null = null;
let nextId = 200;

const keyOf = (code: string, channel: TemplateChannel) => `${code}|${channel}`;
const builtinId = (code: string, channel: TemplateChannel, locale: TemplateLocale) => `builtin-${code}-${channel}-${locale}`;
const refOf = (def: TargetDef) => ({ type: def.type, code: def.code, audience: def.audience });

function builtins(): TemplateView[] {
    return TARGETS.flatMap((def) =>
        def.channels.flatMap((ch) =>
            TEMPLATE_LOCALES.map((locale) => ({
                id: builtinId(def.code, ch.channel, locale),
                target: refOf(def),
                channel: ch.channel,
                // 이름은 서버가 요청 언어로 준다(목업은 한국어)
                name: '기본 템플릿',
                locale,
                layoutId: ch.channel === 'EMAIL' ? DEFAULT_LAYOUT_ID[def.audience] : null,
                subject: ch.defaults[locale].subject,
                body: ch.defaults[locale].body,
                builtin: true,
                updatedAt: null,
                updatedByName: null,
            })),
        ),
    );
}

function userTemplate(id: string, code: string, channel: TemplateChannel, name: string, locale: TemplateLocale, layoutId: string | null, subject: string, body: string, updatedAt: string, by: string): TemplateView {
    return { id, target: refOf(targetOf('', code)), channel, name, locale, layoutId, subject, body, builtin: false, updatedAt, updatedByName: by };
}

function state(): Store {
    if (store) return store;
    const verify = (REQUIRED_MAILS[0] as TargetDef).channels[0] as ChannelDef;
    const user: TemplateView[] = [
        userTemplate(
            'tpl-101',
            'MEMBER_EMAIL_VERIFY',
            'EMAIL',
            '소셜 가입 회원용',
            'ko',
            'layout-security',
            '받은 이메일이 맞는지 확인해 주세요',
            verify.defaults.ko.body.replace('{{nickname}}님, {{siteName}}에 가입해 주셔서 고마워요.', '{{nickname}}님, 소셜 계정으로 {{siteName}}에 가입하셨네요. 소셜 계정의 이메일이 맞는지 한 번만 확인해 주세요.'),
            '2026-10-20T05:12:00Z',
            ME,
        ),
        userTemplate(
            'tpl-102',
            'BATCH_RUN_FAILED',
            'EMAIL',
            '긴급 배치 실패',
            'ko',
            'layout-security',
            '[긴급] {{title}}',
            '<p><strong>[긴급] {{title}}</strong></p><p>{{body}}</p><p>{{occurredAt}} 에 일어났어요. 바로 확인해 주세요.</p>' + btn('link', '배치 로그 보기') + '<p>{{link}}</p>',
            '2026-10-21T01:40:00Z',
            '박운영',
        ),
        userTemplate(
            'tpl-103',
            'OPERATOR_INVITE',
            'EMAIL',
            '초대 메일(간결)',
            'ko',
            'layout-simple',
            '{{siteName}} 관리자 앱 초대',
            '<p>{{siteName}} 관리자 앱 운영자로 초대됐어요. {{validHours}}시간 안에 수락해 주세요.</p>' + btn('inviteLink', '초대 수락하기') + '<p>{{inviteLink}}</p>',
            '2026-10-22T08:05:00Z',
            ME,
        ),
        userTemplate('tpl-104', 'MAIL_TEST', 'EMAIL', 'Plain test', 'en', DEFAULT_LAYOUT_ID.OPERATOR, 'Test mail from {{siteName}}', '<p>This is a test mail from {{siteName}}.</p>', '2026-10-22T10:30:00Z', ME),
        userTemplate('tpl-105', 'BATCH_RUN_FAILED', 'CHAT', '운영 채널용(짧게)', 'ko', null, '', '{{severityIcon}} **{{jobName}}** 실패 — {{reason}}\n[배치 로그]({{link}})', '2026-10-22T11:10:00Z', '박운영'),
        userTemplate('tpl-106', 'COMMENT', 'IN_APP', '묶인 댓글', 'ko', null, '{{actorName}}님 외 {{otherCount}}개의 댓글이 내 글에 달렸어요', '{{postTitle}}', '2026-10-22T12:00:00Z', ME),
        userTemplate('tpl-107', 'POST_HIDDEN', 'IN_APP', '권리 침해 임시 숨김', 'ko', null, '권리 침해 신고로 내 글이 임시로 숨겨졌어요', '{{postTitle}} — 이의가 있으면 고객센터로 알려 주세요.', '2026-10-22T12:20:00Z', ME),
    ];
    const channels = new Map<string, ChannelState>();
    for (const def of TARGETS) for (const ch of def.channels) channels.set(keyOf(def.code, ch.channel), { selected: { ko: builtinId(def.code, ch.channel, 'ko'), en: builtinId(def.code, ch.channel, 'en') }, rules: [] });
    const at = (code: string, channel: TemplateChannel) => channels.get(keyOf(code, channel)) as ChannelState;
    at('MEMBER_EMAIL_VERIFY', 'EMAIL').rules = [{ id: 'rule-1', condition: { key: 'signupMethod', value: 'SOCIAL' }, templateId: 'tpl-101' }];
    at('BATCH_RUN_FAILED', 'EMAIL').rules = [{ id: 'rule-2', condition: { key: 'jobName', value: 'grade-promotion' }, templateId: 'tpl-102' }];
    at('OPERATOR_INVITE', 'EMAIL').selected.ko = 'tpl-103';
    at('BATCH_RUN_FAILED', 'CHAT').selected.ko = 'tpl-105';
    at('COMMENT', 'IN_APP').rules = [{ id: 'rule-3', condition: { key: 'grouped', value: 'GROUPED' }, templateId: 'tpl-106' }];
    at('POST_HIDDEN', 'IN_APP').rules = [{ id: 'rule-4', condition: { key: 'hideReason', value: 'RIGHTS' }, templateId: 'tpl-107' }];

    const history: HistoryRow[] = [
        historyRow('tpl-101', 'UPDATE', '2026-10-20T05:12:00Z', ME, [change('subject', '이메일 인증을 완료해 주세요', '받은 이메일이 맞는지 확인해 주세요'), change('layoutId', '회원 기본', '보안 안내')]),
        historyRow('tpl-101', 'CREATE', '2026-10-19T23:58:00Z', ME, [change('name', null, '소셜 가입 회원용'), change('locale', null, 'ko'), change('layoutId', null, '회원 기본')]),
        historyRow('tpl-102', 'CREATE', '2026-10-21T01:40:00Z', '박운영', [change('name', null, '긴급 배치 실패'), change('locale', null, 'ko'), change('layoutId', null, '보안 안내')]),
        historyRow('tpl-103', 'CREATE', '2026-10-22T08:05:00Z', ME, [change('name', null, '초대 메일(간결)'), change('locale', null, 'ko'), change('layoutId', null, '간단')]),
        historyRow('tpl-104', 'CREATE', '2026-10-22T10:30:00Z', ME, [change('name', null, 'Plain test'), change('locale', null, 'en'), change('layoutId', null, '운영자 기본')]),
        historyRow('tpl-105', 'CREATE', '2026-10-22T11:10:00Z', '박운영', [change('name', null, '운영 채널용(짧게)'), change('locale', null, 'ko')]),
        historyRow('tpl-106', 'CREATE', '2026-10-22T12:00:00Z', ME, [change('name', null, '묶인 댓글'), change('locale', null, 'ko')]),
        historyRow('tpl-107', 'CREATE', '2026-10-22T12:20:00Z', ME, [change('name', null, '권리 침해 임시 숨김'), change('locale', null, 'ko')]),
        historyRow('layout-security', 'UPDATE', '2026-10-18T02:10:00Z', ME, [change('headerColor', '주황', '빨강'), change('audience', '회원', '모두')]),
        historyRow('layout-security', 'CREATE', '2026-10-17T09:00:00Z', ME, [change('name', null, '보안 안내')]),
        historyRow('layout-notice', 'CREATE', '2026-10-19T07:45:00Z', '박운영', [change('name', null, '공지')]),
        historyRow('layout-simple', 'CREATE', '2026-10-20T00:30:00Z', ME, [change('name', null, '간단')]),
    ];

    store = { templates: [...builtins(), ...user], channels, layouts: initialLayouts(), history };
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

const notFound = () => new ApiError({ status: 404, code: 'TEMPLATE_NOT_FOUND', message: '템플릿을 찾을 수 없어요.' });
const targetNotFound = () => new ApiError({ status: 404, code: 'TEMPLATE_TARGET_NOT_FOUND', message: '이벤트나 필수 안내 메일을 찾을 수 없어요.' });
const layoutNotFound = () => new ApiError({ status: 404, code: 'LAYOUT_NOT_FOUND', message: '레이아웃을 찾을 수 없어요.' });
const invalid = (fields: Record<string, string>) => new ApiError({ status: 400, code: 'VALIDATION', message: '입력값을 확인해 주세요.', fields });

/** 대상 — `type` 이 비면 코드만으로 찾는다(목업 안에서만 — 코드는 겹치지 않는다) */
function targetOf(type: TemplateTargetType | '', code: string): TargetDef {
    const def = TARGETS.find((item) => item.code === code && (!type || item.type === type));
    if (!def) throw targetNotFound();
    return def;
}

function channelDefOf(def: TargetDef, channel: TemplateChannel): ChannelDef | undefined {
    return def.channels.find((item) => item.channel === channel);
}

function templateOf(id: string): TemplateView {
    const found = state().templates.find((item) => item.id === id);
    if (!found) throw notFound();
    return found;
}

function layoutOf(id: string): LayoutRow {
    const found = state().layouts.find((item) => item.id === id);
    if (!found) throw layoutNotFound();
    return found;
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

const clone = <T>(value: T): T => JSON.parse(JSON.stringify(value)) as T;

/** 이 템플릿을 쓰는 곳 — 언어별 쓸 템플릿 또는 규칙 */
function usageOf(template: TemplateView): { defaultFor: TemplateLocale[]; ruleCount: number } {
    const ch = state().channels.get(keyOf(template.target.code, template.channel));
    if (!ch) return { defaultFor: [], ruleCount: 0 };
    return { defaultFor: TEMPLATE_LOCALES.filter((locale) => ch.selected[locale] === template.id), ruleCount: ch.rules.filter((rule) => rule.templateId === template.id).length };
}

function inUse(template: TemplateView): boolean {
    const usage = usageOf(template);
    return usage.defaultFor.length > 0 || usage.ruleCount > 0;
}

function layoutUseCount(id: string): number {
    return state().templates.filter((item) => item.layoutId === id).length;
}

function layoutUseCounts(id: string): Record<TemplateAudience, number> {
    const using = state().templates.filter((item) => item.layoutId === id);
    return { MEMBER: using.filter((item) => item.target.audience === 'MEMBER').length, OPERATOR: using.filter((item) => item.target.audience === 'OPERATOR').length };
}

/** 검사 문제 → 서버 문구(목업 — 서버가 요청 언어로 줄 글) */
function problemText(field: string, problem: MailTemplateProblem): string {
    const names = (problem.names ?? []).map((name) => `{{${name}}}`).join(', ');
    switch (problem.code) {
        case 'REQUIRED':
            return field === 'name' ? '이름을 입력해 주세요.' : field === 'subject' ? '제목을 입력해 주세요.' : '본문을 입력해 주세요.';
        case 'TOO_LONG':
            return `${problem.max}자까지 입력할 수 있어요.`;
        case 'MULTI_LINE':
            return '제목은 한 줄로 입력해 주세요.';
        case 'UNKNOWN_VARIABLE':
            return `이 템플릿에서 쓸 수 없는 변수예요: ${names}`;
        case 'AUDIENCE_VARIABLE':
            return `이 템플릿에서는 쓸 수 없는 변수예요: ${names} — ${problem.audience === 'OPERATOR' ? '운영자' : '회원'}에게 가는 템플릿에서만 쓸 수 있어요.`;
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

/** 템플릿 내용 검사 — 채널마다 다른 규칙(서버도 같은 규칙) */
function validateTemplate(body: TemplateSaveRequest, def: TargetDef): void {
    const ch = channelDefOf(def, body.channel);
    if (!ch) throw invalid({ channel: `「${def.name}」은(는) ${CHANNEL_NAMES[body.channel]}(으)로 나가지 않아요.` });
    const fields: Record<string, string> = {};
    if (body.channel === 'EMAIL') {
        const problems = UtilMailTemplate.validate({ name: body.name, subject: body.subject, bodyHtml: body.body }, { audience: def.audience, security: def.security, variables: ch.variables });
        for (const [field, problem] of Object.entries(problems) as [MailTemplateField, MailTemplateProblem][]) {
            // 버튼 문제는 서버에서는 본문 칸의 문제다
            const key = field === 'button' || field === 'bodyHtml' ? 'body' : field;
            if (!fields[key]) fields[key] = problemText(field === 'bodyHtml' ? 'body' : field, problem);
        }
        const layout = state().layouts.find((item) => item.id === body.layoutId);
        if (!layout) fields.layoutId = '레이아웃을 골라 주세요.';
        else if (!UtilMailTemplate.layoutFits(layout.audience, def.audience)) fields.layoutId = `${AUDIENCE_NAMES[def.audience]} 메일에는 쓸 수 없는 레이아웃이에요. 쓰는 대상이 ${AUDIENCE_NAMES[def.audience]} 또는 모두인 레이아웃을 골라 주세요.`;
    } else {
        const rules = { audience: def.audience, variables: ch.variables };
        const problems = body.channel === 'CHAT' ? UtilNotificationTemplate.validateChat(body, rules) : UtilNotificationTemplate.validateInApp(body, rules);
        for (const [field, problem] of Object.entries(problems) as [NotificationTemplateField, MailTemplateProblem][]) fields[field] = problemText(field, problem);
    }
    if (body.locale !== 'ko' && body.locale !== 'en') fields.locale = '언어는 ko · en 중 하나예요.';
    if (Object.keys(fields).length) throw invalid(fields);
}

function channelSummary(def: TargetDef, ch: ChannelDef): TemplateChannelSummaryView {
    const s = state();
    const cs = s.channels.get(keyOf(def.code, ch.channel)) as ChannelState;
    const pick = (locale: TemplateLocale) => {
        const template = s.templates.find((item) => item.id === cs.selected[locale]) ?? templateOf(builtinId(def.code, ch.channel, locale));
        return { id: template.id, name: template.name, builtin: template.builtin };
    };
    return {
        channel: ch.channel,
        variables: ch.variables,
        conditions: ch.conditions,
        selected: { ko: pick('ko'), en: pick('en') },
        ruleCount: cs.rules.length,
        templateCount: s.templates.filter((item) => item.target.code === def.code && item.channel === ch.channel && !item.builtin).length,
    };
}

function targetView(def: TargetDef): TemplateTargetView {
    return { type: def.type, code: def.code, audience: def.audience, area: def.area, name: def.name, description: def.description, security: def.security, channels: def.channels.map((ch) => channelSummary(def, ch)) };
}

function detailView(def: TargetDef): TemplateTargetDetailView {
    const s = state();
    return clone({
        summary: targetView(def),
        channels: def.channels.map((ch): TemplateChannelDetailView => {
            const cs = s.channels.get(keyOf(def.code, ch.channel)) as ChannelState;
            return {
                channel: ch.channel,
                defaults: { ko: builtinId(def.code, ch.channel, 'ko'), en: builtinId(def.code, ch.channel, 'en') },
                selected: cs.selected,
                rules: cs.rules,
                templates: s.templates.filter((item) => item.target.code === def.code && item.channel === ch.channel),
            };
        }),
    });
}

function listItem(template: TemplateView): TemplateListItemView {
    const def = targetOf(template.target.type, template.target.code);
    const layout = template.layoutId ? state().layouts.find((item) => item.id === template.layoutId) : undefined;
    const { body: _body, subject: _subject, ...rest } = template;
    return { ...rest, targetName: def.name, targetArea: def.area, security: def.security, layoutName: layout?.name ?? null, ...usageOf(template) };
}

function layoutListItem(row: LayoutRow): MailLayoutListItemView {
    return { id: row.id, name: row.name, builtin: row.builtin, audience: row.audience, headerColor: row.headerColor, templateCount: layoutUseCount(row.id), templateCounts: layoutUseCounts(row.id), updatedAt: row.updatedAt, updatedByName: row.updatedByName };
}

function layoutView(row: LayoutRow): MailLayoutView {
    const variables = Object.fromEntries(MAIL_LAYOUT_AUDIENCES.map((audience) => [audience, layoutVars(audience)])) as Record<MailLayoutAudience, TemplateVariableView[]>;
    return clone({ ...row, templateCount: layoutUseCount(row.id), templateCounts: layoutUseCounts(row.id), logoUrl: LOGO_URL, siteName: SITE_NAME, variables });
}

/** 이력의 본문 값 — 글자 요약 80자(가정) */
function excerpt(text: string): string {
    const plain = UtilMailTemplate.plainText(text);
    return plain.length > 80 ? `${plain.slice(0, 80)}…` : plain;
}

function pageOf(targetId: string, page: number, size: number): TemplateHistoryPage {
    const rows = state().history.filter((row) => row.targetId === targetId);
    const items = rows.slice(page * size, page * size + size).map(({ targetId: _omit, ...rest }) => rest);
    return clone({ items, total: rows.length, page, size });
}

/* ── API — 대상(이벤트 · 필수 안내 메일) ── */

export async function mockTargets(can: Can, query: TemplateTargetQuery = {}): Promise<TemplateTargetView[]> {
    await readList(can);
    if (mockFlags().has('template-empty')) return [];
    return clone(TARGETS.filter((def) => (!query.type || def.type === query.type) && (!query.audience || def.audience === query.audience)).map(targetView));
}

export async function mockTarget(can: Can, type: TemplateTargetType, code: string): Promise<TemplateTargetDetailView> {
    await readDetail(can);
    if (mockFlags().has('template-empty')) throw targetNotFound();
    return detailView(targetOf(type, code));
}

export async function mockSaveTarget(can: Can, type: TemplateTargetType, code: string, body: TemplateTargetSaveRequest): Promise<TemplateTargetDetailView> {
    await write(can);
    const def = targetOf(type, code);
    const s = state();
    const fields: Record<string, string> = {};
    for (const [channel, value] of Object.entries(body.channels) as [TemplateChannel, NonNullable<TemplateTargetSaveRequest['channels'][TemplateChannel]>][]) {
        const ch = channelDefOf(def, channel);
        const prefix = `channels.${channel}`;
        if (!ch) {
            fields[prefix] = `「${def.name}」은(는) ${CHANNEL_NAMES[channel]}(으)로 나가지 않아요.`;
            continue;
        }
        const own = (id: string) => s.templates.find((item) => item.id === id && item.target.code === code && item.channel === channel);
        for (const locale of TEMPLATE_LOCALES) {
            const template = own(value.selected[locale]);
            if (!template) fields[`${prefix}.selected.${locale}`] = '이 채널의 템플릿을 골라 주세요.';
            else if (template.locale !== locale) fields[`${prefix}.selected.${locale}`] = '고른 언어의 템플릿만 쓸 수 있어요.';
        }
        value.rules.forEach((rule, at) => {
            const condition = ch.conditions.find((item) => item.key === rule.condition.key);
            if (!condition || !condition.options.some((option) => option.value === rule.condition.value)) fields[`${prefix}.rules[${at}].condition`] = '이 채널에서 고를 수 있는 조건이 아니에요.';
            if (!own(rule.templateId)) fields[`${prefix}.rules[${at}].templateId`] = '이 채널의 템플릿을 골라 주세요.';
        });
        if (value.rules.length > TEMPLATE_RULE_MAX) fields[`${prefix}.rules`] = `규칙은 ${TEMPLATE_RULE_MAX}개까지 둘 수 있어요.`;
    }
    if (Object.keys(fields).length) throw invalid(fields);
    for (const [channel, value] of Object.entries(body.channels) as [TemplateChannel, NonNullable<TemplateTargetSaveRequest['channels'][TemplateChannel]>][]) {
        const cs = s.channels.get(keyOf(code, channel)) as ChannelState;
        cs.selected = { ...value.selected };
        cs.rules = value.rules.map((rule) => ({ id: rule.id ?? `rule-${(nextId += 1)}`, condition: { ...rule.condition }, templateId: rule.templateId }));
    }
    return detailView(def);
}

/* ── API — 템플릿 ── */

export async function mockTemplates(can: Can): Promise<TemplateListItemView[]> {
    await readList(can);
    if (mockFlags().has('template-empty')) return [];
    return clone(state().templates.map(listItem));
}

export async function mockTemplate(can: Can, id: string): Promise<TemplateView> {
    await readDetail(can);
    return clone(templateOf(id));
}

export async function mockCreateTemplate(can: Can, body: TemplateSaveRequest): Promise<TemplateView> {
    await write(can);
    const def = targetOf(body.targetType, body.targetCode);
    const s = state();
    if (s.templates.filter((item) => item.target.code === def.code && item.channel === body.channel && !item.builtin).length >= TEMPLATE_LIMIT) {
        throw new ApiError({ status: 409, code: 'TEMPLATE_LIMIT', message: `한 대상의 한 채널에 템플릿은 ${TEMPLATE_LIMIT}개까지 만들 수 있어요.` });
    }
    validateTemplate(body, def);
    nextId += 1;
    const email = body.channel === 'EMAIL';
    const created: TemplateView = {
        id: `tpl-${nextId}`,
        target: refOf(def),
        channel: body.channel,
        name: body.name.trim(),
        locale: body.locale,
        layoutId: email ? body.layoutId : null,
        subject: body.channel === 'CHAT' ? '' : body.subject,
        body: email ? UtilMailTemplate.cleanHrefs(body.body) : body.body,
        builtin: false,
        updatedAt: new Date().toISOString(),
        updatedByName: ME,
    };
    s.templates.push(created);
    const changes = [change('name', null, created.name), change('locale', null, created.locale)];
    if (created.layoutId) changes.push(change('layoutId', null, layoutOf(created.layoutId).name));
    s.history.unshift(historyRow(created.id, 'CREATE', created.updatedAt as string, ME, changes));
    return clone(created);
}

export async function mockUpdateTemplate(can: Can, id: string, body: TemplateSaveRequest): Promise<TemplateView> {
    await write(can);
    const template = templateOf(id);
    if (template.builtin) throw new ApiError({ status: 409, code: 'TEMPLATE_BUILTIN', message: '기본 템플릿은 고칠 수 없어요. 복사해서 만들어 주세요.' });
    const def = targetOf(template.target.type, template.target.code);
    // 대상·채널은 바꾸지 못한다 — 원래 값으로 본다
    const fixed = { ...body, targetType: template.target.type, targetCode: template.target.code, channel: template.channel };
    validateTemplate(fixed, def);
    const email = template.channel === 'EMAIL';
    const next = {
        name: fixed.name.trim(),
        locale: fixed.locale,
        layoutId: email ? fixed.layoutId : null,
        subject: template.channel === 'CHAT' ? '' : fixed.subject,
        body: email ? UtilMailTemplate.cleanHrefs(fixed.body) : fixed.body,
    };
    const cs = state().channels.get(keyOf(template.target.code, template.channel)) as ChannelState;
    if (next.locale !== template.locale && cs.selected[template.locale] === template.id) throw invalid({ locale: '이 언어의 쓸 템플릿으로 쓰는 중이라 언어를 바꿀 수 없어요.' });
    const changes: SettingHistoryChange[] = [];
    if (next.name !== template.name) changes.push(change('name', template.name, next.name));
    if (next.locale !== template.locale) changes.push(change('locale', template.locale, next.locale));
    if (next.layoutId !== template.layoutId) changes.push(change('layoutId', template.layoutId ? layoutOf(template.layoutId).name : null, next.layoutId ? layoutOf(next.layoutId).name : null));
    if (next.subject !== template.subject) changes.push(change('subject', template.subject, next.subject));
    if (next.body !== template.body) changes.push(change('body', excerpt(template.body), excerpt(next.body)));
    if (changes.length === 0) return clone(template);
    Object.assign(template, next, { updatedAt: new Date().toISOString(), updatedByName: ME });
    state().history.unshift(historyRow(id, 'UPDATE', template.updatedAt as string, ME, changes));
    return clone(template);
}

export async function mockDeleteTemplate(can: Can, id: string): Promise<void> {
    await write(can);
    const template = templateOf(id);
    if (template.builtin) throw new ApiError({ status: 409, code: 'TEMPLATE_BUILTIN', message: '기본 템플릿은 지울 수 없어요.' });
    if (inUse(template) || mockFlags().has('template-in-use')) {
        throw new ApiError({ status: 409, code: 'TEMPLATE_IN_USE', message: '쓰는 중인 템플릿이라 지울 수 없어요. 이벤트(또는 필수 안내 메일)의 쓸 템플릿과 특수 상황 규칙에서 먼저 빼 주세요.' });
    }
    const s = state();
    s.templates = s.templates.filter((item) => item.id !== id);
}

export async function mockPreview(can: Can, body: MailPreviewRequest): Promise<MailPreviewView> {
    await read(can);
    const def = body.target ? targetOf(body.target.type, body.target.code) : null;
    const ch = def ? channelDefOf(def, 'EMAIL') : undefined;
    // 레이아웃이 없으면 그 받는 사람의 기본 레이아웃(대상도 없으면 회원 기본)
    const layout = body.layout ?? layoutOf(body.layoutId ?? DEFAULT_LAYOUT_ID[def?.audience ?? 'MEMBER']);
    // 견본 값 — 대상이 있으면 그 이메일 채널의 변수, 없으면(레이아웃 미리보기) 레이아웃 쓰는 대상의 공통 변수
    const variables = ch ? ch.variables : commonVars(layout.audience);
    return UtilMailTemplate.render({
        locale: body.locale,
        subject: body.subject,
        bodyHtml: UtilMailTemplate.cleanHrefs(body.bodyHtml),
        variables,
        layout: { showLogo: layout.showLogo, showSiteName: layout.showSiteName, headerColor: layout.headerColor, footer: layout.footer, frames: layout.frames },
        siteName: SITE_NAME,
        logoUrl: LOGO_URL,
    });
}

export async function mockTest(can: Can, body: MailTemplateTestRequest): Promise<MailTemplateTestResultView> {
    await write(can);
    const to = body.to.trim();
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(to)) throw invalid({ to: '받는 이메일 형식이 올바르지 않아요.' });
    const flags = mockFlags();
    if (flags.has('template-test-limit')) throw new ApiError({ status: 429, code: 'MAIL_TEST_RATE_LIMITED', message: '시험 발송이 너무 잦아요.', retryAfterSeconds: 30 });
    const def = targetOf(body.target.type, body.target.code);
    validateTemplate({ targetType: def.type, targetCode: def.code, channel: 'EMAIL', name: 'test', locale: body.locale, layoutId: body.layoutId ?? DEFAULT_LAYOUT_ID[def.audience], subject: body.subject, body: body.bodyHtml }, def);
    if (flags.has('template-test-fail')) return { succeeded: false, reason: 'CONNECT_FAILED', detail: '메일 서버에 접속하지 못했어요. 서버 주소와 포트를 확인해 주세요.' };
    return { succeeded: true, reason: null, detail: null };
}

/** 채팅 시험 — 실제로 올리지 않는다(목업). 도구는 화면이 실제 알림 도구 목록에서 고른 id 다 */
export async function mockChatTest(can: Can, body: ChatTemplateTestRequest): Promise<ChatTemplateTestResultView> {
    await write(can);
    if (!body.channelId) throw invalid({ channelId: '시험할 알림 도구를 골라 주세요.' });
    const flags = mockFlags();
    if (flags.has('template-test-limit')) throw new ApiError({ status: 429, code: 'MAIL_TEST_RATE_LIMITED', message: '시험이 너무 잦아요.', retryAfterSeconds: 30 });
    const def = targetOf(body.target.type, body.target.code);
    validateTemplate({ targetType: def.type, targetCode: def.code, channel: 'CHAT', name: 'test', locale: body.locale, layoutId: null, subject: '', body: body.body }, def);
    if (flags.has('template-test-fail')) return { ok: false, message: '채널에 글을 올리지 못했어요. 봇 토큰과 채널을 확인해 주세요.' };
    return { ok: true, message: '시험 글을 올렸어요. 채널에서 확인해 보세요.' };
}

export async function mockHistory(can: Can, id: string, page: number, size: number): Promise<TemplateHistoryPage> {
    await read(can);
    if (mockFlags().has('template-history-error')) throw MOCK_ERRORS.internal();
    templateOf(id);
    return pageOf(id, page, size);
}

/* ── API — 레이아웃 ── */

function validateLayout(body: MailLayoutSaveRequest, current: LayoutRow | null = null): void {
    const fields: Record<string, string> = {};
    if (!MAIL_LAYOUT_AUDIENCES.includes(body.audience)) throw invalid({ audience: '쓰는 대상은 회원 · 운영자 · 모두 중 하나예요.' });
    for (const [field, problem] of Object.entries(UtilMailTemplate.validateLayout(body, commonNames(body.audience))) as [MailLayoutField, MailTemplateProblem][]) {
        fields[field] = field === 'name' && problem.code === 'REQUIRED' ? '이름을 입력해 주세요.' : problemText(field, problem);
    }
    if (body.headerColor !== null && !UtilMailTemplate.HEADER_COLORS.includes(body.headerColor)) fields.headerColor = '팔레트의 색 중에서 골라 주세요.';
    if (current && body.audience !== current.audience && body.audience !== 'ANY') {
        const counts = layoutUseCounts(current.id);
        const other: TemplateAudience = body.audience === 'MEMBER' ? 'OPERATOR' : 'MEMBER';
        if (counts[other] > 0) fields.audience = `이 레이아웃을 쓰는 ${AUDIENCE_NAMES[other]} 메일 템플릿이 ${counts[other]}개 있어 쓰는 대상을 바꿀 수 없어요. 그 템플릿의 레이아웃을 먼저 바꿔 주세요.`;
    }
    if (Object.keys(fields).length) throw invalid(fields);
}

function layoutRowOf(body: MailLayoutSaveRequest): Omit<LayoutRow, 'id' | 'builtin' | 'updatedAt' | 'updatedByName'> {
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

const colorName = (color: MailLayoutColor | null) => (color ? COLOR_NAMES[color] : '없음');

export async function mockLayouts(can: Can, query: MailLayoutAudienceQuery = {}): Promise<MailLayoutListItemView[]> {
    await readList(can);
    return clone(
        state()
            .layouts.filter((row) => !query.audience || row.audience === query.audience)
            .map(layoutListItem),
    );
}

export async function mockLayout(can: Can, id: string): Promise<MailLayoutView> {
    await readDetail(can);
    return layoutView(layoutOf(id));
}

export async function mockCreateLayout(can: Can, body: MailLayoutSaveRequest): Promise<MailLayoutView> {
    await write(can);
    const s = state();
    if (s.layouts.filter((item) => !item.builtin).length >= MAIL_LAYOUT_LIMIT) throw new ApiError({ status: 409, code: 'LAYOUT_LIMIT', message: `레이아웃은 ${MAIL_LAYOUT_LIMIT}개까지 만들 수 있어요.` });
    validateLayout(body);
    nextId += 1;
    const created: LayoutRow = { id: `layout-${nextId}`, builtin: false, ...layoutRowOf(body), updatedAt: new Date().toISOString(), updatedByName: ME };
    s.layouts.push(created);
    s.history.unshift(historyRow(created.id, 'CREATE', created.updatedAt as string, ME, [change('name', null, created.name), change('audience', null, AUDIENCE_NAMES[created.audience])]));
    return layoutView(created);
}

export async function mockUpdateLayout(can: Can, id: string, body: MailLayoutSaveRequest): Promise<MailLayoutView> {
    await write(can);
    const row = layoutOf(id);
    if (row.builtin) throw new ApiError({ status: 409, code: 'LAYOUT_BUILTIN', message: '기본 레이아웃은 고칠 수 없어요. 복사해서 만들어 주세요.' });
    validateLayout(body, row);
    const next = layoutRowOf(body);
    const changes: SettingHistoryChange[] = [];
    if (next.name !== row.name) changes.push(change('name', row.name, next.name));
    if (next.audience !== row.audience) changes.push(change('audience', AUDIENCE_NAMES[row.audience], AUDIENCE_NAMES[next.audience]));
    if (next.showLogo !== row.showLogo) changes.push(change('showLogo', String(row.showLogo), String(next.showLogo)));
    if (next.showSiteName !== row.showSiteName) changes.push(change('showSiteName', String(row.showSiteName), String(next.showSiteName)));
    if (next.headerColor !== row.headerColor) changes.push(change('headerColor', colorName(row.headerColor), colorName(next.headerColor)));
    for (const locale of TEMPLATE_LOCALES) {
        const suffix = locale === 'ko' ? 'Ko' : 'En';
        if (next.footer[locale] !== row.footer[locale]) changes.push(change(`footer${suffix}`, row.footer[locale], next.footer[locale]));
        if (next.frames[locale] !== row.frames[locale]) changes.push(change(`frame${suffix}`, excerpt(row.frames[locale]), excerpt(next.frames[locale])));
    }
    if (changes.length === 0) return layoutView(row);
    Object.assign(row, next, { updatedAt: new Date().toISOString(), updatedByName: ME });
    state().history.unshift(historyRow(id, 'UPDATE', row.updatedAt as string, ME, changes));
    return layoutView(row);
}

export async function mockDeleteLayout(can: Can, id: string): Promise<void> {
    await write(can);
    const row = layoutOf(id);
    if (row.builtin) throw new ApiError({ status: 409, code: 'LAYOUT_BUILTIN', message: '기본 레이아웃은 지울 수 없어요.' });
    const count = layoutUseCount(id);
    if (count > 0 || mockFlags().has('layout-in-use')) throw new ApiError({ status: 409, code: 'LAYOUT_IN_USE', message: `이 레이아웃을 쓰는 템플릿이 ${Math.max(count, 1)}개 있어 지울 수 없어요. 템플릿의 레이아웃을 먼저 바꿔 주세요.` });
    const s = state();
    s.layouts = s.layouts.filter((item) => item.id !== id);
}

export async function mockLayoutHistory(can: Can, id: string, page: number, size: number): Promise<MailLayoutHistoryPage> {
    await read(can);
    if (mockFlags().has('template-history-error')) throw MOCK_ERRORS.internal();
    layoutOf(id);
    return pageOf(id, page, size);
}

