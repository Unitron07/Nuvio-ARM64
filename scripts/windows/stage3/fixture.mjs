import http from 'node:http';
import https from 'node:https';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root = path.join(path.dirname(fileURLToPath(import.meta.url)), 'fixtures', 'stage3');
export async function startFixture({port = 19876, media, log, cert, key, httpsPort = 19877, tablet = false} = {}) {
  if (Boolean(cert) !== Boolean(key)) throw new Error('Supply both TLS certificate and key');
  const mediaSize = media ? fs.statSync(media).size : 0;
  let baseUrl, tlsUrl;
  const servers = [];
  const render = (file, base) => fs.readFileSync(path.join(root, file), 'utf8').replaceAll('BASE_URL', base);
  const handler = (req, res) => {
    const base = req.socket.encrypted ? tlsUrl : baseUrl;
    const url = new URL(req.url, base);
    const route = url.pathname;
    // Logs deliberately contain no URLs, queries, header values, bodies, or filenames.
    res.on('finish', () => {
      if (log) fs.appendFileSync(log, JSON.stringify({time: new Date().toISOString(),
        method: req.method, route: ['/manifest.json', '/plugins/manifest.json', '/plugins/scraper.js', '/scraper.js', '/video', '/redirect', '/error', '/malformed/manifest.json'].includes(route)
          ? route : route.startsWith('/catalog/') ? 'catalog' : route.startsWith('/meta/') ? 'meta' : route.startsWith('/stream/') ? 'stream' : 'unknown',
        status: res.statusCode, tls: Boolean(req.socket.encrypted), range: Boolean(req.headers.range),
        fixtureAuthorization: req.headers.authorization === 'Bearer fixture-only',
        fixtureUserAgent: req.headers['user-agent'] === 'Nuvio-Stage3-Fixture',
        fixtureReferer: req.headers.referer === `${base}/`,
        fixtureExtraHeader: req.headers['x-fixture'] === 'comma,backslash\\value'}) + '\n');
    });
    const send = (status, data, type = 'application/json') => {
      res.writeHead(status, {'Content-Type': type, 'Cache-Control': 'no-store'});
      res.end(data);
    };
    if (tablet && route === '/manifest.json') {
      const manifest = JSON.parse(render('manifest.json', base));
      manifest.types = ['movie', 'series'];
      manifest.catalogs = Array.from({length: 8}, (_, i) => ({type: i % 2 ? 'series' : 'movie', id: 'tablet-' + i, name: 'Tablet shelf ' + i}));
      return send(200, JSON.stringify(manifest));
    }
    if (tablet && route.startsWith('/catalog/')) {
      const type = route.split('/')[2];
      return send(200, JSON.stringify({metas: Array.from({length: 40}, (_, i) => ({id: 'tablet:' + i, type, name: 'Tablet title ' + i, description: 'Synthetic scrolling fixture'}))}));
    }
    if (tablet && route.startsWith('/meta/')) {
      const type = route.split('/')[2];
      const id = decodeURIComponent(route.split('/')[3].replace(/\.json$/, ''));
      return send(200, JSON.stringify({meta: {id, type, name: 'Tablet detail fixture', description: 'Synthetic description. '.repeat(80),
        videos: type === 'series' ? Array.from({length: 48}, (_, i) => ({id: id + ':' + (i + 1), title: 'Episode ' + (i + 1), season: 1 + Math.floor(i / 12), episode: 1 + i % 12, released: '2026-01-01T00:00:00Z'})) : undefined}}));
    }
    if (route === '/manifest.json') return send(200, render('manifest.json', base));
    if (route.startsWith('/catalog/')) return send(200, render('catalog.json', base));
    if (route.startsWith('/meta/')) return send(200, render('meta.json', base));
    if (route.startsWith('/stream/')) {
      const response = JSON.parse(render('stream.json', base));
      if (tlsUrl && base === baseUrl) {
        const stream = JSON.parse(render('stream.json', tlsUrl)).streams[0];
        stream.name = 'Stage 3 HTTPS redirect + headers';
        response.streams.splice(2, 0, stream);
      }
      return send(200, JSON.stringify(response));
    }
    if (route === '/plugins/manifest.json') return send(200, render('plugin-manifest.json', base));
    if (route === '/plugins/scraper.js' || route === '/scraper.js') return send(200, render('scraper.js', base), 'text/javascript');
    if (route === '/error') return send(503, '{"error":"controlled fixture failure"}');
    if (route === '/malformed/manifest.json') return send(200, '{malformed-fixture');
    if (route === '/redirect') {
      res.writeHead(302, {Location: `${base}/video${url.search}`});
      return res.end();
    }
    if (route !== '/video') return send(404, '{}');
    if (req.headers.authorization !== 'Bearer fixture-only' ||
        req.headers['user-agent'] !== 'Nuvio-Stage3-Fixture' || req.headers.referer !== `${base}/` ||
        req.headers['x-fixture'] !== 'comma,backslash\\value' ||
        url.searchParams.get('fixture') !== 'one+two' || url.searchParams.get('session') !== 'local') {
      return send(401, '{"error":"fixture URL or headers missing"}');
    }
    if (!mediaSize) return send(503, '{"error":"start fixture with --media authorized-file"}');
    let start = 0, end = mediaSize - 1;
    if (req.headers.range) {
      const match = /^bytes=(\d*)-(\d*)$/.exec(req.headers.range);
      if (!match || (!match[1] && !match[2])) {
        res.writeHead(416, {'Content-Range': `bytes */${mediaSize}`}); return res.end();
      }
      if (!match[1]) start = Math.max(0, mediaSize - Number(match[2]));
      else { start = Number(match[1]); if (match[2]) end = Math.min(Number(match[2]), end); }
      if (start > end || start >= mediaSize || !Number.isSafeInteger(start) || !Number.isSafeInteger(end)) {
        res.writeHead(416, {'Content-Range': `bytes */${mediaSize}`}); return res.end();
      }
    }
    const headers = {'Accept-Ranges': 'bytes', 'Content-Length': end - start + 1,
      'Content-Type': path.extname(media).toLowerCase() === '.avi' ? 'video/x-msvideo' : 'application/octet-stream'};
    if (req.headers.range) headers['Content-Range'] = `bytes ${start}-${end}/${mediaSize}`;
    res.writeHead(req.headers.range ? 206 : 200, headers);
    if (req.method === 'HEAD') return res.end();
    const stream = fs.createReadStream(media, {start, end});
    stream.on('error', () => res.destroy());
    res.on('close', () => stream.destroy());
    stream.pipe(res);
  };
  try {
    const server = http.createServer(handler);
    await new Promise((resolve, reject) => { server.once('error', reject); server.listen(port, '127.0.0.1', resolve); });
    servers.push(server);
    baseUrl = `http://127.0.0.1:${server.address().port}`;
    if (cert) {
      const tls = https.createServer({cert: fs.readFileSync(cert), key: fs.readFileSync(key)}, handler);
      await new Promise((resolve, reject) => { tls.once('error', reject); tls.listen(httpsPort, '127.0.0.1', resolve); });
      servers.push(tls);
      tlsUrl = `https://127.0.0.1:${tls.address().port}`;
    }
    return {baseUrl, tlsUrl, close: async () => {
      await Promise.all(servers.map(server => new Promise(resolve => { server.close(resolve); server.closeAllConnections(); })));
    }};
  } catch (error) {
    servers.forEach(server => { server.close(); server.closeAllConnections(); });
    throw error;
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const options = {};
  for (let i = 2; i < process.argv.length; i += 2) {
    const name = process.argv[i].slice(2);
    if (!['port', 'media', 'log', 'cert', 'key', 'https-port', 'tablet'].includes(name) || !process.argv[i + 1]) throw new Error('Invalid fixture option');
    options[name === 'https-port' ? 'httpsPort' : name] = ['port', 'https-port'].includes(name) ? Number(process.argv[i + 1]) : process.argv[i + 1];
  }
  const fixture = await startFixture(options);
  console.log(`Addon: ${fixture.baseUrl}/manifest.json`);
  console.log(`Plugin repository: ${fixture.baseUrl}/plugins/manifest.json`);
  if (fixture.tlsUrl) console.log(`HTTPS addon: ${fixture.tlsUrl}/manifest.json`);
  for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, async () => { await fixture.close(); process.exit(0); });
}
