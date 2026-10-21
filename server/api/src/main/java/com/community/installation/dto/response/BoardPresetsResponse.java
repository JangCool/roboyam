package com.community.installation.dto.response;

import java.util.List;

/** {@code GET …/board-presets} — 프리셋 목록과 기본값 */
public record BoardPresetsResponse(String defaultId, List<BoardPresetItem> presets) {
}
