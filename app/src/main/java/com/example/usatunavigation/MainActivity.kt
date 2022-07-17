package com.example.usatunavigation


import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color.rgb
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.*
import android.widget.AdapterView.OnItemSelectedListener
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.preference.PreferenceManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.toast
import org.jetbrains.anko.uiThread
import org.json.JSONArray
import org.json.JSONTokener
import org.osmdroid.bonuspack.kml.KmlDocument
import org.osmdroid.bonuspack.kml.KmlLineString
import org.osmdroid.bonuspack.kml.Style
import org.osmdroid.config.Configuration.getInstance
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.GroundOverlay
import org.osmdroid.views.overlay.OverlayItem
import org.osmdroid.views.overlay.Polyline
import java.io.IOException
import java.io.InputStream
import java.net.URL


class MainActivity : AppCompatActivity(), OnItemSelectedListener {

    private val REQUEST_PERMISSIONS_REQUEST_CODE = 1

    lateinit var map: MapView
    private val kmlDocument = KmlDocument()
    private val defaultStyle = Style(
        null,
        rgb( 41, 41, 41 ),
        4.0f,
        rgb(131, 176, 202)
    )
    private val lineStyle = Style(
        null,
        rgb( 217, 33, 25 ),
        9.0f,
        rgb(0, 0, 0)
    )

    private val startFloors: MutableList<String> = ArrayList()
    private val endFloors: MutableList<String> = ArrayList()
    private val startRooms: MutableList<String> = ArrayList()
    private val endRooms: MutableList<String> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        setContentView(R.layout.activity_main)

        //BOTTOM SHEET
        BottomSheetBehavior.from(findViewById(R.id.sheet)).apply {
            peekHeight = 120
            this.state = BottomSheetBehavior.STATE_COLLAPSED
        }

        // SPINNER ADAPTERS

        //STARTCAMPUS
        val startCampusSpinner: Spinner = findViewById(R.id.start_campus_sp)
        val startCampusAdapter: ArrayAdapter<CharSequence> = ArrayAdapter.createFromResource(
            applicationContext,
            R.array.campuses,
            android.R.layout.simple_spinner_item)
        with(startCampusAdapter) {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            notifyDataSetChanged()
        }
        startCampusSpinner.adapter = startCampusAdapter
        startCampusSpinner.onItemSelectedListener = this@MainActivity
        //ENDCAMPUS
        val endCampusSpinner: Spinner = findViewById(R.id.end_campus_sp)
        val endCampusAdapter: ArrayAdapter<CharSequence> = ArrayAdapter.createFromResource(
            applicationContext,
            R.array.campuses,
            android.R.layout.simple_spinner_item)
        with(endCampusAdapter) {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            notifyDataSetChanged()
        }
        endCampusSpinner.adapter = endCampusAdapter
        endCampusSpinner.onItemSelectedListener = this@MainActivity

        // MAP
        map = findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        val mapController = map.controller
        mapController.setZoom(18.5)
        map.setMultiTouchControls(true)
        map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)

        // KML
        //val root: KmlFolder = kmlDocument.mKmlRoot
        val startPoint = GeoPoint( 54.7247,55.9408)
        mapController.setCenter(startPoint)


        val radioGroup = findViewById<RadioGroup>(R.id.radio_group)


        radioGroup.setOnCheckedChangeListener{ _, checkedId ->
            when (checkedId) {
                -1 -> toast("-_-")
                R.id.radio_floor1 -> drawOverlay(1)
                R.id.radio_floor2 -> drawOverlay(2)
                R.id.radio_floor3 -> drawOverlay(3)
                R.id.radio_floor4 -> drawOverlay(4)
                R.id.radio_floor5 -> drawOverlay(5)

            }
        }
        drawOverlay(1)

        // BUTTON EVENT
        val btn = findViewById<Button>(R.id.button)
        val toiletCheckBox = findViewById<CheckBox>(R.id.toilet_cb)
        toiletCheckBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                endCampusSpinner.visibility = View.GONE
                findViewById<Spinner>(R.id.end_floor_sp).visibility = View.GONE
                findViewById<Spinner>(R.id.end_number_sp).visibility = View.GONE
            } else {
                endCampusSpinner.visibility = View.VISIBLE
                findViewById<Spinner>(R.id.end_floor_sp).visibility = View.VISIBLE
                findViewById<Spinner>(R.id.end_number_sp).visibility = View.VISIBLE
            }
        }
        btn.setOnClickListener{

            val items = arrayOf("Мужской", "Женский")
            val startRoom = findViewById<Spinner>(R.id.start_campus_sp).selectedItem.toString()+
                    "-"+findViewById<Spinner>(R.id.start_number_sp).selectedItem.toString()
            if (toiletCheckBox.isChecked){
                MaterialAlertDialogBuilder(this)
                    .setTitle(resources.getString(R.string.title))
                    .setItems(items) { _, which ->
                        val firstChar = items[which].first()
                        val url = String.format("http://185.128.105.9:3000/api/getRouteToToilet?startRoom=%s&sex=%s",
                            startRoom, firstChar)
                        drawRoute(url)
                    }
                    .show()
            } else{
                val endRoom = findViewById<Spinner>(R.id.end_campus_sp).selectedItem.toString()+
                        "-"+findViewById<Spinner>(R.id.end_number_sp).selectedItem.toString()
                val url = String.format("http://185.128.105.9:3000/api/getRoute?startRoom=%s&endRoom=%s",
                    startRoom, endRoom)
                if (startRoom != endRoom) {
                    drawRoute(url)
                } else toast("Выберите различающиеся значения")
            }

        }

    }


    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }
    override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
        when (parent.id){
            R.id.start_campus_sp ->
            {
                val floorSpinner: Spinner = findViewById(R.id.start_floor_sp)

                val campus = parent.selectedItem.toString()
                val url = String.format("http://185.128.105.9:3000/api/getCampusFloor?campus=%s", campus)
                startFloors.clear()
                doAsync {
                    val result = URL(url).readText()
                    uiThread {
                        val jsonArray = JSONTokener(result).nextValue() as JSONArray
                        for (i in 0 until jsonArray.length()){
                            startFloors.add(jsonArray.getJSONObject(i).getString("floor"))
                        }
                        val floorAdapter: ArrayAdapter<*> = ArrayAdapter(
                            applicationContext,
                            android.R.layout.simple_spinner_item,
                            startFloors
                        )
                        with(floorAdapter) {
                            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                            notifyDataSetChanged()
                        }
                        floorSpinner.adapter = floorAdapter
                        floorSpinner.onItemSelectedListener = this@MainActivity
                    }
                }


            }
            R.id.start_floor_sp ->
            {
                val roomSpinner: Spinner = findViewById(R.id.start_number_sp)

                val floor = parent.selectedItem.toString()
                val campusSpinner: Spinner = findViewById(R.id.start_campus_sp)
                val campus =  campusSpinner.selectedItem.toString()
                val url = String.format("http://185.128.105.9:3000/api/getFloorRooms?campus=%s&floor=%s", campus, floor)
                startRooms.clear()
                doAsync {
                    val result = URL(url).readText()
                    uiThread {
                        val jsonArray = JSONTokener(result).nextValue() as JSONArray
                        for (i in 0 until jsonArray.length()){
                            startRooms.add(jsonArray.getJSONObject(i).getString("number"))
                        }
                        val roomAdapter: ArrayAdapter<*> = ArrayAdapter(
                            applicationContext,
                            android.R.layout.simple_spinner_item,
                            startRooms
                        )
                        with(roomAdapter) {
                            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                            notifyDataSetChanged()
                        }
                        roomSpinner.adapter = roomAdapter
                        roomSpinner.onItemSelectedListener = this@MainActivity
                    }
                }

            }
            R.id.start_number_sp ->
            {
                //toast("Стартовый кабинет выбран")
            }
            R.id.end_campus_sp ->
            {
                val floorSpinner: Spinner = findViewById(R.id.end_floor_sp)

                val campus = parent.selectedItem.toString()
                val url = String.format("http://185.128.105.9:3000/api/getCampusFloor?campus=%s", campus)
                endFloors.clear()
                doAsync {
                    val result = URL(url).readText()
                    uiThread {
                        val jsonArray = JSONTokener(result).nextValue() as JSONArray
                        for (i in 0 until jsonArray.length()){
                            endFloors.add(jsonArray.getJSONObject(i).getString("floor"))
                        }
                        val floorAdapter: ArrayAdapter<*> = ArrayAdapter(
                            applicationContext,
                            android.R.layout.simple_spinner_item,
                            endFloors
                        )
                        with(floorAdapter) {
                            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                            notifyDataSetChanged()
                        }
                        floorSpinner.adapter = floorAdapter
                        floorSpinner.onItemSelectedListener = this@MainActivity
                    }
                }
            }
            R.id.end_floor_sp ->
            {
                val roomSpinner: Spinner = findViewById(R.id.end_number_sp)

                val floor = parent.selectedItem.toString()
                val campusSpinner: Spinner = findViewById(R.id.end_campus_sp)
                val campus =  campusSpinner.selectedItem.toString()
                val url = String.format("http://185.128.105.9:3000/api/getFloorRooms?campus=%s&floor=%s", campus, floor)
                endRooms.clear()
                doAsync {
                    val result = URL(url).readText()
                    uiThread {
                        val jsonArray = JSONTokener(result).nextValue() as JSONArray
                        for (i in 0 until jsonArray.length()){
                            endRooms.add(jsonArray.getJSONObject(i).getString("number"))
                        }
                        val roomAdapter: ArrayAdapter<*> = ArrayAdapter(
                            applicationContext,
                            android.R.layout.simple_spinner_item,
                            endRooms
                        )
                        with(roomAdapter) {
                            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                            notifyDataSetChanged()
                        }
                        roomSpinner.adapter = roomAdapter
                        roomSpinner.onItemSelectedListener = this@MainActivity
                    }
                }
            }
            R.id.end_number_sp ->
            {
                //toast("Конечный кабинет выбран")
            }
        }
    }

    override fun onNothingSelected(parent: AdapterView<*>) {
        // Another interface callback
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val permissionsToRequest = ArrayList<String>()
        var i = 0
        while (i < grantResults.size) {
            permissionsToRequest.add(permissions[i])
            i++
        }
        if (permissionsToRequest.size > 0) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                REQUEST_PERMISSIONS_REQUEST_CODE)
        }
    }
    private fun getJsonDataFromAsset(fileName: String): String? {
        val jsonString: String
        try {
            jsonString = assets.open(fileName).bufferedReader().use { it.readText() }
        } catch (ioException: IOException) {
            ioException.printStackTrace()
            return null
        }
        return jsonString
    }
    private fun getBitmapFromAsset(strName: String?): Bitmap? {
        val istr: InputStream
        var bitmap: Bitmap? = null
        try {
            istr = assets.open(strName!!)
            bitmap = BitmapFactory.decodeStream(istr)
        } catch (e: IOException) {
            return null
        }
        return bitmap
    }
    private  fun drawRoute(url: String){
        if (map.overlays.lastIndex == 2){
            map.overlays.removeAt(2)
        }
        doAsync {
            val result = URL(url).readText()

            //toast(result)
            uiThread {
                kmlDocument.parseGeoJSON(result)
                val myOverLay = kmlDocument.mKmlRoot.buildOverlay(map, lineStyle, null, kmlDocument) as FolderOverlay
                map.overlays.add(2, myOverLay)
                map.invalidate()
                map.zoomToBoundingBox(kmlDocument.mKmlRoot.boundingBox, true)

            }
        }
    }
    private fun drawOverlay(floor: Int) {
        if (map.overlays.isNotEmpty()){
            map.overlays.removeAt(1)
            map.overlays.removeAt(0)
        }
        doAsync {

            uiThread {
                val overlay = GroundOverlay()

                overlay.transparency = 0.1f
                overlay.image = getBitmapFromAsset(String.format("f%s.bmp", floor))
                overlay.setPosition(
                    GeoPoint(54.72557707,55.94045790),
                    GeoPoint(54.72380657,55.94327701)
                )
                map.overlays.add(0, overlay)



                val f1 = getJsonDataFromAsset(String.format("f%s_rooms.geojson",floor))
                kmlDocument.parseGeoJSON(f1)
                val f1OverLay = kmlDocument.mKmlRoot.buildOverlay(map, defaultStyle, null, kmlDocument) as FolderOverlay
                map.overlays.add(1, f1OverLay)
                map.invalidate()
            }
        }

    }


}

