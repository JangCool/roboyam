package com.community.post.dto.response;

import java.time.Instant;

/**
 * 처리 상태를 바꾼 뒤의 값(공개·관리자 같은 모양 — user-web {@code PostProcessStatusResult}).
 *
 * @param processStatus     지금 상태({@code PostProcessStatus} 이름)
 * @param processStatusAt   마지막으로 바꾼 시각(값이 없던 글에 RECEIVED 를 주면 그때 시각)
 * @param processStatusNote 마지막 상태 변경의 메모(V168 — 글 상세의 {@code processStatusNote} 와 같은 값, 없으면 null)
 */
public record PostProcessStatusView(String processStatus, Instant processStatusAt, String processStatusNote) {
}
