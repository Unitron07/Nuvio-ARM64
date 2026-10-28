import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {startFixture} from './fixture.mjs';

test('local addon, plugin, protected redirects, queries, ranges, errors, and log privacy', async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'nuvio-stage3-'));
  const media = path.join(dir, 'fixture.bin');
  const log = path.join(dir, 'requests.jsonl');
  fs.writeFileSync(media, '0123456789');
  const fixture = await startFixture({port: 0, media, log});
  try {
    const base = fixture.baseUrl;
    assert.equal((await (await fetch(`${base}/manifest.json`)).json()).id, 'org.nuvio.stage3.fixture');
    assert.equal((await (await fetch(`${base}/catalog/movie/stage3.json`)).json()).metas.length, 1);
    assert.equal((await (await fetch(`${base}/meta/movie/stage3%3Avideo.json`)).json()).meta.id, 'stage3:video');
    const pluginManifestUrl = `${base}/plugins/manifest.json`;
    const pluginManifest = await (await fetch(pluginManifestUrl)).json();
    assert.equal(pluginManifest.scrapers[0].filename, 'scraper.js');
    const scraper = await (await fetch(new URL(pluginManifest.scrapers[0].filename, pluginManifestUrl))).text();
    assert.ok(scraper.includes(`${base}/stream/`));
    const {streams} = await (await fetch(`${base}/stream/movie/stage3%3Avideo.json`)).json();
    const stream = streams[0];
    assert.equal((await fetch(stream.url)).status, 401);
    const headers = stream.behaviorHints.proxyHeaders.request;
    assert.equal(await (await fetch(stream.url, {headers})).text(), '0123456789');
    const partial = await fetch(stream.url, {headers: {...headers, Range: 'bytes=2-5'}});
    assert.equal(partial.status, 206); assert.equal(partial.headers.get('content-range'), 'bytes 2-5/10');
    assert.equal(await partial.text(), '2345');
    assert.equal(await (await fetch(stream.url, {headers: {...headers, Range: 'bytes=-2'}})).text(), '89');
    assert.equal((await fetch(stream.url, {headers: {...headers, Range: 'bytes=99-'}})).status, 416);
    assert.equal((await fetch(`${base}/error`)).status, 503);
    await assert.rejects((await fetch(`${base}/malformed/manifest.json`)).json());
    await fetch(`${base}/unknown?token=must-never-appear`, {headers: {Authorization: 'Bearer must-never-appear'}});
    const records = fs.readFileSync(log, 'utf8');
    assert.ok(!records.includes('must-never-appear'));
    assert.ok(!records.includes('fixture-only'));
    assert.ok(records.includes('"fixtureExtraHeader":true'));
  } finally { await fixture.close(); fs.rmSync(dir, {recursive: true}); }
});
