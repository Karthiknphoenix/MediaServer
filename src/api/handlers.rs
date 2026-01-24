use axum::{
    body::Body,
    extract::{Path, State},
    http::{header, StatusCode, HeaderMap},
    response::{IntoResponse, Response},
    Json,
};
use sqlx::{SqlitePool, FromRow};
use tokio::fs::File;
use tokio::io::{AsyncReadExt, AsyncSeekExt};
use std::io::SeekFrom;
use crate::db::models::{Media, Library, LibraryType, Setting};
use crate::core::scanner::scan_media;

#[derive(serde::Deserialize)]
pub struct CreateLibraryRequest {
    name: String,
    path: String,
    library_type: LibraryType,
}

#[derive(serde::Deserialize)]
pub struct UpdateSettingRequest {
    key: String,
    value: String,
}

pub async fn get_libraries(State(pool): State<SqlitePool>) -> impl IntoResponse {
    let libraries = sqlx::query_as::<_, Library>("SELECT * FROM libraries")
        .fetch_all(&pool)
        .await
        .unwrap_or(vec![]);
    Json(libraries)
}

pub async fn get_settings(State(pool): State<SqlitePool>) -> impl IntoResponse {
    let settings = sqlx::query_as::<_, Setting>("SELECT * FROM settings")
        .fetch_all(&pool)
        .await
        .unwrap_or(vec![]);
    Json(settings)
}

pub async fn update_setting(
    State(pool): State<SqlitePool>,
    Json(payload): Json<UpdateSettingRequest>,
) -> impl IntoResponse {
    let result = sqlx::query("INSERT INTO settings (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = ?")
        .bind(&payload.key)
        .bind(&payload.value)
        .bind(&payload.value)
        .execute(&pool)
        .await;

    match result {
        Ok(_) => StatusCode::OK,
        Err(_) => StatusCode::INTERNAL_SERVER_ERROR,
    }
}

pub async fn create_library(
    State(pool): State<SqlitePool>,
    Json(payload): Json<CreateLibraryRequest>,
) -> impl IntoResponse {
    let result = sqlx::query("INSERT INTO libraries (name, path, library_type) VALUES (?, ?, ?)")
        .bind(&payload.name)
        .bind(&payload.path)
        .bind(&payload.library_type)
        .execute(&pool)
        .await;

    match result {
        Ok(_) => StatusCode::CREATED,
        Err(_) => StatusCode::INTERNAL_SERVER_ERROR,
    }
}

pub async fn delete_library(
    Path(id): Path<i64>,
    State(pool): State<SqlitePool>,
) -> impl IntoResponse {
    let result = sqlx::query("DELETE FROM libraries WHERE id = ?")
        .bind(id)
        .execute(&pool)
        .await;

    match result {
        Ok(_) => StatusCode::NO_CONTENT,
        Err(_) => StatusCode::INTERNAL_SERVER_ERROR,
    }
}

pub async fn scan_all_libraries(State(pool): State<SqlitePool>) -> impl IntoResponse {
    let pool_clone = pool.clone();
    tokio::spawn(async move {
        scan_media(&pool_clone).await;
    });
    StatusCode::ACCEPTED
}

pub async fn get_library_media(
    Path(id): Path<i64>,
    State(pool): State<SqlitePool>,
) -> impl IntoResponse {
    let media = sqlx::query_as::<_, Media>("SELECT * FROM media WHERE library_id = ? ORDER BY title ASC")
        .bind(id)
        .fetch_all(&pool)
        .await
        .unwrap_or(vec![]);
    
    Json(media)
}

#[derive(serde::Deserialize)]
pub struct UpdateProgressRequest {
    position: i64,
    total_duration: i64,
}

#[derive(Debug, FromRow, Serialize, Deserialize, Clone)]
pub struct MediaWithProgress {
    #[serde(flatten)]
    pub media: Media,
    pub progress: Option<i64>,
}

pub async fn update_progress(
    Path(id): Path<i64>,
    State(pool): State<SqlitePool>,
    Json(payload): Json<UpdateProgressRequest>,
) -> impl IntoResponse {
    let result = sqlx::query(
        "INSERT INTO playback_progress (media_id, position, total_duration, last_watched) 
         VALUES (?, ?, ?, CURRENT_TIMESTAMP) 
         ON CONFLICT(media_id) DO UPDATE SET position = ?, total_duration = ?, last_watched = CURRENT_TIMESTAMP"
    )
    .bind(id)
    .bind(payload.position)
    .bind(payload.total_duration)
    .bind(payload.position)
    .bind(payload.total_duration)
    .execute(&pool)
    .await;

    match result {
        Ok(_) => StatusCode::OK,
        Err(_) => StatusCode::INTERNAL_SERVER_ERROR,
    }
}

pub async fn get_continue_watching(State(pool): State<SqlitePool>) -> impl IntoResponse {
    // Join media with progress where position > 0 and position < total_duration * 0.95 (assume finished if > 95%)
    let media = sqlx::query_as::<_, MediaWithProgress>(
        "SELECT m.*, p.position as progress 
         FROM media m
         JOIN playback_progress p ON m.id = p.media_id
         WHERE p.position > 10 AND p.position < (p.total_duration * 0.95)
         ORDER BY p.last_watched DESC
         LIMIT 10"
    )
    .fetch_all(&pool)
    .await
    .unwrap_or(vec![]);

    Json(media)
}

pub async fn get_media_progress(
    Path(id): Path<i64>,
    State(pool): State<SqlitePool>,
) -> impl IntoResponse {
    let progress: Option<i64> = sqlx::query_scalar("SELECT position FROM playback_progress WHERE media_id = ?")
        .bind(id)
        .fetch_optional(&pool)
        .await
        .unwrap_or(None);
    
    Json(serde_json::json!({ "position": progress.unwrap_or(0) }))
}

pub async fn get_recently_added(State(pool): State<SqlitePool>) -> impl IntoResponse {
    let media = sqlx::query_as::<_, Media>("SELECT * FROM media ORDER BY added_at DESC LIMIT 20")
        .fetch_all(&pool)
        .await
        .unwrap_or(vec![]);
    
    Json(media)
}

pub async fn stream_video(
    Path(id): Path<i64>,
    State(pool): State<SqlitePool>,
    headers: HeaderMap,
) -> Result<impl IntoResponse, StatusCode> {
    let result: Option<(String,)> = sqlx::query_as("SELECT file_path FROM media WHERE id = ?")
        .bind(id)
        .fetch_optional(&pool)
        .await
        .map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;

    let file_path = match result {
        Some((path,)) => path,
        None => return Err(StatusCode::NOT_FOUND),
    };

    let mut file = File::open(&file_path).await.map_err(|_| StatusCode::NOT_FOUND)?;
    let metadata = file.metadata().await.map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;
    let file_size = metadata.len();

    let range = headers
        .get(header::RANGE)
        .and_then(|value| value.to_str().ok())
        .and_then(|s| {
            let s = s.strip_prefix("bytes=")?;
            let mut parts = s.split('-');
            let start = parts.next()?.parse::<u64>().ok()?;
            let end = parts.next().and_then(|s| s.parse::<u64>().ok());
            Some((start, end))
        });

    match range {
        Some((start, end)) => {
            let end = end.unwrap_or(file_size - 1);
            let length = end - start + 1;

            file.seek(SeekFrom::Start(start)).await.map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;
            
            let mut buf = vec![0; length as usize];
            file.read_exact(&mut buf).await.map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)?;

            let body = Body::from(buf);

            let mut response = Response::new(body);
            *response.status_mut() = StatusCode::PARTIAL_CONTENT;
            response.headers_mut().insert(header::CONTENT_RANGE, format!("bytes {}-{}/{}", start, end, file_size).parse().unwrap());
            response.headers_mut().insert(header::CONTENT_LENGTH, length.to_string().parse().unwrap());
            response.headers_mut().insert(header::CONTENT_TYPE, "video/mp4".parse().unwrap());
            response.headers_mut().insert(header::ACCEPT_RANGES, "bytes".parse().unwrap());

            Ok(response)
        }
        None => {
             // Serve full content if no range
            let body = Body::from_stream(tokio_util::io::ReaderStream::new(file));
             let mut response = Response::new(body);
            response.headers_mut().insert(header::CONTENT_LENGTH, file_size.to_string().parse().unwrap());
            response.headers_mut().insert(header::CONTENT_TYPE, "video/mp4".parse().unwrap());
            Ok(response)
        }
    }
}
