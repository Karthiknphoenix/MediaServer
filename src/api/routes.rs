use axum::{
    routing::get,
    Router,
};
use sqlx::SqlitePool;
use crate::api::handlers::{get_recently_added, stream_video, get_libraries, create_library, delete_library, scan_all_libraries, get_library_media, get_settings, update_setting, update_progress, get_continue_watching, get_media_progress};

pub fn app(pool: SqlitePool) -> Router {
    Router::new()
        .route("/api/recent", get(get_recently_added))
        .route("/stream/:id", get(stream_video))
        .route("/api/libraries", get(get_libraries).post(create_library))
        .route("/api/libraries/:id", axum::routing::delete(delete_library))
        .route("/api/libraries/:id/media", get(get_library_media))
        .route("/api/settings", get(get_settings).post(update_setting))
        .route("/api/scan", axum::routing::post(scan_all_libraries))
        .route("/api/media/:id/progress", get(get_media_progress).post(update_progress))
        .route("/api/continue", get(get_continue_watching))
        .with_state(pool)
}
