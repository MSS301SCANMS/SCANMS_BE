import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';
import assert from 'node:assert/strict';

const repo=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const work=path.join(repo,'.local','product-cloud');
fs.mkdirSync(work,{recursive:true});
function docker(args,binary=false) {
  const r=spawnSync('docker',['exec','keycloak-product-db-1',...args],{encoding:binary?undefined:'utf8',maxBuffer:128*1024*1024});
  assert.equal(r.status,0,'Product export failed; source database was not changed'); return r.stdout;
}
const counts=JSON.parse(docker(['psql','-U','postgres','-d','product_db','-X','-A','-t','-c',"SELECT json_build_object('products',(SELECT count(*) FROM products),'variants',(SELECT count(*) FROM product_variants),'images',(SELECT count(*) FROM product_images),'stores',(SELECT count(*) FROM stores));"]).trim());
const stamp=new Date().toISOString().replace(/[:.]/g,'-');
const dumpPath=path.join(work,`product-${stamp}.dump`);
const dump=docker(['pg_dump','-U','postgres','-d','product_db','--format=custom','--no-owner','--no-privileges'],true);
fs.writeFileSync(dumpPath,dump,{flag:'wx',mode:0o600});
const pgBin=process.env.POSTGRES_BIN||'C:/Program Files/PostgreSQL/18/bin';
const listing=spawnSync(path.join(pgBin,'pg_restore.exe'),['--list',dumpPath],{encoding:'utf8'});
assert.equal(listing.status,0,'Export created but archive verification failed');
for(const table of ['products','product_variants','product_images','stores'])assert.ok(listing.stdout.includes('TABLE DATA public '+table+' '),'Archive missing '+table);
const connectionFile=path.join(work,'connection.env');
if(!fs.existsSync(connectionFile))fs.writeFileSync(connectionFile,'# Paste the direct PostgreSQL connection string from Neon Connect below.\nDATABASE_URL=\n',{flag:'wx',mode:0o600});
const receipt={at:new Date().toISOString(),source:'Local Product DB',counts,dumpPath,bytes:dump.length,sha256:crypto.createHash('sha256').update(dump).digest('hex'),archiveVerified:true,connectionFile,uploaded:false};
fs.writeFileSync(path.join(work,'export-receipt.json'),JSON.stringify(receipt,null,2));
console.log(JSON.stringify(receipt,null,2));
