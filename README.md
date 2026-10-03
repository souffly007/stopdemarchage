# Stop Démarchage — 1.0

Application de filtrage des appels entrants, en complément de l'app
Téléphone Google : Stop Démarchage ne remplace pas ton app de téléphonie,
elle prend uniquement le rôle Android de filtrage des appels (voir
« Limites Android à connaître » plus bas).

Historique des versions bêta : voir [CHANGELOG.md](CHANGELOG.md).

## Ouvrir et compiler

1. Décompresser cette archive dans un nouveau dossier, sans la superposer à
   l'ancien projet.
2. Dans Android Studio, choisir **Open** et sélectionner `StopDemarchage-1.0`.
3. Utiliser le JDK 17 ou une version compatible avec Gradle 9.1 et Android
   Gradle Plugin 9.0.
4. Installer Android SDK Platform 36 depuis le SDK Manager et laisser Gradle
   synchroniser.
5. Lancer l'application sur un téléphone **Android 10 ou plus récent**.

En terminal Linux/macOS :

```sh
chmod +x gradlew
./gradlew :app:assembleDebug
```

Sous Windows : `gradlew.bat :app:assembleDebug`.
APK généré : `app/build/outputs/apk/debug/app-debug.apk`.

Pour la synchro communautaire (facultative), voir SUPABASE.md avant de
compiler : il faut ajouter `supabase_url` et `supabase_publishable_key` au
`local.properties` de ce projet. L'application compile et fonctionne sans
ces valeurs ; le filtre communautaire reste alors inactif.

## Première utilisation

Ouvrir Stop Démarchage, toucher **Activer le filtrage**, puis sélectionner
l'application dans le sélecteur Android d'identification / filtrage des
appels. Il ne faut pas la sélectionner comme composeur téléphonique : ce
rôle n'est pas demandé.

L'accueil affiche l'état réel du rôle Android et permet de mettre le
filtrage en pause. Le filtre de préfixes France est activé initialement ; il
peut être désactivé sans supprimer les règles manuelles. Une seule
application peut détenir le rôle de filtrage à la fois : le choisir remplace
donc un autre filtre éventuellement actif.

## Fonctions

- Trois écrans : Protection, Journal, Réglages.
- Filtrage local des appels entrants présentés par Android au service.
- 22 préfixes du plan Arcep vérifié le 16 septembre 2026 :
  `0162, 0163, 0270, 0271, 0377, 0378, 0424, 0425, 0568, 0569, 0948, 0949,
  09475, 09476, 09477, 09478, 09479, 02688, 02689, 05987, 05988, 05989`.
- Blocage manuel par numéro exact et préfixes personnels avec confirmation.
- Liste des numéros autorisés prioritaire sur toutes les autres règles.
- Blocage optionnel des appels hors France (indicatif étranger), désactivé
  par défaut ; métropole et outre-mer en sont exclus.
- Mode strict optionnel : rejette tout appel identifié qui n'est ni un
  contact ni une autorisation ; désactivé par défaut.
- Filtre communautaire optionnel (lecture seule de la liste partagée avec
  PhoneZen, seuil de 10 signalements), désactivé par défaut. Aucune requête
  réseau pendant un appel.
- Question optionnelle « spam ou légitime ? » pour les mobiles 06/07 inconnus,
  par notification à deux boutons (Spam = bloquer, Légitime = autoriser),
  désactivée par défaut. Locale, sans réseau ; s'applique aux appels suivants.
- Test local d'un numéro : explique la décision sans appel ni écriture au
  journal.
- Journal limité aux 1 000 derniers appels rejetés, avec date, heure et
  motif. **Débloquer / autoriser** depuis le journal retire le blocage exact
  et ajoute une exception prioritaire, y compris si le préfixe reste filtré.
  Effacement du journal après confirmation, sans effacer les règles.
- Quatre choix de thème : Système, Clair, Sombre, CyanogenMod.
- Marges tenant compte des barres système, du clavier et des découpes
  d'écran. Navigation Retour adaptée aux versions récentes d'Android.

Un préfixe n'identifie pas avec certitude un appel abusif. La liste de
préfixes est embarquée et ne fait pas l'objet d'une mise à jour en ligne.

## Allègement

Par rapport à l'ancien projet dont cette application est issue : pas de
clavier d'appel, contacts, favoris téléphoniques, SMS/MMS, journal général
du téléphone, composeur, filtres SMS, graphiques, animations, code C++/CMake.

Le code utilise Java et les composants natifs Android : aucune dépendance
applicative externe, pas de Hilt, Room, Gson, bibliothèque de graphiques ou
moteur d'animation. SQLite Android pour le journal, SharedPreferences pour
les réglages et règles. La compilation release active R8 et la suppression
des ressources inutilisées.

Aucune permission Contacts, SMS, Téléphone ou lecture du journal système
n'est demandée. La permission Notifications (Android 13+) n'est demandée
qu'à l'activation de la question « spam ou légitime ? ». Internet, état réseau et réception du démarrage servent
uniquement à la synchronisation communautaire facultative. Le service est
protégé par `BIND_SCREENING_SERVICE`, une permission réservée au système.
Pas de compte, de télémétrie ou de service permanent. Les données locales
sont exclues de la sauvegarde Android standard (`allowBackup=false`).

## Limites Android à connaître

- Le mode autonome repose sur `ROLE_CALL_SCREENING`, disponible à partir
  d'Android 10. Android 9 n'est pas pris en charge, afin de ne pas redevenir
  le composeur par défaut.
- Sans permission Contacts, Android ne transmet pas les appels de contacts
  enregistrés. Ils restent autorisés même s'ils figurent dans une règle
  personnelle. Aucun écran Contacts ni accès au carnet d'adresses n'est
  nécessaire.
- Les identités masquées, restreintes ou indisponibles ne sont pas
  transmises au service standard de filtrage. Aucun bouton de blocage des
  appels masqués n'est présenté : utiliser le réglage correspondant de
  l'application Téléphone.
- Ce filtre ne traite pas les appels WhatsApp, Signal ou autres applications.
- Le journal local ne contient ni les appels autorisés ni ceux bloqués par
  un autre filtre ou par l'opérateur. Les anciens événements restent
  visibles après déblocage.
- La réponse au système précède l'écriture SQLite. En cas d'erreur de
  décision, l'appel est autorisé. Les numéros ne sont pas écrits dans logcat.
- Les notifications de rejet sont laissées au système, sauf option de
  masquage dans Réglages. L'application n'affiche de notification propre
  que pour la question optionnelle « spam ou légitime ? ».
- Un service de filtrage ne peut pas afficher de fenêtre ni suspendre un
  appel : la question passe par une notification (parfois masquée par
  l'écran d'appel, alors visible après l'appel) et vaut pour les appels
  suivants.

Référence : [CallScreeningService — documentation Android](https://developer.android.com/reference/android/telecom/CallScreeningService).

## Version et mise à jour

- Nom : **Stop Démarchage**.
- Identifiant conservé : `fr.bonobo.stopdemarchage`.
- Version visible : **1.0**.
- `versionCode = 10`. **Si une bêta avec un code supérieur est déjà
  installée sur un appareil de test, augmenter ce numéro avant de publier.**

Pour installer par-dessus une ancienne version en conservant ses données,
signer avec la même clé que celle utilisée auparavant. Une signature
différente est refusée par Android. Ne pas désinstaller si les anciennes
données doivent être conservées.

Au premier lancement, les anciennes préférences `StopDemarchagePrefs` sont
lues pour reprendre `white_list_contacts` et `blocked_numbers`, normalisées
et dédoublonnées. Les numéros complets deviennent des règles exactes, les
préfixes reconnus sont rangés à part ; les autorisations ont priorité. Les
anciennes options SMS, composeur et thèmes ne sont pas réutilisées.

## Vérifications réalisées

- **509 assertions passées** sur le moteur Java réel : préfixes, formats
  `0`, `+33`, `0033`, séparateurs, numéros étrangers, saisies invalides,
  pause, priorité des autorisations, blocage exact, absence de
  correspondance sur un numéro voisin et préfixes personnels.
- Analyse syntaxique de tous les fichiers Java par le compilateur Java 17.
- XML analysés ; inventaire du manifeste : une activité, un service de
  filtrage, un JobService de synchronisation et les permissions réseau
  nécessaires, sans accès aux contacts ou au journal système.

Relancer les tests sans SDK Android, avec Java 17 :

```sh
./test-filter.sh
```

**Non vérifiés dans cet environnement** (sans réseau ni SDK Android) : la
compilation `:app:assembleDebug` complète, la connexion réelle à Supabase,
le comportement sur appareil, et les appels téléphoniques réels. Voir
« Essais à faire sur téléphone » ci-dessous avant toute publication.

## Essais à faire sur téléphone

1. Compiler, installer, activer le rôle ; vérifier le changement d'état de
   l'accueil.
2. Depuis un second téléphone **non enregistré dans les contacts**, appeler
   sans règle : l'appel doit sonner et le journal local rester vide.
3. Ajouter ce numéro aux numéros bloqués et rappeler : rejet et nouvelle
   entrée datée.
4. Depuis le journal, autoriser ce numéro, rappeler : l'appel doit sonner.
5. Ajouter un préfixe personnel couvrant le numéro autorisé : il doit
   toujours passer. Retirer ensuite l'autorisation : le préfixe doit
   bloquer l'appel suivant.
6. Mettre la protection en pause : cet appel doit passer ; reprendre puis
   retester.
7. Vérifier qu'un contact enregistré passe comme annoncé et que les appels
   sortants restent inchangés.
8. Vérifier le journal après fermeture et réouverture ; l'effacer et
   contrôler que les règles existent toujours.
9. Tester les quatre thèmes, la rotation, le Retour gestuel, le grand texte
   Android et le basculement clair/sombre du téléphone en thème Système.
10. Retirer le rôle Android, revenir dans l'application : elle doit
    afficher « Activation nécessaire ». Le refuser ne doit pas activer
    faussement l'état.
11. Sur un appareil double SIM, répéter les appels entrants sur chacune des
    deux lignes.
12. Simuler un appel +39 avec le blocage hors France désactivé puis activé,
    autoriser ce même numéro et recommencer ; vérifier aussi un +33 et un
    numéro ultramarin.
13. Dans Réglages → Communauté PhoneZen, activer le filtre avec de vraies
    clés Supabase et vérifier le résultat de la synchronisation.
14. Activer « Demander pour les mobiles inconnus » (accepter la permission
    Notifications), appeler depuis un mobile 06/07 non enregistré : la
    notification apparaît avec Spam / Légitime. Toucher Spam puis rappeler :
    rejet et entrée au journal. Refaire avec Légitime : l'appel sonne sans
    nouvelle question. Refuser la permission : l'option doit se désactiver.
    Vérifier qu'un fixe et un contact ne déclenchent aucune question.

Pour les essais réels : utiliser un second téléphone consentant, non
enregistré dans les contacts. Masquer les derniers chiffres des numéros
dans les captures partagées.

## Licence et distribution

Le code applicatif est placé sous **GPL-3.0-only**. Le texte complet est
dans LICENSE et les fichiers Java portent l'identifiant SPDX. D'après les
fichiers inspectés, aucune dépendance applicative incompatible n'a été
identifiée ; une dépendance ajoutée ultérieurement devra être revérifiée.
Le Gradle Wrapper conserve sa licence Apache 2.0 (voir THIRD_PARTY_NOTICES.md
et LICENSES/Apache-2.0.txt).

Pour distribuer l'APK, fournir aussi l'accès au code source correspondant
exactement au binaire, aux scripts de compilation et aux licences. Pas
d'obligation de publication publique si la diffusion reste privée, mais les
destinataires conservent les droits de modification et de redistribution
prévus par la GPL. Les clés privées de signature ne doivent pas être
ajoutées à cette archive.

Références : https://www.gnu.org/licenses/gpl-3.0.en.html et
https://www.apache.org/licenses/GPL-compatibility.html.
