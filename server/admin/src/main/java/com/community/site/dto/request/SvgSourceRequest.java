package com.community.site.dto.request;

/**
 * SVG 소스를 로고·사이트 아이콘으로 삼는 요청. 검사는 서비스가 한다(100,000자 이하·위험 요소 거절·아이콘은 정사각).
 *
 * @param svgSource SVG 마크업 원문. {@code xmlns} 가 없으면 서버가 넣는다
 */
public record SvgSourceRequest(String svgSource) {
}
