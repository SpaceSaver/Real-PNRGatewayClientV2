package dev.altavision.pnrgatewayclient;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;
import android.util.Base64;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import java.nio.charset.StandardCharsets;

public class PDUReceiver extends BroadcastReceiver {
    private SharedPreferences mPrefs = null;
    private static final String TAG = "PDU_RCVR";

    private boolean sanityCheck(String result) {
        return result != null && result.contains("REG-RESP");
    }

    private boolean sanityCheck(SmsMessage message) {
        return message != null && sanityCheck(message.getMessageBody());
    }

    private String found(String input) {
        String resdata = input.substring(input.indexOf("REG-RESP"));
        Log.w(TAG, "PDU: " + resdata);
        return resdata;
    }

    private String parse(byte[] pdu) {
        // Print base64-encoded PDU
        Log.w(TAG, "PDU: " + new String(Base64.encode(pdu, Base64.DEFAULT)));
        Log.d(TAG, "Attempting to process message as 3gpp...");
        try {
            SmsMessage message = SmsMessage.createFromPdu((byte[]) pdu, "3gpp");
            if (sanityCheck(message)) {
                return found(message.getMessageBody());
            }
        } catch (Exception e) {
            Log.e(TAG, "The following error occurred while attempting to process PDU as 3gpp:\n" + e.toString());
        }
        Log.d(TAG, "Failed to get body using 3gpp parsing, trying 3gpp2...");
        try {
            SmsMessage message = SmsMessage.createFromPdu((byte[]) pdu, "3gpp2");
            if (sanityCheck(message)) {
                return found(message.getMessageBody());
            }
        } catch (Exception e) {
            Log.e(TAG, "The following error occurred while attempting to process PDU as 3gpp2:\n" + e.toString());
        }
        Log.d(TAG, "Failed to get body using 3gpp2 parsing, manually parsing...");
        try {
            String pduString = new String(pdu, 0, pdu.length, StandardCharsets.US_ASCII);
            if (sanityCheck(pduString)) {
                return found(pduString);
            }
        } catch (Exception e) {
            Log.e(TAG, "The following error occurred while attempting to process PDU manually:\n" + e.toString());
        }
        Log.w(TAG, "PDU could not be deciphered.");
        return null;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        //Runs whenever a data SMS PDU is received. On some carriers (i.e. AT&T), the REG-RESP message is sent as
        //  a data SMS (PDU) instead of a regular SMS message.

        Log.d("PDU_RCVR", "Got data SMS!!");

        mPrefs = PreferenceManager.getDefaultSharedPreferences(context);

        Bundle bundle = intent.getExtras();
        SmsMessage recMsg = null;
        byte[] data = null;
        if (bundle != null) {
            Object[] pdus = (Object[]) bundle.get("pdus");
            Log.d("PDU_RCVR","Got received PDUs: "+pdus.toString());

            //Loops through each received SMS
            for (int i = 0; i < pdus.length; i++) {

                //messageBody will include the REG-RESP text--i.e.
                //  REG-RESP?v=3;r=72325403;n=+11234567890;s=CA21C50C645469B25F4B65C38A7DCEC56592E038F39489F35C7CD6972D
                // String messageBody = recMsg.getMessageBody();

                //Hands the REG-RESP message off to the SMSReceiver to notify the user
                String code = parse((byte[]) pdus[i]);
                if (code != null) {
                    SMSReceiver.processResponseMessage(code, context);
                }

            }
        }
    }
}
