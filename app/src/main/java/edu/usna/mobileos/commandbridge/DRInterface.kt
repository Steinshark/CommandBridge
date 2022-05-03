package edu.usna.mobileos.commandbridge

interface DRInterface {
    fun setStaticDisplayMode()
    fun setGraphDisplayMode(items: ArrayList<String>)
    fun doNothing(item:String)
    fun cancel()
}