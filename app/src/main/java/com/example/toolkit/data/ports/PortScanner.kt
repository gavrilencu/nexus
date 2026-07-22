package com.example.toolkit.data.ports

import com.example.toolkit.data.model.PortResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.InetSocketAddress
import java.net.Socket

class PortScanner {

    companion object {
        val COMMON_PORTS = listOf(
            21 to "FTP",
            22 to "SSH",
            23 to "Telnet",
            25 to "SMTP",
            53 to "DNS",
            80 to "HTTP",
            110 to "POP3",
            143 to "IMAP",
            443 to "HTTPS",
            445 to "SMB",
            465 to "SMTPS",
            587 to "SMTP",
            993 to "IMAPS",
            995 to "POP3S",
            1433 to "MSSQL",
            1521 to "Oracle",
            3306 to "MySQL",
            3389 to "RDP",
            5432 to "PostgreSQL",
            5900 to "VNC",
            6379 to "Redis",
            8080 to "HTTP-Alt",
            8443 to "HTTPS-Alt",
            8888 to "HTTP-Alt",
            9200 to "Elasticsearch",
            11211 to "Memcached",
            27017 to "MongoDB"
        )
    }

    fun scan(
        host: String,
        ports: List<Pair<Int, String>> = COMMON_PORTS,
        timeoutMs: Int = 800,
        concurrency: Int = 32
    ): Flow<ScanEvent> = callbackFlow {
        val cleaned = host.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore("/")
            .substringBefore(":")

        if (cleaned.isBlank()) {
            trySend(ScanEvent.Error("Invalid host"))
            close()
            return@callbackFlow
        }

        trySend(ScanEvent.Started(cleaned, ports.size))
        val open = mutableListOf<PortResult>()
        val semaphore = Semaphore(concurrency)
        var scanned = 0

        val job = launch(Dispatchers.IO) {
            coroutineScope {
                ports.map { (port, service) ->
                    async {
                        semaphore.withPermit {
                            val result = probe(cleaned, port, service, timeoutMs)
                            val progress = synchronized(open) {
                                scanned++
                                if (result.open) open.add(result)
                                ScanEvent.Progress(
                                    scanned = scanned,
                                    total = ports.size,
                                    lastResult = result,
                                    openPorts = open.sortedBy { it.port }.toList()
                                )
                            }
                            trySend(progress)
                        }
                    }
                }.awaitAll()
            }
            trySend(
                ScanEvent.Completed(
                    target = cleaned,
                    openPorts = open.sortedBy { it.port }
                )
            )
            close()
        }

        awaitClose { job.cancel() }
    }.flowOn(Dispatchers.Default)

    private fun probe(host: String, port: Int, service: String, timeoutMs: Int): PortResult {
        val start = System.currentTimeMillis()
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                PortResult(
                    port = port,
                    open = true,
                    service = service,
                    latencyMs = System.currentTimeMillis() - start
                )
            }
        } catch (_: Exception) {
            PortResult(port = port, open = false, service = service)
        }
    }

    sealed class ScanEvent {
        data class Started(val target: String, val total: Int) : ScanEvent()
        data class Progress(
            val scanned: Int,
            val total: Int,
            val lastResult: PortResult,
            val openPorts: List<PortResult>
        ) : ScanEvent()

        data class Completed(val target: String, val openPorts: List<PortResult>) : ScanEvent()
        data class Error(val message: String) : ScanEvent()
    }
}
