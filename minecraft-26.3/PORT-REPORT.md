# Port Minecraft 26.3

## Dépendances

- Minecraft 26.3 stable
- Java 25
- Fabric Loader 0.19.5
- Fabric API 0.161.0+26.3
- Fabric Loom 1.17.20 et Gradle 9.6.1

## Changement vanilla observé

Dans 26.3, `Beardifier.getBuryContribution` utilise désormais des `float` et contient déjà le raccourci exact fondé sur `Mth.lengthSquared` : la contribution devient immédiatement `0.0F` lorsque la distance au carré atteint 36.

Rubidium n'applique donc aucun mixin sur 26.3. Le JAR sert de build de compatibilité explicite et conserve entièrement le comportement vanilla. Réintroduire l'ancien overwrite `double` provoque une erreur Mixin au chargement ; cette incompatibilité a été reproduite pendant le port puis éliminée.

## Vérifications

- compilation de l'ensemble du dépôt ;
- lancement d'un serveur Fabric 26.3 réel ;
- chargement de Rubidium 0.1.0-alpha.2 sans mixin ;
- comparaison bit à bit de 375 956 appels à la méthode vanilla, incluant grille, rayon six, valeurs extrêmes, infinis, NaN et valeurs aléatoires ;
- arrêt et sauvegarde propres du serveur.

Le port ne revendique aucun gain supplémentaire sur 26.3, puisque l'optimisation ciblée est devenue native.
