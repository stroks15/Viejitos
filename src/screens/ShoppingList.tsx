import React,{useEffect,useMemo,useRef,useState} from 'react';
import {Alert,ScrollView,Text,TextInput,TouchableOpacity,View} from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
type Item={id:string;name:string;qty:number;price:number;bought:boolean};
const KEY='feros.shopping.v1';
export default function ShoppingList({onSpeak}:{onSpeak?:(s:string)=>void}){
 const [budget,setBudget]=useState('500'),[items,setItems]=useState<Item[]>([]),[name,setName]=useState(''),[qty,setQty]=useState(1),[price,setPrice]=useState('0');
 const [editingId,setEditingId]=useState<string|null>(null),[editQty,setEditQty]=useState('1'),[editPrice,setEditPrice]=useState('0');
 const warned=useRef(false);
 useEffect(()=>{(async()=>{try{const raw=await AsyncStorage.getItem(KEY);if(raw){const v=JSON.parse(raw);if(Array.isArray(v.items))setItems(v.items);if(typeof v.budget==='string')setBudget(v.budget);}}catch{setItems([]);}})();},[]);
 useEffect(()=>{AsyncStorage.setItem(KEY,JSON.stringify({budget,items})).catch(()=>{});},[budget,items]);
 const total=useMemo(()=>items.filter(x=>x.bought).reduce((sum,x)=>sum+x.qty*x.price,0),[items]);
 const limit=Math.max(0,Number(budget)||0),remaining=limit-total,subtotal=qty*Math.max(0,Number(price)||0);
 useEffect(()=>{if(remaining<0&&!warned.current){warned.current=true;onSpeak?.('Cuidado, ya pasaste tu presupuesto');}if(remaining>=0)warned.current=false;},[remaining]);
 const add=()=>{if(!name.trim()){Alert.alert('Producto','Escribe el nombre del producto.');return;}setItems(v=>[{id:String(Date.now()),name:name.trim(),qty,price:Math.max(0,Number(price)||0),bought:true},...v]);setName('');setQty(1);setPrice('0');};
 const edit=(item:Item)=>{setEditingId(item.id);setEditQty(String(item.qty));setEditPrice(String(item.price));};
 return <ScrollView contentContainerStyle={{padding:16}}>
  <Text style={s.heading}>PRESUPUESTO MÁXIMO ($)</Text><TextInput value={budget} onChangeText={setBudget} keyboardType="decimal-pad" style={s.input}/>
  <View style={s.summary}><Text style={s.label}>TOTAL GASTADO</Text><Text style={s.total}>$ {total.toFixed(2)}</Text><Text style={[s.remaining,{color:remaining<0?'#C62828':remaining<limit*.2?'#D97706':'#16834A'}]}>DISPONIBLE: $ {remaining.toFixed(2)}</Text>{remaining<0?<Text style={s.warning}>⚠ CUIDADO, YA PASASTE TU PRESUPUESTO</Text>:null}</View>
  <Text style={s.heading}>AGREGAR PRODUCTO</Text><TextInput value={name} onChangeText={setName} placeholder="Nombre (ej. Leche)" style={s.input}/>
  <Text style={s.label}>PIEZAS</Text><View style={s.row}><TouchableOpacity style={s.step} onPress={()=>setQty(v=>Math.max(1,v-1))}><Text style={s.stepText}>−</Text></TouchableOpacity><Text style={s.qty}>{qty}</Text><TouchableOpacity style={s.step} onPress={()=>setQty(v=>v+1)}><Text style={s.stepText}>＋</Text></TouchableOpacity></View>
  <Text style={s.label}>PRECIO POR PIEZA ($)</Text><TextInput value={price} onChangeText={setPrice} keyboardType="decimal-pad" style={s.input}/><Text style={s.subtotal}>Subtotal: $ {subtotal.toFixed(2)}</Text>
  <TouchableOpacity style={s.add} onPress={add}><Text style={s.addText}>＋ AGREGAR A LA CUENTA</Text></TouchableOpacity>
  <TouchableOpacity style={s.reset} onPress={()=>Alert.alert('Nueva compra','¿Limpiar la lista y comenzar otra compra?',[{text:'Cancelar',style:'cancel'},{text:'Nueva compra',style:'destructive',onPress:()=>setItems([])}])}><Text style={s.resetText}>NUEVA COMPRA</Text></TouchableOpacity>
  <Text style={s.heading}>MI LISTA</Text>
  {items.map(item=><View key={item.id} style={s.item}>
   <TouchableOpacity style={{flex:1}} onPress={()=>setItems(v=>v.map(x=>x.id===item.id?{...x,bought:!x.bought}:x))}><Text style={[s.itemName,item.bought&&s.strike]}>{item.bought?'✓ ':''}{item.name}</Text><Text style={s.meta}>{item.qty} × $ {item.price.toFixed(2)} = $ {(item.qty*item.price).toFixed(2)}</Text></TouchableOpacity>
   <TouchableOpacity onPress={()=>edit(item)} style={s.small}><Text style={s.smallText}>EDITAR</Text></TouchableOpacity>
   <TouchableOpacity onPress={()=>setItems(v=>v.filter(x=>x.id!==item.id))} style={s.small}><Text style={s.smallText}>✕</Text></TouchableOpacity>
   {editingId===item.id?<View style={{width:'100%',flexDirection:'row',gap:8,marginTop:8}}><TextInput value={editQty} onChangeText={setEditQty} keyboardType="number-pad" style={[s.input,{flex:1}]}/><TextInput value={editPrice} onChangeText={setEditPrice} keyboardType="decimal-pad" style={[s.input,{flex:1}]}/><TouchableOpacity style={s.add} onPress={()=>{setItems(v=>v.map(x=>x.id===item.id?{...x,qty:Math.max(1,Number(editQty)||1),price:Math.max(0,Number(editPrice)||0)}:x));setEditingId(null);}}><Text style={s.addText}>GUARDAR</Text></TouchableOpacity></View>:null}
  </View>)}
 </ScrollView>;
}
const s:any={heading:{fontSize:22,fontWeight:'900',color:'#17354B',marginVertical:12},input:{backgroundColor:'#FFF',borderWidth:2,borderColor:'#A9C8D8',borderRadius:15,padding:15,fontSize:23,color:'#17354B',marginVertical:5},summary:{backgroundColor:'#E8F8EF',borderRadius:22,padding:18,marginVertical:12},label:{fontSize:16,fontWeight:'900',color:'#38566B',marginTop:10},total:{fontSize:36,fontWeight:'900',color:'#17354B'},remaining:{fontSize:25,fontWeight:'900',marginTop:5},warning:{color:'#C62828',fontSize:18,fontWeight:'900'},row:{flexDirection:'row',alignItems:'center',gap:20,justifyContent:'center',marginVertical:8},step:{backgroundColor:'#315D86',borderRadius:16,width:64,height:60,alignItems:'center',justifyContent:'center'},stepText:{color:'#FFF',fontSize:36,fontWeight:'900'},qty:{fontSize:32,fontWeight:'900',color:'#17354B'},subtotal:{fontSize:24,fontWeight:'900',color:'#17354B',marginVertical:10},add:{backgroundColor:'#16834A',borderRadius:18,padding:14,alignItems:'center'},addText:{color:'#FFF',fontWeight:'900',fontSize:17},reset:{padding:15,alignItems:'center'},resetText:{color:'#B33A35',fontWeight:'900'},item:{backgroundColor:'#FFF',borderRadius:15,padding:14,marginBottom:10,flexDirection:'row',alignItems:'center',gap:8,flexWrap:'wrap'},itemName:{fontSize:20,fontWeight:'900',color:'#17354B'},strike:{textDecorationLine:'line-through',color:'#77838B'},meta:{fontSize:16,color:'#526B7A'},small:{backgroundColor:'#315D86',padding:10,borderRadius:10},smallText:{color:'#FFF',fontWeight:'900'}};
