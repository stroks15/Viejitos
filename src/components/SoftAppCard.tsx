import React, {useRef} from 'react';
import {Image, Pressable, StyleSheet, Text, Animated, Vibration, View} from 'react-native';
type Props={label:string;glyph:string;color:string;icon?:string;onPress:()=>void;onSpeak?:()=>void;large?:boolean};
export default function SoftAppCard({label,glyph,color,icon,onPress,onSpeak,large=false}:Props){
 const scale=useRef(new Animated.Value(1)).current;
 const down=()=>{Vibration.vibrate(12);Animated.spring(scale,{toValue:0.975,useNativeDriver:true,speed:35,bounciness:3}).start();onSpeak?.();};
 const up=()=>Animated.spring(scale,{toValue:1,useNativeDriver:true,speed:30,bounciness:5}).start();
 return <Animated.View style={[styles.outer,{transform:[{scale}]}]}><Pressable onPressIn={down} onPressOut={up} onPress={onPress} style={[styles.card,{backgroundColor:color,minHeight:large?138:154}]}><View style={styles.iconWell}>{icon?<Image source={{uri:icon.startsWith('data:')?icon:'data:image/png;base64,'+icon}} style={styles.icon}/>:<Text style={styles.glyph}>{glyph}</Text>}</View><Text style={styles.label} numberOfLines={2}>{label}</Text></Pressable></Animated.View>;
}
const styles=StyleSheet.create({outer:{width:'48%',marginBottom:12},card:{borderRadius:24,padding:14,alignItems:'center',justifyContent:'center',elevation:5,shadowColor:'#18354D',shadowOpacity:.15,shadowRadius:12,shadowOffset:{width:0,height:5},borderWidth:1,borderColor:'rgba(255,255,255,.5)'},iconWell:{width:78,height:78,borderRadius:25,backgroundColor:'rgba(255,255,255,.22)',alignItems:'center',justifyContent:'center',marginBottom:8},icon:{width:62,height:62,borderRadius:17},glyph:{fontSize:55,color:'#FFF',fontWeight:'900',textShadowColor:'rgba(0,0,0,.18)',textShadowRadius:4,textShadowOffset:{width:1,height:3}},label:{fontSize:21,fontWeight:'900',color:'#FFF',textAlign:'center'}});
