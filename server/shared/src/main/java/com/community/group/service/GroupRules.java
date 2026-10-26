package com.community.group.service;

import com.community.group.entity.GroupEntity;
import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

/** 소모임 값 규칙 — 이름 다듬기·이름 키·한 줄 소개·모집 상태·채팅 링크 주소. DB 를 읽지 않는 순수 함수다 */
public final class GroupRules {

    public static final int NAME_MIN = 2;
    public static final int NAME_MAX = 20;
    public static final int DESCRIPTION_MAX = 500;
    public static final int SUMMARY_MAX = 60;
    public static final int JOIN_MESSAGE_MAX = 100;
    public static final int APPLICATION_REJECT_MAX = 100;
    public static final int ADMIN_REASON_MAX = 200;
    public static final int CHAT_LABEL_MAX = 30;
    public static final int CHAT_URL_MAX = 300;

    public static final String RECRUIT_OPEN = "OPEN";
    public static final String RECRUIT_FULL = "FULL";
    public static final String RECRUIT_CLOSED = "CLOSED";

    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern FORBIDDEN = Pattern.compile("[<>\\p{Cntrl}]");

    private GroupRules() {
    }

    /** 앞뒤 공백을 다듬고 안쪽 공백을 하나로 줄인다(null 이면 null) */
    public static String normalize(String raw) {
        return raw == null ? null : SPACES.matcher(raw.strip()).replaceAll(" ");
    }

    /** 같은 이름 막기 — 공백을 모두 빼고 소문자 */
    public static String nameKey(String name) {
        return SPACES.matcher(name).replaceAll("").toLowerCase(Locale.ROOT);
    }

    /** 이름이 규칙(2~20자, &lt; &gt; 줄바꿈·제어 문자 없음)에 맞나 — 맞으면 null, 아니면 문구 키 */
    public static String nameProblem(String raw) {
        if (raw == null || raw.isBlank()) {
            return "group.name.required";
        }
        if (FORBIDDEN.matcher(raw.strip()).find()) {
            return "group.name.invalid";
        }
        int length = normalize(raw).codePointCount(0, normalize(raw).length());
        return length < NAME_MIN || length > NAME_MAX ? "group.name.length" : null;
    }

    /** 소개를 한 줄로 — 공백을 접고 60자에서 … */
    public static String summary(String description) {
        if (description == null) {
            return "";
        }
        String flat = SPACES.matcher(description.strip()).replaceAll(" ");
        return flat.codePointCount(0, flat.length()) <= SUMMARY_MAX ? flat : flat.substring(0, flat.offsetByCodePoints(0, SUMMARY_MAX)) + "…";
    }

    /** 모집 상태 — 모임장이 마감 CLOSED → 정원 참 FULL → 모집 중 OPEN */
    public static String recruitState(GroupEntity group) {
        if (!group.isRecruiting()) {
            return RECRUIT_CLOSED;
        }
        return group.full() ? RECRUIT_FULL : RECRUIT_OPEN;
    }

    /** 카카오 오픈채팅 주소인가({@code https://open.kakao.com/…}) */
    public static boolean kakaoUrl(String url) {
        return allowed(url, "open.kakao.com", null);
    }

    /** 디스코드 초대 주소인가({@code https://discord.gg/…} · {@code https://discord.com/invite/…}) */
    public static boolean discordUrl(String url) {
        return allowed(url, "discord.gg", null) || allowed(url, "discord.com", "/invite/");
    }

    private static boolean allowed(String url, String host, String pathPrefix) {
        if (url == null || url.isBlank() || url.length() > CHAT_URL_MAX || FORBIDDEN.matcher(url).find() || url.strip().contains(" ")) {
            return false;
        }
        try {
            URI uri = new URI(url.strip());
            if (!"https".equals(uri.getScheme()) || uri.getRawUserInfo() != null || uri.getPort() != -1 || !host.equalsIgnoreCase(uri.getHost())) {
                return false;
            }
            String path = uri.getPath() == null ? "" : uri.getPath();
            if (pathPrefix != null) {
                return path.startsWith(pathPrefix) && path.length() > pathPrefix.length();
            }
            return path.length() > 1;
        } catch (Exception e) {
            return false;
        }
    }
}
