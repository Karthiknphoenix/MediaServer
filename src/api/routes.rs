use axum::{
    routing::get,
    Router,
};
use sqlx::SqlitePool;
use crate::api::handlers::{
    library::{get_libraries, create_library, delete_library, scan_all_libraries, list_directories, browse_library},
    movies::{get_recently_added, get_library_media, get_media_details, refresh_media_metadata, search_tmdb_handler, identify_media},
    playback::{stream_video, update_progress, get_continue_watching, get_media_progress},
    settings::{get_settings, update_setting, reset_database},
    tv::{get_all_series, get_series_seasons, get_season_episodes, get_series_detail, refresh_series_metadata},
};

pub fn app(pool: SqlitePool) -> Router {
    Router::new()
        .route("/api/v1/recent", get(get_recently_added))
        .route("/api/v1/directories", axum::routing::post(list_directories))
        .route("/api/v1/stream/:id", get(stream_video))
        .route("/api/v1/libraries", get(get_libraries).post(create_library))
        .route("/api/v1/libraries/:id", axum::routing::delete(delete_library))
        .route("/api/v1/libraries/:id/media", get(get_library_media))
        .route("/api/v1/libraries/:id/browse", get(browse_library))
        .route("/api/v1/media/:id", get(get_media_details))
        .route("/api/v1/media/:id/refresh", axum::routing::post(refresh_media_metadata))
        .route("/api/v1/media/:id/identify", axum::routing::post(identify_media))
        .route("/api/v1/tmdb/search", get(search_tmdb_handler))
        .route("/api/v1/settings", get(get_settings).post(update_setting))
        .route("/api/v1/reset", axum::routing::post(reset_database))
        .route("/api/v1/scan", axum::routing::post(scan_all_libraries))
        .route("/api/v1/media/:id/progress", get(get_media_progress).post(update_progress))
        .route("/api/v1/continue", get(get_continue_watching))
        // TV Show routes
        .route("/api/v1/series", get(get_all_series))
        .route("/api/v1/series/:name/seasons", get(get_series_seasons))
        .route("/api/v1/series/:name/detail", get(get_series_detail))
        .route("/api/v1/series/:name/refresh", axum::routing::post(refresh_series_metadata))
        .route("/api/v1/series/:name/season/:num", get(get_season_episodes))
        .with_state(pool)
}

