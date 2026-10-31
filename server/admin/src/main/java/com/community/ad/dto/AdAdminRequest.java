package com.community.ad.dto;

import java.util.List;

/** 광고 일정표 처리 본문 — 검사는 서비스(400 {@code fields.*}) */
public final class AdAdminRequest {

    private AdAdminRequest() {
    }

    public record Period(String startAt, String endAt) {
    }

    /** 소재 — 배너 {@code imageFileId}(운영자가 올린 이미지)·{@code linkUrl}(https)·{@code altText}, 한 줄 {@code text}(40자)·{@code linkUrl} */
    public record Creative(String imageFileId, String text, String linkUrl, String altText) {
    }

    /**
     * 운영자 등록(심사 요청) — @param advertiserId {@code adv-…}·{@code biz-…} · @param type 광고 종류(배너 넷·한 줄) · @param weight 1~10 · @param supply 계약 금액 제안(공급가) ·
     * @param contractFileIds 미리 올린 계약서 사본(선택, 10장까지)
     */
    public record Manual(String advertiserId, String type, Period period, Integer weight, Creative creative, Long supply, List<String> contractFileIds, String memo) {
    }

    /** 새 광고주 — @param businessId 연결할 사업자 신원 ID(선택) */
    public record AdvertiserCreate(String name, String contactName, String contactPhone, String email, String memo, String businessId) {
    }

    public record Reveal(String reason) {
    }

    public record Takedown(String reason, Boolean notifyMail) {
    }

    /** @param compensation EXTEND(끝 시각 연장)·PRORATE_REFUND(일할 환불) */
    public record Restore(String compensation, String reason) {
    }
}
