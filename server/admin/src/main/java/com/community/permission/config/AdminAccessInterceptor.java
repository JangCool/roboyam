package com.community.permission.config;

import com.community.adminauth.controller.AdminSession;
import com.community.permission.annotation.AdminAuthenticated;
import com.community.permission.annotation.AdminPublic;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.permission.exception.PermissionDeniedException;
import com.community.permission.service.AccessDeniedRecorder;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * <b>{@code /api/admin/**} 의 모든 요청이 거치는 권한 검사</b>(fail-closed).
 *
 * <ul>
 *   <li>핸들러는 {@link RequirePermission} · {@link AdminAuthenticated} · {@link AdminPublic} 중 <b>정확히 하나</b>를 선언해야 한다.
 *       선언이 없거나 여럿이거나 컨트롤러 메서드가 아닌 핸들러(정적 자원 등)면 <b>거절</b>한다 — 새 컨트롤러가 검사를 빼먹어도 열리지 않는다.</li>
 *   <li>로그인이 필요한 선언이면 세션의 운영자를 <b>요청마다 DB 에서 다시 읽어</b>({@link AdminSession#resolve}) 현재 상태(승인·잠금·사용·기간)와 권한 합집합으로 판단한다.
 *       캐시하지 않으므로 권한·상태 변경이 다음 요청부터 반영된다. 로그인하지 않았거나 세션이 낡았으면 {@code 401 UNAUTHENTICATED}.</li>
 *   <li>권한이 모자라면 {@code 403 PERMISSION_DENIED} 이고 감사 기록(결과 DENIED)을 남긴다. 최고 관리자(루트)는 모든 권한을 가진다.</li>
 * </ul>
 * 통과한 요청에는 {@link AdminActor} 를 요청 속성({@link #ACTOR_ATTRIBUTE})에 실어 컨트롤러가 인자로 받게 한다.
 */
@Component
public class AdminAccessInterceptor implements HandlerInterceptor {

    /** 이 요청의 {@link AdminActor} 를 싣는 요청 속성 이름 */
    public static final String ACTOR_ATTRIBUTE = AdminAccessInterceptor.class.getName() + ".ACTOR";

    private static final Logger log = LoggerFactory.getLogger(AdminAccessInterceptor.class);

    private final AdminSession adminSession;
    private final AccessDeniedRecorder denied;

    public AdminAccessInterceptor(AdminSession adminSession, AccessDeniedRecorder denied) {
        this.adminSession = adminSession;
        this.denied = denied;
    }

    /** 핸들러가 선언한 접근 규칙 — 선언 개수가 하나가 아니면 {@code NONE}(거절) */
    enum Kind {
        PUBLIC,
        AUTHENTICATED,
        PERMISSION,
        NONE
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Kind kind = handler instanceof HandlerMethod method ? kindOf(method) : Kind.NONE;
        if (kind == Kind.PUBLIC) {
            return true;
        }

        // 로그인이 필요한 요청 — 거절하는 경우에도 먼저 로그인을 확인해 누가 두드렸는지 남긴다
        Optional<AdminActor> resolved = adminSession.resolve(request);
        if (resolved.isEmpty()) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        AdminActor actor = resolved.get();
        String endpoint = request.getMethod() + " " + request.getRequestURI();

        if (kind == Kind.NONE) {
            // 선언이 없는(또는 어긋난) 핸들러 — 열어 두지 않는다. 개발자가 고쳐야 하는 결함이라 오류로 남긴다
            log.error("권한 선언이 없는 관리자 API 를 막았다: {}", endpoint);
            denied.record(actor, "UNDECLARED", "ENDPOINT", endpoint, List.of());
            throw new PermissionDeniedException();
        }

        if (kind == Kind.PERMISSION) {
            RequirePermission required = requirementOf((HandlerMethod) handler);
            List<String> missing = missingOf(actor, required);
            if (!missing.isEmpty()) {
                denied.record(actor, "MISSING_PERMISSION", "ENDPOINT", endpoint, missing);
                throw new PermissionDeniedException();
            }
        }

        request.setAttribute(ACTOR_ATTRIBUTE, actor);
        return true;
    }

    /** {@code value} 중 못 가진 것, {@code anyOf} 가 있으면 하나도 못 가졌을 때 그 전부. 비어 있으면 통과 */
    private static List<String> missingOf(AdminActor actor, RequirePermission required) {
        List<String> missing = Arrays.stream(required.value()).filter(permission -> !actor.can(permission)).map(Permission::code).toList();
        if (!missing.isEmpty()) {
            return missing;
        }
        if (required.anyOf().length > 0 && Arrays.stream(required.anyOf()).noneMatch(actor::can)) {
            return Arrays.stream(required.anyOf()).map(Permission::code).toList();
        }
        return List.of();
    }

    /** 메서드의 선언이 이기고, 메서드에 없으면 클래스의 선언을 본다. 선언이 정확히 하나여야 한다 */
    static Kind kindOf(HandlerMethod handler) {
        Kind onMethod = declared(handler.getMethod());
        if (onMethod != null) {
            return onMethod;
        }
        Kind onClass = declared(handler.getBeanType());
        return onClass != null ? onClass : Kind.NONE;
    }

    /** 이 요소에 선언된 규칙. 선언이 없으면 null, 둘 이상이면 {@code NONE} */
    private static Kind declared(java.lang.reflect.AnnotatedElement element) {
        boolean isPublic = AnnotatedElementUtils.hasAnnotation(element, AdminPublic.class);
        boolean isAuthenticated = AnnotatedElementUtils.hasAnnotation(element, AdminAuthenticated.class);
        boolean isPermission = AnnotatedElementUtils.hasAnnotation(element, RequirePermission.class);
        int count = (isPublic ? 1 : 0) + (isAuthenticated ? 1 : 0) + (isPermission ? 1 : 0);
        if (count == 0) {
            return null;
        }
        if (count > 1) {
            return Kind.NONE;
        }
        return isPublic ? Kind.PUBLIC : isAuthenticated ? Kind.AUTHENTICATED : Kind.PERMISSION;
    }

    private static RequirePermission requirementOf(HandlerMethod handler) {
        RequirePermission onMethod = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), RequirePermission.class);
        return onMethod != null ? onMethod : AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), RequirePermission.class);
    }
}
