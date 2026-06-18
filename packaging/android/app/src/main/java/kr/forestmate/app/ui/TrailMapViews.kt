package kr.forestmate.app.ui

import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kr.forestmate.app.state.LatLon
import kr.forestmate.app.state.TrailMapState
import kr.forestmate.app.state.TrailMarkerKind

object TrailMapViews {
    fun createMap(context: Context, state: TrailMapState): MapView =
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            minZoomLevel = 6.0
            maxZoomLevel = 18.0
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 280),
            )
            controller.setZoom(14.0)
            controller.setCenter(state.start.geoPoint())
            addRoute(state)
            addMarkers(state)
            zoomToState(state)
        }

    private fun MapView.addRoute(state: TrailMapState) {
        if (state.routePoints.size >= 2) {
            overlays.add(
                Polyline(this).apply {
                    setPoints(state.routePoints.map { it.geoPoint() })
                    outlinePaint.color = Color.rgb(45, 106, 79)
                    outlinePaint.strokeWidth = 8f
                    outlinePaint.alpha = 215
                    title = "추천 등산 경로"
                },
            )
        }
        if (state.trackPoints.size >= 2) {
            overlays.add(
                Polyline(this).apply {
                    setPoints(state.trackPoints.map { it.geoPoint() })
                    outlinePaint.color = Color.rgb(40, 83, 173)
                    outlinePaint.strokeWidth = 6f
                    title = "GPS 트랙"
                },
            )
        }
    }

    private fun MapView.addMarkers(state: TrailMapState) {
        state.markers.forEach { marker ->
            overlays.add(
                Marker(this).apply {
                    position = marker.point.geoPoint()
                    title = marker.title
                    snippet = marker.subtitle
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    icon?.setTint(colorFor(marker.kind))
                },
            )
        }
    }

    private fun MapView.zoomToState(state: TrailMapState) {
        val points = state.routePoints + state.trackPoints + state.markers.map { it.point }
        if (points.size < 2) return
        val north = points.maxOf { it.lat }
        val south = points.minOf { it.lat }
        val east = points.maxOf { it.lon }
        val west = points.minOf { it.lon }
        post {
            zoomToBoundingBox(BoundingBox(north, east, south, west), false, dp(context, 28))
            invalidate()
        }
    }

    private fun colorFor(kind: TrailMarkerKind): Int =
        when (kind) {
            TrailMarkerKind.START -> Color.rgb(45, 106, 79)
            TrailMarkerKind.SUMMIT -> Color.rgb(28, 83, 142)
            TrailMarkerKind.HAZARD -> Color.rgb(201, 48, 78)
            TrailMarkerKind.RESCUE -> Color.rgb(224, 138, 30)
            TrailMarkerKind.POSITION -> Color.rgb(40, 83, 173)
        }

    private fun LatLon.geoPoint(): GeoPoint = GeoPoint(lat, lon)

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
