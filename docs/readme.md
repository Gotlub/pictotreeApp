# Pictotree (Android App)

## 🌳 Project Vision
Pictotree is an AAC (Augmentative and Alternative Communication) tool designed for people with oral communication disabilities, as well as for their caregivers.
The project is built around a web platform (`pictotree.eu`) for content creation, and this Android application which serves as an interactive "reader" and a means of expression.

The goal is to allow users to navigate through "pictogram trees" (daily routines, meals, activities) to project themselves in time and communicate their needs.

## 🛠 Global Architecture
The application is based on the **Composite Design Pattern**: data is structured as nodes and leaves (folders and action/object pictograms) interconnected to form linked lists and logical trees.

### Key Features
* **Profile Management:** An entry menu allowing the selection of profiles (e.g., "David", "George Outing", "George Home").
* **Cloud / Local Sync:** 
    * Account-less use (access to public trees).
    * Connection via a `pictotree.eu` account to retrieve the user's private trees and images.
* **Intra-tree Navigation:** Smooth movement within pictogram trees with vocal feedback (Text-to-Speech) of each image's personalized caption.
* **Offline Mode (Crucial):** Designed for medico-social environments. Images (notably those from the ARASAAC API or uploaded to the site) are physically downloaded to a local folder (`images_picto`) on the tablet/phone to guarantee uninterrupted access even without an internet connection.

## ⚙️ Android Technical Stack
* **Package Name:** `org.libera.pictotree`
* **Language:** Kotlin
* **Minimum SDK:** API 28 (Android 9.0)
* **UI:** XML (Empty Views Activity)
* **Architecture:** MVVM (Model - View - ViewModel)
* **Local Database:** Room in "Database-per-User" mode (Tables: trees with JSON payload, and images for hashed local cache). Global profiles are managed via EncryptedSharedPreferences.
* **Network:** Retrofit + Kotlin Coroutines (to communicate with the pictotree.eu API).
* **Image Management:** Glide or Coil (for display from local storage or network).

## 🧠 Guidelines for AI / Assistant
* Prioritize clean, modular, and commented code.
* Respect the MVVM architecture: the View handles no business logic.
* Consider accessibility constraints (contrast, large click targets, UI simplicity).
