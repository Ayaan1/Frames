/*
 * Copyright (c) 2017. Jahir Fiquitiva
 *
 * Licensed under the CreativeCommons Attribution-ShareAlike
 * 4.0 International License. You may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *    http://creativecommons.org/licenses/by-sa/4.0/legalcode
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jahirfiquitiva.libs.frames.activities;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.github.javiersantos.piracychecker.PiracyChecker;
import com.github.javiersantos.piracychecker.enums.InstallerID;
import com.github.javiersantos.piracychecker.enums.PiracyCheckerCallback;
import com.github.javiersantos.piracychecker.enums.PiracyCheckerError;
import com.github.javiersantos.piracychecker.enums.PirateApp;

import java.util.ArrayList;

import jahirfiquitiva.libs.frames.R;
import jahirfiquitiva.libs.frames.activities.base.ThemedActivity;
import jahirfiquitiva.libs.frames.adapters.WallpapersAdapter;
import jahirfiquitiva.libs.frames.dialogs.FramesDialogs;
import jahirfiquitiva.libs.frames.fragments.CollectionFragment;
import jahirfiquitiva.libs.frames.utils.ColorUtils;
import jahirfiquitiva.libs.frames.utils.FavoritesUtils;
import jahirfiquitiva.libs.frames.utils.Preferences;
import jahirfiquitiva.libs.frames.utils.ThemeUtils;
import jahirfiquitiva.libs.frames.utils.ToolbarColorizer;
import jahirfiquitiva.libs.frames.utils.Utils;

public class FavoritesActivity extends ThemedActivity {

    private CollectionFragment favsFragment;

    private boolean check = true;
    private boolean allAma = false;
    private boolean checkLPF = true;
    private boolean checkStores = true;
    private String key = "";

    private MaterialDialog dialog;
    private PiracyChecker checker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        check = getIntent().getBooleanExtra("check", true);
        allAma = getIntent().getBooleanExtra("allAma", false);
        key = getIntent().getStringExtra("key");
        checkLPF = getIntent().getBooleanExtra("checkLPF", true);
        checkStores = getIntent().getBooleanExtra("checkStores", true);

        FavoritesUtils.init(this);

        setContentView(R.layout.activity_simple);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        ToolbarColorizer.colorizeToolbar(toolbar, ColorUtils.getMaterialPrimaryTextColor(
                !(ColorUtils.isLightColor(ThemeUtils.darkOrLight(this, R.color.dark_theme_primary,
                        R.color.light_theme_primary)))));
        ToolbarColorizer.tintStatusBar(this);

        favsFragment = CollectionFragment.newInstance(FavoritesUtils.getFavorites(this), true,
                false);

        getSupportFragmentManager().beginTransaction().replace(R.id.content, favsFragment, "favs")
                .commit();
    }

    @Override
    protected void onResume() {
        super.onResume();
        runLicenseChecker(check, key, allAma, checkLPF, checkStores);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        FavoritesUtils.destroy(this);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finishAndSendData();
                break;
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        finishAndSendData();
    }

    private void finishAndSendData() {
        Intent intent = new Intent();
        StringBuilder s = new StringBuilder("");
        if (favsFragment != null) {
            ArrayList<String> list = ((WallpapersAdapter) favsFragment.getRVAdapter())
                    .getModifiedWallpapers();
            for (int i = 0; i < list.size(); i++) {
                s.append(list.get(i));
                if (list.size() > 1 && i < (list.size() - 1)) {
                    s.append(",");
                }
            }
        }
        intent.putExtra("modified", s.toString());
        setResult(14, intent);
        finish();
    }

    private void runLicenseChecker(boolean ch, String lic, boolean allAma,
                                   boolean checkLPF, boolean checkStores) {
        Preferences mPrefs = new Preferences(this);
        if (ch) {
            if (Utils.isNewVersion(this) || (!(mPrefs.isDashboardWorking()))) {
                if (Utils.isConnected(this)) {
                    try {
                        checkLicense(lic, allAma, checkLPF, checkStores);
                    } catch (Exception e) {
                        showSimpleLicenseCheckErrorDialog();
                    }
                } else {
                    showSimpleLicenseCheckErrorDialog();
                }
            }
        } else {
            mPrefs.setDashboardWorking(true);
        }
    }

    private void checkLicense(final String lic, final boolean allAma, final boolean checkLPF,
                              final boolean checkStores) {
        destroyChecker();
        checker = new PiracyChecker(this);
        checker.enableInstallerId(InstallerID.GOOGLE_PLAY);
        if (lic != null && lic.length() > 50) checker.enableGooglePlayLicensing(lic);
        if (allAma) checker.enableInstallerId(InstallerID.AMAZON_APP_STORE);
        if (checkLPF) checker.enableUnauthorizedAppsCheck();
        if (checkStores) checker.enableStoresCheck();
        checker.enableEmulatorCheck(false)
                .enableDebugCheck()
                .callback(new PiracyCheckerCallback() {
                    @Override
                    public void allow() {
                        showLicensedDialog();
                    }

                    @Override
                    public void dontAllow(@NonNull PiracyCheckerError piracyCheckerError,
                                          @Nullable final PirateApp pirateApp) {
                        showNotLicensedDialog(pirateApp);
                    }

                    @Override
                    public void onError(@NonNull PiracyCheckerError error) {
                        showLicenseCheckErrorDialog(lic, allAma, checkLPF, checkStores);
                    }
                });
        checker.start();
    }

    private void showLicensedDialog() {
        clearDialog();
        final Preferences mPrefs = new Preferences(this);
        dialog = FramesDialogs.buildLicenseSuccessDialog(this,
                new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog materialDialog,
                                        @NonNull DialogAction dialogAction) {
                        mPrefs.setDashboardWorking(true);
                    }
                }, new MaterialDialog.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        mPrefs.setDashboardWorking(true);
                    }
                }, new MaterialDialog.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        mPrefs.setDashboardWorking(true);
                    }
                });
        dialog.show();
    }

    private void showLicenseCheckErrorDialog(final String lic, final boolean allAma,
                                             final boolean checkLPF, final boolean checkStores) {
        clearDialog();
        dialog = FramesDialogs.buildLicenseErrorDialog(this,
                new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog,
                                        @NonNull DialogAction which) {
                        dialog.dismiss();
                        checkLicense(lic, allAma, checkLPF, checkStores);
                    }
                }, new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog,
                                        @NonNull DialogAction which) {
                        finish();
                    }
                }, new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialogInterface) {
                        finish();
                    }
                }, new MaterialDialog.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        finish();
                    }
                });
        dialog.show();
    }

    private void showSimpleLicenseCheckErrorDialog() {
        clearDialog();
        dialog = FramesDialogs.buildLicenseErrorDialog(this, null,
                new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull
                            DialogAction which) {
                        finish();
                    }
                }, new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialogInterface) {
                        finish();
                    }
                }, new MaterialDialog.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        finish();
                    }
                });
        dialog.show();
    }

    private void showNotLicensedDialog(PirateApp app) {
        Preferences mPrefs = new Preferences(this);
        mPrefs.setDashboardWorking(false);
        clearDialog();
        dialog = FramesDialogs.buildShallNotPassDialog(this, app,
                new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog materialDialog, @NonNull
                            DialogAction
                            dialogAction) {
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://play.google.com/store/apps/details?id="
                                        + getPackageName())));
                    }
                }, new MaterialDialog.SingleButtonCallback() {

                    @Override
                    public void onClick(@NonNull MaterialDialog materialDialog, @NonNull
                            DialogAction
                            dialogAction) {
                        finish();
                    }
                }, new MaterialDialog.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        finish();
                    }
                }, new MaterialDialog.OnCancelListener() {

                    @Override
                    public void onCancel(DialogInterface dialog) {
                        finish();
                    }
                });
        dialog.show();
    }

    private void clearDialog() {
        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }
    }

    private void destroyChecker() {
        if (checker != null) {
            checker.destroy();
            checker = null;
        }
    }

}