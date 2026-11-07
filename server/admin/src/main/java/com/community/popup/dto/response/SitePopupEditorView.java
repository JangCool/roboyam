package com.community.popup.dto.response;

/** 마지막으로 고친 운영자 — 운영자가 지워졌으면 {@code name} null */
public record SitePopupEditorView(String id, String name) {
}
