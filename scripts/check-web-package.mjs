import assert from 'node:assert/strict';
import { calculateCollageLayout } from '@wildberries/gorberry-collage';

// Consumer contract: loading the packed package must resolve the generated Kotlin module.
const images = [
  { id: 'landscape', width: 1200, height: 800 },
  { id: 42, width: 800, height: 1200 },
  { width: 1000, height: 1000 },
];
const layout = calculateCollageLayout({ images, width: 360 });
assert.equal(layout.width, 360);
assert.ok(Number.isFinite(layout.height) && layout.height > 0);
assert.equal(layout.tiles.length, images.length);
for (const tile of layout.tiles) {
  assert.ok(Number.isFinite(tile.box.x) && Number.isFinite(tile.box.y));
  assert.ok(tile.box.width > 0 && tile.box.height > 0);
  assert.ok(tile.box.x >= -0.001 && tile.box.x + tile.box.width <= 360.001);
}
assert.throws(() => calculateCollageLayout({ images, width: 0 }), RangeError);
assert.throws(() => calculateCollageLayout({ images: [], width: 360 }), RangeError);
console.log('Packed web library: import, layout geometry and input validation passed.');
