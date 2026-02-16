# Modèle de Données & Stockage Local

## 1. Philosophie de Stockage : "Database-per-User"
Pour garantir une étanchéité totale des données en collectivité et simplifier la gestion hors-ligne, l'application n'utilise pas une base de données globale unique, mais une base de données **par utilisateur**.

### A. Registre Global (App Level)
Pour afficher la View 1 (Liste des comptes locaux) sans avoir à ouvrir de base de données, l'application utilise les `EncryptedSharedPreferences`.
* **Données stockées :** Liste des utilisateurs connus localement (ex: `[{username: "jeanmarc", offline_ready: true}, ...]`).
* **Avantage :** Ultra-rapide au lancement, sécurisé, et piloté par le `SessionManager`.

### B. Espace Utilisateur (User Level)
Lorsqu'un utilisateur se connecte, l'application "pointe" vers son dossier personnel isolé :
`Internal Storage > Android > data > com.pictotree.app > files > {username}/`

Ce dossier contient :
1. **Le dossier `/images/`** : Contient tous les pictogrammes téléchargés pour cet utilisateur.
2. **La base de données SQLite : `user_data.db`**

---

## 2. Structure de la Base de Données Utilisateur (`user_data.db`)

Puisque cette base est strictement personnelle, les tables sont allégées (aucun `user_id` nécessaire).

### Table : `trees`
Stocke les métadonnées et le contenu de l'arbre. Le parcours visuel n'est pas stocké sous forme de table relationnelle, mais directement conservé dans son format d'origine via le champ `json_data`.

| Colonne | Type | Description |
| :--- | :--- | :--- |
| `id` | INTEGER | Clé primaire locale (Auto-incrément). |
| `remote_id` | INTEGER | L'ID correspondant sur pictotree.eu (pour la synchronisation). |
| `name` | VARCHAR | Nom de l'arbre (ex: "Activité", "Sortie"). |
| `is_public` | BOOLEAN | Indique si l'arbre importé était public ou personnel. |
| `json_data` | TEXT | Le contenu complet de l'arbre (noeuds, liens) au format JSON. |
| `last_sync` | DATETIME| Date de la dernière mise à jour depuis le serveur. |

### Table : `images`
Sert de registre (cache) pour faire le lien entre les images requises par le `json_data` et les fichiers physiques téléchargés sur la tablette.

| Colonne | Type | Description |
| :--- | :--- | :--- |
| `id` | INTEGER | Clé primaire locale. |
| `remote_path` | VARCHAR | Le chemin ou l'URL d'origine de l'image sur le serveur. |
| `local_path` | VARCHAR | Le chemin physique sur la tablette (ex: `images/picto_manger.png`). |
| `name` | VARCHAR | Nom du pictogramme pour l'accessibilité ou la recherche. |

---

## 3. Cycle de vie des données
1. **Importation d'un arbre :** L'app télécharge les infos de la table `tree` du serveur, insère le `json_data` dans la table locale, puis scanne ce JSON pour identifier les images manquantes.
2. **Téléchargement des médias :** Les images manquantes sont téléchargées dans le dossier `/images/` de l'utilisateur, et référencées dans la table locale `images`.
3. **Mode Hors-ligne :** Lors de l'ouverture de la View 4, l'application lit le `json_data`, construit l'interface, et remplace à la volée les URL du serveur par les `local_path` de la table `images`.


## 4. Politique de gestion des fichiers médias (Cache Hors-ligne)

Pour gérer le téléchargement massif d'images (ex: base ARASAAC + uploads utilisateurs personnels) sans recréer l'arborescence complexe du serveur et sans risquer de conflits de noms, l'application utilise une stratégie de **Hachage d'URL (URL Hashing)** via l'algorithme SHA-256.

### A. Le principe du Hachage
Au lieu de conserver le nom de fichier original (qui peut créer des conflits, ex: plusieurs fichiers nommés `image.png`), l'application génère une empreinte cryptographique unique basée sur l'URL distante complète de l'image.

* **URL source (exemple) :** `https://pictotree.eu/static/public/arasaac/pomme.png`
* **Nom local généré :** `8f4b2c9a1d5e...f9a.png` (Hash SHA-256 de l'URL + extension d'origine)

Toutes les images sont stockées "à plat" (sans sous-dossiers) dans le répertoire isolé de l'utilisateur : 
`Internal Storage > ... > files > {username}/images/`

### B. Avantages de cette architecture
1.  **Zéro conflit :** Deux images ayant le même nom d'origine mais provenant de dossiers serveurs différents auront des URL différentes, donc des noms locaux (Hash) strictement différents.
2.  **Anti-doublon naturel (Dédoublonnage) :** Si plusieurs arbres différents utilisent la même image ARASAAC, le hachage de l'URL sera identique. L'application vérifiera la présence de ce fichier et ne le retéléchargera pas (économie de stockage et de data).
3.  **Performances accrues :** La lecture dans un dossier plat unique est beaucoup plus rapide et optimisée pour le système de fichiers Android.

### C. La table de correspondance
Lors du rendu d'un arbre (View 4), l'application lit le champ `json_data`. À chaque fois qu'elle rencontre une URL distante, elle interroge la table SQLite `images` pour trouver le nom du fichier haché correspondant en local, et l'affiche instantanément, même sans connexion internet.