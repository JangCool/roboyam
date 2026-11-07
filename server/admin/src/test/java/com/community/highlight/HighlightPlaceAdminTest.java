package com.community.highlight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.board.service.BoardCatalog;
import com.community.commerce.CommerceAdminTestBase;
import com.community.highlight.dto.request.HighlightOrderRequest;
import com.community.highlight.dto.request.HighlightSaveRequest;
import com.community.highlight.dto.response.HighlightItemView;
import com.community.highlight.dto.response.HighlightPlaceView;
import com.community.highlight.exception.HighlightDuplicateException;
import com.community.highlight.service.HighlightAdminService;
import com.community.home.entity.GadgetPageEntity;
import com.community.home.repository.master.GadgetPageRepository;
import com.community.permission.dto.AdminActor;
import com.community.testsupport.AuditLogQuery.AuditRow;
import com.community.testsupport.BoardFixture;
import com.community.testsupport.MemberFixture;
import com.community.zconfiguration.exception.ValidationException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 하이라이트를 놓일 곳(place)별로(V161, docs/features/tips-contract.md 「서버 구현 결과」) — 같은 글을 곳마다 한 번씩 · 같은 곳 두 번은 409 · 목록·순서·글 찾기는 곳 단위 ·
 * 비운 곳은 home · 없는 곳·틀린 모양은 {@code fields.place} · 곳 목록(홈·팁·가젯 페이지) · 감사에 place. 실제 PostgreSQL.
 * 시험 DB 를 다른 시험과 같이 쓰므로 순서 시험은 이 시험이 만든 가젯 페이지 곳에서 한다.
 */
class HighlightPlaceAdminTest extends CommerceAdminTestBase {

    @Autowired
    private HighlightAdminService service;
    @Autowired
    private GadgetPageRepository pages;
    @Autowired
    private BoardCatalog catalog;
    @Autowired
    private ApplicationContext context;
    @Autowired
    private PlatformTransactionManager transactions;

    private AdminActor root;
    private BoardFixture boards;
    private long board;
    private long author;
    private String pageKey;
    private long pageId;

    @BeforeEach
    void setUp() {
        long rootId = rootId();
        root = actor(rootId);
        boards = new BoardFixture(context);
        board = ids.nextId();
        boards.board(board, "hp" + Long.toString(board, 36), "곳 시험 " + Long.toString(board, 36), 950);
        catalog.invalidate();
        author = ids.nextId();
        new MemberFixture(context).user(author, "hp-" + author + "@test.local");
        pageId = ids.nextId();
        pageKey = "hp-" + Long.toString(pageId, 36);
        new TransactionTemplate(transactions).executeWithoutResult(s -> pages.persist(
                GadgetPageEntity.create(pageId, new GadgetPageEntity.Fields(pageKey, "곳 페이지 " + Long.toString(pageId, 36), "Place page", "", null, false), "{}", rootId, Instant.now())));
        LocaleContextHolder.setLocale(java.util.Locale.KOREAN);
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    private long post(String title) {
        long id = ids.nextId();
        boards.post(id, board, author, title, "본문");
        return id;
    }

    private HighlightItemView create(String place, long postId) {
        return service.create(root, new HighlightSaveRequest(Long.toString(postId), null, null, null, null, null, null, null, null, null, place));
    }

    @Test
    void 같은_글을_곳마다_한_번씩_넣을_수_있고_같은_곳_두_번은_409() {
        long p = post("곳마다 한 번");

        HighlightItemView home = create(null, p);
        HighlightItemView tips = create("tips", p);
        HighlightItemView page = create(pageKey, p);

        assertThat(home.place()).as("비우면 home").isEqualTo("home");
        assertThat(tips.place()).isEqualTo("tips");
        assertThat(page.place()).isEqualTo(pageKey);
        assertThat(page.order()).as("순서는 곳 안에서 센다").isEqualTo(1);
        assertThatThrownBy(() -> create("tips", p)).isInstanceOf(HighlightDuplicateException.class);
        assertThatThrownBy(() -> create("home", p)).isInstanceOf(HighlightDuplicateException.class);

        assertThat(service.list(pageKey).items()).extracting(HighlightItemView::id).containsExactly(page.id());
        assertThat(service.list(pageKey).place()).isEqualTo(pageKey);
        assertThat(service.list("tips").items()).extracting(HighlightItemView::id).contains(tips.id()).doesNotContain(home.id(), page.id());
        assertThat(service.list(null).items()).extracting(HighlightItemView::id).contains(home.id()).doesNotContain(tips.id(), page.id());

        service.delete(root, page.id());
        assertThat(service.list(pageKey).items()).isEmpty();
        assertThat(service.list("tips").items()).as("다른 곳의 같은 글은 그대로").extracting(HighlightItemView::id).contains(tips.id());
    }

    @Test
    void 없는_곳이나_틀린_모양의_곳은_fields_place() {
        long p = post("없는 곳");

        assertThatThrownBy(() -> create("no-such-page-" + Long.toString(p, 36).substring(0, 6), p)).isInstanceOfSatisfying(ValidationException.class,
                e -> assertThat(e.getFields()).containsKey("place"));
        assertThatThrownBy(() -> create("Tips!", p)).isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("place"));
        assertThatThrownBy(() -> service.list("BAD KEY")).isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("place"));
        assertThatThrownBy(() -> service.candidates(null, null, null, "x")).isInstanceOfSatisfying(ValidationException.class,
                e -> assertThat(e.getFields()).containsKey("place"));
        assertThat(service.list(pageKey).items()).as("만들지 않았다").isEmpty();
    }

    @Test
    void 순서는_그_곳의_ID_집합으로만_바꾼다() {
        HighlightItemView a = create(pageKey, post("가"));
        HighlightItemView b = create(pageKey, post("나"));
        HighlightItemView other = create("tips", post("다"));

        assertThatThrownBy(() -> service.order(root, new HighlightOrderRequest(pageKey, List.of(b.id(), a.id(), other.id()))))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsKey("ids"));
        assertThatThrownBy(() -> service.order(root, new HighlightOrderRequest(null, List.of(b.id(), a.id()))))
                .as("곳을 비우면 home 의 집합과 비교한다").isInstanceOf(ValidationException.class);

        service.order(root, new HighlightOrderRequest(pageKey, List.of(b.id(), a.id())));

        assertThat(service.list(pageKey).items()).extracting(HighlightItemView::id, HighlightItemView::order)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(b.id(), 1), org.assertj.core.groups.Tuple.tuple(a.id(), 2));
        AuditRow audit = lastAudit(root.operatorId(), "HIGHLIGHT_ORDER");
        assertThat(audit.detail()).contains("\"place\"").contains(pageKey);
    }

    @Test
    void 글_찾기와_후보는_그_곳에_없는_글만_보인다() {
        long p = post("찾을 글 " + System.nanoTime());
        create(pageKey, p);

        assertThat(service.search(Long.toString(p), pageKey)).as("이미 그 곳에 있다").isEmpty();
        assertThat(service.search(Long.toString(p), "tips")).as("다른 곳에는 넣을 수 있다").extracting(r -> r.id()).containsExactly(Long.toString(p));
        assertThat(service.candidates("0", "1", Long.toString(board), "tips").place()).isEqualTo("tips");
    }

    @Test
    void 곳_목록은_홈_팁_가젯_페이지_차례이고_감사에_place_가_남는다() {
        List<HighlightPlaceView> places = service.places();

        assertThat(places.get(0)).extracting(HighlightPlaceView::key, HighlightPlaceView::name, HighlightPlaceView::kind).containsExactly("home", "홈", "HOME");
        assertThat(places.get(1)).extracting(HighlightPlaceView::key, HighlightPlaceView::name, HighlightPlaceView::kind).containsExactly("tips", "팁·강좌·사용기", "TIPS");
        assertThat(places).extracting(HighlightPlaceView::key, HighlightPlaceView::name, HighlightPlaceView::kind)
                .contains(org.assertj.core.groups.Tuple.tuple(pageKey, "곳 페이지 " + Long.toString(pageId, 36), "PAGE"));
        assertThat(places).extracting(HighlightPlaceView::key).doesNotHaveDuplicates();

        HighlightItemView row = create("tips", post("감사"));
        long tipsBefore = places.get(1).total();
        assertThat(service.places().get(1).total()).as("곳마다 하이라이트 수 — 만든 곳만 하나 는다").isEqualTo(tipsBefore + 1);
        assertThat(service.places().get(0).total()).isEqualTo(places.get(0).total());
        AuditRow audit = lastAudit(root.operatorId(), "HIGHLIGHT_CREATE");
        assertThat(audit.targetId()).isEqualTo(row.id());
        assertThat(audit.detail()).contains("\"place\"").contains("\"tips\"");
    }
}
