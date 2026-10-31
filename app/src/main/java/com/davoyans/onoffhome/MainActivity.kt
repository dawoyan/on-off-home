package com.davoyans.onoffhome

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private enum class DeviceState { ON, OFF, PENDING, UNKNOWN, ERROR }
private data class Room(val id:String=UUID.randomUUID().toString(), val name:String, val x:Float, val y:Float, val w:Float, val h:Float)
private data class Circuit(val id:String=UUID.randomUUID().toString(), val name:String, val purpose:String, val panel:String="", val roomIds:Set<String>, val provider:String="Home Assistant", val entity:String="switch.example", val state:DeviceState=DeviceState.UNKNOWN, val detail:String="Not yet refreshed")

class MainActivity: ComponentActivity() { override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { MaterialTheme(colorScheme = lightColorScheme(primary=Color(0xFF0B6E4F), secondary=Color(0xFF2E6F9E))) { HomeApp(this) } } } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun HomeApp(context: Context) {
    val store = remember { Store(context) }; var rooms by remember { mutableStateOf(store.rooms()) }; var circuits by remember { mutableStateOf(store.circuits(rooms)) }
    var edit by remember { mutableStateOf(false) }; var russian by remember { mutableStateOf(store.russian()) }; var selected by remember { mutableStateOf<Room?>(null) }; var editRoom by remember { mutableStateOf<Room?>(null) }; var confirm by remember { mutableStateOf<Circuit?>(null) }; var deviceEditor by remember { mutableStateOf(false) }; val scope=rememberCoroutineScope()
    fun persist() { store.save(rooms,circuits) }
    Scaffold(containerColor=Color(0xFFF7F9F8), topBar={ TopAppBar(title={Column { Text(if(russian) "Умный дом" else "Home control",fontWeight=FontWeight.Bold); Text(if(edit) if(russian) "Редактирование — управление отключено" else "Edit plan — controls are disabled" else if(russian) "Управление" else "Control mode",fontSize=12.sp,color=Color.Gray) }}, actions={ TextButton(onClick={russian=!russian;store.saveRussian(russian)}) { Text(if(russian) "ENG" else "РУС") }; TextButton(onClick={deviceEditor=true}) { Icon(Icons.Default.Settings,null); Text(if(russian) " Устройства" else " Devices") }; TextButton(onClick={edit=!edit}) { Icon(if(edit) Icons.Default.Home else Icons.Default.Edit,null); Spacer(Modifier.width(5.dp)); Text(if(edit) if(russian) "Готово" else "Done" else if(russian) "План" else "Edit") } }) }, floatingActionButton={ if(edit) FloatingActionButton(onClick={ editRoom=Room(name=if(russian)"Новая комната" else "New room",x=.1f,y=.1f,w=.3f,h=.2f) }) { Icon(Icons.Default.Add,"Add room") } }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            StatusLegend()
            Plan(rooms, circuits, edit, onRoom={ room -> if(edit) editRoom=room else selected=room }, onMove={ r,dx,dy -> rooms=rooms.map { if(it.id==r.id) it.copy(x=(it.x+dx).coerceIn(0f, .8f),y=(it.y+dy).coerceIn(0f,.8f)) else it }; persist() })
            Text(if(russian) "Нажмите комнату, чтобы увидеть её линии" else "Tap a room for its circuits", Modifier.padding(16.dp), color=Color(0xFF56635E),fontSize=13.sp)
        }
    }
    selected?.let { room -> RoomSheet(room, circuits.filter { room.id in it.roomIds }, onDismiss={selected=null}, onAction={c,target -> if(target==DeviceState.OFF) confirm=c else { circuits=circuits.map { if(it.id==c.id) it.copy(state=DeviceState.PENDING,detail="Command sent — waiting for report") else it }; persist(); scope.launch { delay(900); circuits=circuits.map { if(it.id==c.id) it.copy(state=DeviceState.ON,detail="Reported just now") else it }; persist() } } }) }
    editRoom?.let { room -> RoomEditor(room, onDismiss={editRoom=null}, onSave={ updated -> rooms=if(rooms.any{it.id==updated.id}) rooms.map{if(it.id==updated.id) updated else it} else rooms+updated; persist(); editRoom=null }, onDelete={ rooms=rooms.filterNot{it.id==room.id}; circuits=circuits.map{it.copy(roomIds=it.roomIds-room.id)}; persist(); editRoom=null }) }
    if(deviceEditor) DeviceEditor(rooms, onDismiss={deviceEditor=false}, onSave={circuit->circuits=circuits+circuit;persist();deviceEditor=false})
    confirm?.let { c ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Turn off ${c.name}?") },
            text = { Column { Text(c.purpose); Spacer(Modifier.height(8.dp)); Text("Affected rooms: " + rooms.filter { it.id in c.roomIds }.joinToString { it.name }, fontWeight = FontWeight.Medium) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
            confirmButton = {
                TextButton(onClick = {
                    val id = c.id; confirm = null
                    circuits = circuits.map { if (it.id == id) it.copy(state = DeviceState.PENDING, detail = "Command sent — waiting for report") else it }; persist()
                    scope.launch { delay(900); circuits = circuits.map { if (it.id == id) it.copy(state = DeviceState.OFF, detail = "Reported just now") else it }; persist() }
                }) { Text("Turn off") }
            }
        )
    }
}

@Composable private fun StatusLegend(){ Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){ listOf("ON" to Color(0xFF0B6E4F),"OFF" to Color.Gray,"Unknown" to Color(0xFFF0A72E)).forEach{(s,c)->Text(s,Modifier.background(c.copy(alpha=.14f),RoundedCornerShape(12.dp)).padding(horizontal=9.dp,vertical=4.dp),color=c,fontSize=12.sp,fontWeight=FontWeight.Bold)} } }
@Composable private fun Plan(rooms:List<Room>,circuits:List<Circuit>,edit:Boolean,onRoom:(Room)->Unit,onMove:(Room,Float,Float)->Unit){ Box(Modifier.padding(horizontal=16.dp).fillMaxWidth().height(390.dp).background(Color.White,RoundedCornerShape(20.dp)).border(1.dp,Color(0xFFE1E7E3),RoundedCornerShape(20.dp))){ rooms.forEach { room -> val states=circuits.filter{room.id in it.roomIds}.map{it.state}; val color=when { states.isEmpty()->Color(0xFFE9EEEB); states.any{it==DeviceState.UNKNOWN||it==DeviceState.ERROR}->Color(0xFFFFF4D7); states.all{it==DeviceState.ON}->Color(0xFFBCEAD6); states.all{it==DeviceState.OFF}->Color(0xFFDDE2DF); else->Color(0xFFDDE6F0) }; Box(Modifier.padding(start=(room.x*300).dp,top=(room.y*360).dp).width((room.w*300).dp).height((room.h*360).dp).background(color,RoundedCornerShape(12.dp)).border(if(edit)2.dp else 1.dp,if(edit)Color(0xFF2E6F9E) else Color(0xFF8A9890),RoundedCornerShape(12.dp)).pointerInput(edit,room){if(edit) detectDragGestures{change,drag->change.consume();onMove(room,drag.x/300f,drag.y/360f)}}.clickable{onRoom(room)},contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Text(room.name,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center); Text(roomStatus(states),fontSize=11.sp,color=Color(0xFF53615B))} } }; if(rooms.isEmpty()) Text("Edit the plan to add your first room",Modifier.align(Alignment.Center),color=Color.Gray) } }
private fun roomStatus(s:List<DeviceState>)=when {s.isEmpty()->"No devices";s.any{it==DeviceState.UNKNOWN||it==DeviceState.ERROR}->"State unknown";s.all{it==DeviceState.ON}->"All ON";s.all{it==DeviceState.OFF}->"All OFF";else->"Mixed"}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun RoomSheet(room:Room,circuits:List<Circuit>,onDismiss:()->Unit,onAction:(Circuit,DeviceState)->Unit){ ModalBottomSheet(onDismissRequest=onDismiss){ Column(Modifier.padding(20.dp).fillMaxWidth()){ Text(room.name,fontSize=24.sp,fontWeight=FontWeight.Bold); Text("Circuits in this room",color=Color.Gray); Spacer(Modifier.height(12.dp)); if(circuits.isEmpty()) Text("No circuits assigned yet.",Modifier.padding(vertical=24.dp)); circuits.forEach{ CircuitCard(it,onAction) }; Spacer(Modifier.height(20.dp)) } } }
@Composable private fun CircuitCard(c:Circuit,onAction:(Circuit,DeviceState)->Unit){
    val color=when(c.state){DeviceState.ON->Color(0xFF0B6E4F);DeviceState.OFF->Color.Gray;DeviceState.PENDING->Color(0xFFB87800);DeviceState.ERROR->Color(0xFFB3261E);DeviceState.UNKNOWN->Color(0xFF9A6200)}
    Card(Modifier.fillMaxWidth().padding(vertical=5.dp),colors=CardDefaults.cardColors(containerColor=Color.White)) {
        Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(c.name,fontWeight=FontWeight.Bold); Text(c.purpose+" · "+c.provider,fontSize=12.sp,color=Color.Gray); Text(when(c.state){DeviceState.PENDING->"Pending";DeviceState.UNKNOWN->"Offline / unknown";else->c.state.name}+" · "+c.detail,fontSize=11.sp,color=color); if(c.state==DeviceState.PENDING) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top=6.dp)) }
            if(c.state!=DeviceState.PENDING) Button(onClick={onAction(c,if(c.state==DeviceState.ON)DeviceState.OFF else DeviceState.ON)},colors=ButtonDefaults.buttonColors(containerColor=color)){Text(if(c.state==DeviceState.ON)"OFF" else "ON")}
        }
    }
}
@Composable private fun RoomEditor(room:Room,onDismiss:()->Unit,onSave:(Room)->Unit,onDelete:()->Unit){var name by remember{mutableStateOf(room.name)}; AlertDialog(onDismissRequest=onDismiss,title={Text("Room details")},text={Column{OutlinedTextField(name,{name=it},label={Text("Room name")});Text("Drag the room on the plan to move it. Resize support is provided by changing its dimensions in a future provider-backed editor.",fontSize=12.sp,color=Color.Gray,modifier=Modifier.padding(top=10.dp))}},confirmButton={TextButton(onClick={onSave(room.copy(name=name.ifBlank{"Room"}))}){Text("Save")}},dismissButton={Row{if(room.name!="New room")TextButton(onClick=onDelete){Text("Delete",color=Color(0xFFB3261E))};TextButton(onClick=onDismiss){Text("Cancel")}}})}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun DeviceEditor(rooms:List<Room>,onDismiss:()->Unit,onSave:(Circuit)->Unit){
    var name by remember{mutableStateOf("")}; var purpose by remember{mutableStateOf("Lighting")}; var entity by remember{mutableStateOf("")}; var provider by remember{mutableStateOf("Home Assistant")}; var selected by remember{mutableStateOf(emptySet<String>())}
    ModalBottomSheet(onDismissRequest=onDismiss){ LazyColumn(Modifier.padding(20.dp).fillMaxWidth()){
        item { Text("Add device / Добавить устройство",fontSize=22.sp,fontWeight=FontWeight.Bold); Text("Assign a controllable channel to one or more rooms.",color=Color.Gray,fontSize=13.sp); Spacer(Modifier.height(12.dp)); OutlinedTextField(name,{name=it},label={Text("Circuit name / Название")},modifier=Modifier.fillMaxWidth()); OutlinedTextField(purpose,{purpose=it},label={Text("Purpose / Назначение")},modifier=Modifier.fillMaxWidth()); OutlinedTextField(entity,{entity=it},label={Text("Entity or channel ID / ID канала")},modifier=Modifier.fillMaxWidth()); Spacer(Modifier.height(10.dp)); Text("Provider / Провайдер",fontWeight=FontWeight.Medium) }
        items(listOf("Home Assistant","Tuya Local","Tuya Cloud")){ p-> Row(Modifier.fillMaxWidth().clickable{provider=p}.padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically){ RadioButton(selected=provider==p,onClick={provider=p});Text(p) } }
        item { Text("Rooms / Комнаты",fontWeight=FontWeight.Medium) }
        items(rooms){ room-> Row(Modifier.fillMaxWidth().clickable{selected=if(room.id in selected)selected-room.id else selected+room.id}.padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically){ Checkbox(checked=room.id in selected,onCheckedChange={selected=if(it)selected+room.id else selected-room.id});Text(room.name) } }
        item { Button(onClick={onSave(Circuit(name=name.ifBlank{"New circuit"},purpose=purpose.ifBlank{"Custom"},roomIds=selected,provider=provider,entity=entity.ifBlank{"Unconfigured"},state=DeviceState.UNKNOWN,detail="Awaiting first provider report"))},enabled=selected.isNotEmpty(),modifier=Modifier.fillMaxWidth().padding(top=12.dp)){Text("Save assignment / Сохранить")} ; Spacer(Modifier.height(24.dp)) }
    } }
}
private class Store(private val ctx:Context){private val p=ctx.getSharedPreferences("home",0); fun russian()=p.getBoolean("russian",false);fun saveRussian(value:Boolean){p.edit().putBoolean("russian",value).apply()}; fun rooms():List<Room>{val raw=p.getString("rooms",null)?:return listOf(Room(name="Living room",x=.08f,y=.08f,w=.45f,h=.35f),Room(name="Kitchen",x=.57f,y=.08f,w=.34f,h=.35f),Room(name="Bedroom",x=.08f,y=.49f,w=.4f,h=.32f));return JSONArray(raw).let{a->List(a.length()){i->a.getJSONObject(i).let{o->Room(o.getString("id"),o.getString("name"),o.getDouble("x").toFloat(),o.getDouble("y").toFloat(),o.getDouble("w").toFloat(),o.getDouble("h").toFloat())}}}};fun circuits(rooms:List<Room>):List<Circuit>{val raw=p.getString("circuits",null)?:return rooms.take(2).mapIndexed{i,r->Circuit(name=if(i==0)"Living lighting" else "Kitchen lighting",purpose="Lighting",panel="B${i+1}",roomIds=setOf(r.id),state=DeviceState.OFF,detail="Reported just now")};return JSONArray(raw).let{a->List(a.length()){i->a.getJSONObject(i).let{o->Circuit(o.getString("id"),o.getString("name"),o.getString("purpose"),o.optString("panel"),o.getJSONArray("roomIds").let{x->(0 until x.length()).map{x.getString(it)}.toSet()},o.optString("provider","Home Assistant"),o.optString("entity"),DeviceState.valueOf(o.getString("state")),o.optString("detail"))}}}};fun save(rs:List<Room>,cs:List<Circuit>){p.edit().putString("rooms",JSONArray(rs.map{JSONObject().put("id",it.id).put("name",it.name).put("x",it.x).put("y",it.y).put("w",it.w).put("h",it.h)}).toString()).putString("circuits",JSONArray(cs.map{JSONObject().put("id",it.id).put("name",it.name).put("purpose",it.purpose).put("panel",it.panel).put("roomIds",JSONArray(it.roomIds.toList())).put("provider",it.provider).put("entity",it.entity).put("state",it.state.name).put("detail",it.detail)}).toString()).apply()}}
