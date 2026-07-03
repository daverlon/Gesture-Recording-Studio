package ai.symly.ble

import ai.symly.ImuSample
import com.juul.kable.Advertisement
import com.juul.kable.Peripheral
import com.juul.kable.Scanner
import com.juul.kable.State
import com.juul.kable.characteristicOf
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class BleDevice(
    val id: String,
    val name: String,
    val rssi: Int?
)

@OptIn(ExperimentalUuidApi::class)
class BleClient {
    private val scanner by lazy { Scanner() }
    private val advertisements = mutableMapOf<String, Advertisement>()
    private var peripheral: Peripheral? = null
    private var scanJob: Job? = null
    private var connectionJob: Job? = null
    private var disconnectRequested = false
    private var imuRxBuffer = ByteArray(0)

    // Own scope, not tied to Compose — survives recomposition
    private val clientScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _imuSamples = MutableSharedFlow<ImuSample>(extraBufferCapacity = 64)
    val imuSamples: SharedFlow<ImuSample> = _imuSamples

    fun startScan(scope: CoroutineScope, onDevices: (List<BleDevice>) -> Unit) {
        stopScan()
        val devices = linkedMapOf<String, BleDevice>()
        scanJob = scope.launch {
            launch {
                scanner.advertisements.collect { advertisement ->
                    val id = advertisement.identifier.toString()
                    val deviceName = advertisement.name?.takeIf { it.isNotBlank() } ?: "Unknown device"
                    
                    // Filter by manufacturer data with company ID 0xFFFF and "SY" marker
                    // manufacturerData(code) returns ByteArray without the company ID prefix
                    val mfgData = advertisement.manufacturerData(0xFFFF)
                    val hasSymlyMarker = mfgData != null && 
                        mfgData.size >= 2 && 
                        mfgData[0] == 0x53.toByte() && 
                        mfgData[1] == 0x59.toByte()
                    
                    if (hasSymlyMarker) {
                        advertisements[id] = advertisement
                        devices[id] = BleDevice(
                            id = id,
                            name = deviceName,
                            rssi = advertisement.rssi
                        )
                    }
                }
            }
            while (isActive) {
                delay(SCAN_UI_REFRESH_MS)
                if (devices.isNotEmpty()) {
                    onDevices(devices.values.sortedWith(deviceSort))
                }
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
    }

    suspend fun connect(deviceId: String): String {
        val advertisement = advertisements[deviceId] ?: error("Device no longer available")
        val p = Peripheral(advertisement)
        peripheral = p
        imuRxBuffer = ByteArray(0)

        try {
            p.connect()
        } catch (e: Exception) {
            peripheral = null
            throw e
        }

        println("BleClient: connected, starting observation")

        connectionJob?.cancel()
        connectionJob = clientScope.launch {
            try {
                val characteristic = characteristicOf(
                    service = Uuid.parse(UART_SERVICE_UUID),
                    characteristic = Uuid.parse(UART_TX_CHARACTERISTIC_UUID),
                )
                println("BleClient: observing $UART_TX_CHARACTERISTIC_UUID")
                p.observe(characteristic).collect { chunk ->
                    feedImuRxBuffer(chunk).forEach { _imuSamples.emit(it) }
                }
                println("BleClient: observe completed")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("BleClient: observe error — ${e::class.simpleName}: ${e.message}")
            }
        }

        return advertisement.name?.takeIf { it.isNotBlank() } ?: "Unknown device"
    }

    fun startConnectionMonitor(scope: CoroutineScope, onConnectionLost: () -> Unit) {
        val p = peripheral ?: return
        scope.launch {
            var sawConnected = false
            p.state.collect { state ->
                println("BleClient: state -> $state")
                when (state) {
                    is State.Connected -> sawConnected = true
                    is State.Disconnected -> {
                        if (!sawConnected || disconnectRequested) return@collect
                        delay(CONNECTION_LOST_DEBOUNCE_MS)
                        if (disconnectRequested || p.state.value !is State.Disconnected) return@collect
                        sawConnected = false
                        clearConnection()
                        onConnectionLost()
                    }
                    else -> Unit
                }
            }
        }
    }

    suspend fun disconnect() {
        disconnectRequested = true
        try {
            stopScan()
            connectionJob?.cancel()
            connectionJob = null
            peripheral?.disconnect()
        } finally {
            clearConnection()
            disconnectRequested = false
        }
    }

    private fun clearConnection() {
        connectionJob?.cancel()
        connectionJob = null
        peripheral = null
        advertisements.clear()
        imuRxBuffer = ByteArray(0)
    }

    private fun feedImuRxBuffer(chunk: ByteArray): List<ImuSample> {
        if (chunk.isEmpty()) return emptyList()
        
        // Accumulate incoming bytes (may arrive in batches)
        imuRxBuffer += chunk
        val samples = mutableListOf<ImuSample>()

        // Extract all complete 40-byte samples from buffer
        // Device sends batches of 5 samples (200 bytes) every 50ms
        while (imuRxBuffer.size >= IMU_PAYLOAD_BYTES) {
            val payload = imuRxBuffer.copyOfRange(0, IMU_PAYLOAD_BYTES)
            imuRxBuffer = imuRxBuffer.copyOfRange(IMU_PAYLOAD_BYTES, imuRxBuffer.size)
            
            val sample = parseImuBinaryFrameOrNull(payload)
            if (sample != null) {
                samples.add(sample)
            }
        }

        if (imuRxBuffer.size > 500) imuRxBuffer = ByteArray(0)
        
        return samples
    }


    private companion object {
        const val SCAN_UI_REFRESH_MS = 500L
        const val CONNECTION_LOST_DEBOUNCE_MS = 750L
        const val UART_SERVICE_UUID = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
        // Nordic UART TX: peripheral → central (notifications from bleuart.write)
        const val UART_TX_CHARACTERISTIC_UUID = "6e400003-b5a3-f393-e0a9-e50e24dcca9e"
        val deviceSort = compareBy<BleDevice> { it.name.lowercase() }
            .thenByDescending { it.rssi ?: Int.MIN_VALUE }
    }
}
