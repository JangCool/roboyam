package com.community.ad.controller;

import com.community.ad.service.AdSenseSiteService;
import java.nio.charset.StandardCharsets;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /ads.txt}(IAB 표준 — 구글 애드센스 사이트 심사, docs/features/adsense-contract.md) — 애드센스가 VERIFY·SHOW 이고 게시자 ID 가 있으면
 * {@code google.com, pub-…, DIRECT, f08c47fec0942fa0} 한 줄({@code text/plain; charset=utf-8}), 아니면 본문 없는 404.
 *
 * <p>표준 형식이라 {@code ApiResponse} 로 감싸지 않는다. {@code SecurityConfig} 의 {@code anyRequest().permitAll()} 로 열려 있고, 컨트롤러가 화면 파일
 * 처리기({@code SpaResourceConfig} 의 {@code /**})보다 먼저 받으므로 404 가 화면 {@code index.html} 로 바뀌지 않는다. 확장자가 있는 주소라 {@code SiteHeadFilter} 도 받지 않는다.
 * 설정을 바꾸면 곧 따라오도록 {@code Cache-Control: no-cache}.
 */
@RestController
public class AdsTxtController {

    private static final MediaType TEXT_UTF8 = new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8);

    private final AdSenseSiteService adsense;

    public AdsTxtController(AdSenseSiteService adsense) {
        this.adsense = adsense;
    }

    @GetMapping("/ads.txt")
    public ResponseEntity<String> adsTxt() {
        return adsense.adsTxt()
                .map(body -> ResponseEntity.ok().contentType(TEXT_UTF8).cacheControl(CacheControl.noCache()).body(body))
                .orElseGet(() -> ResponseEntity.notFound().cacheControl(CacheControl.noCache()).build());
    }
}
