package com.community.site.dto.request;

/**
 * 「로고에 사이트 이름이 들어 있음」 바꾸기(V237). 빠뜨리면(null) 서비스가 {@code fields.nameInLogo} 로 거절한다.
 *
 * @param nameInLogo 켜면 공개 헤더가 로고 옆 이름 글자를 숨긴다
 */
public record SiteLogoNameRequest(Boolean nameInLogo) {
}
