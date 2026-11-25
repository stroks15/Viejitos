import React from 'react';
import {StyleSheet, Text, TouchableOpacity, View} from 'react-native';

type Props={torchOn:boolean;onAction:(action:string)=>void};
const ITEMS=[{id:'flash',label:'Linterna',icon:'🔦'},{id:'wifi',label:'Wi-Fi',icon:'◉'},{id:'bluetooth',label:'Bluetooth',icon:'ᛒ'},{id:'airplane',label:'Modo avión',icon:'✈'},{id:'brightness',label:'Brillo',icon:'☀'},{id:'volume',label:'Volumen',icon:'🔊'}];
export default function QuickControls({torchOn,onAction}:Props){
 return <View style={styles.grid}>{ITEMS.map(item=><TouchableOpacity key={item.id} accessibilityRole="button" style={[styles.tile,{backgroundColor:item.id==='flash'&&torchOn?'#B88716':'#477E91'}]} onPress={()=>onAction(item.id)}><Text style={styles.icon}>{item.icon}</Text><Text style={styles.label}>{item.id==='flash'&&torchOn?'Linterna encendida':item.label}</Text></TouchableOpacity>)}</View>;
}
const styles=StyleSheet.create({grid:{flexDirection:'row',flexWrap:'wrap',justifyContent:'space-between',gap:8},tile:{width:'31%',minHeight:100,borderRadius:20,alignItems:'center',justifyContent:'center',padding:8,elevation:3},icon:{fontSize:32,color:'#FFF'},label:{fontSize:15,fontWeight:'900',color:'#FFF',textAlign:'center'}});
