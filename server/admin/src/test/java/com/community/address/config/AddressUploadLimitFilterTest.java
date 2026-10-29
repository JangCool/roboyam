package com.community.address.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.unit.DataSize;

/** 큰 파일은 주소 DB 올리기만 — 다른 multipart 는 예전 한도(11MB)를 넘으면 본문을 읽기 전에 413 */
class AddressUploadLimitFilterTest {

    private static final long MB = 1024L * 1024L;

    /** 쓴 오류 코드만 남긴다 */
    private static final class Recording extends ErrorResponseWriter {
        String code;

        Recording() {
            super(null);
        }

        @Override
        public void write(HttpServletRequest request, HttpServletResponse response, CommonException e) {
            code = e.getErrorCode();
            response.setStatus(e.getStatus().value());
        }
    }

    /** 본문 없이 길이만 알리는 요청(필터는 본문을 읽지 않는다) */
    private static MockHttpServletRequest request(String method, String uri, String contentType, long length) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri) {
            @Override
            public long getContentLengthLong() {
                return length;
            }
        };
        request.setContentType(contentType);
        return request;
    }

    private record Outcome(boolean passed, int status, String code) {
    }

    private static Outcome run(MockHttpServletRequest request) throws Exception {
        Recording errors = new Recording();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        new AddressUploadLimitFilter(DataSize.ofMegabytes(11), errors).doFilter(request, response, chain);
        return new Outcome(chain.getRequest() != null, response.getStatus(), errors.code);
    }

    @Test
    void 다른_주소의_큰_multipart_는_413() throws Exception {
        assertThat(run(request("POST", "/api/admin/site/logo", "multipart/form-data; boundary=x", 12 * MB))).isEqualTo(new Outcome(false, 413, "FILE_TOO_LARGE"));
    }

    @Test
    void 다른_주소도_한도_안이면_지나간다() throws Exception {
        assertThat(run(request("POST", "/api/admin/site/logo", "multipart/form-data; boundary=x", 11 * MB))).isEqualTo(new Outcome(true, 200, null));
    }

    @Test
    void 주소_DB_올리기는_큰_파일도_지나간다() throws Exception {
        assertThat(run(request("POST", "/api/admin/address/imports", "multipart/form-data; boundary=x", 290 * MB))).isEqualTo(new Outcome(true, 200, null));
    }

    @Test
    void multipart_가_아니면_보지_않는다() throws Exception {
        assertThat(run(request("PUT", "/api/admin/address/settings", "application/json", 20 * MB))).isEqualTo(new Outcome(true, 200, null));
    }
}
