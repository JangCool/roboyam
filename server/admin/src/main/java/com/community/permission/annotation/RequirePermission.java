package com.community.permission.annotation;

import com.community.permission.entity.Permission;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 API 를 부르려면 운영자가 가져야 하는 권한을 선언한다. {@code AdminAccessInterceptor} 가 요청마다 운영자의 <b>현재</b> 권한(역할의 합집합)을 읽어 검사하고,
 * 모자라면 {@code 403 PERMISSION_DENIED} 와 감사 기록으로 남긴다. 최고 관리자(루트)는 모든 권한을 가진다.
 *
 * <p>{@code /api/admin/**} 의 모든 핸들러는 {@code @RequirePermission} · {@link AdminAuthenticated} · {@link AdminPublic} 중 <b>하나</b>를 선언해야 한다.
 * 선언이 없으면 거절되고(fail-closed) 서버가 뜰 때도 막힌다. 클래스에 선언하면 모든 메서드의 기본값이고 메서드 선언이 이긴다.
 *
 * <p>이 선언은 <b>입구</b>의 검사다. 요청 내용에 따라 달라지는 검사(역할을 줄 때의 권한 상승 방지, 상태 칸마다 다른 권한)는 서비스가 {@code AdminActor} 로 이어서 한다.
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /** 모두 가져야 하는 권한 */
    Permission[] value() default {};

    /** 이 중 하나 이상 가져야 하는 권한 — 요청 내용에 따라 필요한 권한이 갈리는 API 의 입구(세부는 서비스가 다시 검사한다) */
    Permission[] anyOf() default {};
}
