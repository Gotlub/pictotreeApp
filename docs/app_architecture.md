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

### View 2 : Tableau de bord (Dashboard)
* **Objectif :** Afficher et sélectionner les arbres disponibles pour l'utilisateur connecté.
* **Composants UI :**
  * Liste cliquable des arbres locaux (la sélection redirige vers la **View 4**).
  * *Conditionnel (si mode En Ligne actif) :* Boutons "Menu de gestion" et "Créer un nouvel arbre".
* **Comportement "Créer un nouvel arbre" :**
  * Ouverture d'un **DialogFragment** (fenêtre superposée).
  * Sélection du type d'import depuis `pictotree.eu` via boutons radio exclusifs : "Arbre Public" ou "Arbre Personnel".
  * Liste déroulante (Spinner) mise à jour dynamiquement selon le type choisi.
  * Boutons d'action : "Annuler" et "Importer" (le bouton "Importer" est désactivé / grisé tant qu'aucun arbre de la liste n'est sélectionné).

### View 3 : Menu de Gestion (Management)
* **Objectif :** Gérer les ressources de l'utilisateur.
* **Composants UI :**
  * Liste déroulante de sélection des arbres stockés localement.
  * Option contextuelle : "Supprimer" (efface l'arbre de la BDD SQLite et nettoie les fichiers médias associés).
  * *Note pour plus tard :* Cet espace accueillera les futurs paramètres d'options graphiques pour la View 4.

### View 4 : Parcours d'Arbre (Core UX)
* **Objectif :** Visualisation, interaction et construction de la liste chaînée.
* **Spécificités :** Interface entièrement responsive. Gère dynamiquement les contraintes d'espace (ex: tiroirs coulissants ou split-screen sur tablette, vue condensée sur mobile).

---

## 3. Gestion des Données et Stockage

### A. Base de Données (SQLite)
La base de données locale (implémentée idéalement via la librairie Android Room) est la source de vérité de l'application.
* **Table trees :** Gère les métadonnées des arbres et contient l'arborescence complète via un payload JSON.
* **Table images :** Gère le cache local (hachage des URL) pour le mode hors-ligne.
(Note : Les identités locales des utilisateurs sont gérées globalement par les EncryptedSharedPreferences).

### B. Système de Fichiers (File System)
Pour éviter les conflits en collectivité et faciliter la gestion des données :
* Chaque utilisateur possède un répertoire isolé généré dynamiquement.
* Les images (pictogrammes) et autres médias liés aux arbres y sont sauvegardés pour l'accès hors-ligne.

---

## 4. Internationalisation (i18n)
* **Conception "English First" :** Tous les textes de l'interface utilisent l'anglais comme langue de base dans `res/values/strings.xml`.
* **Traduction :** Le support du français (et autres langues) se fera via des fichiers de ressources dédiés (ex: `res/values-fr/strings.xml`).
* **Règle de code :** Aucun texte n'est "en dur" (hardcoded) dans les vues ; tous les labels passent par les ID de ressources système.