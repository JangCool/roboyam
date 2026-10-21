package com.community.site.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * {@code /manifest.webmanifest} — 웹 앱 매니페스트(W3C 형식이라 {@code ApiResponse} 로 감싸지 않는다. 키 이름이 표준의 snake_case 다).
 * 사이트 이름·소개·표시 언어는 설정을 따라가고, 아이콘은 올려 둔 192·512 PNG 또는 기본 SVG 아이콘이다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WebManifestResponse(String name, @JsonProperty("short_name") String shortName, String description, String lang,
                                  @JsonProperty("start_url") String startUrl, String scope, String display,
                                  @JsonProperty("background_color") String backgroundColor, @JsonProperty("theme_color") String themeColor,
                                  List<WebManifestIcon> icons) {
}
