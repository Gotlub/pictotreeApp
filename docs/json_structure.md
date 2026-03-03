# Structure du JSON (Le Contrat de Données)

## 1. Pourquoi ce document ?
L'application Android (Antigravity) utilise un champ `json_data` dans sa base SQLite pour stocker l'intégralité d'un arbre de pictogrammes. Ce document définit le schéma exact de ce JSON généré par l'API `pictotree.eu`. 
Il sert de base pour générer les Data Classes Kotlin et implémenter le Pattern Composite.

## 2. Le Modèle Conceptuel (Pattern Composite)
Un arbre est composé de **Nœuds** (Nodes). 
Un Nœud possède ses propres informations (texte, image) et peut contenir une liste d'enfants (`children`). Si la liste d'enfants est vide, c'est une "feuille" (la fin d'une phrase ou d'une action).

## 3. Le Schéma JSON (Exemple d'un arbre "Repas")

Voici à quoi doit ressembler la chaîne JSON stockée en base de données :

```json
{
  "tree_id": 42,
  "name": "Routine Repas",
  "root_node": {
    "node_id": "root_01",
    "label": "Je veux",
    "image_url": "https://pictotree.eu/static/public/arasaac/want.png",
    "children": [
      {
        "node_id": "node_10",
        "label": "Manger",
        "image_url": "https://pictotree.eu/static/public/arasaac/eat.png",
        "children": [
          {
            "node_id": "node_101",
            "label": "Une pomme",
            "image_url": "https://pictotree.eu/static/public/arasaac/apple.png",
            "children": [] 
          },
          {
            "node_id": "node_102",
            "label": "Un gâteau",
            "image_url": "https://pictotree.eu/static/public/arasaac/cake.png",
            "children": []
          }
        ]
      },
      {
        "node_id": "node_20",
        "label": "Boire",
        "image_url": "https://pictotree.eu/static/public/arasaac/drink.png",
        "children": [
          {
            "node_id": "node_201",
            "label": "De l'eau",
            "image_url": "https://pictotree.eu/static/public/arasaac/water.png",
            "children": []
          }
        ]
      }
    ]
  }
}