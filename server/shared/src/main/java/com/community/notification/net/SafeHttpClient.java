package com.community.notification.net;

import com.community.notification.net.OutboundTargetGuard.Target;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 운영자·회원이 입력한 주소로 요청을 한 번 보내는 작은 HTTP/1.1 클라이언트 — {@link OutboundTargetGuard} 가 <b>검사한 그 IP</b> 로만 연결한다(SSRF 방어).
 * 알림 도구의 JSON POST({@link #postJson})와 핫딜 링크 OG 가져오기의 GET({@link #get})이 같은 방어를 쓴다.
 *
 * <p>JDK {@code HttpClient} 를 쓰지 않는 이유: 거기서는 연결 때 이름을 <b>다시 풀기</b> 때문에 검사한 IP 와 연결하는 IP 가 달라질 수 있다(DNS 재해석 우회).
 * 여기서는 검사가 돌려준 IP 로 소켓을 직접 열고, https 는 그 소켓 위에 TLS 를 올려 <b>호스트 이름으로 인증서를 확인</b>한다(SNI·호스트 이름 검증 켜짐).
 *
 * <ul>
 *   <li><b>리다이렉트를 따라가지 않는다</b> — 3xx 는 그냥 상태 코드로 돌려준다(POST 는 호출하는 쪽이 실패로 본다. GET 은 {@code Location} 을 돌려주고 따라갈지는 호출하는 쪽이
 *       매 단계 다시 검사해 정한다).</li>
 *   <li>연결·읽기 제한 시간과 <b>전체 마감 시간</b>이 있다(조금씩 흘려 보내는 응답에 매이지 않는다). POST 응답 본문은 {@value #MAX_BODY} 바이트까지만 읽고, GET 은 부르는 쪽이 정한 상한까지.</li>
 *   <li>POST 응답 본문은 호출하는 쪽(발송기)만 보고, 사용자에게 돌려주지 않는다 — 결과는 성공/실패와 짧은 사유 코드뿐이다.</li>
 *   <li>헤더 값에 줄바꿈이 있으면 거절한다(헤더 주입 방지). {@code Accept-Encoding} 을 보내지 않아 압축 없이 받는다.</li>
 * </ul>
 */
@Component
public class SafeHttpClient {

    /** POST 응답 본문을 읽는 최대 바이트 */
    static final int MAX_BODY = 64 * 1024;
    private static final int MAX_LINE = 8 * 1024;
    private static final int MAX_HEADERS = 100;

    /** 응답 — 본문은 최대 {@value #MAX_BODY} 바이트(잘렸을 수 있다) */
    public record Response(int status, String body) {
    }

    /**
     * GET 의 한도.
     *
     * @param connectTimeoutMillis 연결 제한 시간
     * @param readTimeoutMillis    한 번 읽기의 제한 시간
     * @param deadlineNanos        전체 마감({@link System#nanoTime()} 기준 — 리디렉션 여러 단계가 같은 마감을 나눠 쓸 수 있다)
     * @param maxBody              본문 최대 바이트(넘으면 거기서 멈추고 {@code truncated})
     * @param acceptContentType    이 Content-Type(소문자, 매개변수 뺀 것)이면 본문을 읽는다 — 아니면 본문을 읽지 않는다. 2xx 일 때만 읽는다
     * @param stopAfter            본문에 이 글자(ASCII, 대소문자 무시)가 나오면 거기까지 읽고 멈춘다 — null 이면 끝까지(상한까지)
     */
    public record FetchLimits(int connectTimeoutMillis, int readTimeoutMillis, long deadlineNanos, int maxBody, Predicate<String> acceptContentType,
                              String stopAfter) {
    }

    /**
     * GET 응답.
     *
     * @param location    {@code Location} 헤더(없으면 null) — 따라갈지는 부르는 쪽이 정한다
     * @param contentType {@code Content-Type} 의 형식(소문자, 매개변수 뺀 것 — 없으면 null)
     * @param charset     {@code Content-Type} 의 {@code charset}(없으면 null)
     * @param body        읽은 본문(읽지 않았으면 빈 배열)
     * @param truncated   상한에 닿아 다 읽지 못했다
     */
    public record FetchResponse(int status, String location, String contentType, String charset, String contentEncoding, byte[] body, boolean truncated) {
    }

    private record Io(int connectTimeoutMillis, int readTimeoutMillis, long deadline, int maxBody, byte[] stopAfter) {
    }

    private final OutboundTargetGuard guard;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;
    private final int totalTimeoutMillis;

    public SafeHttpClient(OutboundTargetGuard guard, @Value("${irobit.notification.connect-timeout-millis:3000}") int connectTimeoutMillis,
                          @Value("${irobit.notification.read-timeout-millis:5000}") int readTimeoutMillis,
                          @Value("${irobit.notification.total-timeout-millis:10000}") int totalTimeoutMillis) {
        this.guard = guard;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
        this.totalTimeoutMillis = totalTimeoutMillis;
    }

    /**
     * JSON 을 POST 한다.
     *
     * @param url      대상 주소(검사는 여기서 한다)
     * @param headers  추가 헤더(예: {@code Authorization}) — 값에 줄바꿈이 있으면 거절한다
     * @param jsonBody 요청 본문(UTF-8)
     * @throws OutboundTargetGuard.BlockedTargetException 막힌 주소·이름을 못 풀음
     * @throws IOException                               연결·TLS·읽기 실패({@link SocketTimeoutException} 은 시간 초과)
     */
    public Response postJson(String url, Map<String, String> headers, String jsonBody) throws IOException {
        Target target = guard.resolve(url);
        long deadline = System.nanoTime() + totalTimeoutMillis * 1_000_000L;
        Io io = new Io(connectTimeoutMillis, readTimeoutMillis, deadline, MAX_BODY, null);
        byte[] body = jsonBody.getBytes(StandardCharsets.UTF_8);
        Raw raw = withAddresses(target, io, socket -> {
            writePost(socket.getOutputStream(), target, headers, body);
            return read(socket, io, true, null);
        });
        return new Response(raw.status, new String(raw.body, StandardCharsets.UTF_8));
    }

    /**
     * GET 을 한 번 보낸다 — 리디렉션을 따라가지 않는다. 대상은 부르는 쪽이 이미 검사했다({@link OutboundTargetGuard#resolve(String, Predicate)} — 검사한 IP 로만 연결한다).
     *
     * @param headers 추가 헤더(예: {@code User-Agent}·{@code Accept}) — 값에 줄바꿈이 있으면 거절한다
     * @throws IOException 연결·TLS·읽기 실패({@link SocketTimeoutException} 은 시간 초과 — 전체 마감 포함)
     */
    public FetchResponse get(Target target, Map<String, String> headers, FetchLimits limits) throws IOException {
        byte[] stop = limits.stopAfter() == null ? null : limits.stopAfter().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.ISO_8859_1);
        Io io = new Io(limits.connectTimeoutMillis(), limits.readTimeoutMillis(), limits.deadlineNanos(), limits.maxBody(), stop);
        Raw raw = withAddresses(target, io, socket -> {
            writeGet(socket.getOutputStream(), target, headers);
            return read(socket, io, false, limits.acceptContentType());
        });
        return new FetchResponse(raw.status, raw.location, raw.contentType, raw.charset, raw.contentEncoding, raw.body, raw.truncated);
    }

    @FunctionalInterface
    private interface Exchange {
        Raw run(Socket socket) throws IOException;
    }

    /** 읽은 응답 */
    private record Raw(int status, String location, String contentType, String charset, String contentEncoding, byte[] body, boolean truncated) {
    }

    /** 검사한 주소 차례로(최대 3개) 연결해 본다 — 시간 초과는 다른 주소로 다시 하지 않는다(전체 마감을 넘기게 된다) */
    private Raw withAddresses(Target target, Io io, Exchange exchange) throws IOException {
        IOException last = null;
        int tried = 0;
        for (InetAddress address : target.addresses()) {
            if (tried++ >= 3) {
                break;
            }
            try {
                return exchange(target, address, io, exchange);
            } catch (SocketTimeoutException e) {
                throw e;
            } catch (IOException e) {
                last = e;
            }
        }
        throw last != null ? last : new IOException("no address");
    }

    private Raw exchange(Target target, InetAddress address, Io io, Exchange exchange) throws IOException {
        Socket plain = new Socket();
        try {
            plain.connect(new InetSocketAddress(address, target.port()), Math.min(io.connectTimeoutMillis(), remainingMillis(io.deadline())));
            plain.setSoTimeout(Math.min(io.readTimeoutMillis(), remainingMillis(io.deadline())));
            plain.setTcpNoDelay(true);
            Socket socket = plain;
            if (target.https()) {
                SSLSocket tls = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(plain, target.host(), target.port(), true);
                SSLParameters params = tls.getSSLParameters();
                params.setEndpointIdentificationAlgorithm("HTTPS"); // 인증서의 호스트 이름 검증
                if (!isIpLiteral(target.host())) {
                    params.setServerNames(List.of(new SNIHostName(target.host())));
                }
                tls.setSSLParameters(params);
                tls.startHandshake();
                socket = tls;
            }
            return exchange.run(socket);
        } finally {
            try {
                plain.close();
            } catch (IOException ignored) {
                // 이미 닫힘
            }
        }
    }

    private void writePost(OutputStream out, Target target, Map<String, String> headers, byte[] body) throws IOException {
        StringBuilder head = new StringBuilder();
        head.append("POST ").append(target.requestTarget()).append(" HTTP/1.1\r\n");
        head.append("Host: ").append(hostHeader(target)).append("\r\n");
        head.append("User-Agent: irobit-notification\r\n");
        head.append("Accept: application/json\r\n");
        head.append("Content-Type: application/json; charset=utf-8\r\n");
        head.append("Content-Length: ").append(body.length).append("\r\n");
        appendHeaders(head, headers);
        head.append("Connection: close\r\n\r\n");
        out.write(head.toString().getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
        out.flush();
    }

    private void writeGet(OutputStream out, Target target, Map<String, String> headers) throws IOException {
        StringBuilder head = new StringBuilder();
        head.append("GET ").append(target.requestTarget()).append(" HTTP/1.1\r\n");
        head.append("Host: ").append(hostHeader(target)).append("\r\n");
        appendHeaders(head, headers);
        head.append("Connection: close\r\n\r\n");
        out.write(head.toString().getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
    }

    private static void appendHeaders(StringBuilder head, Map<String, String> headers) {
        for (Map.Entry<String, String> header : headers.entrySet()) {
            String name = header.getKey();
            String value = header.getValue();
            if (hasLineBreak(name) || hasLineBreak(value) || name.isBlank()) {
                throw new IllegalArgumentException("header");
            }
            head.append(name).append(": ").append(value).append("\r\n");
        }
    }

    /**
     * @param alwaysBody        상태·형식과 무관하게 본문을 읽는다(POST — 예전 동작)
     * @param acceptContentType GET — 2xx 이고 이 형식이면 본문을 읽는다
     */
    private Raw read(Socket socket, Io io, boolean alwaysBody, Predicate<String> acceptContentType) throws IOException {
        InputStream in = socket.getInputStream();
        String statusLine = readLine(in, socket, io);
        if (statusLine == null || !statusLine.startsWith("HTTP/")) {
            throw new IOException("bad status line");
        }
        String[] parts = statusLine.split(" ", 3);
        int status;
        try {
            status = Integer.parseInt(parts[1].trim());
        } catch (RuntimeException e) {
            throw new IOException("bad status line");
        }
        long contentLength = -1;
        boolean chunked = false;
        String location = null;
        String contentType = null;
        String charset = null;
        String contentEncoding = null;
        for (int i = 0; i <= MAX_HEADERS; i++) {
            String line = readLine(in, socket, io);
            if (line == null) {
                break;
            }
            if (line.isEmpty()) {
                break;
            }
            if (i == MAX_HEADERS) {
                throw new IOException("too many headers");
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String name = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            switch (name) {
                case "content-length" -> {
                    try {
                        contentLength = Long.parseLong(value);
                    } catch (NumberFormatException e) {
                        contentLength = -1;
                    }
                }
                case "transfer-encoding" -> chunked = value.toLowerCase(Locale.ROOT).contains("chunked");
                case "location" -> location = location == null ? value : location;
                case "content-encoding" -> contentEncoding = value.toLowerCase(Locale.ROOT);
                case "content-type" -> {
                    String[] typeParts = value.split(";");
                    contentType = typeParts[0].trim().toLowerCase(Locale.ROOT);
                    for (int p = 1; p < typeParts.length; p++) {
                        String param = typeParts[p].trim();
                        if (param.toLowerCase(Locale.ROOT).startsWith("charset=")) {
                            charset = param.substring(8).trim().replace("\"", "");
                        }
                    }
                }
                default -> {
                    // 그 밖의 헤더는 보지 않는다
                }
            }
        }
        boolean noBody = status == 204 || status == 304 || (status >= 100 && status < 200);
        boolean wanted = alwaysBody || (status >= 200 && status < 300 && acceptContentType != null && contentType != null && acceptContentType.test(contentType));
        if (noBody || !wanted || io.maxBody() <= 0) {
            return new Raw(status, location, contentType, charset, contentEncoding, new byte[0], false);
        }
        Body body = readBody(in, socket, io, contentLength, chunked);
        return new Raw(status, location, contentType, charset, contentEncoding, body.bytes, body.truncated);
    }

    private record Body(byte[] bytes, boolean truncated) {
    }

    /** 멈춤 글자 찾기의 상태(한 응답 안) */
    private static final class Stop {
        final byte[] marker;
        int matched;
        boolean hit;

        Stop(byte[] marker) {
            this.marker = marker;
        }

        /** 이 조각을 보고 멈춤 글자가 끝나는 위치(그 다음 바이트 수)를 돌려준다 — 없으면 -1 */
        int scan(byte[] buf, int len) {
            if (marker == null) {
                return -1;
            }
            for (int i = 0; i < len; i++) {
                int b = buf[i] & 0xff;
                int lower = b >= 'A' && b <= 'Z' ? b + 32 : b;
                if (lower == (marker[matched] & 0xff)) {
                    matched++;
                    if (matched == marker.length) {
                        hit = true;
                        return i + 1;
                    }
                } else {
                    matched = lower == (marker[0] & 0xff) ? 1 : 0;
                }
            }
            return -1;
        }
    }

    private Body readBody(InputStream in, Socket socket, Io io, long contentLength, boolean chunked) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Stop stop = new Stop(io.stopAfter());
        boolean ended;
        if (chunked) {
            ended = false;
            while (out.size() < io.maxBody() && !stop.hit) {
                String sizeLine = readLine(in, socket, io);
                if (sizeLine == null) {
                    ended = true;
                    break;
                }
                int semi = sizeLine.indexOf(';');
                String hex = (semi >= 0 ? sizeLine.substring(0, semi) : sizeLine).trim();
                if (hex.isEmpty()) {
                    continue;
                }
                long size;
                try {
                    size = Long.parseLong(hex, 16);
                } catch (NumberFormatException e) {
                    throw new IOException("bad chunk");
                }
                if (size == 0) {
                    ended = true;
                    break;
                }
                boolean done = copy(in, socket, io, out, size, stop);
                if (!done) {
                    break;
                }
                readLine(in, socket, io); // 조각 끝의 CRLF
            }
        } else if (contentLength >= 0) {
            ended = copy(in, socket, io, out, contentLength, stop);
        } else {
            ended = copy(in, socket, io, out, Long.MAX_VALUE, stop); // 연결이 닫힐 때까지
        }
        boolean truncated = !ended && !stop.hit && out.size() >= io.maxBody();
        return new Body(out.toByteArray(), truncated);
    }

    /**
     * 최대 {@code want} 바이트를 읽는다(끝에 닿으면 멈춘다). 전체 본문은 {@code maxBody} 바이트까지만 담는다 — 넘치는 부분은 읽어서 버리지 않고 멈춘다.
     * 멈춤 글자가 나오면 거기까지만 담고 멈춘다.
     *
     * @return {@code want} 를 다 읽었거나 끝에 닿았다(true) · 상한·멈춤 글자로 멈췄다(false)
     */
    private boolean copy(InputStream in, Socket socket, Io io, ByteArrayOutputStream out, long want, Stop stop) throws IOException {
        byte[] buffer = new byte[4096];
        long left = want;
        while (left > 0 && out.size() < io.maxBody()) {
            socket.setSoTimeout(Math.min(io.readTimeoutMillis(), remainingMillis(io.deadline())));
            int read = in.read(buffer, 0, (int) Math.min(buffer.length, Math.min(left, io.maxBody() - out.size())));
            if (read < 0) {
                return true;
            }
            int end = stop.scan(buffer, read);
            if (end >= 0) {
                out.write(buffer, 0, end);
                return false;
            }
            out.write(buffer, 0, read);
            left -= read;
        }
        return left <= 0;
    }

    private String readLine(InputStream in, Socket socket, Io io) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        while (true) {
            socket.setSoTimeout(Math.min(io.readTimeoutMillis(), remainingMillis(io.deadline())));
            int b = in.read();
            if (b < 0) {
                return line.size() == 0 ? null : line.toString(StandardCharsets.ISO_8859_1);
            }
            if (b == '\n') {
                String text = line.toString(StandardCharsets.ISO_8859_1);
                return text.endsWith("\r") ? text.substring(0, text.length() - 1) : text;
            }
            if (line.size() >= MAX_LINE) {
                throw new IOException("line too long");
            }
            line.write(b);
        }
    }

    /** 전체 마감까지 남은 밀리초 — 넘었으면 시간 초과 */
    private static int remainingMillis(long deadline) throws SocketTimeoutException {
        long left = (deadline - System.nanoTime()) / 1_000_000L;
        if (left <= 0) {
            throw new SocketTimeoutException("deadline");
        }
        return (int) Math.min(left, Integer.MAX_VALUE);
    }

    private static String hostHeader(Target target) {
        String host = target.host().contains(":") ? "[" + target.host() + "]" : target.host();
        int defaultPort = target.https() ? 443 : 80;
        return target.port() == defaultPort ? host : host + ":" + target.port();
    }

    private static boolean hasLineBreak(String value) {
        return value == null || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
    }

    private static boolean isIpLiteral(String host) {
        return host.indexOf(':') >= 0 || host.chars().allMatch(c -> c == '.' || Character.isDigit(c));
    }
}
