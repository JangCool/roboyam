package com.community.notification.channel;

import com.community.notification.entity.NotificationChannelType;

/**
 * Slack · Mattermost 글에 섞이는 <b>사용자가 입력한 값</b>(운영자 이름·배치 이름·오류 요약 …)을 <b>무력화</b>한다 — 알림 글 자체는 알림 문구 엔진({@code ChatRenderer})이
 * 템플릿·문구로 그리고, 변수 값만 여기를 지난다:
 * <ul>
 *   <li>멘션: {@code @channel}·{@code @here}·{@code @all}·{@code @사용자}·Slack 의 {@code <!here>}·{@code <@U…>}·{@code <#C…>} — 모든 {@code @} 뒤에 폭 없는 공백을 끼우고,
 *       Slack 은 {@code & < >} 를 엔티티로 바꿔(Slack 규칙) 특수 꺾쇠 구문이 만들어지지 않게 한다.</li>
 *   <li>마크다운: Mattermost 는 마크다운 기호를 역슬래시로 가리고, Slack 은 기호 앞에 폭 없는 공백을 끼워 서식·링크·코드 블록이 열리지 않게 한다.</li>
 *   <li>링크 값은 그 앱의 링크 구문을 깨는 글자만 인코딩한다.</li>
 * </ul>
 */
public final class ChannelMessageFormatter {

    private static final char ZWSP = '​';
    static final int BODY_MAX = 1000;

    private ChannelMessageFormatter() {
    }

    /** 연결 시험 글 — 사용자 입력이 없다 */
    public static String testMessage(NotificationChannelType type, String text) {
        return neutralize(text, type == NotificationChannelType.SLACK, BODY_MAX);
    }

    /**
     * 템플릿에 넣을 <b>변수 값</b>을 무력화한다(알림 템플릿 렌더링 엔진이 쓴다 — 멘션·마크다운이 열리지 않게). 운영자가 쓴 문구·틀의 표기는 건드리지 않고 값만 가린다.
     *
     * @param slack Slack 이면 true, Mattermost 면 false
     */
    public static String neutralizeValue(boolean slack, String raw, int max) {
        return neutralize(raw, slack, max);
    }

    /** 링크 변수 값을 그 앱의 링크 구문 안에 넣을 수 있게 — 구문을 깨는 글자만 인코딩한다(서버가 조립한 주소 · 무력화하면 주소가 깨진다) */
    public static String linkValue(boolean slack, String url) {
        return url == null ? "" : slack ? slackUrl(url) : markdownUrl(url);
    }

    /** 사용자 입력이 섞인 글을 무력화한다 */
    static String neutralize(String raw, boolean slack, int max) {
        if (raw == null) {
            return "";
        }
        String text = raw.length() > max ? raw.substring(0, max - 1) + "…" : raw;
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\r') {
                continue;
            }
            if (c != '\n' && (Character.isISOControl(c) || c == ZWSP)) {
                continue;
            }
            switch (c) {
                case '@' -> out.append('@').append(ZWSP);
                case '&' -> out.append(slack ? "&amp;" : "&");
                case '<' -> out.append(slack ? "&lt;" : "\\<");
                case '>' -> out.append(slack ? "&gt;" : "\\>");
                case '*', '_', '~', '`' -> out.append(slack ? ZWSP + String.valueOf(c) : "\\" + c);
                case '\\', '[', ']', '(', ')', '#', '|', '!' -> out.append(slack ? String.valueOf(c) : "\\" + c);
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private static String slackUrl(String url) {
        return url.replace("&", "&amp;").replace("<", "%3C").replace(">", "%3E").replace("|", "%7C");
    }

    private static String markdownUrl(String url) {
        return url.replace("(", "%28").replace(")", "%29").replace("[", "%5B").replace("]", "%5D").replace(" ", "%20");
    }
}
