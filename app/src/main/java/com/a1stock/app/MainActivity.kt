package com.a1stock.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.work.*
import java.util.concurrent.TimeUnit

class MainActivity: ComponentActivity() {
    override fun onCreate(b:Bundle?) { super.onCreate(b)
        if(Build.VERSION.SDK_INT>=33) ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.POST_NOTIFICATIONS),10)
        val req=PeriodicWorkRequestBuilder<SyncWorker>(24,TimeUnit.HOURS).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("A1_DAILY_SYNC",ExistingPeriodicWorkPolicy.UPDATE,req)
        setContent { A1Screen() }
    }
}

@Composable fun A1Screen(){
    var tab by remember{mutableIntStateOf(0)}
    Scaffold(topBar={TopAppBar(title={Text("A1 • Stock Intelligence")})},bottomBar={
        NavigationBar{ listOf("Dashboard","Scanner","Watchlist").forEachIndexed{ i,n->NavigationBarItem(tab==i,{tab=i},{Text(if(i==0)"⌂" else if(i==1)"⌕" else "★")},{Text(n)}) } }
    }){p->Box(Modifier.padding(p).fillMaxSize()){when(tab){0->Dashboard();1->Scanner();2->Watchlist()}}}
}
@Composable fun Dashboard(){LazyColumn(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Text("A1",style=MaterialTheme.typography.headlineLarge)};item{Text("AUTO DATA ENGINE",style=MaterialTheme.typography.titleMedium)};item{Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text("Sinkronisasi otomatis: aktif");Text("Sumber utama: IDX/BEI");Text("Data ownership: sumber KSEI yang dipublikasikan melalui BEI");Text("Histori disimpan lokal di database A1")}}};item{Text("Fitur",style=MaterialTheme.typography.titleLarge)};item{Text("• Keterbukaan informasi\n• Corporate action\n• Pemegang saham\n• Deteksi perubahan\n• Watchlist & notifikasi")}}}
@Composable fun Scanner(){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("Scanner",style=MaterialTheme.typography.headlineMedium);Text("Ketik kode emiten untuk modul analisis A1.");OutlinedTextField("",{},label={Text("Contoh: BBRI")},modifier=Modifier.fillMaxWidth());Button({}){Text("Analisa")}}}
@Composable fun Watchlist(){Column(Modifier.padding(16.dp)){Text("Watchlist",style=MaterialTheme.typography.headlineMedium);Text("Tambahkan emiten yang ingin dipantau pada tahap UI berikutnya.")}}
