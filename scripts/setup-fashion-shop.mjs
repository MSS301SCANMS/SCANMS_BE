import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { randomBytes, createHash } from 'node:crypto';
import { spawnSync } from 'node:child_process';

const repo=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const readEnv=file=>fs.existsSync(file)?Object.fromEntries(fs.readFileSync(file,'utf8').split(/\r?\n/).filter(s=>/^\s*[^#=]+=/.test(s)).map(s=>{const i=s.indexOf('=');return[s.slice(0,i).trim(),s.slice(i+1).trim().replace(/^['"]|['"]$/g,'')]})):{};
const env={...readEnv(path.join(repo,'.env')),...readEnv(path.join(repo,'product-service','.env')),...process.env};
const root=env.KEYCLOAK_URL||'http://127.0.0.1:8180';
const userApi=env.USER_SERVICE_URL||'http://127.0.0.1:8081';
for(const value of [root,userApi])if(!['localhost','127.0.0.1'].includes(new URL(value).hostname))throw Error('Demo account setup is restricted to local services.');
const local=path.join(repo,'.local','fashion-seed-v1');
const accountFile=path.join(local,'shop-login.json');
const summary=JSON.parse(fs.readFileSync(path.join(local,'summary.json'),'utf8'));
const shop=summary.shops[0];
function id(key){const b=createHash('sha256').update(`scanms-fashion-demo-v1:${key}`).digest().subarray(0,16);b[6]=(b[6]&15)|80;b[8]=(b[8]&63)|128;const h=b.toString('hex');return`${h.slice(0,8)}-${h.slice(8,12)}-${h.slice(12,16)}-${h.slice(16,20)}-${h.slice(20)}`}
const dbUrl=new URL((env.DB_URL||`jdbc:postgresql://127.0.0.1:${env.PRODUCT_DB_PORT||5437}/${env.PRODUCT_DB_NAME||'product_db'}`).replace(/^jdbc:/,''));
if(!['localhost','127.0.0.1'].includes(dbUrl.hostname))throw Error('Demo database must be local.');
const pgBin=env.POSTGRES_BIN||'C:\\Program Files\\PostgreSQL\\18\\bin';
const pgArgs=['-h',dbUrl.hostname,'-p',dbUrl.port||'5432','-U',env.DB_USERNAME||env.POSTGRES_USER||'postgres','-d',decodeURIComponent(dbUrl.pathname.slice(1))];
function query(sql){const r=spawnSync(path.join(pgBin,'psql.exe'),[...pgArgs,'-At','-v','ON_ERROR_STOP=1'],{input:sql,encoding:'utf8',env:{...process.env,PGPASSWORD:env.DB_PASSWORD||env.POSTGRES_PASSWORD||'postgres',PGCONNECT_TIMEOUT:'5',PGCLIENTENCODING:'UTF8'}});if(r.status!==0)throw Error('Local database operation failed: '+r.stderr);return r.stdout.trim()}
const q=s=>"'"+s.replaceAll("'","''")+"'";
async function request(url,options={}){const r=await fetch(url,{...options,signal:AbortSignal.timeout(20000)});let data;try{data=await r.json()}catch{}if(!r.ok)throw Error(`Local service request failed (${r.status}): ${new URL(url).pathname}`);return data}
async function main(){
 // Existing local databases may still have Hibernate's original varchar(255).
 // Expanding to text preserves existing descriptions and supports the product editor.
 query("SET lock_timeout='5s'; ALTER TABLE products ALTER COLUMN description TYPE text;");
 const previousOwner=query(`SELECT owner_user_id FROM stores WHERE store_id=${q(shop.id)} AND approval_status='APPROVED';`);
 if(!previousOwner)throw Error('Seeded approved shop is missing. Run seed-fashion.mjs --apply first.');
 const admin=await request(root+'/realms/master/protocol/openid-connect/token',{method:'POST',body:new URLSearchParams({client_id:'admin-cli',grant_type:'password',username:env.KEYCLOAK_ADMIN||'admin',password:env.KEYCLOAK_ADMIN_PASSWORD||'admin'})});
 const headers={Authorization:'Bearer '+admin.access_token,'Content-Type':'application/json'};
 let account=fs.existsSync(accountFile)?JSON.parse(fs.readFileSync(accountFile,'utf8')):null;
 if(!account){
  const username='moc.basics.demo',email='moc.basics.demo@scanms.test';
  const existing=await request(root+'/admin/realms/scanms/users?exact=true&username='+encodeURIComponent(username),{headers});
  if(existing.length)throw Error('Demo username already exists without a local credential file. No existing account was changed.');
  const password='Shop!'+randomBytes(12).toString('base64url')+'9a';
  account={username,email,password,shop:shop.name,shopId:shop.id,loginUrl:'http://localhost:5173/login?role=shop',createdAt:new Date().toISOString()};
  fs.mkdirSync(local,{recursive:true});
  const response=await fetch(root+'/admin/realms/scanms/users',{method:'POST',headers,body:JSON.stringify({username,email,firstName:'Moc',lastName:'Basics',enabled:true,emailVerified:true,requiredActions:[],credentials:[{type:'password',value:password,temporary:false}]})});
  if(!response.ok)throw Error('Demo account creation failed ('+response.status+').');
  account.identityId=response.headers.get('location')?.split('/').pop();
  fs.writeFileSync(accountFile,JSON.stringify(account,null,2),{mode:0o600});
 }
 if(!account.identityId)throw Error('Missing demo identity ID; local credential file must be inspected.');
 const identity=await request(root+'/admin/realms/scanms/users/'+account.identityId,{headers});
 if(identity.username!=='moc.basics.demo'||identity.email!=='moc.basics.demo@scanms.test')throw Error('Credential file does not refer to the dedicated demo account. No identity was changed.');
 await request(root+'/admin/realms/scanms/users/'+account.identityId,{method:'PUT',headers,body:JSON.stringify({emailVerified:true,requiredActions:[]})});
 const role=await request(root+'/admin/realms/scanms/roles/SHOP_OWNER',{headers});
 const mapped=await fetch(root+'/admin/realms/scanms/users/'+account.identityId+'/role-mappings/realm',{method:'POST',headers,body:JSON.stringify([role])});
 if(!mapped.ok)throw Error('Assigning SHOP_OWNER to the new demo account failed.');
 const login=await request(userApi+'/api/v1/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email:account.email,password:account.password})});
 const token=login.result?.access_token;
 if(!token)throw Error('Demo account login returned no access token.');
 const me=(await request(userApi+'/api/v1/users/me',{headers:{Authorization:'Bearer '+token}})).result;
 if(!me?.userId||me.identitySubject!==account.identityId||!me.roles?.includes('SHOP_OWNER'))throw Error('Demo identity could not be synchronized as an active shop owner.');
 if(previousOwner!==id('owner:moc-basics')&&previousOwner!==me.userId)throw Error('The demo shop owner was changed outside this script. No owner was overwritten.');
 const ownerBackup=path.join(local,'shop-owner-before.json');
 if(!fs.existsSync(ownerBackup))fs.writeFileSync(ownerBackup,JSON.stringify({shopId:shop.id,ownerUserId:previousOwner},null,2));
 query(`BEGIN; UPDATE stores SET owner_user_id=${q(me.userId)},updated_at=now(),version=coalesce(version,0)+1 WHERE store_id=${q(shop.id)} AND owner_user_id IN (${q(previousOwner)},${q(me.userId)}); COMMIT;`);
 account.userId=me.userId;
 fs.writeFileSync(accountFile,JSON.stringify(account,null,2),{mode:0o600});
 console.log('Demo SHOP_OWNER account ready for '+shop.name+'. Login credentials saved to be/.local/fashion-seed-v1/shop-login.json.');
 console.log('Only the seeded Moc Basics shop was linked. Existing personal accounts were not changed.');
}
main().catch(e=>{console.error(e.message);process.exitCode=1});
