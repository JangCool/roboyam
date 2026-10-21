package com.community.permission.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 로그인한 운영자라면 누구나 부를 수 있는 API(특정 권한이 필요 없다). 로그인 상태는 요청마다 현재 계정 상태로 다시 확인한다. {@link RequirePermission} 과 함께 쓰지 않는다 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AdminAuthenticated {
}
