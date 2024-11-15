package com.electric.rideflow

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.WindowManager
import android.widget.Toast
import com.electric.rideflow.databinding.DialogDataCompletionBinding

class DataCompletionDialog(context: Context) : Dialog(context) {

    private var onSaveListener: ((String, String) -> Unit)? = null
    private lateinit var binding : DialogDataCompletionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogDataCompletionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.saveButton.setOnClickListener {
            val dni = binding.dniInput.text.toString()
            val phone = binding.phoneInput.text.toString()
            if (dni.isNotEmpty() && phone.isNotEmpty()) {
                onSaveListener?.invoke(dni, phone)
                dismiss()
            }else{
                Toast.makeText(context,"Complete los campos!",Toast.LENGTH_SHORT)
            }
        }

        // Ajustar el tamaño del diálogo a 400dp x 200dp
        window?.setLayout(
            dpToPx(300), // Ancho del diálogo en píxeles
            WindowManager.LayoutParams.WRAP_CONTENT  // Alto del diálogo en píxeles
        )

    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }

    fun setOnSaveListener(listener: (String, String) -> Unit) {
        onSaveListener = listener
    }
}
