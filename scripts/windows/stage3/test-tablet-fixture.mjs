import assert from 'node:assert/strict';
import {startFixture} from './fixture.mjs';
const fixture = await startFixture({port: 0, tablet: true});
try {
  const get = async path => (await fetch(fixture.baseUrl + path)).json();
  const manifest = await get('/manifest.json');
  assert.equal(manifest.catalogs.length, 8);
  assert.deepEqual(manifest.types, ['movie', 'series']);
  const catalog = await get('/catalog/series/tablet-1.json');
  assert.equal(catalog.metas.length, 40);
  assert.equal(new Set(catalog.metas.map(x => x.id)).size, 40);
  const {meta} = await get('/meta/series/tablet%3A0.json');
  assert.equal(meta.id, 'tablet:0');
  assert.equal(meta.videos.length, 48);
  assert.equal(new Set(meta.videos.map(x => x.season)).size, 4);
  assert.equal((await get('/meta/movie/tablet%3A0.json')).meta.videos, undefined);
  console.log('PASS: tablet fixture shelves, unique titles, movie and series episodes');
} finally { await fixture.close(); }
