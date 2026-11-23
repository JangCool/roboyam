package com.community.site.service;

import com.community.board.service.BoardCatalog;
import com.community.board.service.RichText;
import com.community.group.service.GroupPostGate;
import com.community.post.entity.PostEntity;
import com.community.post.repository.master.PostRepository;
import com.community.post.service.PostThumbnails;
import com.community.site.dto.PostLinkPreview;
import com.community.site.dto.PostLinkResult;
import com.community.site.dto.PublicSiteSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 글 주소를 공유했을 때(카카오톡·페이스북·X 등) 미리보기에 <b>사이트 제목 대신 글 제목·본문 앞부분·썸네일</b>이 나오게 하는 칸을 만든다
 * (관리자 QA 2026-11-17 — 사용자 결정 「추천대로」). 크롤러는 자바스크립트를 돌리지 않으므로 {@code index.html} 머리에 서버가 넣는다({@link IndexHtmlRenderer#withPost}).
 *
 * <ul>
 *   <li><b>비회원이 읽을 수 있는 글만</b> — 비회원으로 글 상세를 열 때와 같은 판정({@link GroupPostGate#resolve}: 게시판 사용 중 · 성인 전용 게시판 아님 · 19금 글 아님 ·
 *       비밀글 아님 · 소모임 비공개 글 아님)에 지운 글 · 숨긴 글 제외. 그 밖이면 비어 있다 — 사이트 미리보기 그대로(제목조차 내지 않는다).</li>
 *   <li>설명은 본문의 보이는 글자({@link RichText#plainOf}) 앞 {@value #DESCRIPTION_MAX}자, 그림은 목록 썸네일({@link PostThumbnails} — 고른 그림 · 첫 본문 이미지 · 첫 영상).</li>
 *   <li>대표 주소(canonical)도 함께 만든다 — 공개 주소 + {@code /posts/{id}}(docs/features/sitemap-contract.md).</li>
 *   <li><b>없는 글</b>(글이 없음 · 지움 · 예약 삭제 시각 지남 · 임시저장)은 {@link PostLinkResult#GONE} — 화면 처리기가 상태만 404 로 내린다. 숨긴 글 · 비밀글 · 19금 글 ·
 *       성인 게시판 글 · 소모임 비공개 글은 볼 수 있는 회원이 있으므로 {@link PostLinkResult#NONE}(200)이다.</li>
 *   <li>조회수는 오르지 않는다(읽기만). 실패하면 {@link PostLinkResult#NONE} — 화면 응답은 막히지 않는다.</li>
 * </ul>
 */
@Service
public class PostLinkPreviewService {

    private static final Logger log = LoggerFactory.getLogger(PostLinkPreviewService.class);

    /** 글 상세 화면 주소(user-web {@code pages/posts/[id]/index.vue}) */
    private static final Pattern POST_PATH = Pattern.compile("^/posts/([0-9]+)/?$");
    static final int DESCRIPTION_MAX = 150;

    private final PostRepository posts;
    private final BoardCatalog catalog;
    private final GroupPostGate gate;
    private final PostThumbnails thumbnails;
    private final SiteAssets assets;

    public PostLinkPreviewService(PostRepository posts, BoardCatalog catalog, GroupPostGate gate, PostThumbnails thumbnails, SiteAssets assets) {
        this.posts = posts;
        this.catalog = catalog;
        this.gate = gate;
        this.thumbnails = thumbnails;
        this.assets = assets;
    }

    /** 화면 주소가 글 상세면 그 글의 미리보기 칸 또는 「없는 글」 — 글 주소가 아니거나 비회원이 읽을 수 없는 글이면 {@link PostLinkResult#NONE} */
    public PostLinkResult forPath(String path, PublicSiteSnapshot snapshot) {
        Matcher m = POST_PATH.matcher(path);
        if (!m.matches()) {
            return PostLinkResult.NONE;
        }
        long id;
        try {
            id = Long.parseLong(m.group(1));
        } catch (NumberFormatException e) {
            return PostLinkResult.GONE; // long 범위를 넘는 번호의 글은 있을 수 없다
        }
        try {
            PostEntity post = posts.findById(id).orElse(null);
            Instant now = Instant.now();
            if (post == null || post.isGoneAt(now)) {
                return PostLinkResult.GONE;
            }
            if (post.isHiddenAt(now) || post.isSecret() || post.isAdult()) {
                return PostLinkResult.NONE;
            }
            gate.resolve(catalog.snapshot(), post, null); // 비회원이 열 수 없으면 예외(404·403) — 미리보기도 내지 않는다
            String image = thumbnails.of(List.of(id)).get(id);
            return PostLinkResult.of(new PostLinkPreview(post.getTitle(), excerpt(RichText.plainOf(post.getContentFormat(), post.getContent())),
                    image == null ? null : assets.absolute(image, snapshot.publicUrl()), canonicalUrl(id, snapshot.publicUrl())));
        } catch (RuntimeException e) {
            log.debug("글 {} 공유 미리보기를 만들지 않는다: {}", id, e.toString());
            return PostLinkResult.NONE;
        }
    }

    /** 글의 대표 주소 — 공개 주소가 없으면 null */
    private static String canonicalUrl(long id, String publicUrl) {
        return publicUrl == null || publicUrl.isBlank() ? null : publicUrl.strip().replaceAll("/+$", "") + "/posts/" + id;
    }

    /** 줄바꿈·이어진 공백을 하나로 줄이고 {@value #DESCRIPTION_MAX}자에서 자른다(넘으면 끝에 …) */
    static String excerpt(String plain) {
        if (plain == null) {
            return "";
        }
        String flat = plain.replaceAll("\\s+", " ").strip();
        if (flat.codePointCount(0, flat.length()) <= DESCRIPTION_MAX) {
            return flat;
        }
        return flat.substring(0, flat.offsetByCodePoints(0, DESCRIPTION_MAX)).stripTrailing() + "…";
    }
}
