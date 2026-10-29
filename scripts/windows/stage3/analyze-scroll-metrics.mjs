import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const keys = ['frames', 'totalNs', 'maxNs', 'over16ms', 'over33ms'];
const workKeys = ['painters','scaleHits','scaleMisses','scaleNs','scaledBytes','imageCompletions','imageLatencyNs','imageMemoryHits','imageErrors','scaleWorstNs','imageLatencySamples','gcCollections','gcMs'];
function number(value) { if (!Number.isSafeInteger(value) || value < 0) throw Error('Invalid numeric metric'); return value; }
function bucket(value) {
  const b = Object.fromEntries(keys.map(k => [k, number(value?.[k])]));
  if (b.over33ms > b.over16ms || b.over16ms > b.frames || (b.frames && b.maxNs > b.totalNs)) throw Error('Inconsistent frame metrics');
  return b;
}
/** Only allowlisted numeric metrics enter output. Input text/extra fields never do. */
export function summarize(text, {mode = 'all', fromMs = 0, toMs = Infinity, skipRecords = 0} = {}) {
  if (!['all','active','idle'].includes(mode)) throw Error('Invalid mode');
  if (!(fromMs >= 0 && toMs >= fromMs && Number.isInteger(skipRecords) && skipRecords >= 0)) throw Error('Invalid selection');
  const total = Object.fromEntries(keys.map(k => [k, 0]));
  const work = Object.fromEntries(workKeys.map(k => [k, 0]));
  let selected = 0, excluded = 0, v1 = 0, v2 = 0;
  const lines = text.replace(/^\uFEFF/,'').split(/\r?\n/).filter(x => x.trim());
  for (let i = 0; i < lines.length; i++) {
    let record;
    try { record = JSON.parse(lines[i]); } catch { throw Error(`Invalid JSON at record ${i + 1}`); }
    if (i < skipRecords) { excluded++; continue; }
    let buckets;
    if (record.schema === 'nuvio-frame-v1') {
      if (mode !== 'all' || fromMs !== 0 || toMs !== Infinity) throw Error('v1 cannot select active time or exclude setup by elapsed time');
      buckets = [bucket(record)]; v1++;
    } else if (record.schema === 'nuvio-frame-v2') {
      const start = number(record.fromMs), end = number(record.toMs);
      if (end < start) throw Error('Invalid time window');
      if (start < fromMs || end > toMs) { excluded++; continue; }
      buckets = (mode === 'all' ? ['active','idle'] : [mode]).map(k => bucket(record[k])); v2++;
      for (const k of workKeys) {
        const value = number(record.work?.[k]);
        work[k] = k === 'scaleWorstNs' ? Math.max(work[k], value) : work[k] + value;
      }
    } else throw Error('Unsupported metrics schema');
    selected++;
    for (const b of buckets) for (const k of keys) total[k] = k === 'maxNs' ? Math.max(total[k], b[k]) : total[k] + b[k];
  }
  return {schema:'nuvio-scroll-summary-v1', mode, selectedRecords:selected, excludedRecords:excluded, legacyRecords:v1, windowedRecords:v2,
    ...total, averageMs:total.frames ? total.totalNs / total.frames / 1e6 : null,
    over16Percent:total.frames ? 100 * total.over16ms / total.frames : null,
    over33Percent:total.frames ? 100 * total.over33ms / total.frames : null,
    maximumMs:total.maxNs / 1e6, workWholeWindows:work};
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const [input, ...args] = process.argv.slice(2);
    if (!input) throw Error('Provide a metrics JSONL file');
    const options = {};
    const flags = {'--mode':'mode','--from-ms':'fromMs','--to-ms':'toMs','--skip-records':'skipRecords'};
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
