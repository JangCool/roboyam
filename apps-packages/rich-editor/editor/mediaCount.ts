import type { Node as PmNode } from '@tiptap/pm/model';

/** 문서 안의 이미지·유튜브 수 — 첨부 수 표시와 부모의 미리 알림에 쓴다(community `frontend/app/editor/mediaCount.ts`) */
export function countMedia(doc: PmNode): { images: number; videos: number } {
    let images = 0;
    let videos = 0;
    doc.descendants((node) => {
        if (node.type.name === 'image') images++;
        else if (node.type.name === 'youtube') videos++;
    });
    return { images, videos };
}
