package org.bottiger.podcast.activities.openopml;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.bottiger.podcast.R;
import org.bottiger.podcast.ToolbarActivity;
import org.bottiger.podcast.flavors.CrashReporter.VendorCrashReporter;
import org.bottiger.podcast.utils.OPMLImportExport;
import org.bottiger.podcast.utils.SDCardManager;

import java.io.File;
import java.io.IOException;

import static org.bottiger.podcast.SubscriptionsFragment.EXPORT_FILENAME;
import static org.bottiger.podcast.SubscriptionsFragment.RESULT_EXPORT;
import static org.bottiger.podcast.SubscriptionsFragment.RESULT_EXPORT_TO_CLIPBOARD;
import static org.bottiger.podcast.SubscriptionsFragment.RESULT_IMPORT;

public class OPMLImportExportActivity extends ToolbarActivity {

    private static final String TAG = OPMLImportExportActivity.class.getSimpleName();

    /*
    The status codes can be changed, they just need to be the same in all activities, it doesn't matter the number,
        it just matters that reimains the same everywhere.
     */
    public static final int OPML_ACTIVITY_STATUS_CODE = 999; //This number is needed but it can be any number ^^

    private static final int ACTIVITY_CHOOSE_FILE_STATUS_CODE = 99;
    private static final int INTERNAL_STORAGE_PERMISSION_REQUEST = 9;
    private static final String EXTRAS_CODE = "path";
    private static final String EXPORT_RETURN_CODE = "RETURN_EXPORT";
    private static final String MimeType = "file/xml";
    private static final String[] MIME_TYPES = {"file/xml", "application/xml", "text/xml", "text/x-opml", "text/plain"};

    OPMLImportExport importExport;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_opml_import_export);

        //PERMISSION CHECK
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Requesting read storage permission");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, INTERNAL_STORAGE_PERMISSION_REQUEST);
        }

        TextView opml_import_export_text = findViewById(R.id.export_opml_text);
        Resources res = getResources();
        String opml_text;
        try {
            String dir = SDCardManager.getExportDir();
            opml_text = String.format(res.getString(R.string.opml_export_explanation_dynamic), dir);

        } catch (IOException e) {
            Log.e(TAG, "Could not access the OPML export dir");
            e.printStackTrace();
            opml_text = res.getString(R.string.opml_export_explanation_dynamic);
        }

        opml_import_export_text.setText(opml_text);

        Toolbar toolbar = findViewById(R.id.opml_import_export_toolbar);
        if (toolbar != null) {
            toolbar.setTitle(R.string.opml_import_export_toolbar_title);
            setSupportActionBar(toolbar);
        }
        ActionBar actionbar = getSupportActionBar();

        if (actionbar != null)
            actionbar.setDisplayHomeAsUpEnabled(true);

        //PAST THIS POINT WE ASSUME WE HAVE THE NEEDED PERMISSIO

        importExport = new OPMLImportExport(this);

        /*
            2 Listeners set, one for each button.
            The import one call to startActivityForResult and prompts the user to select the file, then returns to SubscriptionsFragment
                overrided method "onActivityResult"

            The export one returns directly to SubscriptionFragment like the other one
         */
        Button importOPML = findViewById(R.id.bOPML_import);
        importOPML.setOnClickListener(view -> {
            Log.e(TAG, "Import OPML clicked");
            Intent chooseFile;
            Intent intent;
            chooseFile = new Intent(Intent.ACTION_GET_CONTENT);
            //Unless you have an external file manager, this not seems to work, the mimetype is wrong or something

            //if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            chooseFile.setType("*/*");
            //chooseFile.putExtra(Intent.EXTRA_MIME_TYPES, MIME_TYPES);
            //} else {
            //    chooseFile.setType(MimeType);
            //}
            intent = Intent.createChooser(chooseFile, "Choose a file"); //this string is not important since is not shown
            startActivityForResult(intent, ACTIVITY_CHOOSE_FILE_STATUS_CODE);
        });


        Button exportOPML = findViewById(R.id.bOMPLexport);
        exportOPML.setOnClickListener(view -> {
            Log.e(TAG, "Export OPML to filesystem clicked");
            //The return data is not checked in this particular case

            String export_dir = "";
            try {
                export_dir = SDCardManager.getExportDir();
            } catch (SecurityException e) {
                VendorCrashReporter.report("EXPORT FAIL", "Cannot export OPML file");
            } catch (IOException e) {
                VendorCrashReporter.report("EXPORT FAIL", "Cannot export OPML file");
            }
            Log.d(TAG, "EXPORT SUBSCRIPTIONS");// NoI18N
            Log.d(TAG, "Export to: " + export_dir + EXPORT_FILENAME);// NoI18N
            importExport.exportSubscriptions(new File(export_dir + EXPORT_FILENAME));

            if (getParent() == null) {
                setResult(RESULT_EXPORT, null);
            } else {
                getParent().setResult(RESULT_EXPORT, null);
            }
            finish();
        });

        Button exportClipboardOPML = findViewById(R.id.bOMPL_clipboard_export);
        exportClipboardOPML.setOnClickListener(view -> {
            Log.e(TAG, "Export OPML to clipboard clicked");

            importExport.exportSubscriptionsToClipboard();

            //The return data is not checked in this particular case
            if (getParent() == null) {
                setResult(RESULT_EXPORT_TO_CLIPBOARD, null);
            } else {
                getParent().setResult(RESULT_EXPORT_TO_CLIPBOARD, null);
            }
            finish();
        });
    }

    public static void handleImportActivityResult(@NonNull Activity activity, int requestCode, int resultCode, Intent data) {
        if (requestCode == OPML_ACTIVITY_STATUS_CODE) {
            OPMLImportExportActivity.handleResult(activity, requestCode, resultCode, data);
        }
    }

    public static void handleResult(@NonNull Activity argActivity, int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_IMPORT){
            Uri extraData = data.getData();

            if (ActivityCompat.checkSelfPermission(argActivity, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(argActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            Log.d(TAG, "IMPORT SUBSCRIPTIONS. File: " + extraData);// NoI18N
            Intent selectSubs = new Intent(argActivity.getApplicationContext(), OpenOpmlFromIntentActivity.class);
            selectSubs.setData(extraData);
            argActivity.startActivity(selectSubs);

        }
        else if (resultCode == RESULT_EXPORT) {

            String export_dir = null;
            try {
                export_dir = SDCardManager.getExportDir();
            } catch (IOException e) {
                VendorCrashReporter.report("EXPORT FAIL", "Cannot export OPML file");
            }

            Toast.makeText(argActivity.getApplicationContext(), argActivity.getString(R.string.opml_exported_to_toast) + export_dir + EXPORT_FILENAME, Toast.LENGTH_SHORT).show();
        } else if (resultCode == RESULT_EXPORT_TO_CLIPBOARD) {
            Log.d(TAG, "EXPORT SUBSCRIPTIONS TO CLIPBOARD");// NoI18N

            Toast.makeText(argActivity.getApplicationContext(), argActivity.getString(R.string.opml_exported_to_clipboard_toast), Toast.LENGTH_SHORT).show();
        }
    }


    //This method is called when the activity resumes from the filesystem selection.

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK) {
            Log.e(TAG, "Operation cancelled because of error or by user");// NoI18N
            //finish();
        }
        if (requestCode == ACTIVITY_CHOOSE_FILE_STATUS_CODE && resultCode == RESULT_OK) {
            Uri uri = data.getData();

            Log.e(TAG, "onActivityResult returned RESULT_OK. uri: " + uri);// NoI18N
            //RETURN THE VALUE TO THE APP AND CLOSE
            Intent returnData = new Intent();
            if (getParent() == null) {
                Log.e(TAG, "getParent() == null");// NoI18N
                returnData.setData(uri);
                setResult(RESULT_IMPORT, returnData);
            } else {
                Log.e(TAG, "getParent() != null");// NoI18N
                getParent().setResult(RESULT_IMPORT, data);
            }
            finish();
        }
    }

}
