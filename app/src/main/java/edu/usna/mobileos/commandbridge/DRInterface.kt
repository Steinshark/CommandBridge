package edu.usna.mobileos.commandbridge

interface DRInterface {
    fun setDisplayMode(items: ArrayList<String>)
    fun doNothing(item:String)
    fun cancel()
}