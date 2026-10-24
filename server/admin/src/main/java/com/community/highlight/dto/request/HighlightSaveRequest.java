package com.community.highlight.dto.request;

import java.time.Instant;

/**
 * {@code POST /api/admin/highlights}(만들기) · {@code PUT /api/admin/highlights/{id}}(고치기 — 모든 칸을 보낸다).
 *
 * @param postId      대상 글 ID — 만들 때만 받고 고칠 때는 무시한다(대상 글은 바꾸지 않는다)
 * @param title       노출 제목(40자까지, 비우면 null — 화면은 글 제목을 쓴다)
 * @param summary     요약 문구(100자까지, 비우면 null — 화면은 요약을 그리지 않는다)
 * @param imageFileId 대표 이미지 — {@code POST /api/admin/files}(purpose HIGHLIGHT_IMAGE)로 올린 파일 ID. null 이면 대표 이미지 없음(고칠 때 null 이면 지운다), 지금 이미지와 같으면 그대로
 * @param visible     노출 켬 — 만들 때 비우면 true, 고칠 때 비우면 그대로
 * @param showFrom    노출 시작(포함, 비우면 바로)
 * @param showUntil   노출 끝(미포함, 비우면 상시) — {@code showFrom} 보다 늦어야 한다
 * @param debate      그 <b>글의</b> 찬반 토론(하이라이트는 토론을 갖지 않는다 — docs/features/post-debate-contract.md). true = 연다(이미 열려 있으면 아래 두 칸만 바꾼다),
 *                    고칠 때 false = 글의 토론을 닫는다(운영자라 투표가 있어도), null = 그대로. <b>만들 때 false 는 그대로</b>(글쓴이가 연 토론을 모르고 닫지 않게)
 * @param neutralEnabled 토론의 「중립도 받기」 — 열 때 비우면 켬, 열려 있으면 비우면 그대로. 끄면 회원이 새로 중립을 고를 수 없다(이미 고른 중립·쓴 중립 의견은 남는다)
 * @param stanceChangeAllowed 토론의 「입장 바꾸기 허용」 — 같은 규칙. 끄면 한 번 고른 입장을 바꿀 수 없다(처음 고르기는 된다). 나중에 꺼도 이미 고른 입장·쓴 의견은 그대로
 */
public record HighlightSaveRequest(String postId, String title, String summary, String imageFileId, Boolean visible, Instant showFrom, Instant showUntil,
                                   Boolean debate, Boolean neutralEnabled, Boolean stanceChangeAllowed) {
}
