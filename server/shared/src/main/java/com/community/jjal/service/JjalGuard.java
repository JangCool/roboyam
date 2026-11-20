package com.community.jjal.service;

import com.community.jjal.config.JjalProperties;
import com.community.jjal.entity.JjalFileEntity;
import com.community.jjal.entity.JjalUseEntity;
import com.community.jjal.exception.JjalBlockedException;
import com.community.jjal.exception.JjalNotAllowedException;
import com.community.jjal.exception.JjalNotInBoxException;
import com.community.jjal.repository.master.JjalFileRepository;
import com.community.jjal.repository.master.JjalRepositoryQDSL;
import com.community.jjal.repository.master.JjalUseRepository;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 글·댓글 본문의 짤 토큰 {@code :jjal-{공개ID}:} 검사 — 로보티콘 토큰 검사({@code EmoticonGuard})와 같은 자리·같은 방식(my-jjal-contract 「글·댓글에 붙이기」·「게시판별 사용」).
 * 검사 지점 = 글쓰기·글 고치기·소모임 글쓰기·댓글 쓰기·고치기.
 *
 * <ol>
 *   <li><b>개수</b>({@link #countInto}): 정규식 {@link #TOKEN} 을 본문 전체 글자(HTML 이면 정화된 HTML 전체)에서 센다 — 같은 토큰 여러 번·없는 ID 도 센다. 글 {@code postTokens}(5) ·
 *       댓글 {@code commentTokens}(1) 넘으면 {@code 400 VALIDATION fields.content}(그 칸에 오류가 이미 있으면 그 오류가 남는다). 입력 오류와 함께 모은다.</li>
 *   <li><b>새로 넣은 짤</b>({@link #requireAllowed}): 입력 오류가 없을 때, 고치기 전 본문에 없던 토큰 중 <b>있는 짤</b>만 본다(없는 ID 는 글자라 오류가 아니다 — 로보티콘과 같다).
 *       게시판 칸이 꺼져 있으면 {@code 422 JJAL_NOT_ALLOWED}(나중에 끄면 이미 붙은 짤은 그대로 — 글 이미지 「고치면 새 것만 막는다」와 같다) → 내려간 짤 {@code 409 JJAL_BLOCKED}
 *       → 작성자의 지금 보관함에 없음 {@code 409 JJAL_NOT_IN_BOX}(비회원은 보관함이 없다). 이미 있던 토큰은 다시 보지 않는다(보관함에서 뺐어도 쓴 글은 고칠 수 있다).</li>
 *   <li><b>쓰임</b>({@link #recordUses}): 저장이 성공하는 같은 트랜잭션에서 그 글 본문·댓글의 쓰임 줄을 본문 토큰으로 다시 쓴다(정리 배치·담기 검사가 본다).</li>
 * </ol>
 * 오류 차례는 입력 오류(400, 개수 포함) → 로보티콘 자격(409) → 짤(422·409) → 금지어·속도 제한(부르는 서비스가 그다음에 본다).
 */
@Component
public class JjalGuard {

    /** 토큰 하나 — 공개 ID 는 영문·숫자 1~32자(user-web {@code util-rich-jjal.ts} 와 같다) */
    public static final Pattern TOKEN = Pattern.compile(":jjal-([A-Za-z0-9]{1,32}):");
    /** 공개 ID 모양 */
    public static final Pattern PUBLIC_ID = Pattern.compile("[A-Za-z0-9]{1,32}");

    /** 본문 자리 — 글 본문 · 댓글 */
    public enum Place {
        POST,
        COMMENT
    }

    private final JjalFileRepository files;
    private final JjalRepositoryQDSL queries;
    private final JjalUseRepository uses;
    private final SnowflakeIdGenerator ids;
    private final JjalProperties props;

    public JjalGuard(JjalFileRepository files, JjalRepositoryQDSL queries, JjalUseRepository uses, SnowflakeIdGenerator ids, JjalProperties props) {
        this.files = files;
        this.queries = queries;
        this.uses = uses;
        this.ids = ids;
        this.props = props;
    }

    /** 공개 ID 모양인가(영문·숫자 1~32자) */
    public static boolean validPublicId(String raw) {
        return raw != null && PUBLIC_ID.matcher(raw).matches();
    }

    /** 토큰 수(같은 토큰 여러 번 · 없는 ID 도) */
    public static int count(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        Matcher m = TOKEN.matcher(content);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    /** 본문 순서의 토큰 공개 ID(겹치면 겹친 대로) */
    public static List<String> ids(String content) {
        List<String> out = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return out;
        }
        Matcher m = TOKEN.matcher(content);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    /** 이 자리에 넣을 수 있는 토큰 수 */
    public int maxTokens(Place place) {
        return place == Place.POST ? props.postTokens() : props.commentTokens();
    }

    /** 개수 검사 — 넘으면 {@code fields.<field>}(그 칸에 오류가 이미 있으면 그대로 둔다) */
    public void countInto(String content, Place place, String field, Map<String, MessageRef> fields) {
        int max = maxTokens(place);
        if (count(content) > max && !fields.containsKey(field)) {
            fields.put(field, new MessageRef("jjal.body.tooMany", max));
        }
    }

    /**
     * 새로 넣은 짤 검사 — 개수 검사를 지난 본문에서 부른다.
     *
     * @param authorId     작성자(비회원이면 null — 보관함이 없다)
     * @param content      저장할 본문
     * @param previous     고치기 전 본문(쓰기면 null)
     * @param boardAllowed 게시판 유효값(글 본문 {@code postJjalAllowed} · 댓글 {@code commentJjalAllowed})
     */
    public void requireAllowed(Long authorId, String content, String previous, boolean boardAllowed) {
        Set<String> before = new HashSet<>(ids(previous));
        List<String> added = new ArrayList<>(new LinkedHashSet<>(ids(content)));
        added.removeIf(before::contains);
        if (added.isEmpty()) {
            return;
        }
        Map<String, JjalFileEntity> byId = files.findAllByPublicIdIn(added).stream()
                .collect(Collectors.toMap(JjalFileEntity::getPublicId, Function.identity(), (a, b) -> a));
        if (byId.isEmpty()) {
            return; // 없는 ID 는 글자 그대로다
        }
        if (!boardAllowed) {
            throw new JjalNotAllowedException();
        }
        Instant now = Instant.now();
        Set<Long> inBox = authorId == null ? Set.of() : queries.inBox(authorId, byId.values().stream().map(JjalFileEntity::getId).toList());
        for (String publicId : added) {
            JjalFileEntity f = byId.get(publicId);
            if (f == null) {
                continue;
            }
            if (f.isBlockedAt(now)) {
                throw new JjalBlockedException(publicId);
            }
            if (!inBox.contains(f.getId())) {
                throw new JjalNotInBoxException(publicId);
            }
        }
    }

    /**
     * 쓰임 다시 쓰기 — 저장 트랜잭션 안에서, 글·댓글 행을 쓴 뒤에 부른다. 그 글 본문(댓글 ID null)·그 댓글의 쓰임 줄을 지우고 본문에 있는 짤마다 한 줄씩 넣는다.
     *
     * @return 넣은 줄 수
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int recordUses(long postId, Long commentId, String content) {
        queries.deleteUses(postId, commentId);
        List<String> present = new ArrayList<>(new LinkedHashSet<>(ids(content)));
        if (present.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (JjalFileEntity f : files.findAllByPublicIdIn(present)) {
            uses.persist(JjalUseEntity.create(ids.nextId(), f.getId(), postId, commentId));
            n++;
        }
        return n;
    }
}
