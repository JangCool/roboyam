package com.community.media.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.media.service.FakeS3Client;
import com.community.media.service.S3ImageStorage;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.CacheControl;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

/**
 * S3 저장소의 {@code /media/**} — Spring 정적 리소스 처리기에 {@link BucketMediaResolver} 만 끼운 모양({@code MediaResourceConfig} 와 같다)으로
 * 있는 키는 200·바이트·Content-Type·1년 캐시, 없는 키는 404. 경로 탈출 키 거절은 {@code S3ImageStorageTest}.
 */
class BucketMediaResolverTest {

    private final FakeS3Client client = new FakeS3Client();

    private ResourceHttpRequestHandler handler() throws Exception {
        ResourceHttpRequestHandler handler = new ResourceHttpRequestHandler();
        handler.setResourceResolvers(List.of(new BucketMediaResolver(new S3ImageStorage(client, "media", null))));
        handler.setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
        handler.setServletContext(new MockServletContext());
        handler.afterPropertiesSet();
        return handler;
    }

    private static MockHttpServletRequest get(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/media/" + path);
        request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, path);
        return request;
    }

    @Test
    void servesTheObjectWithCacheHeaders() throws Exception {
        client.objects.put("post-image/202611/abc.png", new byte[] {1, 2, 3, 4});
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler().handleRequest(get("post-image/202611/abc.png"), response);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsByteArray()).containsExactly(1, 2, 3, 4);
        assertThat(response.getContentType()).isEqualTo("image/png");
        assertThat(response.getHeader("Cache-Control")).contains("max-age=31536000").contains("immutable");
    }

    /** 로컬 디스크와 같은 길 — 처리기가 NoResourceFoundException 을 던지고 MVC 가 404 로 바꾼다 */
    @Test
    void missingObjectIsNoResourceFound() throws Exception {
        ResourceHttpRequestHandler handler = handler();

        assertThatThrownBy(() -> handler.handleRequest(get("post-image/202611/none.png"), new MockHttpServletResponse()))
                .isInstanceOf(NoResourceFoundException.class);
    }
}
