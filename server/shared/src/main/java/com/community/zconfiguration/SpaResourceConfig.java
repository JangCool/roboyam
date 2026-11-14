package com.community.zconfiguration;

import java.io.IOException;
import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * 화면(Nuxt SPA)을 스프링이 서빙한다.
 *
 * <p>운영에 Node 런타임을 두지 않으므로 화면은 정적 파일로 빌드되어 jar 안({@code classpath:/static/})에 실린다.
 * 내보내는 자리는 각 앱의 {@code nuxt.config.ts} 의 {@code nitro.output.publicDir} 이 정한다
 * ({@code server/api}·{@code server/admin} 의 {@code src/main/resources/static}).
 *
 * <p>SPA 의 주소({@code /login} 같은)는 서버에 파일이 없고 화면 안의 라우터가 만드는 길이다. 그래서 요청한 파일이
 * 있으면 그 파일을, 없으면 {@code index.html} 을 돌려준다.
 *
 * <p>{@code /api/**} 는 컨트롤러가 먼저 받는다. 없는 API 를 부르면 여기가 아니라 {@code GlobalExceptionHandler} 가
 * 404 를 주므로 API 오답이 화면 HTML 로 돌아가지 않는다.
 */
@Configuration
public class SpaResourceConfig implements WebMvcConfigurer {

    /** 화면이 없을 때 돌려줄 파일. 빌드가 만들어 jar 에 넣는다. */
    private static final String INDEX = "/static/index.html";

    /** 빌드 산출물(JS·CSS·글꼴) 자리 — 파일 이름에 내용 해시가 붙는다. */
    private static final String BUILD_ASSETS = "/_nuxt/**";


    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 이름에 해시가 붙어 내용이 바뀌면 이름도 바뀌므로 1년 동안 다시 묻지 않게 한다(immutable).
        // 없는 조각은 index.html 이 아니라 404 다 — 배포 뒤 옛 화면이 지운 조각을 부르면 HTML 을 JS 로 읽어 깨지는 대신 Nuxt 가 새로 고친다.
        registry.addResourceHandler(BUILD_ASSETS)
                .addResourceLocations("classpath:/static/_nuxt/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());

        // 앱에 딸린 그림(로고·마스코트·기본 아바타·로보티콘 — StaticAssetUrls.FOLDERS)은 캐시 머리글을 StaticAssetCacheInterceptor 가 정한다
        // (내용 해시가 붙은 주소 ?v= 는 1년 + immutable, 해시 없는 주소는 하루). 그래서 여기서는 캐시 설정을 두지 않는다. 없는 그림은 index.html 이 아니라 404 다.
        // 예전에는 캐시 설정이 없어 보안 기본값(no-store)이 붙어 브라우저·CDN 이 저장하지 않았다 — 첫 로딩 스플래시 로고가 늦게 떴다(사용자 2026-11-14)
        for (String folder : StaticAssetUrls.FOLDERS) {
            registry.addResourceHandler(folder + "**").addResourceLocations("classpath:/static" + folder);
        }

        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource wanted = location.createRelative(resourcePath);

                        // 있는 파일이면 그대로 — /_nuxt/*.js · 그림 · 글꼴
                        if (wanted.exists() && wanted.isReadable()) {
                            return wanted;
                        }

                        // 없으면 화면 한 장을 준다. 화면을 아직 빌드하지 않았으면 null 을 돌려 평소처럼 404 가 나가게 한다
                        // (API 만 쓰는 구성이 막히면 안 되므로 예외는 던지지 않는다).
                        ClassPathResource index = new ClassPathResource(INDEX);
                        return index.exists() ? index : null;
                    }
                });
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new StaticAssetCacheInterceptor()).addPathPatterns(StaticAssetUrls.FOLDERS.stream().map(folder -> folder + "**").toList());
    }
}
