// MOCK: 우수 글 선정·취소(point-board-tier-contract — 후보 `POST/DELETE /api/admin/posts/{id}/feature`). 서버가 생기기 전까지 이 탭의 메모리에만 남는다(새로 고치면 사라진다).
// 서버가 붙으면 이 파일과 `usePostApi.ts` 의 `// MOCK:` 줄을 지운다.

import type { AdminPostDetail, AdminPostItem, AdminPostListView } from '~/composables/post/api/usePostApi.types';

/** 우수 글로 선정한 글 ID */
const featuredIds = new Set<string>();

/** 300~700ms 기다린다 */
function delay(): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, 300 + Math.random() * 400));
}

function mark(item: AdminPostItem): AdminPostItem {
    return item.featured === undefined ? { ...item, featured: featuredIds.has(item.id) } : item;
}

/** 목록 줄에 선정 여부를 채운다(서버가 `featured` 를 주면 그대로) */
export function withFeaturedList(view: AdminPostListView): AdminPostListView {
    return { ...view, items: view.items.map(mark) };
}

/** 상세 글에 선정 여부를 채운다 */
export function withFeaturedDetail(detail: AdminPostDetail): AdminPostDetail {
    return { ...detail, post: mark(detail.post) };
}

/** 선정(on=true)·취소(on=false) — 지연 뒤 성공 */
export async function mockFeature(id: string, on: boolean): Promise<void> {
    await delay();
    if (on) featuredIds.add(id);
    else featuredIds.delete(id);
}
