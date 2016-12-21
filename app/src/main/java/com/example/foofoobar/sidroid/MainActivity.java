package com.example.foofoobar.sidroid;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.security.KeyChain;
import android.security.KeyChainAliasCallback;
import android.security.KeyChainException;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.webkit.ClientCertRequest;
import android.webkit.SslErrorHandler;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import libs.QRcode;


public class MainActivity extends FragmentActivity {

    private ProgressBar progress;
    private int nb_progress = 0;
    // On récupère le Build.SERIAL, élément unique
    String serialId = Build.SERIAL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        progress = (ProgressBar) findViewById(R.id.scanbar);
        TextView scanText = (TextView) findViewById(R.id.connexion_text);
        scanText.setText("Connexion en cours...");
        startprogress();
    }

    // On "scan" au démarrage
    public void startprogress() {
        nb_progress = 0;
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                while (nb_progress++ < 100){
                    final int value = nb_progress;
                    doFakeWork();
                    progress.post(new Runnable() {
                        @Override
                        public void run() {
                            progress.setProgress(value);
                        }
                    });
                }
                if (nb_progress >= 100)
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run()
                        {
                            if (is_root())
                                setContentView(R.layout.isroot);
                              else
                                connexion();
                        }
                    });
            }
        };
        new Thread(runnable).start();
    }

    // On créer temps de chargement
    private void doFakeWork() {
        try {
            Thread.sleep(19);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // On vérifie si le smartphone est root
    public static boolean is_root()
    {
        return (new File("system/bin/su").exists() ||
                new File("system/xbin/su").exists() ||
                new File("system/app/Superuser.apk").exists());
    }
    // On récupère le certificat dans le keychain créé si le serveur nous le demande
    WebViewClient wvc = new WebViewClient()
    {
        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            handler.proceed();
        }
        @Override
        public void onReceivedClientCertRequest(WebView view, final ClientCertRequest request)
        {
           setContentView(R.layout.mainqrcode);
            TextView cert_text = (TextView)findViewById(R.id.Scan_QRCODE);
            TextView cert_ask = (TextView)findViewById(R.id.cert_ask);
            TextView cert_id = (TextView)findViewById(R.id.textView);
            String androidId = Settings.Secure.getString(getApplicationContext().getContentResolver(), Settings.Secure.ANDROID_ID);
            Button keychain_button = (Button) findViewById(R.id.Keychain_button);
            Button qr_button = (Button) findViewById(R.id.button);

            cert_ask.setText(" DEMANDE DE CERTIFICAT");
            cert_text.setText(" .Télécharger le certificat \n(QRcode généré par le serveur P4S poc-android.)");
            cert_text.setText(" .Installez le depuis le smartphone \n     Si vous n'en avez pas encore \n .téléchargez-en un (QRcode généré par le serveur)");
            cert_id.setText(" .Voici l'android ID et le serial ID à renseigner lors de la génération : " + "\n Android ID: " + androidId + "\n Serial ID: " + serialId);

            keychain_button.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view1) {
                    Log.v(getClass().getSimpleName(), "===> certificate required!");
                    KeyChain.choosePrivateKeyAlias(MainActivity.this, new KeyChainAliasCallback()
                    {
                        @Override
                        public void alias(String alias) {
                            Log.v(getClass().getSimpleName(), "===>Key alias is: " + alias);
                            try {
                                PrivateKey changPrivateKey = KeyChain.getPrivateKey(MainActivity.this, alias);
                                X509Certificate[] certificates = KeyChain.getCertificateChain(MainActivity.this, alias);
                                Log.v(getClass().getSimpleName(), "===>Getting Private Key Success!" );
                                request.proceed(changPrivateKey, certificates);
                            } catch (KeyChainException e) {
                                Log.e(getClass().getSimpleName(), "ERROR KEYCHAIN");
                            } catch (InterruptedException e) {
                                Log.e(getClass().getSimpleName(), "ERROR INTERRUPTED");
                            }
                        }
                    },new String[]{"RSA"}, null, null, -1, null);

                }
            });

            qr_button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view1) {
                    Intent QRcode_intent = new Intent(MainActivity.this, QRcode.class);
                    startActivity(QRcode_intent);
                }
            });
        }
    };

    // On check la connexion internet
    public boolean is_internet()
    {
        NetworkInfo network = ((ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
        return !(network == null || !network.isConnected());
    }

    // On accède au serveur
    public void display()
    {
        //setContentView(R.layout.fake);

        setContentView(R.layout.display);
        final WebView web = (WebView) findViewById(R.id.web);

        final WebSettings webSettings = web.getSettings();
        webSettings.setJavaScriptEnabled(true);
        getCacheDir();
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        web.setWebViewClient(wvc);
        web.loadUrl("https://mon_vpn_et_site_perso.com");
    }

    // On vérifie la connexion internet
    public void connexion()
    {
        if (is_internet())
            display();
        else
        {
            setContentView(R.layout.offline);
            Button reset_button = (Button)findViewById(R.id.actualiser);

            reset_button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    connexion();
                }
            });
        }
    }
}
