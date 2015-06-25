/**
 * Copyright (c) 2014, Airbitz Inc
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms are permitted provided that
 * the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 3. Redistribution or use of modified source code requires the express written
 *    permission of Airbitz Inc.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * The views and conclusions contained in the software and documentation are those
 * of the authors and should not be interpreted as representing official policies,
 * either expressed or implied, of the Airbitz Project.
 */

package com.airbitz.fragments.send;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.airbitz.AirbitzApplication;
import com.airbitz.R;
import com.airbitz.activities.NavigationActivity;
import com.airbitz.adapters.WalletPickerAdapter;
import com.airbitz.api.CoreAPI;
import com.airbitz.fragments.WalletBaseFragment;
import com.airbitz.fragments.HelpFragment;
import com.airbitz.fragments.settings.SettingFragment;
import com.airbitz.fragments.wallet.WalletsFragment;
import com.airbitz.models.BleDevice;
import com.airbitz.models.Wallet;
import com.airbitz.models.WalletPickerEnum;
import com.airbitz.objects.BleUtil;
import com.airbitz.objects.BluetoothListView;
import com.airbitz.objects.HighlightOnPressButton;
import com.airbitz.objects.HighlightOnPressSpinner;
import com.airbitz.objects.QRCamera;

import java.util.ArrayList;
import java.util.List;


/**
 * Created on 2/22/14.
 */
public class SendFragment extends WalletBaseFragment implements
        QRCamera.OnScanResult,
        BluetoothListView.OnPeripheralSelected,
        CoreAPI.OnWalletLoaded,
        BluetoothListView.OnBitcoinURIReceived,
        BluetoothListView.OnOneScanEnded {
    private final String TAG = getClass().getSimpleName();

    private final String FIRST_USAGE_COUNT = "com.airbitz.fragments.send.firstusagecount";
    private final String FIRST_BLE_USAGE_COUNT = "com.airbitz.fragments.send.firstusageblecount";

    public static final String AMOUNT_SATOSHI = "com.airbitz.Sendfragment_AMOUNT_SATOSHI";
    public static final String AMOUNT_FIAT = "com.airbitz.Sendfragment_AMOUNT_FIAT";
    public static final String LABEL = "com.airbitz.Sendfragment_LABEL";
    public static final String CATEGORY = "com.airbitz.Sendfragment_CATEGORY";
    public static final String RETURN_URL = "com.airbitz.Sendfragment_RETURN_URL";
    public static final String NOTES = "com.airbitz.Sendfragment_NOTES";
    public static final String LOCKED = "com.airbitz.Sendfragment_LOCKED";
    public static final String UUID = "com.airbitz.Sendfragment_UUID";
    public static final String IS_UUID = "com.airbitz.Sendfragment_IS_UUID";
    public static final String FROM_WALLET_UUID = "com.airbitz.Sendfragment_FROM_WALLET_UUID";

    private Handler mHandler;
    private boolean hasCheckedFirstUsage;
    private EditText mToEdittext;
    private TextView mFromTextView;
    private TextView mToTextView;
    private TextView mQRCodeTextView;
    private ImageButton mBluetoothButton;
    private ListView mToWalletListView;
    private RelativeLayout mListviewContainer;
    private RelativeLayout mCameraLayout;
    private RelativeLayout mBluetoothLayout;
    private BluetoothListView mBluetoothListView;
    private HighlightOnPressSpinner pickWalletSpinner;
    private HighlightOnPressButton mHelpButton;
    private List<Wallet> mWalletOtherList;//NAMES
    private List<Wallet> mWallets;//Actual wallets
    private Wallet mFromWallet;
    private String mReturnURL;
    private List<Wallet> mCurrentListing;
    private WalletPickerAdapter listingAdapter;
    private boolean mForcedBluetoothScanning = false;
    private View mView;
    QRCamera mQRCamera;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mView = inflater.inflate(R.layout.fragment_send, container, false);

        mBluetoothLayout = (RelativeLayout) mView.findViewById(R.id.fragment_send_bluetooth_layout);
        mCameraLayout = (RelativeLayout) mView.findViewById(R.id.fragment_send_layout_camera);
        mQRCamera = new QRCamera(this, mCameraLayout);

        mFromTextView = (TextView) mView.findViewById(R.id.textview_from);
        mToTextView = (TextView) mView.findViewById(R.id.textview_to);
        mQRCodeTextView = (TextView) mView.findViewById(R.id.textview_scan_qrcode);

        mToEdittext = (EditText) mView.findViewById(R.id.edittext_to);

        mCurrentListing = new ArrayList<Wallet>();
        listingAdapter = new WalletPickerAdapter(getActivity(), mCurrentListing, WalletPickerEnum.SendTo);
        mToWalletListView.setAdapter(listingAdapter);

        mFromTextView.setTypeface(NavigationActivity.latoBlackTypeFace);
        mToTextView.setTypeface(NavigationActivity.latoBlackTypeFace);
        mToEdittext.setTypeface(NavigationActivity.latoRegularTypeFace);
        mQRCodeTextView.setTypeface(NavigationActivity.latoRegularTypeFace);

        final RelativeLayout header = (RelativeLayout) mView.findViewById(R.id.fragment_send_header);
        mHelpButton = (HighlightOnPressButton) header.findViewById(R.id.layout_wallet_select_header_right);
        mHelpButton.setVisibility(View.VISIBLE);
        mHelpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mActivity.pushFragment(new HelpFragment(HelpFragment.SEND), NavigationActivity.Tabs.SEND.ordinal());
            }
        });

        pickWalletSpinner = (HighlightOnPressSpinner) header.findViewById(R.id.layout_wallet_select_header_spinner);
        pickWalletSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                mFromWallet = mWallets.get(i);
                updateWalletOtherList();
                goAutoCompleteWalletListing();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

//        mBluetoothButton = mQRCamera.getBluetoothButton();
//        mBluetoothButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                mForcedBluetoothScanning = true;
//                ViewBluetoothPeripherals(true);
//            }
//        });

        mToEdittext.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    checkAndSendAddress(mToEdittext.getText().toString());
                    return true;
                }
                return false;
            }
        });

        mToEdittext.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                mActivity.showSoftKeyboard(mToEdittext);
            }

            @Override
            public void afterTextChanged(Editable editable) {
                goAutoCompleteWalletListing();
            }
        });

        mToEdittext.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (hasFocus) {
                    goAutoCompleteWalletListing();
                    mActivity.showSoftKeyboard(mToEdittext);
                } else {
                    mListviewContainer.setVisibility(View.GONE);
                }
            }
        });

        mToEdittext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mListviewContainer.setVisibility(View.VISIBLE);
            }
        });

        mToEdittext.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    if (mListviewContainer.getVisibility() == View.VISIBLE) {
                        mListviewContainer.setVisibility(View.GONE);
                        return true;
                    }
                }
                return false;
            }
        });

        mToWalletListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                CoreAPI.SpendTarget target = mCoreApi.getNewSpendTarget();
                target.newTransfer(mCurrentListing.get(i).getUUID());
                GotoSendConfirmation(target);
            }
        });

        // if BLE is supported on the device, enable
        if (mActivity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            if (SettingFragment.getBLEPref()) {
                mBluetoothListView = new BluetoothListView(mActivity);
                mBluetoothLayout.addView(mBluetoothListView, 0);
                mBluetoothButton.setVisibility(View.VISIBLE);
            }
            else {
                // Bluetooth is not enabled - ask for enabling?
            }
        }

        return mView;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_standard, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_help:
                mActivity.pushFragment(new HelpFragment(HelpFragment.SEND), NavigationActivity.Tabs.SEND.ordinal());
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void checkAndSendAddress(String strTo) {

        if (strTo == null || strTo.isEmpty()) {
            ((NavigationActivity) getActivity()).hideSoftKeyboard(mToEdittext);
//            mListviewContainer.setVisibility(View.GONE);
            return;
        }
        newSpend(strTo);
    }

    public void stopCamera() {
        Log.d(TAG, "stopCamera");
        mQRCamera.stopCamera();
    }

    public void startCamera() {
        mQRCamera.startCamera();
        mQRCamera.setOnScanResultListener(this);
        checkFirstUsage();
    }

    private void checkFirstUsage() {
        SharedPreferences prefs = AirbitzApplication.getContext().getSharedPreferences(AirbitzApplication.PREFS, Context.MODE_PRIVATE);
        int count = prefs.getInt(FIRST_USAGE_COUNT, 1);
        if(count <= 2) {
            count++;
            mActivity.ShowFadingDialog(getString(R.string.fragment_send_first_usage), 5000);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(FIRST_USAGE_COUNT, count);
            editor.apply();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (mQRCamera != null && requestCode == QRCamera.RESULT_LOAD_IMAGE && resultCode == Activity.RESULT_OK && null != data) {
            mQRCamera.onActivityResult(requestCode, resultCode, data);
        }
    }

    public void GotoSendConfirmation(CoreAPI.SpendTarget target) {
        if (mToEdittext != null) {
            mActivity.hideSoftKeyboard(mToEdittext);
        }
        SendConfirmationFragment fragment = new SendConfirmationFragment();
        fragment.setSpendTarget(target);
        Bundle bundle = new Bundle();
        if (mFromWallet == null) {
            mFromWallet = mWallets.get(0);
        }
        bundle.putString(FROM_WALLET_UUID, mFromWallet.getUUID());
        fragment.setArguments(bundle);
        if (mActivity != null)
            mActivity.pushFragment(fragment, NavigationActivity.Tabs.SEND.ordinal());
    }

    @Override
    public void onResume() {
        super.onResume();

        mCoreApi.setOnWalletLoadedListener(this);

        hasCheckedFirstUsage = false;
        if (mHandler == null) {
            mHandler = new Handler();
        }

        startCamera();

        if(mBluetoothButton.getVisibility() == View.VISIBLE) {
            ViewBluetoothPeripherals(true);
            mBluetoothListView.setOnOneScanEndedListener(this);
        }
        else {
            ViewBluetoothPeripherals(false);
        }

        if (pickWalletSpinner != null && pickWalletSpinner.getAdapter() != null) {
            ((WalletPickerAdapter) pickWalletSpinner.getAdapter()).notifyDataSetChanged();
        }

        final NfcManager nfcManager = (NfcManager) mActivity.getSystemService(Context.NFC_SERVICE);
        NfcAdapter mNfcAdapter = nfcManager.getDefaultAdapter();

        if (mNfcAdapter != null && mNfcAdapter.isEnabled() && SettingFragment.getNFCPref()) {
            mQRCodeTextView.setText(getString(R.string.send_scan_text_nfc));
        }
        else {
            mQRCodeTextView.setText(getString(R.string.send_scan_text));
        }
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser) {
            mQRCamera.startCamera();
        } else {
            if (mQRCamera != null) {
                mQRCamera.stopCamera();
            }
        }
    }

    public void updateWalletOtherList() {
        mWalletOtherList = new ArrayList<Wallet>();
        for (Wallet wallet : mWallets) {
            if (mFromWallet != null && mFromWallet.getUUID() != null && !wallet.getUUID().equals(mFromWallet.getUUID())) {
                mWalletOtherList.add(wallet);
            }
        }
    }

    public void goAutoCompleteWalletListing() {
        if(mWalletOtherList == null) {
            return;
        }
        String text = mToEdittext.getText().toString();
        mCurrentListing.clear();
        if (text.isEmpty()) {
            for (Wallet w : mWalletOtherList) {
                if (!w.isArchived()) {
                    mCurrentListing.add(w);
                }
            }
        } else {
            for (Wallet w : mWalletOtherList) {
                if (!w.isArchived() && w.getName().toLowerCase().contains(text.toLowerCase())) {
                    mCurrentListing.add(w);
                }
            }
        }
        if (mCurrentListing.isEmpty() || !mToEdittext.hasFocus()) {
            mListviewContainer.setVisibility(View.GONE);
        } else {
            mListviewContainer.setVisibility(View.VISIBLE);
        }
        listingAdapter.notifyDataSetChanged();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopCamera();
        if(mBluetoothListView != null) {
            mBluetoothListView.close();
            mBluetoothListView.setOnOneScanEndedListener(null);
        }

        mCoreApi.setOnWalletLoadedListener(null);
        hasCheckedFirstUsage = false;
    }

    public void ShowMessageAndStartCameraDialog(String title, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(new ContextThemeWrapper(getActivity(), R.style.AlertDialogCustom));
        builder.setMessage(message)
                .setTitle(title)
                .setCancelable(false)
                .setNeutralButton(getResources().getString(R.string.string_ok),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                startCamera();
                                dialog.cancel();
                            }
                        }
                );
        AlertDialog alert = builder.create();
        alert.show();
    }

    //************** Bluetooth support

    private void checkFirstBLEUsage() {
        if(hasCheckedFirstUsage) {
            return;
        }
        SharedPreferences prefs = AirbitzApplication.getContext().getSharedPreferences(AirbitzApplication.PREFS, Context.MODE_PRIVATE);
        int count = prefs.getInt(FIRST_BLE_USAGE_COUNT, 1);
        if(count <= 2) {
            count++;
            mActivity.ShowFadingDialog(getString(R.string.fragment_send_first_usage_ble), 5000);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(FIRST_BLE_USAGE_COUNT, count);
            editor.apply();
        }
        hasCheckedFirstUsage = true;
    }

    // Show Bluetooth peripherals
    private void ViewBluetoothPeripherals(boolean bluetooth) {
        if(bluetooth) {
            mCameraLayout.setVisibility(View.GONE);
            mBluetoothLayout.setVisibility(View.VISIBLE);
            mQRCodeTextView.setVisibility(View.GONE);
            startBluetoothSearch();
        }
        else {
            stopBluetoothSearch();
            mCameraLayout.setVisibility(View.VISIBLE);
            if(mBluetoothLayout.getVisibility() != View.GONE  &&  mBluetoothLayout.getAnimation() == null) {
                mBluetoothLayout.animate()
                        .alpha(0f)
                        .setDuration(200)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                mBluetoothLayout.setVisibility(View.GONE);
                            }
                        });
            }
            mQRCodeTextView.setVisibility(View.VISIBLE);
        }
    }

    // Start the Bluetooth search
    private void startBluetoothSearch() {
        if(mBluetoothListView != null && BleUtil.isBleAvailable(mActivity)) {
            mBluetoothListView.setOnPeripheralSelectedListener(this);
            mBluetoothListView.scanForBleDevices(true);
        }
    }

    // Stop the Bluetooth search
    private void stopBluetoothSearch() {
        if(mBluetoothListView != null && BleUtil.isBleAvailable(mActivity)) {
            mBluetoothListView.scanForBleDevices(false);
            mBluetoothListView.close();
        }
    }

    @Override
    public void onPeripheralSelected(BleDevice device) {
        stopBluetoothSearch();
        mBluetoothListView.setOnPeripheralSelectedListener(null);
        mBluetoothListView.setOnBitcoinURIReceivedListener(this);
        mBluetoothListView.connectGatt(device);
    }

    @Override
    public void onBitcoinURIReceived(final String bitcoinAddress) {
        if(mBluetoothListView != null) {
            mBluetoothListView.setOnBitcoinURIReceivedListener(null);
        }
        mToEdittext.post(new Runnable() {
            @Override
            public void run() {
                checkAndSendAddress(bitcoinAddress);
            }
        });
    }

    @Override
    public void onOneScanEnded(boolean hasDevices) {
        if(!hasDevices) {
            if(mForcedBluetoothScanning) {
                mBluetoothLayout.setVisibility(View.VISIBLE);
            }
            else {
                Log.d(TAG, "No bluetooth devices, switching to guns...");
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        ViewBluetoothPeripherals(false);
                    }
                });
            }
        }
        else {
            checkFirstBLEUsage();
        }
    }

    @Override
    public void onScanResult(String result) {
        Log.d(TAG, "checking result = " + result);
        if (result != null) {
            newSpend(result);
            mQRCamera.setOnScanResultListener(null);
        } else {
            ShowMessageAndStartCameraDialog(getString(R.string.send_title), getString(R.string.fragment_send_send_bitcoin_unscannable));
        }
    }

    @Override
    public void onWalletsLoaded() {
        mWallets = mCoreApi.getCoreActiveWallets();
        final WalletPickerAdapter dataAdapter = new WalletPickerAdapter(getActivity(), mWallets, WalletPickerEnum.SendFrom);
        pickWalletSpinner.setAdapter(dataAdapter);

        if (!mWallets.isEmpty()) {
            mFromWallet = mWallets.get(0);
        }
        Bundle bundle = getArguments();
        if (bundle != null) {
            String uuid = bundle.getString(UUID); // From a wallet with this UUID
            if (uuid != null) {
                mFromWallet = mCoreApi.getWalletFromUUID(uuid);
                if (mFromWallet != null) {
                    for (int i = 0; i < mWallets.size(); i++) {
                        if (mFromWallet.getUUID().equals(mWallets.get(i).getUUID()) && !mWallets.get(i).isArchived()) {
                            final int finalI = i;
                            pickWalletSpinner.post(new Runnable() {
                                @Override
                                public void run() {
                                    pickWalletSpinner.setSelection(finalI);
                                }
                            });
                            break;
                        }
                    }
                }
            } else if (bundle.getString(WalletsFragment.FROM_SOURCE).equals(NavigationActivity.URI_SOURCE)) {
                String uriData = bundle.getString(NavigationActivity.URI_DATA);
                bundle.putString(NavigationActivity.URI_DATA, ""); //to clear the URI_DATA after reading once
                if (!uriData.isEmpty()) {
                    newSpend(uriData);
                }
            }
        }

        updateWalletOtherList();
    }

    private void newSpend(String text) {
        new NewSpendTask().execute(text);
    }

    public class NewSpendTask extends AsyncTask<String, Void, Boolean> {
        CoreAPI.SpendTarget target;

        NewSpendTask() {
            target = mCoreApi.getNewSpendTarget();
        }

        @Override
        protected Boolean doInBackground(String... text) {
            return target.newSpend(text[0]);
        }

        @Override
        protected void onPostExecute(final Boolean result) {
            if (result) {
                GotoSendConfirmation(target);
            } else {
                ((NavigationActivity) getActivity()).hideSoftKeyboard(mToEdittext);
                ((NavigationActivity) getActivity()).ShowOkMessageDialog(
                        getResources().getString(R.string.fragment_send_failure_title),
                        getString(R.string.fragment_send_confirmation_invalid_bitcoin_address));
            }
        }

        @Override
        protected void onCancelled() {
        }
    }
}
