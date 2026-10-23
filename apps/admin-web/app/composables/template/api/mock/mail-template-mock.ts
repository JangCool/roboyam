// MOCK: 메일 종류·템플릿·레이아웃 API(`/api/admin/mail-templates/**`·`/api/admin/mail-layouts/**`, 계약 `docs/features/message-template-contract.md` 「개정」·「API 후보」)의 메모리 목업
// — `useMailTemplateApi.ts`·`useMailLayoutApi.ts` 가 이 파일의 함수를 부른다(템플릿이 레이아웃을 가리키므로 상태를 한 곳에 둔다).
// 서버가 붙으면 `template/api/mock/` 과 함께 지운다. 바꾼 것은 메모리에만 남아 **새로고침하면 처음 상태로 돌아간다**.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` (쉼표로 여러 개) ─
//   template-error          목록(메일 종류·템플릿·레이아웃 목록)이 `500 INTERNAL` 로 실패(화면 오류 상태와 다시 시도)
//   template-detail-error   상세(종류 상세·템플릿 하나·레이아웃 하나)가 `500` 으로 실패
//   template-empty          메일 종류가 하나도 없다 — 종류 목록·템플릿 목록이 빈 상태(레이아웃은 기본 레이아웃이 늘 있다)
//   template-denied         `template.read` 가 없다 — 읽기가 모두 `403 PERMISSION_DENIED`(권한 없음 안내)
//   template-readonly       `template.read` 만 있다 — 화면이 읽기 전용, 쓰기는 `403`
//   template-save-error     쓰기(종류 저장·템플릿·레이아웃 만들기·저장·삭제)가 `500` 으로 실패
//   template-in-use         템플릿 지우기가 늘 `409 TEMPLATE_IN_USE`(쓰는 중이라 못 지움 토스트)
//   layout-in-use           레이아웃 지우기가 늘 `409 LAYOUT_IN_USE`
//   template-test-fail      시험 발송이 실패 결과(`CONNECT_FAILED`)
//   template-test-limit     시험 발송이 `429 MAIL_TEST_RATE_LIMITED`(30초)
//   template-history-error  변경 이력(템플릿·레이아웃)이 `500` 으로 실패
//
// ── 흉내 ─
//   종류: 시스템 메일 5종(앞 4종은 보안 메일) + 이메일을 보낼 수 있는 관리자 알림 이벤트 5종(`AdminNotificationEvent` 의 `email()` — 보안 영역 4 · 시스템 영역 1).
//   기본 템플릿 내용은 지금 서버 메일 문구(메시지 번들 `mail.*` · `notification.mail.*`)를 그대로 옮겼다. 알림 이벤트는 지금 한 양식(`NotificationMailTemplate`)이라 이벤트마다 같은 기본 템플릿이다. 기본 템플릿은 모두 기본 레이아웃을 쓴다.
//   사용자 템플릿 4개(인증 메일 「소셜 가입 회원용」 — 규칙에서 씀·보안 안내 레이아웃 · 배치 실패 「긴급 배치 실패」 — 규칙에서 씀·보안 안내 레이아웃 · 초대 「초대 메일(간결)」 — 한국어 기본으로 씀·간단 레이아웃 · 시험 메일 「Plain test」 — 안 씀·기본 레이아웃).
//   레이아웃 4개: 기본 레이아웃(builtin) · 보안 안내(빨강 띠, 템플릿 2개) · 공지(파랑 띠, 인사말 틀, 쓰는 템플릿 없음 — 지울 수 있다) · 간단(로고·사이트 이름·꼬리말 없음, 템플릿 1개).
//   서버 규칙 흉내: 이름·제목·본문 검사(`UtilMailTemplate.validate` — 모르는 변수 · 보안 메일 필수 변수 · 길이 → 400 `VALIDATION` `fields.name|subject|bodyHtml|layoutId`),
//   기본 템플릿·레이아웃 고치기·지우기 409 `TEMPLATE_BUILTIN`·`LAYOUT_BUILTIN`, 쓰는 중 지우기 409 `TEMPLATE_IN_USE`·`LAYOUT_IN_USE`, 종류당 20개·레이아웃 20개 넘게 만들기 409 `TEMPLATE_LIMIT`·`LAYOUT_LIMIT`,
//   직접 쓴 링크의 http·https 아닌 주소 걷기, 레이아웃 검사(`UtilMailTemplate.validateLayout` — `fields.name|footer.ko|footer.en|frames.ko|frames.en|headerColor`),
//   규칙의 조건·템플릿 확인(`fields.rules[0].templateId` …), 시험 받는 주소 형식(`fields.to`).
//   오류 문구는 서버 몫이라 한국어로 둔다(`i18n.md` 5). 변수 설명·조건 이름·기본 템플릿/레이아웃 이름·이력의 값(레이아웃 이름·띠 색 이름)도 서버가 요청 언어로 줄 글이라 한국어로 둔다.

import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { SettingHistoryChange } from '~/composables/common/useSettingHistory.types';
import type { MailLayoutColor, MailLayoutHistoryPage, MailLayoutListItemView, MailLayoutSaveRequest, MailLayoutView } from '~/composables/template/api/useMailLayoutApi.types';
import { MAIL_LAYOUT_LIMIT } from '~/composables/template/api/useMailLayoutApi.types';
import type {
    MailConditionView,
    MailKindDetailView,
    MailKindGroup,
    MailKindSaveRequest,
    MailKindView,
    MailLocale,
    MailPreviewRequest,
    MailPreviewView,
    MailRuleView,
    MailTemplateHistoryItemView,
    MailTemplateHistoryPage,
    MailTemplateListItemView,
    MailTemplateSaveRequest,
    MailTemplateTestRequest,
    MailTemplateTestResultView,
    MailTemplateView,
    MailVariableView,
} from '~/composables/template/api/useMailTemplateApi.types';
import { MAIL_TEMPLATE_LIMIT } from '~/composables/template/api/useMailTemplateApi.types';
import type { MailLayoutField, MailTemplateField, MailTemplateProblem } from '~/utils/util-mail-template';
import UtilMailTemplate from '~/utils/util-mail-template';

type Can = (code: string) => boolean;

/** 목업 스위치가 정하는 내 권한 — 서버 `Permission` 에는 아직 `template.*` 가 없어 최고 관리자만 화면이 보인다(메뉴는 실제 권한을 본다) */
export function mockTemplateCan(can: Can): Can {
    return (code: string) => {
        const flags = mockFlags();
        if (code.startsWith('template.') && flags.has('template-denied')) return false;
        if (code === 'template.manage' && flags.has('template-readonly')) return false;
        return can(code);
    };
}

/* ── 정의(코드가 정하는 것) ── */

const COMMON_VARS: MailVariableView[] = [
    { name: 'siteName', description: '사이트 이름', sample: '로보얌', required: false, link: false },
    { name: 'siteUrl', description: '공개 사이트 주소', sample: 'https://roboyam.example.com', required: false, link: true },
    { name: 'recipientName', description: '받는 사람 이름(없으면 이메일)', sample: '홍길동', required: false, link: false },
];

const v = (name: string, description: string, sample: string, extra: { required?: boolean; link?: boolean } = {}): MailVariableView => ({ name, description, sample, required: extra.required ?? false, link: extra.link ?? false });

const NOTIFICATION_CONDITIONS: MailConditionView[] = [
    {
        key: 'severity',
        label: '심각도',
        options: [
            { value: 'INFO', label: '정보' },
            { value: 'WARNING', label: '경고' },
            { value: 'CRITICAL', label: '긴급' },
        ],
    },
    {
        key: 'recipientLocale',
        label: '받는 운영자 언어',
        options: [
            { value: 'ko', label: '한국어' },
            { value: 'en', label: 'English' },
        ],
    },
];

interface KindDef {
    kind: string;
    group: MailKindGroup;
    area: string | null;
    name: string;
    description: string;
    security: boolean;
    variables: MailVariableView[];
    conditions: MailConditionView[];
    /** 기본 템플릿(언어별) */
    defaults: Record<MailLocale, { subject: string; bodyHtml: string }>;
}

const btn = (link: string, label: string) => `<p data-mail-button=""><a href="{{${link}}}">${label}</a></p>`;

/** 관리자 알림 메일의 한 양식(`NotificationMailTemplate`) — 이벤트마다 같은 기본 템플릿이다 */
const NOTIFICATION_DEFAULTS: KindDef['defaults'] = {
    ko: {
        subject: '{{title}}',
        bodyHtml:
            '<p><strong>{{title}}</strong></p><p>{{body}}</p>' +
            btn('link', '관리자 앱에서 보기') +
            '<p>{{link}}</p><p>관리자 알림 설정에 따라 보낸 메일이에요. 내 정보 &gt; 개인 설정 &gt; 알림 설정에서 알림 메일 수신을 끌 수 있어요.</p>',
    },
    en: {
        subject: '{{title}}',
        bodyHtml:
            '<p><strong>{{title}}</strong></p><p>{{body}}</p>' +
            btn('link', 'Open in the admin app') +
            '<p>{{link}}</p><p>This email was sent according to your admin notification settings. You can turn notification emails off under My info &gt; Personal settings &gt; Notification settings.</p>',
    },
};

function notificationKind(kind: string, area: string, name: string, description: string, title: string, body: string, path: string): KindDef {
    return {
        kind,
        group: 'NOTIFICATION',
        area,
        name,
        description,
        security: false,
        variables: [
            ...COMMON_VARS,
            v('title', '알림 제목', title),
            v('body', '알림 본문', body),
            v('link', '관리자 앱에서 볼 주소', `https://admin.roboyam.example.com${path}`, { link: true }),
            v('occurredAt', '일어난 시각(받는 사람의 시간대)', '2026-10-23 09:30'),
        ],
        conditions: NOTIFICATION_CONDITIONS,
        defaults: NOTIFICATION_DEFAULTS,
    };
}

const KINDS: KindDef[] = [
    {
        kind: 'MEMBER_EMAIL_VERIFY',
        group: 'SYSTEM',
        area: null,
        name: '회원 이메일 인증',
        description: '회원이 가입하거나 이메일을 바꿀 때 인증 링크를 보내요.',
        security: true,
        variables: [...COMMON_VARS, v('nickname', '회원 닉네임', '로보얌이'), v('verifyLink', '이메일 인증 링크', 'https://roboyam.example.com/verify-email?token=sample', { required: true, link: true }), v('validHours', '링크를 쓸 수 있는 시간', '24')],
        conditions: [
            {
                key: 'signupMethod',
                label: '가입 방식',
                options: [
                    { value: 'EMAIL', label: '이메일 가입' },
                    { value: 'SOCIAL', label: '소셜 가입(미확인 이메일)' },
                ],
            },
        ],
        defaults: {
            ko: {
                subject: '이메일 인증을 완료해 주세요',
                bodyHtml:
                    '<p><strong>이메일 인증</strong></p><p>{{nickname}}님, {{siteName}}에 가입해 주셔서 고마워요.</p><p>아래 버튼을 눌러 이메일 인증을 완료해 주세요. 인증하기 전에는 글·댓글·추천·쪽지를 쓸 수 없어요.</p>' +
                    btn('verifyLink', '이메일 인증하기') +
                    '<p>이 링크는 {{validHours}}시간 동안 쓸 수 있어요. 버튼이 안 눌리면 아래 주소를 브라우저에 붙여 넣어 주세요.</p><p>{{verifyLink}}</p><p>본인이 가입한 게 아니라면 이 메일을 무시해 주세요.</p>',
            },
            en: {
                subject: 'Complete your email verification',
                bodyHtml:
                    '<p><strong>Email verification</strong></p><p>Hi {{nickname}}, thanks for joining {{siteName}}.</p><p>Click the button below to verify your email. Until you verify, you cannot post, comment, like, or send messages.</p>' +
                    btn('verifyLink', 'Verify email') +
                    '<p>This link is valid for {{validHours}} hours. If the button does not work, paste the address below into your browser.</p><p>{{verifyLink}}</p><p>If you did not sign up, you can ignore this email.</p>',
            },
        },
    },
    {
        kind: 'OPERATOR_INVITE',
        group: 'SYSTEM',
        area: null,
        name: '운영자 초대',
        description: '관리자 앱 운영자로 초대할 때 수락 링크를 보내요.',
        security: true,
        variables: [...COMMON_VARS, v('inviteLink', '초대 수락 링크', 'https://admin.roboyam.example.com/invitation?token=sample', { required: true, link: true }), v('validHours', '링크를 쓸 수 있는 시간', '72')],
        conditions: [
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
        defaults: {
            ko: {
                subject: '관리자 앱 운영자로 초대됐어요',
                bodyHtml:
                    '<p><strong>운영자 초대</strong></p><p>{{siteName}}의 관리자 앱 운영자로 초대됐어요.</p><p>아래 버튼을 눌러 이름과 비밀번호를 정하면 계정이 만들어져요.</p>' +
                    btn('inviteLink', '초대 수락하기') +
                    '<p>이 링크는 {{validHours}}시간 동안 쓸 수 있어요. 버튼이 안 눌리면 아래 주소를 브라우저에 붙여 넣어 주세요.</p><p>{{inviteLink}}</p><p>초대받을 이유가 없다면 이 메일을 무시해 주세요.</p>',
            },
            en: {
                subject: 'You have been invited as an admin app operator',
                bodyHtml:
                    '<p><strong>Operator invitation</strong></p><p>You have been invited as an operator of the {{siteName}} admin app.</p><p>Click the button below and choose your name and password to create your account.</p>' +
                    btn('inviteLink', 'Accept invitation') +
                    '<p>This link is valid for {{validHours}} hours. If the button does not work, paste the address below into your browser.</p><p>{{inviteLink}}</p><p>If you have no reason to be invited, you can ignore this email.</p>',
            },
        },
    },
    {
        kind: 'OPERATOR_CREDENTIALS',
        group: 'SYSTEM',
        area: null,
        name: '운영자 임시 비밀번호',
        description: '운영자를 직접 추가할 때 임시 비밀번호를 보내요.',
        security: true,
        variables: [...COMMON_VARS, v('operatorName', '운영자 이름', '박운영'), v('temporaryPassword', '임시 비밀번호', 'Tmp-8f3K2q', { required: true }), v('adminUrl', '관리자 앱 주소', 'https://admin.roboyam.example.com', { link: true })],
        conditions: [],
        defaults: {
            ko: {
                subject: '관리자 앱 운영자 계정이 만들어졌어요',
                bodyHtml:
                    '<p><strong>운영자 계정 안내</strong></p><p>{{operatorName}}님, {{siteName}}의 관리자 앱 운영자 계정이 만들어졌어요.</p><p>이 메일을 받은 주소와 아래 임시 비밀번호로 로그인해 주세요.</p><p>임시 비밀번호: <strong>{{temporaryPassword}}</strong></p><p>관리자 앱 주소: {{adminUrl}}</p><p>이 메일을 다른 사람에게 전달하지 마세요.</p>',
            },
            en: {
                subject: 'Your admin app operator account has been created',
                bodyHtml:
                    '<p><strong>Operator account</strong></p><p>Hi {{operatorName}}, an operator account for the {{siteName}} admin app has been created.</p><p>Log in with the address that received this email and the temporary password below.</p><p>Temporary password: <strong>{{temporaryPassword}}</strong></p><p>Admin app address: {{adminUrl}}</p><p>Do not forward this email to anyone.</p>',
            },
        },
    },
    {
        kind: 'OPERATOR_RESET_CODE',
        group: 'SYSTEM',
        area: null,
        name: '운영자 비밀번호 재설정 승인번호',
        description: '운영자가 비밀번호 재설정을 요청하면 승인번호를 보내요.',
        security: true,
        variables: [...COMMON_VARS, v('code', '승인번호', '482913', { required: true }), v('validMinutes', '승인번호를 쓸 수 있는 시간(분)', '10')],
        conditions: [],
        defaults: {
            ko: {
                subject: '관리자 앱 비밀번호 재설정 승인번호',
                bodyHtml:
                    '<p><strong>비밀번호 재설정 승인번호</strong></p><p>{{siteName}} 관리자 앱의 비밀번호 재설정을 요청하셨어요.</p><p>아래 승인번호를 비밀번호 재설정 화면에 입력해 새 비밀번호를 정해 주세요.</p><p>승인번호: <strong>{{code}}</strong></p><p>이 승인번호는 {{validMinutes}}분 동안 쓸 수 있고, 다시 받으면 이전 번호는 쓸 수 없어요.</p><p>승인번호를 다른 사람에게 알려 주지 마세요.</p><p>비밀번호 재설정을 요청하지 않았다면 이 메일을 무시해 주세요. 비밀번호는 바뀌지 않아요.</p>',
            },
            en: {
                subject: 'Admin app password reset verification code',
                bodyHtml:
                    '<p><strong>Password reset verification code</strong></p><p>A password reset was requested for the {{siteName}} admin app.</p><p>Enter the verification code below on the password reset page to choose a new password.</p><p>Verification code: <strong>{{code}}</strong></p><p>This code is valid for {{validMinutes}} minutes. If you request a new one, the previous code stops working.</p><p>Do not share this code with anyone.</p><p>If you did not request a password reset, you can ignore this email. Your password has not changed.</p>',
            },
        },
    },
    {
        kind: 'MAIL_TEST',
        group: 'SYSTEM',
        area: null,
        name: '메일 서버 시험',
        description: '메일 서버 설정의 「시험 발송」이 보내요.',
        security: false,
        variables: [...COMMON_VARS],
        conditions: [],
        defaults: {
            ko: { subject: '메일 서버 설정 시험 메일', bodyHtml: '<p>이 메일이 보이면 관리자 앱의 메일 서버 설정으로 메일이 잘 나가고 있는 거예요.</p>' },
            en: { subject: 'Mail server settings test', bodyHtml: '<p>If you can see this email, mail is being sent correctly with the mail server settings of the admin app.</p>' },
        },
    },
    notificationKind('OPERATOR_STATUS_CHANGED_ME', 'security', '내 계정 상태 변경', '내 계정이 잠기거나 사용 중지·승인·계정 기간이 바뀌면 본인에게 보내요.', '내 계정 상태가 바뀌었어요', '계정 기간이 2026-12-31 까지로 바뀌었어요.', '/me'),
    notificationKind('OPERATOR_LOCKED_BY_FAILURES', 'security', '로그인 실패로 계정 잠금', '로그인 실패가 이어져 계정이 잠기는 순간 잠금 권한이 있는 운영자에게 보내요.', '로그인 실패로 계정이 잠겼어요', '박운영 님의 계정이 로그인 실패가 이어져 잠겼어요. 일정 시간이 지나면 저절로 풀려요.', '/settings/operators'),
    notificationKind('PASSWORD_CHANGED_ME', 'security', '내 비밀번호 변경', '내 계정의 비밀번호가 바뀌면 본인에게 보내요.', '비밀번호가 변경됐어요', '내 계정의 비밀번호가 변경됐어요. 직접 바꾼 것이 아니라면 바로 관리자에게 알려 주세요.', '/me?tab=password'),
    notificationKind('PASSWORD_RESET_DONE_ME', 'security', '내 비밀번호 재설정', '승인번호로 비밀번호가 재설정되면 본인에게 보내요.', '비밀번호가 재설정됐어요', '승인번호로 내 계정의 비밀번호가 재설정됐어요. 직접 한 것이 아니라면 바로 관리자에게 알려 주세요.', '/me?tab=password'),
    notificationKind('BATCH_RUN_FAILED', 'system', '배치 실패', '배치가 실패하면 배치 조회 권한이 있는 운영자에게 보내요(같은 배치는 30분에 한 번).', '배치가 실패했어요', 'grade-promotion 배치가 실패했어요. DB 연결 시간이 지났어요.', '/settings/batch-logs?jobName=grade-promotion'),
];

/** 목업의 「나」 — 저장·이력의 운영자 이름 */
const ME = '김관리';

/* ── 레이아웃 정의 ── */

const SITE_NAME = '로보얌';
/** 로고는 사이트 설정 「일반」의 것 — 목업은 로고가 없는 사이트로 둔다 */
const LOGO_URL: string | null = null;
const CONTENT_VAR: MailVariableView = { name: 'content', description: '메일 본문이 들어갈 자리(지울 수 없어요)', sample: '', required: true, link: false };
const LAYOUT_VARS: MailVariableView[] = [...COMMON_VARS, CONTENT_VAR];
const COMMON_NAMES = COMMON_VARS.map((item) => item.name);
const BUILTIN_LAYOUT_ID = 'layout-default';
/** 이력에 남기는 띠 색 이름 — 서버가 요청 언어로 줄 글(목업은 한국어) */
const COLOR_NAMES: Record<MailLayoutColor, string> = { BLUE: '파랑', INDIGO: '남색', TEAL: '청록', GREEN: '초록', AMBER: '호박', ORANGE: '주황', RED: '빨강', SLATE: '먹색' };

interface LayoutRow {
    id: string;
    name: string;
    builtin: boolean;
    showLogo: boolean;
    showSiteName: boolean;
    headerColor: MailLayoutColor | null;
    footer: Record<MailLocale, string>;
    frames: Record<MailLocale, string>;
    updatedAt: string | null;
    updatedByName: string | null;
}

const PLAIN_FRAMES: Record<MailLocale, string> = { ko: '<p>{{content}}</p>', en: '<p>{{content}}</p>' };

function initialLayouts(): LayoutRow[] {
    return [
        {
            id: BUILTIN_LAYOUT_ID,
            // 이름은 서버가 요청 언어로 준다(목업은 한국어)
            name: '기본 레이아웃',
            builtin: true,
            showLogo: true,
            showSiteName: true,
            headerColor: null,
            footer: { ko: '이 메일은 발신 전용이에요. 회신하지 마세요.', en: 'This email is send-only. Please do not reply.' },
            frames: { ...PLAIN_FRAMES },
            updatedAt: null,
            updatedByName: null,
        },
        {
            id: 'layout-security',
            name: '보안 안내',
            builtin: false,
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

interface KindState {
    selected: Record<MailLocale, string>;
    rules: MailRuleView[];
}

/** 이력 한 줄 — 템플릿 이력과 레이아웃 이력을 한 곳에 두고 `targetId` 로 가른다 */
interface HistoryRow extends MailTemplateHistoryItemView {
    targetId: string;
}

interface Store {
    templates: MailTemplateView[];
    kinds: Map<string, KindState>;
    layouts: LayoutRow[];
    history: HistoryRow[];
}

let store: Store | null = null;
let nextId = 200;

const builtinId = (kind: string, locale: MailLocale) => `builtin-${kind}-${locale}`;
const LOCALES: readonly MailLocale[] = ['ko', 'en'];

function builtins(): MailTemplateView[] {
    return KINDS.flatMap((def) =>
        LOCALES.map((locale) => ({
            id: builtinId(def.kind, locale),
            kind: def.kind,
            // 이름은 서버가 요청 언어로 준다(목업은 한국어)
            name: '기본 템플릿',
            locale,
            layoutId: BUILTIN_LAYOUT_ID,
            subject: def.defaults[locale].subject,
            bodyHtml: def.defaults[locale].bodyHtml,
            builtin: true,
            updatedAt: null,
            updatedByName: null,
        })),
    );
}

function state(): Store {
    if (store) return store;
    const verify = KINDS[0] as KindDef;
    const user: MailTemplateView[] = [
        {
            id: 'tpl-101',
            kind: 'MEMBER_EMAIL_VERIFY',
            name: '소셜 가입 회원용',
            locale: 'ko',
            layoutId: 'layout-security',
            subject: '받은 이메일이 맞는지 확인해 주세요',
            bodyHtml: verify.defaults.ko.bodyHtml.replace('{{nickname}}님, {{siteName}}에 가입해 주셔서 고마워요.', '{{nickname}}님, 소셜 계정으로 {{siteName}}에 가입하셨네요. 소셜 계정의 이메일이 맞는지 한 번만 확인해 주세요.'),
            builtin: false,
            updatedAt: '2026-10-20T05:12:00Z',
            updatedByName: ME,
        },
        {
            id: 'tpl-102',
            kind: 'BATCH_RUN_FAILED',
            name: '긴급 배치 실패',
            locale: 'ko',
            layoutId: 'layout-security',
            subject: '[긴급] {{title}}',
            bodyHtml: '<p><strong>[긴급] {{title}}</strong></p><p>{{body}}</p><p>{{occurredAt}} 에 일어났어요. 바로 확인해 주세요.</p>' + btn('link', '배치 로그 보기') + '<p>{{link}}</p>',
            builtin: false,
            updatedAt: '2026-10-21T01:40:00Z',
            updatedByName: '박운영',
        },
        {
            id: 'tpl-103',
            kind: 'OPERATOR_INVITE',
            name: '초대 메일(간결)',
            locale: 'ko',
            layoutId: 'layout-simple',
            subject: '{{siteName}} 관리자 앱 초대',
            bodyHtml: '<p>{{siteName}} 관리자 앱 운영자로 초대됐어요. {{validHours}}시간 안에 수락해 주세요.</p>' + btn('inviteLink', '초대 수락하기') + '<p>{{inviteLink}}</p>',
            builtin: false,
            updatedAt: '2026-10-22T08:05:00Z',
            updatedByName: ME,
        },
        {
            id: 'tpl-104',
            kind: 'MAIL_TEST',
            name: 'Plain test',
            locale: 'en',
            layoutId: BUILTIN_LAYOUT_ID,
            subject: 'Test mail from {{siteName}}',
            bodyHtml: '<p>This is a test mail from {{siteName}}.</p>',
            builtin: false,
            updatedAt: '2026-10-22T10:30:00Z',
            updatedByName: ME,
        },
    ];
    const kinds = new Map<string, KindState>();
    for (const def of KINDS) kinds.set(def.kind, { selected: { ko: builtinId(def.kind, 'ko'), en: builtinId(def.kind, 'en') }, rules: [] });
    (kinds.get('MEMBER_EMAIL_VERIFY') as KindState).rules = [{ id: 'rule-1', condition: { key: 'signupMethod', value: 'SOCIAL' }, templateId: 'tpl-101' }];
    (kinds.get('BATCH_RUN_FAILED') as KindState).rules = [{ id: 'rule-2', condition: { key: 'severity', value: 'CRITICAL' }, templateId: 'tpl-102' }];
    (kinds.get('OPERATOR_INVITE') as KindState).selected.ko = 'tpl-103';

    const history: HistoryRow[] = [
        historyRow('tpl-101', 'UPDATE', '2026-10-20T05:12:00Z', ME, [change('subject', '이메일 인증을 완료해 주세요', '받은 이메일이 맞는지 확인해 주세요'), change('layoutId', '기본 레이아웃', '보안 안내')]),
        historyRow('tpl-101', 'CREATE', '2026-10-19T23:58:00Z', ME, [change('name', null, '소셜 가입 회원용'), change('locale', null, 'ko'), change('layoutId', null, '기본 레이아웃')]),
        historyRow('tpl-102', 'CREATE', '2026-10-21T01:40:00Z', '박운영', [change('name', null, '긴급 배치 실패'), change('locale', null, 'ko'), change('layoutId', null, '보안 안내')]),
        historyRow('tpl-103', 'CREATE', '2026-10-22T08:05:00Z', ME, [change('name', null, '초대 메일(간결)'), change('locale', null, 'ko'), change('layoutId', null, '간단')]),
        historyRow('tpl-104', 'CREATE', '2026-10-22T10:30:00Z', ME, [change('name', null, 'Plain test'), change('locale', null, 'en'), change('layoutId', null, '기본 레이아웃')]),
        historyRow('layout-security', 'UPDATE', '2026-10-18T02:10:00Z', ME, [change('headerColor', '주황', '빨강')]),
        historyRow('layout-security', 'CREATE', '2026-10-17T09:00:00Z', ME, [change('name', null, '보안 안내')]),
        historyRow('layout-notice', 'CREATE', '2026-10-19T07:45:00Z', '박운영', [change('name', null, '공지')]),
        historyRow('layout-simple', 'CREATE', '2026-10-20T00:30:00Z', ME, [change('name', null, '간단')]),
    ];

    store = { templates: [...builtins(), ...user], kinds, layouts: initialLayouts(), history };
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
const kindNotFound = () => new ApiError({ status: 404, code: 'MAIL_KIND_NOT_FOUND', message: '메일 종류를 찾을 수 없어요.' });
const layoutNotFound = () => new ApiError({ status: 404, code: 'LAYOUT_NOT_FOUND', message: '레이아웃을 찾을 수 없어요.' });
const invalid = (fields: Record<string, string>) => new ApiError({ status: 400, code: 'VALIDATION', message: '입력값을 확인해 주세요.', fields });

function defOf(kind: string): KindDef {
    const def = KINDS.find((item) => item.kind === kind);
    if (!def) throw kindNotFound();
    return def;
}

function templateOf(id: string): MailTemplateView {
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

/** 목록 화면(종류·템플릿·레이아웃 목록)의 읽기 */
async function readList(can: Can): Promise<void> {
    await read(can);
    if (mockFlags().has('template-error')) throw MOCK_ERRORS.internal();
}

/** 상세·편집 화면(종류 상세·템플릿·레이아웃 하나)의 읽기 */
async function readDetail(can: Can): Promise<void> {
    await read(can);
    if (mockFlags().has('template-detail-error')) throw MOCK_ERRORS.internal();
}

const clone = <T>(value: T): T => JSON.parse(JSON.stringify(value)) as T;

/** 이 템플릿을 쓰는 곳 — 언어별 기본 또는 규칙 */
function usageOf(template: MailTemplateView): { defaultFor: MailLocale[]; ruleCount: number } {
    const kind = state().kinds.get(template.kind);
    if (!kind) return { defaultFor: [], ruleCount: 0 };
    return { defaultFor: LOCALES.filter((locale) => kind.selected[locale] === template.id), ruleCount: kind.rules.filter((rule) => rule.templateId === template.id).length };
}

function inUse(template: MailTemplateView): boolean {
    const usage = usageOf(template);
    return usage.defaultFor.length > 0 || usage.ruleCount > 0;
}

function layoutUseCount(id: string): number {
    return state().templates.filter((item) => item.layoutId === id).length;
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
            return `이 메일에서 쓸 수 없는 변수예요: ${names}`;
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

function validateTemplate(body: MailTemplateSaveRequest, def: KindDef): void {
    const problems = UtilMailTemplate.validate(body, def);
    const fields: Record<string, string> = {};
    for (const [field, problem] of Object.entries(problems) as [MailTemplateField, MailTemplateProblem][]) {
        // 버튼 문제는 서버에서는 본문 칸의 문제다
        const key = field === 'button' ? 'bodyHtml' : field;
        if (!fields[key]) fields[key] = problemText(field, problem);
    }
    if (body.locale !== 'ko' && body.locale !== 'en') fields.locale = '언어는 ko · en 중 하나예요.';
    if (!state().layouts.some((item) => item.id === body.layoutId)) fields.layoutId = '레이아웃을 골라 주세요.';
    if (Object.keys(fields).length) throw invalid(fields);
}

function kindView(def: KindDef): MailKindView {
    const s = state();
    const kind = s.kinds.get(def.kind) as KindState;
    const pick = (locale: MailLocale) => {
        const template = s.templates.find((item) => item.id === kind.selected[locale]) ?? templateOf(builtinId(def.kind, locale));
        return { id: template.id, name: template.name, builtin: template.builtin };
    };
    return {
        kind: def.kind,
        group: def.group,
        area: def.area,
        name: def.name,
        description: def.description,
        security: def.security,
        variables: def.variables,
        conditions: def.conditions,
        selected: { ko: pick('ko'), en: pick('en') },
        ruleCount: kind.rules.length,
        templateCount: s.templates.filter((item) => item.kind === def.kind && !item.builtin).length,
    };
}

function detailView(def: KindDef): MailKindDetailView {
    const s = state();
    const kind = s.kinds.get(def.kind) as KindState;
    return clone({
        kind: def.kind,
        summary: kindView(def),
        defaults: { ko: builtinId(def.kind, 'ko'), en: builtinId(def.kind, 'en') },
        selected: kind.selected,
        rules: kind.rules,
        templates: s.templates.filter((item) => item.kind === def.kind),
    });
}

function listItem(template: MailTemplateView): MailTemplateListItemView {
    const def = defOf(template.kind);
    const layout = state().layouts.find((item) => item.id === template.layoutId);
    const { bodyHtml: _body, subject: _subject, ...rest } = template;
    return { ...rest, kindName: def.name, security: def.security, layoutName: layout?.name ?? '', ...usageOf(template) };
}

function layoutListItem(row: LayoutRow): MailLayoutListItemView {
    return { id: row.id, name: row.name, builtin: row.builtin, headerColor: row.headerColor, templateCount: layoutUseCount(row.id), updatedAt: row.updatedAt, updatedByName: row.updatedByName };
}

function layoutView(row: LayoutRow): MailLayoutView {
    return clone({ ...row, templateCount: layoutUseCount(row.id), logoUrl: LOGO_URL, siteName: SITE_NAME, variables: LAYOUT_VARS });
}

/** 이력의 본문 값 — 글자 요약 80자(가정) */
function excerpt(html: string): string {
    const text = UtilMailTemplate.plainText(html);
    return text.length > 80 ? `${text.slice(0, 80)}…` : text;
}

function pageOf(targetId: string, page: number, size: number): MailTemplateHistoryPage {
    const rows = state().history.filter((row) => row.targetId === targetId);
    const items = rows.slice(page * size, page * size + size).map(({ targetId: _omit, ...rest }) => rest);
    return clone({ items, total: rows.length, page, size });
}

/* ── API — 메일 종류 ── */

export async function mockKinds(can: Can): Promise<MailKindView[]> {
    await readList(can);
    if (mockFlags().has('template-empty')) return [];
    return clone(KINDS.map(kindView));
}

export async function mockKindDetail(can: Can, kind: string): Promise<MailKindDetailView> {
    await readDetail(can);
    return detailView(defOf(kind));
}

export async function mockSaveKind(can: Can, kind: string, body: MailKindSaveRequest): Promise<MailKindDetailView> {
    await write(can);
    const def = defOf(kind);
    const s = state();
    const fields: Record<string, string> = {};
    for (const locale of LOCALES) {
        const template = s.templates.find((item) => item.id === body.selected[locale]);
        if (!template || template.kind !== kind) fields[`selected.${locale}`] = '이 메일 종류의 템플릿을 골라 주세요.';
        else if (template.locale !== locale) fields[`selected.${locale}`] = '고른 언어의 템플릿만 쓸 수 있어요.';
    }
    body.rules.forEach((rule, at) => {
        const condition = def.conditions.find((item) => item.key === rule.condition.key);
        if (!condition || !condition.options.some((option) => option.value === rule.condition.value)) fields[`rules[${at}].condition`] = '이 메일 종류에서 고를 수 있는 조건이 아니에요.';
        const template = s.templates.find((item) => item.id === rule.templateId);
        if (!template || template.kind !== kind) fields[`rules[${at}].templateId`] = '이 메일 종류의 템플릿을 골라 주세요.';
    });
    if (body.rules.length > 10) fields.rules = '규칙은 10개까지 둘 수 있어요.';
    if (Object.keys(fields).length) throw invalid(fields);
    const target = s.kinds.get(kind) as KindState;
    target.selected = { ...body.selected };
    target.rules = body.rules.map((rule) => ({ id: rule.id ?? `rule-${(nextId += 1)}`, condition: { ...rule.condition }, templateId: rule.templateId }));
    return detailView(def);
}

/* ── API — 템플릿 ── */

export async function mockTemplates(can: Can): Promise<MailTemplateListItemView[]> {
    await readList(can);
    if (mockFlags().has('template-empty')) return [];
    return clone(state().templates.map(listItem));
}

export async function mockTemplate(can: Can, id: string): Promise<MailTemplateView> {
    await readDetail(can);
    return clone(templateOf(id));
}

export async function mockCreateTemplate(can: Can, body: MailTemplateSaveRequest): Promise<MailTemplateView> {
    await write(can);
    const def = defOf(body.kind);
    const s = state();
    if (s.templates.filter((item) => item.kind === body.kind && !item.builtin).length >= MAIL_TEMPLATE_LIMIT) {
        throw new ApiError({ status: 409, code: 'TEMPLATE_LIMIT', message: `한 메일 종류에 템플릿은 ${MAIL_TEMPLATE_LIMIT}개까지 만들 수 있어요.` });
    }
    validateTemplate(body, def);
    nextId += 1;
    const created: MailTemplateView = {
        id: `tpl-${nextId}`,
        kind: body.kind,
        name: body.name.trim(),
        locale: body.locale,
        layoutId: body.layoutId,
        subject: body.subject,
        bodyHtml: UtilMailTemplate.cleanHrefs(body.bodyHtml),
        builtin: false,
        updatedAt: new Date().toISOString(),
        updatedByName: ME,
    };
    s.templates.push(created);
    s.history.unshift(historyRow(created.id, 'CREATE', created.updatedAt as string, ME, [change('name', null, created.name), change('locale', null, created.locale), change('layoutId', null, layoutOf(created.layoutId).name)]));
    return clone(created);
}

export async function mockUpdateTemplate(can: Can, id: string, body: MailTemplateSaveRequest): Promise<MailTemplateView> {
    await write(can);
    const template = templateOf(id);
    if (template.builtin) throw new ApiError({ status: 409, code: 'TEMPLATE_BUILTIN', message: '기본 템플릿은 고칠 수 없어요. 복사해서 만들어 주세요.' });
    const def = defOf(template.kind);
    validateTemplate({ ...body, kind: template.kind }, def);
    const next = { name: body.name.trim(), locale: body.locale, layoutId: body.layoutId, subject: body.subject, bodyHtml: UtilMailTemplate.cleanHrefs(body.bodyHtml) };
    // 언어를 바꾸면 그 템플릿을 고른 언어의 「기본으로 쓸 템플릿」과 맞지 않는다 — 서버 흉내로 거절한다
    const kind = state().kinds.get(template.kind) as KindState;
    if (next.locale !== template.locale && kind.selected[template.locale] === template.id) {
        throw invalid({ locale: '이 언어의 기본으로 쓰는 중이라 언어를 바꿀 수 없어요.' });
    }
    const changes: SettingHistoryChange[] = [];
    if (next.name !== template.name) changes.push(change('name', template.name, next.name));
    if (next.locale !== template.locale) changes.push(change('locale', template.locale, next.locale));
    if (next.layoutId !== template.layoutId) changes.push(change('layoutId', layoutOf(template.layoutId).name, layoutOf(next.layoutId).name));
    if (next.subject !== template.subject) changes.push(change('subject', template.subject, next.subject));
    if (next.bodyHtml !== template.bodyHtml) changes.push(change('bodyHtml', excerpt(template.bodyHtml), excerpt(next.bodyHtml)));
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
        throw new ApiError({ status: 409, code: 'TEMPLATE_IN_USE', message: '쓰는 중인 템플릿이라 지울 수 없어요. 메일 종류의 쓰는 템플릿과 특수한 상황 규칙에서 먼저 빼 주세요.' });
    }
    const s = state();
    s.templates = s.templates.filter((item) => item.id !== id);
}

export async function mockPreview(can: Can, body: MailPreviewRequest): Promise<MailPreviewView> {
    await read(can);
    const variables = body.kind ? defOf(body.kind).variables : COMMON_VARS;
    const layout = body.layout ?? layoutOf(body.layoutId ?? BUILTIN_LAYOUT_ID);
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
    if (body.templateId) templateOf(body.templateId);
    else validateTemplate({ kind: body.kind, name: 'test', locale: body.locale, layoutId: body.layoutId ?? BUILTIN_LAYOUT_ID, subject: body.subject ?? '', bodyHtml: body.bodyHtml ?? '' }, defOf(body.kind));
    if (flags.has('template-test-fail')) return { succeeded: false, reason: 'CONNECT_FAILED', detail: '메일 서버에 접속하지 못했어요. 서버 주소와 포트를 확인해 주세요.' };
    return { succeeded: true, reason: null, detail: null };
}

export async function mockHistory(can: Can, id: string, page: number, size: number): Promise<MailTemplateHistoryPage> {
    await read(can);
    if (mockFlags().has('template-history-error')) throw MOCK_ERRORS.internal();
    templateOf(id);
    return pageOf(id, page, size);
}

/* ── API — 레이아웃 ── */

function validateLayout(body: MailLayoutSaveRequest): void {
    const fields: Record<string, string> = {};
    for (const [field, problem] of Object.entries(UtilMailTemplate.validateLayout(body, COMMON_NAMES)) as [MailLayoutField, MailTemplateProblem][]) {
        fields[field] = field === 'name' && problem.code === 'REQUIRED' ? '이름을 입력해 주세요.' : problemText(field, problem);
    }
    if (body.headerColor !== null && !UtilMailTemplate.HEADER_COLORS.includes(body.headerColor)) fields.headerColor = '팔레트의 색 중에서 골라 주세요.';
    if (Object.keys(fields).length) throw invalid(fields);
}

function layoutRowOf(body: MailLayoutSaveRequest): Omit<LayoutRow, 'id' | 'builtin' | 'updatedAt' | 'updatedByName'> {
    return {
        name: body.name.trim(),
        showLogo: body.showLogo,
        showSiteName: body.showSiteName,
        headerColor: body.headerColor,
        footer: { ko: body.footer.ko.trim(), en: body.footer.en.trim() },
        frames: { ko: UtilMailTemplate.cleanHrefs(body.frames.ko), en: UtilMailTemplate.cleanHrefs(body.frames.en) },
    };
}

const colorName = (color: MailLayoutColor | null) => (color ? COLOR_NAMES[color] : '없음');

export async function mockLayouts(can: Can): Promise<MailLayoutListItemView[]> {
    await readList(can);
    return clone(state().layouts.map(layoutListItem));
}

export async function mockLayout(can: Can, id: string): Promise<MailLayoutView> {
    await readDetail(can);
    return layoutView(layoutOf(id));
}

export async function mockCreateLayout(can: Can, body: MailLayoutSaveRequest): Promise<MailLayoutView> {
    await write(can);
    const s = state();
    if (s.layouts.filter((item) => !item.builtin).length >= MAIL_LAYOUT_LIMIT) {
        throw new ApiError({ status: 409, code: 'LAYOUT_LIMIT', message: `레이아웃은 ${MAIL_LAYOUT_LIMIT}개까지 만들 수 있어요.` });
    }
    validateLayout(body);
    nextId += 1;
    const created: LayoutRow = { id: `layout-${nextId}`, builtin: false, ...layoutRowOf(body), updatedAt: new Date().toISOString(), updatedByName: ME };
    s.layouts.push(created);
    s.history.unshift(historyRow(created.id, 'CREATE', created.updatedAt as string, ME, [change('name', null, created.name)]));
    return layoutView(created);
}

export async function mockUpdateLayout(can: Can, id: string, body: MailLayoutSaveRequest): Promise<MailLayoutView> {
    await write(can);
    const row = layoutOf(id);
    if (row.builtin) throw new ApiError({ status: 409, code: 'LAYOUT_BUILTIN', message: '기본 레이아웃은 고칠 수 없어요. 복사해서 만들어 주세요.' });
    validateLayout(body);
    const next = layoutRowOf(body);
    const changes: SettingHistoryChange[] = [];
    if (next.name !== row.name) changes.push(change('name', row.name, next.name));
    if (next.showLogo !== row.showLogo) changes.push(change('showLogo', String(row.showLogo), String(next.showLogo)));
    if (next.showSiteName !== row.showSiteName) changes.push(change('showSiteName', String(row.showSiteName), String(next.showSiteName)));
    if (next.headerColor !== row.headerColor) changes.push(change('headerColor', colorName(row.headerColor), colorName(next.headerColor)));
    for (const locale of LOCALES) {
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
    if (count > 0 || mockFlags().has('layout-in-use')) {
        throw new ApiError({ status: 409, code: 'LAYOUT_IN_USE', message: `이 레이아웃을 쓰는 템플릿이 ${Math.max(count, 1)}개 있어 지울 수 없어요. 템플릿의 레이아웃을 먼저 바꿔 주세요.` });
    }
    const s = state();
    s.layouts = s.layouts.filter((item) => item.id !== id);
}

export async function mockLayoutHistory(can: Can, id: string, page: number, size: number): Promise<MailLayoutHistoryPage> {
    await read(can);
    if (mockFlags().has('template-history-error')) throw MOCK_ERRORS.internal();
    layoutOf(id);
    return pageOf(id, page, size);
}
