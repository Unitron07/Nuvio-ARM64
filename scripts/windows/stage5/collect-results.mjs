import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const hw = new Set(['no', 'auto', 'd3d11va', 'd3d11va-copy', 'dxva2', 'dxva2-copy', 'nvdec', 'nvdec-copy', 'vulkan', 'vulkan-copy']);
const codecs = new Set(['h264', 'hevc', 'av1', 'vp9', 'vp8', 'mpeg4', 'mpeg2video', 'rawvideo']);
const decoders = new Set([...codecs, 'libdav1d', 'libaom-av1', 'libvpx-vp9', 'h264_d3d11va', 'hevc_d3d11va', 'av1_d3d11va']);
const pixels = new Set(['d3d11', 'd3d11va_vld', 'dxva2_vld', 'vulkan', 'nv12', 'p010', 'p010le', 'yuv420p', 'yuv420p10le', 'yuv422p', 'yuv444p', 'bgra', 'bgr0', 'rgb24']);
const phases = new Set(['initialized', 'sample', 'playback-error', 'destroy-returned']);
const fields = ['elapsedSeconds', 'errorCode', 'video-params/w', 'video-params/h', 'container-fps', 'estimated-vf-fps', 'time-pos', 'pause', 'core-idle', 'frame-drop-count', 'decoder-frame-drop-count', 'avsync', 'demuxer-cache-duration'];
const finite = value => typeof value === 'number' && Number.isFinite(value) ? value : null;
const category = (value, allowed) => allowed.has(value) ? value : value === 'unavailable' ? 'unavailable' : 'other';
export function sanitize(record) {
  if (record.schema !== 'nuvio-playback-v1' || !phases.has(record.phase) ||
      !Number.isSafeInteger(record.session) || record.session < 1) throw new Error('Invalid playback record');
  const safe = {schema: 'nuvio-playback-v1', session: record.session,
    architecture: category(record.architecture, new Set(['arm64', 'x64'])), phase: record.phase,
    requested: category(record.requested, hw), current: category(record.current, hw),
    codec: category(record.codec, codecs), decoder: category(record.decoder, decoders), pixelFormat: category(record.pixelFormat, pixels)};
  for (const key of fields) safe[key] = finite(record[key]);
  return safe;
}
export function summarize(records, metrics = []) {
  const sessions = new Map();
  for (const raw of records) {
    const r = sanitize(raw);
    const s = sessions.get(r.session) ?? {id: r.session, initialized: false, destroyReturned: false,
      errors: 0, samples: 0, progressingHardwareIntervals: 0, softwareSamples: 0, previous: null,
      modes: new Set(), codecs: new Set(), requestedModes: new Set(), video: {}};
    s.initialized ||= r.phase === 'initialized'; s.destroyReturned ||= r.phase === 'destroy-returned';
    s.errors += Number(r.phase === 'playback-error');
    if (r.phase === 'sample') {
      s.samples++; s.modes.add(r.current); s.codecs.add(r.codec);
      s.requestedModes.add(r.requested);
      for (const key of ['video-params/w', 'video-params/h', 'container-fps', 'estimated-vf-fps',
          'frame-drop-count', 'decoder-frame-drop-count', 'avsync', 'demuxer-cache-duration']) {
        const v = r[key];
        if (v === null) continue;
        const old = s.video[key];
        s.video[key] = old ? {...old, last:v, min:Math.min(old.min,v), max:Math.max(old.max,v)} : {first:v,last:v,min:v,max:v};
      }
      s.softwareSamples += Number(r.current === 'no');
      const p = s.previous;
      // A seek-sized jump, paused/buffering state, or missing decoded parameters is not playback evidence.
      if (p && hw.has(r.current) && !['no', 'auto'].includes(r.current) && p.current === r.current &&
          p.pause === 0 && r.pause === 0 && p['core-idle'] === 0 && r['core-idle'] === 0 &&
          p['video-params/w'] > 0 && r['video-params/w'] > 0 && p['video-params/h'] > 0 && r['video-params/h'] > 0 &&
          p['time-pos'] !== null && r['time-pos'] !== null &&
          r['time-pos'] > p['time-pos'] && r.elapsedSeconds > p.elapsedSeconds &&
          r['time-pos'] - p['time-pos'] <= (r.elapsedSeconds - p.elapsedSeconds) * 1.5 + 0.25) {
        s.progressingHardwareIntervals++;
      }
      s.previous = r;
    }
    sessions.set(r.session, s);
  }
  const stats = key => {
    const values = metrics.map(m => finite(m[key])).filter(v => v !== null);
    return values.length ? {count: values.length, mean: values.reduce((a,b) => a+b,0)/values.length,
      peak: values.reduce((a,b) => Math.max(a,b), -Infinity), first: values[0], last: values.at(-1)} : null;
  };
  return {schema: 'nuvio-stage5-summary-v1', sessions: [...sessions.values()].map(({previous, modes, codecs, requestedModes, ...s}) =>
    ({...s, modes: [...modes], codecs: [...codecs], requestedModes: [...requestedModes]})), metrics: Object.fromEntries(
      ['cpuPercent', 'workingSetBytes', 'privateBytes', 'handles', 'threads', 'processCount'].map(k => [k, stats(k)])),
    processScopes: [...new Set(metrics.map(m => category(m.scope, new Set(['process-tree', 'launcher-only']))))],
    limitations: 'Runtime-reported decoder plus progressing playback; not a per-frame GPU trace. Destroy-returned is libmpv return only. CPU is best-effort process-tree utilization normalized to logical processors; check processScopes for launcher-only fallback. Exited/inaccessible workers may be missed; summed working sets double-count shared pages. Private bytes include managed and native allocations; growth is not a leak diagnosis. GPU, GC and energy require separate measurement.'};
}
function jsonLines(file) {
  if (fs.statSync(file).size > 32 * 1024 * 1024) throw new Error('Oversized diagnostic file');
  return fs.readFileSync(file, 'utf8').replace(/^\uFEFF/, '').split(/\r?\n/).filter(Boolean).map(line => JSON.parse(line));
}
export function collect(run) {
  const results = [];
  const privateDir = path.join(run, 'private');
  for (const file of fs.readdirSync(run).filter(f => /^\d{8}T\d{9}Z\.session\.json$/.test(f))) {
    const manifest = JSON.parse(fs.readFileSync(path.join(run, file), 'utf8').replace(/^\uFEFF/, ''));
    if (!/^[a-f0-9]{40}$/.test(manifest.sourceCommit) || manifest.osArchitecture !== 'Arm64') throw new Error('Invalid provenance');
    const stem = file.replace('.session.json', '');
    const playback = path.join(privateDir, `${stem}.playback.jsonl`);
    const metrics = path.join(privateDir, `${stem}.metrics.jsonl`);
    results.push({sourceCommit: manifest.sourceCommit, architecture: 'arm64', exitCode: finite(manifest.exitCode),
      crashReports: finite(manifest.privateCrashReportCount),
      ...summarize(fs.existsSync(playback) ? jsonLines(playback) : [], fs.existsSync(metrics) ? jsonLines(metrics) : [])});
  }
  const target = path.join(run, 'stage5-safe-results.json');
  fs.writeFileSync(target, JSON.stringify({schema:'nuvio-stage5-results-v1', runs:results}, null, 2));
  return target;
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  if (!process.argv[2]) throw new Error('Usage: node collect-results.mjs RUN_DIRECTORY');
  console.log(`Review before sharing: ${collect(path.resolve(process.argv[2]))}`);
}
