/**
 * 에디터의 이미지·유튜브 규칙 — community `frontend/app/utils/util-media.ts` 에서 옮겼다.
 * 서버가 거절할 것을 화면이 **보내기 전에** 미리 알려 주는 용도이고, 판정은 늘 서버가 한다(서버 문구를 그대로 보인다).
 * 로보얌 서버의 용량·장수 한도는 화면이 모른다(서버 설정값) — 그래서 미리 보는 것은 **형식**(JPG·PNG·GIF·WebP)과 빈 파일뿐이고, 크기·한도·속도는 서버 문구로 알린다.
 * 레이어의 순수 코어 — `vue`·Nuxt 자동 임포트를 쓰지 않는다.
 */

const MB = 1024 * 1024;

const MIME_BY_EXT: Record<string, string> = {
    jpg: 'image/jpeg',
    jpeg: 'image/jpeg',
    png: 'image/png',
    gif: 'image/gif',
    webp: 'image/webp',
};
const ALLOWED_MIME = new Set(Object.values(MIME_BY_EXT));

/** 유튜브 영상 ID — 영문·숫자·`-`·`_` 11자 */
const YOUTUBE_ID = /^[A-Za-z0-9_-]{11}$/;
/** 유튜브 주소로 받는 호스트 */
const YOUTUBE_HOSTS = new Set(['youtube.com', 'www.youtube.com', 'm.youtube.com', 'music.youtube.com', 'youtu.be', 'www.youtube-nocookie.com', 'youtube-nocookie.com']);

/** 유튜브 밖의 영상 플랫폼 — 공식 퍼가기 플레이어가 있고 다른 사이트에서 띄울 수 있는 것만(2026-11-06 확인) */
export type RichEmbedProvider = 'chzzk' | 'soop' | 'tiktok' | 'instagram';

/** 영상 하나 — 플랫폼과 ID. 글에는 `<div data-embed="플랫폼:ID">` 로만 실린다(주소는 싣지 않는다) */
export interface RichEmbed {
    provider: RichEmbedProvider;
    id: string;
}

/** 플랫폼별 ID 형식 — 서버 `RichText.EMBED` 와 같다 */
const EMBED_ID: Record<RichEmbedProvider, RegExp> = {
    chzzk: /^[A-Za-z0-9]{6,20}$/,
    soop: /^[1-9]\d{0,11}$/,
    tiktok: /^\d{10,25}$/,
    instagram: /^[A-Za-z0-9_-]{5,40}$/,
};

/**
 * 누른 뒤에 만드는 퍼가기 주소 — 플랫폼이 정한 틀에 ID 만 끼운다(사용자가 쓴 주소는 쓰지 않는다)
 *  - 치지직: 클립만 퍼가기가 있다(`/embed/clip/{클립ID}` — 다시보기·생방송은 없다)
 *  - SOOP: 다시보기·클립 플레이어의 `/player/{번호}/embed`
 *  - 틱톡: 공식 Embed Player `player/v1/{영상번호}`
 *  - 인스타그램: 게시물·릴스의 `/p/{단축 코드}/embed`(공개 게시물만 보인다)
 */
const EMBED_URL: Record<RichEmbedProvider, (id: string) => string> = {
    chzzk: (id) => `https://chzzk.naver.com/embed/clip/${id}`,
    soop: (id) => `https://vod.sooplive.com/player/${id}/embed`,
    tiktok: (id) => `https://www.tiktok.com/player/v1/${id}?autoplay=1`,
    instagram: (id) => `https://www.instagram.com/p/${id}/embed`,
};

/** 세로 영상 플랫폼(카드가 9:16) */
const TALL_PROVIDERS = new Set<RichEmbedProvider>(['tiktok', 'instagram']);

/** 주소 → 플랫폼·ID. 호스트와 경로 꼴이 맞을 때만 읽는다 */
function embedFromUrl(url: URL): RichEmbed | null {
    const host = url.hostname.toLowerCase().replace(/^(www|m)\./, '');
    const parts = url.pathname.split('/').filter(Boolean);
    let found: RichEmbed | null = null;
    if (host === 'chzzk.naver.com') {
        // chzzk.naver.com/clips/{ID} · /embed/clip/{ID}
        if (parts[0] === 'clips' && parts[1]) found = { provider: 'chzzk', id: parts[1] };
        else if (parts[0] === 'embed' && parts[1] === 'clip' && parts[2]) found = { provider: 'chzzk', id: parts[2] };
    } else if (['vod.sooplive.co.kr', 'vod.sooplive.com', 'vod.afreecatv.com'].includes(host)) {
        // vod.sooplive.co.kr/player/{번호}(/embed …)
        if (parts[0] === 'player' && parts[1]) found = { provider: 'soop', id: parts[1] };
    } else if (host === 'tiktok.com') {
        // tiktok.com/@아이디/video/{번호} · /player/v1/{번호} · /embed/v2/{번호}
        if (parts[0]?.startsWith('@') && parts[1] === 'video' && parts[2]) found = { provider: 'tiktok', id: parts[2] };
        else if (parts[0] === 'player' && parts[1] === 'v1' && parts[2]) found = { provider: 'tiktok', id: parts[2] };
        else if (parts[0] === 'embed' && parts[1] === 'v2' && parts[2]) found = { provider: 'tiktok', id: parts[2] };
    } else if (host === 'instagram.com') {
        // instagram.com/p/{코드} · /reel/{코드} · /reels/{코드} · /tv/{코드} (앞에 아이디가 붙은 /{아이디}/reel/{코드} 도)
        const at = parts.findIndex((part) => ['p', 'reel', 'reels', 'tv'].includes(part));
        if (at >= 0 && at <= 1 && parts[at + 1]) found = { provider: 'instagram', id: parts[at + 1] as string };
    }
    return found && EMBED_ID[found.provider].test(found.id) ? found : null;
}

/** 파일 이름 끝의 확장자(소문자). 없으면 빈 문자열 */
function extensionOf(name: string): string {
    const dot = name.lastIndexOf('.');
    return dot < 0 ? '' : name.slice(dot + 1).toLowerCase();
}

/** `File.type` 과 확장자로 본 형식. 둘 다 모르면 빈 문자열 */
function mimeOf(file: Pick<File, 'name' | 'type'>): string {
    return file.type.toLowerCase() || MIME_BY_EXT[extensionOf(file.name)] || '';
}

/** 보내기 전 검사의 문제 — 서버 오류 코드와 같은 이름을 쓴다(문구는 `UtilRichMessages.precheck`) */
export type RichImagePrecheck = 'IMAGE_UNSUPPORTED' | 'IMAGE_EMPTY';

export default class UtilRichMedia {
    /** 파일 선택 창에 걸어 두는 형식 */
    static readonly ACCEPT = 'image/jpeg,image/png,image/gif,image/webp';

    /**
     * 파일이 GIF 인지 봅니다.
     * @param {Pick<File, 'name' | 'type'>} file - 이름과 형식을 가진 파일.
     * @returns {boolean} GIF 이면 true.
     */
    static isGif(file: Pick<File, 'name' | 'type'>): boolean {
        return mimeOf(file) === 'image/gif';
    }

    /**
     * 1.8MB · 340KB — 파일 크기를 사람이 읽는 글자로
     * @param {number} bytes - 바이트 수.
     * @returns {string} "1.8MB" · "340KB" 꼴의 글자.
     */
    static formatBytes(bytes: number): string {
        if (bytes >= MB) return `${(bytes / MB).toFixed(1)}MB`;
        return `${Math.max(1, Math.round(bytes / 1024))}KB`;
    }

    /**
     * 보내기 전에 보는 형식 검사. 문제가 없으면 null (그래도 판정은 서버가 한다)
     * @param {Pick<File, 'name' | 'type' | 'size'>} file - 이름·형식·크기를 가진 파일.
     * @returns {RichImagePrecheck | null} 문제가 있으면 코드, 없으면 null.
     */
    static precheck(file: Pick<File, 'name' | 'type' | 'size'>): RichImagePrecheck | null {
        const mime = mimeOf(file);
        // 형식을 아는데 허용 목록에 없거나(BMP·SVG·PDF …), 형식을 모르는데 확장자도 허용 목록에 없으면 거절한다
        const unsupported = mime ? !ALLOWED_MIME.has(mime) : !(extensionOf(file.name) in MIME_BY_EXT);
        if (unsupported) return 'IMAGE_UNSUPPORTED';
        if (file.size <= 0) return 'IMAGE_EMPTY';
        return null;
    }

    /**
     * 유튜브 영상 ID 형식인가 — 영문·숫자·`-`·`_` 11자 (서버도 저장 때 다시 검사한다)
     * @param {unknown} id - 검사할 값.
     * @returns {boolean} 11자 형식의 영상 ID 이면 true(이때 `string` 으로 좁혀진다).
     */
    static isYoutubeId(id: unknown): id is string {
        return typeof id === 'string' && YOUTUBE_ID.test(id);
    }

    /**
     * 붙여 넣은 유튜브 주소에서 영상 ID 를 꺼낸다 — `youtu.be/ID` · `youtube.com/watch?v=ID` · `/shorts/ID` · `/embed/ID` · `/live/ID` · `youtube-nocookie.com/embed/ID`, 또는 ID 그대로.
     * 로보얌은 서버의 주소 확인 API 가 없어 화면이 읽는다(community 는 서버 `POST /api/media/youtube` 가 확인했다). 글에 실리는 것은 ID 뿐이고 서버가 저장 때 형식을 다시 본다.
     * @param {string} raw - 입력한 주소.
     * @returns {string | null} 영상 ID. 읽을 수 없으면 null.
     */
    static youtubeIdOf(raw: string): string | null {
        const value = raw.trim();
        if (UtilRichMedia.isYoutubeId(value)) return value;
        let url: URL;
        try {
            url = new URL(/^[a-z][a-z0-9+.-]*:/i.test(value) ? value : `https://${value}`);
        } catch {
            return null;
        }
        if (!['http:', 'https:'].includes(url.protocol) || !YOUTUBE_HOSTS.has(url.hostname.toLowerCase())) return null;
        const parts = url.pathname.split('/').filter(Boolean);
        let id: string | null | undefined;
        if (url.hostname.toLowerCase() === 'youtu.be') id = parts[0];
        else if (parts[0] === 'watch') id = url.searchParams.get('v');
        else if (['shorts', 'embed', 'live', 'v'].includes(parts[0] ?? '')) id = parts[1];
        return UtilRichMedia.isYoutubeId(id) ? id : null;
    }

    /**
     * 글 보기·에디터가 쓰는 유튜브 썸네일(구글 서버) — 누르기 전에는 이 그림만 불러온다
     * @param {string} id - 유튜브 영상 ID.
     * @returns {string} 썸네일 주소.
     */
    static youtubeThumbnail(id: string): string {
        return `https://i.ytimg.com/vi/${id}/hqdefault.jpg`;
    }

    /**
     * 누른 뒤에만 만드는 임베드 주소 — 쿠키를 심지 않는 도메인(youtube-nocookie.com)
     * @param {string} id - 유튜브 영상 ID.
     * @returns {string} 임베드 주소.
     */
    static youtubeEmbedUrl(id: string): string {
        return `https://www.youtube-nocookie.com/embed/${id}?autoplay=1`;
    }

    /**
     * 붙여 넣은 주소에서 유튜브 밖의 영상(치지직 클립·SOOP 다시보기·틱톡·인스타그램)을 읽는다. 단축 주소(`vm.tiktok.com` 등)는 화면이 풀 수 없어 읽지 않는다(링크로 붙는다).
     * @param {string} raw - 입력한 주소(http·https).
     * @returns {RichEmbed | null} 플랫폼·ID. 읽을 수 없으면 null.
     */
    static embedOf(raw: string): RichEmbed | null {
        let url: URL;
        try {
            url = new URL(raw.trim());
        } catch {
            return null;
        }
        if (!['http:', 'https:'].includes(url.protocol)) return null;
        return embedFromUrl(url);
    }

    /**
     * 글에 싣는 표식 값 `플랫폼:ID`
     * @param {RichEmbed} embed - 영상.
     * @returns {string} 표식 값.
     */
    static embedMarker(embed: RichEmbed): string {
        return `${embed.provider}:${embed.id}`;
    }

    /**
     * 표식 값 `플랫폼:ID` → 영상. 모르는 플랫폼이거나 ID 형식이 틀리면 null(그리지 않는다)
     * @param {string | null | undefined} value - `data-embed` 값.
     * @returns {RichEmbed | null} 영상.
     */
    static parseEmbedMarker(value: string | null | undefined): RichEmbed | null {
        const at = value?.indexOf(':') ?? -1;
        if (!value || at < 0) return null;
        const provider = value.slice(0, at) as RichEmbedProvider;
        const id = value.slice(at + 1);
        return Object.hasOwn(EMBED_ID, provider) && EMBED_ID[provider].test(id) ? { provider, id } : null;
    }

    /**
     * 누른 뒤에만 만드는 퍼가기 주소 — 플랫폼이 정한 틀에 ID 만 끼운다
     * @param {RichEmbed} embed - 영상(형식을 본 것).
     * @returns {string} 퍼가기 주소.
     */
    static embedUrl(embed: RichEmbed): string {
        return EMBED_URL[embed.provider](embed.id);
    }

    /**
     * 세로 영상 플랫폼인가(틱톡·인스타그램 — 카드가 9:16)
     * @param {RichEmbedProvider} provider - 플랫폼.
     * @returns {boolean} 세로면 true.
     */
    static isTallEmbed(provider: RichEmbedProvider): boolean {
        return TALL_PROVIDERS.has(provider);
    }

    /**
     * 이미지 주소로 써도 되는 것(사이트 안 경로 또는 http·https) — 그 밖의 값(`javascript:` 등)은 버린다
     * @param {string | null | undefined} value - 이미지 주소 후보.
     * @returns {string | null} 써도 되는 주소. 아니면 null.
     */
    static safeImageUrl(value: string | null | undefined): string | null {
        if (!value) return null;
        if (value.startsWith('/') && !value.startsWith('//')) return value;
        return /^https?:\/\//i.test(value) ? value : null;
    }
}
