// Keepout: "the aurora". A dark plum field lit by soft coral, rose and magenta light: coral rising
// from the lower left, magenta high on the right, a warm glow behind the devices, laid out across the
// whole strip so every seam continues. Nothing is random, and nothing sits between the reader and the app.
import {panoramaGradient, panoramaLights} from './studio/shapes.js';


function art(ctx) {
  const {W, H} = ctx;
  const defs = panoramaGradient(ctx, 'field', ['#170910', '#1d0b14', '#180a11', '#200c16', '#170910', '#1e0b14']);
  let body = `<rect width="${W}" height="${H}" fill="url(#field)"/>`;
  // The aurora. On the Play strip it is laid out once across every card, so each seam continues into
  // the next; a README image (always seen alone) gets the whole aurora to itself.
  const lights = [[.15, .95, .55, '#ff5d73', .42], [.85, .2, .5, '#b0306a', .38], [.55, .7, .45, '#ff8a5c', .22],
                  [.95, .92, .32, '#e2456e', .3], [.05, .1, .4, '#5a1a5a', .5]].map(([x, y, r, color, opacity]) => ({x, y, r, color, opacity}));
  body += panoramaLights(ctx, lights, {id: 'aurora', alone: ctx.kind === 'readme'});
  return `<defs>${defs}</defs>${body}`;
}

export default {
  art,
  continuous: true,

  compose({main}) {
    // A card without a headline (the README trio, under its own section heading) has no copy.
    const header = main.querySelector('header');
    if (header && !header.querySelector('h1')?.textContent.trim()) header.remove();
  },
};
