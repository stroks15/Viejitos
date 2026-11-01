import React, {useEffect, useRef, useState} from 'react';
import {ActivityIndicator, Alert, BackHandler, Dimensions, Image, Linking, Modal, ScrollView, StatusBar, StyleSheet, Switch, Text, TextInput, TouchableOpacity, View} from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import Tts from 'react-native-tts';

type Video={videoId:string;title:string;publishedAt:string;thumbnail:string};
type Feed={channelTitle:string;videos:Video[]};
const MAX_CHANNELS=3;
const QUICK_LINKS=4;
const VIDEOS_PER_CHANNEL=3;
const DEFAULT_YOUTUBE_ENDPOINT='https://yxjyqpnvgtkdaaukackt.supabase.co/functions/v1/youtube-feed';
const DEFAULT_SUPABASE_ANON='eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inl4anlxcG52Z3RrZGFhdWthY2t0Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTI1NTQ0NjYsImV4cCI6MjEwODEzMDQ2Nn0.dezLOZfa9BDrWjf6WOuH_4FtZB0MpL_Ulm3NRD8XJQQ';
const EMPTY_CHANNELS=Array.from({length:MAX_CHANNELS},()=> '');
const COLORS=['#1976D2','#168C82','#7955A5','#D18B16','#C04F36','#D52B2B','#526B8A','#59636F'];
const APPS=[['LLAMAR','☎'],['MENSAJES','✉'],['CONTACTOS','●'],['WHATSAPP','◉'],['LINTERNA','☼'],['RADIO','♫'],['YOUTUBE','▶'],['CALCULADORA','▦'],['CÁMARA','▣']];
const say=(s:string)=>{Tts.setDefaultLanguage('es-MX').catch(()=>{});Tts.speak(s);};

export default function App(){
 const [page,setPage]=useState(0),[channels,setChannels]=useState(EMPTY_CHANNELS),[feeds,setFeeds]=useState<Record<number,Feed>>({});
 const [endpoint,setEndpoint]=useState(DEFAULT_YOUTUBE_ENDPOINT),[anon,setAnon]=useState(DEFAULT_SUPABASE_ANON),[busy,setBusy]=useState(false),[feedErrors,setFeedErrors]=useState<Record<number,string>>({}),[edit,setEdit]=useState<number|null>(null),[draft,setDraft]=useState('');
 const [font,setFont]=useState(24),[contrast,setContrast]=useState(false),[voice,setVoice]=useState(true),[time,setTime]=useState(new Date());
 const [quickLinks,setQuickLinks]=useState(['','','','']);
 const [consultName,setConsultName]=useState(''),[consultPhone,setConsultPhone]=useState(''),[consultUrl,setConsultUrl]=useState('');
 const [visibleApps,setVisibleApps]=useState(APPS.map(a=>a[0]));
 const [settingsLoaded,setSettingsLoaded]=useState(false);
 const scroll=useRef<ScrollView>(null),width=Dimensions.get('window').width;
 useEffect(()=>{const t=setInterval(()=>setTime(new Date()),1000);return()=>clearInterval(t);},[]);
 useEffect(()=>{AsyncStorage.multiGet(['yt.channels','yt.endpoint','yt.anon','font','contrast','voice','quickLinks','consultName','consultPhone','consultUrl','visibleApps']).then(rows=>{const v=Object.fromEntries(rows);if(v['yt.channels']){const saved=JSON.parse(v['yt.channels']!);setChannels(Array.from({length:MAX_CHANNELS},(_,i)=>typeof saved[i]==='string'?saved[i]:''));}if(v['yt.endpoint'])setEndpoint(v['yt.endpoint']!);if(v['yt.anon'])setAnon(v['yt.anon']!);if(v.font)setFont(Number(v.font));if(v.contrast)setContrast(v.contrast==='true');if(v.voice)setVoice(v.voice==='true');if(v.quickLinks){const links=JSON.parse(v.quickLinks);setQuickLinks(Array.from({length:QUICK_LINKS},(_,i)=>typeof links[i]==='string'?links[i]:''));}if(v.consultName!==null)setConsultName(v.consultName||'');if(v.consultPhone!==null)setConsultPhone(v.consultPhone||'');if(v.consultUrl!==null)setConsultUrl(v.consultUrl||'');if(v.visibleApps){const saved=JSON.parse(v.visibleApps);setVisibleApps(APPS.map(a=>a[0]).filter(name=>saved.includes(name)));}setSettingsLoaded(true);}).catch(()=>setSettingsLoaded(true));},[]);
 useEffect(()=>{if(!settingsLoaded)return;AsyncStorage.multiSet([['yt.channels',JSON.stringify(channels)],['yt.endpoint',endpoint],['yt.anon',anon],['font',String(font)],['contrast',String(contrast)],['voice',String(voice)],['quickLinks',JSON.stringify(quickLinks)],['consultName',consultName],['consultPhone',consultPhone],['consultUrl',consultUrl],['visibleApps',JSON.stringify(visibleApps)]]).catch(()=>{});},[settingsLoaded,channels,endpoint,anon,font,contrast,voice,quickLinks,consultName,consultPhone,consultUrl,visibleApps]);
 useEffect(()=>{const sub=BackHandler.addEventListener('hardwareBackPress',()=>{if(page){go(0);return true;}return true;});return()=>sub.remove();},[page]);
 const go=(n:number)=>{const p=Math.max(0,Math.min(4,n));setPage(p);scroll.current?.scrollTo({x:p*width,animated:true});};
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
 const tile=(name:string,glyph:string,i:number)=><TouchableOpacity key={name} style={[styles.tile,{backgroundColor:COLORS[i%COLORS.length]}]} onPress={()=>{if(voice)say(name);if(name==='CONTACTOS')go(1);else if(name==='WHATSAPP')go(2);else Alert.alert(name,`Se abrirá ${name} cuando se conecte su integración nativa.`);}}><Text style={styles.glyph}>{glyph}</Text><Text style={[styles.tileText,{fontSize:Math.max(18,font-6)}]}>{name}</Text></TouchableOpacity>;
 return <View style={[styles.root,{backgroundColor:bg}]}>
  <StatusBar backgroundColor={bg} barStyle={dark?'light-content':'dark-content'}/>
  <View style={styles.top}><Text style={{color:fg,fontWeight:'900'}}>▰ Batería   ◉ Wi-Fi   ▥ Datos   ᛒ Bluetooth</Text><Text style={{color:fg,fontWeight:'900'}}>{page+1}/5</Text></View>
  <ScrollView ref={scroll} horizontal pagingEnabled scrollEnabled showsHorizontalScrollIndicator={false}>
   <ScrollView style={{width}} contentContainerStyle={{padding:16}}>
    <View style={styles.clock}><Text style={[styles.time,{color:fg}]}>{time.toLocaleTimeString('es-MX',{hour:'2-digit',minute:'2-digit',hour12:true})}</Text><Text style={{color:muted,fontWeight:'900'}}>{time.toLocaleDateString('es-MX',{weekday:'long',day:'numeric',month:'long'}).toUpperCase()}</Text></View>
    <TouchableOpacity style={styles.whatsapp} onPress={()=>{if(voice)say('WhatsApp');Alert.alert('WhatsApp','Se abrirá WhatsApp al completar la integración nativa.')}}><Text style={{fontSize:34,color:'#FFF'}}>◉</Text><Text style={styles.whatsappText}>WHATSAPP</Text></TouchableOpacity>
    <Text style={[styles.section,{color:fg}]}>NOTIFICACIONES</Text><View style={[styles.card,{backgroundColor:card}]}><Text style={{color:fg}}>🔔 Las notificaciones aparecerán aquí cuando se habilite el permiso de Android.</Text></View>
    <Text style={[styles.section,{color:fg}]}>APLICACIONES</Text><View style={styles.grid}>{APPS.filter(a=>visibleApps.includes(a[0])).map((a,i)=>tile(a[0],a[1],i))}</View>
    <Text style={[styles.section,{color:fg}]}>ENLACES RÁPIDOS</Text>{consultPhone.trim()||consultUrl.trim()?<TouchableOpacity style={[styles.card,{backgroundColor:card}]} onPress={()=>{const target=consultUrl.trim()||`tel:${consultPhone.replace(/[^+0-9]/g,'')}`;Linking.openURL(target).catch(()=>Alert.alert('Servicio de consulta','No se pudo abrir el contacto configurado.'));}}><Text style={{color:fg,fontSize:font,fontWeight:'900'}}>{consultName.trim()||'SERVICIO DE CONSULTA'}  ↗</Text><Text style={{color:muted}}>{consultPhone}</Text></TouchableOpacity>:null}{quickLinks.map((url,i)=>url.trim()?<TouchableOpacity key={i} style={[styles.card,{backgroundColor:card}]} onPress={()=>Linking.openURL(url.trim()).catch(()=>Alert.alert('Enlace','No se pudo abrir este enlace.'))}><Text style={{color:fg,fontSize:font,fontWeight:'800'}}>ENLACE {i+1}  ↗</Text><Text style={{color:muted}} numberOfLines={1}>{url}</Text></TouchableOpacity>:null)}
   </ScrollView>
   <ScrollView style={{width}} contentContainerStyle={{padding:16}}>{title('CONTACTOS')}<Text style={{color:muted,marginBottom:14}}>24 espacios · seis por página</Text><View style={styles.grid}>{Array.from({length:6},(_,i)=><TouchableOpacity key={i} style={[styles.contact,{backgroundColor:COLORS[i%4]}]} onPress={()=>say(`Contacto ${i+1}`)}><Text style={[styles.contactText,{fontSize:font-2}]}>Contacto {i+1}</Text></TouchableOpacity>)}</View><Text style={{color:muted,marginTop:14}}>La agenda completa se conectará a los contactos del teléfono.</Text></ScrollView>
   <ScrollView style={{width}} contentContainerStyle={{padding:16}}>{title('WHATSAPP')}<Text style={{color:muted}}>Conversaciones recientes disponibles al conceder acceso a notificaciones.</Text><View style={[styles.card,{backgroundColor:card,marginTop:16}]}><Text style={{color:fg}}>Aquí aparecerán las conversaciones recibidas, con su nombre y vista previa cuando Android las permita.</Text></View></View>
   <ScrollView style={{width}} contentContainerStyle={{padding:16}}>
    {title('VIDEOS')}
    <Text style={{color:muted,marginBottom:14}}>Agrega hasta tres canales. Cada espacio ＋ acepta un enlace y muestra automáticamente sus tres videos más recientes, primero el nuevo y después los dos anteriores.</Text>
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
      <TouchableOpacity key={i} style={[styles.plusCard,{backgroundColor:card}]} onPress={()=>{setEdit(i);setDraft('');}}>
       <Text style={styles.plus}>＋</Text>
       <Text style={[styles.channelTitle,{color:fg}]}>AGREGAR CANAL {i+1}</Text>
       <Text style={{color:muted}}>Pulsa ＋ y pega el enlace de YouTube</Text>
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
     <Text style={[styles.channelTitle,{color:fg,fontSize:font-2}]}>Aplicaciones visibles en Inicio</Text>
     <Text style={{color:muted,marginVertical:8}}>Activa o desactiva los accesos que se muestran en la pantalla principal.</Text>
     {APPS.map(([name])=><View key={name} style={styles.row}><Text style={{color:fg,fontSize:font-4,flex:1}}>{name}</Text><Switch value={visibleApps.includes(name)} onValueChange={enabled=>setVisibleApps(old=>enabled?[...old,name]:old.filter(x=>x!==name))}/></View>)}
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
  </ScrollView>
  <View style={styles.nav}>{['INICIO','CONTACTOS','WHATSAPP','VIDEOS','AJUSTES'].map((n,i)=><TouchableOpacity key={n} onPress={()=>go(i)}><Text style={[styles.navText,{color:fg}]}>{n}</Text></TouchableOpacity>)}</View>
  <Modal visible={edit!==null} transparent animationType="fade" onRequestClose={()=>setEdit(null)}><View style={styles.backdrop}><View style={[styles.modal,{backgroundColor:card}]}><Text style={[styles.title,{color:fg,fontSize:25}]}>AGREGAR ENLACE</Text><Text style={{color:muted,marginVertical:10}}>Pega el enlace de un canal de YouTube. Mostraremos sus tres videos más recientes.</Text><TextInput value={draft} onChangeText={setDraft} placeholder="https://youtube.com/@canal" autoCapitalize="none" autoCorrect={false} keyboardType="url" style={styles.input}/><View style={styles.row}><TouchableOpacity style={[styles.action,{backgroundColor:'#687582'}]} onPress={()=>setEdit(null)}><Text style={styles.white}>CANCELAR</Text></TouchableOpacity><TouchableOpacity style={styles.action} onPress={saveChannel}><Text style={styles.white}>GUARDAR</Text></TouchableOpacity></View>{edit!==null&&channels[edit]?<TouchableOpacity onPress={()=>{setChannels(old=>old.map((v,i)=>i===edit?'':v));setFeeds(old=>{const n={...old};delete n[edit];return n;});setFeedErrors(old=>{const n={...old};delete n[edit];return n;});setEdit(null);}}><Text style={{color:'#C0392B',textAlign:'center',fontWeight:'900',marginTop:16}}>ELIMINAR CANAL</Text></TouchableOpacity>:null}</View></View></Modal>
 </View>;
}
const styles=StyleSheet.create({root:{flex:1},top:{height:42,paddingHorizontal:14,flexDirection:'row',justifyContent:'space-between',alignItems:'center'},clock:{alignItems:'center',paddingVertical:12},time:{fontSize:58,fontWeight:'900'},whatsapp:{backgroundColor:'#16834A',borderRadius:18,padding:15,flexDirection:'row',alignItems:'center',gap:14},whatsappText:{fontSize:25,color:'#FFF',fontWeight:'900'},section:{fontSize:18,fontWeight:'900',marginTop:16,marginBottom:8},card:{padding:16,borderRadius:16,marginBottom:12},grid:{flexDirection:'row',flexWrap:'wrap',gap:10,justifyContent:'space-between'},tile:{width:'48%',minHeight:108,borderRadius:18,alignItems:'center',justifyContent:'center'},glyph:{fontSize:36,color:'#FFF'},tileText:{fontSize:18,fontWeight:'900',color:'#FFF'},title:{fontSize:32,fontWeight:'900',marginTop:12,marginBottom:8},contact:{width:'48%',height:135,borderRadius:18,justifyContent:'center',alignItems:'center'},contactText:{fontSize:22,fontWeight:'900',color:'#FFF'},channel:{padding:14,borderRadius:18,marginBottom:14},channelHead:{flexDirection:'row',alignItems:'center',gap:8},channelTitle:{fontSize:19,fontWeight:'900'},edit:{backgroundColor:'#245C91',padding:10,borderRadius:10},white:{color:'#FFF',fontWeight:'900'},video:{flexDirection:'row',alignItems:'center',gap:10,paddingVertical:12,borderTopWidth:1,borderTopColor:'#DCE3EA'},thumb:{width:112,height:66,borderRadius:9,backgroundColor:'#DCE3EA'},plusCard:{minHeight:142,borderRadius:18,borderWidth:2,borderStyle:'dashed',borderColor:'#9AA7B4',alignItems:'center',justifyContent:'center',marginBottom:14},plus:{fontSize:56,color:'#D52B2B',fontWeight:'900'},refresh:{backgroundColor:'#D52B2B',padding:14,borderRadius:12,alignItems:'center',marginBottom:14},nav:{height:54,backgroundColor:'#DCE3EA',flexDirection:'row',justifyContent:'space-around',alignItems:'center'},navText:{fontSize:10,fontWeight:'900'},input:{borderWidth:1,borderColor:'#AAB5C0',borderRadius:10,padding:12,fontSize:15,backgroundColor:'#FFF',color:'#17212B',marginTop:8},row:{flexDirection:'row',alignItems:'center',justifyContent:'space-between',gap:12,marginTop:12},adjust:{fontSize:26,color:'#1769AA',fontWeight:'900'},backdrop:{flex:1,backgroundColor:'rgba(0,0,0,.65)',justifyContent:'center',padding:22},modal:{padding:20,borderRadius:20},action:{flex:1,backgroundColor:'#245C91',padding:14,borderRadius:12,alignItems:'center'}});
