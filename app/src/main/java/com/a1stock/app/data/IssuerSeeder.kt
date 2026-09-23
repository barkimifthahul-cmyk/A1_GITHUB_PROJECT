package com.a1stock.app.data

object IssuerSeeder {

    fun initialData(): List<IssuerEntity> = listOf(
        IssuerEntity("BBCA", "Bank Central Asia Tbk"),
        IssuerEntity("BBNI", "Bank Negara Indonesia (Persero) Tbk"),
        IssuerEntity("BBRI", "Bank Rakyat Indonesia (Persero) Tbk"),
        IssuerEntity("BMRI", "Bank Mandiri (Persero) Tbk"),
        IssuerEntity("BRIS", "Bank Syariah Indonesia Tbk"),
        IssuerEntity("TLKM", "Telkom Indonesia (Persero) Tbk"),
        IssuerEntity("ASII", "Astra International Tbk"),
        IssuerEntity("GOTO", "GoTo Gojek Tokopedia Tbk"),
        IssuerEntity("ANTM", "Aneka Tambang Tbk"),
        IssuerEntity("INCO", "Vale Indonesia Tbk")
    )
}
