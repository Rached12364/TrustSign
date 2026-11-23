# 🛡️ TrustSign — Signature électronique PAdES et archivage à valeur probante

![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-6DB33F?logo=springboot)
![Angular](https://img.shields.io/badge/Angular-18-DD0031?logo=angular)
![PDFBox](https://img.shields.io/badge/Apache%20PDFBox-3.0-blue)
![BouncyCastle](https://img.shields.io/badge/BouncyCastle-CMS%20%2F%20X.509-555)
![Tests](https://img.shields.io/badge/tests-18%20JUnit-brightgreen)

TrustSign est une plateforme de **circuit de signature électronique** pour des documents PDF :

- on crée un dossier ;
- on désigne les signataires dans un **ordre imposé** ;
- chacun appose une **signature PAdES** réelle (certificat X.509 + CMS), intégrée dans le PDF ;
- une fois le dossier complet, le document est **archivé dans un registre chaîné** (SHA-256). Ce registre rend toute falsification détectable.

Un module de **vérification indépendante** contrôle n'importe quel PDF signé : intégrité, certificat, ajout de données après signature.

> Projet personnel inspiré des métiers de la **confiance numérique** : signature électronique, archivage probant, intégrité documentaire.

![Signature et vérification](docs/signature_et_verification.png)

## ✨ Fonctionnalités

| | |
|---|---|
| ✍️ **Signature PAdES B-B** | `ETSI.CAdES.detached`, SHA-256 / RSA, attribut *signing-certificate-v2*, sauvegarde **incrémentale** : chaque signature préserve les précédentes. |
| 🔢 **Circuit ordonné** | Le serveur impose l'ordre des signataires (`403 « Ce n'est pas encore votre tour »`). Seuls les participants voient le dossier. |
| 🏛️ **PKI interne** | AC racine (RSA 3072) et certificats signataires (RSA 2048, *nonRepudiation*) générés et stockés en PKCS#12. |
| 🔍 **Vérification** | Pour chaque signature : intégrité CMS, certificat émis par l'AC de confiance, validité à la date de signature, couverture du fichier. Verdicts : `VALIDE`, `ALTERE`, `MODIFIE_APRES_SIGNATURE`, `NON_FIABLE`, `AUCUNE_SIGNATURE`. |
| ⛓️ **Archive chaînée** | `hash(n) = SHA-256(position ‖ hash(n-1) ‖ dossier ‖ titre ‖ sha256(document) ‖ date)`. Modifier, supprimer ou insérer une entrée, ou retoucher un fichier archivé, casse la chaîne à cet endroit. |
| 🔐 **Sécurité** | JWT, rôles USER / ADMIN (`@PreAuthorize`), en-têtes CSP, contrôle de l'empreinte du fichier stocké avant chaque signature. |
| 📜 **Journal d'audit** | Connexions, créations, signatures, vérifications, contrôles d'archive. |

### Validation indépendante
Les PDF produits sont reconnus par un outil tiers, **`pdfsig` (Poppler)** :

```
Signature #1: - Signature Type: ETSI.CAdES.detached - Signature Validation: Signature is Valid.
```

*(« issuer isn't trusted » est attendu, puisque l'AC de démonstration n'est pas dans le magasin de confiance du système.)*

## 🖼️ Captures

| Dossiers | Document altéré détecté |
|---|---|
| ![](docs/dossiers.png) | ![](docs/verification_altere.png) |
| **Registre d'archive chaîné** | **Journal d'audit** |
| ![](docs/archive.png) | ![](docs/journal_audit.png) |

## 🏗️ Architecture

```mermaid
flowchart LR
  A["Angular 18<br/>dossiers · signature · vérification · archive"] -->|REST + JWT| B["Spring Boot 3"]
  B --> E["EnvelopeService<br/>circuit ordonné"]
  E --> P["PadesSigner<br/>PDFBox + BouncyCastle CMS"]
  E --> K["PkiService<br/>AC + certificats X.509 (PKCS#12)"]
  E --> R["ArchiveService<br/>registre chaîné SHA-256"]
  B --> V["SignatureVerifier"]
  E & R --> D[("H2 / PostgreSQL")]
  E --> F[("Fichiers PDF")]
```

## 🚀 Lancer

Prérequis : **JDK 17**. L'interface Angular est déjà compilée dans le backend.

```bash
cd backend
./mvnw spring-boot:run        # Windows : mvnw.cmd spring-boot:run
```

Ouvrir **http://localhost:8081**. Quatre dossiers de démonstration sont chargés au démarrage.

| Compte | Mot de passe | Rôle |
|---|---|---|
| `alice` / `bob` / `claire` | `alice123` / `bob123` / `claire123` | Signataires |
| `admin` | `admin123` | Archive + audit |

Avec Docker : `docker compose up --build`.

Tests : `cd backend && ./mvnw test`. Les **18 tests** couvrent la signature, la falsification d'octets, l'ajout après signature, une AC étrangère, l'ordre du circuit, les droits d'accès et la falsification de l'archive.

## ⚖️ Limites (démo honnête)
- **AC de démonstration** : en production, on utiliserait des certificats qualifiés eIDAS / ANCE.
- **Les clés sont conservées par le serveur** : cela correspond à une *signature à distance*. En production, elles seraient dans un **HSM**.
- **Pas encore d'horodatage RFC 3161** (PAdES B-T) ni de données de validation à long terme (B-LT / LTV).
- La vérification utilise une seule ancre de confiance, sans contrôle CRL ni OCSP.

## 👨‍💻 Auteur
**Rached ILAHI**, élève ingénieur en génie informatique, ENICarthage
[GitHub](https://github.com/Rached12364) · voir aussi [TrustDoc](https://github.com/Rached12364/TrustDoc), un assistant documentaire RAG avec intégrité et audit.
