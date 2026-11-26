package com.community.board.dto.request;

/**
 * 공통 말머리(질문·후기·잡담·공지) 한 줄 — 이름·운영진 전용은 고정이라 켜고 끄기·지우기·다시 넣기만 보낸다(board-lineup-contract 「공통 말머리 지우기·다시 넣기」).
 *
 * <ul>
 *   <li>{@code {id, active}} — 있는 공통 말머리를 켜고 끈다(지금과 같다).</li>
 *   <li>{@code {id, remove: true}} — 그 행을 지운다. 쓰는 글이 있으면 409 {@code PREFIX_IN_USE}, 체험단({@code TRIAL})이면 400 {@code fields.commonPrefixes}.
 *       {@code active} 가 같이 와도 지우기가 이긴다.</li>
 *   <li>{@code {id: null, common: "QUESTION"|"REVIEW"|"CHAT"|"NOTICE", active}} — 그 게시판에 그 키가 없을 때만 새로 만든다({@code active} 를 비우면 켠 채).
 *       이미 있거나 {@code TRIAL}·모르는 키면 400 {@code fields.commonPrefixes}.</li>
 * </ul>
 * 목록에서 빠진 공통 말머리는 그대로 둔다(지우라는 뜻 아님).
 *
 * @param common 다시 넣을 공통 말머리 키 — {@code id} 가 없을 때만 본다(앞뒤 공백·소문자 허용)
 * @param remove true 면 지운다 — {@code id} 가 있을 때만 본다
 */
public record BoardCommonPrefixSaveRequest(String id, Boolean active, String common, Boolean remove) {

    /** 켜고 끄기만(지우기·다시 넣기 칸 없이) */
    public BoardCommonPrefixSaveRequest(String id, Boolean active) {
        this(id, active, null, null);
    }
}
