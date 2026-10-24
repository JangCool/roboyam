package com.community.terms.dto.request;

import jakarta.validation.constraints.NotNull;

/** 가입·로그인 화면 약관 「보기」 링크 표시 */
public record TermsSettingsRequest(@NotNull(message = "{terms.showLinks.required}") Boolean showLinks) {
}
