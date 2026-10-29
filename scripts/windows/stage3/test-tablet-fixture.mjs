import assert from 'node:assert/strict';
import {startFixture} from './fixture.mjs';
const fixture = await startFixture({port: 0, tablet: true, artwork: true});
try {
  const get = async path => (await fetch(fixture.baseUrl + path)).json();
  const manifest = await get('/manifest.json');
  assert.equal(manifest.catalogs.length, 8);
  assert.deepEqual(manifest.types, ['movie', 'series']);
  const catalog = await get('/catalog/series/tablet-1.json');
  assert.equal(catalog.metas.length, 40);
  assert.equal(new Set(catalog.metas.map(x => x.id)).size, 40);
  const poster = await fetch(catalog.metas[0].poster);
  assert.equal(poster.headers.get('content-type'), 'image/jpeg');
  assert.ok((await poster.arrayBuffer()).byteLength > 10000);
  const first = Buffer.from(await (await fetch(catalog.metas[0].poster)).arrayBuffer());
  const different = Buffer.from(await (await fetch(catalog.metas[1].poster)).arrayBuffer());
  assert.notDeepEqual(first, different);
  assert.match(poster.headers.get('cache-control'), /max-age=3600/);
  const otherShelf = await get('/catalog/series/tablet-3.json');
  assert.notEqual(catalog.metas[0].poster, otherShelf.metas[0].poster);
  assert.equal((await get('/stream/movie/tablet%3A0.json')).streams.length, 80);
  const {meta} = await get('/meta/series/tablet%3A0.json');
  assert.equal(meta.id, 'tablet:0');
  assert.equal(meta.videos.length, 48);
  assert.equal(new Set(meta.videos.map(x => x.season)).size, 4);
  assert.equal((await get('/meta/movie/tablet%3A0.json')).meta.videos, undefined);
  console.log('PASS: tablet fixture shelves, unique titles, movie and series episodes');
} finally { await fixture.close(); }
