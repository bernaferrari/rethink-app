// Re-Rethink: one deep-wine field under the whole strip, crossed by the "cipher current" of the
// Home screen: loose packets that drift in from the left and fall into line as woven strands.
// Everything is drawn in panorama coordinates, so the strip joins exactly in the store carousel.
import {panoramaGradient, panoramaX} from './studio/shapes.js';

// Deterministic noise, so every export draws the same packets.
function random(seed) {
  let s = seed >>> 0;
  return () => ((s = (s * 1664525 + 1013904223) >>> 0) / 4294967296);
}

// Strands: height (fraction of the canvas), wave amplitude, wavelength in card widths, phase.
const strands = [
  {y: .70, amp: .07, wave: 2.6, phase: 0, size: 1},
  {y: .74, amp: .06, wave: 2.6, phase: Math.PI * .66, size: .9},
  {y: .78, amp: .05, wave: 2.6, phase: Math.PI * 1.33, size: .8},
  {y: .29, amp: .04, wave: 3.4, phase: Math.PI * .4, size: .6},
];

function art(ctx) {
  const {W, H, slides, index} = ctx;
  const unit = Math.max(W, H);
  const landscape = W > H;
  let defs = panoramaGradient(ctx, 'field', ['#2a0c15', '#35101c', '#2d0d18', '#3a1220', '#2b0c16', '#36111d']);
  let body = `<rect width="${W}" height="${H}" fill="url(#field)"/>`;
  // A warm light behind each card's product, wide enough to spill into the neighbours.
  slides.forEach((_, k) => {
    const cx = panoramaX(ctx, k + (landscape ? .68 : .5)), cy = H * (landscape ? .55 : .72);
    defs += `<radialGradient id="glow${k}" gradientUnits="userSpaceOnUse" cx="${cx}" cy="${cy}" r="${unit * .6}"><stop offset="0" stop-color="#ff7a68" stop-opacity=".30"/><stop offset=".5" stop-color="#ff7a68" stop-opacity=".08"/><stop offset="1" stop-color="#ff7a68" stop-opacity="0"/></radialGradient>`;
    body += `<rect width="${W}" height="${H}" fill="url(#glow${k})"/>`;
  });

  // Packets along each strand. Order grows along the strip: early cards scatter the packets,
  // later ones tighten them into strands, with a slow breathing between.
  const step = unit * .016;
  const total = slides.length * W;
  let dots = '';
  strands.forEach((strand, s) => {
    const rnd = random(941 + s * 97);
    for (let gx = -step; gx < total + step; gx += step) {
      const along = gx / total;
      const order = Math.min(1, .25 + along * 1.1) * (.8 + .2 * Math.sin(gx / W * 2.1 + s));
      const jitter = (1 - order) * H * .09;
      const local = gx - index * W;
      const theta = (gx / W) / strand.wave * Math.PI * 2 + strand.phase;
      const r1 = rnd(), r2 = rnd(), r3 = rnd();
      if (local < -40 || local > W + 40) continue;
      const y = H * (strand.y + strand.amp * Math.sin(theta)) + (r1 - .5) * 2 * jitter;
      const x = local + (r2 - .5) * step * (1 - order) * 1.6;
      const radius = (3.2 + r3 * 3.6) * strand.size * (unit / 1920);
      const opacity = (.28 + .5 * order) * strand.size * (r3 > .85 ? 1.5 : 1);
      dots += `<circle cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="${radius.toFixed(2)}" fill="#ffb4a6" fill-opacity="${Math.min(.9, opacity).toFixed(3)}"/>`;
    }
  });
  // Packets stay faint behind the headline and brighten around the screens.
  defs += `<linearGradient id="hushg" x1="0" y1="0" x2="${landscape ? 1 : 0}" y2="${landscape ? 0 : 1}"><stop offset="0" stop-color="#fff" stop-opacity=".25"/><stop offset=".38" stop-color="#fff" stop-opacity=".45"/><stop offset=".6" stop-color="#fff" stop-opacity="1"/></linearGradient><mask id="hush" maskUnits="userSpaceOnUse" x="0" y="0" width="${W}" height="${H}"><rect width="${W}" height="${H}" fill="url(#hushg)"/></mask>`;
  body += `<g mask="url(#hush)">${dots}</g>`;
  defs += `<linearGradient id="dusk" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#14040a" stop-opacity=".3"/><stop offset=".4" stop-color="#14040a" stop-opacity="0"/><stop offset="1" stop-color="#14040a" stop-opacity=".25"/></linearGradient>`;
  return `<defs>${defs}</defs>${body}<rect width="${W}" height="${H}" fill="url(#dusk)"/>`;
}

export default {
  art,

  compose({index, main}) {
    if (index !== 0) return;
    // The app opens the set; later cards carry only their headline.
    main.classList.add('first');
    const brand = document.createElement('div');
    brand.className = 'brand';
    brand.dataset.checkBounds = '';
    brand.innerHTML = '<img src="../brand/icon.png" alt="">Re-Rethink';
    main.append(brand);
  },

  /** Bezel and corners follow the frame's width, so small and large screens look like one device family. */
  layout({main}) {
    for (const node of main.querySelectorAll('.device:not(.device-frame-none)')) {
      const image = node.querySelector('img'), width = node.clientWidth;
      const landscape = image.naturalWidth > image.naturalHeight;
      const tablet = node.classList.contains('device-frame-tablet');
      node.style.setProperty('--bezel', `${Math.round(width * (landscape ? .012 : tablet ? .018 : .02))}px`);
      node.style.setProperty('--radius', `${Math.round(width * (landscape ? .035 : tablet ? .05 : .09))}px`);
    }
  },

  // Screens may run off the bottom (or hang from the top on the stacked card), never off the sides,
  // and most of each screen stays in view. Landscape tablets stay whole.
  decorate({W, H, slide, devices}) {
    const stacked = /stack/.test(slide.layout || '');
    for (const {box, spec} of devices) {
      if (box.left < 0 || box.left + box.width > W + 1) throw new Error(`Screen leaves the side of the card: ${spec.raw}`);
      if (stacked) continue;
      if (box.top < 0) throw new Error(`Screen leaves the top of the card: ${spec.raw}`);
      const landscapeScreen = box.width > box.height;
      if (landscapeScreen && box.top + box.height > H + 1) throw new Error(`Landscape tablet is cut: ${spec.raw}`);
      if ((H - box.top) / box.height < .6) throw new Error(`Too little of ${spec.raw} is visible`);
    }
  },
};
