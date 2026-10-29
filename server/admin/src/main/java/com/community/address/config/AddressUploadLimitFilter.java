package com.community.address.config;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.util.unit.DataSize;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 관리자 앱의 파일 올리기 한도를 주소마다 가른다. 컨테이너의 multipart 한도(관리자 앱 {@code application.yml})는 주소 DB 파일 때문에 300MB 로 올렸으므로,
 * 주소 DB 올리기({@value #IMPORT_PATH}) 말고 다른 multipart 요청은 예전 한도({@code roboyam.upload.default-max-request-size}, 공유 설정의 11MB)를 넘으면
 * 본문을 읽기 전에 413 {@code FILE_TOO_LARGE} 로 막는다. 길이를 알리지 않는 요청(chunked)은 컨테이너 한도가 막는다.
 */
public class AddressUploadLimitFilter extends OncePerRequestFilter {

    static final String IMPORT_PATH = "/api/admin/address/imports";

    private final long defaultMaxBytes;
    private final ErrorResponseWriter errors;

    public AddressUploadLimitFilter(DataSize defaultMax, ErrorResponseWriter errors) {
        this.defaultMaxBytes = defaultMax.toBytes();
        this.errors = errors;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String type = request.getContentType();
        return type == null || !type.toLowerCase().startsWith("multipart/") || IMPORT_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        if (request.getContentLengthLong() > defaultMaxBytes) {
            errors.write(request, response, new CommonException(ErrorCode.FILE_TOO_LARGE));
            return;
        }
        chain.doFilter(request, response);
    }
}
