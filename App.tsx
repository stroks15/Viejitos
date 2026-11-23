import React, {useEffect, useRef, useState} from 'react';
import {ActivityIndicator, Alert, AppState, BackHandler, DeviceEventEmitter, Dimensions, Image, ImageBackground, Linking, Modal, PermissionsAndroid, NativeModules, Platform, ScrollView, StatusBar, StyleSheet, Switch, Text, TextInput, TouchableOpacity, View} from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import Tts from 'react-native-tts';

type Video={videoId:string;title:string;publishedAt:string;thumbnail:string};
type Feed={channelTitle:string;videos:Video[]};
type PhoneContact={id:string;name:string;photoUri?:string;phoneNumbers:string[]};
type EmergencyAction='call'|'wa-call'|'sms'|'wa-message';
type EmergencyShortcut={contact:PhoneContact;action:EmergencyAction};
const ContactsNative=NativeModules.ContactsModule as {getContacts:()=>Promise<PhoneContact[]>} | undefined;
type AppInfo={label:string;packageName:string;icon?:string};
type WaNote={key:string;packageName:string;appName?:string;sender:string;text:string;time:number};
type Insets={top:number;bottom:number;left:number;right:number};
const Launcher=NativeModules.LauncherModule as {
 getInstalledApps:()=>Promise<AppInfo[]>;openApp:(pkg:string)=>Promise<boolean>;openCategory:(c:string)=>Promise<boolean>;
 callNumber:(n:string)=>Promise<string>;isFlashAvailable:()=>Promise<boolean>;isTorchOn:()=>Promise<boolean>;setTorch:(on:boolean)=>Promise<boolean>;
 isNotificationAccessGranted:()=>Promise<boolean>;openNotificationAccessSettings:()=>Promise<boolean>;openAppSettings:()=>Promise<boolean>;
 getStoredNotifications:()=>Promise<WaNote[]>;clearStoredNotifications:()=>Promise<boolean>;getInsets:()=>Promise<Insets>;speakSpanish?:(text:string)=>Promise<boolean>;
} | undefined;
const DEBOUNCE_MS=400;
const WHATSAPP_PKGS=['com.whatsapp','com.whatsapp.w4b'];
const ASSIGNABLE=['LLAMAR','MENSAJES','RADIO','YOUTUBE','CALCULADORA','CÁMARA'];
const APP_DEFAULTS:Record<string,{packages?:string[];category?:string;url?:string}>={
 'LLAMAR':{category:'DIALER'},
 'MENSAJES':{packages:['com.google.android.apps.messaging','com.samsung.android.messaging','com.android.mms','com.miui.mms'],category:'MESSAGING'},
 'RADIO':{packages:['com.sec.android.app.fm','com.android.fmradio','com.miui.fm','com.motorola.fmplayer']},
 'YOUTUBE':{packages:['com.google.android.youtube'],url:'https://www.youtube.com'},
 'CALCULADORA':{packages:['com.google.android.calculator','com.sec.android.app.popupcalculator','com.android.calculator2','com.miui.calculator'],category:'CALCULATOR'},
 'CÁMARA':{category:'CAMERA'},
};
const MAX_CHANNELS=3;
const QUICK_LINKS=4;
const VIDEOS_PER_CHANNEL=3;
const DEFAULT_YOUTUBE_ENDPOINT='https://yxjyqpnvgtkdaaukackt.supabase.co/functions/v1/youtube-feed';
const DEFAULT_SUPABASE_ANON='eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inl4anlxcG52Z3RrZGFhdWthY2t0Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTI1NTQ0NjYsImV4cCI6MjEwODEzMDQ2Nn0.dezLOZfa9BDrWjf6WOuH_4FtZB0MpL_Ulm3NRD8XJQQ';
const EMPTY_CHANNELS=Array.from({length:MAX_CHANNELS},()=> '');
const COLORS=['#1976D2','#168C82','#7955A5','#D18B16','#C04F36','#D52B2B','#526B8A','#59636F'];
const APPS=[['LLAMAR','☎'],['MENSAJES','✉'],['CONTACTOS','●'],['WHATSAPP','◉'],['LINTERNA','☼'],['RADIO','♫'],['YOUTUBE','▶'],['CALCULADORA','▦'],['CÁMARA','▣']];
const say=(s:string)=>{if(Platform.OS==='android'&&Launcher?.speakSpanish){Launcher.speakSpanish(s).catch(()=>{});return;}Tts.getInitStatus().then(()=>{Tts.setDefaultLanguage('es-MX').catch(()=>{});Tts.speak(s);}).catch(()=>{});};

export default function App(){
 const [page,setPage]=useState(0),[channels,setChannels]=useState(EMPTY_CHANNELS),[feeds,setFeeds]=useState<Record<number,Feed>>({});
 const [endpoint,setEndpoint]=useState(DEFAULT_YOUTUBE_ENDPOINT),[anon,setAnon]=useState(DEFAULT_SUPABASE_ANON),[busy,setBusy]=useState(false),[feedErrors,setFeedErrors]=useState<Record<number,string>>({}),[edit,setEdit]=useState<number|null>(null),[draft,setDraft]=useState('');
 const [font,setFont]=useState(24),[contrast,setContrast]=useState(false),[voice,setVoice]=useState(true),[time,setTime]=useState(new Date());
 const [quickLinks,setQuickLinks]=useState(['','','','']);
 const [consultName,setConsultName]=useState(''),[consultPhone,setConsultPhone]=useState(''),[consultUrl,setConsultUrl]=useState('');
 const [visibleApps,setVisibleApps]=useState(APPS.map(a=>a[0]));
 const [extraHomeApps,setExtraHomeApps]=useState<string[]>([]),[homeOrder,setHomeOrder]=useState(APPS.map(a=>a[0])),[homePicker,setHomePicker]=useState(false),[channelSearch,setChannelSearch]=useState('');
 const [settingsLoaded,setSettingsLoaded]=useState(false);
 const [contacts,setContacts]=useState<PhoneContact[]>([]),[contactsLoading,setContactsLoading]=useState(false),[contactsError,setContactsError]=useState(''),[contactsPage,setContactsPage]=useState(0),[contactSearch,setContactSearch]=useState('');
 const [emergencySlots,setEmergencySlots]=useState<(EmergencyShortcut|null)[]>(Array(20).fill(null));
 const [contactSlotIndex,setContactSlotIndex]=useState<number|null>(null),[pendingContact,setPendingContact]=useState<PhoneContact|null>(null),[contactChooser,setContactChooser]=useState(false),[actionChooser,setActionChooser]=useState(false);
 const [apps,setApps]=useState<AppInfo[]>([]),[appMap,setAppMap]=useState<Record<string,string>>({}),[pickFor,setPickFor]=useState<string|null>(null),[appSearch,setAppSearch]=useState('');
 const [torchOn,setTorchOn]=useState(false),[notifAccess,setNotifAccess]=useState(false),[notes,setNotes]=useState<WaNote[]>([]),[insets,setInsets]=useState<Insets>({top:0,bottom:0,left:0,right:0});
 const lastTap=useRef(0),askedCall=useRef(false),pageRef=useRef(0);pageRef.current=page;
 const [selectedItem,setSelectedItem]=useState<string|null>(null);
 const scroll=useRef<ScrollView>(null),width=Dimensions.get('window').width-insets.left-insets.right;
 useEffect(()=>{const t=setInterval(()=>setTime(new Date()),1000);return()=>clearInterval(t);},[]);
 useEffect(()=>{AsyncStorage.multiGet(['yt.channels','yt.endpoint','yt.anon','font','contrast','voice','quickLinks','consultName','consultPhone','consultUrl','visibleApps','appMap','extraHomeApps','homeOrder','emergencySlots']).then(rows=>{const v=Object.fromEntries(rows);if(v['yt.channels']){const saved=JSON.parse(v['yt.channels']!);setChannels(Array.from({length:MAX_CHANNELS},(_,i)=>typeof saved[i]==='string'?saved[i]:''));}if(v['yt.endpoint'])setEndpoint(v['yt.endpoint']!);if(v['yt.anon'])setAnon(v['yt.anon']!);if(v.font)setFont(Number(v.font));if(v.contrast)setContrast(v.contrast==='true');if(v.voice)setVoice(v.voice==='true');if(v.quickLinks){const links=JSON.parse(v.quickLinks);setQuickLinks(Array.from({length:QUICK_LINKS},(_,i)=>typeof links[i]==='string'?links[i]:''));}if(v.consultName!==null)setConsultName(v.consultName||'');if(v.consultPhone!==null)setConsultPhone(v.consultPhone||'');if(v.consultUrl!==null)setConsultUrl(v.consultUrl||'');if(v.visibleApps){const saved=JSON.parse(v.visibleApps);setVisibleApps(APPS.map(a=>a[0]).filter(name=>saved.includes(name)));}if(v.appMap){try{setAppMap(JSON.parse(v.appMap));}catch{}}if(v.extraHomeApps){try{setExtraHomeApps(JSON.parse(v.extraHomeApps));}catch{}}if(v.homeOrder){try{setHomeOrder(JSON.parse(v.homeOrder));}catch{}}if(v.emergencySlots){try{const saved=JSON.parse(v.emergencySlots);setEmergencySlots(Array.from({length:20},(_,i)=>saved[i]||null));}catch{}}setSettingsLoaded(true);}).catch(()=>setSettingsLoaded(true));},[]);
 useEffect(()=>{if(!settingsLoaded)return;AsyncStorage.multiSet([['yt.channels',JSON.stringify(channels)],['yt.endpoint',endpoint],['yt.anon',anon],['font',String(font)],['contrast',String(contrast)],['voice',String(voice)],['quickLinks',JSON.stringify(quickLinks)],['consultName',consultName],['consultPhone',consultPhone],['consultUrl',consultUrl],['visibleApps',JSON.stringify(visibleApps)],['appMap',JSON.stringify(appMap)],['extraHomeApps',JSON.stringify(extraHomeApps)],['homeOrder',JSON.stringify(homeOrder)],['emergencySlots',JSON.stringify(emergencySlots)]]).catch(()=>{});},[settingsLoaded,channels,endpoint,anon,font,contrast,voice,quickLinks,consultName,consultPhone,consultUrl,visibleApps,appMap,extraHomeApps,homeOrder,emergencySlots]);
 useEffect(()=>{const handle=()=>{if(pageRef.current)go(0);return true;};const sub=BackHandler.addEventListener('hardwareBackPress',handle);const ev=DeviceEventEmitter.addListener('LauncherBack',handle);return()=>{sub.remove();ev.remove();};},[]);
 const go=(n:number)=>{const p=Math.max(0,Math.min(5,n));setPage(p);scroll.current?.scrollTo({x:p*width,animated:true});};
 const loadContacts=async()=>{
  if(Platform.OS!=='android'){setContactsError('La agenda nativa solo está disponible en Android.');return;}
  if(!ContactsNative){setContactsError('El lector de contactos no está incluido en esta compilación.');return;}
  setContactsLoading(true);setContactsError('');
  try{
   let granted=await PermissionsAndroid.check(PermissionsAndroid.PERMISSIONS.READ_CONTACTS);
   if(!granted)granted=(await PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.READ_CONTACTS,{title:'Permitir acceso a contactos',message:'FerOs abues necesita permiso para mostrar los nombres, fotografías y teléfonos de tu agenda. Los datos permanecen en este teléfono.',buttonPositive:'Permitir',buttonNegative:'Ahora no'}))===PermissionsAndroid.RESULTS.GRANTED;
   if(!granted){setContactsError('Permiso no concedido. Pulsa “Dar permiso” para volver a solicitarlo desde Android.');return;}
   const list=await ContactsNative.getContacts();
   setContacts(list);setContactsPage(0);
   if(voice)say(`Se cargaron ${list.length} contactos`);
  }catch(error){setContactsError(error instanceof Error?error.message:'No se pudieron cargar los contactos.');}
  finally{setContactsLoading(false);}
 };
 useEffect(()=>{if(page===1&&contacts.length===0&&!contactsLoading)void loadContacts();},[page]);
 const filteredContacts=contactSearch.trim()?contacts.filter(contact=>{const q=contactSearch.trim().toLocaleLowerCase('es-MX');return contact.name.toLocaleLowerCase('es-MX').includes(q)||contact.phoneNumbers.some(phone=>phone.toLowerCase().includes(q));}):[];
 const contactPageCount=5;
 const visibleContacts=filteredContacts.slice(contactsPage*6,contactsPage*6+6);
 const contactActions=(contact:PhoneContact)=>{
  const phone=contact.phoneNumbers[0]||'';
  if(!phone){Alert.alert(contact.name,'Este contacto no tiene un número telefónico.');return;}
  Alert.alert('¿Qué deseas hacer?',`Seleccionaste a ${contact.name}. Elige cómo quieres comunicarte.`,[
   {text:'Llamar',onPress:()=>{void callContact(phone);}},
   {text:'WhatsApp',onPress:()=>{const digits=phone.replace(/\\D/g,'');const waNumber=digits.length===10?`52${digits}`:digits;Linking.openURL(`https://wa.me/${waNumber}`).catch(()=>Alert.alert('WhatsApp','No se pudo abrir WhatsApp.'));}},
   {text:'Mensaje SMS',onPress:()=>{const num=phone.replace(/[^+0-9]/g,'');Linking.openURL(`sms:${num}`).catch(()=>Alert.alert('Mensajes','No se pudo abrir la aplicación de mensajes.'));}},
   {text:'Cancelar',style:'cancel'}
  ]);
 };
 const callContact=async(phone:string)=>{
  const num=phone.replace(/[^+0-9]/g,'');if(!num)return;
  try{
   if(Platform.OS==='android'&&!askedCall.current){const ok=await PermissionsAndroid.check(PermissionsAndroid.PERMISSIONS.CALL_PHONE);if(!ok){askedCall.current=true;await PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.CALL_PHONE,{title:'Permitir llamadas directas',message:'FerOs abues puede llamar con un solo toque. Si no lo permites, se abrirá el marcador con el número listo.',buttonPositive:'Permitir',buttonNegative:'Ahora no'});}}
   if(Launcher){await Launcher.callNumber(num);return;}
  }catch{}
  Linking.openURL(`tel:${num}`).catch(()=>Alert.alert('Llamar','No se pudo abrir la aplicación de teléfono.'));
 };

 const once=(fn:()=>void)=>()=>{const now=Date.now();if(now-lastTap.current<DEBOUNCE_MS)return;lastTap.current=now;fn();};
 const openEmergencyAction=(slot:EmergencyShortcut)=>{const raw=slot.contact.phoneNumbers[0]||'';const phone=raw.replace(/[^+0-9]/g,'');if(!phone){Alert.alert(slot.contact.name,'Este contacto no tiene un número telefónico.');return;}const digits=phone.replace(/\\D/g,'');const waNumber=digits.length===10?'52'+digits:digits;if(slot.action==='call'){void callContact(phone);return;}if(slot.action==='sms'){Linking.openURL('smsto:'+phone).catch(()=>Alert.alert('Mensajes','No se pudo abrir la aplicación de mensajes.'));return;}if(slot.action==='wa-message'){Linking.openURL('https://wa.me/'+waNumber).catch(()=>Alert.alert('WhatsApp','No se pudo abrir el chat.'));return;}Alert.alert('Llamada por WhatsApp','Se abrirá el chat de '+slot.contact.name+'. Para iniciar la llamada, toca el icono de teléfono dentro de WhatsApp.',[{text:'ABRIR CHAT',onPress:()=>Linking.openURL('https://wa.me/'+waNumber).catch(()=>{})},{text:'CANCELAR',style:'cancel'}]);};
 const selectEmergencyContact=(contact:PhoneContact)=>{setPendingContact(contact);setContactChooser(false);setActionChooser(true);};
 const saveEmergencyAction=(action:EmergencyAction)=>{if(contactSlotIndex===null||!pendingContact)return;setEmergencySlots(old=>old.map((slot,i)=>i===contactSlotIndex?{contact:pendingContact,action}:slot));setPendingContact(null);setContactSlotIndex(null);setActionChooser(false);};
 const actionLabel=(action:EmergencyAction)=>({call:'llamada telefónica','wa-call':'llamada por WhatsApp',sms:'mensaje SMS','wa-message':'mensaje por WhatsApp'}[action]);
 const twoTap=(key:string,label:string,fn:()=>void)=>{if(selectedItem!==key){setSelectedItem(key);if(voice)say(label);return;}setSelectedItem(null);fn();};
 const refreshInsets=()=>{if(Platform.OS!=='android')return;if(Launcher)Launcher.getInsets().then(setInsets).catch(()=>{});else setInsets(o=>({...o,top:StatusBar.currentHeight||0}));};
 const refreshNative=async()=>{
  refreshInsets();
  try{const ok=await PermissionsAndroid.check(PermissionsAndroid.PERMISSIONS.READ_CONTACTS);if(ok&&contactsError&&!contactsLoading)void loadContacts();}catch{}
  if(!Launcher)return;
  try{setTorchOn(await Launcher.isTorchOn());}catch{}
  try{setNotifAccess(await Launcher.isNotificationAccessGranted());}catch{}
  try{setNotes(await Launcher.getStoredNotifications());}catch{}
  try{setApps(await Launcher.getInstalledApps());}catch{}
 };
 const refreshRef=useRef(refreshNative);refreshRef.current=refreshNative;
 useEffect(()=>{
  refreshRef.current();const t=setTimeout(()=>refreshInsets(),700);
  const st=AppState.addEventListener('change',s=>{if(s==='active')refreshRef.current();});
  const dim=Dimensions.addEventListener('change',()=>refreshInsets());
  const tor=DeviceEventEmitter.addListener('TorchStateChanged',(e:{on:boolean})=>setTorchOn(!!e.on));
  const wa=DeviceEventEmitter.addListener('DeviceNotification',(n:WaNote)=>setNotes(old=>[n,...old.filter(x=>x.key!==n.key)].slice(0,60)));
  return()=>{clearTimeout(t);st.remove();dim.remove();tor.remove();wa.remove();};
 },[]);
 const toggleTorch=async()=>{
  if(!Launcher){Alert.alert('Linterna','La linterna no está incluida en esta compilación.');return;}
  try{const next=await Launcher.setTorch(!torchOn);setTorchOn(next);if(voice)say(next?'Linterna encendida':'Linterna apagada');}
  catch(e:any){const c=e?.code;Alert.alert('Linterna',c==='NO_FLASH'?'Este teléfono no tiene linterna disponible.':c==='TORCH_IN_USE'?'La linterna está en uso por otra aplicación, por ejemplo la cámara. Ciérrala e intenta de nuevo.':'No se pudo cambiar el estado de la linterna.');try{setTorchOn(await Launcher.isTorchOn());}catch{}}
 };
 const openPackages=async(pkgs:string[])=>{if(!Launcher)return false;for(const p of pkgs){try{if(await Launcher.openApp(p))return true;}catch{}}return false;};
 const openTileApp=async(name:string)=>{
  if(!Launcher){Alert.alert(name,'Esta función no está incluida en esta compilación.');return;}
  const assigned=appMap[name];if(assigned&&await openPackages([assigned]))return;
  const d=APP_DEFAULTS[name];
  if(d?.packages&&await openPackages(d.packages))return;
  if(d?.category){try{if(await Launcher.openCategory(d.category))return;}catch{}}
  if(d?.url){Linking.openURL(d.url).catch(()=>{});return;}
  Alert.alert(name,'No se encontró una aplicación para este botón. Elige una en Ajustes → Aplicaciones de los botones.',[{text:'Elegir ahora',onPress:()=>setPickFor(name)},{text:'Cancelar',style:'cancel'}]);
 };
 const openWhatsApp=async(pkg?:string)=>{const list=pkg?[pkg,...WHATSAPP_PKGS.filter(p=>p!==pkg)]:WHATSAPP_PKGS;if(await openPackages(list))return;Alert.alert('WhatsApp','WhatsApp no está instalado en este teléfono.');};
 const grantNotifAccess=()=>{if(!Launcher){Alert.alert('Notificaciones','Esta compilación no incluye el lector de notificaciones.');return;}Launcher.openNotificationAccessSettings().catch(()=>Alert.alert('Notificaciones','No se pudo abrir los ajustes de Android.'));};
 const clearNotes=()=>{setNotes([]);Launcher?.clearStoredNotifications().catch(()=>{});};
 const refresh=async()=>{
  if(!endpoint.trim()){Alert.alert('Falta configurar videos','En Configuración agrega la URL de la función segura de Supabase.');return;}
  const entries=channels.map((url,index)=>({url:url.trim(),index})).filter(x=>x.url);if(!entries.length)return;
  setBusy(true);setFeedErrors({});const results=await Promise.allSettled(entries.map(async x=>{const r=await fetch(endpoint.trim(),{method:'POST',headers:{'Content-Type':'application/json','apikey':anon,'Authorization':`Bearer ${anon}`},body:JSON.stringify({channelUrl:x.url,limit:VIDEOS_PER_CHANNEL})});const data=await r.json();if(!r.ok)throw new Error(data.error||`Canal ${x.index+1}: HTTP ${r.status}`);return [x.index,{channelTitle:data.channelTitle||`CANAL ${x.index+1}`,videos:Array.isArray(data.videos)?data.videos.slice(0,VIDEOS_PER_CHANNEL):[]}] as const;}));const nextFeeds:Record<number,Feed>={};const nextErrors:Record<number,string>={};results.forEach((result,i)=>{const index=entries[i].index;if(result.status==='fulfilled')nextFeeds[index]=result.value[1];else nextErrors[index]=result.reason instanceof Error?result.reason.message:'No se pudo cargar este canal.';});setFeeds(old=>({...old,...nextFeeds}));setFeedErrors(nextErrors);setBusy(false);if(results.every(x=>x.status==='rejected'))Alert.alert('No se pudieron cargar los videos','Revisa la conexión, el enlace y la configuración de Supabase.');
 };
 useEffect(()=>{if(page===3&&channels.some(x=>x.trim())&&endpoint.trim())refresh();},[page,channels,endpoint]);
 const saveChannel=()=>{const value=draft.trim();if(!/^https?:\/\/(www\.)?(youtube\.com|m\.youtube\.com)\/(\@[^/]+|channel\/UC[a-zA-Z0-9_-]+)/i.test(value)){Alert.alert('Enlace no válido','Pega un enlace de canal de YouTube /@usuario o /channel/ID.');return;}if(edit===null)return;setChannels(old=>old.map((v,i)=>i===edit?value:v));setFeeds(old=>{const next={...old};delete next[edit];return next;});setFeedErrors(old=>{const next={...old};delete next[edit];return next;});setEdit(null);setDraft('');if(voice)say('Canal guardado');};
 const openVideo=(id:string)=>Linking.openURL(`https://www.youtube.com/watch?v=${id}`).catch(()=>Alert.alert('YouTube','No se pudo abrir el video.'));
 const dark=contrast,bg=dark?'#000':'#F3F5F8',card=dark?'#171717':'#FFFFFF',fg=dark?'#FFF':'#17212B',muted=dark?'#EAEAEA':'#536170';
 const title=(s:string)=><Text style={[styles.title,{color:fg,fontSize:font+8}]}>{s}</Text>;
 const tile=(name:string,glyph:string,i:number)=>{
  const assigned=apps.find(a=>a.packageName===appMap[name]);const isTorch=name==='LINTERNA';const lit=isTorch&&torchOn;const ink=lit?{color:'#17212B'}:null;
  return <TouchableOpacity key={name} style={[styles.tile,{backgroundColor:lit?'#F2B705':COLORS[i%COLORS.length],borderWidth:selectedItem==='app:'+name?5:0,borderColor:'#FFD600',transform:[{scale:selectedItem==='app:'+name?1.04:1}]}]} onPress={()=>twoTap('app:'+name,isTorch?(torchOn?'Apagar linterna':'Encender linterna'):name,()=>{if(name==='CONTACTOS')go(1);else if(name==='WHATSAPP')go(5);else if(isTorch)void toggleTorch();else void openTileApp(name);})}>{assigned?.icon?<Image source={{uri:`data:image/png;base64,${assigned.icon}`}} style={styles.appIcon}/>:<Text style={[styles.glyph,ink]}>{glyph}</Text>}<Text style={[styles.tileText,{fontSize:Math.max(18,font-6)},ink]}>{lit?'LINTERNA ENCENDIDA':name}</Text></TouchableOpacity>;
 };
 const noteRow=(n:WaNote)=><TouchableOpacity key={n.key} style={styles.note} onPress={()=>twoTap('notification:'+n.key,n.sender||n.appName||'Notificación',()=>{if(WHATSAPP_PKGS.includes(n.packageName))void openWhatsApp(n.packageName);else void openPackages([n.packageName]);})}><Text style={{color:fg,fontWeight:'900',fontSize:Math.max(16,font-6)}} numberOfLines={1}>{n.sender||n.appName||'Notificación'}</Text><Text style={{color:muted,fontSize:12,fontWeight:'800'}}>{n.appName||n.packageName}</Text><Text style={{color:muted}} numberOfLines={2}>{n.text}</Text><Text style={{color:muted,fontSize:12}}>{new Date(n.time).toLocaleTimeString('es-MX',{hour:'2-digit',minute:'2-digit'})}</Text></TouchableOpacity>;
 const whatsappNotes=notes.filter(n=>WHATSAPP_PKGS.includes(n.packageName));
 const moveHomeApp=(name:string,delta:number)=>setHomeOrder(old=>{const list=old.filter(x=>APPS.some(a=>a[0]===x));const i=list.indexOf(name),j=i+delta;if(i<0||j<0||j>=list.length)return list;const next=[...list];[next[i],next[j]]=[next[j],next[i]];return next;});
 const openYouTubeSearch=()=>{const q=channelSearch.trim();if(!q){Alert.alert('Buscar canal','Escribe el nombre del canal que deseas encontrar.');return;}Linking.openURL('https://www.youtube.com/results?search_query='+encodeURIComponent(q)).catch(()=>Alert.alert('YouTube','No se pudo abrir la búsqueda.'));};
 return <ImageBackground source={page===0?require('./assets/backgrounds/home.jpg'):require('./assets/backgrounds/contacts.jpg')} resizeMode="cover" imageStyle={{opacity:0.24}} style={[styles.root,{backgroundColor:bg,paddingTop:insets.top+4,paddingLeft:insets.left,paddingRight:insets.right}]}>
  <StatusBar hidden={true} translucent={true} backgroundColor="transparent" barStyle={dark?'light-content':'dark-content'}/>
  <View style={styles.top}><Text style={{color:fg,fontWeight:'900'}}>▰ Batería   ◉ Wi-Fi   ▥ Datos   ᛒ Bluetooth</Text><Text style={{color:fg,fontWeight:'900'}}>{page+1}/6</Text></View>
  <ScrollView ref={scroll} horizontal pagingEnabled scrollEnabled showsHorizontalScrollIndicator={false} onMomentumScrollEnd={e=>{const p=Math.round(e.nativeEvent.contentOffset.x/width);if(p!==pageRef.current)setPage(Math.max(0,Math.min(5,p)));}}>
   <ScrollView style={{width}} contentContainerStyle={{padding:16}}>
    <View style={styles.clock}><Text style={[styles.time,{color:fg}]}>{time.toLocaleTimeString('es-MX',{hour:'2-digit',minute:'2-digit',hour12:true})}</Text><Text style={{color:muted,fontWeight:'900'}}>{time.toLocaleDateString('es-MX',{weekday:'long',day:'numeric',month:'long'}).toUpperCase()}</Text></View>
    <TouchableOpacity style={styles.whatsapp} onPress={()=>twoTap('home-whatsapp','WhatsApp',()=>go(5))}><Text style={{fontSize:34,color:'#FFF'}}>◉</Text><Text style={styles.whatsappText}>WHATSAPP</Text></TouchableOpacity>
    <TouchableOpacity onPress={()=>go(2)}><Text style={[styles.section,{color:fg}]}>NOTIFICACIONES · VER TODAS</Text><View style={[styles.card,{backgroundColor:card}]}>{notes.length?notes.slice(0,3).map(n=><View key={n.key} style={styles.note}><Text style={{color:fg,fontWeight:'900'}}>{n.appName||n.packageName} · {n.sender}</Text><Text style={{color:muted}} numberOfLines={1}>{n.text}</Text></View>):<Text style={{color:fg}}>{notifAccess?'No hay notificaciones recientes.':'Activa el acceso para ver avisos de WhatsApp, SMS, llamadas y otras aplicaciones.'}</Text>}</View></TouchableOpacity>
    <Text style={[styles.section,{color:fg}]}>APLICACIONES</Text><View style={styles.grid}>{homeOrder.map(name=>APPS.find(a=>a[0]===name)).filter((a):a is string[]=>!!a&&visibleApps.includes(a[0])).map((a,i)=>tile(a[0],a[1],i))}{extraHomeApps.map((pkg,index)=>{const a=apps.find(x=>x.packageName===pkg);if(!a)return null;return <TouchableOpacity key={pkg} style={[styles.tile,{backgroundColor:COLORS[(APPS.length+index)%COLORS.length]}]} onPress={()=>twoTap('extra-app:'+pkg,a.label,()=>{void openPackages([pkg]);})}>{a.icon?<Image source={{uri:'data:image/png;base64,'+a.icon}} style={styles.appIcon}/>:<Text style={styles.glyph}>▣</Text>}<Text style={[styles.tileText,{fontSize:Math.max(18,font-6)}]} numberOfLines={2}>{a.label}</Text></TouchableOpacity>;})}</View>
    <Text style={[styles.section,{color:fg}]}>ENLACES RÁPIDOS</Text>{consultPhone.trim()||consultUrl.trim()?<TouchableOpacity style={[styles.card,{backgroundColor:card}]} onPress={()=>{const target=consultUrl.trim()||`tel:${consultPhone.replace(/[^+0-9]/g,'')}`;Linking.openURL(target).catch(()=>Alert.alert('Servicio de consulta','No se pudo abrir el contacto configurado.'));}}><Text style={{color:fg,fontSize:font,fontWeight:'900'}}>{consultName.trim()||'SERVICIO DE CONSULTA'}  ↗</Text><Text style={{color:muted}}>{consultPhone}</Text></TouchableOpacity>:null}{quickLinks.map((url,i)=>url.trim()?<TouchableOpacity key={i} style={[styles.card,{backgroundColor:card}]} onPress={()=>Linking.openURL(url.trim()).catch(()=>Alert.alert('Enlace','No se pudo abrir este enlace.'))}><Text style={{color:fg,fontSize:font,fontWeight:'800'}}>ENLACE {i+1}  ↗</Text><Text style={{color:muted}} numberOfLines={1}>{url}</Text></TouchableOpacity>:null)}
   </ScrollView>
   <ScrollView style={{width}} contentContainerStyle={{padding:12,flexGrow:1}}>
    {title('CONTACTOS DE EMERGENCIA')}
    <Text style={{color:muted,fontSize:Math.max(18,font-5),fontWeight:'900',marginBottom:10}}>Toca una vez para escuchar. Toca otra vez el mismo botón para llamar o enviar un mensaje. Hasta 20 personas.</Text>
    {contactsLoading?<ActivityIndicator size="large" color="#1976D2"/>:null}
    {contactsError?<View style={[styles.card,{backgroundColor:'rgba(255,255,255,0.94)'}]}><Text style={{color:'#17212B',fontWeight:'900',fontSize:18}}>{contactsError}</Text><TouchableOpacity style={styles.refresh} onPress={loadContacts}><Text style={styles.white}>DAR PERMISO / REINTENTAR</Text></TouchableOpacity></View>:null}
    <View style={{flexDirection:'row',flexWrap:'wrap',justifyContent:'space-between',gap:10}}>
     {emergencySlots.slice(contactsPage*4,contactsPage*4+4).map((slot,local)=>{const index=contactsPage*4+local;const key='emergency:'+index;const active=selectedItem===key;return <TouchableOpacity key={index} accessibilityRole="button" accessibilityLabel={slot?slot.contact.name+' '+actionLabel(slot.action):'Agregar contacto de emergencia'} style={{width:'48%',height:Math.max(148,(Dimensions.get('window').height-245)/2),borderRadius:22,borderWidth:active?6:3,borderColor:active?'#FFD600':'#245C91',backgroundColor:slot?'rgba(224,250,242,0.97)':'rgba(255,255,255,0.96)',alignItems:'center',justifyContent:'center',padding:10,elevation:active?8:2,transform:[{scale:active?1.025:1}]}} onPress={()=>{if(!slot){twoTap(key,'Agregar contacto de emergencia',()=>{setContactSlotIndex(index);if(contacts.length===0)void loadContacts();setContactChooser(true);});return;}twoTap(key,slot.contact.name+', '+actionLabel(slot.action),()=>openEmergencyAction(slot));}}>
      {slot?.contact.photoUri?<Image source={{uri:slot.contact.photoUri}} style={{width:76,height:76,borderRadius:38,marginBottom:6}}/>:null}
      <Text style={{fontSize:slot?Math.max(20,font-2):68,fontWeight:'900',color:slot?'#123B48':'#16834A',textAlign:'center'}} numberOfLines={2}>{slot?slot.contact.name:'＋'}</Text>
      {slot?<><Text style={{fontSize:16,fontWeight:'900',color:'#145B54',marginTop:5}}>{({call:'☎ LLAMAR','wa-call':'🟢 WHATSAPP','sms':'✉ SMS','wa-message':'💬 WHATSAPP'})[slot.action]}</Text><TouchableOpacity onPress={()=>Alert.alert('Editar '+slot.contact.name,'Elige una opción.',[{text:'Cambiar acción',onPress:()=>{setContactSlotIndex(index);setPendingContact(slot.contact);setActionChooser(true);}},{text:'Cambiar contacto',onPress:()=>{setContactSlotIndex(index);setContactChooser(true);}},{text:'Eliminar',style:'destructive',onPress:()=>setEmergencySlots(old=>old.map((v,i)=>i===index?null:v))},{text:'Cancelar',style:'cancel'}])}><Text style={{color:'#9B1C1C',fontWeight:'900',fontSize:15,marginTop:7}}>EDITAR / QUITAR</Text></TouchableOpacity></>:<Text style={{fontSize:17,fontWeight:'900',color:'#145B54'}}>AGREGAR CONTACTO</Text>}
     </TouchableOpacity>;})}
    </View>
    <View style={styles.row}><TouchableOpacity disabled={contactsPage===0} onPress={()=>{setContactsPage(p=>Math.max(0,p-1));setSelectedItem(null);}}><Text style={[styles.adjust,{opacity:contactsPage===0?0.35:1}]}>‹ ANTERIOR</Text></TouchableOpacity><Text style={{color:'#17212B',fontWeight:'900',fontSize:20}}>{contactsPage+1} / 5</Text><TouchableOpacity disabled={contactsPage>=4} onPress={()=>{setContactsPage(p=>Math.min(4,p+1));setSelectedItem(null);}}><Text style={[styles.adjust,{opacity:contactsPage>=4?0.35:1}]}>SIGUIENTE ›</Text></TouchableOpacity></View>
    <TouchableOpacity style={[styles.refresh,{marginTop:12}]} onPress={()=>twoTap('refresh-contacts','Actualizar agenda de contactos',loadContacts)}><Text style={styles.white}>ACTUALIZAR AGENDA</Text></TouchableOpacity>
   </ScrollView>
   <ScrollView style={{width}} contentContainerStyle={{padding:16}}>{title('NOTIFICACIONES')}
    <Text style={{color:muted,marginBottom:12}}>Avisos recientes de WhatsApp, SMS, llamadas perdidas, Messenger y otras aplicaciones.</Text>
    <TouchableOpacity style={[styles.refresh,{backgroundColor:'#245C91'}]} onPress={once(grantNotifAccess)}><Text style={styles.white}>{notifAccess?'ADMINISTRAR PERMISO DE NOTIFICACIONES':'ACTIVAR NOTIFICACIONES'}</Text></TouchableOpacity>
    {!notifAccess?<View style={[styles.card,{backgroundColor:card}]}><Text style={{color:fg,fontWeight:'800',fontSize:Math.max(16,font-6)}}>Para ver aquí tus mensajes, activa el acceso a notificaciones de FerOs abues.</Text><Text style={{color:muted,marginTop:8}}>Si Android indica “Ajuste restringido”, abre Información de la app, pulsa ⋮ y elige “Permitir ajustes restringidos”.</Text><TouchableOpacity style={[styles.refresh,{marginTop:12}]} onPress={once(grantNotifAccess)}><Text style={styles.white}>ACTIVAR ACCESO A NOTIFICACIONES</Text></TouchableOpacity><TouchableOpacity style={[styles.refresh,{backgroundColor:'#245C91',marginBottom:0}]} onPress={()=>{Launcher?.openAppSettings().catch(()=>{});}}><Text style={styles.white}>INFORMACIÓN DE LA APP</Text></TouchableOpacity></View>:null}
    <View style={[styles.card,{backgroundColor:card}]}>{notes.length?notes.map(noteRow):<Text style={{color:fg}}>{notifAccess?'No hay notificaciones recientes.':'Activa el acceso en Android para consultar tus notificaciones.'}</Text>}</View>
    {notes.length?<TouchableOpacity onPress={clearNotes}><Text style={{color:'#C0392B',textAlign:'center',fontWeight:'900',marginVertical:12}}>BORRAR HISTORIAL</Text></TouchableOpacity>:null}
    <Text style={[styles.section,{color:fg}]}>CONVERSACIONES RECIENTES DE WHATSAPP</Text><Text style={{color:muted,marginBottom:8}}>Historial local de los últimos avisos recibidos, aunque ya se hayan leído.</Text>{whatsappNotes.length?whatsappNotes.map(noteRow):<Text style={{color:muted}}>Todavía no hay conversaciones registradas.</Text>}
    <TouchableOpacity style={[styles.refresh,{backgroundColor:'#16834A'}]} onPress={once(()=>{void openWhatsApp();})}><Text style={styles.white}>ABRIR WHATSAPP</Text></TouchableOpacity>
   </ScrollView>
   <ScrollView style={{width}} contentContainerStyle={{padding:16}}>
    {title('VIDEOS')}
    <TextInput value={channelSearch} onChangeText={setChannelSearch} placeholder="Nombre del canal que quieres buscar" style={styles.input}/><TouchableOpacity style={[styles.refresh,{backgroundColor:'#245C91'}]} onPress={openYouTubeSearch}><Text style={styles.white}>BUSCAR CANAL EN YOUTUBE</Text></TouchableOpacity>
    <Text style={{color:muted,marginBottom:14}}>Busca el nombre del canal en YouTube y abre sus resultados para elegirlo. Después copia el enlace del canal y pégalo para mostrar sus tres videos más recientes.</Text>
    {channels.map((url,i)=>{
     const feed=feeds[i];
     const videos=feed?.videos?.slice(0,VIDEOS_PER_CHANNEL)||[];
     return url.trim()?(
      <View key={i} style={[styles.channel,{backgroundColor:card}]}>
       <View style={styles.channelHead}>
        <View style={{flex:1}}>
         <Text style={[styles.channelTitle,{color:fg}]}>{feed?.channelTitle||`CANAL ${i+1}`}</Text>
         <Text style={{color:muted,fontSize:11}} numberOfLines={1}>{url}</Text>
        </View>
        <TouchableOpacity style={styles.edit} onPress={()=>{setEdit(i);setDraft(url);}}>
         <Text style={styles.white}>EDITAR</Text>
        </TouchableOpacity>
       </View>
       {videos.length>0?videos.map((v,j)=>(
        <TouchableOpacity key={v.videoId} style={styles.video} onPress={()=>openVideo(v.videoId)}>
         <Image source={{uri:v.thumbnail}} style={styles.thumb}/>
         <View style={{flex:1}}>
          <Text style={{color:fg,fontWeight:'900'}} numberOfLines={2}>{v.title}</Text>
          <Text style={{color:muted,fontSize:11}}>{j===0?'MÁS RECIENTE':`ANTERIOR ${j}`} · {new Date(v.publishedAt).toLocaleDateString('es-MX')}</Text>
         </View>
         <Text style={{color:'#D52B2B',fontSize:25}}>▶</Text>
        </TouchableOpacity>
       )):<Text style={{color:muted,marginTop:12}}>{busy?'Cargando los tres videos…':feedErrors[i]||'No hay videos disponibles. Comprueba el enlace y la conexión.'}</Text>}
      </View>
     ):(
      <TouchableOpacity key={i} style={[styles.plusCard,{backgroundColor:card}]} onPress={()=>twoTap('youtube-add:'+i,'Agregar canal de YouTube '+(i+1),()=>{setEdit(i);setDraft('');})}>
       <Text style={styles.plus}>＋</Text>
       <Text style={[styles.channelTitle,{color:fg}]}>AGREGAR CANAL {i+1}</Text>
       <Text style={{color:muted}}>Pulsa ＋ para elegir el canal encontrado y pegar su enlace</Text>
      </TouchableOpacity>
     );
    })}
    {busy?<ActivityIndicator size="large" color="#D52B2B"/>:null}
    <TouchableOpacity style={styles.refresh} onPress={refresh}><Text style={styles.white}>ACTUALIZAR VIDEOS</Text></TouchableOpacity>
   </ScrollView>
   <ScrollView style={{width}} contentContainerStyle={{padding:16}}>
    {title('CONFIGURACIÓN')}
    <Text style={{color:muted,marginBottom:14,fontSize:font-4}}>Ajustes de accesibilidad, enlaces y aplicaciones</Text>
    <View style={[styles.card,{backgroundColor:card}]}>
     <Text style={[styles.channelTitle,{color:fg,fontSize:font-2}]}>Conexión segura de YouTube</Text>
     <Text style={{color:muted,marginVertical:8,fontSize:font-5}}>Estos datos solo conectan la pantalla 4 con la función de Supabase. La clave secreta YOUTUBE_API_KEY permanece en Supabase y nunca se introduce en el teléfono.</Text>
     <TextInput value={endpoint} onChangeText={setEndpoint} placeholder="URL de Supabase Edge Function" autoCapitalize="none" style={styles.input}/>
     <TextInput value={anon} onChangeText={setAnon} placeholder="Clave pública anon / publishable" autoCapitalize="none" style={styles.input}/>
     <TouchableOpacity style={styles.refresh} onPress={refresh}><Text style={styles.white}>PROBAR CONEXIÓN</Text></TouchableOpacity>
    </View>
    <View style={[styles.card,{backgroundColor:card}]}>
     <Text style={[styles.channelTitle,{color:fg,fontSize:font-2}]}>Cuatro enlaces editables</Text>
     <Text style={{color:muted,marginVertical:8}}>Agrega direcciones web que aparecerán como accesos rápidos en Inicio.</Text>
     {quickLinks.map((url,i)=><TextInput key={i} value={url} onChangeText={value=>setQuickLinks(old=>old.map((v,j)=>j===i?value:v))} placeholder={`Enlace ${i+1} · https://...`} autoCapitalize="none" keyboardType="url" style={styles.input}/>)}
    </View>
    <View style={[styles.card,{backgroundColor:card}]}>
     <Text style={[styles.channelTitle,{color:fg,fontSize:font-2}]}>Servicio de consulta</Text>
     <Text style={{color:muted,marginVertical:8}}>Configura el nombre y los datos de contacto del servicio de consulta.</Text>
     <TextInput value={consultName} onChangeText={setConsultName} placeholder="Nombre del servicio" style={styles.input}/>
     <TextInput value={consultPhone} onChangeText={setConsultPhone} placeholder="Teléfono de contacto" keyboardType="phone-pad" style={styles.input}/>
     <TextInput value={consultUrl} onChangeText={setConsultUrl} placeholder="Enlace web o WhatsApp (https://...)" autoCapitalize="none" keyboardType="url" style={styles.input}/>
     {consultPhone.trim()||consultUrl.trim()?<TouchableOpacity style={styles.refresh} onPress={()=>{const target=consultUrl.trim()||`tel:${consultPhone.replace(/[^+0-9]/g,'')}`;Linking.openURL(target).catch(()=>Alert.alert('Servicio de consulta','No se pudo abrir el contacto configurado.'));}}><Text style={styles.white}>ABRIR SERVICIO DE CONSULTA</Text></TouchableOpacity>:null}
    </View>
    <View style={[styles.card,{backgroundColor:card}]}>
     <Text style={[styles.channelTitle,{color:fg,fontSize:font-2}]}>Aplicaciones y orden de Inicio</Text>
     <Text style={{color:muted,marginVertical:8}}>Activa los accesos y usa las flechas para cambiar su posición en la pantalla principal.</Text>
     {homeOrder.map(name=><View key={name} style={styles.row}><Text style={{color:fg,fontSize:font-4,flex:1}}>{name}</Text><TouchableOpacity onPress={()=>moveHomeApp(name,-1)}><Text style={styles.adjust}>↑</Text></TouchableOpacity><TouchableOpacity onPress={()=>moveHomeApp(name,1)}><Text style={styles.adjust}>↓</Text></TouchableOpacity><Switch value={visibleApps.includes(name)} onValueChange={enabled=>setVisibleApps(old=>enabled?[...old,name]:old.filter(x=>x!==name))}/></View>)}
     <TouchableOpacity style={[styles.refresh,{backgroundColor:'#245C91'}]} onPress={()=>{Launcher?.getInstalledApps().then(setApps).catch(()=>{});setHomePicker(true);}}><Text style={styles.white}>＋ AGREGAR APLICACIÓN A INICIO</Text></TouchableOpacity>
     {extraHomeApps.map((pkg,index)=><View key={pkg} style={styles.row}><Text style={{color:fg,flex:1}}>{apps.find(x=>x.packageName===pkg)?.label||pkg}</Text><TouchableOpacity onPress={()=>setExtraHomeApps(old=>{const next=[...old];if(index>0)[next[index-1],next[index]]=[next[index],next[index-1]];return next;})}><Text style={styles.adjust}>↑</Text></TouchableOpacity><TouchableOpacity onPress={()=>setExtraHomeApps(old=>{const next=[...old];if(index<next.length-1)[next[index+1],next[index]]=[next[index],next[index+1]];return next;})}><Text style={styles.adjust}>↓</Text></TouchableOpacity><TouchableOpacity onPress={()=>setExtraHomeApps(old=>old.filter(x=>x!==pkg))}><Text style={{color:'#C0392B',fontWeight:'900'}}>QUITAR</Text></TouchableOpacity></View>)}
    </View>
    <View style={[styles.card,{backgroundColor:card}]}>
     <Text style={[styles.channelTitle,{color:fg,fontSize:font-2}]}>Aplicaciones de los botones</Text>
     <Text style={{color:muted,marginVertical:8}}>Elige qué aplicación instalada abre cada botón. En “Automática” se usa la aplicación predeterminada del teléfono.</Text>
     {ASSIGNABLE.map(name=>{const a=apps.find(x=>x.packageName===appMap[name]);return <View key={name} style={styles.row}><View style={{flex:1}}><Text style={{color:fg,fontSize:font-4,fontWeight:'900'}}>{name}</Text><Text style={{color:muted,fontSize:13}} numberOfLines={1}>{a?a.label:appMap[name]?appMap[name]:'Automática'}</Text></View><TouchableOpacity style={styles.edit} onPress={()=>{setAppSearch('');setPickFor(name);}}><Text style={styles.white}>ELEGIR</Text></TouchableOpacity></View>;})}
    </View>
    <View style={[styles.card,{backgroundColor:card}]}>
     <Text style={[styles.channelTitle,{color:fg,fontSize:font-2}]}>Tamaño de letra</Text>
     <View style={styles.row}><TouchableOpacity onPress={()=>setFont(Math.max(18,font-2))}><Text style={styles.adjust}>A−</Text></TouchableOpacity><Text style={{color:fg,fontSize:font}}>{font}</Text><TouchableOpacity onPress={()=>setFont(Math.min(36,font+2))}><Text style={styles.adjust}>A+</Text></TouchableOpacity></View>
    </View>
    <View style={[styles.card,{backgroundColor:card}]}>
     <View style={styles.row}><Text style={[styles.channelTitle,{color:fg,flex:1,fontSize:font-2}]}>Leer nombres en voz alta</Text><Switch value={voice} onValueChange={setVoice}/></View>
     <View style={styles.row}><Text style={[styles.channelTitle,{color:fg,flex:1,fontSize:font-2}]}>Alto contraste</Text><Switch value={contrast} onValueChange={setContrast}/></View>
    </View>
   </ScrollView>
   <ScrollView style={{width}} contentContainerStyle={{padding:16}}>
    {title('WHATSAPP · CONVERSACIONES RECIENTES')}
    <Text style={{color:muted,marginBottom:12}}>Aquí se conservan las conversaciones detectadas en las notificaciones, incluso después de leerlas o descartarlas del panel de Android.</Text>
    <TouchableOpacity style={[styles.refresh,{backgroundColor:'#16834A'}]} onPress={once(()=>{void openWhatsApp();})}><Text style={styles.white}>ABRIR WHATSAPP</Text></TouchableOpacity>
    {whatsappNotes.length?whatsappNotes.map(noteRow):<View style={[styles.card,{backgroundColor:card}]}><Text style={{color:fg}}>Todavía no hay conversaciones registradas. Activa el acceso a notificaciones en la pestaña NOTIFICACIONES y WhatsApp irá guardando los avisos de tus chats.</Text></View>}
   </ScrollView>
  </ScrollView>
  <View style={[styles.nav,{height:54+insets.bottom,paddingBottom:insets.bottom}]}>{['INICIO','CONTACTOS','NOTIFICACIONES','VIDEOS','AJUSTES','WHATSAPP'].map((n,i)=><TouchableOpacity key={n} onPress={()=>twoTap('tab:'+i,'Pestaña '+n,()=>go(i))}><Text style={[styles.navText,{color:fg,fontSize:14}]}>{n}</Text></TouchableOpacity>)}</View>
  <Modal visible={contactChooser} transparent animationType="slide" onRequestClose={()=>{setContactChooser(false);setContactSlotIndex(null);}}><View style={styles.backdrop}><View style={[styles.modal,{backgroundColor:card,maxHeight:'88%'}]}><Text style={[styles.title,{color:fg,fontSize:24}]}>ELIGE DE TU AGENDA</Text><Text style={{color:muted,fontSize:17}}>Selecciona a la persona para este espacio.</Text><ScrollView>{contacts.map(c=><TouchableOpacity key={c.id} style={styles.appRow} onPress={()=>selectEmergencyContact(c)}>{c.photoUri?<Image source={{uri:c.photoUri}} style={styles.contactPhoto}/>:<View style={styles.contactAvatar}><Text style={styles.contactInitial}>{c.name[0]||'?'}</Text></View>}<Text style={{color:fg,fontSize:20,fontWeight:'900',flex:1}}>{c.name}</Text></TouchableOpacity>)}</ScrollView><TouchableOpacity style={styles.action} onPress={()=>{setContactChooser(false);setContactSlotIndex(null);}}><Text style={styles.white}>CERRAR</Text></TouchableOpacity></View></View></Modal>
  <Modal visible={actionChooser} transparent animationType="fade" onRequestClose={()=>{setActionChooser(false);setPendingContact(null);setContactSlotIndex(null);}}><View style={styles.backdrop}><View style={[styles.modal,{backgroundColor:card}]}><Text style={[styles.title,{color:fg,fontSize:24}]}>¿QUÉ HARÁ ESTE BOTÓN?</Text><Text style={{color:muted,fontSize:18,marginBottom:10}}>{pendingContact?.name||'Contacto seleccionado'}</Text>{([{id:'call',label:'☎ LLAMAR CON TELÉFONO'},{id:'wa-call',label:'🟢 LLAMADA POR WHATSAPP'},{id:'sms',label:'✉ MENSAJE SMS'},{id:'wa-message',label:'💬 MENSAJE POR WHATSAPP'}] as const).map(x=><TouchableOpacity key={x.id} style={[styles.refresh,{backgroundColor:'#245C91',padding:18}]} onPress={()=>saveEmergencyAction(x.id)}><Text style={styles.white}>{x.label}</Text></TouchableOpacity>)}</View></View></Modal>
  <Modal visible={homePicker} transparent animationType="slide" onRequestClose={()=>setHomePicker(false)}><View style={styles.backdrop}><View style={[styles.modal,{backgroundColor:card,maxHeight:'88%'}]}><Text style={[styles.title,{color:fg,fontSize:22}]}>AGREGAR A INICIO</Text><TextInput value={appSearch} onChangeText={setAppSearch} placeholder="Buscar aplicación instalada" style={styles.input}/><ScrollView style={{marginTop:8}}>{apps.filter(a=>!extraHomeApps.includes(a.packageName)&&(!appSearch.trim()||a.label.toLocaleLowerCase('es-MX').includes(appSearch.trim().toLocaleLowerCase('es-MX')))).map(a=><TouchableOpacity key={a.packageName} style={styles.appRow} onPress={()=>{setExtraHomeApps(old=>[...old,a.packageName]);setHomePicker(false);setAppSearch('');}}>{a.icon?<Image source={{uri:'data:image/png;base64,'+a.icon}} style={styles.appIconSmall}/>:<View style={styles.appIconSmall}/>}<Text style={{color:fg,fontSize:18,fontWeight:'800',flex:1}}>{a.label}</Text><Text style={{color:'#16834A',fontWeight:'900'}}>AGREGAR</Text></TouchableOpacity>)}</ScrollView><TouchableOpacity style={styles.action} onPress={()=>setHomePicker(false)}><Text style={styles.white}>CERRAR</Text></TouchableOpacity></View></View></Modal>
  <Modal visible={edit!==null} transparent animationType="fade" onRequestClose={()=>setEdit(null)}><View style={styles.backdrop}><View style={[styles.modal,{backgroundColor:card}]}><Text style={[styles.title,{color:fg,fontSize:25}]}>ELEGIR CANAL</Text><Text style={{color:muted,marginVertical:10}}>Paso 1: abre YouTube. Paso 2: entra al perfil del canal. Paso 3: toca Compartir y Copiar enlace. Paso 4: vuelve aquí y pega el enlace o @usuario. FerOs conservará esta ventana para que no pierdas la configuración.</Text><TouchableOpacity style={[styles.refresh,{backgroundColor:'#D52B2B'}]} onPress={()=>{const q=channelSearch.trim()||'canales';Linking.openURL('https://www.youtube.com/results?search_query='+encodeURIComponent(q)).catch(()=>{});}}><Text style={styles.white}>ABRIR YOUTUBE PARA ELEGIR</Text></TouchableOpacity><TextInput value={draft} onChangeText={setDraft} placeholder="Pega el enlace del canal o @usuario" autoCapitalize="none" autoCorrect={false} keyboardType="url" style={styles.input}/><View style={styles.row}><TouchableOpacity style={[styles.action,{backgroundColor:'#687582'}]} onPress={()=>setEdit(null)}><Text style={styles.white}>CANCELAR</Text></TouchableOpacity><TouchableOpacity style={styles.action} onPress={saveChannel}><Text style={styles.white}>GUARDAR</Text></TouchableOpacity></View>{edit!==null&&channels[edit]?<TouchableOpacity onPress={()=>{setChannels(old=>old.map((v,i)=>i===edit?'':v));setFeeds(old=>{const n={...old};delete n[edit];return n;});setFeedErrors(old=>{const n={...old};delete n[edit];return n;});setEdit(null);}}><Text style={{color:'#C0392B',textAlign:'center',fontWeight:'900',marginTop:16}}>ELIMINAR CANAL</Text></TouchableOpacity>:null}</View></View></Modal>
  <Modal visible={pickFor!==null} transparent animationType="slide" onRequestClose={()=>setPickFor(null)}><View style={styles.backdrop}><View style={[styles.modal,{backgroundColor:card,maxHeight:'88%'}]}><Text style={[styles.title,{color:fg,fontSize:22}]}>ELEGIR APP PARA {pickFor}</Text><TextInput value={appSearch} onChangeText={setAppSearch} placeholder="Buscar aplicación" style={styles.input}/>{apps.length===0?<Text style={{color:muted,marginTop:12}}>No se pudo leer la lista de aplicaciones.</Text>:null}<ScrollView style={{marginTop:8}}>{apps.filter(a=>!appSearch.trim()||a.label.toLocaleLowerCase('es-MX').includes(appSearch.trim().toLocaleLowerCase('es-MX'))).map(a=><TouchableOpacity key={a.packageName} style={styles.appRow} onPress={()=>{if(pickFor)setAppMap(old=>({...old,[pickFor]:a.packageName}));setPickFor(null);setAppSearch('');}}>{a.icon?<Image source={{uri:`data:image/png;base64,${a.icon}`}} style={styles.appIconSmall}/>:<View style={styles.appIconSmall}/>}<Text style={{color:fg,fontSize:18,fontWeight:'800',flex:1}} numberOfLines={1}>{a.label}</Text></TouchableOpacity>)}</ScrollView><View style={styles.row}><TouchableOpacity style={[styles.action,{backgroundColor:'#687582'}]} onPress={()=>setPickFor(null)}><Text style={styles.white}>CANCELAR</Text></TouchableOpacity><TouchableOpacity style={styles.action} onPress={()=>{if(pickFor)setAppMap(old=>{const n={...old};delete n[pickFor];return n;});setPickFor(null);}}><Text style={styles.white}>AUTOMÁTICA</Text></TouchableOpacity></View></View></View></Modal>
 </ImageBackground>;
}
const styles=StyleSheet.create({root:{flex:1},top:{height:42,paddingHorizontal:14,flexDirection:'row',justifyContent:'space-between',alignItems:'center'},clock:{alignItems:'center',paddingVertical:12},time:{fontSize:58,fontWeight:'900'},whatsapp:{backgroundColor:'#16834A',borderRadius:18,padding:15,flexDirection:'row',alignItems:'center',gap:14},whatsappText:{fontSize:25,color:'#FFF',fontWeight:'900'},section:{fontSize:18,fontWeight:'900',marginTop:16,marginBottom:8},card:{padding:16,borderRadius:16,marginBottom:12},grid:{flexDirection:'row',flexWrap:'wrap',gap:10,justifyContent:'space-between'},tile:{width:'48%',minHeight:158,borderRadius:22,alignItems:'center',justifyContent:'center'},glyph:{fontSize:58,color:'#FFF'},tileText:{fontSize:18,fontWeight:'900',color:'#FFF'},title:{fontSize:32,fontWeight:'900',marginTop:12,marginBottom:8},contact:{width:'48%',height:135,borderRadius:18,justifyContent:'center',alignItems:'center'},contactText:{fontSize:22,fontWeight:'900',color:'#FFF'},contactList:{gap:9,marginTop:12},contactCard:{minHeight:82,borderRadius:15,padding:10,flexDirection:'row',alignItems:'center',gap:12},contactAvatar:{width:58,height:58,borderRadius:29,backgroundColor:'#526B8A',alignItems:'center',justifyContent:'center',overflow:'hidden'},contactPhoto:{width:58,height:58,borderRadius:29},contactInitial:{fontSize:26,fontWeight:'900',color:'#FFF'},contactName:{fontWeight:'900'},callButton:{backgroundColor:'#16834A',paddingHorizontal:12,paddingVertical:13,borderRadius:11},channel:{padding:14,borderRadius:18,marginBottom:14},channelHead:{flexDirection:'row',alignItems:'center',gap:8},channelTitle:{fontSize:19,fontWeight:'900'},edit:{backgroundColor:'#245C91',padding:10,borderRadius:10},white:{color:'#FFF',fontWeight:'900'},video:{flexDirection:'row',alignItems:'center',gap:10,paddingVertical:12,borderTopWidth:1,borderTopColor:'#DCE3EA'},thumb:{width:112,height:66,borderRadius:9,backgroundColor:'#DCE3EA'},plusCard:{minHeight:142,borderRadius:18,borderWidth:2,borderStyle:'dashed',borderColor:'#9AA7B4',alignItems:'center',justifyContent:'center',marginBottom:14},plus:{fontSize:56,color:'#D52B2B',fontWeight:'900'},refresh:{backgroundColor:'#D52B2B',padding:14,borderRadius:12,alignItems:'center',marginBottom:14},nav:{height:54,backgroundColor:'#DCE3EA',flexDirection:'row',justifyContent:'space-around',alignItems:'center'},navText:{fontSize:15,fontWeight:'900'},input:{borderWidth:1,borderColor:'#AAB5C0',borderRadius:10,padding:12,fontSize:15,backgroundColor:'#FFF',color:'#17212B',marginTop:8},row:{flexDirection:'row',alignItems:'center',justifyContent:'space-between',gap:12,marginTop:12},adjust:{fontSize:26,color:'#1769AA',fontWeight:'900'},backdrop:{flex:1,backgroundColor:'rgba(0,0,0,.65)',justifyContent:'center',padding:22},modal:{padding:20,borderRadius:20},action:{flex:1,backgroundColor:'#245C91',padding:14,borderRadius:12,alignItems:'center'},appIcon:{width:76,height:76,borderRadius:16},appIconSmall:{width:44,height:44,borderRadius:10,backgroundColor:'#DCE3EA'},appRow:{flexDirection:'row',alignItems:'center',gap:12,paddingVertical:10,borderBottomWidth:1,borderBottomColor:'#DCE3EA'},note:{paddingVertical:10,borderBottomWidth:1,borderBottomColor:'#DCE3EA'}});
