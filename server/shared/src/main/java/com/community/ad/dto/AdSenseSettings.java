package com.community.ad.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 구글 애드센스 설정(shared V176 — {@code ad_placement_settings} 의 {@code adsense_*} 칸, docs/features/adsense-contract.md 화면 확정 2026-11-10). 관리자
 * {@code GET·PUT /api/admin/ads/placements} 의 {@code adsense} 묶음이 이 모양이고, 공개 {@code GET /api/ads/placements} 는 {@link #publicView()} 로 줄여 낸다.
 * 장부 광고(사업자 신청·운영자 등록)가 늘 먼저이고, 애드센스는 그 자리에 장부 광고가 없을 때만 화면이 그린다.
 *
 * @param mode   {@code OFF} 끔 · {@code VERIFY} 사이트 확인만(머리 스크립트·{@code /ads.txt}, 단위는 그리지 않음) · {@code SHOW} 광고 표시
 * @param client 게시자 ID {@code ca-pub-} + 숫자 16자리, 없으면 null(OFF 여도 입력해 둔 값은 남긴다)
 * @param adtest 시험 표시({@code data-adtest="on"})
 * @param units  자리별 광고 단위 ID
 */
public record AdSenseSettings(String mode, String client, boolean adtest, Units units) {

    public static final String OFF = "OFF";
    public static final String VERIFY = "VERIFY";
    public static final String SHOW = "SHOW";
    public static final List<String> MODES = List.of(OFF, VERIFY, SHOW);
    /** 디스플레이 광고 단위를 쓰는 자리(응답·이력 차례) — 배너 가젯 넷 · 날개 160×600 · 글 보기 */
    public static final List<String> DISPLAY_SLOTS = List.of("WIDE_BANNER", "STRIP_BANNER", "SQUARE_BANNER", "SMALL_BANNER", "WING_BANNER", "POST_INLINE");
    /** 게시판 목록 한 줄 인피드 */
    public static final String BOARD_LIST = "BOARD_LIST";
    /** ads.txt 의 구글 인증 기관 ID(구글이 정한 고정값) */
    static final String ADS_TXT_CERT = "f08c47fec0942fa0";

    /** 저장한 적 없을 때 — 끔, 게시자 ID·단위 없음 */
    public static final AdSenseSettings DEFAULTS = new AdSenseSettings(OFF, null, false, Units.EMPTY);

    public AdSenseSettings {
        units = units == null ? Units.EMPTY : units;
    }

    /** 머리 스크립트·{@code /ads.txt}·공개 묶음을 내는 게시자 ID — VERIFY·SHOW 이고 게시자 ID 가 있을 때만 */
    public Optional<String> activeClient() {
        return OFF.equals(mode) || client == null || client.isBlank() ? Optional.empty() : Optional.of(client);
    }

    /** {@code /ads.txt} 한 줄(줄바꿈 포함) — 켜져 있지 않으면 비어 있다. {@code ca-pub-} 의 {@code ca-} 를 뗀 {@code pub-…} 를 쓴다 */
    public Optional<String> adsTxt() {
        return activeClient().map(c -> "google.com, " + (c.startsWith("ca-") ? c.substring(3) : c) + ", DIRECT, " + ADS_TXT_CERT + "\n");
    }

    /**
     * 공개 {@code GET /api/ads/placements} 의 {@code adsense} — 켜져 있지 않으면 null. {@code units} 는 SHOW 일 때만 단위 ID 가 있는 자리만(VERIFY 면 빈 묶음),
     * {@code BOARD_LIST} 는 단위 ID·레이아웃 키가 둘 다 있을 때만.
     */
    public PublicView publicView() {
        if (activeClient().isEmpty()) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        if (SHOW.equals(mode)) {
            for (String slot : DISPLAY_SLOTS) {
                String unit = units.display(slot);
                if (unit != null) {
                    out.put(slot, unit);
                }
            }
            InFeed feed = units.boardList();
            if (feed != null && feed.slot() != null && feed.layoutKey() != null) {
                out.put(BOARD_LIST, feed);
            }
        }
        return new PublicView(mode, client, adtest, out);
    }

    /** 공개 묶음 — {@code units} 는 값 있는 자리만(키 = 자리 이름) */
    public record PublicView(String mode, String client, boolean adtest, Map<String, Object> units) {
    }

    /** 자리별 광고 단위 ID(숫자 10자리, 없으면 null) — JSON 키는 자리 이름 그대로 */
    public record Units(@JsonProperty("WIDE_BANNER") String wideBanner, @JsonProperty("STRIP_BANNER") String stripBanner,
                        @JsonProperty("SQUARE_BANNER") String squareBanner, @JsonProperty("SMALL_BANNER") String smallBanner,
                        @JsonProperty("WING_BANNER") String wingBanner, @JsonProperty("POST_INLINE") String postInline,
                        @JsonProperty("BOARD_LIST") InFeed boardList) {

        public static final Units EMPTY = new Units(null, null, null, null, null, null, new InFeed(null, null));

        public Units {
            boardList = boardList == null ? new InFeed(null, null) : boardList;
        }

        /** 디스플레이 자리 이름({@link #DISPLAY_SLOTS}) → 단위 ID */
        public String display(String slot) {
            return switch (slot) {
                case "WIDE_BANNER" -> wideBanner;
                case "STRIP_BANNER" -> stripBanner;
                case "SQUARE_BANNER" -> squareBanner;
                case "SMALL_BANNER" -> smallBanner;
                case "WING_BANNER" -> wingBanner;
                case "POST_INLINE" -> postInline;
                default -> throw new IllegalArgumentException("모르는 애드센스 자리: " + slot);
            };
        }
    }

    /** 게시판 목록 한 줄 인피드 — 단위 ID + 레이아웃 키({@code -fb+5w+4e-db+86} 꼴) */
    public record InFeed(String slot, String layoutKey) {
    }
}
