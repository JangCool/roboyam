/**
 * 알림 도구(채널) API 가 주고받는 모양 — 후보 계약이고 확정 전이다(목업 단계, `docs/features/notification-contract.md` 「알림 도구·조직 구성」).
 * 칸 이름은 hamonica3 `useNotificationApi.ts`(`ConfigView`·`SaveConfigRequest`)와 서버 `ChannelSpec` 을 따르되 이 제품의 말로 바꿨다.
 * 비밀(Slack·Mattermost 봇 토큰, Mattermost 웹훅 주소)은 **서버가 응답에서 지운다** — 화면은 「입력됨」(`secretKeys`)만 안다. ⚠ ID 는 문자열이다(Snowflake). 숫자로 바꾸지 않는다.
 */

/**
 * 알림 도구의 종류. `DRAWER`(앱 안 종 알림 서랍 — 항상 켜진 기본 도구) · `EMAIL`(설정 > 메일 서버 설정으로 발송) · `BROWSER`(브라우저 알림 — 받을 것이 없다)는 기본 제공이라 설정 칸이 없다.
 * `SLACK` · `MATTERMOST` 가 사용자가 만드는 도구다. `KAKAO` · `SMS` 는 업체 계약 전이라 **새로 만들 수 없고** 종류 선택지에 「준비 중」으로만 보인다(그래서 도구 목록에는 나오지 않는다).
 */
export type NotificationChannelType = 'DRAWER' | 'EMAIL' | 'BROWSER' | 'SLACK' | 'MATTERMOST' | 'KAKAO' | 'SMS';

/** Mattermost 연결 방식 — 웹훅 주소 하나로 보내거나(`webhook`), 서버 주소 + 봇 토큰으로 보낸다(`bot`) */
export type MattermostMode = 'webhook' | 'bot';

/** 비밀이 아닌 설정 값 — 응답에도 그대로 실린다. 종류마다 쓰는 칸이 다르다(`UtilNotificationChannel.requiredKeys`) */
export interface NotificationChannelConfig {
    /** Slack: `#ops-alerts` 같은 채널 · Mattermost(봇 방식): 채널 이름 */
    channel?: string;
    /** Mattermost 연결 방식 */
    mattermostMode?: MattermostMode;
    /** Mattermost(봇 방식) 서버 주소 */
    serverUrl?: string;
}

/** 비밀 값 — 요청에만 실린다. 고칠 때 **보내지 않으면 저장된 값을 그대로 쓴다**(빈 문자열도 보내지 않는다) */
export interface NotificationChannelSecrets {
    /** Slack · Mattermost(봇 방식) */
    botToken?: string;
    /** Mattermost(웹훅 방식) */
    webhookUrl?: string;
}

/** 서버에 비밀이 저장돼 있다는 표시에 쓰는 칸 이름 */
export type NotificationSecretKey = keyof NotificationChannelSecrets;

/** 연결 시험 한 번의 결과 — 시험하면 서버가 마지막 결과를 도구에 남긴다 */
export interface NotificationChannelTestResult {
    ok: boolean;
    /** 서버 문구(성공 안내 또는 실패 사유). 화면은 가공하지 않고 그대로 보인다 */
    message: string;
    /** 시험한 시각 — UTC ISO */
    testedAt: string;
}

/** 알림 도구 한 개 — `GET /api/admin/notification-channels` (권한 `notification.read`) */
export interface NotificationChannelView {
    channelId: string;
    type: NotificationChannelType;
    /** 도구 이름. 기본 제공 도구(`builtIn`)의 이름은 화면이 종류 이름으로 보인다 */
    name: string;
    description: string | null;
    /** 사용 여부. 서랍은 늘 true */
    enabled: boolean;
    /** 기본 제공(서랍·이메일·브라우저) — 이름을 바꾸거나 지울 수 없다 */
    builtIn: boolean;
    config: NotificationChannelConfig;
    /** 서버에 저장돼 있는 비밀 칸들 — 화면은 「입력됨」으로만 보인다 */
    secretKeys: NotificationSecretKey[];
    /** 마지막 연결 시험 결과. 시험한 적이 없거나 시험할 수 없는 도구(서랍·브라우저)는 null */
    lastTest: NotificationChannelTestResult | null;
    /** 이 도구를 전달 도구로 고른 알림 이벤트 수 — 지울 때 확인 문구에 쓴다 */
    usedByEventCount: number;
}

/** `POST /api/admin/notification-channels` · `PUT /api/admin/notification-channels/{channelId}` 의 요청(권한 `notification.manage`) — 응답은 바뀐 한 줄(`NotificationChannelView`). 종류는 만든 뒤 바꿀 수 없다 */
export interface NotificationChannelSaveRequest {
    type: NotificationChannelType;
    name: string;
    description: string | null;
    enabled: boolean;
    config: NotificationChannelConfig;
    /** 입력한 비밀만 담는다 — 비워 두면 키 자체를 보내지 않는다 */
    secrets: NotificationChannelSecrets;
}

/** `PUT /api/admin/notification-channels/{channelId}/enabled` 의 요청 — 스위치 한 칸을 곧바로 저장한다(응답은 바뀐 한 줄) */
export interface NotificationChannelEnabledRequest {
    enabled: boolean;
}
