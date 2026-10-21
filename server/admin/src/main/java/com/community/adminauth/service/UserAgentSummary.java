package com.community.adminauth.service;

import com.community.audit.dto.AuditEvent;
import java.util.regex.Pattern;

/**
 * 요청의 {@code User-Agent} 를 <b>짧은 요약</b>({@code 브라우저 / 운영체제 / 기기})으로 줄인다 — 로그인 감사 기록의 {@code detail.agent} 에 남기고 내 정보 > 로그인 이력의 {@code agent} 로 내보낸다.
 *
 * <ul>
 *   <li>원문을 그대로 저장하지 않는다. 먼저 <b>200자로 자르고 제어문자를 지운</b> 뒤 정규식으로 브라우저(Edge·Opera·Firefox·Chrome·Safari)와 운영체제(Windows·macOS·iOS·Android·Linux)를 찾는다.
 *       기기는 {@code Mobile}·{@code Tablet}·{@code Desktop} 이다(언어에 묶이지 않는 영문 고정 낱말 — 화면이 그대로 보인다). 예: {@code Chrome / Windows / Desktop}.</li>
 *   <li>브라우저나 운영체제를 못 알아내면(curl·봇·낯선 앱) <b>정제한 원문의 앞 80자</b>만 남긴다. 이메일처럼 보이는 조각은 {@code (hidden)} 으로 가린다
 *       (봇 UA 의 연락처 메일이 기록에 새지 않게). 비었거나 공백뿐이면 {@code null} — 기록에 칸을 만들지 않는다.</li>
 *   <li>버전 번호는 남기지 않는다(기록을 짧게, 개인을 가리키는 정보를 줄이려고). 결과는 늘 80자 이하다.</li>
 * </ul>
 * 상태가 없는 순수 함수다. UA 는 요청자가 마음대로 꾸밀 수 있는 값이라 <b>판정에 쓰지 않고 참고용 기록</b>으로만 쓴다.
 */
public final class UserAgentSummary {

    /** 감사 기록 {@code detail} 의 칸 이름 — 로그인 이력(LoginHistoryService)이 이 이름으로 읽는다 */
    public static final String DETAIL_KEY = "agent";

    /** 자르는 길이 — 이보다 긴 UA 는 앞부분만 본다 */
    static final int INPUT_LIMIT = 200;
    /** 요약하지 못할 때 남기는 원문 길이 */
    static final int FALLBACK_LIMIT = 80;

    private static final Pattern EMAIL_LIKE = Pattern.compile("[^\\s@]+@[^\\s@]+");
    private static final Pattern EDGE = Pattern.compile("\\b(Edg|EdgA|EdgiOS|Edge)/");
    private static final Pattern OPERA = Pattern.compile("\\b(OPR|OPiOS|Opera)\\b");
    private static final Pattern FIREFOX = Pattern.compile("\\b(Firefox|FxiOS)/");
    private static final Pattern CHROME = Pattern.compile("\\b(Chrome|CriOS|Chromium)/");
    private static final Pattern SAFARI = Pattern.compile("\\bVersion/[\\d.]+.*\\bSafari/");
    private static final Pattern IOS = Pattern.compile("\\b(iPhone|iPad|iPod)\\b|CPU (iPhone )?OS \\d");
    private static final Pattern ANDROID = Pattern.compile("\\bAndroid\\b");
    private static final Pattern WINDOWS = Pattern.compile("\\bWindows\\b");
    private static final Pattern MAC = Pattern.compile("\\bMac OS X\\b|\\bMacintosh\\b");
    private static final Pattern LINUX = Pattern.compile("\\bLinux\\b|\\bX11\\b|\\bCrOS\\b");

    private UserAgentSummary() {
    }

    /** 감사 기록에 {@code agent} 칸으로 요약을 덧붙인다 — UA 가 없거나 비었으면 칸을 만들지 않고 그대로 돌려준다 */
    public static AuditEvent attach(AuditEvent event, String rawUserAgent) {
        String summary = of(rawUserAgent);
        return summary == null ? event : event.with(DETAIL_KEY, summary);
    }

    /** 요약한 글자. 없거나 비었으면 null */
    public static String of(String userAgent) {
        String text = clean(userAgent);
        if (text.isEmpty()) {
            return null;
        }
        String browser = browser(text);
        String os = os(text);
        if (browser == null || os == null) {
            return fallback(text);
        }
        return browser + " / " + os + " / " + device(text, os);
    }

    /** 200자로 자르고 제어문자·보이지 않는 서식 문자를 지운 뒤 공백을 하나로 모은다 */
    static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String cut = raw.length() > INPUT_LIMIT ? raw.substring(0, INPUT_LIMIT) : raw;
        StringBuilder out = new StringBuilder(cut.length());
        cut.codePoints().forEach(cp -> {
            int type = Character.getType(cp);
            boolean junk = Character.isISOControl(cp) || type == Character.FORMAT || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR
                    || type == Character.UNASSIGNED || type == Character.PRIVATE_USE || type == Character.SURROGATE;
            if (!junk) {
                out.appendCodePoint(cp);
            }
        });
        return out.toString().replaceAll("\\s+", " ").strip();
    }

    private static String browser(String ua) {
        if (EDGE.matcher(ua).find()) {
            return "Edge";
        }
        if (OPERA.matcher(ua).find()) {
            return "Opera";
        }
        if (FIREFOX.matcher(ua).find()) {
            return "Firefox";
        }
        if (CHROME.matcher(ua).find()) {
            return "Chrome";
        }
        if (SAFARI.matcher(ua).find()) {
            return "Safari";
        }
        return null;
    }

    private static String os(String ua) {
        if (IOS.matcher(ua).find()) {
            return "iOS"; // iPad 의 데스크톱 모드(Macintosh)는 macOS 로 보인다 — UA 만으로는 가를 수 없다
        }
        if (ANDROID.matcher(ua).find()) {
            return "Android"; // Linux 보다 먼저 — Android UA 에도 Linux 가 들어 있다
        }
        if (WINDOWS.matcher(ua).find()) {
            return "Windows";
        }
        if (MAC.matcher(ua).find()) {
            return "macOS";
        }
        if (LINUX.matcher(ua).find()) {
            return "Linux";
        }
        return null;
    }

    private static String device(String ua, String os) {
        if (ua.contains("iPad") || ua.contains("Tablet") || (os.equals("Android") && !ua.contains("Mobile"))) {
            return "Tablet";
        }
        if (os.equals("iOS") || os.equals("Android") || ua.contains("Mobile")) {
            return "Mobile";
        }
        return "Desktop";
    }

    private static String fallback(String text) {
        String hidden = EMAIL_LIKE.matcher(text).replaceAll("(hidden)");
        if (hidden.codePointCount(0, hidden.length()) <= FALLBACK_LIMIT) {
            return hidden;
        }
        return hidden.substring(0, hidden.offsetByCodePoints(0, FALLBACK_LIMIT)).stripTrailing(); // 글자 쌍(서로게이트)을 반으로 가르지 않는다
    }
}
