package com.community.permission.config;

import com.community.permission.dto.AdminActor;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 관리자 API 의 웹 구성 — {@code /api/admin/**} 의 모든 요청에 권한 검사 인터셉터({@link AdminAccessInterceptor})를 걸고,
 * 컨트롤러가 {@link AdminActor} 를 인자로 받을 수 있게 한다.
 */
@Configuration
public class AdminWebConfig implements WebMvcConfigurer {

    private final AdminAccessInterceptor interceptor;

    public AdminWebConfig(AdminAccessInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/admin/**");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return AdminActor.class.equals(parameter.getParameterType());
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest,
                                          WebDataBinderFactory binderFactory) {
                Object actor = webRequest.getAttribute(AdminAccessInterceptor.ACTOR_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
                if (actor == null) {
                    // 로그인이 필요한 선언을 한 API 만 행위자를 받을 수 있다(@AdminPublic 은 받을 수 없다) — 코드 결함
                    throw new IllegalStateException("AdminActor 를 받으려면 @RequirePermission 또는 @AdminAuthenticated 를 선언해야 한다");
                }
                return actor;
            }
        });
    }
}
