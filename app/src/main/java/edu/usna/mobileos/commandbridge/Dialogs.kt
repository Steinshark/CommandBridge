package edu.usna.mobileos.commandbridge

import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import java.lang.reflect.GenericArrayType

class RadioButtonDialog(var commandItems: Array<String>, var myInterface: DRInterface) : DialogFragment(), DialogInterface.OnClickListener {

    lateinit var item:String                                                                        //What to return

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(activity)
        builder.setTitle("Select Display Item")
                .setSingleChoiceItems(commandItems, -1, this)
                .setPositiveButton("Set Display", this)
                .setNegativeButton("Cancel", this)
        return builder.create()
    }
    override fun onClick(dialog: DialogInterface, itemId: Int) {
        val clickedThing = when (itemId) {
            Dialog.BUTTON_NEGATIVE -> {}
            Dialog.BUTTON_POSITIVE -> myInterface.setDisplayMode(arrayListOf(item))
            else -> item = commandItems[itemId]                                                     //Set clicked item as item to return
        }
    }
}
class CheckBoxDialog(var commandItems: Array<String>, var myInterface: DRInterface) : DialogFragment(), DialogInterface.OnMultiChoiceClickListener, DialogInterface.OnClickListener {

    var returningModes = ArrayList<String>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(activity)
        builder.setTitle("Select Display Items")
            .setMultiChoiceItems(commandItems, null, this)
            .setPositiveButton("Set Display", this)
            .setNegativeButton("Cancel", this)
        return builder.create()
    }
    override fun onClick(dialog: DialogInterface, id: Int) {
        when (id) {
            Dialog.BUTTON_NEGATIVE -> {myInterface.setDisplayMode(returningModes as ArrayList<String>) }
            Dialog.BUTTON_POSITIVE -> {return}
        }
    }                                    //For Return
    override fun onClick(dialog: DialogInterface, itemId: Int, isChecked: Boolean) {                //For select items
        returningModes.add(commandItems[itemId])
    }
}