import React from 'react';
import {StyleSheet, Text, TouchableOpacity, View} from 'react-native';
import {assessMessage} from '../utils/scamDetector';
import type {SmsMessage} from '../types/family';

type Props={messages:SmsMessage[];foreground:string;muted:string;fontSize:number;onRead:(text:string)=>void};
export default function SmsInbox({messages,foreground,muted,fontSize,onRead}:Props){
 if(!messages.length)return <Text style={{color:muted}}>Todavía no hay SMS recibidos en esta bandeja.</Text>;
 return <>{messages.map(message=>{const risk=assessMessage(message.text);return <View key={message.id} style={[styles.card,{borderWidth:risk.risky?2:0,borderColor:'#D71920'}]}>{risk.risky?<Text style={styles.warning}>⚠ Cuidado: posible estafa</Text>:null}<Text style={[styles.sender,{color:foreground}]}>{message.sender}</Text><Text style={{color:foreground,fontSize:Math.max(17,fontSize-6)}}>{risk.safeText}</Text>{risk.reasons.map(reason=><Text key={reason} style={styles.reason}>{reason}</Text>)}<TouchableOpacity style={styles.read} onPress={()=>onRead('Mensaje de '+message.sender+'. '+risk.safeText)}><Text style={styles.readText}>🔊 LEER EN VOZ ALTA</Text></TouchableOpacity></View>;})}</>;
}
const styles=StyleSheet.create({card:{padding:16,borderRadius:18,marginBottom:12,backgroundColor:'#FFF'},warning:{color:'#D71920',fontSize:19,fontWeight:'900',marginBottom:6},sender:{fontSize:20,fontWeight:'900',marginBottom:5},reason:{color:'#D71920',fontSize:13,fontWeight:'800',marginTop:4},read:{backgroundColor:'#315D86',borderRadius:12,padding:14,alignItems:'center',marginTop:10},readText:{color:'#FFF',fontWeight:'900'}});
