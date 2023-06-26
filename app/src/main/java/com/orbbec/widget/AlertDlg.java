package com.orbbec.widget;

import android.app.AlertDialog;
import android.content.Context;

import com.orbbec.ui.R;

public class AlertDlg {
    public static void showInfo(Context context, String msg) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Notification")
                .setIcon(R.drawable.ic_info)
                .setMessage(msg)
                .setNegativeButton("OK", (dialog, which) -> {
                    dialog.dismiss();
                });
        builder.show();
    }

    public static void showWarning(Context context, String msg, Response response) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Warning")
                .setMessage(msg)
                .setIcon(R.drawable.ic_warning)
                .setPositiveButton("OK", (dialog, which) -> {
                    response.positiveResponse();
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    response.negativeResponse();
                })
                .setCancelable(false);
        builder.show();
    }

    public static void showError(Context context, String msg, Response response) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Error")
                .setMessage(msg)
                .setIcon(R.drawable.ic_error)
                .setPositiveButton("OK", (dialog, which) -> {
                    response.positiveResponse();
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    response.negativeResponse();
                })
                .setCancelable(false);
        builder.show();
    }

    public interface Response {
        void negativeResponse();

        void positiveResponse();
    }
}
