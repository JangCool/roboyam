package com.community.api;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 공개 사이트 서버(user-web 용).
 *
 * <p>업무 패키지는 모듈과 상관없이 {@code com.community.<업무>} 이고 공통 설정은 {@code com.community.zconfiguration} 이다. 이 클래스는 그 위(자기 모듈 이름의
 * 패키지)에 있으므로, 컴포넌트·설정 속성·엔티티의 스캔 범위를 {@code com.community} 로 명시한다. 클래스패스에는 shared 와 이 모듈만 있다(admin 과 서로 의존하지 않는다).
 * 저장소(JPA)의 스캔 범위와 바탕 클래스는 {@code JpaMasterConfig} 가 정한다({@code ...repository.master}).
 */
@SpringBootApplication(scanBasePackages = "com.community")
@ConfigurationPropertiesScan("com.community")
@EntityScan("com.community")
@EnableScheduling
public class ApiApplication {

    public static void main(String[] args) {
        // JVM 기본 시간대는 UTC 로 명시한다(호스트 시간대에 기대지 않는다 — DB 세션 시간대도 JVM 을 따라간다). 다른 일보다 먼저 한다
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(ApiApplication.class, args);
    }
}
