package com.community.site.dto;

/** 검사를 마친 사이트 입력(이름은 앞뒤 공백을 자르고, 주소는 스킴·호스트를 소문자로 맞춘 값, 슬로건은 앞뒤 공백을 자르고 비면 {@code null}) */
public record SiteValues(String name, String publicUrl, String tagline) {
}
