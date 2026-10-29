import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const keys = ['frames', 'totalNs', 'maxNs', 'over16ms', 'over33ms'];
const workKeys = ['painters','scaleHits','scaleMisses','scaleNs','scaledBytes','imageCompletions','imageLatencyNs','imageMemoryHits','imageErrors','scaleWorstNs','imageLatencySamples','gcCollections','gcMs'];
const directionWorkKeys = ['sharedScaleHits','shelfEntries','shelfDisposals','cacheBudgetBytes'];
function number(value) { if (!Number.isSafeInteger(value) || value < 0) throw Error('Invalid numeric metric'); return value; }
function bucket(value) {
  const b = Object.fromEntries(keys.map(k => [k, number(value?.[k])]));
  if (b.over33ms > b.over16ms || b.over16ms > b.frames || (b.frames && b.maxNs > b.totalNs)) throw Error('Inconsistent frame metrics');
  return b;
}
/** Only allowlisted numeric metrics enter output. Input text/extra fields never do. */
export function summarize(text, {mode = 'all', fromMs = 0, toMs = Infinity, skipRecords = 0, probe} = {}) {
  if (!['all','active','idle','vertical','horizontal','mixed'].includes(mode)) throw Error('Invalid mode');
  if (!(fromMs >= 0 && toMs >= fromMs && Number.isInteger(skipRecords) && skipRecords >= 0)) throw Error('Invalid selection');
  const total = Object.fromEntries(keys.map(k => [k, 0]));
  const work = Object.fromEntries([...workKeys,...directionWorkKeys].map(k => [k, 0]));
  let selected = 0, excluded = 0, v1 = 0, v2 = 0, directionalRecords = 0, overlappingWindows = 0, previousEnd = -1;
  const probes = new Set();
  const lines = text.replace(/^\uFEFF/,'').split(/\r?\n/).filter(x => x.trim());
  for (let i = 0; i < lines.length; i++) {
    let record;
    try { record = JSON.parse(lines[i]); } catch { throw Error(`Invalid JSON at record ${i + 1}`); }
    if (i < skipRecords) { excluded++; continue; }
    let buckets;
    if (record.schema === 'nuvio-frame-v1') {
      if (mode !== 'all' || fromMs !== 0 || toMs !== Infinity) throw Error('v1 cannot select active time or exclude setup by elapsed time');
      buckets = [bucket(record)]; v1++;
    } else if (['nuvio-frame-v2','nuvio-frame-v3'].includes(record.schema)) {
      if (record.schema === 'nuvio-frame-v3') {
        const id = number(record.probe);
        if (probe !== undefined && id !== probe) { excluded++; continue; }
      } else if (probe !== undefined) throw Error('v2 has no probe IDs');
      const start = number(record.fromMs), end = number(record.toMs);
      if (end < start) throw Error('Invalid time window');
      if (start < fromMs || end > toMs) { excluded++; continue; }
      if (start < previousEnd) overlappingWindows++;
      previousEnd = Math.max(previousEnd, end);
      if (record.schema === 'nuvio-frame-v3') probes.add(record.probe);
      const directional = record.schema === 'nuvio-frame-v3';
      if (!directional && ['vertical','horizontal','mixed'].includes(mode)) throw Error('v2 cannot distinguish scroll direction');
      const groups = directional ? (mode === 'all' ? ['vertical','horizontal','mixed','idle'] : mode === 'active' ? ['vertical','horizontal','mixed'] : [mode]) : (mode === 'all' ? ['active','idle'] : [mode]);
      buckets = groups.map(k => bucket(record[k])); v2++;
      if (directional) directionalRecords++;
      if (directional) for (const k of directionWorkKeys) { const value = number(record.work?.[k]); work[k] = k === "cacheBudgetBytes" ? Math.max(work[k], value) : work[k] + value; }
      for (const k of workKeys) {
        const value = number(record.work?.[k]);
        work[k] = k === 'scaleWorstNs' ? Math.max(work[k], value) : work[k] + value;
      }
    } else throw Error('Unsupported metrics schema');
    selected++;
    for (const b of buckets) for (const k of keys) total[k] = k === 'maxNs' ? Math.max(total[k], b[k]) : total[k] + b[k];
  }
  if (probes.size > 1 && probe === undefined) throw Error('Multiple probes: select one with --probe N');
  if (!directionalRecords) for (const k of directionWorkKeys) delete work[k];
  return {schema:'nuvio-scroll-summary-v1', directionalRecords, overlappingWindows, probes:[...probes], mode, selectedRecords:selected, excludedRecords:excluded, legacyRecords:v1, windowedRecords:v2,
    ...total, averageMs:total.frames ? total.totalNs / total.frames / 1e6 : null,
    over16Percent:total.frames ? 100 * total.over16ms / total.frames : null,
    over33Percent:total.frames ? 100 * total.over33ms / total.frames : null,
    maximumMs:total.maxNs / 1e6, workWholeWindows:work};
}

export function listProbes(text) {
  const result = new Map();
  for (const line of text.trim().split(/\r?\n/)) {
    let record; try { record = JSON.parse(line); } catch { throw Error('Invalid JSON'); }
    if (record.schema !== 'nuvio-frame-v3') throw Error('This recording has no v3 probe IDs');
    const id = number(record.probe), start = number(record.fromMs), end = number(record.toMs);
    const entry = result.get(id) || {probe:id,firstMs:start,lastMs:end,records:0};
    entry.firstMs = Math.min(entry.firstMs,start); entry.lastMs = Math.max(entry.lastMs,end); entry.records++;
    result.set(id,entry);
  }
  return [...result.values()];
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const [input, ...args] = process.argv.slice(2);
    if (!input) throw Error('Provide a metrics JSONL file');
    if (args.length === 1 && args[0] === '--list-probes') { console.log(JSON.stringify(listProbes(fs.readFileSync(input,'utf8')),null,2)); process.exit(0); }
    const options = {};
    const flags = {'--mode':'mode','--from-ms':'fromMs','--to-ms':'toMs','--skip-records':'skipRecords','--probe':'probe'};
    for (let i = 0; i < args.length; i += 2) {
      const key = flags[args[i]];
      if (!key || args[i+1] === undefined) throw Error('Invalid option');
      options[key] = key === 'mode' ? args[i+1] : Number(args[i+1]);
    }
    console.log(JSON.stringify(summarize(fs.readFileSync(input,'utf8'), options), null, 2));
  } catch (error) {
    // Filesystem errors can contain user paths. Do not echo them or input records.
    console.error(error.code ? 'Cannot read metrics file' : error.message); process.exitCode = 1;
  }
}
