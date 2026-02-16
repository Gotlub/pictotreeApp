# Pictotree : Guide UX/UI et Accessibilité

## 1. Philosophie de l'Interface (Réduction de la charge cognitive)
L'application s'adresse à des personnes avec des troubles du neurodéveloppement (TSA, TDAH) ou des troubles moteurs (IMC, dyspraxie). 
L'interface doit proscrire les grilles surchargées (type `GridView` avec 50 petites icônes). 
**Le concept central est le Carrousel (CoverFlow) : l'utilisateur se concentre sur UNE seule décision visuelle à la fois.**

## 2. Le Carrousel (Composant Principal)
L'écran de communication affiche les enfants du nœud actuel sous forme de carrousel horizontal.
* **Item Central :** L'image active est GRANDE, lumineuse, avec une ombre portée (élévation). Un indicateur visuel (ex: petite flèche animée vers le bas) indique qu'elle peut être sélectionnée.
* **Items Latéraux (Précédent/Suivant) :** Sont partiellement visibles, plus petits, et légèrement transparents pour indiquer le contexte sans distraire.
* **Le Bandeau de Phrase :** Situé en bas de l'écran, il accumule les pictogrammes validés pour former la phrase finale.

## 3. Mécanique des Gestes tactiles (Le "Swipe" AAC)
La navigation est spatiale et physique :

* **Swipe Gauche / Droite (Naviguer) :** Fait tourner le carrousel pour explorer les choix de même niveau.
* **Swipe Bas (Valider / "Mettre dans le panier") :** 1. L'image centrale est sélectionnée.
    2. Une animation de translation Y la fait "tomber" physiquement dans le bandeau de phrase en bas.
    3. L'arbre descend d'un niveau : le carrousel se met à jour avec les enfants du pictogramme validé.
* **Swipe Haut (Retour / Annuler) :** Remonte au nœud parent dans l'arborescence de pictogrammes.

## 4. Accessibilité Obligatoire (Le Tap remplace le Swipe)
Le geste de glissement rectiligne (Swipe/Fling) peut être impossible pour certains handicaps moteurs. 
**Le système de "Tap" n'est pas une option, c'est le moyen de navigation principal pour 50% des utilisateurs :**
* Taper sur le bord droit/gauche de l'écran = Swipe Droite/Gauche.
* Taper sur l'image centrale = Valider (déclenche l'action du Swipe Bas).

## 5. Instructions Techniques pour l'IA (Android Natif)
Pour implémenter cette vue en Kotlin / XML, respecter strictement cette stack :
1. **ViewPager2 :** Pour gérer la liste horizontale et le recyclage des vues.
2. **CompositePageTransformer :** Pour gérer l'effet CoverFlow (`scaleY 1.0` au centre, `scaleY 0.85` sur les côtés, gestion de l'`alpha` et de la `translationX`).
3. **GestureDetector :** Appliqué sur la vue centrale pour détecter les événements `onFling` verticaux (Haut/Bas).
4. **⚠️ Conflits Tactiles (Crucial) :** Gérer finement `onInterceptTouchEvent` pour éviter que le `ViewPager2` n'intercepte les swipes verticaux destinés au `GestureDetector`. Le swipe bas doit être fluide et prioritaire s'il dépasse un certain angle vertical.