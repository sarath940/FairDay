package com.gerardbradshaw.whetherweather.activities.detail

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.location.Address
import android.net.Uri
import android.os.*
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.resource.bitmap.BitmapTransitionOptions
import com.gerardbradshaw.weatherinfoview.datamodels.WeatherData
import com.gerardbradshaw.whetherweather.Constants
import com.gerardbradshaw.whetherweather.application.BaseApplication
import com.gerardbradshaw.whetherweather.R
import com.gerardbradshaw.whetherweather.activities.utils.AutocompleteUtil
import com.gerardbradshaw.whetherweather.room.LocationEntity
import com.gerardbradshaw.whetherweather.activities.utils.BaseViewModel
import com.gerardbradshaw.whetherweather.activities.detail.utils.ConditionImageUtil
import com.gerardbradshaw.whetherweather.activities.detail.utils.GpsUtil
import com.gerardbradshaw.whetherweather.activities.saved.SavedActivity
import com.gerardbradshaw.whetherweather.activities.detail.utils.GpsUtil.Companion.REQUEST_CODE_CHECK_SETTINGS
import com.gerardbradshaw.whetherweather.activities.detail.utils.OpenWeatherCreditView
import com.gerardbradshaw.whetherweather.activities.detail.utils.WeatherUtil
import com.gerardbradshaw.whetherweather.activities.detail.viewpager.DetailPagerAdapter
import com.gerardbradshaw.whetherweather.activities.detail.viewpager.PagerItemUtil
import java.util.LinkedHashMap
import javax.inject.Inject

class DetailActivity :
  AppCompatActivity(),
  GpsUtil.GpsUtilListener,
  WeatherUtil.WeatherDetailsListener,
  DetailPagerAdapter.DataChangeListener,
  SharedPreferences.OnSharedPreferenceChangeListener {
  private lateinit var app: BaseApplication
  private lateinit var viewModel: BaseViewModel
  private lateinit var backgroundImage: ImageView
  private lateinit var instructionsTextView: TextView
  private lateinit var viewPager: ViewPager2
  private lateinit var pagerItemUtil: PagerItemUtil
  private lateinit var optionsMenu: Menu
  private lateinit var prefs: SharedPreferences

  @Inject
  lateinit var pagerAdapter: DetailPagerAdapter
  @Inject
  lateinit var gpsUtil: GpsUtil
  @Inject
  lateinit var weatherUtil: WeatherUtil
  @Inject
  lateinit var glideInstance: RequestManager
  @Inject
  lateinit var autocompleteUtil: AutocompleteUtil




  // ------------------------ ACTIVITY WAKE ------------------------

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_detail)
    initActivity(savedInstanceState)
  }

  override fun onResume() {
    gpsUtil.onResume()
    initListeners()
    viewPager.registerOnPageChangeCallback(viewPagerPageChangeCallback)
    super.onResume()
  }

  private fun initListeners() {
    weatherUtil.setWeatherDetailsListener(this)
    gpsUtil.setOnGpsUpdateListener(this)

    findViewById<OpenWeatherCreditView>(R.id.open_weather_credit_view).setOnClickListener {
      Intent(Intent.ACTION_VIEW).also {
        it.data = Uri.parse(Constants.URL_OPEN_WEATHER)
        startActivity(it)
      }
    }

    prefs.registerOnSharedPreferenceChangeListener(this)
  }

  private val viewPagerPageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
    override fun onPageSelected(position: Int) {
      updateBackgroundImageToMatchAdapterAt(viewPager.currentItem)
      super.onPageSelected(position)
    }
  }


  // ------------------------ INIT ------------------------

  private fun initActivity(savedInstanceState: Bundle?) {
    app = application as BaseApplication
    viewModel = ViewModelProvider(this).get(BaseViewModel::class.java)

    initFields()
    injectFields()
    initPager()
    loadInstanceState(savedInstanceState)
  }

  private fun initFields() {
    prefs = PreferenceManager.getDefaultSharedPreferences(this)
    backgroundImage = findViewById(R.id.background_image_view)
    instructionsTextView = findViewById(R.id.instructions_text_view)
  }

  private fun injectFields() {
    val component = app
      .getAppComponent()
      .getDetailActivityComponentFactory()
      .create(this, this)

    component.inject(this)
  }

  private fun initPager() {
    viewPager = findViewById(R.id.view_pager)
    pagerAdapter = DetailPagerAdapter(this)
    pagerAdapter.setItemCountChangeListener(this)
    viewPager.adapter = pagerAdapter

    pagerItemUtil = PagerItemUtil(
      this,
      viewModel.getLiveLocations(),
      MutableLiveData(),
      weatherUtil)

    pagerItemUtil.dataLive.observe(this) {
      pagerAdapter.setData(it)
      showInstructions(it.isEmpty())
    }
  }

  private fun loadInstanceState(savedInstanceState: Bundle?) {
    Log.d(TAG, "loadInstanceState: no state loaded from bundle ($savedInstanceState)")
  }


  // ------------------------ ACTIVITY SLEEP ------------------------

  override fun onPause() {
    gpsUtil.onPause()
    unregisterListeners()
    super.onPause()
  }

  private fun unregisterListeners() {
    viewPager.unregisterOnPageChangeCallback(viewPagerPageChangeCallback)
    prefs.unregisterOnSharedPreferenceChangeListener(this)
  }


  // ------------------------ ACTION MENU ------------------------

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    optionsMenu = menu
    menuInflater.inflate(R.menu.menu_action_bar_detail_activity, menu)
    supportActionBar?.setDisplayShowTitleEnabled(false)

    onSharedPreferenceChanged(prefs, Constants.KEY_GPS_ENABLED)

    return super.onCreateOptionsMenu(menu)
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {
      R.id.action_add -> onAddButtonClicked()
      R.id.action_pin -> onPinButtonClicked()
      R.id.action_list -> onListButtonClicked()
      R.id.action_refresh -> onRefreshButtonClicked()
      else -> return super.onOptionsItemSelected(item)
    }
    return true
  }

  private fun onAddButtonClicked() {
    autocompleteUtil.getPlaceFromAutocomplete()
  }

  private fun onPinButtonClicked() {
    val isGpsEnabled = prefs.getBoolean(Constants.KEY_GPS_ENABLED, false)

    if (!isGpsEnabled) {
      gpsUtil.requestUpdates()
    } else {
      gpsUtil.stopRequestingUpdates()
      pagerItemUtil.disableCurrentLocation()
    }
  }

  private fun setPinIconAsOn(boolean: Boolean) {
    optionsMenu.findItem(R.id.action_pin)?.let {
      if (boolean) {
        it.icon = ContextCompat.getDrawable(this, R.drawable.ic_pin_on)
        it.title = getString(R.string.string_disable_location_services)
      } else {
        it.icon = ContextCompat.getDrawable(this, R.drawable.ic_pin_off)
        it.title = getString(R.string.string_enable_location_services)
      }
      it.icon.setTint(Color.WHITE)
    }
  }

  private fun onListButtonClicked() {
    val intent = Intent(this, SavedActivity::class.java)
    savedActivityResultLauncher.launch(intent)
  }

  private val savedActivityResultLauncher =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
      when (it.resultCode) {
        RESULT_CANCELED -> Log.i(TAG, "movePagerToPosition: no place selected.")
        RESULT_OK -> {
          val position = it.data?.getIntExtra(EXTRA_PAGER_POSITION, -1) ?: -1
          updatePagerPosition(position)
        }
      }
    }

  private fun updatePagerPosition(position: Int) {
    if (position != -1) viewPager.setCurrentItem(position, true)
    else Log.i(TAG, "updatePagerPosition: no update as position was null")
  }

  private fun onRefreshButtonClicked() {
    pagerItemUtil.refreshWeather()
  }


  // ------------------------ EXTERNAL EVENTS ------------------------

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    when (requestCode) {
      REQUEST_CODE_CHECK_SETTINGS -> gpsUtil.onActivityResult(requestCode, resultCode)
      else -> super.onActivityResult(requestCode, resultCode, data)
    }
  }

  override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
    if (sharedPreferences != null && key == Constants.KEY_GPS_ENABLED) {
      setPinIconAsOn(sharedPreferences.getBoolean(key, false))
    }
  }

  override fun onGpsUpdate(address: Address?) {
    if (address == null) {
      Log.e(TAG, "onGpsUpdate: address is null")
      return
    }

    val currentLocationEntity = LocationEntity(
      address.locality,
      address.latitude.toFloat(),
      address.longitude.toFloat())

    pagerItemUtil.setCurrentLocation(currentLocationEntity)

    showInstructions(false)
  }

  private fun showInstructions(b: Boolean) {
    instructionsTextView.visibility = if (b) View.VISIBLE else View.GONE
  }

  override fun onWeatherReceived(weatherData: WeatherData, locationEntity: LocationEntity?) {
    if (locationEntity != null) {
      pagerItemUtil.setWeather(locationEntity, weatherData)
    }
  }

  private fun updateBackgroundImageToMatchAdapterAt(position: Int) {
    val conditionId = pagerAdapter.getConditionIdFor(position)
    val resId = ConditionImageUtil.getResId(conditionId)

    glideInstance
      .asBitmap()
      .load(resId)
      .transition(BitmapTransitionOptions.withCrossFade())
      .into(backgroundImage)
  }

  override fun onDataUpdate() {
    updateBackgroundImageToMatchAdapterAt(viewPager.currentItem)
  }


  // ------------------------ UTIL ------------------------

  companion object {
    private const val TAG = "GGG DetailActivity"
    const val EXTRA_PAGER_POSITION = "detail_pager_adapter_position"
  }
}