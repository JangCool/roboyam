import { Extension } from '@tiptap/vue-3';
import type { Editor } from '@tiptap/vue-3';
import { Plugin, PluginKey } from '@tiptap/pm/state';

/**
 * 올리는 중인 이미지가 **어디에 들어갈지**를 기억한다.
 * 올리는 동안 글을 고쳐도(앞에 글자를 쓰거나 지워도) 자리가 함께 밀려, 다 올라오면 고른 자리에 들어간다.
 * 자리는 문서에 노드로 남기지 않는다 — 문서에는 올리기가 끝난 이미지만 들어가므로 `getHTML()` 이 늘 깨끗하고 되돌리기(undo)도 안전하다.
 * (community `frontend/app/editor/uploadSlots.ts` 그대로)
 */
type Slots = Record<string, number>;

interface SlotMeta {
    add?: { id: string; pos: number };
    remove?: string;
}

const key = new PluginKey<Slots>('uploadSlots');

export const UploadSlots = Extension.create({
    name: 'uploadSlots',
    addProseMirrorPlugins() {
        return [
            new Plugin<Slots>({
                key,
                state: {
                    init: () => ({}),
                    apply(tr, value) {
                        let next = value;
                        if (tr.docChanged) {
                            next = {};
                            // 두 장이 같은 자리에 올라오면 먼저 들어간 이미지 뒤로 밀려 순서가 지켜진다(assoc 1)
                            for (const [id, pos] of Object.entries(value)) next[id] = tr.mapping.map(pos, 1);
                        }
                        const meta = tr.getMeta(key) as SlotMeta | undefined;
                        if (meta?.add) next = { ...next, [meta.add.id]: meta.add.pos };
                        if (meta?.remove) {
                            next = { ...next };
                            delete next[meta.remove];
                        }
                        return next;
                    },
                },
            }),
        ];
    },
});

function dispatchMeta(editor: Editor, meta: SlotMeta): void {
    editor.view.dispatch(editor.state.tr.setMeta(key, meta).setMeta('addToHistory', false));
}

/** 이 이미지가 들어갈 자리를 정한다 */
export function reserveSlot(editor: Editor, id: string, pos: number): void {
    dispatchMeta(editor, { add: { id, pos } });
}

/** 자리를 꺼내고(잊는다) 지금의 위치를 돌려준다. 자리를 모르면 null */
export function takeSlot(editor: Editor, id: string): number | null {
    const pos = key.getState(editor.state)?.[id];
    dispatchMeta(editor, { remove: id });
    return typeof pos === 'number' ? Math.min(pos, editor.state.doc.content.size) : null;
}

/** 자리만 잊는다(올리기를 취소·삭제했을 때) */
export function releaseSlot(editor: Editor, id: string): void {
    if (key.getState(editor.state)?.[id] === undefined) return;
    dispatchMeta(editor, { remove: id });
}
