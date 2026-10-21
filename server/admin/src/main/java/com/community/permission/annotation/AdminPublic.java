package com.community.permission.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 로그인 없이 부를 수 있는 API(로그인 상태 조회·로그인·로그아웃). 일부러 공개하는 것이라는 뜻으로 선언해야 한다 — 선언이 없는 API 는 거절된다.
 * {@code SecurityConfig} 의 허용 주소와 짝이다(그쪽이 열어 두지 않으면 이 선언만으로는 열리지 않는다).
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AdminPublic {
}
