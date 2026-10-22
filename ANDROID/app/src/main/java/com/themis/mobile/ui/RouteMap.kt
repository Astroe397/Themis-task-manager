package com.themis.mobile.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.themis.mobile.tracking.GeoPoint
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

// Free vector tiles, no API key. Swap for any MapLibre style URL.
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val ROUTE_SRC = "route-src"
private const val ROUTE_LAYER = "route-layer"
private const val ME_SRC = "me-src"
private const val ME_LAYER = "me-layer"

/** MapLibre map showing an already-reduced polyline (never raw GPS arrays) and the current position. */
@Composable
fun RouteMap(
    route: List<GeoPoint>, current: GeoPoint?, follow: Boolean,
    modifier: Modifier = Modifier, interactive: Boolean = true
) {
    val ctx = LocalContext.current
    val mapView = remember { MapLibre.getInstance(ctx); MapView(ctx) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            // Leaving the tab: tear the map down, but only the stages the host lifecycle hasn't already done.
            lifecycle.removeObserver(observer)
            val state = lifecycle.currentState
            if (state.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (state.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            if (state != Lifecycle.State.DESTROYED) mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            map = m
            m.uiSettings.isRotateGesturesEnabled = false
            if (!interactive) m.uiSettings.setAllGesturesEnabled(false)   // static overview inside a scrolling page
            m.setStyle(STYLE_URL) { style ->
                style.addSource(GeoJsonSource(ROUTE_SRC))
                style.addLayer(
                    LineLayer(ROUTE_LAYER, ROUTE_SRC).withProperties(
                        PropertyFactory.lineColor("#1F6F62"), PropertyFactory.lineWidth(5f),
                        PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                    )
                )
                style.addSource(GeoJsonSource(ME_SRC))
                style.addLayer(
                    CircleLayer(ME_LAYER, ME_SRC).withProperties(
                        PropertyFactory.circleRadius(7f), PropertyFactory.circleColor("#1F6F62"),
                        PropertyFactory.circleStrokeColor("#FFFFFF"), PropertyFactory.circleStrokeWidth(2.5f)
                    )
                )
                styleReady = true
            }
        }
    }

    LaunchedEffect(map, styleReady, route, current, follow) {
        val m = map ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        val style = m.style ?: return@LaunchedEffect
        val line = style.getSourceAs<GeoJsonSource>(ROUTE_SRC)
        if (route.size >= 2) line?.setGeoJson(Feature.fromGeometry(LineString.fromLngLats(route.map { Point.fromLngLat(it.lon, it.lat) })))
        else line?.setGeoJson(FeatureCollection.fromFeatures(emptyList<Feature>()))
        val me = style.getSourceAs<GeoJsonSource>(ME_SRC)
        if (current != null) me?.setGeoJson(Feature.fromGeometry(Point.fromLngLat(current.lon, current.lat)))
        else me?.setGeoJson(FeatureCollection.fromFeatures(emptyList<Feature>()))

        if (follow && current != null) {
            m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(current.lat, current.lon), 16.0))
        } else if (route.size >= 2) {
            val bounds = LatLngBounds.Builder().apply { route.forEach { include(LatLng(it.lat, it.lon)) } }.build()
            m.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 64))
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}
