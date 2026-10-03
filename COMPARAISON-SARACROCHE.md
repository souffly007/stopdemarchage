# Comparaison Saracroche / Stop Démarchage — 17 septembre 2026

## Périmètre réellement examiné

Le dépôt Codeberg n’a pas pu être consulté directement. L’archive source officielle
liée à Saracroche **5.4.0 (39)** sur F-Droid a été téléchargée et examinée.
Il ne s’agit pas d’un audit du dernier commit Codeberg ni d’un essai sur téléphone.

Source : https://f-droid.org/repo/com.cbouvat.android.saracroche_39_src.tar.gz
Présentation : https://f-droid.org/packages/com.cbouvat.android.saracroche/
SHA-256 de l’archive examinée : `c380b934aa7bc0627570150debf968546aec539b3aadf2962244d4c5199cbc93`.

Fichiers lus : service/CallScreeningService.kt, util/PatternManager.kt,
util/PhoneNumberMatcher.kt, service/PatternService.kt, service/ListPriorityService.kt,
service/ListSyncService.kt, config/Config.kt, network/ApiModels.kt.

## Comment Saracroche prend sa décision

1. Ne filtre que les appels entrants et respecte la pause du filtrage.
2. Normalise le numéro, y compris ses variantes selon les indicatifs configurés.
3. Charge en cache les listes actives et les parcourt dans leur ordre de priorité.
   En cas d’égalité de priorité, les listes d’autorisation passent avant le blocage.
4. Compare les numéros avec des motifs où `#` représente un chiffre : la longueur
   du motif doit être égale à celle du numéro. Des préfixes peuvent donc représenter
   des millions de numéros sans les stocker individuellement.
5. Une autorisation trouvée laisse passer l’appel. Sinon le mode contacts uniquement
   ou une correspondance de blocage entraîne le rejet.
6. Appelle CallResponse avec setDisallowCall(true) et setRejectCall(true).
7. Demande aussi de masquer la notification et l’entrée de journal système, enregistre
   son propre journal, puis peut envoyer une notification propre si l’option est activée.

Le mécanisme de rejet est donc le même que dans Stop Démarchage. La couverture dépend
surtout des listes utilisées. Les 22 préfixes de Stop Démarchage représentent eux
également des plages entières ; leur nombre ne se compare pas directement à un
nombre de numéros couverts annoncé par une autre application.

Saracroche prévoit des listes téléchargées depuis `app.saracroche.org/api/v2`, avec
versions, priorités et licence propre à chaque liste. L’intervalle configuré est de
24 heures. Les vérifications pendant les appels sont locales. L’intégration de ces
listes dans Stop Démarchage n’a pas été faite : ni leurs licences individuelles ni
les conditions d’accès à l’API n’ont été validées dans cette étude. La licence du
code de l’application ne prouve pas à elle seule celle de ses données distantes.

### Appels masqués : nuance

Le code Saracroche possède une branche qui rejette un appel si aucun numéro ne peut
être normalisé et si l’option anonyme ou contacts uniquement est activée. Cela ne
force pas Android à lui transmettre ces appels. La présence de ce code n’est donc
pas une preuve que le blocage des masqués fonctionne sur tous les appareils.
La documentation Android exclut les présentations restreintes/inconnues/indisponibles
et ne transmet normalement pas les contacts sans la permission READ_CONTACTS.
Source : https://developer.android.com/reference/android/telecom/CallScreeningService
Le bouton des appels masqués reste retiré de Stop Démarchage conformément à la demande.

## Améliorations intégrées à Stop Démarchage bêta 5

| Fonction | Comportement |
| --- | --- |
| Mode strict, optionnel | Rejette les numéros identifiés transmis par Android qui ne sont pas autorisés ; les contacts sont laissés passer par Android. Confirmation avant activation. |
| Autorisations prioritaires | Restent prioritaires, y compris devant le mode strict. |
| Pause générale | Désactive le mode strict et les autres règles. |
| Notification système des rejets | Option pour demander son masquage, sans supprimer le journal local. Effet à vérifier selon le téléphone. |
| Test de numéro | Explique localement la décision pour un numéro hors contacts, sans émettre d’appel ni écrire dans le journal. Signale la pause et l’absence du rôle de filtrage. |

Les deux nouvelles options sont désactivées par défaut : aucune augmentation du
blocage après mise à jour sans choix de l’utilisateur. Les appels masqués et
identités non normalisables restent hors de ce mode strict. Les numéros courts
continuent de passer. Le test de numéro ne consulte pas le carnet d’adresses.

Implémentation originale en Java, sans copie de code ni de listes Saracroche.
Aucune nouvelle dépendance, permission Internet ou Contacts n’a été ajoutée.
Les paramètres setSkipCallLog(false) restent inchangés pour conserver la trace système.

## Validation

390 assertions passent sur le moteur : dont pause, autorisations prioritaires,
numéro 0424119501 de la capture, mode strict et motifs de blocage existants.
Analyse syntaxique Java et XML validée. L’APK n’a pas été compilé dans cet environnement.

Essais utiles : numéro de test non enregistré → autorisé en mode normal ; activer
le mode strict → rejet ; autoriser le numéro → appel reçu ; pause → appel reçu ;
contacts → appel reçu ; comparer notification système avec/sans l’option de masquage.
Le test local affiche une prévision des règles, pas une preuve de comportement réseau.
