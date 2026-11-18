package com.community.adultboard.dto.request;

/** {@code PUT /api/me/adult-board/relock-mode} — {@code { relockMode: IDLE|FIXED }} */
public record AdultBoardRelockModeRequest(String relockMode) {
}
