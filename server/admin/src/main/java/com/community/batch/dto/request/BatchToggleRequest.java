package com.community.batch.dto.request;

import jakarta.validation.constraints.NotNull;

/** 배치 켜기·끄기 */
public record BatchToggleRequest(@NotNull(message = "{batch.enabled.required}") Boolean enabled) {
}
