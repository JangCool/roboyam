package com.community.post.service;

import com.community.media.repository.master.FileRepositoryQDSL;
import com.community.media.service.ImageStorage;
import com.community.post.entity.QPostEntity;
import com.community.post.repository.master.PostRepositoryQDSL;
import com.querydsl.core.Tuple;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 목록 줄의 썸네일({@code thumbnailUrl}) — 글에 붙은 첫 이미지(먼저 올린 것), 이미지가 없으면 본문의 첫 YouTube 영상 썸네일. 둘 다 없으면 없다.
 * 한 쪽의 글을 질의 두 번(이미지 · 이미지 없는 글의 본문)으로 읽는다. 그 밖의 영상 플랫폼({@code data-embed})은 썸네일이 없다.
 */
@Component
public class PostThumbnails {

    private static final QPostEntity POST = QPostEntity.postEntity;
    private static final Pattern YOUTUBE = Pattern.compile("data-youtube-id=\"([A-Za-z0-9_-]{11})\"");

    private final FileRepositoryQDSL files;
    private final PostRepositoryQDSL posts;
    private final ImageStorage storage;

    public PostThumbnails(FileRepositoryQDSL files, PostRepositoryQDSL posts, ImageStorage storage) {
        this.files = files;
        this.posts = posts;
        this.storage = storage;
    }

    /** 글 ID → 썸네일 주소(없는 글은 빠진다) */
    public Map<Long, String> of(Collection<Long> postIds) {
        Map<Long, String> out = new HashMap<>();
        if (postIds.isEmpty()) {
            return out;
        }
        files.firstImageKeys(postIds).forEach((postId, key) -> out.put(postId, storage.publicUrl(key)));
        List<Long> rest = postIds.stream().filter(id -> !out.containsKey(id)).distinct().toList();
        for (Tuple t : posts.youtubeContents(rest)) {
            Matcher m = YOUTUBE.matcher(t.get(POST.content));
            if (m.find()) {
                out.put(t.get(POST.id), "https://i.ytimg.com/vi/" + m.group(1) + "/hqdefault.jpg");
            }
        }
        return out;
    }
}
