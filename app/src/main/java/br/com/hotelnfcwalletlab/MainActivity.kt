package br.com.hotelnfcwalletlab

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.nfc.NfcAdapter
import android.os.Bundle
import android.view.LayoutInflater
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.android.gms.pay.Pay
import com.google.android.gms.pay.PayApiAvailabilityStatus
import com.google.android.gms.pay.PayClient
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

private const val SAVE_REQUEST = 7001
private val blue = Color(0xFF123B4B)
private val teal = Color(0xFF007D82)

class MainActivity : ComponentActivity() {
    private val walletClient by lazy { Pay.getClient(this) }
    private var walletAvailability by mutableStateOf("Não testado")
    private var walletSaved by mutableStateOf("Não testado")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        walletClient.getPayApiAvailabilityStatus(PayClient.RequestType.SAVE_PASSES)
            .addOnSuccessListener {
                walletAvailability = if (it == PayApiAvailabilityStatus.AVAILABLE)
                    "Disponível (SDK)" else "Indisponível (SDK)"
            }.addOnFailureListener { walletAvailability = "Indisponível: " + it.message }
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = teal)) { LabScreen() } }
    }

    @Deprecated("Google Wallet SDK currently returns save result via onActivityResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SAVE_REQUEST) {
            walletSaved = when (resultCode) {
                Activity.RESULT_OK -> "Confirmado pelo Google Wallet SDK (RESULT_OK)"
                Activity.RESULT_CANCELED -> "Usuário cancelou"
                PayClient.SavePassesResult.SAVE_ERROR ->
                    "Erro Wallet: " + (data?.getStringExtra(PayClient.EXTRA_API_ERROR_MESSAGE) ?: "sem mensagem")
                else -> "Resultado desconhecido: " + resultCode
            }
        }
    }

    @Composable
    private fun LabScreen() {
        val prefs = remember { getSharedPreferences("lab_hce", MODE_PRIVATE) }
        val scope = rememberCoroutineScope()
        var base by remember { mutableStateOf("http://10.0.2.2:8000") }
        var key by remember { mutableStateOf("") } // do not persist API key on device
        var hotel by remember { mutableStateOf("Hotel Fazenda Vale da Mantiqueira") }
        var guest by remember { mutableStateOf("Hóspede Teste") }
        var room by remember { mutableStateOf("101") }
        val now = remember { OffsetDateTime.now() }
        var checkin by remember { mutableStateOf(now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)) }
        var checkout by remember { mutableStateOf(now.plusDays(2).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)) }
        var identifier by remember { mutableStateOf("") }
        var opaque by remember { mutableStateOf("") }
        var jwt by remember { mutableStateOf("") }
        var feedback by remember { mutableStateOf("Nenhuma emissão executada") }
        var busy by remember { mutableStateOf(false) }
        var hce by remember { mutableStateOf(prefs.getBoolean("enabled", false)) }
        var logs by remember { mutableStateOf(prefs.getString("logs", "") ?: "") }
        val nfc = remember { NfcAdapter.getDefaultAdapter(this) }
        val hceSupported = remember { packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION) }

        Column(
            modifier = Modifier.fillMaxSize().background(Color(0xFFF4F7F8))
                .verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("HOTEL NFC WALLET LAB", color = blue,
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Text("Experimento isolado — Google Wallet, ISO-DEP HCE e PC/SC. Não abre fechaduras BIS.",
                color = Color.DarkGray)
            LabCard {
                Text("1 • Conexão do emissor", fontWeight = FontWeight.Bold)
                LabInput("URL do backend (celular: IP do computador)", base) { base = it }
                LabInput("Chave da API de laboratório (não é chave Google)", key) { key = it }
                Text("Para celular real use IP LAN do FastAPI. Evite redes públicas.",
                    style = MaterialTheme.typography.bodySmall)
            }
            LabCard {
                Text("2 • Credencial experimental", fontWeight = FontWeight.Bold)
                LabInput("Hotel", hotel) { hotel = it }
                LabInput("Hóspede", guest) { guest = it }
                LabInput("Quarto", room) { room = it }
                LabInput("Check-in ISO-8601 com fuso", checkin) { checkin = it }
                LabInput("Checkout ISO-8601 com fuso", checkout) { checkout = it }
                Button(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val body = JSONObject().put("hotel_name", hotel)
                                .put("guest_name", guest).put("room_number", room)
                                .put("checkin", checkin).put("checkout", checkout)
                            val result = Backend.request(base, key, "POST", "/v1/credentials", body)
                            identifier = result.getString("id")
                            opaque = result.getString("opaque_token")
                            jwt = ""
                            walletSaved = "Não testado"
                            prefs.edit().putString("token", opaque).apply()
                            feedback = "Credencial criada no backend: " + identifier
                        } catch (ex: Exception) {
                            feedback = "Falha ao criar: " + ex.message
                        } finally { busy = false }
                    }
                }) { Text("Criar credencial no servidor") }
                Button(enabled = identifier.isNotBlank() && !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val response = Backend.request(base, key, "POST",
                                "/v1/credentials/" + identifier + "/wallet")
                            jwt = response.getString("jwt")
                            feedback = "GenericObject criado na API Google: " + response.getString("object_id") +
                                ". Adição à carteira ainda não realizada."
                        } catch (ex: Exception) {
                            feedback = "Emissão Google falhou: " + ex.message
                        } finally { busy = false }
                    }
                }) { Text("Emitir GenericObject real") }
                if (jwt.isNotBlank() && walletAvailability.startsWith("Disponível")) {
                    AndroidView(
                        factory = { context ->
                            LayoutInflater.from(context).inflate(R.layout.add_to_google_wallet_button, null)
                        },
                        update = { view ->
                            view.setOnClickListener {
                                walletSaved = "Aguardando resposta do Google Wallet"
                                walletClient.savePassesJwt(jwt, this@MainActivity, SAVE_REQUEST)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    )
                }
                Text(feedback, color = blue)
            }
            if (identifier.isNotBlank()) {
                Column(
                    modifier = Modifier.fillMaxWidth().background(blue, RoundedCornerShape(18.dp))
                        .padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(hotel.uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                    Text("QUARTO " + room, color = Color.White,
                        style = MaterialTheme.typography.headlineMedium)
                    Text(guest, color = Color.White)
                    Text("Check-in: " + checkin, color = Color(0xFFCDE5E7))
                    Text("Checkout: " + checkout, color = Color(0xFFCDE5E7))
                    OpaqueQr(opaque)
                    Text("QR experimental: identificador opaco; não é chave BIS.",
                        color = Color.White, style = MaterialTheme.typography.bodySmall)
                }
            }
            LabCard {
                Text("3 • HCE ISO-DEP experimental", fontWeight = FontWeight.Bold)
                Text("AID: " + LabApdu.AID + " • GET DATA: 80CA000000")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Switch(checked = hce, enabled = hceSupported && opaque.isNotBlank(),
                        onCheckedChange = {
                            hce = it
                            prefs.edit().putBoolean("enabled", it).apply()
                        })
                    Text(if (hce) "HCE ativado" else "HCE desativado")
                }
                Text("Ative a tela, desbloqueie o smartphone e aproxime do ACR122U. " +
                     "Não é MIFARE Classic, Crypto1 nem Smart Tap.",
                     style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { logs = prefs.getString("logs", "") ?: "" }) {
                    Text("Atualizar log de APDUs")
                }
                Text(if (logs.isBlank()) "Nenhuma APDU observada" else logs.take(5000),
                    style = MaterialTheme.typography.bodySmall, maxLines = 25,
                    overflow = TextOverflow.Ellipsis)
            }
            LabCard {
                Text("4 • Evidências de diagnóstico", fontWeight = FontWeight.Bold)
                Status("NFC disponível", when {
                    nfc == null -> "Não"
                    nfc.isEnabled -> "Sim (Android)"
                    else -> "Presente, desligado"
                })
                Status("HCE disponível", if (hceSupported) "Sim (Android)" else "Não")
                Status("Google Wallet API", walletAvailability)
                Status("Credencial adicionada", walletSaved)
                Status("HCE ativado", if (hce) "Sim (controle local)" else "Não")
                Status("Leitor conectado", "Não testado no Windows")
                Status("Cartão detectado", "Não testado no Windows")
                Status("AID selecionado", if (logs.contains("AID selecionado")) "Log local observado" else "Não testado")
                Status("APDU respondida", if (logs.contains("GET DATA respondido")) "Log local observado" else "Não testado")
                Status("Smart Tap configurado", "Não testado / não configurado")
                Status("BIS compatível", "Não demonstrado")
            }
        }
    }
}

@Composable
private fun LabCard(content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(14.dp))
        .padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

@Composable
private fun LabInput(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange,
        label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun Status(label: String, value: String) {
    Column {
        Text(label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodySmall, color = teal)
    }
}

@Composable
private fun OpaqueQr(token: String) {
    val bitmap = remember(token) {
        val matrix = QRCodeWriter().encode(token, BarcodeFormat.QR_CODE, 256, 256)
        val pixels = IntArray(256 * 256)
        for (y in 0 until 256) for (x in 0 until 256)
            pixels[y * 256 + x] = if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        Bitmap.createBitmap(pixels, 256, 256, Bitmap.Config.ARGB_8888)
    }
    Image(bitmap = bitmap.asImageBitmap(), contentDescription = "QR com identificador opaco",
        modifier = Modifier.size(180.dp))
}
