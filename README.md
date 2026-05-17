# 🕵️‍♂️ Qui-est-ce ? - API Backend

[![Java 21](https://img.shields.io/badge/Java-21-blue.svg)](https://adoptium.net/)
[![Quarkus](https://img.shields.io/badge/Quarkus-Powered-blueviolet.svg)](https://quarkus.io/)
[![CI/CD](https://img.shields.io/badge/CI%2FCD-GitHub_Actions-2088FF.svg?logo=github-actions&logoColor=white)]()

## 🎯 Objectifs du projet

Ce projet contient le code d'une application Java Backend permettant de jouer au jeu du **"Qui est-ce ?"** en utilisant des cartes personnalisées.

Conçu dans un but d'apprentissage et de montée en compétences, l'objectif principal est de fournir une implémentation fonctionnelle simple tout en explorant les capacités du framework **Quarkus**.

*Note : L'implémentation actuelle gère une seule instance de jeu à la fois.*

--- 

## 🗄️ Modèle de données

La base de données permettant le stockage des informations relatives aux cartes et aux packs est une base de données relationnelle suivant le schéma suivant :

```mermaid
erDiagram
    PACK {
        Long id PK
        String name
    }
    
    CARD {
        Long id PK
        String name
        String imageUrl
        Long pack_id FK
    }

    PACK ||--o{ CARD : "contains"
```

Les images sont quant à elles stockées dans un Bucket S3.

---

## 🎮 Logique de Jeu (Moteur d'état)

Le moteur de jeu (`GameEngine`) est géré comme un singleton (`@ApplicationScoped`) et fonctionne comme une machine à états. Il valide les actions des joueurs en fonction de l'état d'avancement de la partie.

Voici le cycle de vie complet d'une partie :

```mermaid
stateDiagram-v2
    [*] --> NOT_STARTED
    
    NOT_STARTED --> PREPARING : create(cards)
    note right of PREPARING
        Distribution aléatoire des 
        cartes à deviner aux joueurs
    end note
    
    PREPARING --> STARTED : start()
    
    STARTED --> STARTED : player1/2Guess()\n[Mauvaise réponse]
    
    STARTED --> PLAYER_1_WINS : player1Guess()\n[Bonne réponse]
    STARTED --> PLAYER_2_WINS : player2Guess()\n[Bonne réponse]
    
    PLAYER_1_WINS --> NOT_STARTED : reset()
    PLAYER_2_WINS --> NOT_STARTED : reset()
```

---

## 🚀 Installation et Lancement en local

### Prérequis
- **Java 21** installé sur l'environnement de développement.
- **Docker** en cours d'exécution. *Indispensable car Quarkus utilise les **Dev Services** (Testcontainers) pour monter automatiquement une base de données PostgreSQL et un bucket S3 au démarrage, sans aucune configuration manuelle requise !*

### Lancer l'application en mode développement
Pour démarrer le serveur avec le Live Coding (Hot Reload) activé, exécuter la commande suivante à la racine du projet :

```bash
./mvnw compile quarkus:dev
```
*(Sous Windows, utiliser `mvnw.cmd compile quarkus:dev`)*

Une fois démarrée, l'application est accessible sur `http://localhost:8080`.
L'interface de développement de Quarkus (Dev UI) est également disponible via `http://localhost:8080/q/dev`.

---

## 🛠️ Technologies & Apprentissages

### ☕ Java 21
- Utilisation des **Records** pour une modélisation concise et immuable des données.
- **Sérialisation JSON :** Mapping automatique entre le format JSON et les DTO (implémentés via les Records Java).


### 🚀 Quarkus Framework
Quarkus est un framework Java "Cloud Native" (Subatomic & Supersonic Java) pensé pour les architectures modernes et les conteneurs. Ce projet m'a permis d'en explorer les fonctionnalités clés :

- **Optimisation au Build (Compile-time Boot) :** Contrairement aux frameworks traditionnels (comme Spring), Quarkus déplace de nombreuses tâches de démarrage (comme le scan des annotations) à la phase de compilation. Résultat : un démarrage extrêmement rapide et une faible consommation mémoire, idéal pour le scaling.
- **Live Coding (Hot Reload) :** Le serveur redémarre instantanément à chaque modification du code, permettant de tester les nouvelles implémentations en temps réel sans perte de contexte.
- **Dev Services :** Création automatique et transparente d'une base de données PostgreSQL et d'un serveur S3 (via Testcontainers) au démarrage du mode dev, sans avoir besoin de fournir de configuration complexe ("Zéro config").
- **Hibernate ORM avec Panache :** Utilisation d'une surcouche facilitant grandement l'accès aux données (implémentation du pattern Active Record / Repository).
- **Conteneurisation native :** Possibilité de construire une image Docker optimisée directement via les commandes Quarkus.
- **QuarkusTest & REST-assured :** Mise en place de tests d'intégration fluides et lisibles pour valider les endpoints de l'API.

#### 📦 Extensions Quarkus utilisées
* **Données :** `quarkus-hibernate-orm-panache`, `quarkus-jdbc-postgresql`
* **Cloud & Stockage :** `quarkus-amazon-s3`, `software.amazon.awssdk`
* **Web & API :** `quarkus-rest-jackson`, `quarkus-smallrye-openapi` (génération automatique du Swagger)
* **Sécurité :** `quarkus-oidc` (Keycloak, Dev Services intégré)
* **WebSocket :** `quarkus-websockets-next` (auth via sous-protocole `Sec-WebSocket-Protocol`)
* **Tests :** `quarkus-junit5-mockito`, `rest-assured`
* **Utilitaires :** `lombok`


#### 🧩 Ajouter une extension
Quarkus permet d'ajouter très facilement de nouvelles dépendances sans modifier manuellement le `pom.xml`. Par exemple, pour ajouter la validation des données (`hibernate-validator`), il suffit de lancer :

```bash
./mvnw quarkus:add-extension -Dextensions="hibernate-validator"
```
*(Si la CLI Quarkus est installée, la commande raccourcie est `quarkus ext add hibernate-validator`)*


---

## 🔐 Authentification (Keycloak)

L'API est sécurisée via **Keycloak** (OIDC), intégré grâce à l'extension `quarkus-oidc`. En mode développement, Quarkus provisionne automatiquement un conteneur Keycloak (Dev Services) et importe le realm `qui-est-ce` depuis `src/main/resources/realm-export.json`.

### Rôles

| Rôle | Droits |
|------|--------|
| `joueur` (`player`) | Lire toutes les ressources ; créer, rejoindre, démarrer, deviner, réinitialiser et supprimer des parties |
| `administrateur` (`admin`) | Tout ce que peut faire un joueur + gérer les packs et les cartes (CRUD) |

> Le rôle `admin` est un rôle composite qui inclut `player` : un administrateur peut donc également jouer.

### Obtenir un token (dev)

Le port Keycloak est affiché dans la Dev UI (`http://localhost:8080/q/dev`) → carte *OpenID Connect*.

```bash
curl -s -X POST http://localhost:<kc-port>/realms/qui-est-ce/protocol/openid-connect/token \
  -d "grant_type=password&client_id=qui-est-ce-back&client_secret=dev-secret&username=player1&password=password" \
  | jq -r .access_token
```

Utilisateurs de test (mot de passe `password`) : `player1`, `player2` (rôle : joueur), `admin` (rôle : administrateur).

### Utiliser le token

**REST :** ajouter le header `Authorization: Bearer <token>` à chaque requête.

**WebSocket :** passer le token via le header `Sec-WebSocket-Protocol` (le seul header que l'API JavaScript `WebSocket` autorise sur un handshake). Côté frontend :

```js
const carrier = encodeURIComponent("quarkus-http-upgrade#Authorization#Bearer " + token);
const ws = new WebSocket(url, ["bearer-token-carrier", carrier]);
```

Avec `wscat` :
```bash
ENC=$(python3 -c "import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1]))" \
  "quarkus-http-upgrade#Authorization#Bearer $TOKEN")
wscat -c "ws://localhost:8080/ws/games" -s "bearer-token-carrier,$ENC"
```

#### Comment ça marche côté Quarkus

Les navigateurs n'autorisent pas l'envoi d'un header `Authorization` sur l'API JavaScript `WebSocket`. Quarkus `websockets-next` fournit un mécanisme natif : un sous-protocole de la forme `quarkus-http-upgrade#<HeaderName>#<HeaderValue>` (URI-encodé) est extrait du `Sec-WebSocket-Protocol` puis promu en header HTTP **avant** que le handler OIDC ne s'exécute. Le JWT n'apparaît donc jamais dans l'URL ni les logs d'accès.

Configuration (`application.properties`) :

```properties
quarkus.websockets-next.server.propagate-subprotocol-headers=true
quarkus.websockets-next.server.supported-subprotocols=bearer-token-carrier
```

Le client envoie deux sous-protocoles : `bearer-token-carrier` (un nom factice que le serveur renvoie pour clore le handshake conformément à la RFC 6455) et `quarkus-http-upgrade#Authorization#Bearer <token>` (URI-encodé, porteur du JWT). Le serveur retire le sous-protocole `quarkus-http-upgrade#…`, injecte le header `Authorization`, et renvoie `bearer-token-carrier` au client. `@Authenticated` et les rôles fonctionnent ensuite exactement comme sur les endpoints REST.

---

## 🌐 Déploiement VPS

### Hôte

VPS Fedora 43 (cloud minimal) — utilisateur `fedora` (sudoer), accès SSH par clé uniquement (`PasswordAuthentication no`, `PermitRootLogin no`). Pare-feu `firewalld` ouvrant 22/80/443. Mises à jour de sécurité automatiques via `dnf5-automatic.timer` (`apply_updates = yes`). Docker + Docker Compose installés. Snapshots hebdomadaires activés côté hébergeur.

### Domaines (production)

Domaine : `lepgu.fr` (OVH). La racine est réservée à un futur portfolio — toutes les URL applicatives sont nichées sous `qui-est-qui.lepgu.fr` :

| Service | URL |
|---------|-----|
| Frontend | `https://qui-est-qui.lepgu.fr` |
| Backend (REST + WebSocket) | `https://api.qui-est-qui.lepgu.fr` |
| Keycloak | `https://auth.qui-est-qui.lepgu.fr` |
| MinIO (URLs d'images) | `https://s3.qui-est-qui.lepgu.fr` |

Enregistrements DNS (A) : `qui-est-qui`, `api.qui-est-qui`, `auth.qui-est-qui`, `s3.qui-est-qui` → IP du VPS. TLS automatique via Let's Encrypt (Caddy, voir tâche 7 du roadmap).

> Ce schéma de nommage est verrouillé dans : Caddyfile, `OIDC_AUTH_SERVER_URL`, `redirectUris` / `webOrigins` du realm Keycloak, `quarkus.http.cors.origins`, et l'URL de base de l'API côté front. Tout changement doit être propagé partout.

---

## 🔐 Configuration Keycloak en production

Le template de realm `qui-est-ce` utilisé en production vit dans le dépôt `qui-est-ce_infra` (`keycloak/realm-export-prod.json`). Il ne contient **aucun utilisateur de test, aucun secret littéral et aucune URI wildcard** ; les secrets sont substitués à l'import via les variables d'environnement du conteneur Keycloak (`${OIDC_CLIENT_SECRET}`, `${KEYCLOAK_ADMIN_SECRET}`).

### Modèle à deux clients (séparation des privilèges)

| Client | Rôle | Secret |
|--------|------|--------|
| `qui-est-ce-back` | Resource server OIDC — valide les tokens entrants. Aucun rôle admin. | `OIDC_CLIENT_SECRET` |
| `qui-est-ce-admin` | Client machine-to-machine — service-account avec `realm-management/manage-users` pour `POST /admin/users`. | `KEYCLOAK_ADMIN_SECRET` |
| `qui-est-ce-front` | Client public utilisé par le SPA (flow standard, audience mapper vers `qui-est-ce-back`). | — |

Une fuite du secret front-channel (`qui-est-ce-back`) ne donne donc pas accès à la création d'utilisateurs.

Au premier démarrage, Keycloak résout les deux placeholders `${…}` depuis son environnement et persiste les secrets en base ; aux redémarrages suivants l'import est ignoré silencieusement. Le backend valide les variables au boot via `SecretGuard` (`src/main/java/admin/SecretGuard.java`) : démarrage avorté si une valeur est vide, placeholder (`change_me`, `dev-secret`, `REPLACE_WITH_PROD_SECRET`) ou identique à l'autre.

### Premier déploiement & rotation

Procédure complète (génération des secrets, câblage `.env`, premier `docker compose up -d`, création du premier admin) dans le README du dépôt `qui-est-ce_infra`. En résumé :

```bash
openssl rand -hex 32   # OIDC_CLIENT_SECRET   (≠ KEYCLOAK_ADMIN_SECRET)
openssl rand -hex 32   # KEYCLOAK_ADMIN_SECRET
```

Le premier admin est créé manuellement dans la console Keycloak (realm `qui-est-ce` → Users → Add user → Role mapping → `admin`) ; les utilisateurs suivants passent par `POST /admin/users` (tâche 8 du roadmap).

**Rotation** : `Clients → <client> → Credentials → Regenerate` dans la console admin → mettre à jour `.env` → `docker compose restart back`.

### Variables d'environnement

Pour les valeurs prod, voir `.env.example`. À noter :

- `S3_ENDPOINT` (interne Docker, ex. `http://minio:9000`) ≠ `S3_PUBLIC_BASE_URL` (publique HTTPS, ex. `https://s3.qui-est-qui.lepgu.fr`). Le premier sert au client S3 du back ; le second est utilisé par `ImageService.getImageUrl()` pour générer les liens d'images consommés par le navigateur (l'endpoint interne déclencherait un échec de résolution DNS et une violation *mixed content*).

---

## 🔌 Mises à jour en temps réel (WebSocket)

Deux endpoints WebSocket permettent aux clients de recevoir les changements d'état sans polling :

| Endpoint | Rôle |
|----------|------|
| `ws://localhost:8080/ws/game/{gameId}` | Suivi d'une partie : reçoit chaque transition d'état et résultat de devinette |
| `ws://localhost:8080/ws/games` | Lobby : reçoit les créations et suppressions de parties |

### Format du message

```json
{
  "gameId": "550e8400-e29b-41d4-a716-446655440000",
  "type": "STATE_CHANGE",
  "gameState": "STARTED",
  "correct": null
}
```

| Champ | Description |
|-------|-------------|
| `type` | `GAME_CREATED` · `STATE_CHANGE` · `DELETED` |
| `gameState` | Nouvel état (`PREPARING`, `STARTED`, `PLAYER_1_WINS`, …). Absent si `DELETED`. |
| `correct` | Présent uniquement après une devinette (`true` / `false`). |

### Exemple de connexion (wscat)

```bash
# Installer wscat
npm install -g wscat

# Encoder le porteur JWT en sous-protocole (URI-encodé, espace -> %20, # -> %23)
ENC=$(python3 -c "import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1]))" \
  "quarkus-http-upgrade#Authorization#Bearer $TOKEN")

# Se connecter au lobby (avec token)
wscat -c "ws://localhost:8080/ws/games" -s "bearer-token-carrier,$ENC"

# Se connecter à une partie spécifique (avec token)
wscat -c "ws://localhost:8080/ws/game/<gameId>" -s "bearer-token-carrier,$ENC"
```

---

## ⚙️ Intégration Continue (CI/CD)

Deux workflows **GitHub Actions** se déclenchent à chaque push sur `main` :

- **`deploy-swagger.yml`** — build de la solution, publication automatique du **Swagger/OpenAPI** sur GitHub Pages (https://fileboss.github.io/qui-est-ce_back_API/) pour une documentation API toujours à jour.
- **`build-and-push.yml`** — exécution complète des tests, construction de l'image JVM (`src/main/docker/Dockerfile.jvm`), publication sur GHCR : `ghcr.io/fileboss/qui-est-ce-back:{latest, sha-<short>}`. L'image est consommée par le `docker-compose.yml` du dépôt `qui-est-ce_infra`.

L'étape *SSH-deploy-to-VPS* n'est pas encore automatisée — les mises à jour sont tirées manuellement sur la VPS (`docker compose pull back && docker compose up -d back`).

---

## 🗺️ Évolutions futures

- [ ] **Multi-sessions :** Passer le serveur en mode multi-games pour gérer plusieurs parties en simultané.
- [ ] **Persistance des parties :** Enregistrer l'état des parties pour les retrouver si le serveur redémarre alors qu'une partie n'est pas terminée.
- [ ] **Architecture distribuée :** Déplacer toute la logique métier du jeu dans un microservice dédié développé en **Rust**.
- [x] **Sécurité :** Authentification via **Keycloak** et sécurisation des API.
- [ ] **Rate Limiting :** Protection contre le spam et les abus sur les API publiques de jeu.