// Re-Rethink: "the gate". One dark field runs under the whole strip, and through it a stream of
// packets: green ones are allowed, coral ones are blocked. Wherever the stream meets a device, the
// device is the firewall: blocked packets pile up against its edge and only green ones come out the
// other side, until new traffic mixes back in before the next card. Devices alternate sides, so the
// strip reads as traffic passing gate after gate.
import {panoramaGradient, panoramaX} from './studio/shapes.js';

const ALLOWED = '#6fe0a2';
const BLOCKED = '#ff6f61';

// Deterministic noise, so every export draws the same packets.
function random(seed) {
  let s = seed >>> 0;
  return () => ((s = (s * 1664525 + 1013904223) >>> 0) / 4294967296);
}

/** Lane heights as fractions of the canvas: clear of the copy in every layout. */
const lanes = landscape => landscape ? [.585, .615, .645, .675, .705] : [.55, .575, .6, .625, .65];

function art(ctx) {
  const {W, H, slides} = ctx;
  const unit = Math.max(W, H);
  const landscape = W > H;
  let defs = panoramaGradient(ctx, 'field', ['#170910', '#1d0b14', '#180a11', '#200c16', '#170910', '#1e0b14']);
  let body = `<rect width="${W}" height="${H}" fill="url(#field)"/>`;
  // A faint wash of light behind each card's product, wide enough to join its neighbours.
  slides.forEach((_, k) => {
    const cx = panoramaX(ctx, k + (landscape ? .66 : .5)), cy = H * (landscape ? .5 : .62);
    defs += `<radialGradient id="wash${k}" gradientUnits="userSpaceOnUse" cx="${cx}" cy="${cy}" r="${unit * .55}"><stop offset="0" stop-color="#ff7a68" stop-opacity=".16"/><stop offset=".55" stop-color="#ff7a68" stop-opacity=".04"/><stop offset="1" stop-color="#ff7a68" stop-opacity="0"/></radialGradient>`;
    body += `<rect width="${W}" height="${H}" fill="url(#wash${k})"/>`;
  });
  // The lane bed: a quiet band the packets travel in, continuous across every card.
  const ys = lanes(landscape).map(f => f * H);
  const top = ys[0] - unit * .02, bottom = ys[ys.length - 1] + unit * .02;
  defs += `<linearGradient id="bed" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#ffffff" stop-opacity="0"/><stop offset=".5" stop-color="#ffffff" stop-opacity=".035"/><stop offset="1" stop-color="#ffffff" stop-opacity="0"/></linearGradient>`;
  body += `<rect x="0" y="${top}" width="${W}" height="${bottom - top}" fill="url(#bed)"/>`;
  return `<defs>${defs}</defs>${body}`;
}

/** The devices crossing a lane at height y, as left-to-right spans (overlapping ones merged). */
function gatesAt(devices, y) {
  const spans = devices
    .filter(({box}) => box.top <= y && y <= box.top + box.height)
    .map(({box}) => ({left: box.left, right: box.left + box.width}))
    .sort((a, b) => a.left - b.left);
  const merged = [];
  for (const span of spans) {
    const last = merged[merged.length - 1];
    if (last && span.left <= last.right) last.right = Math.max(last.right, span.right);
    else merged.push({...span});
  }
  return merged;
}

function stream({W, H, index, slides, devices}) {
  const unit = Math.max(W, H);
  const s = unit / 1920;
  const landscape = W > H;
  const ys = lanes(landscape).map(f => f * H);
  const step = 34 * s;
  const total = slides.length * W;
  let packets = '';
  ys.forEach((y, li) => {
    const rnd = random(941 + li * 131);
    const gates = gatesAt(devices, y);
    for (let gx = 0; gx < total; gx += step) {
      const r1 = rnd(), r2 = rnd(), r3 = rnd(), r4 = rnd();
      const length = (12 + r1 * 22) * s;
      let x = gx + (r2 - .5) * step * .7 - index * W;
      if (x < -60 || x > W + 60) continue;
      const blocked = r3 < .36;
      const lane = y + (r4 - .5) * 10 * s;
      let opacity = blocked ? .9 : .72;
      if (gates.some(g => x + length > g.left - 4 * s && x < g.right + 4 * s)) continue;
      const next = gates.find(g => g.left >= x);
      const previous = [...gates].reverse().find(g => g.right <= x);
      if (blocked && next && next.left - x < 300 * s) {
        // Blocked packets slow down and pile up against the device ahead of them.
        x = next.left - length - 8 * s - (next.left - x) * .34;
        opacity = .95;
      } else if (blocked && previous) {
        // Past a gate only allowed traffic flows; new traffic mixes back in towards the next one.
        const room = Math.max(1, (next ? next.left : W) - previous.right);
        if (r4 > Math.pow((x - previous.right) / room, 1.4)) continue;
      }
      const h = 8 * s;
      packets += `<rect x="${x.toFixed(1)}" y="${(lane - h / 2).toFixed(1)}" width="${length.toFixed(1)}" height="${h.toFixed(1)}" rx="${(h / 2).toFixed(1)}" fill="${blocked ? BLOCKED : ALLOWED}" fill-opacity="${opacity}"/>`;
    }
  });
  // Light where each wall holds traffic back, and a green trail where allowed traffic leaves.
  const mid = ys[Math.floor(ys.length / 2)];
  let defs = '', lights = '';
  gatesAt(devices, mid).forEach((g, k) => {
    defs += `<radialGradient id="hold${k}" gradientUnits="userSpaceOnUse" cx="${g.left}" cy="${mid}" r="${260 * s}"><stop offset="0" stop-color="${BLOCKED}" stop-opacity=".5"/><stop offset="1" stop-color="${BLOCKED}" stop-opacity="0"/></radialGradient>`;
    defs += `<radialGradient id="pass${k}" gradientUnits="userSpaceOnUse" cx="${g.right}" cy="${mid}" r="${200 * s}"><stop offset="0" stop-color="${ALLOWED}" stop-opacity=".28"/><stop offset="1" stop-color="${ALLOWED}" stop-opacity="0"/></radialGradient>`;
    lights += `<rect width="${W}" height="${H}" fill="url(#hold${k})"/><rect width="${W}" height="${H}" fill="url(#pass${k})"/>`;
  });
  return `<defs>${defs}</defs>${lights}${packets}`;
}

export default {
  art,

  compose({index, main, kind}) {
    if (index !== 0 || kind === 'readme') return;
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
      if (main.classList.contains('compare')) break;
      const image = node.querySelector('img'), width = node.clientWidth;
      const landscape = image.naturalWidth > image.naturalHeight;
      const tablet = node.classList.contains('device-frame-tablet');
      node.style.setProperty('--bezel', `${Math.round(width * (landscape ? .012 : tablet ? .018 : .02))}px`);
      node.style.setProperty('--radius', `${Math.round(width * (landscape ? .035 : tablet ? .05 : .09))}px`);
    }
  },

  // Screens may run off the bottom (or hang from the top), never off the sides, and most of each
  // screen stays in view. Landscape tablets stay whole. Then the packet stream meets the devices.
  decorate(ctx) {
    const {W, H, slide, devices, art: svg} = ctx;
    if (slide.layout === 'compare') {
      // Each name sits just under its screen; the comparison stays calm, without the stream.
      for (const label of devices[0].node.parentElement.querySelectorAll('.device-label')) label.style.setProperty('--h', `${devices[0].box.height}px`);
      return;
    }
    const hanging = /hang/.test(slide.layout || '');
    for (const {box, spec} of devices) {
      const landscapeScreen = box.width > box.height;
      // Landscape tablets may run off the right edge (most of the screen stays in view); phones never leave the sides.
      const rightOverflow = (box.left + box.width - W) / box.width;
      if (box.left < 0 || rightOverflow > (landscapeScreen ? .22 : 0.001)) throw new Error(`Screen leaves the side of the card: ${spec.raw}`);
      if (landscapeScreen && (box.top < 0 || box.top + box.height > H + 1)) throw new Error(`Landscape tablet is cut: ${spec.raw}`);
      if (hanging) {
        if ((box.top + box.height) / box.height < .6) throw new Error(`Too little of ${spec.raw} is visible`);
        continue;
      }
      if (box.top < 0) throw new Error(`Screen leaves the top of the card: ${spec.raw}`);
      if ((H - box.top) / box.height < .6) throw new Error(`Too little of ${spec.raw} is visible`);
    }
    svg.insertAdjacentHTML('beforeend', stream(ctx));
  },
};
