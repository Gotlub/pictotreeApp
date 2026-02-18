# Pictotree (Android App)

## 🌳 Vision du projet
Pictotree est un outil de CAA (Communication Améliorée et Alternative) conçu pour les personnes ayant des déficiences de communication orale, ainsi que pour leurs aidants (familles, institutions médico-sociales). 
Le projet s'articule autour d'une plateforme web (`pictotree.eu`) pour la création de contenu, et de cette application Android qui sert de "lecteur" interactif et de moyen d'expression.

L'objectif est de permettre aux utilisateurs de naviguer dans des "arbres de pictogrammes" (routines de la journée, repas, activités) pour se projeter dans le temps et communiquer leurs besoins.

## 🛠 Architecture Globale
L'application repose sur le **Design Pattern Composite** : les données sont structurées sous forme de nœuds et de feuilles (dossiers et pictogrammes d'action/objet) interconnectés pour former des listes chaînées et des arborescences logiques.

### Fonctionnalités Clés
* **Gestion des profils :** Un menu d'entrée permettant de sélectionner des profils (ex: "David", "George Sortie", "George Foyer").
* **Synchronisation Cloud / Local :** * Utilisation sans compte (accès aux arbres publics).
    * Connexion via un compte `pictotree.eu` pour récupérer les arbres et images privés de l'utilisateur.
* **Navigation intra-arbres :** Déplacement fluide dans les arbres de pictogrammes avec retour vocal (Text-to-Speech) de la légende personnalisée de chaque image.
* **Mode Hors-Ligne (Crucial) :** Pensé pour un environnement médico-social. Les images (notamment celles de l'API ARASAAC ou uploadées sur le site) sont téléchargées physiquement dans un dossier local (`images_picto`) sur la tablette/téléphone pour garantir un accès ininterrompu même sans connexion internet.

## ⚙️ Stack Technique Android
* **Package Name :** `org.libera.pictotree`
* **Langage :** Kotlin
* **Minimum SDK :** API 28 (Android 9.0)
* **UI :** XML (Empty Views Activity)
* **Architecture :** MVVM (Model - View - ViewModel)
* **Base de données locale :** Room en mode "Database-per-User" (Tables : trees avec payload JSON, et images pour le cache local haché). Les profils globaux sont gérés via EncryptedSharedPreferences.
* **Réseau :** Retrofit + Kotlin Coroutines (pour communiquer avec l'API pictotree.eu).
* **Gestion des images :** Glide ou Coil (pour l'affichage depuis le stockage local ou le réseau).

## 🧠 Consignes pour l'IA / Assistant
* Privilégier un code propre, modulaire et commenté.
* Respecter l'architecture MVVM : la View ne gère aucune logique métier.
* Prendre en compte les contraintes d'accessibilité (contraste, cibles de clic larges, simplicité de l'UI).