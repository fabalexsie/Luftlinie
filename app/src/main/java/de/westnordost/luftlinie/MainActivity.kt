package de.westnordost.luftlinie

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import de.westnordost.luftlinie.geocoding.GeocodingFragment
import de.westnordost.luftlinie.location.DestinationFragment
import de.westnordost.luftlinie.osmandapi.OsmAndHelper
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : AppCompatActivity() {

    private val mainModel: MainViewModel by viewModel()

    companion object {
        const val REQUEST_OSMAND_API = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(R.id.fragmentContainer, GeocodingFragment())
                .commit()
        }

        mainModel.destinationLocation.observe(this, this::onNewDestination)

        window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
        window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
            window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }

        handleGeoUri()

        val osmandHelper =
            OsmAndHelper(this, REQUEST_OSMAND_API, OsmAndHelper.OnOsmandMissingListener { })
        osmandHelper.getInfo()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_OSMAND_API) {
            if (data != null) {
                val extras = data.extras
                if (extras != null && extras.size() > 0) {
                    var lat: Double = Double.NaN
                    var lon: Double = Double.NaN

                    for (key in extras.keySet()) {
                        if (key == "destination_lat") {
                            val value = extras.get(key) ?: return
                            lat = value as Double
                        } else if (key == "destination_lon") {
                            lon = extras.get(key) as Double
                        }
                    }
                    if (lat.isNaN()) return
                    if (lat < -90 || lat > +90) return
                    if (lon.isNaN()) return
                    if (lon < -180 && lon > +180) return

                    Toast.makeText(this, R.string.use_destination_of_osmand, Toast.LENGTH_LONG)
                        .show()
                    mainModel.destinationLocation.value = Location(null as String?).apply {
                        longitude = lon
                        latitude = lat
                    }
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun handleGeoUri() {
        if (Intent.ACTION_VIEW != intent.action) return
        val data = intent.data ?: return
        if (data.scheme != "geo") return
        val geoUriRegex = Regex("(-?[0-9]*\\.?[0-9]+),(-?[0-9]*\\.?[0-9]+).*")
        val match = geoUriRegex.matchEntire(data.schemeSpecificPart) ?: return
        val lat = match.groupValues[1].toDoubleOrNull() ?: return
        if (lat < -90 || lat > +90) return
        val lon = match.groupValues[2].toDoubleOrNull() ?: return
        if (lon < -180 && lon > +180) return

        mainModel.destinationLocation.value = Location(null as String?).apply {
            longitude = lon
            latitude = lat
        }
    }

    private fun onNewDestination(location: Location?) {
        if (location != null) {
            supportFragmentManager.beginTransaction()
                .setCustomAnimations(
                    R.anim.fragment_open_enter, R.anim.fragment_open_exit,
                    R.anim.fragment_close_enter, R.anim.fragment_close_exit
                )
                .replace(R.id.fragmentContainer, DestinationFragment())
                .addToBackStack(null)
                .commit()
        } else {
            supportFragmentManager.popBackStack()
        }
    }

    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
            return
        }
        super.onBackPressed()
    }
}