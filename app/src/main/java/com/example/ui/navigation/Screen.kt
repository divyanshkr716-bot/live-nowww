package com.example.ui.navigation

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object VideoLibrary : Screen("video_library")
    object VideoInspect : Screen("video_inspect/{videoId}") {
        fun createRoute(videoId: Long) = "video_inspect/$videoId"
    }
    object PrepareVideo : Screen("prepare_video/{videoId}") {
        fun createRoute(videoId: Long) = "prepare_video/$videoId"
    }
    object PreparedDetails : Screen("prepared_details/{videoId}") {
        fun createRoute(videoId: Long) = "prepared_details/$videoId"
    }
    object DestinationManagement : Screen("destination_management")
    object EditDestination : Screen("edit_destination/{destId}") {
        fun createRoute(destId: Long = 0) = "edit_destination/$destId"
    }
    object LiveConfig : Screen("live_config")
    object MultiLiveDashboard : Screen("multi_live")
    object StreamDetails : Screen("stream_details/{destId}") {
        fun createRoute(destId: Long) = "stream_details/$destId"
    }
    object Diagnostics : Screen("diagnostics")
    object BatterySaver : Screen("battery_saver")
    object StorageManagement : Screen("storage_management")
    object AboutPermissions : Screen("about_permissions")
}
