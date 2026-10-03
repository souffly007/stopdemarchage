# Historique — Stop Démarchage

## 1.1 (versionCode 11)

Nouvelle option **« Demander pour les mobiles inconnus (06 / 07) »** dans
Protection → Filtrage, désactivée par défaut.

Un mobile métropolitain 06/07 qui n'est ni autorisé ni déjà bloqué sonne
normalement, puis une notification propose deux boutons :
- **Spam** : le numéro est ajouté aux numéros bloqués ;
- **Légitime** : le numéro est ajouté aux numéros autorisés.

Le choix vaut pour les appels suivants (un service de filtrage ne peut pas
afficher de fenêtre ni suspendre un appel en cours). Les contacts ne sont pas
concernés : Android ne les transmet pas au service sans permission Contacts,
qui n'est toujours pas demandée. Aucune donnée n'est envoyée sur Internet.

Ajouts techniques : permission `POST_NOTIFICATIONS` (demandée à l'activation
de l'option, Android 13+), `Review.java`, `ReviewReceiver.java` (non exporté),
`FilterEngine.shouldAsk` avec tests, icône de notification monochrome.
Les mobiles présentés avec un indicatif ultramarin (+590 690…, +262 692…)
ne déclenchent pas la question.

## 1.0 (versionCode 10)

Sortie stable à partir de la 1.0-beta7. Aucun changement de comportement par
rapport à la bêta 7 : ce numéro clôt le cycle de bêta une fois la checklist
d'essais sur téléphone (voir README, « Essais à faire ») effectuée et validée.

**Avant de publier cette version**, vérifier que le `versionCode` choisi (10)
est bien supérieur à celui de toute bêta déjà installée sur un appareil de
test ; l'augmenter sinon. Signer avec la même clé que les versions bêta
précédentes pour permettre une mise à jour sans perte de données.

Deux ajustements suite aux essais réels sur Poco X7 Pro et Realme 12X :
- Clarification à l'écran Protection : quand le filtrage est en pause,
  un texte et un raccourci vers les réglages Android expliquent que
  Stop Démarchage reste l'application de filtrage sélectionnée par
  Android tant qu'elle n'est pas changée dans les réglages système (seul
  moyen de le faire : aucune API ne permet à une app de relâcher elle-même
  le rôle `ROLE_CALL_SCREENING`). Le bouton « Applications par défaut »
  d'À propos utilise désormais le même raccourci.
- Retrait de la mention « bêta 7 » restée dans le texte affiché en
  À propos.

## 1.0-beta7 — bloquer les appels hors France

Dans Protection → Filtrage, activer « Bloquer les appels hors France ».
Option désactivée par défaut, indépendante du filtre des préfixes de
démarchage. Rejette les numéros internationaux étrangers présentés par
Android (par exemple +39 / 0039) et conserve un motif dans le journal.
Toucher l'entrée permet d'autoriser ce numéro pour les prochains appels.

Métropole et outre-mer sont exclus de cette règle : +33, +262, +508, +590,
+594, +596, +681, +687, +689. Les autres règles (préfixes, communauté,
blocage manuel, mode strict) continuent de s'appliquer à ces numéros.
Indicatifs vérifiés dans les plans nationaux UIT.

Le pays est déterminé par l'indicatif du numéro affiché, pas la position de
l'appelant. Les numéros courts, masqués, invalides ou étrangers sans
indicatif explicite ne sont pas classés étrangers.

Correctif du script Gradle : import explicite de Base64. 464 assertions du
moteur passent.

## 1.0-beta6 — communauté partagée avec PhoneZen

Lecture seule de `reported_numbers` (Supabase), seuil de 10 signalements,
contrôle des expirations, pagination, cache local et tâche Android
quotidienne. Aucune requête réseau durant un appel. Activation facultative
dans Réglages → Communauté PhoneZen. Voir SUPABASE.md avant de compiler :
les paramètres `supabase_url` et `supabase_publishable_key` doivent être
ajoutés au `local.properties` de ce projet.

399 assertions passent sur le moteur et la politique communautaire.

## 1.0-beta5 — options inspirées de l'étude de Saracroche

Voir COMPARAISON-SARACROCHE.md pour le fonctionnement exact étudié et les
limites. Nouveau dans Réglages → Options de filtrage : mode strict optionnel
(contacts et numéros autorisés), masquage optionnel de la notification
système d'appel rejeté, test local d'un numéro sans appel ni écriture dans
le journal. Les deux nouvelles options sont désactivées par défaut.

Implémentation originale en Java, sans copie de code ni de listes
Saracroche. 390 assertions passent.

## 1.0-beta4 — simplification

Suppression du bouton des appels masqués dans l'accueil et les réglages,
de sa carte et de la méthode associée.

## 1.0-beta3 — appels privés et À propos

Le raccourci vers les réglages des appels masqués, introduit en bêta 3, a
été retiré en bêta 4 à la demande de l'auteur. La section À propos porte la
signature demandée et propose la licence GPL v3 complète consultable hors
ligne ainsi que les crédits des outils tiers. Code sous GPL-3.0-only.

382 assertions passent.

## Correctif de démarrage — 16 septembre 2026

Correction du plantage `PhoneWindow.getInsetsController` observé sur un
téléphone Xiaomi : installation du contenu avant la configuration de la
fenêtre, puis accès au contrôleur depuis une vue attachée, avec vérification
de sa disponibilité. L'apparence des barres est réappliquée à la prise de
focus.

## 1.0-beta2 — couverture outre-mer

Cinq préfixes ajoutés : 02688, 02689, 05987, 05988, 05989. Les dix préfixes
ultramarins (dont 09475 à 09479) sont associés au bon indicatif
international plutôt que d'être traités comme des numéros +33.

| Territoire | Indicatif | Préfixes filtrés |
| --- | --- | --- |
| Guadeloupe, Saint-Martin, Saint-Barthélemy | +590 | 05987, 09475 |
| Guyane | +594 | 05988, 09476 |
| Martinique | +596 | 05989, 09477 |
| La Réunion | +262 | 02688, 09479 |
| Mayotte | +262 | 02689, 09478 |

Ouvrir l'application après mise à jour normalise les anciennes règles
ultramarines enregistrées à tort en +33.

382 assertions passent.

## 1.0-beta1 — refonte initiale

Refonte autonome de l'archive `stopdemarchage-master` fournie. Trois écrans
(Protection, Journal, Réglages), filtrage local des appels entrants,
22 préfixes du plan Arcep, blocage manuel par numéro et préfixe, liste des
numéros autorisés prioritaire, journal limité à 1000 entrées, quatre thèmes.
Migration automatique des anciennes préférences `StopDemarchagePrefs`.

Retirés par rapport à l'ancien projet : clavier d'appel, contacts, favoris
téléphoniques, SMS/MMS, journal général du téléphone, composeur, filtres
SMS, graphiques, animations, code C++/CMake.
