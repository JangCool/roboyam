package com.community.permission.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 서버가 뜰 때 {@code /api/admin/**} 의 모든 컨트롤러 메서드가 접근 규칙을 선언했는지 확인한다. 하나라도 빠지면 <b>기동을 멈춘다</b> —
 * 요청 때의 거절({@link AdminAccessInterceptor})이 마지막 방어선이고, 이쪽은 빠뜨린 것을 개발 중에 바로 알리는 안전장치다.
 */
@Component
public class AdminAccessVerifier implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccessVerifier.class);

    private final RequestMappingHandlerMapping handlerMapping;

    public AdminAccessVerifier(@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
        this.handlerMapping = handlerMapping;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> undeclared = new ArrayList<>();
        int checked = 0;
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            var patterns = entry.getKey().getPathPatternsCondition();
            if (patterns == null || patterns.getPatternValues().stream().noneMatch(pattern -> pattern.startsWith("/api/admin/") || pattern.equals("/api/admin"))) {
                continue;
            }
            checked++;
            if (AdminAccessInterceptor.kindOf(entry.getValue()) == AdminAccessInterceptor.Kind.NONE) {
                undeclared.add(entry.getKey() + " → " + entry.getValue().getShortLogMessage());
            }
        }
        if (!undeclared.isEmpty()) {
            throw new IllegalStateException("권한 선언(@RequirePermission · @AdminAuthenticated · @AdminPublic 중 하나)이 없는 관리자 API: " + undeclared);
        }
        log.info("관리자 API {}개의 권한 선언을 확인했다", checked);
    }
}
