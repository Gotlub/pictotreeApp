# Pictotree : Guide UX/UI et Accessibilité (V3.0)

## 1. Philosophie de l'Interface (Navigation Spatiale 2D)
L'interface abandonne les grilles surchargées pour une navigation spatiale en croix. L'utilisateur se repère physiquement dans l'arbre : les concepts de même niveau (fratrie) sont sur un axe horizontal, la hiérarchie (parent/enfant) sur un axe vertical.

## 2. Le Moteur de Base : Le Layout Spatial (Croix Directionnelle)
L'écran est un `ConstraintLayout` fixe ("en dur") composé de plusieurs zones mises à jour dynamiquement selon la position dans l'arbre :

* **Item Central (Focus) :** L'image active. Grande, 100% opaque. C'est la seule décision visuelle principale.
* **Items Adjacents (Navigation) :** 4 emplacements autour du centre (Haut = Parent, Bas = Enfant, Gauche/Droite = Frères/Sœurs). Ils sont plus petits et légèrement transparents. Si la direction n'existe pas (ex: on est à la racine, donc pas de Haut), l'emplacement est masqué (`View.GONE`).
* **Micro-miniatures (Anticipation / Lookahead) :** Sur les bords de l'écran (Gauche, Droite, Haut), affichage de 3 très petites images (1/5ème de la taille d'un item adjacent) représentant les "voisins des voisins". Suivi de "..." s'il y en a davantage, si moins de 3 voisins/parent afficher le nb d'image correspondant. Permet de visualiser la suite de la liste sans naviguer. (Non applicable pour le "Bas").

## 3. Mécanique de Navigation (Swipe & Tap)
La navigation doit être fluide et accessible (handicap moteur = Tap prioritaire).

* **Sélection / Ajout (Swipe Bas depuis le centre ou Tap sur le centre) :** L'image centrale est envoyée dans le Bandeau de Phrase (le "panier") en bas de l'écran. *Attention : au milieu cette action ne fait pas descendre dans l'arbre.*
* **Navigation (Swipe dans une direction ou Tap sur un Item Adjacent) :** Fait glisser l'arbre. Le nœud adjacent ciblé devient le nouvel Item Central, et tout le layout se met à jour.

## 4. La Couche HUD (Calques superposés)
Éléments flottants (`ConstraintLayout` Z-index) superposés au-dessus de la croix de navigation :

* **Les 4 Flèches Directionnelles :** (Optionnelles) Placées autour de l'image centrale, par-dessus les Items Adjacents. Visibilité dynamique. Permettent de clarifier les directions possibles pour les profils ayant besoin de repères explicites.
* **Le Bandeau de Phrase (Fixe) :** Situé en permanence en bas de l'écran. Il accumule les pictogrammes sélectionnés (la liste chaînée / phrase).
* **Icône "Œil" (Vue Globale) :** Ouvre une modale (Treant.js via `WebView`) affichant l'arbre complet. Permet de cliquer sur un nœud pour faire sauter le layout spatial directement à cet endroit. Placer l'icône en haut à gauche.
* **Icône "Loupe" (Recherche) :** Ouvre un menu de recherche par description de l'image, avec option d'interroger la base ARASAAC en ligne. En haut à droite.

## 5. Instructions Techniques pour l'IA (Android Natif)

* **Architecture Core :** NE PAS UTILISER `ViewPager2`. Utiliser un seul `ConstraintLayout` statique avec des ID fixes pour les 5 vues principales (Centre, Haut, Bas, Gauche, Droite) et les Micro-miniatures.
* **Détection des gestes :** Appliquer un `GestureDetector` global sur le layout pour détecter les événements `onFling` (Swipes) dans les 4 directions et les transformer en actions de navigation.
* **Mise à jour d'état :** Créer une fonction Kotlin `updateUI(currentNode)` qui recalcule les nœuds adjacents, gère le "Lookahead" (voisins des voisins) et met à jour les `ImageView` (Source de l'image, visibilité `VISIBLE` ou `GONE`).
* **Animations Spatiales (Crucial) :** Ne pas instancier de nouveaux Layouts. Lors d'un changement de nœud, animer les `ImageView` existantes avec `ViewPropertyAnimator` (`translationX` / `translationY`). Puis utiliser un `AnimatorListener` (`onAnimationEnd`) pour :
    1. Figer l'écran un instant.
    2. Mettre à jour les données du modèle.
    3. Recharger les images dans les `ImageView` fixes.
    4. Réinitialiser les translations à 0 (sans animation).
* **Bandeau de Phrase :** Utiliser un simple `LinearLayout` fixé en bas contenant un `RecyclerView` horizontal pour la phrase en cours.
* **Pont JS/Kotlin :** Implémenter une `JavascriptInterface` dans la modale Treant.js pour forcer l'appel à `updateUI(targetNode)` lors d'un clic sur la vue globale.