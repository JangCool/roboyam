package com.community.media.config;

import com.community.media.service.ImageStorage;
import com.community.media.service.LocalDiskImageStorage;
import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * {@code /media/**} 서빙 — 공개 사이트(api)와 관리자 앱(admin)이 <b>둘 다</b> 같은 폴더를 내보낸다(관리자 화면과 공개 화면이 각자 자기 주소에서 읽는다).
 * 로컬 디스크 저장소일 때만 켜진다. 키에 버전 토큰이 들어 있어 내용이 바뀌면 주소가 바뀌므로 1년 immutable 캐시를 건다.
 *
 * <ul>
 *   <li>{@code /media/default/**}: jar 안에 든 기본 이미지(기본 사이트 아이콘·링크 미리보기 이미지). 주소가 고정이라 하루만 캐시한다.</li>
 *   <li>그 밖의 {@code /media/**}: 저장소 폴더의 파일. 폴더 밖으로 나가는 경로는 Spring 의 리소스 해석기가 막는다. 없으면 404.</li>
 * </ul>
 * 로그인 없이 열린다({@code SecurityConfig} 의 {@code anyRequest().permitAll()}) — 로고·아이콘·링크 미리보기 이미지는 공개 그림이다.
 */
@Configuration
public class MediaResourceConfig implements WebMvcConfigurer {

    private final ImageStorage storage;

    public MediaResourceConfig(ImageStorage storage) {
        this.storage = storage;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/media/default/**")
                .addResourceLocations("classpath:/media-default/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic());
        if (storage instanceof LocalDiskImageStorage local) {
            String location = local.root().toUri().toString();
            registry.addResourceHandler("/media/**")
                    .addResourceLocations(location.endsWith("/") ? location : location + "/")
                    .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
        }
    }
}
