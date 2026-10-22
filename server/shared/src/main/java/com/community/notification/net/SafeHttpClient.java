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
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 운영자가 입력한 주소로 JSON 을 한 번 POST 하는 작은 HTTP/1.1 클라이언트 — {@link OutboundTargetGuard} 가 <b>검사한 그 IP</b> 로만 연결한다(SSRF 방어).
 *
 * <p>JDK {@code HttpClient} 를 쓰지 않는 이유: 거기서는 연결 때 이름을 <b>다시 풀기</b> 때문에 검사한 IP 와 연결하는 IP 가 달라질 수 있다(DNS 재해석 우회).
 * 여기서는 검사가 돌려준 IP 로 소켓을 직접 열고, https 는 그 소켓 위에 TLS 를 올려 <b>호스트 이름으로 인증서를 확인</b>한다(SNI·호스트 이름 검증 켜짐).
 *
 * <ul>
 *   <li><b>리다이렉트를 따라가지 않는다</b> — 3xx 는 그냥 상태 코드로 돌려준다(호출하는 쪽이 실패로 본다).</li>
 *   <li>연결·읽기 제한 시간과 <b>전체 마감 시간</b>이 있다(조금씩 흘려 보내는 응답에 매이지 않는다). 응답 본문은 {@value #MAX_BODY} 바이트까지만 읽는다.</li>
 *   <li>응답 본문은 호출하는 쪽(발송기)만 보고, 사용자에게 돌려주지 않는다 — 결과는 성공/실패와 짧은 사유 코드뿐이다.</li>
 *   <li>헤더 값에 줄바꿈이 있으면 거절한다(헤더 주입 방지). {@code Accept-Encoding} 을 보내지 않아 압축 없이 받는다.</li>
 * </ul>
 */
@Component
public class SafeHttpClient {

    /** 응답 본문을 읽는 최대 바이트 */
    static final int MAX_BODY = 64 * 1024;
    private static final int MAX_LINE = 8 * 1024;
    private static final int MAX_HEADERS = 100;

    /** 응답 — 본문은 최대 {@value #MAX_BODY} 바이트(잘렸을 수 있다) */
    public record Response(int status, String body) {
    }

    private final OutboundTargetGuard guard;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;
    private final int totalTimeoutMillis;

    public SafeHttpClient(OutboundTargetGuard guard, @Value("${roboyam.notification.connect-timeout-millis:3000}") int connectTimeoutMillis,
                          @Value("${roboyam.notification.read-timeout-millis:5000}") int readTimeoutMillis,
                          @Value("${roboyam.notification.total-timeout-millis:10000}") int totalTimeoutMillis) {
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
        IOException last = null;
        int tried = 0;
        for (InetAddress address : target.addresses()) {
            if (tried++ >= 3) {
                break;
            }
            try {
                return exchange(target, address, headers, jsonBody.getBytes(StandardCharsets.UTF_8), deadline);
            } catch (SocketTimeoutException e) {
                throw e; // 시간 초과는 다른 주소로 재시도하지 않는다 — 전체 마감을 넘기게 된다
            } catch (IOException e) {
                last = e;
            }
        }
        throw last != null ? last : new IOException("no address");
    }

    private Response exchange(Target target, InetAddress address, Map<String, String> headers, byte[] body, long deadline) throws IOException {
        Socket plain = new Socket();
        try {
            plain.connect(new InetSocketAddress(address, target.port()), Math.min(connectTimeoutMillis, remainingMillis(deadline)));
            plain.setSoTimeout(Math.min(readTimeoutMillis, remainingMillis(deadline)));
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
            write(socket.getOutputStream(), target, headers, body);
            return read(socket, deadline);
        } finally {
            try {
                plain.close();
            } catch (IOException ignored) {
                // 이미 닫힘
            }
        }
    }

    private void write(OutputStream out, Target target, Map<String, String> headers, byte[] body) throws IOException {
        StringBuilder head = new StringBuilder();
        head.append("POST ").append(target.requestTarget()).append(" HTTP/1.1\r\n");
        head.append("Host: ").append(hostHeader(target)).append("\r\n");
        head.append("User-Agent: roboyam-notification\r\n");
        head.append("Accept: application/json\r\n");
        head.append("Content-Type: application/json; charset=utf-8\r\n");
        head.append("Content-Length: ").append(body.length).append("\r\n");
        for (Map.Entry<String, String> header : headers.entrySet()) {
            String name = header.getKey();
            String value = header.getValue();
            if (hasLineBreak(name) || hasLineBreak(value) || name.isBlank()) {
                throw new IllegalArgumentException("header");
            }
            head.append(name).append(": ").append(value).append("\r\n");
        }
        head.append("Connection: close\r\n\r\n");
        out.write(head.toString().getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
        out.flush();
    }

    private Response read(Socket socket, long deadline) throws IOException {
        InputStream in = socket.getInputStream();
        String statusLine = readLine(in, socket, deadline);
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
        for (int i = 0; i <= MAX_HEADERS; i++) {
            String line = readLine(in, socket, deadline);
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
            if (name.equals("content-length")) {
                try {
                    contentLength = Long.parseLong(value);
                } catch (NumberFormatException e) {
                    contentLength = -1;
                }
            } else if (name.equals("transfer-encoding") && value.toLowerCase(Locale.ROOT).contains("chunked")) {
                chunked = true;
            }
        }
        byte[] body = (status == 204 || status == 304 || (status >= 100 && status < 200)) ? new byte[0] : readBody(in, socket, deadline, contentLength, chunked);
        return new Response(status, new String(body, StandardCharsets.UTF_8));
    }

    private byte[] readBody(InputStream in, Socket socket, long deadline, long contentLength, boolean chunked) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (chunked) {
            while (out.size() < MAX_BODY) {
                String sizeLine = readLine(in, socket, deadline);
                if (sizeLine == null) {
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
                    break;
                }
                copy(in, socket, deadline, out, size);
                readLine(in, socket, deadline); // 조각 끝의 CRLF
            }
        } else if (contentLength >= 0) {
            copy(in, socket, deadline, out, contentLength);
        } else {
            copy(in, socket, deadline, out, Long.MAX_VALUE); // 연결이 닫힐 때까지
        }
        return out.toByteArray();
    }

    /** 최대 {@code want} 바이트를 읽는다(끝에 닿으면 멈춘다). 전체 본문은 {@value #MAX_BODY} 바이트까지만 담는다 — 넘치는 부분은 읽어서 버리지 않고 멈춘다 */
    private void copy(InputStream in, Socket socket, long deadline, ByteArrayOutputStream out, long want) throws IOException {
        byte[] buffer = new byte[4096];
        long left = want;
        while (left > 0 && out.size() < MAX_BODY) {
            socket.setSoTimeout(Math.min(readTimeoutMillis, remainingMillis(deadline)));
            int read = in.read(buffer, 0, (int) Math.min(buffer.length, Math.min(left, MAX_BODY - out.size())));
            if (read < 0) {
                return;
            }
            out.write(buffer, 0, read);
            left -= read;
        }
    }

    private String readLine(InputStream in, Socket socket, long deadline) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        while (true) {
            socket.setSoTimeout(Math.min(readTimeoutMillis, remainingMillis(deadline)));
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
