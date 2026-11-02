package com.community.ad.dto;

/** 광고주 관리 처리 본문 — 검사는 서비스(400 {@code fields.*}) */
public final class AdAdvertiserAdminRequest {

    private AdAdvertiserAdminRequest() {
    }

    /** 새 광고주(직접 등록) — 기존 {@code POST /api/admin/ads/advertisers} 와 같은 칸(사업자 연결은 없다) */
    public record Create(String name, String contactName, String contactPhone, String email, String memo) {
    }

    /** 고치기 — 빠진 칸(null)은 그대로 · 빈 글자는 지우기 · 값은 바꾸기 */
    public record Update(String name, String contactName, String contactPhone, String email, String memo) {
    }
}
