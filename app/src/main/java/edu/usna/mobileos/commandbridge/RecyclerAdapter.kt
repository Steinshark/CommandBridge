package edu.usna.mobileos.commandbridge

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries

class TextItemViewHolder (val view : View) : RecyclerView.ViewHolder(view){

    val textView: TextView = view.findViewById(R.id.recyclerText)
    val Graph: GraphView = view.findViewById(R.id.recyclerGraph)

    // bind an item to a TextView
    fun bind(title: String, data: LineGraphSeries<DataPoint>) {
        Log.i("recycle","Setting info $title")
        textView.text = title
        Graph.addSeries(data)
    }
}


class RecyclerAdapter(val data: ArrayList<Command?>) : RecyclerView.Adapter<TextItemViewHolder>() {

    override fun getItemCount() = data.size

    // override our create function
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TextItemViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val view = layoutInflater.inflate(R.layout.item_layout, parent, false)
        return TextItemViewHolder(view)
    }

    // override our Bind function
    override fun onBindViewHolder(holder: TextItemViewHolder, position: Int) {
        try {
            holder.bind(data[position]!!.name, data[position]!!.graphData)
        }
        catch(n: NullPointerException){
            Log.i("recycle","Failed on $data")
        }
    }
}

// Create the interface for the listener
interface RecyclerListener {
    fun onItemClick(task: String)
}


