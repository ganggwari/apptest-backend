import { readdir, readFile, writeFile } from 'node:fs/promises'
import { createHash } from 'node:crypto'
async function files(dir) {
  const result = []
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    const path = dir + '/' + entry.name
    if (entry.isDirectory()) result.push(...(await files(path)))
    else result.push(path)
  }
  return result
}
const paths = (await files('dist')).filter(
  (p) => !['sw.js', '_redirects', '_headers'].includes(p.split('/').at(-1)),
)
const hash = createHash('sha256')
for (const path of paths) hash.update(await readFile(path))
const cache = 'routel-static-' + hash.digest('hex').slice(0, 12)
const assets = paths.map((p) => '/' + p.slice(5))
await writeFile(
  'dist/sw.js',
  `
const CACHE=${JSON.stringify(cache)},ASSETS=${JSON.stringify(assets)};
self.addEventListener('install',event=>event.waitUntil(caches.open(CACHE).then(cache=>cache.addAll(ASSETS))));
self.addEventListener('activate',event=>event.waitUntil((async()=>{for(const name of await caches.keys())if(name.startsWith('routel-static-')&&name!==CACHE)await caches.delete(name);await self.clients.claim()})()));
self.addEventListener('fetch',event=>{
 const request=event.request,url=new URL(request.url);
 if(request.method!=='GET'||url.origin!==self.location.origin||url.pathname.startsWith('/api/'))return;
 if(request.mode==='navigate'){event.respondWith((async()=>{const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),4000);try{return await fetch(request,{signal:controller.signal})}catch{return await (await caches.open(CACHE)).match('/index.html')}finally{clearTimeout(timer)}})());return}
 if(ASSETS.includes(url.pathname))event.respondWith(caches.open(CACHE).then(async cache=>(await cache.match(url.pathname))||fetch(request)));
});

// ---- 푸시 (FCM 웹푸시, data-only 메시지: title/body/url/tag) ----
self.addEventListener('push',event=>{
 let payload={};
 try{payload=event.data?event.data.json():{}}catch(e){try{payload={data:{body:event.data.text()}}}catch(e2){}}
 const d=payload.data||payload.notification||payload;
 event.waitUntil((async()=>{
  const wins=await self.clients.matchAll({type:'window',includeUncontrolled:true});
  // 앱 화면이 열려 있으면 앱이 SSE 로 알림·소리·진동을 처리하므로 중복 표시하지 않는다
  if(wins.some(w=>w.visibilityState==='visible'))return;
  await self.registration.showNotification(d.title||'PUF 알림',{
   body:d.body||'',
   tag:d.tag||'puf',
   renotify:true,
   requireInteraction:true,
   silent:false,
   vibrate:[300,120,300,120,600],
   icon:'/icons/customer-192.png',
   badge:'/icons/customer-192.png',
   data:{url:d.url||'/notifications'}
  });
 })());
});
self.addEventListener('notificationclick',event=>{
 event.notification.close();
 const url=(event.notification.data&&event.notification.data.url)||'/notifications';
 event.waitUntil((async()=>{
  const wins=await self.clients.matchAll({type:'window',includeUncontrolled:true});
  for(const w of wins){if('focus' in w){await w.focus();if('navigate' in w){try{await w.navigate(url)}catch(e){}}return}}
  await self.clients.openWindow(url);
 })());
});
`,
)
console.log('PWA shell generated:', assets.length, 'files;', cache)
