package com.example.toolkit.data.capture

/**
 * Offline OUI (MAC prefix) → vendor map, used as a fallback identity signal
 * when mDNS/SSDP don't return a friendly name. Not exhaustive (the real IEEE
 * registry has 50,000+ entries) but covers the vendors that show up on the
 * overwhelming majority of home/office Wi‑Fi networks: phones, laptops,
 * routers, TVs, speakers, cameras, and common IoT/smart-home silicon.
 */
object OuiLookup {
    private val map = mapOf(
        // Apple
        "00:03:93" to "Apple", "00:0a:95" to "Apple", "00:1b:63" to "Apple",
        "00:1e:c2" to "Apple", "00:23:12" to "Apple", "00:25:00" to "Apple",
        "00:26:4a" to "Apple", "00:1f:5b" to "Apple", "ac:bc:32" to "Apple",
        "f0:d1:a9" to "Apple", "a4:83:e7" to "Apple", "d0:03:4b" to "Apple",
        "00:17:f2" to "Apple", "00:21:e9" to "Apple", "00:1c:b3" to "Apple",
        "3c:22:fb" to "Apple", "f4:5c:89" to "Apple", "a8:60:b6" to "Apple",
        "bc:d0:74" to "Apple", "00:26:bb" to "Apple", "88:66:5a" to "Apple",
        "28:6a:ba" to "Apple", "3c:07:54" to "Apple", "40:33:1a" to "Apple",
        "48:a1:95" to "Apple", "4c:8d:79" to "Apple", "5c:95:ae" to "Apple",
        "68:96:7b" to "Apple", "70:cd:60" to "Apple", "7c:c3:a1" to "Apple",
        "84:38:35" to "Apple", "8c:85:90" to "Apple", "90:72:40" to "Apple",
        "98:01:a7" to "Apple", "a4:d1:d2" to "Apple", "b8:09:8a" to "Apple",
        "c8:2a:14" to "Apple", "d8:96:95" to "Apple", "dc:2b:2a" to "Apple",
        "e0:ac:cb" to "Apple", "f0:18:98" to "Apple", "f4:37:b7" to "Apple",

        // Samsung
        "00:16:6c" to "Samsung", "00:1a:8a" to "Samsung", "00:1d:f6" to "Samsung",
        "00:23:39" to "Samsung", "08:37:3d" to "Samsung", "5c:0a:5b" to "Samsung",
        "c8:19:f7" to "Samsung", "e8:50:8b" to "Samsung", "34:23:87" to "Samsung",
        "38:2c:4a" to "Samsung", "4c:3c:16" to "Samsung", "78:1f:db" to "Samsung",
        "8c:71:f8" to "Samsung", "a0:0b:ba" to "Samsung", "b4:79:a7" to "Samsung",
        "cc:07:ab" to "Samsung", "d0:59:e4" to "Samsung", "e4:7d:bd" to "Samsung",
        "f0:5a:09" to "Samsung", "fc:c7:34" to "Samsung",

        // Google / Nest / Chromecast
        "00:1a:11" to "Google", "3c:5a:b4" to "Google", "54:60:09" to "Google",
        "f4:f5:e8" to "Google", "da:a1:19" to "Google", "18:b4:30" to "Google Nest",
        "64:16:66" to "Google Nest", "d8:eb:97" to "Google Nest", "6c:ad:f8" to "Google Nest",
        "94:95:a0" to "Google Chromecast", "a4:77:33" to "Google Chromecast",

        // Amazon (Echo / Fire / Ring)
        "00:0f:b5" to "Amazon", "34:d2:70" to "Amazon", "fc:65:de" to "Amazon",
        "84:d6:d0" to "Amazon", "00:fc:8b" to "Amazon", "68:37:e9" to "Amazon",
        "00:1a:79" to "Amazon", "44:65:0d" to "Amazon", "74:c2:46" to "Amazon Echo",
        "b0:fc:0d" to "Amazon Echo", "f0:27:2d" to "Amazon Echo", "0c:47:c9" to "Ring",
        "38:f7:3d" to "Ring", "50:f5:da" to "Amazon Fire TV", "68:54:fd" to "Amazon Fire TV",

        // Xiaomi
        "28:6c:07" to "Xiaomi", "34:ce:00" to "Xiaomi", "64:cc:2e" to "Xiaomi",
        "78:11:dc" to "Xiaomi", "a4:50:46" to "Xiaomi", "f8:a4:5f" to "Xiaomi",
        "50:8f:4c" to "Xiaomi", "7c:1d:d9" to "Xiaomi", "9c:99:a0" to "Xiaomi",
        "c4:0b:cb" to "Xiaomi", "f0:b4:29" to "Xiaomi",

        // Huawei
        "00:24:e4" to "Huawei", "00:e0:fc" to "Huawei", "04:b0:e7" to "Huawei",
        "20:08:ed" to "Huawei", "48:46:fb" to "Huawei", "70:72:3c" to "Huawei",
        "e0:19:1d" to "Huawei", "f4:9f:f3" to "Huawei",

        // LG, Sony
        "00:1e:75" to "LG", "00:1c:62" to "LG", "10:68:3f" to "LG", "a8:16:b2" to "LG",
        "00:18:e7" to "Sony", "00:13:a9" to "Sony", "3c:01:ef" to "Sony PlayStation",
        "00:19:c5" to "Sony PlayStation", "70:9e:29" to "Sony PlayStation",

        // Microsoft / Xbox
        "00:50:f2" to "Microsoft", "28:18:78" to "Microsoft", "00:15:5d" to "Microsoft Hyper-V",
        "7c:1e:52" to "Microsoft Xbox", "98:5f:d3" to "Microsoft Xbox", "d4:e6:b7" to "Xbox",

        // Raspberry Pi / SBCs
        "b8:27:eb" to "Raspberry Pi", "dc:a6:32" to "Raspberry Pi", "e4:5f:01" to "Raspberry Pi",
        "d8:3a:dd" to "Raspberry Pi", "2c:cf:67" to "Raspberry Pi",

        // Espressif (ESP32/ESP8266 — very common in smart plugs, sensors, cheap IoT)
        "24:6f:28" to "Espressif (IoT/ESP32)", "3c:71:bf" to "Espressif (IoT/ESP32)",
        "30:ae:a4" to "Espressif (IoT/ESP32)", "24:0a:c4" to "Espressif (IoT/ESP32)",
        "5c:cf:7f" to "Espressif (IoT/ESP32)", "8c:aa:b5" to "Espressif (IoT/ESP32)",
        "a0:20:a6" to "Espressif (IoT/ESP32)", "cc:50:e3" to "Espressif (IoT/ESP32)",
        "ec:fa:bc" to "Espressif (IoT/ESP32)", "b4:e6:2d" to "Espressif (IoT/ESP32)",

        // Sonoff / Tuya / Broadlink smart-home
        "68:c6:3a" to "Tuya Smart", "d8:f1:5b" to "Tuya Smart", "cc:64:1a" to "ITEAD/Sonoff",

        // Sonos
        "5c:aa:fd" to "Sonos", "b8:e9:37" to "Sonos", "94:9f:3e" to "Sonos", "00:0e:58" to "Sonos",

        // Networking gear
        "00:09:5b" to "Netgear", "00:1b:2f" to "Netgear", "00:1e:2a" to "Netgear",
        "00:14:6c" to "Netgear", "00:1f:33" to "Netgear", "20:4e:7f" to "Netgear",
        "28:c6:8e" to "Netgear", "a0:04:60" to "Netgear", "b0:7f:b9" to "Netgear",
        "c4:04:15" to "Netgear", "e0:46:9a" to "Netgear",
        "50:c7:bf" to "TP-Link", "60:32:b1" to "TP-Link", "14:cc:20" to "TP-Link",
        "ec:08:6b" to "TP-Link", "00:1d:0f" to "TP-Link", "c0:25:e9" to "TP-Link",
        "00:27:19" to "TP-Link", "18:a6:f7" to "TP-Link", "a4:2b:b0" to "TP-Link Kasa",
        "00:1e:58" to "D-Link", "00:15:e9" to "D-Link", "00:26:5a" to "D-Link",
        "1c:af:f7" to "D-Link", "00:0c:29" to "VMware", "00:50:56" to "VMware",
        "24:a4:3c" to "Ubiquiti", "74:83:c2" to "Ubiquiti", "fc:ec:da" to "Ubiquiti",
        "80:2a:a8" to "Ubiquiti", "b4:fb:e4" to "Ubiquiti", "68:d7:9a" to "Ubiquiti",
        "00:0c:42" to "MikroTik", "4c:5e:0c" to "MikroTik", "d4:ca:6d" to "MikroTik",
        "00:40:96" to "Cisco", "00:22:6b" to "Cisco", "00:25:9c" to "Cisco-Linksys",
        "00:1d:7e" to "Cisco-Linksys", "58:6d:8f" to "Cisco-Linksys", "68:7f:74" to "Cisco-Linksys",
        "c0:c1:c0" to "Cisco-Linksys", "b0:be:76" to "ASUS", "1c:87:2c" to "ASUS",
        "38:d5:47" to "ASUS", "50:46:5d" to "ASUS", "ac:22:0b" to "ASUS",
        "00:1a:3f" to "Intelbras",

        // Printers / office
        "00:1b:a9" to "Brother", "00:80:77" to "Brother", "3c:2a:f4" to "HP Printer",
        "d4:85:64" to "HP Printer", "00:17:c8" to "HP Printer", "00:1e:0b" to "Canon",
        "00:00:85" to "Canon", "00:26:ab" to "Epson", "64:eb:8c" to "Epson",

        // NAS
        "00:11:32" to "Synology", "00:08:9b" to "QNAP",

        // Roku / streaming
        "00:0d:4b" to "Roku", "d8:31:34" to "Roku", "b0:a7:37" to "Roku", "cc:6d:a0" to "Roku",
        "8c:49:62" to "Roku",

        // Smart home / cameras
        "00:17:88" to "Philips Hue", "ec:b5:fa" to "Philips Hue", "9c:8e:cd" to "Wyze",
        "2c:aa:8e" to "Arlo/Netgear Cam", "44:07:0b" to "TCL", "18:dd:50" to "Vizio",
        "00:19:63" to "Vizio",

        // Chip/board vendors that end up in generic IoT gear
        "00:03:7f" to "Atheros", "00:0e:8e" to "SparkLAN", "00:1f:1f" to "Realtek",
        "52:54:00" to "QEMU/Virtual", "02:00:00" to "Locally administered",
        "00:00:5e" to "IANA / VRRP"
    )

    fun vendor(mac: String): String? {
        val norm = mac.lowercase().replace('-', ':')
        val prefix = norm.split(':').take(3).joinToString(":")
        if (prefix.length < 8) return null
        map[prefix]?.let { return it }
        // locally administered bit — common for randomized/private MACs (iOS 14+,
        // Android 10+ use a random MAC per network by default for privacy).
        val first = norm.substringBefore(':').toIntOrNull(16) ?: return "Unknown vendor"
        return if (first and 0x02 != 0) "Randomized MAC (privacy address)" else "Unknown vendor"
    }
}
