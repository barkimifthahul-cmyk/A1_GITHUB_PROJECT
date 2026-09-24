package com.a1stock.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.room.Room
import androidx.work.*
import com.a1stock.app.data.A1Database
import com.a1stock.app.data.IssuerEntity
import com.a1stock.app.data.IssuerSeeder
import com.a1stock.app.data.EventEntity
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)

        if (Build.VERSION.SDK_INT >= 33) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                10
            )
        }

        val req = PeriodicWorkRequestBuilder<SyncWorker>(
            24,
            TimeUnit.HOURS
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "A1_DAILY_SYNC",
            ExistingPeriodicWorkPolicy.UPDATE,
            req
        )

        setContent {
            A1Screen()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun A1Screen() {
    var tab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("A1 • Stock Intelligence")
                }
            )
        },
        bottomBar = {
            NavigationBar {
                listOf(
                    "Dashboard",
                    "Scanner",
                    "Event",
                    "Watchlist"
                ).forEachIndexed { i, n ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = {
                            Text(
                                when (i) {
                                    0 -> "⌂"
                                    1 -> "⌕"
                                    else -> "★"
                                }
                            )
                        },
                        label = {
                            Text(n)
                        }
                    )
                }
            }
        }
    ) { p ->
        Box(
            Modifier
                .padding(p)
                .fillMaxSize()
        ) {
            when (tab) {
                0 -> Dashboard()
                1 -> Scanner()
                2 -> EventScreen()
                3 -> Watchlist()
            }
        }
    }
}

@Composable
fun Dashboard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var syncing by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "A1",
                style = MaterialTheme.typography.headlineLarge
            )
        }

        item {
            Text(
                "AUTO DATA ENGINE",
                style = MaterialTheme.typography.titleMedium
            )
        }

        item {
            Card(
                Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.padding(16.dp)
                ) {
                    Text("Sinkronisasi otomatis: aktif")
                    Text("Sumber utama: IDX/BEI + KSEI")
                    Text("Data corporate action dari KSEI")
                    Text("Histori disimpan lokal di database A1")

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = {
                            syncing = true

                            val request =
                                OneTimeWorkRequestBuilder<SyncWorker>()
                                    .setConstraints(
                                        Constraints.Builder()
                                            .setRequiredNetworkType(
                                                NetworkType.CONNECTED
                                            )
                                            .build()
                                    )
                                    .build()

                            WorkManager.getInstance(context)
                                .enqueue(request)

                            syncing = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !syncing
                    ) {
                        Text(
                            if (syncing)
                                "Sinkronisasi..."
                            else
                                "🔄 Sinkronkan Sekarang"
                        )
                    }
                }
            }
        }

        item {
            Text(
                "Fitur",
                style = MaterialTheme.typography.titleLarge
            )
        }

        item {
            Text(
                "• Keterbukaan informasi\n" +
                "• Corporate action\n" +
                "• Pemegang saham\n" +
                "• Deteksi perubahan\n" +
                "• Watchlist & notifikasi"
            )
        }
    }
}

@Composable
fun EventScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current

    var events by remember {
        mutableStateOf<List<EventEntity>>(emptyList())
    }

    LaunchedEffect(Unit) {
        try {
            val db = Room.databaseBuilder(
                context,
                A1Database::class.java,
                "a1.db"
            )
                .addMigrations(
                    A1Database.MIGRATION_1_2,
                    A1Database.MIGRATION_2_3
                )
                .build()

            events = db.eventDao().latest()
            db.close()
        } catch (_: Exception) {
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Event",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            "Corporate action & keterbukaan informasi",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(12.dp))

        if (events.isEmpty()) {
            Text(
                "Belum ada event tersimpan. Tekan Sinkronkan Sekarang di Dashboard."
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(events) { event ->
                    Card(
                        Modifier.fillMaxWidth()
                    ) {
                        Column(
                            Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                event.ticker ?: "UMUM",
                                style = MaterialTheme.typography.titleMedium
                            )

                            Text(
                                event.category,
                                style = MaterialTheme.typography.labelMedium
                            )

                            Text(
                                event.title,
                                style = MaterialTheme.typography.bodyLarge
                            )

                            Text(
                                "Sumber: ${event.source}",
                                style = MaterialTheme.typography.bodySmall
                            )

                            event.publishedAt?.let {
                                Text(
                                    "Tanggal: $it",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Scanner() {
    val context = androidx.compose.ui.platform.LocalContext.current

    var query by remember { mutableStateOf("") }
    var issuers by remember {
        mutableStateOf<List<IssuerEntity>>(emptyList())
    }
    var selectedIssuer by remember {
        mutableStateOf<IssuerEntity?>(null)
    }
    var analysisIssuer by remember {
        mutableStateOf<IssuerEntity?>(null)
    }

    val db = remember {
        Room.databaseBuilder(
            context,
            A1Database::class.java,
            "a1.db"
        )
            .addMigrations(A1Database.MIGRATION_1_2, A1Database.MIGRATION_2_3)
            .build()
    }

    LaunchedEffect(Unit) {
        try {
            db.issuerDao().insertAll(IssuerSeeder.initialData(context))
            issuers = db.issuerDao().all()
        } catch (_: Exception) {
        }
    }

    LaunchedEffect(query) {
        if (query.isNotBlank()) {
            try {
                issuers = db.issuerDao().search(query.trim())
            } catch (_: Exception) {
            }
        } else {
            try {
                issuers = db.issuerDao().all()
            } catch (_: Exception) {
            }
        }
    }

    var analysisMarket by remember { mutableStateOf<com.a1stock.app.data.MarketEntity?>(null) }

    LaunchedEffect(analysisIssuer?.ticker) {
        analysisMarket = analysisIssuer?.let { db.marketDao().get(it.ticker) }
    }

    analysisIssuer?.let { issuer ->
        AnalysisScreen(
            issuer = issuer,
            market = analysisMarket,
            onBack = {
                analysisIssuer = null
            }
        )
        return
    }

    Column(
        Modifier
            .padding(16.dp)
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Scanner",
                    "Event",
            style = MaterialTheme.typography.headlineMedium
        )

        Text("Cari kode atau nama emiten.")

        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                selectedIssuer = null
            },
            label = {
                Text("Contoh: BBRI")
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        selectedIssuer?.let { issuer ->
            Card(
                Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.padding(16.dp)
                ) {
                    Text(
                        issuer.ticker,
                        style = MaterialTheme.typography.titleLarge
                    )

                    Text(issuer.name)

                    Spacer(
                        Modifier.height(8.dp)
                    )

                    Button(
                        onClick = {
                            analysisIssuer = issuer
                        }
                    ) {
                        Text("Analisa ${issuer.ticker}")
                    }
                }
            }
        }

        if (selectedIssuer == null) {
            Text(
                "Daftar emiten",
                style = MaterialTheme.typography.titleMedium
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(issuers) { issuer ->
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedIssuer = issuer
                            }
                    ) {
                        Column(
                            Modifier.padding(12.dp)
                        ) {
                            Text(
                                issuer.ticker,
                                style = MaterialTheme.typography.titleMedium
                            )

                            Text(issuer.name)

                            issuer.sector?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AnalysisScreen(
    issuer: IssuerEntity,
    market: com.a1stock.app.data.MarketEntity?,
    onBack: () -> Unit
) {
    Column(
        Modifier
            .padding(16.dp)
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(onClick = onBack) {
            Text("← Kembali")
        }

        Text("ANALISIS EMITEN", style = MaterialTheme.typography.headlineMedium)
        Text(issuer.ticker, style = MaterialTheme.typography.headlineLarge)
        Text(issuer.name)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Data Market", style = MaterialTheme.typography.titleMedium)

                if (market == null) {
                    Text("Data market belum tersedia.")
                    Text("Harga, volume, dan market cap akan muncul setelah sumber data terhubung.")
                } else {
                    Text("Harga: ${market.price ?: 0.0}")
                    Text("Perubahan: ${market.change ?: 0.0}")
                    Text("Perubahan %: ${market.changePercent ?: 0.0}%")
                    Text("Volume: ${market.volume ?: 0}")
                    Text("Market Cap: ${market.marketCap ?: 0}")
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Profil Emiten")
                Text("Kode: ${issuer.ticker}")
                Text("Nama: ${issuer.name}")
                Text("Sektor: ${issuer.sector ?: "Belum tersedia"}")
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Modul Analisis")
                Text("• Corporate Action")
                Text("• Kepemilikan")
                Text("• Keterbukaan Informasi")
                Text("• Perubahan Pemegang Saham")
                Text("• RUPS")
            }
        }
    }
}

@Composable
fun Watchlist() {
    Column(
        Modifier.padding(16.dp)
    ) {
        Text(
            "Watchlist",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            "Tambahkan emiten yang ingin dipantau pada tahap UI berikutnya."
        )
    }
}
