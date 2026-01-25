use axum::{
    extract::{Path, State},
    Json,
};
use sqlx::SqlitePool;
use crate::api::error::AppError;
use crate::core::media_service;
use crate::db::models::Media;

pub async fn get_library_media(
    Path(id): Path<i64>,
    State(pool): State<SqlitePool>,
) -> Result<Json<Vec<Media>>, AppError> {
    let media = sqlx::query_as::<_, Media>("SELECT m.*, l.library_type FROM media m JOIN libraries l ON m.library_id = l.id WHERE m.library_id = ? ORDER BY m.title ASC")
        .bind(id)
        .fetch_all(&pool)
        .await?;
    Ok(Json(media))
}

pub async fn get_recently_added(State(pool): State<SqlitePool>) -> Result<Json<Vec<Media>>, AppError> {
    let query = "
        SELECT 
            MAX(media.id) as id,
            media.library_id,
            l.library_type,
            MAX(media.file_path) as file_path,
            COALESCE(media.series_name, media.title) as title,
            media.year,
            (CASE WHEN media.series_name IS NOT NULL THEN 
                (SELECT poster_url FROM media m2 WHERE m2.series_name = media.series_name AND m2.poster_url IS NOT NULL LIMIT 1)
             ELSE media.poster_url END) as poster_url,
            media.plot,
            (CASE WHEN media.series_name IS NOT NULL THEN 'series' ELSE 'movie' END) as media_type,
            MAX(media.added_at) as added_at,
            media.series_name,
            NULL as season_number,
            NULL as episode_number,
            NULL as tmdb_id,
            (CASE WHEN media.series_name IS NOT NULL THEN 
                (SELECT backdrop_url FROM media m2 WHERE m2.series_name = media.series_name AND m2.backdrop_url IS NOT NULL LIMIT 1)
             ELSE media.backdrop_url END) as backdrop_url,
            NULL as still_url,
            media.runtime,
            media.genres
        FROM media
        JOIN libraries l ON media.library_id = l.id
        WHERE l.library_type != 'other'
        GROUP BY COALESCE(media.series_name, media.id)
        ORDER BY MAX(media.added_at) DESC
        LIMIT 20
    ";

    let media = sqlx::query_as::<_, Media>(query)
        .fetch_all(&pool)
        .await?;
    
    Ok(Json(media))
}

pub async fn get_media_details(
    State(pool): State<SqlitePool>,
    Path(id): Path<i64>,
) -> Result<Json<Media>, AppError> {
    let item = sqlx::query_as::<_, Media>("SELECT m.*, l.library_type FROM media m JOIN libraries l ON m.library_id = l.id WHERE m.id = ?")
        .bind(id)
        .fetch_optional(&pool)
        .await?
        .ok_or_else(|| AppError::NotFound(format!("Media with id {} not found", id)))?;
    
    Ok(Json(item))
}

pub async fn refresh_media_metadata(
    State(pool): State<SqlitePool>,
    Path(id): Path<i64>,
) -> Result<Json<Media>, AppError> {
    use crate::core::metadata::fetch_metadata;

    let media = sqlx::query_as::<_, Media>("SELECT * FROM media WHERE id = ?")
        .bind(id)
        .fetch_optional(&pool)
        .await?
        .ok_or_else(|| AppError::NotFound(format!("Media with id {} not found", id)))?;

    let title_to_search = media.title.unwrap_or_else(|| {
         std::path::Path::new(&media.file_path)
            .file_stem()
            .unwrap_or_default()
            .to_string_lossy()
            .to_string()
    });

    let type_hint = if media.series_name.is_some() { Some("series") } else { Some("movie") };

    tracing::info!("Refreshing metadata for: {}", title_to_search);

    let meta = fetch_metadata(&title_to_search, type_hint, &pool).await
        .map_err(|e| AppError::External(format!("Failed to fetch metadata: {}", e)))?;

    media_service::update_media_metadata(&pool, id, &meta).await?;
    
    get_media_details(State(pool), Path(id)).await
}

#[derive(serde::Deserialize)]
pub struct SearchQuery {
    query: String,
    media_type: Option<String>,
}

pub async fn search_tmdb_handler(
    State(pool): State<SqlitePool>,
    axum::extract::Query(params): axum::extract::Query<SearchQuery>,
) -> Result<Json<Vec<crate::core::metadata::TmdbSearchResult>>, AppError> {
    use crate::core::metadata::{search_tmdb, fetch_tmdb_by_id, TmdbSearchResult};

    let key = media_service::get_tmdb_api_key(&pool).await?
        .ok_or_else(|| AppError::BadRequest("TMDB API key not configured".to_string()))?;

    let media_type = params.media_type.as_deref();
    
    // Check if query is a numeric TMDB ID
    if let Ok(tmdb_id) = params.query.trim().parse::<i64>() {
        let meta = fetch_tmdb_by_id(tmdb_id, media_type, &key).await?;
        let result = TmdbSearchResult {
            id: tmdb_id,
            title: meta.title,
            year: meta.year,
            poster_url: meta.poster,
            overview: meta.plot,
        };
        Ok(Json(vec![result]))
    } else {
        let results = search_tmdb(&params.query, media_type, &key).await?;
        Ok(Json(results))
    }
}

#[derive(serde::Deserialize)]
pub struct IdentifyRequest {
    tmdb_id: i64,
    media_type: Option<String>,
}

pub async fn identify_media(
    State(pool): State<SqlitePool>,
    Path(id): Path<i64>,
    Json(payload): Json<IdentifyRequest>,
) -> Result<Json<Media>, AppError> {
    use crate::core::metadata::fetch_tmdb_by_id;

    let key = media_service::get_tmdb_api_key(&pool).await?
        .ok_or_else(|| AppError::BadRequest("TMDB API key not configured".to_string()))?;

    let media_type = payload.media_type.as_deref();
    let meta = fetch_tmdb_by_id(payload.tmdb_id, media_type, &key).await?;

    media_service::update_media_metadata(&pool, id, &meta).await?;
    
    get_media_details(State(pool), Path(id)).await
}

