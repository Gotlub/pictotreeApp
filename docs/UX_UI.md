# Pictotree : Guide UX/UI et Accessibilité (V4.0)

## 1. Philosophie de l'Interface (Navigation Spatiale 2D)
L'interface abandonne les grilles surchargées pour une navigation spatiale en croix. L'utilisateur se repère physiquement dans l'arbre : les concepts de même niveau (fratrie) sont sur un axe horizontal, la hiérarchie (parent/enfant) sur un axe vertical.

## 2. Le Moteur de Base : Le Layout Spatial (Croix Directionnelle)
L'écran est un `ConstraintLayout` fixe ("en dur") composé de plusieurs zones mises à jour dynamiquement selon la position dans l'arbre :

* **Item Central (Focus) :** L'image active. Grande, 100% opaque. C'est la seule décision visuelle principale.
* **Items Adjacents (Navigation) :** 4 emplacements autour du centre (Haut = Parent, Bas = 1er Enfant, Gauche/Droite = Frères/Sœurs). Ils sont plus petits et légèrement transparents. Masqués (`View.GONE`) si la direction n'existe pas.
* **Micro-miniatures Latérales (Anticipation / Lookahead) :** Sur les bords Gauche, Droite et Haut, affichage de très petites images (1/5ème) représentant les "voisins des voisins" (max 3 images, suivies de "..." s'il y en a plus).
* **Micro-miniatures Basses (Teaser Enfant) :** Sous l'Item Bas (le 1er enfant), un petit indicateur visuel affiche les enfants 2 et 3 en micro-miniatures, suivis de "..." s'il y en a davantage.

## 3. Mécanique de Navigation (Swipe & Tap)
La navigation doit être fluide et accessible (handicap moteur = Tap prioritaire).

* **Sélection / Ajout (Swipe Bas depuis le centre ou Tap sur le centre) :** L'image centrale est envoyée dans le Bandeau de Phrase (le "panier"). *Attention : cette action au centre ne fait pas descendre dans l'arbre.*
* **Navigation (Swipe dans une direction ou Tap sur un Item Adjacent) :** Fait glisser l'arbre. Le nœud ciblé devient le nouvel Item Central.

## 4. La Couche HUD (Calques superposés)
Éléments flottants (`ConstraintLayout` Z-index) superposés au-dessus de la croix de navigation :

* **Les 4 Flèches Directionnelles :** (Optionnelles / Désactivables) Placées autour de l'image centrale. Permettent de clarifier les directions possibles pour les profils ayant besoin de repères explicites.
* **Le Bandeau de Phrase (Fixe) :** Situé en permanence en bas de l'écran. Accumule les pictogrammes validés (phrase).
    * **Manipulation :** Maintenir et glisser horizontalement pour changer l'ordre. Glisser un élément vers le HAUT en dehors du bandeau pour le supprimer.
    * **Lecture Vocale :** Un bouton "Play" permet de lire la phrase avec le lecteur vocal (Google TTS). La langue et le moteur TTS doivent être configurables.
    * **Plein Écran :** Une icône permet d'afficher le bandeau en grand.
* **Icône "Œil" (Vue Globale) :** (Haut-Gauche). Ouvre une modale (Treant.js via `WebView`) affichant l'arbre complet. L'element qui etait selectionné dans la vue précédente doit etre en surbrillance dans cette vue.
    * Changement d'arbre : Deux flèches fixes (positionnées à mi-hauteur de l'arbre, sur ses bords gauche et droite) permettent de passer à l'arbre précédent ou suivant du profil (selon l'ordre défini dans le menu edit_profil).
    *  Mécanique de sélection : Toucher un nœud ou une feuille dans cet arbre le met visuellement en surbrillance (focus), mais ne ferme pas la modale (un seul element peu etre en surbrillance, en selectionné un déselectionne le précédent).
    * Boutons d'action fixes : l'utilisateur a accès à deux boutons fixes sur l'écran :
        ** Bouton "Ajouter au panier" (à gauche) : Envoie le pictogramme sélectionné directement dans le bandeau de phrase, permettant de construire une phrase entière depuis cette vue globale.
        ** Bouton "Retourner à la navigation" (à droite ou en bas) : Ferme la modale de la vue globale et met à jour l'UX principal (la croix spatiale). L'élément qui était en surbrillance devient le nouvel Item Central de la vue classique.
* **Icône "Loupe" (Recherche) :** (Haut-Droite). Ouvre un menu de recherche par description, avec option d'interroger la base ARASAAC en ligne (si connection disponible).

## 5. Configuration Profils ("Safe Mode" vs "Admin")
Un menu de configuration par profil utilisateur permet de désactiver ou activer des éléments du HUD (Flèches, Vue Globale, Recherche) pour éviter la surcharge cognitive (TSA, TDAH), ainsi que le réglage de la langue (TTS) Et ce pour chaque profil (a créer dans le menu edit_profil).

## 6. Instructions Techniques pour l'IA (Android Natif)

* **Architecture Core :** NE PAS UTILISER `ViewPager2`. Utiliser un seul `ConstraintLayout` statique avec des ID fixes.
* **Détection Gestuelle :** Appliquer un `GestureDetector` global sur le layout pour les `onFling` (navigation).
* **Bandeau de Phrase & Drag/Drop :** Utiliser un `RecyclerView` horizontal. Implémenter un `ItemTouchHelper` avec `ItemTouchHelper.UP` pour la suppression (swipe to dismiss) et `LEFT/RIGHT` pour la réorganisation (drag and drop).
* **Lecture Vocale :** Utiliser l'API native Android `TextToSpeech` pour lire la liste du bandeau de phrase concaténée.
* **Animations Spatiales :** Lors d'un changement de nœud, animer les `ImageView` existantes (`translationX`/`translationY`). Au `onAnimationEnd`, mettre à jour les données, recharger les Bitmaps, et réinitialiser les translations à 0 (sans animation).
* **Pont JS/Kotlin :** Implémenter une `JavascriptInterface` dans la modale Treant.js pour forcer l'appel à la fonction Kotlin de mise à jour lors d'un clic sur la vue globale.