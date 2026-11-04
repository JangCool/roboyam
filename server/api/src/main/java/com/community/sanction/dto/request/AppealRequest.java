package com.community.sanction.dto.request;

/**
 * {@code POST /api/me/appeals} — 사이트 제재({@code sanctionId})와 게시판 제재({@code boardSanctionId}) 중 <b>하나만</b>(board-sanction-contract 결정 4).
 * 검사는 서비스가 한다(사유 10~500자 {@code fields.reason}).
 */
public record AppealRequest(String sanctionId, String boardSanctionId, String reason) {
}
