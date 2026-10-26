package com.alastor.discordrelicvault
import android.app.*
import android.os.*
import android.content.*
import android.widget.*
import android.graphics.Color
import android.view.ViewGroup
import org.json.*
import java.net.URL
import kotlin.concurrent.thread
class MainActivity: Activity() {
 private val found=linkedMapOf<String,String>()
 override fun onCreate(savedInstanceState:Bundle?) {
 super.onCreate(savedInstanceState)
 val root=LinearLayout(this).apply { orientation=1; setPadding(24,30,24,16); setBackgroundColor(Color.rgb(22,22,29)) }
 val title=TextView(this).apply { text="Discord RelicVault"; textSize=26f; setTextColor(Color.WHITE) };root.addView(title)
 val status=TextView(this).apply { text="Load a public JSON archive of released Discord collectibles"; setTextColor(Color.LTGRAY) };root.addView(status)
 val url=EditText(this).apply { hint="HTTPS catalog JSON URL"; setSingleLine(true);setTextColor(Color.WHITE);setHintTextColor(Color.GRAY) };root.addView(url)
 val list=LinearLayout(this).apply { orientation=1 }
 fun download(name:String,link:String) { try { val req=DownloadManager.Request(android.net.Uri.parse(link)).setTitle(name).setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED).setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,"DiscordRelicVault/"+name.replace(Regex("[^a-zA-Z0-9._-]"),"_"));(getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req) }catch(e:Exception){status.text=e.message} }
 root.addView(Button(this).apply { text="Load catalog";setOnClickListener { val link=url.text.toString().trim();if(!link.startsWith("https://")) { status.text="HTTPS required" } else thread { try { val c=URL(link).openConnection();c.connectTimeout=15000;c.readTimeout=20000;val json=c.getInputStream().bufferedReader().use{it.readText()};val parsed=JSONTokener(json).nextValue();val output=linkedMapOf<String,String>();fun walk(v:Any?,depth:Int){if(depth>25||output.size>=10000)return;when(v){is JSONObject->{val keys=v.keys();while(keys.hasNext()){val k=keys.next();val child=v.opt(k);if(child is String && child.startsWith("https://") && (child.contains("discord.com")||child.contains("discordapp.com")) && (k.contains("url",true)||k=="src")){output["asset_"+output.size]=child};walk(child,depth+1)}};is JSONArray->for(i in 0 until v.length())walk(v.opt(i),depth+1)}};walk(parsed,0);runOnUiThread { found.clear();found.putAll(output);list.removeAllViews();status.text="Found "+found.size+" asset URLs";found.forEach{(name,asset)->list.addView(Button(this).apply{text=name;setOnClickListener{download(name,asset)}})} } }catch(e:Exception){runOnUiThread{status.text="Error: "+e.message}} } } } })
 root.addView(Button(this).apply { text="Download all";setOnClickListener { found.forEach{(name,link)->download(name,link)};status.text="Queued "+found.size+" downloads" } })
 val scroll=ScrollView(this);scroll.addView(list);root.addView(scroll,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));setContentView(root)
 }
}