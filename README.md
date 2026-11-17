# 💊 PiluleSync

Application Android (Kotlin, sans dépendance externe) qui syncronise le rappel de prise d'une pilule **entre deux téléphones**.

## Fonctionnement
- **21h30 chaque jour** : notification de rappel sur les **deux** téléphones.
- Si la pilule n'est pas confirmée → **rappel toutes les 30 minutes sur les deux téléphones**, jusqu'à confirmation.
- Dès qu'une personne appuie sur « J'ai pris ma pilule » :
  - l'autre téléphone reçoit **immédiatement** la notification « 💊 [Prénom] a pris la pilule »,
  - tous les rappels s'arrêtent sur les deux téléphones.
- Reprise automatique après redémarrage du téléphone (BOOT_COMPLETED). Redemande
  la permission « Alarmes et rappels » (exact), les notifications, et garde une
  connexion de synchronisation active (service au premier plan canal bas).

## Synchronisation entre téléphones
La synchronisation utilise le service public **ntfy.sh** (aucun compte requis) :
les deux téléphones utilisent le **même code de salon** et s'écoutent sur le sujet
`pilule-<code>`. Chaque confirmation est publiée sur ce sujet et poussée en
temps réel à l'autre téléphone (flux JSON en continu) avec repli par
interrogation à chaque rappel, au cas où la connexion a été coupée (Doze, réseau).

> Le code de salon fait office de secret partagé : choisissez un code long et
> connu des deux personnes uniquement.

## Installation
1. Installer `app/build/outputs/apk/release/PiluleSync.apk` sur les **deux** téléphones
   (autoriser « Installer des applications inconnues » si demandé).
2. Sur chaque téléphone : renseigner le **prénom** et le **même code de salon**, puis « Enregistrer et activer ».
3. Autoriser les notifications et, si proposé, « Alarmes et rappels ».

## Compilation
```bash
./gradlew assembleRelease
```
(SDK Android 34, JDK 17.) L'APK de ce dépôt est signé avec une clé auto-signée :
désinstallez-la avant d'installer une version recompilée avec une autre clé.
