# SIDROID-21-12-2016
Assurer une connexion sécurisée entre un terminal Android et le SI de l'entreprise.


Plusieurs mesures de sécurité : 
  - vérification du root de l'appareil
  - version minimum autorisée (suite à analyse de sécurité du système Android)
  - Se connecter au SI en passant par un VPN -> authentification par certificat.

Si l'application ne présente pas de bon certificat à la terminaison VPN, il sera alors demandé d'en télécharger un.

Ce certificat se télécharge en scannant un QRcode affiché sur l'écran du PC burautique connecté en interne à l'entreprise.

Ce QRcode contient l'URL de téléchargement du certificat généré et chiffré par le serveur.
Le chiffrement résulte d'un algorithme AES en mode CBC. La clé de chiffrement est formée par l'Android ID et le Serial ID (deux ID affichées par l'application Android) et d'une phrase secrète que l'on hashera (sha).
Le vecteur d'initialisation (IV) généré lors le chiffrement sera placé au début d'un fichier qui contiendra également le certificat chiffré.
Le certificat est alors affiché sur un portail utilisateur accessible depuis l'intranet sous format QRcode.

L'application recupèrera alors ce fichier chiffré en scannant le QRcode, générera sa propre clé de chiffrement à partir de son Android ID et Seria ID et de la phrase secrète qui sera également hashée.

Si le terminal est le même que celui assigné au certificat, alors les deux ID correspondent et la clé générée est bien identique à celle utilisée lors du chiffrement.
Afin de déchiffrer le certicat, il faut alors récuprérer l'IV en parsant les premiers caractères du fichier récupéré.

Une fois la clé généré et l'IV récupéré, on peut déchiffrer le certificat.
Le certificat sera alors installé dans le Keystore du terminal (l'utilisateur est invité à rentrer le mot de passe du certificat, afficher sur l'écran du poste burautique. Ce mot de passe est généré aléatoirement).



Au lancement de l'application, ces mesures seront vérifiées et la connexion au VPN se fera -ou non- .
Si oui, le VPN redirigera alors l'application vers le SI de l'entreprise.
