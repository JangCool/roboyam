// 붓글씨 글자를 패스로 굳힌다(나눔손글씨 붓, OFL). node brush-text.mjs <글자> [크기=20] → brush-text.json { d, w, h } (왼쪽 위가 0,0)
import fs from 'node:fs'
import path from 'node:path'
import opentype from 'opentype.js'
const [text, sizeArg] = process.argv.slice(2)
const size = Number(sizeArg ?? 20)
const dir = path.resolve(process.env.LOGO_MODULES ?? 'node_modules', '@fontsource/nanum-brush-script/files')
const fonts = fs.readdirSync(dir).filter((f) => f.endsWith('-400-normal.woff')).map((f) => { const b = fs.readFileSync(path.join(dir, f)); try { return opentype.parse(b.buffer.slice(b.byteOffset, b.byteOffset + b.byteLength)) } catch { return null } }).filter(Boolean) // 일부 조각 파일은 opentype.js 가 못 읽는다 — 건너뛴다
let x = 0
const paths = []
for (const ch of text) {
  const ft = fonts.find((f) => f.charToGlyphIndex(ch) > 0)
  if (!ft) throw new Error('글자 없음: ' + ch)
  const g = ft.charToGlyph(ch)
  paths.push(g.getPath(x, 0, size))
  x += (g.advanceWidth / ft.unitsPerEm) * size * 0.92
}
let x1 = Infinity, y1 = Infinity, x2 = -Infinity, y2 = -Infinity
for (const p of paths) { const b = p.getBoundingBox(); x1 = Math.min(x1, b.x1); y1 = Math.min(y1, b.y1); x2 = Math.max(x2, b.x2); y2 = Math.max(y2, b.y2) }
const n = (v) => String(+v.toFixed(1))
let d = ''
for (const p of paths) for (const c of p.commands) {
  if (c.type === 'Z') { d += 'Z'; continue }
  const pts = c.type === 'Q' ? [c.x1, c.y1, c.x, c.y] : c.type === 'C' ? [c.x1, c.y1, c.x2, c.y2, c.x, c.y] : [c.x, c.y]
  d += c.type + pts.map((v, i) => n(v - (i % 2 ? y1 : x1))).join(' ')
}
const out = { text, d, w: +(x2 - x1).toFixed(1), h: +(y2 - y1).toFixed(1) }
fs.writeFileSync(new URL('./brush-text.json', import.meta.url), JSON.stringify(out))
console.log(text, out.w, out.h, d.length)
