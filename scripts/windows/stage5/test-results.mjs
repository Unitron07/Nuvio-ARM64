import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {sanitize, summarize, collect} from './collect-results.mjs';
const sample = {schema:'nuvio-playback-v1', session:1, architecture:'arm64', phase:'sample',
  requested:'auto', current:'d3d11va', codec:'h264', decoder:'h264', pixelFormat:'d3d11',
  'video-params/w':1920, 'video-params/h':1080, pause:0, 'core-idle':0, 'time-pos':1, elapsedSeconds:1};
const later = {...sample, 'time-pos':2, elapsedSeconds:2};
assert.equal(summarize([sample,later]).sessions[0].progressingHardwareIntervals,1);
for (const altered of [{current:'auto'},{current:'no'}, {current:'unavailable'}, {pause:1}, {'core-idle':1},
  {'video-params/w':null}, {'time-pos':100}, {'time-pos':null}, {elapsedSeconds:0}]) {
  assert.equal(summarize([sample,{...later,...altered}]).sessions[0].progressingHardwareIntervals,0);
}
const secret = {...sample, codec:'https://private/token', current:'Bearer secret', title:'private title',
  errorCode:'private', avsync:Infinity, elapsedSeconds:'secret'};
const safe = sanitize(secret);
assert.equal(safe.current,'other'); assert.equal(safe.avsync,null);
assert(!JSON.stringify(safe).match(/private|secret|title/));
assert.throws(() => sanitize({...sample,session:'secret'}));
assert.equal(summarize([{...sample,current:'no'}]).sessions[0].softwareSamples,1);
assert.equal(summarize([sample,{...sample,phase:'destroy-returned'}]).sessions[0].destroyReturned,true);
const tmp=fs.mkdtempSync(path.join(os.tmpdir(),'nuvio-stage5-'));
try {
  fs.mkdirSync(path.join(tmp,'private')); fs.mkdirSync(path.join(tmp,'profile'));
  const stem='20261030T123456000Z';
  fs.writeFileSync(path.join(tmp,`${stem}.session.json`),JSON.stringify({sourceCommit:'a'.repeat(40),osArchitecture:'Arm64',exitCode:0,privateCrashReportCount:0,secret:'private'}));
  fs.writeFileSync(path.join(tmp,'private',`${stem}.playback.jsonl`),[sample,later,secret].map(JSON.stringify).join('\n'));
  fs.writeFileSync(path.join(tmp,'private','raw.log'),'secret'); fs.writeFileSync(path.join(tmp,'profile','credentials'),'secret');
  const output=fs.readFileSync(collect(tmp),'utf8');
  assert(!output.includes('secret')); assert(!output.includes('https:'));
  assert.equal(JSON.parse(output).runs[0].sessions[0].progressingHardwareIntervals,1);
} finally {
  assert.equal(path.dirname(path.resolve(tmp)), path.resolve(os.tmpdir()));
  assert(path.basename(tmp).startsWith('nuvio-stage5-'));
  fs.rmSync(tmp,{recursive:true,force:true});
}
console.log('PASS: sustained decoder evidence, missing values, seek/paused rejection and allowlisted collection');
