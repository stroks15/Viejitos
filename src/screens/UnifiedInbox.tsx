import React from 'react';
import {StyleSheet, Text, TouchableOpacity, View} from 'react-native';
import {assessMessage} from '../utils/scamDetector';

export type UnifiedMessage = {
  id: string;
  sender: string;
  text: string;
  timestamp: number;
  source: 'SMS' | 'WHATSAPP';
};

type Props = {
  messages: UnifiedMessage[];
  foreground: string;
  muted: string;
  fontSize: number;
  onRead: (text: string) => void;
  onOpen: (message: UnifiedMessage) => void;
};

export default function UnifiedInbox({messages, foreground, muted, fontSize, onRead, onOpen}: Props) {
  if (!messages.length) return <Text style={{color: muted}}>Todavía no hay mensajes recibidos. Activa los permisos de SMS y notificaciones para llenar esta bandeja.</Text>;
  return <View>{messages.map(message => {
    const risk = assessMessage(message.text);
    return <View key={message.id} style={[styles.card, {borderWidth: risk.risky ? 2 : 1, borderColor: risk.risky ? '#D71920' : '#DCE3EA'}]}>
      <View style={styles.header}>
        <Text style={[styles.source, {backgroundColor: message.source === 'SMS' ? '#315D86' : '#16834A'}]}>{message.source}</Text>
        <Text style={[styles.date, {color: muted}]}>{new Date(message.timestamp).toLocaleString('es-MX', {day:'2-digit', month:'short', hour:'2-digit', minute:'2-digit'})}</Text>
      </View>
      {risk.risky ? <Text style={styles.warning}>⚠ Posible intento de fraude</Text> : null}
      <Text style={[styles.sender, {color: foreground}]}>{message.sender}</Text>
      <Text style={{color: foreground, fontSize: Math.max(17, fontSize - 6)}}>{risk.safeText}</Text>
      {risk.reasons.map(reason => <Text key={reason} style={styles.reason}>{reason}</Text>)}
      <View style={styles.actions}>
        <TouchableOpacity style={styles.action} onPress={() => onRead('Mensaje de ' + message.sender + '. ' + risk.safeText)}><Text style={styles.actionText}>🔊 LEER</Text></TouchableOpacity>
        <TouchableOpacity style={[styles.action, {backgroundColor:'#16834A'}]} onPress={() => onOpen(message)}><Text style={styles.actionText}>ABRIR {message.source}</Text></TouchableOpacity>
      </View>
    </View>;
  })}</View>;
}

const styles = StyleSheet.create({
  card:{backgroundColor:'#FFF',padding:15,borderRadius:18,marginBottom:12},
  header:{flexDirection:'row',justifyContent:'space-between',alignItems:'center',marginBottom:7},
  source:{overflow:'hidden',borderRadius:8,paddingHorizontal:10,paddingVertical:5,color:'#FFF',fontWeight:'900',fontSize:12},
  date:{fontSize:12,fontWeight:'800'},
  warning:{color:'#D71920',fontWeight:'900',fontSize:18,marginBottom:5},
  sender:{fontSize:20,fontWeight:'900',marginBottom:5},
  reason:{color:'#D71920',fontSize:13,fontWeight:'800',marginTop:4},
  actions:{flexDirection:'row',gap:9,marginTop:11},
  action:{flex:1,backgroundColor:'#315D86',borderRadius:11,padding:12,alignItems:'center'},
  actionText:{color:'#FFF',fontWeight:'900'}
});
