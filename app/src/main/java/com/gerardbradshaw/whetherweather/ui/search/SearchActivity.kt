package com.gerardbradshaw.whetherweather.ui.search

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import com.gerardbradshaw.whetherweather.R
import com.gerardbradshaw.whetherweather.room.LocationEntity
import com.gerardbradshaw.whetherweather.ui.BaseViewModel
import com.gerardbradshaw.whetherweather.ui.detail.DetailActivity
import com.gerardbradshaw.whetherweather.util.Constants.API_KEY_MAPS
import com.google.android.gms.common.api.Status
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener

class SearchActivity : AppCompatActivity() {
  private lateinit var viewModel: BaseViewModel
  private lateinit var placesClient: PlacesClient
  private lateinit var autocompleteFragment: AutocompleteSupportFragment


  // ------------------------ ACTIVITY EVENTS ------------------------

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_find)
    initActivity()
  }


  // ------------------------ INIT ------------------------

  private fun initActivity() {
    supportActionBar?.hide()
    viewModel = ViewModelProvider(this).get(BaseViewModel::class.java)

    initPlacesApi()
    initAutocompleteFragment()
  }

  private fun initPlacesApi() {
    if (!Places.isInitialized()) {
      Places.initialize(applicationContext, API_KEY_MAPS)
      placesClient = Places.createClient(this)
    }
  }

  private fun initAutocompleteFragment() {
    autocompleteFragment = supportFragmentManager.findFragmentById(R.id.autocomplete_fragment)
        as AutocompleteSupportFragment

    autocompleteFragment.let {
      it.setPlaceFields(listOf(
          Place.Field.NAME,
          Place.Field.TYPES,
          Place.Field.ADDRESS_COMPONENTS,
          Place.Field.LAT_LNG,
          Place.Field.UTC_OFFSET))

      it.setOnPlaceSelectedListener(object : PlaceSelectionListener {
        override fun onPlaceSelected(p0: Place) {
          saveLocationToDb(p0)

          val returnIntent = Intent()
          returnIntent.putExtra(DetailActivity.EXTRA_PAGER_POSITION, Int.MAX_VALUE)
          setResult(RESULT_OK, returnIntent)
          finish()
        }

        override fun onError(p0: Status) {
          Log.d(TAG, "onError: ERROR: no place was selected")
          setResult(RESULT_CANCELED)
          finish()
        }
      })

      it.view?.requestFocus()
    }
  }


  // ------------------------ UTIL ------------------------

  private fun saveLocationToDb(place: Place) {
    val addressComponents = place.addressComponents
    val list = addressComponents!!.asList()
    Log.d(TAG, "saveLocationToDb: address components: $list")

    val name = place.name
    val offset = place.utcOffsetMinutes?.times(60L) ?: 0L
    val lat = place.latLng?.latitude?.toFloat()
    val lon = place.latLng?.longitude?.toFloat()

    if (name == null || lat == null || lon == null) {
      Log.d(TAG, "saveLocationToDb: ERROR: location info incomplete " +
          "(name = $name, offset = $offset, lat = $lat, lon = $lon")
      return
    }

    val entity = LocationEntity(name, offset, lat, lon)

    viewModel.saveLocation(entity)
    Log.d(TAG, "saveLocationToDb: Success! ${entity.name} sent to DB")
  }

  companion object {
    private const val TAG = "GGG SearchActivity"
  }
}