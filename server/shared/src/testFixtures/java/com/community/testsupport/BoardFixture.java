package com.community.testsupport;

import com.community.board.dto.BoardPolicyValues;
import com.community.board.entity.BoardEntity;
import com.community.board.repository.master.BoardRepository;
import com.community.comment.entity.CommentEntity;
import com.community.comment.repository.master.CommentRepository;
import com.community.post.entity.ContentFormat;
import com.community.post.entity.PostEntity;
import com.community.post.repository.master.PostRepository;
import org.springframework.context.ApplicationContext;

/**
 * 시험 게시판({@code boards})·글({@code posts}) — 엔티티 정적 팩터리·도메인 메서드로 만들고 {@code persist()} 로 넣는다(호출마다 커밋).
 *
 * <ul>
 *   <li>게시판은 {@link BoardEntity#create} — 켜진 채·목록형(LIST)·보기 바꾸기 켬·색 없음·정책은 모두 기본값 따름(NULL), 만든 운영자 없음. 표의 기본값과 같은 행이다.</li>
 *   <li>글은 {@link PostEntity#create} — 회원 글·평문(TEXT)·게시 상태·19금 아님·이미지 없음, 말머리·작성 IP 없음.</li>
 * </ul>
 */
public final class BoardFixture {

    /** 일반 게시판 */
    public static final String GENERAL = "GENERAL";
    /** 핫딜 게시판 */
    public static final String DEAL = "DEAL";

    private final FixtureTx tx;
    private final BoardRepository boards;
    private final PostRepository posts;
    private final CommentRepository comments;

    public BoardFixture(ApplicationContext context) {
        this.tx = new FixtureTx(context);
        this.boards = context.getBean(BoardRepository.class);
        this.posts = context.getBean(PostRepository.class);
        this.comments = context.getBean(CommentRepository.class);
    }

    /** 일반 게시판(설명 없음) */
    public void board(long id, String slug, String name, int sortOrder) {
        board(id, slug, name, "", sortOrder);
    }

    /** 일반 게시판 */
    public void board(long id, String slug, String name, String description, int sortOrder) {
        tx.run(() -> boards.persist(BoardEntity.create(id, slug, name, null, description, GENERAL, "LIST", true, null, false, sortOrder, null, FixtureTx.now())));
    }

    /** 고른 종류의 게시판 — {@code writeMin} 은 글쓰기 최소 등급(null = 기본값 따름) */
    public void board(long id, String slug, String name, int sortOrder, String kind, String writeMin) {
        tx.run(() -> {
            BoardEntity board = BoardEntity.create(id, slug, name, null, "", kind, "LIST", true, null, false, sortOrder, null, FixtureTx.now());
            if (writeMin != null) {
                board.changePolicy(writeMinOnly(writeMin));
            }
            boards.persist(board);
        });
    }

    /** 고른 종류의 게시판 — 19금 허용·광고 숨김·켜짐까지 고른다 */
    public void board(long id, String slug, String name, int sortOrder, String kind, boolean allowAdult, boolean adsHidden, boolean active) {
        tx.run(() -> {
            BoardEntity board = BoardEntity.create(id, slug, name, null, "", kind, "LIST", true, null, false, sortOrder, null, FixtureTx.now());
            board.changeBasic(name, null, "", "LIST", true, null, allowAdult, false);
            board.changeAdsHidden(adsHidden);
            board.changeActive(active);
            boards.persist(board);
        });
    }

    /** 회원이 쓴 평문 글 */
    public void post(long id, long boardId, long authorId, String title, String content) {
        tx.run(() -> posts.persist(PostEntity.create(id, boardId, null, authorId, null, null, null, title, ContentFormat.TEXT, content, null, false, false,
                FixtureTx.now())));
    }

    /** 비회원이 쓴 평문 글({@code authorId} 가 null 이면 비회원 이름·비밀번호 해시를 남긴다) */
    public void post(long id, long boardId, Long authorId, String guestName, String guestPasswordHash, String title, String content) {
        tx.run(() -> posts.persist(PostEntity.create(id, boardId, null, authorId, guestName, guestPasswordHash, null, title, ContentFormat.TEXT, content, null, false,
                false, FixtureTx.now())));
    }

    /** 적립 정책만 고른 일반 게시판 — {@code pointMode}·{@code pointMinLengthApplied} 는 null 이면 기본값 따름 */
    public void pointBoard(long id, String slug, String name, int sortOrder, String pointMode, Boolean pointMinLengthApplied) {
        tx.run(() -> {
            BoardEntity board = BoardEntity.create(id, slug, name, null, "", GENERAL, "LIST", true, null, false, sortOrder, null, FixtureTx.now());
            board.changePolicy(new BoardPolicyValues(null, null, null, null, null, null, null, null, null, null, pointMode, pointMinLengthApplied, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, null, null, null, null));
            boards.persist(board);
        });
    }

    /** 게시판을 지운다(없으면 그대로) — 시험이 만든 게시판 치우기 */
    public void deleteBoard(long boardId) {
        tx.run(() -> boards.findById(boardId).ifPresent(boards::delete));
    }

    /** 가젯 노출 금지를 모두 푼다 */
    public void clearGadgetExclusions(String slug) {
        tx.run(() -> boards.findBySlug(slug).orElseThrow().changeGadgetExclusions(java.util.List.of()));
    }

    /** 운영 게시판 설정(V166) — 비밀글 방식(OFF · OPTIONAL · ALWAYS)·처리 상태 */
    public void ops(long boardId, String secretMode, boolean statusEnabled) {
        tx.run(() -> boards.findById(boardId).orElseThrow().changeOps(secretMode, statusEnabled));
    }

    /** 회원이 쓴 평문 글 — 비밀글 여부와 처음 처리 상태(상태를 쓰면 RECEIVED)를 고른다(V166) */
    public void post(long id, long boardId, long authorId, String title, String content, boolean secret, boolean statusEnabled) {
        tx.run(() -> {
            PostEntity post = PostEntity.create(id, boardId, null, authorId, null, null, null, title, ContentFormat.TEXT, content, null, false, false, FixtureTx.now());
            post.initOps(secret, statusEnabled);
            posts.persist(post);
        });
    }

    /** 게시판을 켜거나 끈다 */
    public void active(long boardId, boolean active) {
        tx.run(() -> boards.findById(boardId).orElseThrow().changeActive(active));
    }

    /** 운영자가 글을 숨긴 것으로 둔다(까닭 STAFF, 기간 없음 — 고정은 풀린다) */
    public void staffHide(long postId) {
        tx.run(() -> posts.findById(postId).orElseThrow().staffHide(FixtureTx.now()));
    }

    /** 글을 지운 것으로 둔다(지운 시각 = 지금) */
    public void delete(long postId) {
        tx.run(() -> posts.findById(postId).orElseThrow().delete(FixtureTx.now()));
    }

    /** 회원이 쓴 평문 댓글(답글 아님) — 글의 댓글 수는 바꾸지 않는다 */
    public void comment(long id, long postId, long boardId, long authorId, String content) {
        tx.run(() -> comments.persist(CommentEntity.create(id, postId, boardId, null, null, authorId, null, null, null, ContentFormat.TEXT, content, FixtureTx.now())));
    }

    /** 댓글(답글 아님) — {@code authorId} 가 null 이면 비회원 이름·비밀번호 해시를 남긴다. 글의 댓글 수는 바꾸지 않는다 */
    public void comment(long id, long postId, long boardId, Long authorId, String guestName, String guestPasswordHash, String content) {
        tx.run(() -> comments.persist(CommentEntity.create(id, postId, boardId, null, null, authorId, guestName, guestPasswordHash, null, ContentFormat.TEXT, content,
                FixtureTx.now())));
    }

    private static BoardPolicyValues writeMinOnly(String writeMin) {
        return new BoardPolicyValues(writeMin, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
    }
}
