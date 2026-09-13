# Mesures et décisions — Rubidium 0.1.0-alpha.1

## Résultat retenu

**La livraison active uniquement le raccourci de calcul exact.** Elle ne contient aucun régulateur de génération ou budget graphique actif dans une installation normale. Les gains graphiques des prototypes ci-dessous ne décrivent pas le produit livré.

## Référence et méthode

« Référence » signifie Minecraft avec Fabric Loader, Fabric API et le même banc de mesure, optimisations Rubidium désactivées. Ce n'est pas Minecraft sans Fabric. Machine : Ryzen 5 PRO 7535U, Radeon intégrée, 12 processeurs logiques, mémoire Java maximale 4 Gio, Java 21. Client : 1280 × 720, distance 8 chunks, simulation 5, VSync désactivée, limite 260 correspondant au mode sans limite. 1.21.1 indique Fancy ; 1.21.11 indique CUSTOM, avec ses réglages par défaut et les paramètres ci-dessus. Ne pas comparer directement les FPS entre versions.

Chaque parcours graphique mesure 60 secondes, après 20 secondes d'échauffement, avec quatre arrêts en terrain neuf espacés de 15 secondes. La révision 2 fixe la caméra d'échauffement à (60.5,110,-40.5). Les mondes sont des copies fraîches de la graine 123456789. Aucun benchmark ou build n'est exécuté simultanément. Les passages conservés indiquent zéro image sans focus et zéro image avec un écran de menu.

## Génération dédiée 1.21.1

144 chunks au stade FULL, concurrence de demande 8, 16 chunks d'échauffement ailleurs, tickets conservés et cinq secondes de calme. Ordre A/B/B/A, deux passages par variante. Le code de calcul retenu était actif en B, le régulateur Pulse désactivé. Un serveur dédié n'utilise aucun budget graphique.

| Mesure | Référence | Raccourci Rubidium retenu |
|---|---:|---:|
| Temps moyen pour 144 chunks | 11,541 s | 11,391 s |
| Passage 1 | 11,580 s | 11,371 s |
| Passage 2 | 11,501 s | 11,411 s |
| Ticks mesurés > 50 ms | 0 | 0 |

Environ **1,3 % de temps en moins**, sur ce scénario seulement. Deux répétitions ne démontrent ni une significativité statistique ni un gain universel. Sources : `final-perf-a1`, `final-perf-b1`, `final-perf-b2`, `final-perf-a2`. Le champ historique `gcPauseCollectionMs` mesure le temps de collecte remonté par Java, pas les pauses exactes. Les durées de tick ne sont pas des FPS.

## Prototype graphique écarté — 1.21.1

Premiers essais, révision 1 : référence 285,87 FPS, 16 images >50 ms, P99 14,064 ms ; budget graphique sans Pulse 305,74 FPS, 12 images >50 ms, P99 14,086 ms. Le P99 ne s'améliorait pas. Avec Pulse, il ne restait que 17 sections visibles contre 74 pour la référence : ce prototype a été rejeté.

Nouveaux essais, révision 2, en environnement de développement Fabric :

| Passage | FPS moyens | P99 (ms) | Images > 50 ms | Maximum (ms) | Sections au dernier relevé |
|---|---:|---:|---:|---:|---:|
| client-final-a1 | 298.73 | 15.895 | 22 | 326.88 | 70 |
| client-final-b1 | 379.30 | 11.272 | 10 | 100.31 | 76 |
| client-final-b2 | 389.76 | 11.299 | 16 | 228.24 | 84 |
| client-final-a2 | 739.19 | 7.035 | 11 | 130.92 | 163 |
| client-final-b3 | 532.38 | 8.894 | 22 | 256.42 | 77 |

A désigne la référence. B désigne le calcul exact et le budget graphique de 2 ms/64 tâches, sans Pulse. A1/B1/B2 ont été réalisés le matin ; une interruption d'environ cinq heures précède A2/B3. **Ne pas agréger ces séries en un pourcentage de gain.** Le dernier B3 est inférieur à A2 et affiche moins de terrain à la fin. Les changements de charge, de température ou d'alimentation n'ont pas été mesurés : la cause précise de la variation entre sessions reste inconnue. Cette incertitude et le résultat défavorable motivent l'abandon du budget graphique dans la livraison.

Le nombre de sections dans le dernier relevé n'est pas une mesure exacte du temps d'arrivée de tous les chunks. Les captures ont aussi été inspectées. Les tests n'autorisent aucune promesse de hausse générale des FPS.

## JAR livrés : contrôles graphiques avec Fabric normal

Ces essais utilisent les JAR remappés, un client Minecraft de production et un banc séparé. Ils vérifient le chargement, le rendu, le parcours et la sauvegarde à la fermeture. Le budget graphique reste désactivé. La référence 1.21.11 utilise le candidat antérieur avec toutes ses optimisations désactivées ; le passage actif utilise le JAR final.

| Version | Chargement et parcours | Budget graphique actif | Fils Pulse | Sections au dernier relevé |
|---|---|---|---:|---:|
| 1.21.11 | Réussis | Non | 0 | 266 |
| 1.21.1 | Réussis | Non | 0 | 84 |

Le passage 1.21.1 « smoke » contrôle l'installation normale sous un identifiant de banc distinct : zéro fil Pulse et aucune redirection de la file graphique. Il ne forme pas une paire de performance avec les anciens essais de développement. Les essais 1.21.11 n'ont pas explicitement neutralisé la limitation automatique des FPS en cas d'inactivité. **Ces contrôles de fonctionnement sont classés performanceValid=false et ne servent pas à annoncer un gain de FPS.** Les temps d'image bruts sont conservés pour la traçabilité.

## Contrôles de calcul et limites

48 tests unitaires passent, y compris les utilitaires des prototypes conservés dans les sources. Chaque version de Minecraft a exécuté **375 956 comparaisons** de la méthode réelle avec la formule vanilla dans un serveur Fabric, puis sauvegardé les dimensions avant fermeture. Grille, valeurs limites et doubles pseudo-aléatoires : résultat identique bit à bit, avec normalisation des NaN dans l'audit.

Sur 1.21.1, les empreintes des blocs et biomes au stade NOISE sont identiques sur **64/64 chunks Overworld**, **16/16 Nether**, **16/16 End**. Deux références Overworld concordent également. Les audits d'intégrité sont séparés des mesures de performance.

Les comparaisons de mondes finaux FULL ont divergé entre deux références elles-mêmes. Elles restent non concluantes : les empreintes au stade NOISE ne prouvent pas l'équivalence complète des sauvegardes finales rechargées. Les ports 26.x n'ont pas de test graphique ni de mesure de FPS. Les combinaisons avec les autres mods ne sont pas globalement certifiées.

Les JSON, les empreintes des JAR et les sources de mesure sont fournis. Le P99 utilise le rang supérieur ; les FPS moyens sont l'inverse du temps d'image moyen. Les sources permettent de réactiver les prototypes uniquement dans les bancs séparés, pour reproduire la recherche. Le mod reste une première version expérimentale à portée limitée.
