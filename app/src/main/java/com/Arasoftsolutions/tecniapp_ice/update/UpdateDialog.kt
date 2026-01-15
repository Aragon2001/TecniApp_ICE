package com.Arasoftsolutions.tecniapp_ice.update

import android.app.Dialog
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.Arasoftsolutions.tecniapp_ice.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class UpdateDialog : DialogFragment() {

    var onConfirmUpdate: ((UpdateInfo) -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val info = readUpdateInfo()
        val message = buildString {
            append(getString(R.string.update_dialog_message, info.versionName))
            if (info.notes.isNotBlank()) {
                append("\n\n")
                append(getString(R.string.update_dialog_notes_title))
                append("\n")
                append(info.notes)
            }
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.update_dialog_title)
            .setMessage(message)
            .setPositiveButton(R.string.update_dialog_action) { _, _ ->
                onConfirmUpdate?.invoke(info)
            }
            .setNegativeButton(R.string.update_dialog_later, null)
            .create()
    }

    private fun readUpdateInfo(): UpdateInfo {
        val args = requireArguments()
        return UpdateInfo(
            versionCode = args.getInt(ARG_VERSION_CODE),
            versionName = args.getString(ARG_VERSION_NAME).orEmpty(),
            apkUrl = args.getString(ARG_APK_URL).orEmpty(),
            sha256 = args.getString(ARG_SHA256),
            notes = args.getString(ARG_NOTES).orEmpty()
        )
    }

    companion object {
        private const val ARG_VERSION_CODE = "version_code"
        private const val ARG_VERSION_NAME = "version_name"
        private const val ARG_APK_URL = "apk_url"
        private const val ARG_SHA256 = "sha256"
        private const val ARG_NOTES = "notes"

        fun newInstance(info: UpdateInfo): UpdateDialog {
            return UpdateDialog().apply {
                arguments = bundleOf(
                    ARG_VERSION_CODE to info.versionCode,
                    ARG_VERSION_NAME to info.versionName,
                    ARG_APK_URL to info.apkUrl,
                    ARG_SHA256 to info.sha256,
                    ARG_NOTES to info.notes
                )
            }
        }
    }
}
