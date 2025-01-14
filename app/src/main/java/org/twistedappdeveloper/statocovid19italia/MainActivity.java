package org.twistedappdeveloper.statocovid19italia;

import static org.twistedappdeveloper.statocovid19italia.utils.Utils.DarkMode;
import static org.twistedappdeveloper.statocovid19italia.utils.Utils.DayMode;
import static org.twistedappdeveloper.statocovid19italia.utils.Utils.themeModeKey;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ShareCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.loopj.android.http.JsonHttpResponseHandler;
import com.loopj.android.http.SyncHttpClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.twistedappdeveloper.statocovid19italia.datastorage.DataStorage;
import org.twistedappdeveloper.statocovid19italia.fragments.DataVisualizerFragment;
import org.twistedappdeveloper.statocovid19italia.model.Changelog;
import org.twistedappdeveloper.statocovid19italia.network.NetworkUtils;
import org.twistedappdeveloper.statocovid19italia.utils.Utils;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import cz.msebera.android.httpclient.Header;

public class MainActivity extends AppCompatActivity {

    private ProgressDialog progressDialog;

    private FragmentManager fragmentManager;
    private DataVisualizerFragment currentFragment;

    private SyncHttpClient client;

    private final List<Changelog> changelogs = new ArrayList<>();

    private DataStorage nationalDataStorage;

    private SharedPreferences pref;

    private void setTheme() {
        pref = getApplicationContext().getSharedPreferences("default", 0);
        int themeMode = pref.getInt(themeModeKey, 0);
        switch (themeMode) {
            case DayMode:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case DarkMode:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme();
        super.onCreate(savedInstanceState);

        client = new SyncHttpClient();
        client.addHeader("Accept", "application/json");
        client.addHeader("Content-Type", "application/json");
        client.addHeader("X-API-KEY", getResources().getString(R.string.api_key));

        nationalDataStorage = DataStorage.createAndGetIstanceIfNotExist(getResources(), DataStorage.Scope.NAZIONALE);

        setContentView(R.layout.activity_main);
        fragmentManager = getSupportFragmentManager();

        currentFragment = DataVisualizerFragment.newInstance(DataStorage.defaultDataContext);
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.container, currentFragment);
        fragmentTransaction.commit();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.mainmenu, menu);
        return true;
    }

    private void openChangelog() {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(MainActivity.this);
        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.setTitle(String.format(getString(R.string.ver_installata), BuildConfig.VERSION_NAME));
        String versione = getString(R.string.versione).substring(0, 1).toUpperCase() + getString(R.string.versione).substring(1);
        String msgChangelogs = "";
        for (Changelog changelog : changelogs) {
            msgChangelogs = String.format("%s\n\n%s %s\n%s", msgChangelogs, versione, changelog.getVersionaName(), changelog.getDescription());
        }
        if (changelogs.isEmpty()) {
            alertDialog.setMessage(getString(R.string.no_changelog));
        } else {
            alertDialog.setMessage(msgChangelogs.substring(2));
        }
        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(android.R.string.ok),
                (dialog, which) -> dialog.dismiss());
        alertDialog.show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(MainActivity.this);
        AlertDialog alertDialog;
        switch (item.getItemId()) {
            case R.id.action_info:
                final SpannableString s =
                        new SpannableString(getString(R.string.infoMessage));
                Linkify.addLinks(s, Linkify.WEB_URLS);
                alertDialog = alertDialogBuilder.create();
                alertDialog.setTitle(getString(R.string.info));
                alertDialog.setMessage(s);
                alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(android.R.string.ok),
                        (dialog, which) -> dialog.dismiss());
                alertDialog.show();
                TextView txtDialog = alertDialog.findViewById(android.R.id.message);
                if (txtDialog != null) {
                    txtDialog.setMovementMethod(LinkMovementMethod.getInstance());
                }
                break;
            case R.id.action_changelog:
                openChangelog();
                break;
            case R.id.action_chart:
                if (currentFragment.getDataStorage().getDataLength() > 0) {
                    Intent chartActivity = new Intent(getApplicationContext(), ChartActivity.class);
                    chartActivity.putExtra(Utils.DATACONTEXT_KEY, currentFragment.getDataStorage().getDataContext());
                    startActivity(chartActivity);
                } else {
                    Toast.makeText(MainActivity.this, getResources().getString(R.string.no_data_to_display), Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.action_update:
                recoverDataFromNetwork(true);
                checkAppVersion();
                break;
            case R.id.action_change_datacontex:
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                List<String> regioni = new ArrayList<>();
                regioni.add(DataStorage.defaultDataContext);
                regioni.addAll(DataStorage.getIstance().getSubLevelDataKeys());
                final String[] dataContexs = regioni.toArray(new String[0]);
                int checkedItem = 0;
                for (int i = 0; i < dataContexs.length; i++) {
                    if (dataContexs[i].equalsIgnoreCase(currentFragment.getDataStorage().getDataContext())) {
                        checkedItem = i;
                        break;
                    }
                }
                builder.setSingleChoiceItems(dataContexs, checkedItem, (dialog, which) -> {
                    dialog.dismiss();
                    DataVisualizerFragment newFragment = DataVisualizerFragment.newInstance(dataContexs[which], currentFragment.getCursore());
                    FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
                    fragmentTransaction.replace(R.id.container, newFragment);
                    fragmentTransaction.commit();
                    currentFragment = newFragment;
                });
                builder.setTitle(getResources().getString(R.string.sel_dataContext));
                AlertDialog alert = builder.create();
                alert.show();
                break;
            case R.id.action_confronto_regionale:
                if (DataStorage.getIstance().getSubLevelDataKeys().size() > 0) {
                    Intent barChartActivity = new Intent(getApplicationContext(), BarChartActivity.class);
                    barChartActivity.putExtra(Utils.CURSORE_KEY, currentFragment.getCursore());
                    startActivity(barChartActivity);
                } else {
                    Toast.makeText(MainActivity.this, getResources().getString(R.string.no_data_to_display), Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.action_contatta:
                ShareCompat.IntentBuilder.from(MainActivity.this)
                        .setType("message/rfc822")
                        .addEmailTo("twistedappdeveloper@gmail.com")
                        .setSubject(String.format("%s: %s", getString(R.string.oggetto), getString(R.string.app_name)))
                        .setChooserTitle("Scegli un Client Email")
                        .startChooser();
                Toast.makeText(MainActivity.this, "Scegli un Client Email", Toast.LENGTH_SHORT).show();
                break;
            case R.id.action_settings:
                Toast.makeText(getApplicationContext(), "Cambio tema in corso...", Toast.LENGTH_SHORT).show();
                int themeMode = pref.getInt(themeModeKey, 0);
                SharedPreferences.Editor editor = pref.edit();
                if (themeMode == DayMode) {
                    editor.putInt(themeModeKey, DarkMode);
                } else {
                    editor.putInt(themeModeKey, DayMode);
                }
                editor.apply();
                setTheme();
                break;
        }
        return super.onOptionsItemSelected(item);
    }


    private String formatText(int n) {
        if (n == 1) {
            return String.format("%s %s", n, getResources().getString(R.string.versione));
        }
        return String.format("%s %s", n, getResources().getString(R.string.versioni));
    }

    private void checkAppVersion() {
        if (!NetworkUtils.isDeviceOnline(MainActivity.this)) {
            return;
        }

        new Thread(new Runnable() {


            @Override
            public void run() {
                Looper.prepare();
                client.get(getResources().getString(R.string.notification_file), new JsonHttpResponseHandler() {
                    @Override
                    public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
                        super.onSuccess(statusCode, headers, response);
                        try {
                            final int latestVersion = response.getInt("latest_app_version");

                            changelogs.clear();
                            JSONArray changelogJSONArray = response.getJSONArray("changelog");
                            for (int i = 0; i < changelogJSONArray.length(); i++) {
                                JSONObject jsonObject = changelogJSONArray.getJSONObject(i);
                                changelogs.add(new Changelog(jsonObject.getString("ver"), jsonObject.getString("description")));
                            }

                            final int currentVersion = BuildConfig.VERSION_CODE;
                            if (latestVersion > currentVersion) {
                                runOnUiThread(() -> {
                                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                                    builder.setTitle(getResources().getString(R.string.app_update));
                                    builder.setMessage(String.format(getResources().getString(R.string.app_update_message),
                                            formatText(latestVersion - currentVersion),
                                            BuildConfig.VERSION_NAME,
                                            changelogs.get(0).getVersionaName()
                                    ));
                                    if (BuildConfig.DEBUG) {
                                        builder.setPositiveButton(getResources().getString(R.string.aggiorna), new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                dialog.dismiss();
                                                Intent i = new Intent(Intent.ACTION_VIEW);
                                                i.setData(Uri.parse(getString(R.string.new_version_app_site)));
                                                startActivity(i);
                                            }
                                        });
                                    }
                                    builder.setNegativeButton(getString(R.string.chiudi), new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                        }
                                    });
                                    AlertDialog alert = builder.create();
                                    alert.show();
                                });
                            } else if (latestVersion < currentVersion) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(MainActivity.this, getResources().getString(R.string.app_preview), Toast.LENGTH_SHORT).show();
                                    }
                                });
                            } else {
                                String key = "versione";
                                SharedPreferences.Editor editor = pref.edit();
                                int version = pref.getInt(key, 0);
                                if (version < BuildConfig.VERSION_CODE) {
                                    editor.putInt(key, BuildConfig.VERSION_CODE);
                                    editor.apply();
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            openChangelog();
                                        }
                                    });
                                }
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        }).start();
    }


    private final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm", Locale.ITALY);

    private void recoverData() {

        progressDialog = ProgressDialog.show(MainActivity.this, "", getResources().getString(R.string.wait_local_pls), true);

        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean cacheIsValid = false;
                try {

                    JSONObject datiGlobali = new JSONObject(getJsonString("cached_dati_nazionali.json"));
                    JSONObject datiRegionali = new JSONObject(getJsonString("cached_dati_regionali.json"));
                    JSONObject datiProvinciali = new JSONObject(getJsonString("cached_dati_provinciali.json"));

                    Date cachedTimestamp = simpleDateFormat.parse(datiGlobali.getString("timestamp").substring(0, 16));
                    Calendar calendar = Calendar.getInstance();
                    calendar.setTime(cachedTimestamp);
                    calendar.add(Calendar.DATE, 1);
                    Date revisedCachedTimestamp = calendar.getTime();

                    if (revisedCachedTimestamp.before(new Date())) {
                        progressDialog.dismiss();
                        cacheIsValid = false;
                    } else {
                        cacheIsValid = true;
                        nationalDataStorage.setDataArrayJson(datiGlobali.getJSONArray("dataset_nazionale"));
                        nationalDataStorage.setSubLvlDataArrayJson(datiRegionali.getJSONArray("dataset_regionale"), DataStorage.DEN_REGIONE_KEY, DataStorage.Scope.REGIONALE);

                        Map<String, JSONArray> datiPerRegione = new HashMap<>();
                        for (int i = 0; i < datiProvinciali.getJSONArray("dataset_provinciale").length(); i++) {
                            try {
                                JSONObject jsonObject = datiProvinciali.getJSONArray("dataset_provinciale").getJSONObject(i);
                                String regione = jsonObject.getString(DataStorage.DEN_REGIONE_KEY);
                                JSONArray datiProvince;
                                if (datiPerRegione.containsKey(regione)) {
                                    datiProvince = datiPerRegione.get(regione);
                                } else {
                                    datiProvince = new JSONArray();
                                    datiPerRegione.put(regione, datiProvince);
                                }
                                datiProvince.put(jsonObject);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                        for (String regione : datiPerRegione.keySet()) {
                            nationalDataStorage
                                    .getDataStorageByDataContext(regione)
                                    .setSubLvlDataArrayJson(
                                            datiPerRegione.get(regione),
                                            DataStorage.DEN_PROVINCIA_KEY,
                                            DataStorage.Scope.PROVINCIALE
                                    );
                        }

                        runOnUiThread(() -> {
                            currentFragment = DataVisualizerFragment.newInstance(currentFragment.getDataStorage().getDataContext());
                            FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
                            fragmentTransaction.replace(R.id.container, currentFragment);
                            fragmentTransaction.commit();
                            progressDialog.dismiss();
                            Toast.makeText(getApplicationContext(), getString(R.string.dati_cache), Toast.LENGTH_SHORT).show();
                        });

                    }

                } catch (Exception ignored) {
                }

                if (!cacheIsValid) {
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        recoverDataFromNetwork(false);
                    });

                }

            }

            private String getJsonString(String fileName) throws IOException {
                FileInputStream fileIn = openFileInput(fileName);
                InputStreamReader inputRead = new InputStreamReader(fileIn);

                char[] inputBuffer = new char[1024];
                StringBuilder s = new StringBuilder();
                int charRead;

                while ((charRead = inputRead.read(inputBuffer)) > 0) {
                    String readstring = String.copyValueOf(inputBuffer, 0, charRead);
                    s.append(readstring);
                }
                inputRead.close();

                return s.toString();
            }
        }).start();

    }

    private void recoverDataFromNetwork(Boolean allData) {
        if (!NetworkUtils.isDeviceOnline(MainActivity.this)) {
            Toast.makeText(MainActivity.this, getResources().getString(R.string.no_connection), Toast.LENGTH_SHORT).show();
            return;
        }

        String testoDialog = allData ? getResources().getString(R.string.wait_full) : getResources().getString(R.string.wait_pls);
        progressDialog = ProgressDialog.show(MainActivity.this, "", testoDialog, true);

        final Thread threadFetchData = new Thread(new Runnable() {

            @Override
            public void run() {
                Looper.prepare();

                List<Integer> done = new ArrayList<>();

                int i = 0;
                while (done.isEmpty() && i < 3) {
                    Toast.makeText(MainActivity.this, String.format("Tentativo %s/3", ++i), Toast.LENGTH_SHORT).show();

                    if (!pref.getBoolean("counted", false)) {
                        client.post(getString(R.string.counter), new JsonHttpResponseHandler() {
                        });
                        SharedPreferences.Editor editor = pref.edit();
                        editor.putBoolean("counted", true);
                        editor.apply();
                    }

//                client.get(getString(R.string.dataset_avvisi), new JsonHttpResponseHandler() {
//                    @Override
//                    public void onSuccess(int statusCode, Header[] headers, JSONArray response) {
//                        super.onSuccess(statusCode, headers, response);
//                        nationalDataStorage.setAvvisiDataArrayJson(response);
                    String urlNazionali = getResources().getString(R.string.dataset_nazionale);
                    if (allData) {
                        urlNazionali = String.format("%s%s", urlNazionali, "?allData=true");
                    }
                    client.get(urlNazionali, new JsonHttpResponseHandler() {
                        @Override
                        public void onSuccess(int statusCode, Header[] headers, final JSONArray responseNazionale) {
                            super.onSuccess(statusCode, headers, responseNazionale);
                            nationalDataStorage.setDataArrayJson(responseNazionale);

                            String urlRegionale = getResources().getString(R.string.dataset_regionale);
                            if (allData) {
                                urlRegionale = String.format("%s%s", urlRegionale, "?allData=true");
                            }
                            client.get(urlRegionale, new JsonHttpResponseHandler() {
                                @Override
                                public void onSuccess(int statusCode, Header[] headers, final JSONArray responseRegionale) {
                                    super.onSuccess(statusCode, headers, responseRegionale);
                                    nationalDataStorage.setSubLvlDataArrayJson(responseRegionale, DataStorage.DEN_REGIONE_KEY, DataStorage.Scope.REGIONALE);

                                    String urlProvinciali = getResources().getString(R.string.dataset_provinciale);
                                    if (allData) {
                                        urlProvinciali = String.format("%s%s", urlProvinciali, "?allData=true");
                                    }
                                    client.get(urlProvinciali, new JsonHttpResponseHandler() {
                                        @Override
                                        public void onSuccess(int statusCode, Header[] headers, JSONArray responseProvinciale) {
                                            super.onSuccess(statusCode, headers, responseProvinciale);

                                            done.add(1);
                                            try {
                                                JSONObject datiGlobali = new JSONObject();
                                                JSONObject datiRegionali = new JSONObject();
                                                JSONObject datiProvinciali = new JSONObject();
                                                datiGlobali.put("dataset_nazionale", responseNazionale);
                                                datiGlobali.put("timestamp", responseNazionale.getJSONObject(responseNazionale.length() - 1).getString(DataStorage.DATA_KEY));
                                                datiRegionali.put("dataset_regionale", responseRegionale);
                                                datiProvinciali.put("dataset_provinciale", responseProvinciale);
                                                FileOutputStream fileOutNazionali = openFileOutput("cached_dati_nazionali.json", MODE_PRIVATE);
                                                FileOutputStream fileOutRegionali = openFileOutput("cached_dati_regionali.json", MODE_PRIVATE);
                                                FileOutputStream fileOutProvinciali = openFileOutput("cached_dati_provinciali.json", MODE_PRIVATE);

                                                OutputStreamWriter outputWriter = new OutputStreamWriter(fileOutNazionali);
                                                outputWriter.write(datiGlobali.toString());
                                                outputWriter.close();
                                                outputWriter = new OutputStreamWriter(fileOutRegionali);
                                                outputWriter.write(datiRegionali.toString());
                                                outputWriter.close();
                                                outputWriter = new OutputStreamWriter(fileOutProvinciali);
                                                outputWriter.write(datiProvinciali.toString());
                                                outputWriter.close();

                                            } catch (JSONException e) {
                                                e.printStackTrace();
                                            } catch (FileNotFoundException e) {
                                                e.printStackTrace();
                                            } catch (IOException e) {
                                                e.printStackTrace();
                                            }

                                            Map<String, JSONArray> datiPerRegione = new HashMap<>();
                                            for (int i = 0; i < responseProvinciale.length(); i++) {
                                                try {
                                                    JSONObject jsonObject = responseProvinciale.getJSONObject(i);
                                                    String regione = jsonObject.getString(DataStorage.DEN_REGIONE_KEY);
                                                    JSONArray datiProvince;
                                                    if (datiPerRegione.containsKey(regione)) {
                                                        datiProvince = datiPerRegione.get(regione);
                                                    } else {
                                                        datiProvince = new JSONArray();
                                                        datiPerRegione.put(regione, datiProvince);
                                                    }
                                                    datiProvince.put(jsonObject);
                                                } catch (JSONException e) {
                                                    e.printStackTrace();
                                                }
                                            }
                                            for (String regione : datiPerRegione.keySet()) {
                                                nationalDataStorage
                                                        .getDataStorageByDataContext(regione)
                                                        .setSubLvlDataArrayJson(
                                                                datiPerRegione.get(regione),
                                                                DataStorage.DEN_PROVINCIA_KEY,
                                                                DataStorage.Scope.PROVINCIALE
                                                        );
                                            }
                                        }
                                    });
                                }
                            });

                        }

                    });
                }


            }
//                });

//            }
        });

        threadFetchData.start();

        new Thread(() -> {
            try {
                threadFetchData.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                runOnUiThread(() -> {
                    currentFragment = DataVisualizerFragment.newInstance(currentFragment.getDataStorage().getDataContext());
                    FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
                    fragmentTransaction.replace(R.id.container, currentFragment);
                    fragmentTransaction.commit();
                    progressDialog.dismiss();
                });
            }
        }).start();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        recoverData();
        checkAppVersion();
    }


}
