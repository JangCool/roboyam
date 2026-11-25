package com.community.groupbuy.tracking;

import java.time.Instant;
import java.util.List;

/**
 * 송장 조회(계약 결정 90) — 지금 구현은 스마트택배(스윗트래커) 조회 API({@link SweetTrackerClient}). 추적 API(콜백)로 갈아 끼울 수 있게 떼어 둔다.
 * 구현은 <b>던지지 않는다</b> — 실패는 결과의 {@link Outcome}·{@code errorCode} 로. 키·송장 번호를 로그에 싣지 않는다(송장은 끝 4자리까지만).
 * 호출은 업무 트랜잭션 밖에서 한다.
 */
public interface ParcelTrackingClient {

    /** 한 송장의 지금 상태 */
    Tracking track(String apiKey, String carrierCode, String invoiceNo);

    /** API 키의 이용권 남은 양 */
    Usage usage(String apiKey);

    /** 결과 종류 */
    enum Outcome {
        /** 조회됨(단계 1 이상이거나 완료) */
        OK,
        /** 조회는 됐지만 스캔 정보가 없다(단계 0 · {@code result=N}) */
        NO_SCAN,
        /** 키 문제(101 키 없음 · 102 만료 · 103 사용량 초과) — 그 회차를 멈춘다 */
        KEY_ERROR,
        /** 같은 송장 하루 요청 제한(105) */
        DAILY_LIMIT,
        /** 운송장·택배사 코드 잘못(104) · 조회 오류(106) · HTTP 404·500 · 응답 없음 · 읽을 수 없는 응답 */
        FAILED
    }

    /**
     * 조회 결과.
     *
     * @param errorCode   실패면 짧은 코드(스마트택배 {@code 101}~{@code 106} · {@code HTTP_404} · {@code TIMEOUT} · {@code ERROR} · 스캔 없음 {@code NO_SCAN}), 성공이면 null
     * @param level       단계 0 스캔 없음 · 1 배송준비중 · 2 집화완료 · 3 배송중 · 4 지점 도착 · 5 배송출발 · 6 배송 완료(실패면 null)
     * @param complete    택배사가 배송 완료라고 했다({@code complete} 또는 단계 6)
     * @param events      진행 이력(시각 차례) — 시각·진행 상태 글·위치·단계만
     * @param completedAt 완료 단계 이력의 시각(KST {@code timeString} → 순간). 완료가 아니거나 시각이 없으면 null
     */
    record Tracking(Outcome outcome, String errorCode, Integer level, boolean complete, List<Event> events, Instant completedAt) {

        public static Tracking failed(Outcome outcome, String errorCode) {
            return new Tracking(outcome, errorCode, null, false, List.of(), null);
        }
    }

    /** 이력 한 줄 — 기사 이름·전화는 받지 않는다 */
    record Event(Instant time, String kind, String where, int level) {
    }

    /**
     * 이용권 남은 양 — 실패면 {@code outcome != OK} 이고 값은 null.
     *
     * @param startDate 이용 시작일(응답 글 그대로) · {@code endDate} 끝나는 날
     */
    record Usage(Outcome outcome, String errorCode, String startDate, String endDate, Long totalAmount, Long leftAmount) {

        public static Usage failed(Outcome outcome, String errorCode) {
            return new Usage(outcome, errorCode, null, null, null, null);
        }
    }

    /** 로그에 싣는 송장 번호 — 끝 4자리만 */
    static String masked(String invoiceNo) {
        if (invoiceNo == null || invoiceNo.length() <= 4) {
            return "****";
        }
        return "*".repeat(Math.min(6, invoiceNo.length() - 4)) + invoiceNo.substring(invoiceNo.length() - 4);
    }
}
