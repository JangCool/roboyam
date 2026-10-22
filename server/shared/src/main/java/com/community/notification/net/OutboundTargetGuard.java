package com.community.notification.net;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 서버가 <b>운영자가 입력한 주소</b>로 요청을 보내기 전에 거르는 곳(SSRF 방어) — Slack·Mattermost 도구의 서버 주소·웹훅 주소가 대상이다.
 *
 * <ul>
 *   <li><b>모양</b>: {@code http}·{@code https} 만, 호스트가 있어야 하고, 자격 증명({@code user:pass@})·조각({@code #…})·공백/제어 문자는 거절한다. 포트는 1~65535.</li>
 *   <li><b>주소</b>: 호스트를 <b>DNS 로 풀어</b> 나온 모든 IP 를 검사한다(IP 글자가 와도 같다). 하나라도 막힌 주소면 거절한다. 검사한 IP 로 그대로 연결해야
 *       한다({@code SafeHttpClient}) — 검사 뒤에 이름을 다시 풀지 않는다(DNS 재해석 우회 방지).</li>
 *   <li><b>항상 막는 주소</b>: 클라우드 메타데이터·링크 로컬({@code 169.254.0.0/16}, {@code fe80::/10}, AWS {@code fd00:ec2::/32}, 알리바바 {@code 100.100.100.200}),
 *       와일드카드({@code 0.0.0.0/8}, {@code ::}), 멀티캐스트·예약({@code 224.0.0.0/3}, {@code ff00::/8}).</li>
 *   <li><b>설정으로 막는 주소</b>: 로컬호스트·사설망({@code 127/8}, {@code 10/8}, {@code 172.16/12}, {@code 192.168/16}, {@code 100.64/10}, {@code ::1}, {@code fc00::/7} 등)은
 *       {@code roboyam.notification.allow-private-targets} 가 true 일 때만 허용한다(기본 false, {@code local} 프로파일만 true).</li>
 *   <li>IPv4 가 섞여 들어온 IPv6({@code ::ffff:a.b.c.d}, NAT64, 6to4)는 안의 IPv4 로 같은 검사를 한다.</li>
 * </ul>
 * 거절 사유({@link Reason})만 알리고 풀린 IP·입력 주소는 오류에 싣지 않는다.
 */
@Component
public class OutboundTargetGuard {

    /** 거절 사유 */
    public enum Reason {
        /** 주소 모양이 아니거나 http(s) 가 아니다 */
        FORMAT,
        /** 자격 증명이 들어 있다 */
        CREDENTIALS,
        /** 포트가 범위를 벗어났다 */
        PORT,
        /** 막힌 주소(메타데이터·링크 로컬·와일드카드·사설망) */
        ADDRESS,
        /** 이름을 풀지 못했다(저장할 때는 통과시키고 보낼 때 알린다) */
        DNS
    }

    /** 막힌 대상 — 사유만 든다 */
    public static class BlockedTargetException extends RuntimeException {

        private final transient Reason reason;

        public BlockedTargetException(Reason reason) {
            super(reason.name(), null, false, false);
            this.reason = reason;
        }

        public Reason reason() {
            return reason;
        }
    }

    /** 검사를 마친 대상 — 이 {@code addresses} 로 연결한다(다시 풀지 않는다) */
    public record Target(URI uri, String host, int port, boolean https, String requestTarget, List<InetAddress> addresses) {
    }

    private final boolean allowPrivate;

    public OutboundTargetGuard(@Value("${roboyam.notification.allow-private-targets:false}") boolean allowPrivate) {
        this.allowPrivate = allowPrivate;
    }

    /**
     * 저장할 때의 검사 — 모양을 보고, 호스트가 IP 글자이거나 이름이 풀리면 그 IP 가 막힌 주소가 아닌지 본다. 이름이 안 풀려도(아직 없는 내부 이름일 수 있다) 통과시킨다 — 보낼 때 다시 본다.
     *
     * @return 거절 사유. 통과면 null
     */
    public Reason validate(String url) {
        try {
            resolve(url);
            return null;
        } catch (BlockedTargetException e) {
            return e.reason() == Reason.DNS ? null : e.reason();
        }
    }

    /**
     * 주소를 검사하고 DNS 를 풀어 연결할 IP 들을 돌려준다.
     *
     * @throws BlockedTargetException 모양이 틀리거나 막힌 주소이거나 이름을 풀지 못했을 때
     */
    public Target resolve(String url) {
        URI uri = parse(url);
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        boolean https = scheme.equals("https");
        String host = stripBrackets(uri.getHost());
        int port = uri.getPort() == -1 ? (https ? 443 : 80) : uri.getPort();
        InetAddress[] resolved;
        try {
            resolved = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new BlockedTargetException(Reason.DNS);
        }
        if (resolved.length == 0) {
            throw new BlockedTargetException(Reason.DNS);
        }
        List<InetAddress> addresses = new ArrayList<>();
        for (InetAddress address : resolved) {
            if (isBlocked(address)) {
                throw new BlockedTargetException(Reason.ADDRESS);
            }
            addresses.add(address);
        }
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        String requestTarget = uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
        return new Target(uri, host, port, https, requestTarget, List.copyOf(addresses));
    }

    /** 모양만 본다(DNS 를 풀지 않는다) */
    static URI parse(String url) {
        if (url == null || url.isBlank() || url.length() > 2000) {
            throw new BlockedTargetException(Reason.FORMAT);
        }
        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c <= 0x20 || c == 0x7f || Character.isWhitespace(c) || Character.isISOControl(c)) {
                throw new BlockedTargetException(Reason.FORMAT);
            }
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new BlockedTargetException(Reason.FORMAT);
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new BlockedTargetException(Reason.FORMAT);
        }
        if (uri.getRawUserInfo() != null || (uri.getRawAuthority() != null && uri.getRawAuthority().contains("@"))) {
            throw new BlockedTargetException(Reason.CREDENTIALS);
        }
        if (uri.getHost() == null || uri.getHost().isBlank() || uri.getRawFragment() != null) {
            throw new BlockedTargetException(Reason.FORMAT);
        }
        if (uri.getPort() != -1 && (uri.getPort() < 1 || uri.getPort() > 65535)) {
            throw new BlockedTargetException(Reason.PORT);
        }
        return uri;
    }

    private static String stripBrackets(String host) {
        return host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
    }

    // ── 주소 판정 ──────────────────────────────────────────────────────────

    /** 이 IP 로는 연결하지 않는가 */
    boolean isBlocked(InetAddress address) {
        Verdict verdict = classify(address.getAddress());
        return verdict == Verdict.ALWAYS || (verdict == Verdict.PRIVATE && !allowPrivate);
    }

    enum Verdict { OK, PRIVATE, ALWAYS }

    static Verdict classify(byte[] raw) {
        if (raw.length == 4) {
            return classifyV4(raw);
        }
        if (raw.length != 16) {
            return Verdict.ALWAYS;
        }
        int b0 = raw[0] & 0xff;
        int b1 = raw[1] & 0xff;
        // ::ffff:a.b.c.d (IPv4-mapped), ::a.b.c.d (IPv4-compatible, ::1 과 :: 제외), 64:ff9b::/96 (NAT64) — 안의 IPv4 로 본다
        if (isZero(raw, 0, 10) && (raw[10] & 0xff) == 0xff && (raw[11] & 0xff) == 0xff) {
            return classifyV4(new byte[] {raw[12], raw[13], raw[14], raw[15]});
        }
        if (isZero(raw, 0, 12)) {
            if (isZero(raw, 12, 15) && (raw[15] == 0 || raw[15] == 1)) {
                return raw[15] == 1 ? Verdict.PRIVATE : Verdict.ALWAYS; // ::1 사설, :: 와일드카드
            }
            return worse(Verdict.PRIVATE, classifyV4(new byte[] {raw[12], raw[13], raw[14], raw[15]}));
        }
        if (b0 == 0x00 && b1 == 0x64 && (raw[2] & 0xff) == 0xff && (raw[3] & 0xff) == 0x9b && isZero(raw, 4, 12)) {
            return classifyV4(new byte[] {raw[12], raw[13], raw[14], raw[15]});
        }
        if (b0 == 0x20 && b1 == 0x02) { // 6to4 — 안의 IPv4
            return classifyV4(new byte[] {raw[2], raw[3], raw[4], raw[5]});
        }
        if (b0 == 0xff) {
            return Verdict.ALWAYS; // 멀티캐스트
        }
        if (b0 == 0xfe && (b1 & 0xc0) == 0x80) {
            return Verdict.ALWAYS; // fe80::/10 링크 로컬
        }
        if (b0 == 0xfd && b1 == 0x00 && (raw[2] & 0xff) == 0x0e && (raw[3] & 0xff) == 0xc2) {
            return Verdict.ALWAYS; // fd00:ec2::/32 — AWS 메타데이터
        }
        if ((b0 & 0xfe) == 0xfc) {
            return Verdict.PRIVATE; // fc00::/7 고유 로컬
        }
        if (b0 == 0xfe && (b1 & 0xc0) == 0xc0) {
            return Verdict.PRIVATE; // fec0::/10 사이트 로컬(폐기됐지만)
        }
        return Verdict.OK;
    }

    private static Verdict classifyV4(byte[] a) {
        int o1 = a[0] & 0xff;
        int o2 = a[1] & 0xff;
        int o3 = a[2] & 0xff;
        int o4 = a[3] & 0xff;
        if (o1 == 0) {
            return Verdict.ALWAYS; // 0.0.0.0/8 와일드카드
        }
        if (o1 == 169 && o2 == 254) {
            return Verdict.ALWAYS; // 169.254.0.0/16 링크 로컬·클라우드 메타데이터
        }
        if (o1 >= 224) {
            return Verdict.ALWAYS; // 224.0.0.0/4 멀티캐스트 + 240.0.0.0/4 예약(255.255.255.255 포함)
        }
        if (o1 == 100 && o2 == 100 && o3 == 100 && o4 == 200) {
            return Verdict.ALWAYS; // 알리바바 클라우드 메타데이터
        }
        if (o1 == 127 || o1 == 10 || (o1 == 172 && (o2 & 0xf0) == 16) || (o1 == 192 && o2 == 168) || (o1 == 100 && (o2 & 0xc0) == 64)) {
            return Verdict.PRIVATE;
        }
        if ((o1 == 192 && o2 == 0 && o3 == 0) || (o1 == 198 && (o2 & 0xfe) == 18)) {
            return Verdict.PRIVATE; // 192.0.0.0/24 · 198.18.0.0/15
        }
        return Verdict.OK;
    }

    private static Verdict worse(Verdict a, Verdict b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }

    private static boolean isZero(byte[] raw, int from, int to) {
        for (int i = from; i < to; i++) {
            if (raw[i] != 0) {
                return false;
            }
        }
        return true;
    }
}
