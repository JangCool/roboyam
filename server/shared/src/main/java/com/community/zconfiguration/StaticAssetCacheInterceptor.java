package com.community.zconfiguration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 앱에 딸린 그림({@link StaticAssetUrls#FOLDERS})의 캐시 머리글 — 내용 해시가 붙은 요청({@code ?v=})은 1년 + {@code immutable}(그림이 바뀌면 주소가 바뀐다),
 * 해시 없이 온 요청은 하루(미처 해시를 못 붙인 곳이 있어도 옛 그림이 하루 넘게 남지 않게). 없는 그림에는 붙이지 않는다(404 를 오래 담지 않게 — 보안 기본값 no-store 가 붙는다).
 * 정적 파일 처리기({@link SpaResourceConfig})는 이 폴더에 캐시 설정을 두지 않으므로 여기서 건 값이 그대로 나간다.
 */
public class StaticAssetCacheInterceptor implements HandlerInterceptor {

    static final String VERSIONED = "max-age=31536000, public, immutable";
    static final String UNVERSIONED = "max-age=86400, public";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (StaticAssetUrls.version(path).isPresent()) {
            response.setHeader(HttpHeaders.CACHE_CONTROL, request.getParameter(StaticAssetUrls.VERSION_PARAM) != null ? VERSIONED : UNVERSIONED);
        }
        return true;
    }
}
