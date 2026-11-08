package com.community.board.service;

import com.community.board.entity.BoardGadget;
import java.util.List;

/**
 * 운영 게시판 시드의 <b>정본 값</b>(docs/features/ops-boards-contract.md) — 「버그 신고」 {@code bugs} · 「기능 요청」 {@code requests} 와 공개 메뉴 그룹 {@code footer}.
 * 새 설치는 {@link OpsBoardSeeder} 가 이 값으로 만들고, 이미 설치된 DB 는 마이그레이션 {@code V168__post_status_history_and_ops_board_seed.sql} 이 같은 값을 SQL 로 넣는다
 * (어느 쪽을 고치면 다른 쪽도 고친다 — 시험 {@code OpsBoardSeedTest} 가 두 결과가 같은지 본다).
 *
 * <p>두 게시판 공통: 일반·목록형(보기 바꾸기 켬) · 글쓰기·댓글 최소 등급 = 첫 등급(기본 설치의 「새싹」) · 이미지 올리기 켬 · 검색엔진 색인 끔 · 사이트 검색 켬 · 포인트 적립 끔
 * · 찬반 토론·투표 끔 · 가젯 노출 금지(인기·최신) · 광고 숨김 · 처리 상태 켬 · 공통 말머리는 「공지」만. 메인 메뉴에는 넣지 않는다.
 */
public final class OpsBoardPreset {

    /** 메뉴 그룹 footer */
    public static final String FOOTER_SET = "footer";
    public static final String FOOTER_NAME = "푸터 고객 지원";
    public static final String FOOTER_NAME_EN = "Footer support";
    public static final String FOOTER_PLACEMENT = "CUSTOM";
    public static final String FOOTER_DESCRIPTION = "사이트 아래 「고객 지원」 링크";

    public static final String LAYOUT = "LIST";
    public static final List<BoardGadget> GADGET_EXCLUSIONS = List.of(BoardGadget.POPULAR, BoardGadget.LATEST);
    /** 켜는 공통 말머리 */
    public static final String COMMON_ACTIVE = "NOTICE";

    /**
     * 게시판 하나.
     *
     * @param prefixes 고유 말머리(표시 차례)
     */
    public record Board(String slug, String name, String description, String introText, String writeTemplate, String secretMode, List<String> prefixes) {
    }

    public static final Board BUGS = new Board("bugs", "버그 신고", "사이트를 쓰다 생긴 오류를 알려 주세요",
            "사이트를 쓰다 이상한 점을 알려 주세요. 운영자가 확인해 처리 상태(접수 → 확인 중 → 완료)와 댓글로 알려 드려요.\n"
                    + "계정·결제처럼 남에게 보이면 안 되는 내용은 「비밀글」로 써 주세요(글쓴이와 운영진만 봐요). 비밀번호는 비밀글에도 적지 마세요. 로그인이 안 되면 사이트 아래 문의 이메일로 알려 주세요.",
            "■ 문제가 생긴 화면(주소):\n■ 재현 순서\n1.\n2.\n3.\n■ 기대한 결과:\n■ 실제 결과:\n■ 기기·브라우저: (예: 갤럭시 S24 · 크롬 / 윈도우 11 · 엣지)\n■ 발생 시각:\n"
                    + "※ 화면 캡처를 붙여 주시면 더 빨리 찾을 수 있어요.",
            BoardFields.SECRET_OPTIONAL, List.of("화면 오류", "기능 오류", "모바일", "결제·주문", "기타"));

    public static final Board REQUESTS = new Board("requests", "기능 요청", "있으면 좋을 기능과 고쳤으면 하는 점을 제안해 주세요",
            "있으면 좋을 기능이나 고쳤으면 하는 점을 제안해 주세요.\n"
                    + "비슷한 제안이 이미 있으면 추천과 댓글로 힘을 보태 주세요. 운영자가 검토해 처리 상태(접수 → 확인 중 → 진행 중 → 완료)로 알려 드려요.",
            "■ 제안 내용:\n■ 필요한 이유(지금 불편한 점):\n■ 생각한 방식(있으면):\n■ 참고(다른 사이트·화면 캡처):",
            BoardFields.SECRET_OFF, List.of("새 기능", "개선", "디자인", "모바일", "기타"));

    /** 만들 차례 = 푸터 항목 차례 */
    public static final List<Board> BOARDS = List.of(BUGS, REQUESTS);

    private OpsBoardPreset() {
    }
}
