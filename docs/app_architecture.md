# Architecture Globale - Application Pictotree

## 1. Vue d'ensemble
L'application Pictotree est un outil de visualisation et de construction de parcours d'arbres (listes chaînées de pictogrammes). 
Elle est développée "Offline-First" (priorité au mode hors-ligne) et pensée pour un usage en collectivité (multi-comptes sur un même appareil, adaptation Mobile/Tablette).

## 2. Flux de Navigation (Views)

### View 1 : Connexion (Login)
* **Objectif :** Gérer l'accès utilisateur et le mode réseau (En ligne / Hors-ligne).
* **Composants UI :**
  * Liste/Grille des profils locaux déjà enregistrés.
  * Checkbox (ou Switch) : "Mode Hors-ligne".
* **Logique Métier :**
  * **Hors-ligne :** Connexion directe sans mot de passe aux comptes locaux existants. Bloque l'accès aux appels API.
  * **En ligne :** Authentification classique. À la première connexion d'un compte non répertorié : création automatique de l'environnement local (Dossier utilisateur + entrée dans la base SQLite).

### View 2 : Tableau de bord (Dashboard / Profils)
* **Objectif :** Afficher et sélectionner le profil du patient/enfant pour accéder à ses arbres de communication. Conçu avec une approche « Safe Mode » (Hors-ligne) et « Admin Mode » (En ligne).
* **Composants UI :**
  * Icône "Cadenas" en haut à droite indiquant le mode de connexion (Safe / Admin).
  * Liste verticale des profils locaux (gros boutons tactiles centrés).
* **Comportement "Safe Mode" (Hors-ligne) :**
  * Interface épurée, aucun bouton d'édition.
  * Un Tap simple sur un profil ouvre la **View 4** en chargeant les arbres associés à ce profil.
* **Comportement "Admin Mode" (En Ligne) :**
  * L'icône du Cadenas est ouverte.
  * Chaque profil affiche un bouton "Éditer" (icône ⚙️ ou ✏️) sur sa droite.
  * Un large bouton "+ Nouveau Profil" est visible à la fin de la liste.
  * Le Tap sur "Éditer" ouvre la gestion du profil (nom, avatar, et gestion de ses arbres associés via la **View 3**).

### View 3 : Menu de Gestion (Management)
* **Objectif :** Gérer les ressources associées à un Profil spécifique.
* **Composants UI :**
  * Liste réordonnable des arbres associés au profil sélectionné.
  * Bouton "Ajouter un arbre" (ouvre un dialogue d'import public/privé depuis pictotree.eu).
  * Option de suppression d'un arbre pour ce profil (sans forcément le supprimer de la base s'il est utilisé par un autre profil).

### View 4 : Parcours d'Arbre (Core UX)
* **Objectif :** Visualisation, interaction et construction de la liste chaînée.
* **Spécificités :** Interface entièrement responsive. Gère dynamiquement les contraintes d'espace (ex: tiroirs coulissants ou split-screen sur tablette, vue condensée sur mobile).
* **Règle Stricte (Offline-First) :** **Les images doivent TOUJOURS être cherchées en local sur cette vue**. La View 4 est de-corrélée des requêtes HTTP. Au moment du parsing du Payload JSON de la base SQLite, toute URI pointant sur le backend est systématiquement réécrite en URI locale (`file:///.../files/pictograms/...`). Ces fichiers doivent donc avoir été impérativement synchronisés et stockés physiquement en amont lors de l'import des arbres (géré depuis la View 3 / Édition Profil).

---

## 3. Gestion des Données et Stockage

### A. Base de Données (SQLite / Room)
La base de données locale (implémentée via Android Room) est stockée dans le dossier isolé de l'utilisateur. Elle gère la relation dynamique : 1 Utilisateur -> N Profils -> N Arbres.

### A. Registre Global (App Level / User Level)
* **Configuration Utilisateur :**
  * `locale` : Langue globale de l'interface et du TTS.
  * `offline_settings_pin` : Code à 4 chiffres pour protéger l'accès aux réglages (Profils/Langue) en mode hors-ligne.
* **Sécurité & Accès :**
  * **En ligne :** Authentification `pictotree.eu` requise. Donne accès complet (Imports, Recherche online, Paramètres).
  * **Hors-ligne :** Accès direct aux arbres. Le code PIN est requis uniquement pour modifier la langue ou les options de profil. Les fonctions d'importation réseau sont désactivées.

### B. Système de Fichiers (File System)
Pour éviter les conflits en collectivité et faciliter la gestion des données :
* Chaque utilisateur possède un répertoire isolé généré dynamiquement.
* Les images (pictogrammes) et autres médias liés aux arbres y sont sauvegardés pour l'accès hors-ligne.

---

## 4. Internationalisation (i18n) & TTS
* **Conception "English First" :** Tous les textes de l'interface utilisent l'anglais comme langue de base dans `res/values/strings.xml`.
* **Support Multilingue :** Aligné sur le backend Flask (FR, EN, ES, DE, IT, NL, PL).
* **Stratégie "Smart TTS" (Rush 14/15) :**
  * Lors du changement de langue, l'application interroge le moteur Android (`tts.isLanguageAvailable`).
  * **Si `LANG_MISSING_DATA` :** L'application affiche une alerte proposant d'ouvrir les paramètres système Android pour télécharger le pack de voix correspondant.
  * **Si `LANG_NOT_SUPPORTED` :** Un message informe l'utilisateur que seule l'interface sera traduite, la voix restant sur la langue par défaut.
* **Règle de code :** Aucun texte n'est "en dur" (hardcoded) dans les vues ; tous les labels passent par les ID de ressources système.