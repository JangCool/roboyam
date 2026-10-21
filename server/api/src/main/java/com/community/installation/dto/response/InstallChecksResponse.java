package com.community.installation.dto.response;

import java.util.List;

/** @param ok {@code blocking} 인 항목이 모두 통과했는가 */
public record InstallChecksResponse(boolean ok, List<InstallCheckItem> checks) {
}
