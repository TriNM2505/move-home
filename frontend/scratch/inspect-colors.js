const { Jimp } = require('jimp');
const path = require('path');

async function main() {
  const imagePath = 'C:\\Users\\Admin\\.gemini\\antigravity-ide\\brain\\824da5be-39e6-4ad2-aa91-a75fa6dad870\\media__1782078101225.png';
  const image = await Jimp.read(imagePath);
  
  const w = image.width;
  const h = image.height;
  
  const yMid = Math.floor(h / 2);
  
  console.log("Middle row pixel colors (every 20 pixels):");
  for (let x = 0; x < w; x += 20) {
    const color = image.getPixelColor(x, yMid);
    const rgba = Jimp.intToRGBA(color);
    console.log(`x=${x}: rgba(${rgba.r},${rgba.g},${rgba.b},${rgba.a})`);
  }
}

main().catch(console.error);
