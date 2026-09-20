# Rubidium — Fabric

Une optimisation automatique et ciblée du calcul de génération autour des structures. Le raccourci conserve le résultat de la formule vanilla. À partir de Minecraft 26.3, Mojang intègre déjà ce raccourci : le JAR Rubidium 26.3 sert donc uniquement de build de compatibilité et n'injecte aucun mixin. **Le rendu, les files graphiques et la concurrence de génération restent ceux de Minecraft dans une installation normale.** Aucun réglage, menu ou profil n'est nécessaire.

Le rubidium est l'élément chimique 37, un métal alcalin comme le sodium et le lithium. Ce projet est indépendant de Rubidium pour Forge ; son identifiant est `rubidium_flow`.

## Installation

Installer Fabric Loader, puis mettre **un seul JAR Rubidium** correspondant au jeu et la Fabric API correspondante dans `mods`. Les builds de référence utilisent Loader **0.18.4** pour 1.21.11 et **0.19.5** pour 1.21.1 / 26.x.

| Minecraft | Java | Fabric Loader | Fabric API vérifiée | JAR Rubidium |
|---|---|---|---|---|
| 1.21.1 | 21 | 0.19.5 | 0.116.17+1.21.1 | rubidium-flow-1.21.1-0.1.0-alpha.2.jar |
| 1.21.11 | 21 | 0.18.4 | 0.140.2+1.21.11 | rubidium-flow-1.21.11-0.1.0-alpha.1.jar |
| 26.1 | 25 | 0.19.5 | 0.145.1+26.1 | rubidium-flow-26.1-0.1.0-alpha.1.jar |
| 26.2 | 25 | 0.19.5 | 0.160.0+26.2 | rubidium-flow-26.2-0.1.0-alpha.1.jar |
| 26.3 | 25 | 0.19.5 | 0.161.0+26.3 | rubidium-flow-26.3-0.1.0-alpha.2.jar |

Téléchargements officiels : [Fabric](https://fabricmc.net/use/installer/), [Fabric API](https://github.com/FabricMC/fabric/releases).

Le mod s'utilise en solo ou sur serveur Fabric. Pour appliquer l'optimisation 1.21.1 à un serveur distant, il doit être installé sur ce serveur. En 26.3, le raccourci est déjà natif. Les JAR des bancs de test ne sont pas fournis dans le dossier des JAR à installer.

## Fonction retenue après les essais

Dans la contribution d'enfouissement des structures, le résultat vanilla devient exactement nul à partir d'un rayon de six blocs. Rubidium détecte ce cas avant la racine carrée et la division. À l'intérieur, il conserve les mêmes opérations dans le même ordre. Il ne remplace pas les bruits, les graines ou les algorithmes de biomes.

Le prototype Pulse retardait l'arrivée du terrain. Le prototype de budget graphique produisait des résultats variables, avec une régression dans la dernière comparaison. **Les deux ont été écartés du fonctionnement normal.** Leurs sources restent accessibles pour la recherche ; leur présence dans les sources n'indique pas qu'ils sont actifs.

## Résultats et état

Version **0.1.0-alpha.2 pour 1.21.1 et 26.3, expérimentale**. Le scénario dédié de 144 chunks donne environ **1,3 % de temps en moins**, sur deux passages par variante 1.21.1. Les essais ne démontrent pas de hausse générale des FPS ni la disparition des saccades. Lire `BENCHMARKS.md` pour les résultats individuels, les prototypes rejetés et les limites.

La livraison comprend les JAR, les sources, les descriptions et les mesures. `SHA256SUMS.txt` permet de contrôler les fichiers. Les ressources et binaires Minecraft ainsi que Fabric API ne sont pas redistribués.

## Compiler les sources

Installer les JDK **21 et 25**. Définir `JAVA_HOME` sur le JDK **25**, puis exécuter `gradlew.bat build` sous Windows ou `./gradlew build` sous Linux depuis `sources`. Gradle utilise Java 21 pour les anciens modules et Java 25 pour les ports 26.x. Le premier lancement télécharge les dépendances.

Les modules `audit-*`, `benchmark` et `client-benchmark*` sont réservés au développement : ils ouvrent des scénarios isolés et ferment le jeu automatiquement. Les scripts de `reproduction` nécessitent les caches et serveurs Fabric indiqués, avec des chemins à adapter. Ne pas les exécuter dans une sauvegarde personnelle.

Licence MIT, voir `LICENSE`.
