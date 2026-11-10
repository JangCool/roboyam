// 갤러리: out/m·out/f 의 GIF 와 대표 PNG 를 한 HTML 에 담아(data URI) 움직이는 그대로 본다
// 사용: node gallery.mjs  (node build.mjs 뒤) → gallery.html
import { existsSync, readFileSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import emotes from './src/emotes/index.mjs'
import { VARIANT_IDS } from './src/parts.mjs'

const ROOT = import.meta.dirname
const meta = JSON.parse(readFileSync(join(ROOT, 'meta.json'), 'utf8'))
const GROUPS = [['01', '16', '감정'], ['17', '24', '행동'], ['25', '32', '로봇 특화']]
const LABEL = { m: '남', f: '여' }
const esc = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;')
const data = (path, type) => `data:${type};base64,${readFileSync(path).toString('base64')}`

const sections = VARIANT_IDS.map((v) => {
  const pack = meta.packs[v]
  const dir = join(ROOT, 'out', v)
  const rep = meta.items.find((m) => m.id === pack.representative)
  const repPath = rep && join(dir, `robotikon_${rep.id}_${rep.name}_대표.png`)
  const groups = GROUPS.map(([from, to, title]) => {
    const cards = emotes.filter((e) => e.id >= from && e.id <= to).map((e) => {
      const it = meta.items.find((m) => m.id === e.id)
      const path = join(dir, `robotikon_${e.id}_${it.name}.gif`)
      if (!existsSync(path)) return ''
      return `<figure><img src="${data(path, 'image/gif')}" alt="${esc(it.alt)}"><figcaption><b>${e.id}</b> ${esc(it.name)} <span>~${esc(it.alt)}</span></figcaption></figure>`
    }).join('')
    return `<h3>${title} ${from}~${to}</h3><div class="grid">${cards}</div>`
  }).join('')
  const repImg = repPath && existsSync(repPath) ? `<img class="rep" src="${data(repPath, 'image/png')}" alt="대표">` : ''
  return `<section data-v="${v}"><header>${repImg}<div><h2>${LABEL[v]} · ${esc(pack.name)}</h2><p>${esc(pack.description)}</p><p class="tags">${pack.tags.map((t) => `#${esc(t)}`).join(' ')}</p></div></header>${groups}</section>`
}).join('')

const html = `<!doctype html>
<html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>로보얌콘 갤러리</title>
<style>
:root { --bg: #f4f6fa; --card: #ffffff; --ink: #0e1f4d; --muted: #6b7489; --line: #dde2ec; }
body.dark { --bg: #17181c; --card: #1e1f24; --ink: #e8ebf3; --muted: #9aa3b5; --line: #2c2e36; }
* { box-sizing: border-box; }
body { margin: 0; padding: 16px; background: var(--bg); color: var(--ink); font-family: 'Malgun Gothic', 'Noto Sans KR', sans-serif; }
.bar { position: sticky; top: 0; z-index: 1; display: flex; flex-wrap: wrap; gap: 8px; padding: 10px 0; background: var(--bg); }
.bar button { border: 1px solid var(--line); background: var(--card); color: var(--ink); border-radius: 999px; padding: 6px 14px; font: inherit; cursor: pointer; }
.bar button.on { background: var(--ink); color: var(--bg); }
header { display: flex; gap: 16px; align-items: center; margin: 18px 0 4px; }
header .rep { width: 100px; height: 100px; background: var(--card); border: 1px solid var(--line); border-radius: 12px; }
h2 { margin: 0 0 4px; font-size: 20px; } h3 { margin: 22px 0 8px; font-size: 15px; color: var(--muted); }
header p { margin: 2px 0; font-size: 13px; color: var(--muted); }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(var(--w, 120px), 1fr)); gap: 10px; }
figure { margin: 0; padding: 10px 6px 8px; background: var(--card); border: 1px solid var(--line); border-radius: 12px; text-align: center; }
figure img { width: var(--px, 100px); height: var(--px, 100px); display: block; margin: 0 auto 6px; }
figcaption { font-size: 12px; line-height: 1.4; } figcaption span { color: var(--muted); }
section[hidden] { display: none; }
</style></head>
<body>
<div class="bar">
  <button data-show="all" class="on">남·여 모두</button><button data-show="m">남</button><button data-show="f">여</button>
  <button data-size="100" class="on">100px(디시 실제 크기)</button><button data-size="80">80px</button><button data-size="200">200px(원본)</button>
  <button data-theme>다크 배경</button>
</div>
${sections}
<script>
const $ = (s) => [...document.querySelectorAll(s)]
const pick = (attr, el) => { $('[' + attr + ']').forEach((b) => b.classList.toggle('on', b === el)) }
$('[data-show]').forEach((b) => b.onclick = () => { pick('data-show', b); $('section').forEach((s) => s.hidden = b.dataset.show !== 'all' && s.dataset.v !== b.dataset.show) })
$('[data-size]').forEach((b) => b.onclick = () => { pick('data-size', b); const px = +b.dataset.size; document.body.style.setProperty('--px', px + 'px'); document.body.style.setProperty('--w', Math.max(px + 20, 110) + 'px') })
const t = document.querySelector('[data-theme]')
t.onclick = () => { document.body.classList.toggle('dark'); t.classList.toggle('on') }
</script>
</body></html>
`
writeFileSync(join(ROOT, 'gallery.html'), html)
console.log(`gallery.html  ${(html.length / 1024 / 1024).toFixed(1)}MB`)
