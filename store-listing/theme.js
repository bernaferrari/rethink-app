// Keepout: "the aurora". A dark plum field lit by soft coral, rose and magenta light: coral rising
// from the lower left, magenta high on the right, a warm glow behind the devices, laid out across the
// whole strip so every seam continues. Nothing is random, and nothing sits between the reader and the app.
import {panoramaGradient, panoramaX} from './studio/shapes.js';


function art(ctx) {
  const {W, H, slides} = ctx;
  const unit = Math.max(W, H);
  const landscape = W > H;
  let defs = panoramaGradient(ctx, 'field', ['#170910', '#1d0b14', '#180a11', '#200c16', '#170910', '#1e0b14']);
  let body = `<rect width="${W}" height="${H}" fill="url(#field)"/>`;
  // The aurora. On the Play strip it is laid out once across every card, so each seam continues into
  // the next; a README image (always seen alone) gets the whole aurora to itself.
  const lights = [[.15, .95, .55, '#ff5d73', .42], [.85, .2, .5, '#b0306a', .38], [.55, .7, .45, '#ff8a5c', .22],
                  [.95, .92, .32, '#e2456e', .3], [.05, .1, .4, '#5a1a5a', .5]];
  const cards = ctx.kind === 'readme' ? [null] : slides.map((_, k) => k);
  cards.forEach(k => {
    lights.forEach(([x, y, r, colour, o], i) => {
      const cx = k === null ? W * x : panoramaX(ctx, k + x);
      defs += `<radialGradient id="aurora${k}_${i}" gradientUnits="userSpaceOnUse" cx="${cx}" cy="${H * y}" r="${unit * r}"><stop offset="0" stop-color="${colour}" stop-opacity="${o}"/><stop offset="1" stop-color="${colour}" stop-opacity="0"/></radialGradient>`;
      body += `<rect width="${W}" height="${H}" fill="url(#aurora${k}_${i})"/>`;
    });
  });
  return `<defs>${defs}</defs>${body}`;
}

export default {
  art,

  compose({main, slide}) {
    // A card without a headline (the README trio, under its own section heading) has no copy.
    const header = main.querySelector('header');
    if (header && !header.querySelector('h1')?.textContent.trim()) header.remove();
    // The README's first image is the name card: icon, name and line, above the app.
    if (slide.identity) {
      header?.remove();
      const id = document.createElement('div');
      id.className = 'identity';
      id.dataset.checkBounds = '';
      id.innerHTML = '<img src="../brand/icon.png" alt=""><span>Keepout</span><small>Every app, <em>on your terms.</em></small>';
      main.append(id);
    }
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
  // screen stays in view. Landscape tablets stay whole.
  decorate(ctx) {
    const {W, H, slide, devices, main} = ctx;
    // Captions mark their key words like the headlines: *words* turn coral.
    for (const span of main.querySelectorAll('.device-label span')) {
      if (span.querySelector('em')) continue;
      span.innerHTML = span.textContent.replace(/[&<>]/g, c => `&#${c.charCodeAt(0)};`).replace(/\*([^*]+)\*/g, '<em>$1</em>');
    }
    if (slide.layout === 'compare') {
      // Each name sits just under its screen.
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
  },
};
