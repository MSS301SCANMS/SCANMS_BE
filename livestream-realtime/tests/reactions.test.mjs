import test from 'node:test';
import assert from 'node:assert/strict';
import { createHmac } from 'node:crypto';
import { io } from 'socket.io-client';
import { createLiveServer } from '../server.mjs';

test('KOL plus shop is one viewer and shared hearts are deduplicated, room scoped and restored from persistence', {timeout:15000},async()=>{
  const saved={room:7,other:0},key='fixture-reaction-persistence-secret-32',sockets=[];
  let writes=0,holdProof=false,releaseProof,notifyProof;
  const proofStarted=new Promise(resolve=>{notifyProof=resolve});
  const options={pollMs:100,reactionFlushMs:20,metricsSecret:key,
    verifyToken:async token=>({sub:token,exp:Math.floor(Date.now()/1000)+600,realm_access:{roles:token==='host'?['KOL_CTV']:token==='shop'?['SHOP_OWNER']:['USER']}}),
    fetcher:async(url,request)=>{
      const id=url.match(/livestreams\/([^/?]+)/)?.[1];
      if(url.endsWith('/reactions/stats')){
        const count=JSON.parse(request.body).likeCount,time=request.headers['X-Live-Metrics-Time'];
        assert.equal(request.headers['X-Live-Metrics-Signature'],createHmac('sha256',key).update(`LIKES\n${id}\n${count}\n${time}`).digest('hex'));
        saved[id]=Math.max(saved[id],count);writes++;return Response.json({result:{likeCount:saved[id]}});
      }
      if(url.endsWith('/purchase-access')&&holdProof){notifyProof();await new Promise(resolve=>{releaseProof=resolve});}
      if(['POST','PATCH'].includes(request.method))return Response.json({result:true});
      const result=url.endsWith('/collaborators/me')?{collaboratorId:'kol',approvalStatus:'APPROVED'}:
        url.endsWith('/stores/mine')?[{storeId:'store',approvalStatus:'APPROVED'}]:
        url.includes('/referrals/livestream-access?')?{collaboratorId:'kol',storeId:'store',approvalStatus:'APPROVED'}:
        {livestreamId:id,storeId:'store',status:'LIVE',likeCount:saved[id],collaboratorRefs:{collaboratorIds:['kol']},participantRefs:{collaboratorResponses:{kol:'ACCEPTED'}}};
      return Response.json({result});
    }};
  const start=async()=>{const server=createLiveServer(options);await new Promise(resolve=>server.http.listen(0,'127.0.0.1',resolve));return server;};
  let server=await start();
  const connect=async token=>{
    const socket=io(`http://127.0.0.1:${server.http.address().port}/live-stream`,{auth:{token},transports:['websocket'],reconnection:false});sockets.push(socket);
    await new Promise((resolve,reject)=>{socket.once('connect',resolve);socket.once('connect_error',reject)});return socket;
  };
  const emit=(socket,event,body)=>socket.timeout(3000).emitWithAck(event,body);
  const join=(socket,role,sessionId='room')=>emit(socket,'join_live_room',{sessionId,requestedRole:role});
  const heart=(socket,id)=>emit(socket,'send_reaction',{sessionId:'room',emoji:'❤️',clientReactionId:id,likesCount:999999});
  try{
    const host=await connect('host'),shop=await connect('shop');await join(host,'host');
    assert.equal((await join(shop,'moderator')).viewerCount,1);
    const buyer=await connect('buyer');assert.equal((await join(buyer,'viewer')).likesCount,7);
    const hostTab=await connect('host');assert.equal((await join(hostTab,'viewer')).viewerCount,2,'publisher account is excluded even in a second viewer tab');
    const shopTab=await connect('shop');assert.equal((await join(shopTab,'viewer')).viewerCount,2,'shop counts once across tabs');
    let delivered=0;buyer.on('reaction_broadcast',()=>delivered++);
    const firstBroadcast=new Promise(resolve=>buyer.once('reaction_broadcast',resolve));
    assert.equal((await heart(shop,'one-heart')).reaction.likesCount,8);await firstBroadcast;
    assert.equal((await heart(shop,'one-heart')).reaction.likesCount,8,'lost ACK retries are not extra hearts');
    assert.equal(delivered,1);
    const other=await connect('other-viewer');await join(other,'viewer','other');
    assert.equal((await heart(other,'wrong-room')).ok,false);
    assert.equal((await emit(shop,'send_reaction',{sessionId:'room',emoji:'👍',clientReactionId:'thumb'})).reaction.likesCount,8,'other emotions do not inflate hearts');
    assert.equal((await heart(host,'host-heart')).reaction.likesCount,9);
    const replies=await Promise.all([host,shop,buyer].flatMap((socket,i)=>Array.from({length:5},(_,j)=>heart(socket,`burst-${i}-${j}`))));
    assert.ok(replies.every(reply=>reply.ok));
    const late=await connect('late');holdProof=true;
    const joining=join(late,'viewer');await proofStarted;
    assert.equal((await heart(host,'during-late-join')).reaction.likesCount,25);
    holdProof=false;releaseProof();
    assert.equal((await joining).likesCount,25,'join snapshot cannot overwrite a newer heart sent while room access is pending');
    for(const socket of sockets)socket.disconnect();
    await server.close();assert.equal(saved.room,25);assert.ok(writes<18,'heart persistence is batched');
    server=await start();
    const returning=await connect('buyer');assert.equal((await join(returning,'viewer')).likesCount,25,'relay restart restores the Product count');
    assert.equal((await heart(returning,'after-restart')).reaction.likesCount,26);
  }finally{releaseProof?.();sockets.forEach(socket=>socket.disconnect());await server.close();}
});
