import test from 'node:test';
import assert from 'node:assert/strict';
import { io } from 'socket.io-client';
import { createLiveServer } from '../server.mjs';

const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
const wait = async predicate => { const end=Date.now()+4000; while(!predicate()&&Date.now()<end)await pause(20); assert.ok(predicate()); };
async function fixture(options={}) {
  const sockets=[];
  let activeViews=0,peakViews=0,activeReads=0,peakReads=0,patches=0;
  const server=createLiveServer({ maxViewers:8,pollMs:100,metricsSecret:'fixture-metrics-key-32-characters-long',
    verifyToken:async token=>({sub:token,exp:Math.floor(Date.now()/1000)+600,realm_access:{roles:token==='host'?['KOL_CTV']:token==='shop'?['SHOP_OWNER']:['USER']}}),
    fetcher:async(url,request)=>{
      if(request.method==='POST'&&url.endsWith('/views')){
        activeViews++;peakViews=Math.max(peakViews,activeViews);await pause(30);activeViews--;
        return Response.json({result:true});
      }
      if(request.method==='PATCH'){patches++;return Response.json({result:true});}
      if(request.method==='POST')return Response.json({result:{}});
      activeReads++;peakReads=Math.max(peakReads,activeReads);await pause(options.readDelay||0);activeReads--;
      const result=url.endsWith('/collaborators/me')?{collaboratorId:'kol',approvalStatus:'APPROVED'}:
        url.endsWith('/stores/mine')?[{storeId:'store',approvalStatus:'APPROVED'}]:
        url.includes('/referrals/livestream-access?')?{collaboratorId:'kol',storeId:'store',approvalStatus:'APPROVED'}:
        {livestreamId:'room',storeId:'store',status:'LIVE',collaboratorRefs:{collaboratorIds:['kol']},participantRefs:{collaboratorResponses:{kol:'ACCEPTED'}}};
      return Response.json({result});
    },...options,
  });
  await new Promise(resolve=>server.http.listen(0,'127.0.0.1',resolve));
  const connect=async token=>{
    const socket=io(`http://127.0.0.1:${server.http.address().port}/live-stream`,{auth:{token},transports:['websocket'],reconnection:false});sockets.push(socket);
    await new Promise((resolve,reject)=>{socket.once('connect',resolve);socket.once('connect_error',reject)});return socket;
  };
  const join=(socket,role='viewer')=>socket.timeout(5000).emitWithAck('join_live_room',{sessionId:'room',requestedRole:role});
  return {server,connect,join,stats:()=>({peakViews,peakReads,patches}),close:async()=>{sockets.forEach(socket=>socket.disconnect());await server.close();}};
}

test('concurrent audience includes shop, deduplicates accounts, excludes publisher and caps P2P subscriptions under a burst', {timeout:15000},async()=>{
  const f=await fixture({maxConcurrentJoins:64});
  try {
    const host=await f.connect('host');await f.join(host,'host');
    const shop=await f.connect('shop');await f.join(shop,'moderator');
    let count=-1;host.on('live_viewer_count',event=>{count=event.count});
    const first=await f.connect('same-buyer'),second=await f.connect('same-buyer');
    await f.join(first);assert.equal((await f.join(second)).viewerCount,2);
    const burst=await Promise.all(Array.from({length:40},(_,i)=>f.connect('buyer-'+i)));
    const replies=await Promise.all(burst.map(socket=>f.join(socket)));
    assert.equal(replies.filter(reply=>reply.ok).length,5);
    assert.equal(replies.filter(reply=>reply.code==='ROOM_FULL').length,35);
    assert.equal(f.server.rooms.get('room').members.size,9,'1 host plus 8 media subscribers; shop consumes media capacity too');
    await wait(()=>count===7);
    first.disconnect();await wait(()=>f.server.rooms.get('room').members.size===8);await pause(150);assert.equal(count,7);
    second.disconnect();await wait(()=>count===6);
    await wait(()=>f.stats().patches>0);
    assert.ok(f.stats().peakViews<=4,'view recording must not fan out an unbounded number of API requests');
  } finally {await f.close();}
});

test('join admission bounds authorization work and gives busy clients a retry response', {timeout:10000},async()=>{
  const f=await fixture({maxConcurrentJoins:3,maxViewers:20,readDelay:80,pollMs:10000});
  try {
    const sockets=await Promise.all(Array.from({length:12},(_,i)=>f.connect('buyer-'+i)));
    const replies=await Promise.all(sockets.map(socket=>f.join(socket)));
    assert.equal(replies.filter(reply=>reply.ok).length,3);
    assert.equal(replies.filter(reply=>reply.code==='ROOM_BUSY'&&reply.retryAfterMs===2000).length,9);
    assert.ok(f.stats().peakReads<=3);
    assert.equal((await f.join(sockets[replies.findIndex(reply=>reply.code==='ROOM_BUSY')])).ok,true);
  } finally {await f.close();}
});
