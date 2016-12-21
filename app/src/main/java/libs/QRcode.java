
package libs;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.security.KeyChain;
import android.util.Log;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class QRcode extends Activity
{

    static final String ACTION_SCAN = "com.google.zxing.client.android.SCAN";
    private ProgressDialog pDialog;
    public static final int progress_bar_type = 0;
    Cipher cipher;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        scanQR();
    }

    //On essaie d'ouvrir un lecteur de QRcode, si oui : on lit; si non : on en télécharge un
    public void scanQR()
    {
        try
        {
            Intent intent = new Intent(ACTION_SCAN);
            intent.putExtra("SCAN MODE", "QR_CODE_MODE");
            startActivityForResult(intent, 0);

        }
        catch (ActivityNotFoundException anfe)
        {
            showDialog(QRcode.this, "Aucun scanner trouvé.", "En télécharger un ?", "Oui", "Non").show();
        }
    }

    //On demande à l'utilisateur d'installer un lecteur de QRcode
    private static AlertDialog showDialog(final Activity act, CharSequence title, CharSequence message, CharSequence buttonYes, CharSequence buttonNo)
    {
        AlertDialog.Builder downloadDialog = new AlertDialog.Builder(act);
        downloadDialog.setTitle(title);
        downloadDialog.setMessage(message);
        downloadDialog.setPositiveButton(buttonYes, new DialogInterface.OnClickListener()
        {
            public void onClick(DialogInterface dialogInterface, int i)
            {
                Uri uri = Uri.parse("market://search?q=pname:" + "com.google.zxing.client.android");
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                try {
                    act.startActivity(intent);
                }
                catch (ActivityNotFoundException ignored)
                {
                }
            }
        });
        downloadDialog.setNegativeButton(buttonNo, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int i)
            {
            }
        });
        return downloadDialog.show();
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case progress_bar_type:
                pDialog = new ProgressDialog(this);
                pDialog.setMessage("Telechargement en cours...");
                pDialog.setIndeterminate(false);
                pDialog.setMax(100);
                pDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                pDialog.setCancelable(true);
                pDialog.show();
                return pDialog;
            default:
                return null;
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent intent)
    {
        if (requestCode == 0) {
            if (resultCode == RESULT_OK) {
                String contents = intent.getStringExtra("SCAN_RESULT");

                DownloadTask mDownload = new DownloadTask();
                mDownload.execute(contents);
            }
        }
    }

    //On télécharge le certificat, on le déchiffre, on l'installe (ce qui le déplace dans le keystore) puis, si il reste des traces, on le supprime
    class DownloadTask extends AsyncTask<String, String, String> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            showDialog(progress_bar_type);
        }
        @Override
        protected String doInBackground(String ...f_url) {
            String pass = "La même clé que celle utilisée lors du chiffrement du certificat par le serveur";
            String serialId = Build.SERIAL;
            String androidId = Settings.Secure.getString(getApplicationContext().getContentResolver(), Settings.Secure.ANDROID_ID);
            String keyvalue = androidId + serialId + pass;
            try {
                Context context = getApplicationContext();
                URL url = new URL(f_url[0]);
                URLConnection connection = url.openConnection();
                connection.connect();
                int fileLength = connection.getContentLength();
                String fileName = url.getFile().substring(url.getFile().lastIndexOf('/') + 1);
                File file = new File(context.getFilesDir().getAbsolutePath() + "/" + fileName);
                if (file.exists()) {
                    file.delete();
                }

                //ON GENERE LA CLE DE CHIFFREMENT
                MessageDigest hash = MessageDigest.getInstance("SHA1");
                byte[] hash_key = hash.digest(keyvalue.getBytes(StandardCharsets.UTF_8));

                StringBuilder skey = new StringBuilder();
                int i = -1;
                while (++i < hash_key.length) {
                    String hex = Integer.toHexString(0xff & hash_key[i]);
                    if (hex.length() == 1)
                        skey.append('0');
                    skey.append(hex);
                }

                String keytostr = skey.toString().substring(0, 32);
                byte[] Key = keytostr.getBytes(StandardCharsets.UTF_8);
                SecretKeySpec skeySpec = new SecretKeySpec(Key, "AES");

                InputStream input = new BufferedInputStream(url.openStream());

                //ON RECUPERE L'IV ET LE CONTENU CHIFFRE
                byte[] cipherTextByte = IOUtils.toByteArray(input);
                IvParameterSpec iv = new IvParameterSpec(Arrays.copyOfRange(cipherTextByte, 0, 16));
                cipherTextByte = Arrays.copyOfRange(cipherTextByte, 16, cipherTextByte.length);

                //ON LE DECHIFFRE EN UTILISANT L'ALGORITHME D'AES

                cipher = Cipher.getInstance("AES/CBC/ZeroBytePadding");
                try {
                    cipher.init(Cipher.DECRYPT_MODE, skeySpec, iv);
                  //  decrypted_data = new ByteArrayInputStream(AESCrypt.decrypt(skeySpec, iv.getIV(), cipherTextByte));
                } catch (Exception e) {
                    Log.e("Error: ", e.getMessage());
                }
                InputStream decrypted_data = new ByteArrayInputStream(cipher.doFinal(cipherTextByte));


                //ON ECRIT LE CERTIFICAT DECHIFFRE DANS LE FICHIER .P12
                byte[] buffer = new byte[1024];
                long total = 0;
                FileOutputStream output = context.openFileOutput(fileName, Context.MODE_PRIVATE);
                int count;
                while ((count = decrypted_data.read(buffer)) != -1) {
                    total += count;
                    publishProgress("" + (int) ((total * 100) / fileLength));
                    output.write(buffer, 0, count);
                }

                Intent intent = KeyChain.createInstallIntent();
                file = new File(context.getFilesDir().getAbsolutePath() + "/" + fileName);
                byte[] p12 = FileUtils.readFileToByteArray(file);
                intent.putExtra(KeyChain.EXTRA_PKCS12, p12);
                startActivity(intent);
                output.flush();
                output.close();

                if (file.exists()) {
                    file.delete();
                }

            } catch (Exception e) {
                Log.e("Error: ", e.getMessage());
            }
            return (null);
        }

        @Override
        protected void onProgressUpdate(String... values) {
            pDialog.setProgress(Integer.parseInt(values[0]));
        }

        @Override
        protected void onPostExecute(String fileName) {
            try {
                wait(100);
            }
            catch (Exception e) {
                Log.e("Error;", e.getMessage());
            }
            dismissDialog(progress_bar_type);
            Intent restart = getBaseContext().getPackageManager().getLaunchIntentForPackage(getBaseContext().getPackageName());
            restart.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(restart);
            /*setContentView(R.layout.activity_download);
            Intent start = new Intent(QRcode.this, MainActivity.class);
            startActivity(start);
            setContentView(R.layout.disconnect);
            */
        }
    }
}