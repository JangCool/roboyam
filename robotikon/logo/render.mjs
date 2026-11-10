import fs from 'node:fs'
import { createRequire } from 'node:module'
const require = createRequire('D:/project/roboyam/robotikon/package.json')
const { Resvg } = require('@resvg/resvg-js')
const items = [['ko','light','#FFFFFF'],['ko','dark','#2645A8'],['en','light','#FFFFFF'],['en','dark','#16181D']]
let y = 0, body = ''
for (const [l,t,bg] of items) {
  const s = fs.readFileSync(`out/roboyam-logo-${l}-${t}.svg`,'utf8')
  const inner = s.replace(/^<svg[^>]*>/,'').replace(/<\/svg>\s*$/,'')
  body += `<rect x="0" y="${y}" width="260" height="64" fill="${bg}"/><line x1="0" x2="260" y1="${y+12+23}" y2="${y+12+23}" stroke="#FF3B30" stroke-width="0.4" stroke-dasharray="2 2"/><g transform="translate(16 ${y+12})">${inner}</g>`
  y += 64
}
const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="260" height="${y}" viewBox="0 0 260 ${y}">${body}</svg>`
fs.writeFileSync('preview.png', new Resvg(svg, { fitTo: { mode: 'zoom', value: 3 } }).render().asPng())
