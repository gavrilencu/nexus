package com.example.toolkit.ui.docs

import com.example.toolkit.ui.navigation.NexusRoute

/**
 * Documentație în limba română pentru fiecare modul NEXUS.
 *
 * Conținutul de mai jos descrie STRICT ce face codul existent (motoarele din
 * pachetul `data.*` și ecranele din `ui.screens.*`) — nu au fost inventate
 * capabilități suplimentare. Fiecare intrare e cheiată după [NexusRoute.route]
 * și afișată din `ui/NexusApp.kt` printr-un buton de ajutor (info) din bara
 * de sus, într-un bottom sheet.
 */
data class ModuleDoc(
    val route: String,
    val title: String,
    val tagline: String,
    val overview: String,
    val howItWorks: List<String>,
    val usage: List<String>,
    val limitations: List<String> = emptyList()
)

val moduleDocs: Map<String, ModuleDoc> = listOf(
    ModuleDoc(
        route = NexusRoute.Dashboard.route,
        title = "Dashboard",
        tagline = "Punctul de plecare — căutare și acces rapid la toate modulele",
        overview = "Ecranul principal listează toate cele 15 module NEXUS grupate pe categorii " +
            "(Recon & Network, Intel & Vulns, System & Shell, Crypto & Tokens) și oferă o casetă " +
            "de căutare live care filtrează modulele după nume sau descriere.",
        howItWorks = listOf(
            "Lista de module (titlu, subtitlu, iconiță, rută, categorie) e definită o singură dată " +
                "în NexusModules.kt și e refolosită atât de Dashboard cât și de meniul lateral (drawer), " +
                "ca să nu existe două surse de adevăr care pot ajunge nesincronizate.",
            "Căutarea filtrează local, instant, fără cereri de rețea — pur și simplu compară textul " +
                "introdus cu titlul și subtitlul fiecărui modul."
        ),
        usage = listOf(
            "Scrie în caseta de căutare pentru a filtra modulele (ex: \"dns\", \"wifi\", \"hash\").",
            "Atinge un card de modul pentru a naviga direct la el.",
            "Deschide meniul lateral (☰) pentru navigare pe categorii."
        )
    ),
    ModuleDoc(
        route = NexusRoute.Recon.route,
        title = "Domain Recon",
        tagline = "Radiografie completă a unui domeniu: DNS, HTTP, TLS, geo, security headers",
        overview = "Introduci un domeniu sau host și NEXUS rulează în paralel mai multe verificări " +
            "pentru a construi o imagine de ansamblu asupra țintei.",
        howItWorks = listOf(
            "Rezolvare DNS către adresele IP ale hostului.",
            "Probă HTTP: status code, header Server, header X-Powered-By, Content-Type.",
            "Probă TLS: informații despre certificatul X.509 al conexiunii securizate.",
            "Geolocalizare IP: țară, oraș, ISP/organizație pe baza primei adrese IP găsite.",
            "Verificare a 7 headere de securitate: Strict-Transport-Security, Content-Security-Policy, " +
                "X-Frame-Options, X-Content-Type-Options, Referrer-Policy, Permissions-Policy, X-XSS-Protection.",
            "Toate cele 4 verificări (DNS, HTTP, geo, TLS) rulează concurent (coroutines async), nu secvențial, " +
                "pentru viteză."
        ),
        usage = listOf(
            "Introdu un domeniu (ex: example.com) și apasă butonul de analiză.",
            "Rezultatele apar pe secțiuni: DNS, HTTP, TLS, Geo, Security Headers."
        ),
        limitations = listOf(
            "Folosește doar pe domenii/sisteme pe care ai autorizație scrisă să le testezi."
        )
    ),
    ModuleDoc(
        route = NexusRoute.Ports.route,
        title = "Port Scanner",
        tagline = "Scanare de porturi comune pe un host, cu socket-uri concurente",
        overview = "Testează rapid care porturi TCP comune (servicii cunoscute) sunt deschise pe un host, " +
            "conectând simultan mai multe socket-uri pentru viteză.",
        howItWorks = listOf(
            "Pentru fiecare port din lista de porturi comune, deschide un socket TCP cu timeout scurt.",
            "Scanarea rulează cu concurență controlată, astfel încât progresul e afișat live pe măsură ce " +
                "fiecare port răspunde sau expiră."
        ),
        usage = listOf(
            "Introdu un host sau IP și pornește scanarea.",
            "Urmărește progresul live; porturile deschise sunt evidențiate."
        ),
        limitations = listOf(
            "Scanează doar host-uri pe care ai autorizație să le testezi."
        )
    ),
    ModuleDoc(
        route = NexusRoute.Traffic.route,
        title = "Traffic Monitor",
        tagline = "Trimite cereri HTTP și inspectează timing, headere și preview de conținut",
        overview = "Permite emiterea de cereri HTTP către un endpoint și inspectarea rezultatului: timp de " +
            "răspuns, headere primite și un preview al corpului răspunsului.",
        howItWorks = listOf(
            "Cererea e trimisă prin clientul HTTP intern al aplicației; se măsoară timpul de răspuns.",
            "Headerele răspunsului și un preview al body-ului sunt afișate pentru inspecție rapidă."
        ),
        usage = listOf(
            "Introdu URL-ul țintă și declanșează cererea.",
            "Verifică timpul de răspuns, headerele și preview-ul de conținut."
        )
    ),
    ModuleDoc(
        route = NexusRoute.Osint.route,
        title = "OSINT Lookup",
        tagline = "Verifică existența unui username pe 30+ platforme publice",
        overview = "Pentru un username dat, verifică în paralel existența unui profil pe peste 30 de " +
            "platforme (rețele sociale, forumuri, coduri etc.), folosind verificări conștiente de conținut " +
            "ca să evite fals-pozitive.",
        howItWorks = listOf(
            "Fiecare platformă are un mod de verificare: STATUS (se bazează doar pe codul HTTP), " +
                "BODY (caută markeri specifici în HTML/JSON), JSON_API (endpoint dedicat), sau " +
                "REDIRECT (un 3xx fără follow înseamnă de obicei profil lipsă / login wall).",
            "Cererile rulează concurent, cu un semafor care limitează numărul de cereri simultane, " +
                "folosind un User-Agent de browser mobil real.",
            "\"HIT\" înseamnă că au fost confirmați markeri de profil real — paginile din spatele unui " +
                "login wall (ex: Instagram/X) NU sunt numărate ca hit, tocmai ca să nu inducă în eroare."
        ),
        usage = listOf(
            "Introdu un username și pornește căutarea.",
            "Rezultatele arată platformele cu HIT confirmat vs. cele fără profil găsit."
        ),
        limitations = listOf(
            "Doar surse publice — nicio autentificare sau ocolire de login wall."
        )
    ),
    ModuleDoc(
        route = NexusRoute.Api.route,
        title = "API Lab",
        tagline = "Construiește cereri HTTP personalizate și inspectează răspunsul complet",
        overview = "Un laborator pentru a construi manual cereri către API-uri și a inspecta status code, " +
            "headere și payload-ul răspunsului.",
        howItWorks = listOf(
            "Cererea (metodă, URL, eventual body/headere) e trimisă prin clientul HTTP intern.",
            "Răspunsul e afișat structurat: status, headere, payload."
        ),
        usage = listOf(
            "Configurează cererea (metodă + URL) și trimite-o.",
            "Inspectează status code-ul, headerele și payload-ul primit."
        )
    ),
    ModuleDoc(
        route = NexusRoute.Hash.route,
        title = "Hash / Encoder",
        tagline = "Laborator local de criptografie de bază — nimic nu pleacă din telefon",
        overview = "Calculează hash-uri și conversii comune de encoding, complet local (fără rețea).",
        howItWorks = listOf(
            "Hash: MD5, SHA-1, SHA-256, SHA-512 via java.security.MessageDigest.",
            "Base64: encode/decode.",
            "URL encoding: encode/decode.",
            "Hex: encode/decode (conversie text ↔ șir hexazecimal)."
        ),
        usage = listOf(
            "Scrie textul de intrare.",
            "Rezultatele pentru toate formatele se calculează instant, local pe telefon."
        )
    ),
    ModuleDoc(
        route = NexusRoute.Dns.route,
        title = "DNS Dig",
        tagline = "Interoghează toate tipurile de înregistrări DNS pentru un host",
        overview = "Interoghează în paralel înregistrările A, AAAA, MX, NS, TXT, CNAME și SOA pentru un host.",
        howItWorks = listOf(
            "A/AAAA sunt rezolvate atât local (prin rezolverul de sistem Android) cât și prin " +
                "DNS-over-HTTPS (DoH), iar rezultatele sunt combinate.",
            "MX, NS, TXT, CNAME, SOA sunt interogate exclusiv prin DoH (cerere HTTPS către un resolver DNS public).",
            "Toate cele 9 interogări rulează concurent pentru viteză."
        ),
        usage = listOf(
            "Introdu un host și apasă interogare.",
            "Rezultatele sunt grupate pe tip de înregistrare."
        )
    ),
    ModuleDoc(
        route = NexusRoute.Subdomain.route,
        title = "Subdomain Finder",
        tagline = "Descoperă subdomenii reale printr-o listă de cuvinte + rezolvare DNS live",
        overview = "Testează sute de prefixe comune (www, api, mail, dev, staging, vpn, admin etc.) " +
            "împotriva domeniului țintă și păstrează doar subdomeniile care chiar există.",
        howItWorks = listOf(
            "Pentru fiecare cuvânt din listă, se construiește subdomain.domeniu și se încearcă rezolvarea DNS.",
            "Rezolvările rulează concurent, cu un semafor (Semaphore/withPermit) care limitează numărul " +
                "de interogări simultane ca să nu supraîncarce rețeaua.",
            "Rezultatele sunt verificate suplimentar cu o cerere HTTP (status code) pentru a confirma că " +
                "hostul chiar răspunde.",
            "DNS wildcard (domenii care rezolvă ORICE subdomeniu, chiar inexistent, către aceeași adresă) " +
                "este detectat automat și filtrat, ca să nu apară subdomenii false."
        ),
        usage = listOf(
            "Introdu domeniul țintă și pornește scanarea.",
            "Rezultatele apar live, pe măsură ce sunt confirmate subdomenii reale."
        ),
        limitations = listOf(
            "Scanează doar domenii pe care ai autorizație să le testezi."
        )
    ),
    ModuleDoc(
        route = NexusRoute.Jwt.route,
        title = "JWT Lab",
        tagline = "Decodează un token JWT local — header, payload și claims",
        overview = "Descompune un JSON Web Token în cele trei părți și afișează conținutul header-ului " +
            "și payload-ului în clar.",
        howItWorks = listOf(
            "Tokenul e împărțit după caracterul '.'; partea de header și cea de payload sunt Base64-decodate " +
                "local, direct pe telefon.",
            "Sunt extrase claim-urile uzuale: algoritm (alg), subject (sub), issuer (iss), audience (aud), " +
                "issued-at (iat), expires-at (exp), plus un flag \"expired\" calculat comparând exp cu ora curentă.",
            "Toate claim-urile din payload sunt listate ca o hartă cheie-valoare."
        ),
        usage = listOf(
            "Lipește un token JWT complet (header.payload.semnătură).",
            "Vezi claim-urile decodate și dacă tokenul a expirat."
        ),
        limitations = listOf(
            "NU verifică semnătura tokenului — doar decodează conținutul (header + payload) în clar. " +
                "Nu confirmă că tokenul e valid sau nefalsificat."
        )
    ),
    ModuleDoc(
        route = NexusRoute.Cve.route,
        title = "CVE Lookup",
        tagline = "Caută vulnerabilități reale în baza de date NVD (NIST)",
        overview = "Caută vulnerabilități publicate, fie după ID exact (CVE-2021-44228), fie după cuvânt " +
            "cheie (ex: \"apache log4j\"), folosind API-ul public NVD 2.0.",
        howItWorks = listOf(
            "Dacă textul introdus începe cu \"CVE-\", se face o interogare exactă după cveId.",
            "Altfel, se face o căutare după keywordSearch, cu un număr limitat de rezultate.",
            "Pentru fiecare rezultat sunt afișate: descriere, severitate, scor CVSS, dată publicare și link."
        ),
        usage = listOf(
            "Introdu un ID de CVE sau un cuvânt cheie și caută.",
            "Rezultatele arată severitatea și scorul, cu link către detalii."
        )
    ),
    ModuleDoc(
        route = NexusRoute.Ip.route,
        title = "IP Tools",
        tagline = "Rezolvare, reverse DNS, geolocalizare și test de conectivitate pentru un IP/host",
        overview = "Pentru un IP sau host introdus, adună informații de rezolvare, reverse DNS, " +
            "geolocalizare (țară, oraș, ISP, organizație, AS, fus orar) și testează dacă porturile 80/443 " +
            "sunt accesibile, inclusiv latența conexiunii pe 443.",
        howItWorks = listOf(
            "Detectează dacă inputul e deja un IP sau trebuie rezolvat ca host.",
            "Rulează în paralel: rezolvare DNS, reverse DNS, geolocalizare IP și teste de conectivitate " +
                "pe portul 80 și 443 (măsurând timpul de conectare pe 443)."
        ),
        usage = listOf(
            "Introdu un IP sau un host și apasă analiză.",
            "Vezi rezultatele grupate: rezolvare, geo, conectivitate."
        )
    ),
    ModuleDoc(
        route = NexusRoute.Person.route,
        title = "Person Search",
        tagline = "Agregator OSINT public după nume, telefon sau cuvânt cheie",
        overview = "Caută urme publice pentru un nume, număr de telefon sau cuvânt cheie, agregând link-uri " +
            "din surse publice (motoare de căutare, rețele sociale, arhive, paste-uri) și verificând existența " +
            "unor profile pe platforme cunoscute.",
        howItWorks = listOf(
            "Construiește interogări către surse și platforme publice pe baza inputului (nume/telefon/username).",
            "Rulează sondele de platformă concurent (cu semafor pentru limitare), colectând link-uri, " +
                "note și statusul fiecărei sonde (exists: da/nu/necunoscut)."
        ),
        usage = listOf(
            "Introdu numele, telefonul sau cuvântul cheie și alege modul de căutare.",
            "Rezultatele apar ca listă de link-uri și sonde de platformă."
        ),
        limitations = listOf(
            "Accesează DOAR surse publice de pe web (motoare de căutare, rețele sociale, arhive). " +
                "NU are acces la baze de date private ale operatorilor telefonici sau ale instituțiilor " +
                "guvernamentale — acestea necesită autoritate legală, nu sunt \"tot internetul\"."
        )
    ),
    ModuleDoc(
        route = NexusRoute.Hibp.route,
        title = "Have I Been Pwned",
        tagline = "Breșe de date reale prin API-ul oficial HIBP + verificare parole prin k-anonimat",
        overview = "Verifică dacă o adresă de email a apărut în breșe de date cunoscute și dacă o parolă " +
            "a fost văzută în scurgeri de date, folosind serviciul oficial haveibeenpwned.com.",
        howItWorks = listOf(
            "Verificarea de email interoghează API-ul oficial HIBP (necesită o cheie API introdusă de tine — " +
                "e un serviciu plătit de la HIBP, nu gratuit).",
            "Verificarea de parolă folosește modelul k-anonimat: parola e hash-uită local (SHA-1), se trimite " +
                "doar primele 5 caractere ale hash-ului către API, iar potrivirea completă se face local — " +
                "parola în sine nu părăsește niciodată telefonul."
        ),
        usage = listOf(
            "Introdu cheia API HIBP (o singură dată) pentru verificarea de email.",
            "Verifică un email pentru breșe/pastes, sau o parolă pentru expunere (gratuit, fără cheie API)."
        ),
        limitations = listOf(
            "Verificarea de email necesită o cheie API HIBP validă (plătită) — fără ea, doar verificarea " +
                "de parolă funcționează."
        )
    ),
    ModuleDoc(
        route = NexusRoute.WifiMonitor.route,
        title = "Wi‑Fi Monitor",
        tagline = "Dispozitive din rețeaua locală + traficul PROPRIU al acestui telefon",
        overview = "Are două părți: (1) descoperirea dispozitivelor din rețeaua Wi‑Fi locală (IP, MAC, " +
            "producător, nume, model) și (2) captarea și inspectarea traficului de rețea AL ACESTUI TELEFON, " +
            "cu detectarea parolelor/emailurilor trimise necriptat.",
        howItWorks = listOf(
            "Descoperire dispozitive: scanare ping pe subrețea + scanare porturi comune, combinată cu " +
                "identificare mDNS (NsdManager — protocolul Bonjour/Zeroconf), descoperire SSDP/UPnP " +
                "(cerere multicast M-SEARCH + citirea XML-ului de descriere al dispozitivului pentru " +
                "producător/model) și o bază extinsă de peste 250 de prefixe MAC (OUI) pentru identificarea " +
                "producătorului. Aceste surse sunt combinate cu prioritate (mDNS > SSDP > OUI) pentru cel mai " +
                "bun nume posibil.",
            "Captare trafic: printr-un VpnService local, aplicația vede pachetele IP care intră/ies din " +
                "PROPRIUL telefon (nu din alte telefoane din rețea — Android nu permite asta fără root/mod " +
                "monitor pe Wi‑Fi, pe care telefoanele obișnuite nu îl au).",
            "Fiecare pachet e analizat (protocol, porturi, steaguri TCP, un preview de până la 1400 bytes " +
                "din payload) și clasificat (TCP/UDP/DNS/ICMP).",
            "Filtrul \"Credențiale\" (CREDS) scanează preview-ul payload-ului fiecărui pachet cu expresii " +
                "regulate care caută: adrese de email, câmpuri de formular gen password/user/token, câmpuri " +
                "JSON similare, header Authorization Basic (decodat din Base64) sau Bearer, și cookie-uri de " +
                "sesiune — util pentru a găsi date sensibile trimise necriptat (HTTP, nu HTTPS) de propriile " +
                "tale aplicații.",
            "Emailurile detectate pot fi selectate dintr-o listă, ceea ce filtrează automat lista de pachete " +
                "la doar cele care conțin acel email."
        ),
        usage = listOf(
            "Tab \"DEVICES\": vezi dispozitivele din rețea, cu nume/producător/model când sunt disponibile.",
            "Tab \"TRAFFIC\": pornește captarea (cere permisiune VPN Android), apoi filtrează după " +
                "protocol sau după \"Credențiale\".",
            "În filtrul Credențiale, atinge un email din listă pentru a vedea doar pachetele legate de el."
        ),
        limitations = listOf(
            "Traficul detaliat vizibil e DOAR al acestui telefon — nu poți citi pachetele altor " +
                "telefoane/dispozitive din rețea fără root, aceasta fiind o limitare a platformei Android, " +
                "nu a aplicației.",
            "Detectarea numelor de dispozitive depinde de ce expun ele prin mDNS/SSDP/MAC — unele " +
                "dispozitive moderne folosesc adrese MAC randomizate și pot rămâne fără nume identificabil."
        )
    ),
    ModuleDoc(
        route = NexusRoute.LinuxTerminal.route,
        title = "Linux Terminal",
        tagline = "Un Ubuntu 24.04 real, rulat pe telefon prin PRoot — cu apt, servicii de fundal, tot",
        overview = "Rulează un rootfs Ubuntu 24.04 real (nu simulat) direct pe telefon, folosind PRoot. " +
            "Poți instala pachete reale cu apt, rula servere/servicii și continua să le lași pornite chiar " +
            "și cu aplicația în fundal.",
        howItWorks = listOf(
            "La prima pornire se descarcă rootfs-ul Ubuntu (~28 MB) și e stocat în spațiul privat al " +
                "aplicației (nu necesită permisiune de stocare).",
            "Shell-ul e un proces real, ținut într-un singur obiect global (LinuxEnvironmentHolder) partajat " +
                "între interfață și un Service de fundal, ca sesiunea să nu se piardă la schimbarea de ecran.",
            "\"Run in background\": pornește un Foreground Service Android (cu notificare obligatorie) " +
                "care ține procesul shell-ului — și orice serviciu pornit în el (server web, etc.) — " +
                "protejat de la oprirea automată de către sistem când aplicația e minimizată.",
            "Panoul de servicii oferă comenzi reale predefinite: pornire server web demo pe portul 8080 " +
                "(python3 -m http.server, rulat cu nohup în fundal), listare joburi active, listare porturi " +
                "deschise, oprirea serverului.",
            "Butoanele de instalare rapidă (Update, Python, Node, Git, nginx, OpenSSH) rulează comenzi apt " +
                "reale.",
            "O bară de simboluri (|, &&, &, >, >>, <, ~, /, -, _, :, spațiu, ghilimele, *) facilitează " +
                "scrisul comenzilor Linux pe tastatura de telefon.",
            "Output-ul terminalului poate fi selectat și copiat (SelectionContainer)."
        ),
        usage = listOf(
            "Deschide modulul — la prima folosire se descarcă automat rootfs-ul Ubuntu.",
            "Scrie comenzi Linux reale ca într-un terminal obișnuit; folosește bara de simboluri pentru " +
                "caractere speciale.",
            "Activează \"Run in background\" înainte să minimizezi aplicația, dacă vrei ca un serviciu " +
                "pornit (ex: server web) să rămână activ.",
            "Folosește butoanele rapide pentru a instala Python, Node, Git, nginx sau OpenSSH fără să " +
                "tastezi comanda manual."
        ),
        limitations = listOf(
            "Necesită conexiune la internet pentru descărcarea inițială a rootfs-ului (~28 MB) și pentru " +
                "orice pachet instalat ulterior cu apt.",
            "Rularea în fundal necesită permisiunea de notificări (Android 13+) pentru afișarea notificării " +
                "obligatorii a Foreground Service-ului."
        )
    ),
    ModuleDoc(
        route = NexusRoute.DirScan.route,
        title = "Content Discovery",
        tagline = "Brute-force de directoare și fișiere pe un site (stil dirb / gobuster / ffuf)",
        overview = "Testează o listă de căi comune (admin, login, .env, .git/HEAD, backup.zip, api, actuator " +
            "etc.) împotriva unui site și păstrează doar căile care par să existe cu adevărat, cu status live.",
        howItWorks = listOf(
            "Pentru fiecare intrare din wordlist se face o cerere GET către base_url + cale, cu concurență " +
                "controlată printr-un semafor.",
            "Redirect-urile NU sunt urmărite, așa că un 301/302 către un login rămâne vizibil (semn util).",
            "Rezultatele sunt filtrate: 404 este ignorat, iar codurile interesante (200, 301/302, 401, 403, " +
                "500 etc.) sunt raportate cu status, dimensiune și eventual header-ul Location.",
            "Detectare soft-404: înainte de scanare se cere o cale aleatoare inexistentă; dacă serverul " +
                "răspunde tot cu 200, rezultatele 200 cu aceeași dimensiune sunt considerate false și filtrate."
        ),
        usage = listOf(
            "Introdu URL-ul de bază (ex: https://target.com) și apasă Start scan.",
            "Urmărește bara de progres și lista de căi găsite, colorată după codul de status.",
            "Apasă Stop pentru a opri scanarea în orice moment."
        ),
        limitations = listOf(
            "Folosește doar pe site-uri pe care ai autorizație scrisă să le testezi.",
            "Wordlist-ul e unul compact, comun — nu înlocuiește liste mari dedicate (ex: SecLists)."
        )
    ),
    ModuleDoc(
        route = NexusRoute.Fingerprint.route,
        title = "Web Fingerprint",
        tagline = "Detectează stack-ul unui site: server, limbaj, framework, CMS, CDN/WAF, biblioteci JS",
        overview = "Descarcă pagina o singură dată și deduce tehnologiile folosite din headerele răspunsului, " +
            "numele cookie-urilor (Set-Cookie) și markeri din HTML — similar cu WhatWeb / Wappalyzer.",
        howItWorks = listOf(
            "Analizează headere precum Server și X-Powered-By pentru server web și limbaj.",
            "Recunoaște cookie-uri caracteristice: PHPSESSID → PHP, JSESSIONID → Java, laravel_session → " +
                "Laravel, csrftoken → Django, connect.sid → Node/Express etc.",
            "Caută markeri în HTML: /wp-content/ → WordPress, __NEXT_DATA__ → Next.js, ng-version → Angular, " +
                "data-reactroot → React, cdn.shopify.com → Shopify și multe altele.",
            "Identifică CDN/WAF din headere: cf-ray → Cloudflare, x-amz-cf-id → CloudFront, x-akamai → Akamai, " +
                "x-sucuri-id → Sucuri.",
            "Afișează și un rezumat al headerelor de securitate (HSTS, CSP, X-Frame-Options etc.)."
        ),
        usage = listOf(
            "Introdu un domeniu sau URL și apasă Fingerprint.",
            "Vezi tehnologiile detectate (cu categoria și dovada), cookie-urile și headerele de securitate."
        ),
        limitations = listOf(
            "Detectare pasivă, pe baza semnăturilor cunoscute — poate rata tehnologii ascunse în spatele unui " +
                "CDN/WAF sau ofuscate.",
            "Nu trimite payload-uri și nu exploatează nimic — doar citește răspunsul public al paginii."
        )
    ),
    ModuleDoc(
        route = NexusRoute.Whois.route,
        title = "WHOIS / RDAP",
        tagline = "Date de înregistrare pentru domenii și adrese IP, prin protocolul RDAP (JSON peste HTTPS)",
        overview = "Interoghează informațiile de înregistrare ale unui domeniu sau ale unei adrese IP folosind " +
            "RDAP — succesorul modern, structurat (JSON), al vechiului WHOIS pe portul 43.",
        howItWorks = listOf(
            "Detectează automat dacă inputul e un IP (IPv4/IPv6) sau un domeniu.",
            "Trimite o cerere la rdap.org, care redirecționează („bootstrap”) automat către registrul/RIR-ul " +
                "autoritar potrivit (registrar de domeniu sau RIPE/ARIN/APNIC pentru IP).",
            "Pentru domenii extrage: handle, status-uri, cronologia evenimentelor (înregistrare, expirare, " +
                "ultima modificare), name-serverele, entitățile (ex: registrar) și starea DNSSEC.",
            "Pentru IP-uri extrage: handle, nume, intervalul de adrese, țara și organizația.",
            "Numele entităților sunt extrase din structura jCard/vcardArray din răspunsul RDAP."
        ),
        usage = listOf(
            "Introdu un domeniu (ex: example.com) sau un IP și apasă Lookup.",
            "Rezultatele apar pe secțiuni: identitate, status, cronologie, entități, name-servere."
        ),
        limitations = listOf(
            "Unele TLD-uri vechi nu au încă RDAP, sau ascund datele de contact din motive de confidențialitate " +
                "(GDPR) — atunci vei vedea mai puține câmpuri."
        )
    ),
    ModuleDoc(
        route = NexusRoute.Cors.route,
        title = "CORS Scanner",
        tagline = "Detectează configurări greșite de CORS care permit furt de date cross-origin",
        overview = "Trimite mai multe headere Origin construite special și analizează răspunsul " +
            "(Access-Control-Allow-Origin și Access-Control-Allow-Credentials) pentru a găsi slăbiciuni " +
            "clasice de CORS.",
        howItWorks = listOf(
            "Reflectare origine arbitrară: trimite o origine atacator; dacă serverul o reflectă în ACAO, " +
                "orice site poate citi răspunsul.",
            "Origine null: dacă serverul acceptă Origin: null, poate fi exploatat din iframe-uri sandbox.",
            "Bypass prefix/sufix: testează origini de tip target.com.evil.com sau evil-target.com pentru a " +
                "prinde verificări slabe (bazate pe „conține”).",
            "Fiecare test primește o severitate: HIGH dacă reflectă originea ȘI ACAC=true (date autentificate " +
                "expuse), MEDIUM fără credentials, INFO pentru ACAO=* (API public), OK dacă nu e vulnerabil."
        ),
        usage = listOf(
            "Introdu URL-ul endpoint-ului (ideal un API care întoarce date) și apasă Scan CORS.",
            "Verifică fiecare test: originea trimisă, ACAO/ACAC primite și verdictul cu severitate."
        ),
        limitations = listOf(
            "Testează doar aplicații pe care ai autorizație să le verifici.",
            "Verifică o singură cerere GET simplă per origine — nu simulează preflight-uri complexe cu headere " +
                "custom."
        )
    ),
    ModuleDoc(
        route = NexusRoute.HttpMethods.route,
        title = "HTTP Methods",
        tagline = "Ce verbe HTTP acceptă serverul și care sunt periculoase (PUT/DELETE/TRACE)",
        overview = "Testează fiecare metodă HTTP (GET, POST, PUT, PATCH, DELETE, OPTIONS, HEAD, TRACE) împotriva " +
            "unui URL și raportează care par acceptate, semnalând verbele periculoase lăsate active.",
        howItWorks = listOf(
            "Trimite o cerere pentru fiecare metodă și interpretează codul de status: 405/501 (și 400) înseamnă " +
                "„nepermis”, restul înseamnă că metoda e tratată de server.",
            "Citește header-ul Allow din răspunsul la OPTIONS, care listează metodele declarate de server.",
            "Marchează ca periculoase metodele PUT/DELETE/PATCH/TRACE care par active (pot permite modificarea " +
                "resurselor sau atacuri Cross-Site Tracing).",
            "Redirect-urile nu sunt urmărite, ca statusul real al fiecărei metode să rămână vizibil."
        ),
        usage = listOf(
            "Introdu un URL și apasă Test methods.",
            "Vezi fiecare metodă cu status-ul ei; verbele periculoase active sunt evidențiate cu DANGER."
        ),
        limitations = listOf(
            "Un status „acceptat” nu garantează exploatabilitate — confirmă manual dacă metoda chiar modifică " +
                "resurse înainte de a trage concluzii.",
            "Metoda CONNECT nu e testată (nesuportată de clientul HTTP)."
        )
    ),
    ModuleDoc(
        route = NexusRoute.HashCrack.route,
        title = "Hash Cracker",
        tagline = "Identifică tipul unui hash și încearcă spargerea lui cu un dicționar, complet local",
        overview = "Ghicește algoritmul unui hash după lungime/format și apoi încearcă un atac de dicționar " +
            "(dictionary attack) recalculând hash-ul fiecărei parole comune și comparând — totul pe telefon, " +
            "fără rețea.",
        howItWorks = listOf(
            "Identificare: după lungimea în hex (32→MD5, 40→SHA-1, 64→SHA-256, 128→SHA-512 etc.) și după " +
                "prefixe speciale (\$2y\$→bcrypt, \$6\$→SHA-512-crypt, \$argon2→Argon2).",
            "Dictionary attack: pentru hash-uri hex simple, recalculează hash-ul fiecărui candidat dintr-o " +
                "listă încorporată de parole comune (stil rockyou) și compară cu ținta.",
            "Poți adăuga cuvinte proprii (separate prin virgulă, spațiu sau linie nouă) care se adaugă la " +
                "începutul dicționarului.",
            "Doar algoritmii cu lungimea potrivită sunt încercați, pentru viteză; dacă lungimea e necunoscută, " +
                "se încearcă MD5/SHA-1/SHA-256/SHA-512."
        ),
        usage = listOf(
            "Lipește un hash și, opțional, cuvinte extra pentru dicționar.",
            "Apasă Identify & crack: vezi tipul probabil de hash și, dacă e găsit, parola în clar."
        ),
        limitations = listOf(
            "bcrypt / Argon2 / hash-urile cu sare NU pot fi sparte aici — sunt doar identificate.",
            "Dicționarul încorporat e mic (parole foarte comune) — un hash cu parolă puternică nu va fi găsit.",
            "Sparge doar hash-uri pe care ai dreptul legal să le testezi."
        )
    )
).associateBy { it.route }
