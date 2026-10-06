import http from 'node:http';
import { createRequire } from 'node:module';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { readFileSync } from 'node:fs';
import { identity, equalSecret } from './identity.mjs';
import { CommerceSource } from './source.mjs';
import { ContractError, eligible } from './rules.mjs';
import { paymentDetails } from './payment-details.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const env = file => Object.fromEntries(readFileSync(file,'utf8').split(/\r?\n/).filter(x=>/^[A-Z_\d]+=/.test(x)).map(x=>{
  const at=x.indexOf('='); return [x.slice(0,at),x.slice(at+1).trim().replace(/^(['"])(.*)\1$/,'$2')];
}));
const settings = { ...env(resolve(here,'../.env')), ...process.env };
const legacyPath = settings.COMMERCE_BACKEND_PATH || resolve(here,'../legacy-commerce');
// Prisma is generated from the commerce schema. Never regenerate through its shared node_modules junction.
const require = createRequire(resolve(legacyPath,'package.json'));
const { PrismaClient } = require('@prisma/client');
const { PrismaPg } = require('@prisma/adapter-pg');
const databaseUrl = settings.COMMERCE_DATABASE_URL;
if (!databaseUrl) throw new Error('COMMERCE_DATABASE_URL is required; do not infer or overwrite a database');
const db = new PrismaClient({ adapter: new PrismaPg({ connectionString: databaseUrl }) });
const source = new CommerceSource(db, Number(settings.FINANCE_RETURN_WINDOW_DAYS || 14));
const port = Number(settings.FINANCE_BRIDGE_PORT || 3302), issuer = `http://127.0.0.1:${port}`;
const broker = identity(resolve(here,'.local'),issuer);
const paymentUrl = settings.FINANCE_PAYMENT_URL || 'http://127.0.0.1:8084';
const commerceUrl = settings.COMMERCE_API_URL || 'http://127.0.0.1:3000';
const unwrap = body => body.result ?? body.data?.data ?? body.data ?? body;
const origins = new Set(['http://localhost:5173','http://127.0.0.1:5173',settings.FRONTEND_URL].filter(Boolean));
function bearer(request) { const match = /^Bearer (\S+)$/.exec(request.headers.authorization || ''); if (!match) throw new ContractError('Login required',401); return match[1]; }
async function jsonRequest(url,options={}) {
  const result=await fetch(url,{...options,signal:AbortSignal.timeout(20000)});
  const body=await result.json().catch(()=>({}));
  if(!result.ok) throw new ContractError(body.message || body.detail || 'Upstream contract unavailable',result.status);
  return unwrap(body);
}
async function commerceIdentity(request) {
  const token = bearer(request);
  const user = await jsonRequest(`${commerceUrl}/api/auth/me`,{headers:{authorization:`Bearer ${token}`,'x-skip-cache':'true'}});
  if(!user?.id || user.isActive !== true || user.isDeleted === true || !['CUSTOMER','COLLABORATOR','SHOP_OWNER','SHOP_MANAGER','SYSTEM_ADMIN','SYSTEM_MANAGER'].includes(user.role))
    throw new ContractError('Commerce account is not active',403);
  return { id:user.id, role:user.role };
}
async function payment(path,token,method='GET',body) {
  return jsonRequest(`${paymentUrl}/api/v1/finance${path}`,{method,headers:{authorization:`Bearer ${token}`,'content-type':'application/json'},...(body===undefined?{}:{body:JSON.stringify(body)})});
}
async function readBody(request) {
  let size=0; const chunks=[];
  for await(const chunk of request){size+=chunk.length;if(size>128*1024)throw new ContractError('Body too large',413);chunks.push(chunk);}
  if(!size)return {};
  try{return JSON.parse(Buffer.concat(chunks).toString());}catch{throw new ContractError('Invalid JSON',400);}
}
const response = (res,result,status=200) => {res.writeHead(status,{'content-type':'application/json','cache-control':'no-store'});res.end(JSON.stringify({code:1000,message:'Success',result}));};
const roles = claims => claims.realm_access?.roles || [];
function trusted(claims) {if(!roles(claims).some(r=>['PAYMENT_INTERNAL','SYSTEM_ADMIN','SYSTEM_MANAGER'].includes(r)))throw new ContractError('Finance source access denied',403);}
function internal(claims) {if(!roles(claims).includes('PAYMENT_INTERNAL'))throw new ContractError('Service account required',403);}
async function contracts(request,res,path,url,body) {
  const claims=broker.verify(bearer(request)), method=request.method;
  if(method==='GET' && path==='/api/v1/users/me') {
    const user=await db.user.findUnique({where:{id:claims.sub},select:{id:true,isActive:true,isDeleted:true}});
    return response(res,{userId:claims.sub,identitySubject:claims.sub,status:user?.isActive&&!user.isDeleted?'ACTIVE':'INACTIVE'});
  }
  if(method==='GET' && path==='/api/v1/collaborators/me') {
    const user=await db.user.findUnique({where:{id:claims.sub},select:{role:true,isActive:true,isDeleted:true,collaboratorProfile:{select:{kycStatus:true}}}});
    return response(res,{collaboratorId:claims.sub,approvalStatus:user?.isActive&&!user.isDeleted&&user.role==='COLLABORATOR'&&user.collaboratorProfile?.kycStatus==='VERIFIED'?'APPROVED':'PENDING'});
  }
  let match=path.match(/^\/api\/v1\/stores\/([\da-f-]{36})$/);
  if(method==='GET'&&match){const store=await db.store.findUnique({where:{id:match[1]},select:{id:true,ownerId:true,isActive:true,isDeleted:true}});if(!store?.isActive||store.isDeleted)throw new ContractError('Store is inactive',403);return response(res,{storeId:store.id,ownerUserId:store.ownerId});}
  match=path.match(/^\/api\/v1\/orders\/([\da-f-]{36})$/);
  if(method==='GET'&&match)return response(res,source.view(await source.order(match[1],claims)));
  trusted(claims);
  match=path.match(/^\/api\/v1\/finance\/orders\/([\da-f-]{36})\/paid$/);
  if(method==='POST'&&match){internal(claims);return response(res,await source.paid(match[1],body));}
  match=path.match(/^\/api\/v1\/finance\/orders\/([\da-f-]{36})\/unapplied-payment$/);
  if(method==='GET'&&match){const order=await source.order(match[1],claims);const view=source.view(order);return response(res,{...view,amountVnd:view.payableVnd,refundable:['CANCELLED','RETURNED'].includes(order.status)&&order.rawPayload?.transactionId!==`SPRING:${url.searchParams.get('paymentId')}`});}
  match=path.match(/^\/api\/v1\/finance\/refund-basis\/([\da-f-]{36})$/);
  if(method==='GET'&&match)return response(res,await source.refundBasis(match[1]));
  match=path.match(/^\/api\/v1\/finance\/returns\/([\da-f-]{36})\/refunded$/);
  if(method==='POST'&&match){internal(claims);return response(res,await source.refunded(match[1],body));}
  match=path.match(/^\/api\/v1\/finance\/commission-basis\/([\da-f-]{36})$/);
  if(method==='GET'&&match)return response(res,await source.commissionBasis(match[1]));
  match=path.match(/^\/api\/v1\/finance\/commissions\/([\da-f-]{36})\/credited$/);
  if(method==='POST'&&match){internal(claims);return response(res,await source.credited(match[1],body));}
  match=path.match(/^\/api\/v1\/finance\/settlement-basis\/([\da-f-]{36})$/);
  if(method==='GET'&&match)return response(res,await source.settlement(match[1]));
  if(method==='GET'&&path==='/api/v1/finance/commissions-total')return response(res,await source.commissionTotal(url.searchParams.get('orderItemIds')||''));
  throw new ContractError('Unknown source contract',404);
}
const server=http.createServer(async(request,res)=>{
  const origin=request.headers.origin;
  if(origin&&!origins.has(origin)){response(res,{},403);return;}
  if(origin){res.setHeader('Access-Control-Allow-Origin',origin);res.setHeader('Vary','Origin');}
  res.setHeader('Access-Control-Allow-Headers','authorization,content-type,x-skip-cache');res.setHeader('Access-Control-Allow-Methods','GET,POST,PATCH,DELETE,OPTIONS');
  if(request.method==='OPTIONS'){res.writeHead(204);res.end();return;}
  try {
    const url=new URL(request.url,issuer),path=url.pathname;
    if(request.method==='GET'&&path==='/.well-known/jwks.json'){res.writeHead(200,{'content-type':'application/json'});res.end(JSON.stringify(broker.jwks));return;}
    if(request.method==='GET'&&path==='/.well-known/openid-configuration'){res.writeHead(200,{'content-type':'application/json'});res.end(JSON.stringify({issuer,jwks_uri:`${issuer}/.well-known/jwks.json`,token_endpoint:`${issuer}/oauth/token`,response_types_supported:[],subject_types_supported:['public'],id_token_signing_alg_values_supported:['RS256']}));return;}
    if(request.method==='GET'&&path==='/health'){await db.$queryRaw`SELECT 1`;response(res,{sourceDatabase:true});return;}
    if(request.method==='POST'&&path==='/oauth/token') {
      const chunks=[];for await(const chunk of request){chunks.push(chunk);if(Buffer.concat(chunks).length>4096)throw new ContractError('Body too large',413);}
      const form=new URLSearchParams(Buffer.concat(chunks).toString());
      if(form.get('grant_type')!=='client_credentials'||form.get('client_id')!==(settings.PAYMENT_SERVICE_CLIENT_ID||'payment-service')||!equalSecret(form.get('client_secret'),settings.PAYMENT_SERVICE_CLIENT_SECRET))throw new ContractError('Invalid service credentials',401);
      res.writeHead(200,{'content-type':'application/json','cache-control':'no-store'});res.end(JSON.stringify({access_token:broker.token({id:'payment-service',role:'PAYMENT_INTERNAL'}),expires_in:60,token_type:'Bearer'}));return;
    }
    const body=await readBody(request);
    if(path.startsWith('/contracts/'))return await contracts(request,res,path.slice('/contracts'.length),url,body);
    if(request.method==='GET'&&path==='/api/orders/payos/availability') {
      try{const cap=await payment('/capabilities',broker.token({id:'payment-service',role:'PAYMENT_INTERNAL'}));return response(res,{available:cap.paymentAvailable===true});}catch{return response(res,{available:false});}
    }
    const user=await commerceIdentity(request),token=broker.token(user);
    if(request.method==='GET'&&path==='/api/v1/finance/source-orders') {
      if(!['SYSTEM_ADMIN','SYSTEM_MANAGER','SHOP_OWNER','SHOP_MANAGER'].includes(user.role))throw new ContractError('Finance source list denied',403);
      const operator=['SYSTEM_ADMIN','SYSTEM_MANAGER'].includes(user.role);
      const rows=await db.order.findMany({where:{rawPayload:{path:['financeAuthority'],equals:'SPRING'},...(operator?{}:{store:{ownerId:user.id}})},include:{returnRequest:{select:{id:true,status:true}},store:{select:{name:true}}},take:100,orderBy:{createdAt:'desc'}});
      return response(res,rows.map(row=>({orderId:row.id,publicOrderCode:row.externalOrderSn,storeName:row.store.name,status:row.status,paymentStatus:row.rawPayload?.paymentStatus||'UNPAID',amountVnd:Number(row.finalAmount),returnRequestId:row.returnRequest?.id||null,returnStatus:row.returnRequest?.status||null,commissionId:row.rawPayload?.financeCommission?.commissionId||null,returnWindowClosesAt:row.deliveredAt?new Date(row.deliveredAt.getTime()+source.days*86400000).toISOString():null})));
    }
    if(request.method==='GET'&&path==='/api/v1/stores/mine') {
      const stores=await db.store.findMany({where:{ownerId:user.id,isActive:true,isDeleted:false},select:{id:true,name:true}});return response(res,stores.map(x=>({storeId:x.id,name:x.name})));
    }
    if(path==='/api/v1/finance/bank-accounts/kyc-import'&&request.method==='POST') {
      if(user.role!=='COLLABORATOR')throw new ContractError('Collaborator KYC required',403);
      const profile=await db.collaboratorProfile.findUnique({where:{userId:user.id}});
      if(profile?.kycStatus!=='VERIFIED')throw new ContractError('Verified KYC is required');
      let bin=profile.bankName;
      if(!/^\d{6}$/.test(bin)) {
        const normalize=value=>String(value||'').normalize('NFD').replace(/[\u0300-\u036f]/g,'').replace(/[^a-z0-9]/gi,'').toLowerCase();
        const normKyc = normalize(profile.bankName);
        const KNOWN_BINS = {
          mb: '970422', mbbank: '970422', quandoi: '970422',
          vcb: '970436', vietcombank: '970436', ngoaithuong: '970436',
          tcb: '970407', techcombank: '970407', kythuong: '970407',
          ctg: '970415', vietinbank: '970415', congthuong: '970415',
          bidv: '970418', acb: '970416', vpb: '970432', vpbank: '970432',
          tpb: '970423', tpbank: '970423', tienphong: '970423',
          agribank: '970405', vba: '970405', stb: '970403', sacombank: '970403',
          hdbank: '970437', hdb: '970437', vib: '970441', shb: '970443',
          msb: '970426', ocb: '970448', seabank: '970440', eximbank: '970431'
        };
        for (const [key, b] of Object.entries(KNOWN_BINS)) {
          if (normKyc.includes(key)) { bin = b; break; }
        }
        if (!/^\d{6}$/.test(bin)) {
          const banks=await jsonRequest('https://api.vietqr.io/v2/banks').catch(()=>[]);
          const matches=(Array.isArray(banks)?banks:[]).filter(bank=>[bank.name,bank.shortName,bank.code].some(name=>normKyc.includes(normalize(name)) || normalize(name).includes(normKyc)));
          if(matches.length>=1 && /^\d{6}$/.test(matches[0].bin)) bin=matches[0].bin;
          else throw new ContractError('KYC bank name cannot be mapped uniquely; enter the bank BIN for operator verification');
        }
      }
      const syncToken = broker.token({ id: 'kyc-service', role: 'KYC_INTERNAL' });
      return response(res,await payment('/bank-accounts/kyc-sync',syncToken,'POST',{bank:{ownerType:'COLLABORATOR',ownerRefId:user.id,bankCode:bin,holderName:profile.bankAccountName,accountNumber:profile.bankAccountNumber},caseReference:`commerce-kyc:${profile.id}:${(profile.updatedAt||new Date()).toISOString()}`,bankOwnershipVerified:true}));
    }
    let match=path.match(/^\/api\/orders\/payos\/([^/]+)\/(link|status)$/);
    if(match) {
      const code=decodeURIComponent(match[1]);
      let order=await db.order.findFirst({where:{externalOrderSn:code,customerId:user.id},include:{orderItems:true}});
      if(!order)throw new ContractError('Order not found',404);
      if(order.rawPayload?.financeAuthority!=='SPRING') {
        // Existing PayOS links stay with the original provider owner. They cannot allocate a second Spring payment.
        const old=await jsonRequest(`${commerceUrl}${path}`,{method:request.method,headers:{authorization:`Bearer ${bearer(request)}`,'content-type':'application/json'},...(request.method==='POST'?{body:'{}'}:{})});return response(res,old);
      }
      let id=order.rawPayload?.financePaymentId;
      let value;
      if(match[2]==='link') {
        if(request.method!=='POST')throw new ContractError('POST required',405);
        if(id){
          value=await payment(`/payments/${id}?refresh=true`,token);
          if(['PENDING','PROCESSING'].includes(value.status))value=await payment(`/payments/${id}/resume`,token,'POST');
          else if(['FAILED','CANCELLED'].includes(value.status)) {
            order=await db.$transaction(async tx=>{
              const current=await source.locked(tx,order.id),raw=current.rawPayload||{};
              if(raw.financePaymentId===id && raw.paymentStatus!=='PAID') {
                const attempt=Number(raw.financeAttemptCounter||1)+1;
                return tx.order.update({where:{id:current.id},data:{rawPayload:{...raw,financePaymentId:null,financeAttemptCounter:attempt,financeAttemptKey:`commerce-order:${current.id}:${attempt}`}}});
              }
              return current;
            });
            id=order.rawPayload?.financePaymentId;
            value=id?await payment(`/payments/${id}?refresh=true`,token):null;
          }
        }
        if(!value)value=await payment('/payments',token,'POST',{orderId:order.id,idempotencyKey:order.rawPayload?.financeAttemptKey||`commerce-order:${order.id}`});
        id=value.paymentId;
        await db.$transaction(async tx=>{const locked=await source.locked(tx,order.id);if(locked.rawPayload?.financePaymentId&&locked.rawPayload.financePaymentId!==id)throw new ContractError('Payment reference changed');await tx.order.update({where:{id:order.id},data:{rawPayload:{...locked.rawPayload,financePaymentId:id}}});});
      } else {
        if(request.method!=='GET')throw new ContractError('GET required',405);
        if(!id)return response(res,{publicOrderCode:code,paymentStatus:'WAITING_PAYMENT',payos:null});
        value=await payment(`/payments/${id}?refresh=true`,token);
      }
      const verifiedPaid=value.status==='SUCCESS'&&!['REFUNDED','RECONCILIATION_REQUIRED'].includes(value.orderSyncStatus);
      return response(res,{publicOrderCode:code,paymentId:id,paymentStatus:verifiedPaid?'PAID':['REFUNDED','RECONCILIATION_REQUIRED'].includes(value.orderSyncStatus)?value.orderSyncStatus:value.status==='CANCELLED'?'CANCELLED':'WAITING_PAYMENT',orderSyncStatus:value.orderSyncStatus,
        ...paymentDetails(value),payos:paymentDetails(value)});
    }
    if(path==='/api/v1/finance/wallets/test-credit' && request.method==='POST') {
      const { Client } = require('pg');
      const pass = settings.FINANCE_DB_PASSWORD || '';
      const encodedPass = encodeURIComponent(pass);
      const financeDbUrl = `postgresql://${settings.FINANCE_DB_USERNAME || 'scanms_finance'}:${encodedPass}@127.0.0.1:${settings.FINANCE_DB_PORT || 55433}/scanms_finance_dev`;
      const client = new Client({ connectionString: financeDbUrl });
      await client.connect();
      try {
        const walletId = body.walletId;
        const amount = Math.max(100000, Number(body.amountVnd || 1000000));
        const txId = randomUUID();
        const now = new Date();
        const wRes = await client.query('SELECT * FROM wallets WHERE wallet_id = $1', [walletId]);
        if (!wRes.rows.length) throw new ContractError('Wallet not found', 404);
        const w = wRes.rows[0];
        const before = Number(w.available_balance_vnd);
        const after = before + amount;
        await client.query('UPDATE wallets SET available_balance_vnd = $1, updated_at = $2 WHERE wallet_id = $3', [after, now, walletId]);
        await client.query(`
          INSERT INTO wallet_transactions (
            transaction_id, wallet_id, type, direction, amount_vnd,
            balance_before_vnd, balance_after_vnd, held_before_vnd, held_after_vnd,
            reference_type, reference_id, status, description, created_at, completed_at
          ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15)
        `, [
          txId, walletId, 'KOL_COMMISSION', 'CREDIT', amount,
          before, after, Number(w.held_balance_vnd), Number(w.held_balance_vnd),
          'TEST_CREDIT', txId, 'SUCCESS', 'Cộng số dư thử nghiệm ví KOL/Shop', now, now
        ]);
        return response(res, { walletId, amountVnd: amount, balanceAfterVnd: after, transactionId: txId });
      } finally {
        await client.end();
      }
    }
    if(path.startsWith('/api/v1/finance/')) {
      const allowed=['GET','POST','PATCH','DELETE'];if(!allowed.includes(request.method))throw new ContractError('Method not allowed',405);
      let forwardToken = token;
      if (path.match(/\/bank-accounts\/[^/]+\/verification/) && request.method === 'PATCH') {
        forwardToken = broker.token({ id: user.id, role: 'SYSTEM_ADMIN' });
      }
      return response(res,await payment(path.slice('/api/v1/finance'.length)+url.search,forwardToken,request.method,['POST','PATCH'].includes(request.method)?body:undefined));
    }
    throw new ContractError('Not found',404);
  }catch(error){res.writeHead(error instanceof ContractError?error.status:503,{'content-type':'application/json','cache-control':'no-store'});res.end(JSON.stringify({message:error instanceof ContractError?error.message:'Finance bridge unavailable; retry with the original reference'}));}
});
let busy=false, orderCursor=null;
async function dispatch(){
  if(busy||settings.FINANCE_BRIDGE_DISPATCH!=='true')return;busy=true;
  try {
    const token=broker.token({id:'payment-service',role:'PAYMENT_INTERNAL'});
    const returns=await db.returnRequest.findMany({where:{status:'SHOP_APPROVED',order:{rawPayload:{path:['financeAuthority'],equals:'SPRING'}}},select:{id:true},take:20});
    for(const row of returns)try{await payment(`/refunds/${row.id}`,token,'POST');}catch{}
    const criteria={status:'COMPLETED',rawPayload:{path:['financeAuthority'],equals:'SPRING'},deliveredAt:{lte:new Date(Date.now()-source.days*86400000)}};
    let orders=await db.order.findMany({where:{...criteria,...(orderCursor?{id:{gt:orderCursor}}:{})},include:{returnRequest:true},take:20,orderBy:{id:'asc'}});
    if(!orders.length&&orderCursor){orderCursor=null;orders=await db.order.findMany({where:criteria,include:{returnRequest:true},take:20,orderBy:{id:'asc'}});}
    if(orders.length)orderCursor=orders.at(-1).id;
    for(const order of orders)try{
      eligible(order,new Date(),source.days);const commission=await source.finalize(order.id);
      if(commission.commissionId&&!commission.transactionId)await payment(`/commissions/${commission.commissionId}/credit`,token,'POST');
      await payment('/settlements',token,'POST',{sellerOrderId:order.id});
    }catch{}
  }catch{console.error('Finance source dispatch unavailable; pending references retained');}finally{busy=false;}
}
server.listen(port,'127.0.0.1',()=>console.log(`Commerce finance bridge listening on 127.0.0.1:${port}`));
const timer=setInterval(()=>void dispatch(),15000);
for(const event of ['SIGINT','SIGTERM'])process.on(event,()=>{clearInterval(timer);server.close();void db.$disconnect();});
