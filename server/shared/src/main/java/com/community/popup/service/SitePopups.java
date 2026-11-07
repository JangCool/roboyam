package com.community.popup.service;

import com.community.popup.entity.SitePopupEntity;
import com.community.site.service.SiteNoticeBars;
import com.community.zconfiguration.message.MessageRef;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * 팝업(공개 사이트 화면 가운데 뜨는 이벤트·프로모션 창 — docs/features/site-popup-contract.md) 규칙. 관리자 저장(admin)과 공개 읽기(api)가 같은 규칙을 쓴다.
 * 화면은 {@code util-site-popup.ts} 가 미리 검사하고 판정은 서버가 한다. DB 의 {@code ck_site_popups_*}(V162)가 칸 하나씩 한 번 더 지킨다.
 *
 * <ul>
 *   <li>글자 칸: 앞뒤 공백을 뗀 뒤 비면 없음(null, {@link #normalize}). 길이는 코드포인트로 센다 — 이름 1~{@value #NAME_MAX}(필수) · 그림 대체 글 {@value #IMAGE_ALT_MAX} ·
 *       제목 {@value #HEADING_MAX} · 내용 {@value #BODY_MAX}(줄바꿈 유지 — {@code \r\n}·{@code \r} 은 {@code \n} 으로) · 단추 글자 {@value #BUTTON_MAX}.</li>
 *   <li>종류별 필수: {@code IMAGE} 는 그림, {@code TEXT} 는 내용. 지금 종류가 아닌 칸은 검사하지 않고 그대로 저장한다(종류를 되돌리면 다시 쓰인다).</li>
 *   <li>링크(선택): 공지 띠 링크와 같은 규칙({@link SiteNoticeBars#linkViolation} — {@code /…}(단 {@code //}·{@code /\} 아님) 또는 {@code http(s)://}, 공백 없음, 500자).</li>
 *   <li>보일 곳 {@code HOME|ALL} · 기기 {@code PC|MOBILE} 하나 이상 · 닫기 선택지 {@code TODAY|WEEK|NEVER|NONE} · 기간은 끝 &gt; 시작.</li>
 *   <li>상태({@link #status}): OFF(노출 끔) → SCHEDULED(시작 전) → EXPIRED(끝 지남) → SHOWING. 앞의 것이 이긴다.</li>
 *   <li>내용 표식({@link #version}): 보이는 칸(종류 + 그 종류의 칸 + 링크 + 닫기 선택지)의 SHA-256 앞 16자리 hex. 공개 사이트는 이 값별로 「보지 않기」를 기억하므로
 *       운영자가 보이는 내용을 고치면 다시 보인다(이름·대상·기기·기간·노출만 바꾸면 그대로).</li>
 * </ul>
 */
public final class SitePopups {

    public static final String KIND_IMAGE = "IMAGE";
    public static final String KIND_TEXT = "TEXT";
    public static final List<String> KINDS = List.of(KIND_IMAGE, KIND_TEXT);
    public static final String TARGET_HOME = "HOME";
    public static final String TARGET_ALL = "ALL";
    public static final List<String> TARGETS = List.of(TARGET_HOME, TARGET_ALL);
    public static final String DEVICE_PC = "PC";
    public static final String DEVICE_MOBILE = "MOBILE";
    public static final List<String> DEVICES = List.of(DEVICE_PC, DEVICE_MOBILE);
    public static final List<String> HIDE_OPTIONS = List.of("TODAY", "WEEK", "NEVER", "NONE");

    public static final int NAME_MAX = 40;
    public static final int IMAGE_ALT_MAX = 100;
    public static final int HEADING_MAX = 40;
    public static final int BODY_MAX = 1000;
    public static final int BUTTON_MAX = 20;

    public static final String SHOWING = "SHOWING";
    public static final String SCHEDULED = "SCHEDULED";
    public static final String EXPIRED = "EXPIRED";
    public static final String OFF = "OFF";

    private SitePopups() {
    }

    /** 앞뒤 공백을 뗀 값(비면 null) — 공지 띠와 같다 */
    public static String normalize(String value) {
        return SiteNoticeBars.normalize(value);
    }

    /** 내용 — 줄 끝을 {@code \n} 으로 맞추고 앞뒤 공백을 뗀 값(비면 null) */
    public static String normalizeBody(String value) {
        return value == null ? null : normalize(value.replace("\r\n", "\n").replace('\r', '\n'));
    }

    /** 고를 수 있는 값이면 앞뒤 공백을 뗀 그 값, 아니면 null */
    public static String choice(String value, List<String> allowed) {
        String v = normalize(value);
        return v != null && allowed.contains(v) ? v : null;
    }

    public static boolean isImage(String kind) {
        return KIND_IMAGE.equals(kind);
    }

    /** 길이 위반(키 {@code sitePopup.<칸>.tooLong}) — {@code text} 는 정리한 값, 없음은 위반이 아니다 */
    public static Optional<MessageRef> tooLong(String field, String text, int max) {
        if (text != null && text.codePointCount(0, text.length()) > max) {
            return Optional.of(new MessageRef("sitePopup." + field + ".tooLong", String.valueOf(max)));
        }
        return Optional.empty();
    }

    /** 이름 위반 — 필수, {@value #NAME_MAX}자 */
    public static Optional<MessageRef> nameViolation(String name) {
        return name == null ? Optional.of(new MessageRef("sitePopup.name.required")) : tooLong("name", name, NAME_MAX);
    }

    /** 내용 위반 — TEXT 일 때만 부른다. 필수, {@value #BODY_MAX}자 */
    public static Optional<MessageRef> bodyViolation(String body) {
        return body == null ? Optional.of(new MessageRef("sitePopup.body.required")) : tooLong("body", body, BODY_MAX);
    }

    /** 링크 위반 — 공지 띠 링크와 같은 규칙·같은 문구 */
    public static Optional<MessageRef> linkViolation(String link) {
        return SiteNoticeBars.linkViolation(link);
    }

    /**
     * 기기 목록 → [PC, MOBILE] 켬. 비었거나 모르는 값이 있으면 null(칸 오류).
     *
     * @return {@code [showPc, showMobile]} 또는 null
     */
    public static boolean[] devices(List<String> devices) {
        if (devices == null || devices.isEmpty()) {
            return null;
        }
        boolean pc = false;
        boolean mobile = false;
        for (String raw : devices) {
            String d = normalize(raw);
            if (DEVICE_PC.equals(d)) {
                pc = true;
            } else if (DEVICE_MOBILE.equals(d)) {
                mobile = true;
            } else {
                return null;
            }
        }
        return new boolean[] {pc, mobile};
    }

    /** 보일 기기 — {@link #DEVICES} 차례 */
    public static List<String> devicesOf(SitePopupEntity row) {
        List<String> out = new ArrayList<>(2);
        if (row.isShowPc()) {
            out.add(DEVICE_PC);
        }
        if (row.isShowMobile()) {
            out.add(DEVICE_MOBILE);
        }
        return out;
    }

    /** 상태 — OFF → SCHEDULED → EXPIRED → SHOWING */
    public static String status(SitePopupEntity row, Instant now) {
        if (!row.isVisible()) {
            return OFF;
        }
        if (row.getShowFrom() != null && row.getShowFrom().isAfter(now)) {
            return SCHEDULED;
        }
        if (row.getShowUntil() != null && !row.getShowUntil().isAfter(now)) {
            return EXPIRED;
        }
        return SHOWING;
    }

    /** 내용 표식 — 종류 + 그 종류의 보이는 칸 + 링크 + 닫기 선택지(칸마다 길이를 앞에 붙여 이어서 경계가 섞이지 않게) */
    public static String version(SitePopupEntity row) {
        StringBuilder text = new StringBuilder();
        append(text, row.getKind());
        if (isImage(row.getKind())) {
            append(text, row.getImageFileId() == null ? null : Long.toString(row.getImageFileId()));
            append(text, row.getImageAlt());
        } else {
            append(text, row.getHeading());
            append(text, row.getBody());
            append(text, row.getButtonLabel());
        }
        append(text, row.getLink());
        append(text, row.getHideOption());
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void append(StringBuilder out, String value) {
        if (value == null) {
            out.append("-;");
        } else {
            out.append(value.length()).append(':').append(value).append(';');
        }
    }
}
