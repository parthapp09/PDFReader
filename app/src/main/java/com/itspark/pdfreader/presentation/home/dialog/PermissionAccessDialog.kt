package com.itspark.pdfreader.presentation.home.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Window
import com.itspark.pdfreader.databinding.DialogFilePermissionBinding

class PermissionAccessDialog(
    context: Context,
    private val onGoSettingClick: () -> Unit
) : Dialog(context) {

    private lateinit var binding: DialogFilePermissionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        binding = DialogFilePermissionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setCancelable(true)

        binding.btnGoSetting.setOnClickListener {
            dismiss()
            onGoSettingClick()
        }
    }
}
