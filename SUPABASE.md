# Partager la base PhoneZen — bêta 6

## Installation

1. Ouvrir le dossier StopDemarchage-1.0 dans Android Studio.
2. Ouvrir le fichier local.properties de PhoneZen sur votre ordinateur.
3. Copier uniquement les lignes `supabase_url=...` et `supabase_publishable_key=...`
   dans le local.properties de Stop Démarchage. Conserver le sdk.dir propre à ce projet.
4. Utiliser une clé publishable (`sb_publishable_...`) ou un JWT ancien de rôle anon.
   Jamais sb_secret ou service_role. La compilation refuse les formats secrets reconnus.
5. Recompiler et installer avec la même signature que la version précédente.
6. Dans Réglages → Communauté PhoneZen, activer le filtre puis vérifier le résultat
   de la synchronisation. Le bouton « Synchroniser maintenant » permet de réessayer.

La configuration n’est pas dans l’archive fournie. Aucune connexion à votre base
n’a donc pu être testée. Le local.properties réel ne doit pas être joint aux ZIP
publics : il peut aussi contenir des paramètres privés étrangers à cette fonction.
La clé publique nécessaire au client est toutefois lisible dans l’APK par conception ;
la protection des données repose sur les droits serveur et RLS, pas sur son secret.

## Ce qui est partagé

Lecture de `public.reported_numbers`, colonnes `number`, `reports`, `expires_at`.
Requêtes GET uniquement. La RPC report_number de PhoneZen n’est pas appelée.
Stop Démarchage ne modifie ni les signalements ni leurs compteurs et n’envoie pas
les numéros appelants, les contacts ou le journal. Supabase reçoit les demandes de
liste et les informations réseau habituelles, notamment l’IP du téléphone.

La copie PhoneZen 1.4e inspectée contient une incohérence : SpamDetector annonce
COMMUNITY_BLOCK_THRESHOLD = 10 mais MainViewModel.checkReportedNumbers utilise
reports >= 5 pour remplir sa liste de blocage. Cette bêta retient 10, le seuil
annoncé, et ne modifie pas PhoneZen. Les deux applications ne peuvent donc pas être
présentées comme ayant exactement le même comportement tant que PhoneZen n’est pas harmonisé.

Les numéros français enregistrés par cet ancien PhoneZen en 0xxxxxxxxx sont
normalisés vers +33 dans le cache local. Les plages ultramarines reconnues utilisent
leur propre indicatif. Les numéros hors France doivent porter leur indicatif +pays.
Les entrées invalides, de moins de 10 signalements ou expirées sont ignorées.

## Fonctionnement hors ligne

Les lectures par numéro pendant les appels se font dans un cache SharedPreferences,
pas sur Internet. Un téléchargement complet remplace le cache précédent ; un échec
réseau ou une réponse invalide ne publie pas une liste partielle. Les expirations
sont contrôlées avec l’horloge du téléphone à chaque décision, même hors ligne.
Une suppression/modification côté serveur ne sera connue qu’à la prochaine mise à jour.
Le cache est associé à l’URL du projet pour éviter d’utiliser celui d’une autre base.

Une tâche JobScheduler persistante demande une synchronisation toutes les 24 heures.
Android peut la différer selon l’économie d’énergie, la connexion et les restrictions
constructeur. Après un arrêt forcé, rouvrir l’application peut être nécessaire.
Pas de service permanent ni de nouvelle dépendance externe. Internet est une permission
normale : aucun dialogue d’autorisation réseau n’est attendu.

Pagination par numéro croissant (200 lignes demandées par page, continuation jusqu’à
une page vide). Limite explicite de 10 000 lignes et de durée/taille des réponses :
en cas de dépassement, l’erreur est affichée et la liste précédente est conservée.
Une mutation serveur pendant plusieurs pages peut rendre le résultat provisoirement
incomplet : il n’y a pas de transaction serveur couvrant toutes les pages.

Priorité : pause → autorisations → blocages manuels → communauté → préfixes → mode strict.
Les contacts enregistrés restent gérés directement par Android comme auparavant.
Le bouton « Tester un numéro » utilise lui aussi le cache communautaire et son expiration.

## Droits serveur à vérifier

Le ZIP PhoneZen ne contient ni SQL de création ni règles RLS. Ne pas désactiver RLS,
ni ajouter de droits INSERT/UPDATE/DELETE pour faire fonctionner la lecture.
Le rôle anon doit pouvoir lire uniquement les données communautaires destinées
aux applications. Préférer une vue/RPC dédiée si la table contient d’autres données
privées ; le présent client attend cependant la table et les trois colonnes ci-dessus.

Le fichier SUPABASE-DIAGNOSTIC.sql contient uniquement des SELECT à exécuter dans
le SQL Editor pour examiner la configuration, sans changer la base.
Un résultat HTTP 200 avec une liste vide peut signifier qu’aucun numéro n’atteint le
seuil, mais aussi que RLS masque les lignes : l’application ne peut pas le distinguer.
Comparer avec le comptage SQL. Une erreur HTTP 401/403 demande de vérifier la clé,
les droits SELECT et les politiques ; utiliser une clé administrateur n’est pas une solution.

Tests à effectuer : synchronisation réussie avec au moins une entrée éligible,
échec réseau conservant le cache, entrée expirée ignorée, autorisation prioritaire,
communauté désactivée et pause générale. Ne pas créer de faux signalements dans
la base de production pour tester ; utiliser le test local ou un environnement de test.

Documentation officielle : https://supabase.com/docs/guides/getting-started/api-keys
