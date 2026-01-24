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
use serde::{Serialize, Deserialize};
use crate::api::error::AppError;

#[derive(serde::Deserialize)]
pub struct UpdateProgressRequest {
    position: i64,
    total_duration: i64,
}

#[derive(Debug, FromRow, Serialize, Deserialize, Clone)]
pub struct MediaWithProgress {
    pub id: i64,
    pub library_id: i64,
    pub file_path: String,
    pub title: Option<String>,
    pub year: Option<i64>,
    pub poster_url: Option<String>,
    pub plot: Option<String>,
    pub media_type: Option<String>,
    pub added_at: Option<chrono::NaiveDateTime>,
    pub series_name: Option<String>,
    pub season_number: Option<i32>,
    pub episode_number: Option<i32>,
    pub tmdb_id: Option<i64>,
    pub backdrop_url: Option<String>,
    pub still_url: Option<String>,
    pub runtime: Option<i32>,
    pub genres: Option<String>,
    pub progress: Option<i64>,
}

pub async fn get_continue_watching(State(pool): State<SqlitePool>) -> Result<Json<Vec<MediaWithProgress>>, AppError> {
    let media = sqlx::query_as::<_, MediaWithProgress>(
        "SELECT m.*, p.position as progress 
         FROM media m
         JOIN playback_progress p ON m.id = p.media_id
         JOIN libraries l ON m.library_id = l.id
         WHERE p.position > 10 AND p.position < (p.total_duration * 0.95)
         ORDER BY p.last_watched DESC
         LIMIT 10"
    )
    .fetch_all(&pool)
    .await?;

    Ok(Json(media))
}

pub async fn update_progress(
    Path(id): Path<i64>,
    State(pool): State<SqlitePool>,
    Json(payload): Json<UpdateProgressRequest>,
) -> Result<StatusCode, AppError> {
    sqlx::query(
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
    .await?;

    Ok(StatusCode::OK)
}

pub async fn get_media_progress(
    Path(id): Path<i64>,
    State(pool): State<SqlitePool>,
) -> Result<Json<serde_json::Value>, AppError> {
    let progress: Option<i64> = sqlx::query_scalar("SELECT position FROM playback_progress WHERE media_id = ?")
        .bind(id)
        .fetch_optional(&pool)
        .await?;
    
    Ok(Json(serde_json::json!({ "position": progress.unwrap_or(0) })))
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
            let body = Body::from_stream(tokio_util::io::ReaderStream::new(file));
             let mut response = Response::new(body);
            response.headers_mut().insert(header::CONTENT_LENGTH, file_size.to_string().parse().unwrap());
            response.headers_mut().insert(header::CONTENT_TYPE, "video/mp4".parse().unwrap());
            Ok(response)
        }
    }
}
