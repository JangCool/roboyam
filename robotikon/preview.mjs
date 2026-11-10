// 크기별 미리보기: 디시는 200px 원본을 100px 로 보여준다. 80·60px 로도 줄여 밝은·어두운 배경에 늘어놓는다
// GIF 는 반투명이 없으므로 중간 PNG 가 아니라 out/ 의 GIF 를 풀어서 그린다
// 사용: node preview.mjs [m|f]  (node build.mjs 뒤) → preview-sizes-m.png, preview-sizes-f.png
import { existsSync, readFileSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { Resvg } from '@resvg/resvg-js'
import omggif from 'omggif'
import pngjs from 'pngjs'
import emotes from './src/emotes/index.mjs'
import { VARIANT_IDS } from './src/parts.mjs'

const { GifReader } = omggif
const { PNG } = pngjs

const ROOT = import.meta.dirname
const SIZES = [100, 80, 60]
const BG = ['#FFFFFF', '#1E1F24']
const G = 10
const PER = 8 // 한 묶음(밝은·어두운 배경 한 쌍)에 넣을 개수
// 이모티콘마다 특징이 잘 보이는 프레임
const PICK = { 10: 14, 15: 12, 16: 14, 18: 8, 20: 12, 25: 2, 26: 10, 27: 4, 28: 0, 29: 0, 30: 14, 31: 6, 32: 2 }

const meta = JSON.parse(readFileSync(join(ROOT, 'meta.json'), 'utf8'))
const asked = VARIANT_IDS.filter((v) => process.argv.includes(v))

for (const v of asked.length ? asked : VARIANT_IDS) {
  const gifPath = (id) => join(ROOT, 'out', v, `robotikon_${id}_${meta.items.find((m) => m.id === id)?.name}.gif`)
  const ids = emotes.map((e) => e.id).filter((id) => existsSync(gifPath(id)))

  function framePng(id, n) {
    const gif = new GifReader(readFileSync(gifPath(id)))
    const png = new PNG({ width: gif.width, height: gif.height })
    gif.decodeAndBlitFrameRGBA(Math.min(n, gif.numFrames() - 1), png.data)
    return PNG.sync.write(png).toString('base64')
  }

  const cellW = SIZES.reduce((a, s) => a + s + G, G)
  const rowH = SIZES[0] + G * 2
  const blockW = cellW * 2 + 30
  let svg = ''
  for (let g = 0; g * PER < ids.length; g++) {
    const gx = g * (blockW + 40)
    BG.forEach((bg, b) => {
      const ox = gx + b * (cellW + 30)
      let x = ox + G
      for (const s of SIZES) {
        svg += `<text x="${x + s / 2}" y="28" text-anchor="middle" font-size="16" font-weight="700" fill="#333">${s}px</text>`
        x += s + G
      }
      ids.slice(g * PER, g * PER + PER).forEach((id, r) => {
        const b64 = framePng(id, PICK[id] ?? 8)
        const y0 = 40 + r * rowH
        svg += `<rect x="${ox}" y="${y0}" width="${cellW}" height="${rowH}" fill="${bg}"/>`
        if (b === 0) svg += `<text x="${ox + 4}" y="${y0 + 16}" font-size="13" font-weight="700" fill="#8A94A6">${id}</text>`
        let cx = ox + G
        for (const s of SIZES) {
          svg += `<image x="${cx}" y="${y0 + G + SIZES[0] - s}" width="${s}" height="${s}" href="data:image/png;base64,${b64}"/>`
          cx += s + G
        }
      })
    })
  }
  const W = Math.ceil(ids.length / PER) * (blockW + 40) - 40
  const H = 40 + Math.min(PER, ids.length) * rowH
  const doc = `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}"><rect width="100%" height="100%" fill="#C9CED8"/>${svg}</svg>`
  const png = new Resvg(doc, { font: { fontFiles: ['C:/Windows/Fonts/malgunbd.ttf'], loadSystemFonts: false, defaultFontFamily: 'Malgun Gothic' } }).render().asPng()
  writeFileSync(join(ROOT, `preview-sizes-${v}.png`), png)
  console.log(`preview-sizes-${v}.png  ${ids.length}종 × ${SIZES.join('/')}px`)
}
