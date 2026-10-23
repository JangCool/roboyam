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
