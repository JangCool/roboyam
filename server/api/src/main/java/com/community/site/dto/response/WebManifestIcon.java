package com.community.site.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 웹 앱 매니페스트의 아이콘 한 줄 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WebManifestIcon(String src, String sizes, String type, String purpose) {
}
