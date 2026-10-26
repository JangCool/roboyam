package com.community.group.dto.response;

import com.community.dm.dto.response.DmPartnerView;
import java.time.Instant;

/**
 * 일정 상세(community {@code EventDetail}) = 목록 한 줄({@link GroupEventRowView}) 칸 + 아래 칸.
 *
 * @param description   설명(없으면 null)
 * @param counts        응답별 인원(지금 멤버만)
 * @param offlineNotice 오프라인 일정이거나 오프라인 모임이면 true — 화면이 개인 책임 고지를 보인다
 * @param creator       만든 사람(로보얌 사람 칸 — 탈퇴했으면 {@code id: null}·「탈퇴한 회원」)
 * @param canRespond    멤버이고 시작 전(UPCOMING)
 * @param canManage     모임장 또는 일정 관리(EVENTS) 매니저이고 시작 전·진행 중 — 수정·취소 가능(정지·미인증은 상세의 {@code permissions.manageBlockedReason} 이 따로 말한다)
 * @param cancelReason  취소 사유(취소했고 적었을 때만)
 * @param canceledAt    취소 시각(취소했을 때만)
 */
public record GroupEventDetailView(String id, String title, Instant startsAt, Instant endsAt, String mode, String place, String state, int goingCount,
                                   String myResponse, String description, GroupEventCountsView counts, boolean offlineNotice, DmPartnerView creator,
                                   boolean canRespond, boolean canManage, String cancelReason, Instant canceledAt) {
}
