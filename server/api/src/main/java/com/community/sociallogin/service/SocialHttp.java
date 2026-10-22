package com.community.sociallogin.service;

import com.community.sociallogin.config.SocialLoginProperties;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 공급자(코드에 고정한 주소 — {@code SocialEndpoints})에 보내는 작은 HTTP 클라이언트.
 *
 * <ul>
 *   <li>접속·응답 제한 시간이 짧다({@code roboyam.social.connect-timeout}·{@code read-timeout}, 기본 3초·5초). 리다이렉트를 따라가지 않는다.</li>
 *   <li>응답 본문은 {@value #MAX_BODY} 바이트까지만 읽고 JSON 으로만 해석한다. 본문은 <b>부르는 쪽 안에서만</b> 쓰고 사용자·로그에 내보내지 않는다.</li>
 *   <li>실패는 {@link SocialProviderException}(짧은 사유만, 본문·토큰·시크릿 없음)이다.</li>
 * </ul>
 */
@Component
public class SocialHttp {

    static final int MAX_BODY = 64 * 1024;
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final HttpClient client;
    private final SocialLoginProperties props;

    public SocialHttp(SocialLoginProperties props) {
        this.props = props;
        this.client = HttpClient.newBuilder()
                .connectTimeout(props.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /** 폼(application/x-www-form-urlencoded)을 POST 하고 JSON 응답을 읽는다 — 토큰 교환 */
    public Response postForm(String url, Map<String, String> form) {
        StringJoiner body = new StringJoiner("&");
        form.forEach((key, value) -> {
            if (value != null) {
                body.add(URLEncoder.encode(key, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8));
            }
        });
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(props.readTimeout())
                .header("Content-Type", "application/x-www-form-urlencoded;charset=utf-8")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        return send(request);
    }

    /** 액세스 토큰으로 GET 하고 JSON 응답을 읽는다 — 회원 정보 */
    public Response getWithBearer(String url, String accessToken) {
        if (accessToken.indexOf('\r') >= 0 || accessToken.indexOf('\n') >= 0) {
            throw new SocialProviderException("액세스 토큰 형식");
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(props.readTimeout())
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .GET()
                .build();
        return send(request);
    }

    /** 본문까지 다 읽는 데 전체 마감 시간을 둔다(조금씩 흘려 보내는 응답에 매이지 않게) — 응답 제한 시간 + 접속 제한 시간 */
    private Response send(HttpRequest request) {
        CompletableFuture<Response> pending = client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).thenApply(SocialHttp::read);
        try {
            return pending.get(props.readTimeout().plus(props.connectTimeout()).toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            pending.cancel(true);
            throw new SocialProviderException("시간 초과");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof SocialProviderException failure) {
                throw failure;
            }
            throw new SocialProviderException("통신 실패 " + (cause == null ? "?" : cause.getClass().getSimpleName()));
        } catch (InterruptedException e) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw new SocialProviderException("중단됨");
        } catch (IllegalArgumentException e) {
            throw new SocialProviderException("요청을 만들지 못했다");
        }
    }

    private static Response read(HttpResponse<InputStream> response) {
        byte[] bytes;
        try (InputStream in = response.body()) {
            bytes = in.readNBytes(MAX_BODY + 1);
        } catch (IOException e) {
            throw new SocialProviderException("응답을 읽지 못했다 " + e.getClass().getSimpleName());
        }
        if (bytes.length > MAX_BODY) {
            throw new SocialProviderException("응답이 너무 크다");
        }
        JsonNode json = bytes.length == 0 ? null : parse(bytes);
        return new Response(response.statusCode(), json);
    }

    private static JsonNode parse(byte[] bytes) {
        try {
            return MAPPER.readTree(bytes);
        } catch (RuntimeException e) {
            return null; // JSON 이 아니다 — 부르는 쪽이 실패로 본다
        }
    }

    /**
     * 공급자 응답. 본문은 부르는 쪽 안에서만 쓴다.
     *
     * @param json JSON 이 아니거나 비었으면 null
     */
    public record Response(int status, JsonNode json) {

        public boolean ok() {
            return status >= 200 && status < 300 && json != null && json.isObject();
        }

        @Override
        public String toString() {
            return "Response[status=" + status + "]";
        }
    }
}
