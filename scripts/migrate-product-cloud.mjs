import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const repo=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const work=path.join(repo,'.local','product-cloud');
const sourceContainer='keycloak-product-db-1';
const connectionFile=path.resolve(process.argv.find(a=>a.startsWith('--connection-file='))?.slice(18)||path.join(work,'connection.env'));
const line=fs.readFileSync(connectionFile,'utf8').split(/\r?\n/).find(l=>l.startsWith('DATABASE_URL='));
assert.ok(line?.slice(13).trim(),'DATABASE_URL is missing');
const uri=new URL(line.slice(13).trim().replace(/^['"]|['"]$/g,''));
assert.ok(['postgres:','postgresql:'].includes(uri.protocol),'Expected PostgreSQL connection string');
assert.ok(uri.hostname&&!uri.hostname.includes('-pooler.'),'Use a direct PostgreSQL endpoint');
const database=decodeURIComponent(uri.pathname.slice(1));
assert.ok(database&&uri.username&&uri.password,'Incomplete PostgreSQL connection string');
const pgBin=process.env.POSTGRES_BIN||'C:/Program Files/PostgreSQL/18/bin';
const environment={...process.env,PGHOST:uri.hostname,PGPORT:uri.port||'5432',PGDATABASE:database,
  PGUSER:decodeURIComponent(uri.username),PGPASSWORD:decodeURIComponent(uri.password),PGSSLMODE:'require',
  PGCHANNELBINDING:uri.searchParams.get('channel_binding')||'require',PGCONNECT_TIMEOUT:'20',PGCLIENTENCODING:'UTF8',PGOPTIONS:'-c timezone=UTC'};
function run(exe,args,options={}) {
  const r=spawnSync(exe,args,{encoding:'utf8',maxBuffer:128*1024*1024,...options});
  // No URI or password is included in arguments or error messages.
  assert.equal(r.status,0,path.basename(exe)+' failed: '+String(r.stderr||'').slice(0,700));
  return r.stdout;
}
const remote=query=>run(path.join(pgBin,'psql.exe'),['-X','-A','-t','-v','ON_ERROR_STOP=1','-c',query],{env:environment}).trim();
const source=query=>run('docker',['exec',sourceContainer,'psql','-U','postgres','-d','product_db','-X','-A','-t','-v','ON_ERROR_STOP=1','-c',query]).trim();
const tableQuery="SELECT coalesce(json_agg(tablename ORDER BY tablename),'[]') FROM pg_tables WHERE schemaname='public';";
const tables=JSON.parse(source(tableQuery));
assert.ok(tables.includes('products')&&tables.includes('product_variants'));
const preflight=JSON.parse(remote("SELECT json_build_object('database',current_database(),'version',current_setting('server_version_num')::int,'tables',(SELECT count(*) FROM pg_tables WHERE schemaname='public'),'enums',(SELECT count(*) FROM pg_type t JOIN pg_namespace n ON n.oid=t.typnamespace WHERE n.nspname='public' AND t.typtype='e'),'tls',(SELECT ssl FROM pg_stat_ssl WHERE pid=pg_backend_pid()));"));
// Neon terminates TLS at its proxy. Verify the client connection, rather than
// pg_stat_ssl on the backend connection behind that proxy.
const connectionInfo=remote('\\conninfo');
assert.ok(/SSL Connection\s*\|\s*true/.test(connectionInfo)||/SSL connection \(protocol: TLS/.test(connectionInfo),'Client TLS is required');
preflight.tls=true;
assert.equal(preflight.database,database);
assert.ok(preflight.version>=170000,'Target must be PostgreSQL 17 or newer');
fs.mkdirSync(work,{recursive:true});
const receiptFile=path.join(work,'migration-receipt.json');
const previous=fs.existsSync(receiptFile)?JSON.parse(fs.readFileSync(receiptFile,'utf8')):null;
function snapshot(queryFunction) {
  const result={};
  for(const table of tables) {
    assert.ok(/^[a-z_][a-z0-9_]*$/.test(table));
    const json=queryFunction(`SET TIME ZONE 'UTC'; SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text),'[]'::jsonb)::text FROM public."${table}" t;`).split(/\r?\n/).at(-1);
    result[table]={rows:JSON.parse(json).length,sha256:crypto.createHash('sha256').update(json).digest('hex')};
  }
  return result;
}
if(process.argv.includes('--verify')) {
  assert.ok(previous?.restored&&previous.host===uri.hostname&&previous.database===database,'No matching completed migration receipt');
  assert.deepEqual(JSON.parse(remote(tableQuery)),tables,'Target tables differ');
  assert.deepEqual(snapshot(remote),previous.tables,'Target data differs from exported snapshot');
  console.log(JSON.stringify({verified:true,host:uri.hostname,database,tables:previous.tables}));
  process.exit(0);
}
console.log(JSON.stringify({apply:process.argv.includes('--apply'),host:uri.hostname,database,preflight,sourceTables:tables}));
if(!process.argv.includes('--apply')) process.exit(0);
assert.equal(preflight.tables,0,'Target contains tables; refusing to overwrite existing data');
assert.equal(preflight.enums,0,'Target contains enum types; refusing to overwrite');
const before=snapshot(source);
const stamp=new Date().toISOString().replace(/[:.]/g,'-');
const dumpFile=path.join(work,`product-migration-${stamp}.dump`);
const dump=run('docker',['exec',sourceContainer,'pg_dump','-U','postgres','-d','product_db','--format=custom','--no-owner','--no-privileges'],{encoding:undefined});
fs.writeFileSync(dumpFile,dump,{flag:'wx',mode:0o600});
const list=run(path.join(pgBin,'pg_restore.exe'),['--list',dumpFile]);
// Managed Postgres already has public. Keep it and restore objects into it.
const filtered=list.split(/\r?\n/).map(l=>/^\d+;\s+\d+\s+\d+\s+SCHEMA - public\s/.test(l)?'; '+l:l).join('\n');
const listFile=path.join(work,`restore-${stamp}.list`); fs.writeFileSync(listFile,filtered);
run(path.join(pgBin,'pg_restore.exe'),['--dbname',database,'--no-owner','--no-privileges','--single-transaction','--exit-on-error','--use-list',listFile,dumpFile],{env:environment});
assert.deepEqual(JSON.parse(remote(tableQuery)),tables,'Target tables differ');
const after=snapshot(remote); assert.deepEqual(after,before,'Restored rows differ from source');
const receipt={at:new Date().toISOString(),host:uri.hostname,database,restored:true,tls:true,
  dumpFile,dumpSha256:crypto.createHash('sha256').update(dump).digest('hex'),tables:after};
fs.writeFileSync(receiptFile,JSON.stringify(receipt,null,2));
console.log(JSON.stringify({restored:true,host:uri.hostname,database,tables:after}));
