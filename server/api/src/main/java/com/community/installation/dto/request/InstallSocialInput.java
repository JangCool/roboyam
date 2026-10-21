package com.community.installation.dto.request;

import java.util.List;

/** @param providers 공급자는 최대 3개이고 중복될 수 없다. 빈 목록은 건너뜀과 같다 */
public record InstallSocialInput(List<InstallSocialProviderInput> providers) {
}
