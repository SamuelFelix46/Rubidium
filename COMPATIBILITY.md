# Compatibilité et portée

| Minecraft | Java | Fabric API | Contrôles effectués |
|---|---|---|---|
| 1.21.1 | 21 | 0.116.17+1.21.1 | Compilation, serveur réel, calcul exact, empreintes NOISE dans trois dimensions, client graphique |
| 1.21.11 | 21 | 0.140.2+1.21.11 | Compilation, tests du port, serveur réel, calcul exact, client graphique |
| 26.1 | 25 | 0.145.1+26.1 | Compilation, serveur réel, calcul exact ; pas d'essai graphique |
| 26.2 | 25 | 0.160.0+26.2 | Compilation, serveur réel, calcul exact ; pas d'essai graphique |
| 26.3 | 25 | 0.161.0+26.3 | Compilation, serveur réel, vérification du raccourci devenu natif ; aucun mixin Rubidium |

Fabric Loader de référence : **0.18.4** pour 1.21.11 et **0.19.5** pour 1.21.1 / 26.x. Utiliser un seul JAR correspondant exactement au jeu. Ces contrôles limités ne certifient pas tous les matériels, mondes ou modpacks.

| Situation | Comportement normal |
|---|---|
| 1.21.1 à 26.2 | Raccourci mathématique autour des structures ; rendu et concurrence vanilla conservés |
| 26.3 | Le raccourci est déjà présent dans vanilla ; Rubidium ne modifie aucune classe |
| Partie solo | Calcul dans le serveur intégré |
| Serveur dédié | Calcul serveur ; aucune classe de rendu nécessaire |
| Client connecté à un autre serveur | Installer aussi le mod sur ce serveur pour y optimiser le calcul |
| C2ME | Raccourci de calcul désactivé automatiquement ; combinaison complète non certifiée |
| Sodium, Lithium, FerriteCore, ImmediatelyFast, Krypton, ModernFix | Combinaisons complètes non certifiées ; aucun budget graphique Rubidium actif |
| Forge / NeoForge | Non pris en charge par ces JAR Fabric |

Les anciennes intégrations graphiques sont désactivées par défaut, même en présence du banc de test. Leur réactivation exige un module de recherche séparé et une propriété de lancement réservée à celui-ci. Une propriété seule ne les active jamais dans une installation normale.

Les versions 26.x utilisent le mode de construction sans remappage et Java 25 : [documentation Fabric Loom](https://docs.fabricmc.net/develop/loom/). Aucun résultat de FPS 1.21.x n'est attribué aux ports 26.x.
