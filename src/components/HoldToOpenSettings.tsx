import React, {useRef} from 'react';
import {Animated, Pressable, StyleSheet, Text, View} from 'react-native';

type Props={onUnlock:()=>void; color?:string};
export default function HoldToOpenSettings({onUnlock,color='#26384A'}:Props){
 const progress=useRef(new Animated.Value(0)).current; const timer=useRef<ReturnType<typeof setTimeout>|null>(null); const unlocked=useRef(false);
 const start=()=>{unlocked.current=false;progress.setValue(0);Animated.timing(progress,{toValue:1,duration:2000,useNativeDriver:false}).start();timer.current=setTimeout(()=>{unlocked.current=true;onUnlock();},2000);};
 const stop=()=>{if(timer.current)clearTimeout(timer.current);timer.current=null;if(!unlocked.current)progress.stopAnimation(()=>progress.setValue(0));};
 return <Pressable accessibilityRole="button" accessibilityLabel="Mantén presionado dos segundos para abrir Familia y Ajustes" onPressIn={start} onPressOut={stop} style={styles.wrap}><Text style={[styles.label,{color}]}>Familia / Ajustes · Mantén 2 segundos</Text><View style={styles.track}><Animated.View style={[styles.fill,{width:progress.interpolate({inputRange:[0,1],outputRange:['0%','100%']})}]}/></View></Pressable>;
}
const styles=StyleSheet.create({wrap:{padding:10,alignItems:'center'},label:{fontSize:13,fontWeight:'700'},track:{height:5,width:180,backgroundColor:'#DCE3EA',borderRadius:5,overflow:'hidden',marginTop:5},fill:{height:5,backgroundColor:'#168C72'}});
